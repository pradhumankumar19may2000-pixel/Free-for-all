package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.domain.model.RepeatModeState
import com.example.domain.model.SleepTimerState
import com.example.domain.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val playbackSpeed: Float = 1.0f,
    val crossfadeSeconds: Int = 3,
    val sleepTimer: SleepTimerState = SleepTimerState()
)

class PlaybackController(
    private val context: Context,
    private val audioEffectManager: AudioEffectManager,
    private val visualizerEngine: AudioVisualizerEngine,
    private val onTrackStarted: (Track) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var crossfadeDoneForIndex: Int = -1

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        connectController()
        observeAudioSession()
    }

    private fun observeAudioSession() {
        scope.launch {
            MusicPlaybackService.audioSessionIdFlow.collect { sessionId ->
                if (sessionId > 0) {
                    audioEffectManager.attachToSession(sessionId)
                    visualizerEngine.attach(sessionId, _state.value.isPlaying)
                }
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mc = future.get()
                controller = mc
                attachPlayerListener(mc)
                syncFromController(mc)
            } catch (e: Exception) {
                Log.e("PlaybackController", "Failed connecting MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun attachPlayerListener(mc: MediaController) {
        mc.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                visualizerEngine.attach(MusicPlaybackService.audioSessionIdFlow.value, isPlaying)
                if (isPlaying) startPositionTicker() else stopPositionTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                crossfadeDoneForIndex = -1
                syncFromController(mc)
                mc.volume = 1f
                val idx = mc.currentMediaItemIndex
                val track = _state.value.queue.getOrNull(idx)
                if (track != null) {
                    onTrackStarted(track)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncFromController(mc)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mapped = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
                    Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
                    else -> RepeatModeState.OFF
                }
                _state.update { it.copy(repeatMode = mapped) }
            }
        })
    }

    private fun syncFromController(mc: MediaController) {
        val idx = mc.currentMediaItemIndex
        val current = _state.value.queue.getOrNull(idx)
        val dur = mc.duration.takeIf { it > 0L } ?: current?.durationMs ?: 0L
        _state.update {
            it.copy(
                currentIndex = idx,
                currentTrack = current,
                isPlaying = mc.isPlaying,
                positionMs = mc.currentPosition.coerceAtLeast(0L),
                durationMs = dur
            )
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val mc = controller
                if (mc != null && mc.isPlaying) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val dur = mc.duration.takeIf { it > 0L } ?: _state.value.durationMs
                    _state.update { it.copy(positionMs = pos, durationMs = dur) }

                    // Real crossfade ramp near end of track when another track follows
                    val cfSec = _state.value.crossfadeSeconds
                    val idx = mc.currentMediaItemIndex
                    if (cfSec > 0 && dur > (cfSec * 1000L + 2000L) && mc.hasNextMediaItem() && crossfadeDoneForIndex != idx) {
                        val remaining = dur - pos
                        val cfMs = cfSec * 1000L
                        if (remaining in 1..cfMs) {
                            val ratio = (remaining.toFloat() / cfMs.toFloat()).coerceIn(0.15f, 1f)
                            mc.volume = ratio
                            if (remaining <= 450L) {
                                crossfadeDoneForIndex = idx
                                mc.seekToNextMediaItem()
                                fadeInNextTrack(mc, cfSec)
                            }
                        }
                    }
                }
                delay(33L) // ~30 FPS for crisp synced glass lyrics
            }
        }
    }

    private fun fadeInNextTrack(mc: MediaController, cfSec: Int) {
        scope.launch {
            val steps = (cfSec.coerceAtMost(4) * 10).coerceAtLeast(5)
            for (i in 1..steps) {
                mc.volume = (i.toFloat() / steps.toFloat()).coerceIn(0.1f, 1f)
                delay(60L)
            }
            mc.volume = 1f
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val mc = controller ?: return
        _state.update {
            it.copy(
                queue = tracks,
                currentIndex = safeIdx,
                currentTrack = tracks[safeIdx]
            )
        }
        val mediaItems = tracks.map { it.toMediaItem() }
        mc.setMediaItems(mediaItems, safeIdx, 0L)
        mc.volume = 1f
        mc.prepare()
        mc.play()
        onTrackStarted(tracks[safeIdx])
    }

    fun addToQueue(track: Track) {
        val mc = controller ?: return
        val updated = _state.value.queue + track
        _state.update { it.copy(queue = updated) }
        mc.addMediaItem(track.toMediaItem())
        if (updated.size == 1) {
            mc.prepare()
            mc.play()
            onTrackStarted(track)
        }
    }

    fun removeFromQueue(index: Int) {
        val mc = controller ?: return
        val q = _state.value.queue.toMutableList()
        if (index !in q.indices || q.size <= 1) return
        q.removeAt(index)
        mc.removeMediaItem(index)
        _state.update { it.copy(queue = q) }
        syncFromController(mc)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val mc = controller ?: return
        val q = _state.value.queue.toMutableList()
        if (fromIndex !in q.indices || toIndex !in q.indices || fromIndex == toIndex) return
        val item = q.removeAt(fromIndex)
        q.add(toIndex, item)
        mc.moveMediaItem(fromIndex, toIndex)
        _state.update { it.copy(queue = q) }
        syncFromController(mc)
    }

    fun jumpToQueueIndex(index: Int) {
        val mc = controller ?: return
        if (index !in _state.value.queue.indices) return
        mc.seekToDefaultPosition(index)
        mc.play()
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_IDLE && _state.value.queue.isNotEmpty()) {
                mc.prepare()
            }
            mc.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val mc = controller ?: return
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        mc.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val mc = controller ?: return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else if (_state.value.queue.isNotEmpty()) {
            mc.seekToDefaultPosition(0)
        }
        mc.play()
    }

    fun skipPrevious() {
        val mc = controller ?: return
        if (mc.currentPosition > 3000L) {
            mc.seekTo(0L)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val mc = controller ?: return
        val next = !mc.shuffleModeEnabled
        mc.shuffleModeEnabled = next
        _state.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val mc = controller ?: return
        val nextMode = when (_state.value.repeatMode) {
            RepeatModeState.OFF -> {
                mc.repeatMode = Player.REPEAT_MODE_ALL
                RepeatModeState.ALL
            }
            RepeatModeState.ALL -> {
                mc.repeatMode = Player.REPEAT_MODE_ONE
                RepeatModeState.ONE
            }
            RepeatModeState.ONE -> {
                mc.repeatMode = Player.REPEAT_MODE_OFF
                RepeatModeState.OFF
            }
        }
        _state.update { it.copy(repeatMode = nextMode) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        controller?.playbackParameters = PlaybackParameters(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _state.update { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimer = SleepTimerState()) }
            controller?.volume = 1f
            return
        }
        val totalSecs = minutes * 60
        _state.update {
            it.copy(sleepTimer = SleepTimerState(active = true, remainingSeconds = totalSecs, totalSeconds = totalSecs))
        }
        sleepTimerJob = scope.launch {
            var remaining = totalSecs
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _state.update {
                    it.copy(sleepTimer = SleepTimerState(active = true, remainingSeconds = remaining, totalSeconds = totalSecs))
                }
                if (remaining in 1..3) {
                    controller?.volume = remaining / 3f
                }
            }
            if (isActive) {
                controller?.pause()
                controller?.volume = 1f
                _state.update { it.copy(sleepTimer = SleepTimerState()) }
            }
        }
    }

    fun refreshVisualizerPermissionState() {
        visualizerEngine.attach(
            MusicPlaybackService.audioSessionIdFlow.value,
            _state.value.isPlaying
        )
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        visualizerEngine.releaseVisualizerOnly()
        audioEffectManager.releaseEffects()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun Track.toMediaItem(): MediaItem {
        val playableUri = localDownloadedPath?.takeIf { it.isNotBlank() }?.let { "file://$it" } ?: mediaUri
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)
            .apply {
                if (!artworkUri.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(artworkUri))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(playableUri))
            .setMediaMetadata(metadata)
            .build()
    }
}
