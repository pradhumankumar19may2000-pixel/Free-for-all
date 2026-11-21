package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.TrackDownloadManager
import com.example.data.local.CachedLyricsDao
import com.example.data.local.CachedLyricsEntity
import com.example.data.local.LocalMediaScanner
import com.example.data.local.PlayerPreferences
import com.example.data.local.PreferencesStore
import com.example.data.lyrics.BundledLyricsProvider
import com.example.data.lyrics.LrcLibApi
import com.example.data.lyrics.LrcParser
import com.example.data.playlist.PlaylistCodec
import com.example.data.remote.AppUpdateChecker
import com.example.data.remote.InternetArchiveRepository
import com.example.data.repository.GeminiAssistantRepository
import com.example.data.repository.MusicCloudRepository
import com.example.domain.model.AiAssistantMessage
import com.example.domain.model.AlbumGroup
import com.example.domain.model.AppPlaylist
import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.ArtistGroup
import com.example.domain.model.AudioQuality
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DynamicArtworkTheme
import com.example.domain.model.EqualizerState
import com.example.domain.model.FavoriteTrack
import com.example.domain.model.GenreCategory
import com.example.domain.model.ListeningHistoryEntry
import com.example.domain.model.LyricsLine
import com.example.domain.model.Track
import com.example.domain.model.UserProfile
import com.example.domain.model.VisualizerFrame
import com.example.player.ArtworkPaletteExtractor
import com.example.player.AudioEffectManager
import com.example.player.AudioVisualizerEngine
import com.example.player.PlaybackController
import com.example.player.PlaybackUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

class MusicVerseViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String,
    private val userPhotoUrl: String?,
    private val cloudRepo: MusicCloudRepository,
    private val geminiRepo: GeminiAssistantRepository,
    private val localScanner: LocalMediaScanner,
    private val archiveRepo: InternetArchiveRepository,
    private val lrcLibApi: LrcLibApi,
    private val cachedLyricsDao: CachedLyricsDao,
    private val downloadManager: TrackDownloadManager,
    private val preferencesStore: PreferencesStore,
    private val updateChecker: AppUpdateChecker,
    val audioEffectManager: AudioEffectManager,
    private val visualizerEngine: AudioVisualizerEngine
) : ViewModel() {

    private val playbackController = PlaybackController(
        context = appContext,
        audioEffectManager = audioEffectManager,
        visualizerEngine = visualizerEngine,
        onTrackStarted = { track ->
            viewModelScope.launch {
                cloudRepo.recordTrackPlayed(track)
                loadLyricsAndThemeForTrack(track)
            }
        }
    )

    val playbackState: StateFlow<PlaybackUiState> = playbackController.state
    val equalizerState: StateFlow<EqualizerState> = audioEffectManager.state
    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerEngine.frame
    val preferences: StateFlow<PlayerPreferences> = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlayerPreferences())

    val downloadsState: StateFlow<List<DownloadItem>> = downloadManager.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userProfileState: StateFlow<CloudUiState<UserProfile?>> = cloudRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(CloudUiState.Error((fs ?: err).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val cloudPlaylistsState: StateFlow<CloudUiState<List<AppPlaylist>>> = cloudRepo.observePlaylists(currentUserId)
        .map<List<AppPlaylist>, CloudUiState<List<AppPlaylist>>> { CloudUiState.Success(it) }
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(CloudUiState.Error((fs ?: err).message ?: "Failed to sync playlists"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val favoritesState: StateFlow<CloudUiState<List<FavoriteTrack>>> = cloudRepo.observeFavorites(currentUserId)
        .map<List<FavoriteTrack>, CloudUiState<List<FavoriteTrack>>> { CloudUiState.Success(it) }
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(CloudUiState.Error((fs ?: err).message ?: "Failed to load favorites"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val historyState: StateFlow<CloudUiState<List<ListeningHistoryEntry>>> = cloudRepo.observeHistory(currentUserId)
        .map<List<ListeningHistoryEntry>, CloudUiState<List<ListeningHistoryEntry>>> { CloudUiState.Success(it) }
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(CloudUiState.Error((fs ?: err).message ?: "Failed to load history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    private val _bundledTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _localDeviceTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _pickedLocalTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _catalogTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()

    val allKnownTracks: StateFlow<List<Track>> = combine(
        _bundledTracks,
        _localDeviceTracks,
        _pickedLocalTracks,
        _catalogTracks,
        downloadsState
    ) { bundled, local, picked, catalog, downloads ->
        val completedMap = downloads.filter { it.status == DownloadStatus.COMPLETED }
            .associateBy { it.trackId }
        (bundled + local + picked + catalog).distinctBy { it.id }.map { t ->
            val dl = completedMap[t.id]
            if (dl != null) t.copy(localDownloadedPath = dl.localFilePath) else t
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val localTracks: StateFlow<List<Track>> = combine(_localDeviceTracks, _pickedLocalTracks) { a, b ->
        (a + b).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val catalogTracks: StateFlow<List<Track>> = _catalogTracks.asStateFlow()

    val albums: StateFlow<List<AlbumGroup>> = allKnownTracks.map { tracks ->
        tracks.groupBy { it.album }.map { (albumName, list) ->
            AlbumGroup(
                id = albumName.lowercase().replace(Regex("[^a-z0-9]"), "_"),
                name = albumName,
                artist = list.firstOrNull()?.artist ?: "Various Artists",
                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                artworkResName = list.firstNotNullOfOrNull { it.artworkResName },
                tracks = list
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val artists: StateFlow<List<ArtistGroup>> = allKnownTracks.map { tracks ->
        tracks.groupBy { it.artist }.map { (artistName, list) ->
            ArtistGroup(
                name = artistName,
                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                artworkResName = list.firstNotNullOfOrNull { it.artworkResName },
                tracks = list
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val genres: List<GenreCategory> = listOf(
        GenreCategory("electronic", "Electronic & Synth", "Neon pulses & bass", "electronic synthwave", 0xFFA855F7),
        GenreCategory("hindi", "Hindi & Desi", "Melodic vocals & acoustic", "hindi bollywood acoustic", 0xFFEC4899),
        GenreCategory("bhojpuri", "Bhojpuri Folk", "Regional folk & rhythms", "bhojpuri folk india", 0xFFF59E0B),
        GenreCategory("punjabi", "Punjabi Beats", "Folk & modern grooves", "punjabi bhangra", 0xFF06F6D4),
        GenreCategory("meditation", "Meditation & Raag", "Calm ambient & mindfulness", "meditation ambient raga", 0xFF3B82F6),
        GenreCategory("lofi", "Lo-Fi Study", "Warm tape chill beats", "lofi chill beats", 0xFF8B5CF6),
        GenreCategory("classical", "Classical", "Orchestral & strings", "classical symphony piano", 0xFF10B981),
        GenreCategory("jazz", "Midnight Jazz", "Saxophone & upright bass", "jazz instrumental", 0xFF6366F1)
    )

    private val _lyricsLines = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyricsLines: StateFlow<List<LyricsLine>> = _lyricsLines.asStateFlow()

    private val _lyricsStatus = MutableStateFlow<String>("Ready")
    val lyricsStatus: StateFlow<String> = _lyricsStatus.asStateFlow()

    private val _artworkTheme = MutableStateFlow(DynamicArtworkTheme())
    val artworkTheme: StateFlow<DynamicArtworkTheme> = _artworkTheme.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiAssistantMessage>>(
        listOf(
            AiAssistantMessage(
                id = "welcome",
                fromUser = false,
                text = "Hey! I'm MUSICVERSE AI ASSISTANT powered by Gemini. Ask me to play relaxing music, create a workout or 30-minute meditation playlist, find trending Hindi or Bhojpuri songs, or recommend tracks similar to what's playing."
            )
        )
    )
    val aiMessages: StateFlow<List<AiAssistantMessage>> = _aiMessages.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    init {
        viewModelScope.launch {
            cloudRepo.ensureUserProfile(userDisplayName, userEmail, userPhotoUrl)
        }
        val bundled = localScanner.getBundledTracks()
        _bundledTracks.value = bundled
        bundled.firstOrNull()?.let { first ->
            viewModelScope.launch { loadLyricsAndThemeForTrack(first) }
        }
        scanDeviceMusic()
        loadOpenCatalog("ambient electronic", "Electronic")
        checkAppUpdates(silent = true)
        refreshStorageBytes()
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                playbackController.setCrossfadeSeconds(prefs.crossfadeSeconds)
                playbackController.setPlaybackSpeed(prefs.playbackSpeed)
                visualizerEngine.setBatterySaver(prefs.batterySaverVisualizer)
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun scanDeviceMusic() {
        viewModelScope.launch {
            val scanned = localScanner.scanDeviceAudio()
            _localDeviceTracks.value = scanned
        }
    }

    fun importPickedAudio(uri: Uri) {
        viewModelScope.launch {
            val track = localScanner.extractPickedAudioTrack(uri)
            if (track != null) {
                _pickedLocalTracks.update { list -> (list + track).distinctBy { it.id } }
                playTrack(track, listOf(track))
                showBanner("Imported '${track.title}' from device storage")
            } else {
                showBanner("Could not read metadata from selected audio file.")
            }
        }
    }

    fun loadOpenCatalog(query: String, genreLabel: String) {
        viewModelScope.launch {
            _catalogLoading.value = true
            val result = archiveRepo.fetchAuthorizedOpenMusic(query, genreLabel)
            result.onSuccess { list ->
                _catalogTracks.value = list
                if (list.isEmpty()) {
                    showBanner("No open-licensed MP3 streams found for '$query'.")
                }
            }.onFailure { err ->
                Log.w("MusicVerseVM", "Catalog offline or unavailable: ${err.message}")
            }
            _catalogLoading.value = false
        }
    }

    fun playTrack(track: Track, contextQueue: List<Track> = allKnownTracks.value) {
        val list = if (contextQueue.any { it.id == track.id }) contextQueue else listOf(track) + contextQueue
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(list, idx)
    }

    fun addToQueue(track: Track) {
        playbackController.addToQueue(track)
        showBanner("Added '${track.title}' to Up Next queue")
    }

    fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackController.moveQueueItem(fromIndex, toIndex)
    fun jumpToQueueIndex(index: Int) = playbackController.jumpToQueueIndex(index)
    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun skipNext() = playbackController.skipNext()
    fun skipPrevious() = playbackController.skipPrevious()
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferencesStore.setPlaybackSpeed(speed) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { preferencesStore.setCrossfadeSeconds(seconds) }
    }

    fun startSleepTimer(minutes: Int) = playbackController.startSleepTimer(minutes)

    fun refreshVisualizerPermission() {
        playbackController.refreshVisualizerPermissionState()
    }

    fun toggleFavorite(track: Track) {
        val currentFavs = (favoritesState.value as? CloudUiState.Success)?.data.orEmpty()
        val sanitized = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val isFav = currentFavs.any { it.trackId == sanitized || it.trackId == track.id }
        viewModelScope.launch {
            cloudRepo.toggleFavorite(track, isFav).onFailure {
                showBanner("Could not update favorite: ${it.localizedMessage}")
            }
        }
    }

    // --- SYNCED GLASS LYRICS ---
    private suspend fun loadLyricsAndThemeForTrack(track: Track) {
        _artworkTheme.value = ArtworkPaletteExtractor.extractTheme(appContext, track)
        _lyricsStatus.value = "Loading synced lyrics..."

        // 1. Embedded / sidecar .lrc
        val embedded = track.embeddedLyricsRaw
        if (!embedded.isNullOrBlank()) {
            val lines = LrcParser.parse(embedded, track.durationMs)
            if (lines.isNotEmpty()) {
                _lyricsLines.value = lines
                _lyricsStatus.value = "Sidecar LRC Synced"
                return
            }
        }

        // 2. Room cached LRC (from previous fetch or manual sync)
        val cached = cachedLyricsDao.getLyrics(track.id)
        if (cached != null) {
            val lines = LrcParser.parse(cached.rawLrc, track.durationMs)
            if (lines.isNotEmpty()) {
                _lyricsLines.value = lines
                _lyricsStatus.value = "Cached Synced Lyrics"
                return
            }
        }

        // 3. Bundled multi-language lyrics
        val bundledRaw = BundledLyricsProvider.getRawLrcForBundledTrack(track.id)
        if (bundledRaw != null) {
            val lines = LrcParser.parse(bundledRaw, track.durationMs)
            _lyricsLines.value = lines
            _lyricsStatus.value = "Word-Synced LRC"
            return
        }

        // 4. Online authorized LRCLIB lookup
        val onlineLrc = runCatching {
            val rec = lrcLibApi.getLyrics(track.title, track.artist, track.album)
            rec.syncedLyrics ?: rec.plainLyrics
        }.getOrNull() ?: runCatching {
            lrcLibApi.searchLyrics("${track.title} ${track.artist}")
                .firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                ?.let { it.syncedLyrics ?: it.plainLyrics }
        }.getOrNull()

        if (!onlineLrc.isNullOrBlank()) {
            val parsed = LrcParser.parse(onlineLrc, track.durationMs)
            if (parsed.isNotEmpty()) {
                cachedLyricsDao.upsert(CachedLyricsEntity(trackKey = track.id, rawLrc = onlineLrc))
                _lyricsLines.value = parsed
                _lyricsStatus.value = "LRCLIB Synced"
                return
            } else {
                // Plain unsynced lyrics -> convert into untimed lines for manual sync
                val plainLines = onlineLrc.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                    LyricsLine(timeMs = idx * 4000L, text = line.trim())
                }
                _lyricsLines.value = plainLines
                _lyricsStatus.value = "Plain Lyrics (Tap Manual Sync to time)"
                return
            }
        }

        _lyricsLines.value = emptyList()
        _lyricsStatus.value = "Unavailable"
    }

    fun saveManualSyncedLyrics(track: Track, rawLines: List<LyricsLine>) {
        viewModelScope.launch {
            val lrc = LrcParser.serialize(rawLines)
            cachedLyricsDao.upsert(CachedLyricsEntity(trackKey = track.id, rawLrc = lrc))
            _lyricsLines.value = LrcParser.parse(lrc, track.durationMs)
            _lyricsStatus.value = "Manual Sync Saved"
            showBanner("Saved custom synced timestamps for '${track.title}'")
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        viewModelScope.launch {
            preferencesStore.setLyricsOffsetMs(preferences.value.lyricsOffsetMs + deltaMs)
        }
    }

    fun setLyricsFontScale(scale: Float) {
        viewModelScope.launch { preferencesStore.setLyricsFontScale(scale) }
    }

    fun setLyricsOnlyKaraoke(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setLyricsOnlyKaraoke(enabled) }
    }

    fun setBatterySaverVisualizer(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setBatterySaverVisualizer(enabled) }
    }

    fun setReducedMotionLyrics(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setReducedMotionLyrics(enabled) }
    }

    // --- PLAYLIST MANAGEMENT ---
    fun createPlaylist(name: String, description: String = "", trackIds: List<String> = emptyList()) {
        viewModelScope.launch {
            cloudRepo.createPlaylist(name, description, trackIds)
                .onSuccess { showBanner("Created playlist '$name'") }
                .onFailure { showBanner("Playlist creation failed: ${it.localizedMessage}") }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            cloudRepo.updatePlaylist(playlistId, name = newName)
                .onSuccess { showBanner("Renamed playlist to '$newName'") }
                .onFailure { showBanner("Rename failed: ${it.localizedMessage}") }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            cloudRepo.deletePlaylist(playlistId)
                .onSuccess { showBanner("Playlist deleted") }
        }
    }

    fun duplicatePlaylist(playlist: AppPlaylist) {
        createPlaylist("${playlist.name} (Copy)", playlist.description, playlist.trackIds)
    }

    fun addTrackToPlaylist(playlist: AppPlaylist, trackId: String) {
        val updated = (playlist.trackIds + trackId).distinct()
        viewModelScope.launch {
            cloudRepo.updatePlaylist(playlist.id, trackIds = updated)
                .onSuccess { showBanner("Added to '${playlist.name}'") }
        }
    }

    fun removeTrackFromPlaylist(playlist: AppPlaylist, trackId: String) {
        val updated = playlist.trackIds.filterNot { it == trackId }
        viewModelScope.launch {
            cloudRepo.updatePlaylist(playlist.id, trackIds = updated)
        }
    }

    fun reorderPlaylistTrack(playlist: AppPlaylist, fromIdx: Int, toIdx: Int) {
        val list = playlist.trackIds.toMutableList()
        if (fromIdx !in list.indices || toIdx !in list.indices || fromIdx == toIdx) return
        val item = list.removeAt(fromIdx)
        list.add(toIdx, item)
        viewModelScope.launch {
            cloudRepo.updatePlaylist(playlist.id, trackIds = list)
        }
    }

    fun exportPlaylistText(playlist: AppPlaylist, format: PlaylistCodec.Format): String {
        val map = allKnownTracks.value.associateBy { it.id }
        val resolved = playlist.trackIds.mapNotNull { map[it] }
        return PlaylistCodec.export(playlist, resolved, format)
    }

    fun importPlaylistFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                val parsed = PlaylistCodec.parseImport(raw, "Imported Mix")
                val known = allKnownTracks.value
                val matchedIds = parsed.trackUrisOrTitles.mapNotNull { ref ->
                    known.firstOrNull {
                        it.id == ref || it.mediaUri == ref || it.title.equals(ref, ignoreCase = true)
                    }?.id
                }
                cloudRepo.createPlaylist(parsed.name, "Imported from local file", matchedIds)
                showBanner("Imported '${parsed.name}' (${matchedIds.size} matched tracks)")
            }.onFailure {
                showBanner("Failed to import playlist file: ${it.localizedMessage}")
            }
        }
    }

    // --- DOWNLOAD MANAGER ---
    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            downloadManager.enqueueDownload(track, preferences.value.downloadQuality)
                .onSuccess { showBanner("Queued authorized download: ${track.title}") }
                .onFailure { showBanner(it.localizedMessage ?: "Download not authorized") }
            refreshStorageBytes()
        }
    }

    fun pauseDownload(trackId: String) = downloadManager.pauseDownload(trackId)
    fun resumeDownload(trackId: String) = downloadManager.resumeOrRetryDownload(trackId)
    fun removeDownload(trackId: String) {
        downloadManager.removeDownload(trackId)
        refreshStorageBytes()
    }

    fun setDownloadQuality(quality: AudioQuality) {
        viewModelScope.launch { preferencesStore.setDownloadQuality(quality) }
    }

    fun refreshStorageBytes() {
        viewModelScope.launch {
            _downloadedBytes.value = downloadManager.getDownloadedStorageBytes()
        }
    }

    // --- GEMINI AI ASSISTANT ---
    fun sendAiPrompt(promptText: String) {
        val clean = promptText.trim()
        if (clean.isBlank() || _aiBusy.value) return

        val userMsg = AiAssistantMessage(
            id = "u_${System.currentTimeMillis()}",
            fromUser = true,
            text = clean
        )
        _aiMessages.update { it + userMsg }
        _aiBusy.value = true

        viewModelScope.launch {
            val recentArtists = (historyState.value as? CloudUiState.Success)?.data
                ?.sortedByDescending { it.playCount }
                ?.map { it.artist }
                .orEmpty()
            val available = allKnownTracks.value

            val cmdResult = geminiRepo.interpretMusicCommand(
                userPrompt = clean,
                availableTracks = available,
                currentTrack = playbackState.value.currentTrack,
                recentArtists = recentArtists
            )

            cmdResult.onSuccess { cmd ->
                var selectedTracks = available.filter { it.id in cmd.matchedTrackIds }

                // If Gemini recommends searching the open catalog or user requested a specific genre/mood
                if (selectedTracks.isEmpty() || cmd.actionType == "SEARCH_CATALOG" || cmd.actionType == "CREATE_PLAYLIST") {
                    val catalogRes = archiveRepo.fetchAuthorizedOpenMusic(cmd.searchKeywords, cmd.playlistTitle)
                    catalogRes.getOrNull()?.let { fetched ->
                        if (fetched.isNotEmpty()) {
                            _catalogTracks.update { prev -> (fetched + prev).distinctBy { it.id } }
                            if (selectedTracks.isEmpty()) {
                                selectedTracks = fetched
                            }
                        }
                    }
                }

                if (selectedTracks.isEmpty() && available.isNotEmpty()) {
                    selectedTracks = available.take(3)
                }

                var createdName: String? = null
                if (cmd.actionType == "CREATE_PLAYLIST" && selectedTracks.isNotEmpty()) {
                    val title = cmd.playlistTitle.ifBlank { "AI Mix" }
                    cloudRepo.createPlaylist(
                        name = title,
                        description = "Generated by MUSICVERSE AI: $clean",
                        trackIds = selectedTracks.map { it.id },
                        smartPlaylist = true,
                        smartRule = clean
                    )
                    createdName = title
                }

                if (cmd.actionType == "PLAY_MIX" && selectedTracks.isNotEmpty()) {
                    playbackController.playQueue(selectedTracks, 0)
                }

                val aiMsg = AiAssistantMessage(
                    id = "a_${System.currentTimeMillis()}",
                    fromUser = false,
                    text = cmd.assistantReply,
                    suggestedTracks = selectedTracks,
                    createdPlaylistName = createdName
                )
                _aiMessages.update { it + aiMsg }
            }.onFailure { err ->
                // Transparent error propagation
                _aiMessages.update {
                    it + AiAssistantMessage(
                        id = "err_${System.currentTimeMillis()}",
                        fromUser = false,
                        text = "AI Assistant error: ${err.localizedMessage ?: "Unable to reach Gemini via Firebase AI Logic."}"
                    )
                }
            }
            _aiBusy.value = false
        }
    }

    fun streamAiExplanation(question: String) {
        val clean = question.trim()
        if (clean.isBlank() || _aiBusy.value) return
        val userMsg = AiAssistantMessage(id = "u_${System.currentTimeMillis()}", fromUser = true, text = clean)
        val replyId = "a_${System.currentTimeMillis() + 1}"
        _aiMessages.update {
            it + userMsg + AiAssistantMessage(id = replyId, fromUser = false, text = "", isStreaming = true)
        }
        _aiBusy.value = true

        viewModelScope.launch {
            val recentGenres = (historyState.value as? CloudUiState.Success)?.data
                ?.map { it.genre }.orEmpty()
            try {
                geminiRepo.streamConversationalReply(clean, playbackState.value.currentTrack, recentGenres)
                    .collect { partial ->
                        _aiMessages.update { list ->
                            list.map { m -> if (m.id == replyId) m.copy(text = partial, isStreaming = true) else m }
                        }
                    }
                _aiMessages.update { list ->
                    list.map { m -> if (m.id == replyId) m.copy(isStreaming = false) else m }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiMessages.update { list ->
                    list.map { m ->
                        if (m.id == replyId) {
                            m.copy(
                                text = "Streaming error: ${e.localizedMessage ?: "Connection failed"}",
                                isStreaming = false
                            )
                        } else m
                    }
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    // --- ACCOUNT, PRIVACY, SUBSCRIPTION & UPDATES ---
    fun updatePrivacyAndQuality(
        analyticsEnabled: Boolean? = null,
        personalizationEnabled: Boolean? = null,
        preferredQuality: AudioQuality? = null,
        subscriptionTier: String? = null
    ) {
        viewModelScope.launch {
            if (preferredQuality != null) {
                preferencesStore.setDownloadQuality(preferredQuality)
            }
            cloudRepo.updateProfilePreferences(
                subscriptionTier = subscriptionTier,
                analyticsEnabled = analyticsEnabled,
                personalizationEnabled = personalizationEnabled,
                preferredQuality = preferredQuality?.name
            ).onFailure {
                showBanner("Failed saving preference: ${it.localizedMessage}")
            }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            cloudRepo.deleteAccountAndData()
                .onSuccess { onDeleted() }
                .onFailure {
                    showBanner("Account deletion requires recent sign-in: ${it.localizedMessage}")
                }
        }
    }

    fun checkAppUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _updateChecking.value = true
            updateChecker.checkForUpdates()
                .onSuccess { info ->
                    _updateInfo.value = info
                    if (!silent && !info.updateAvailable) {
                        showBanner("MUSICVERSE AI ${info.currentVersionName} is up to date.")
                    }
                }
                .onFailure { err ->
                    if (!silent) {
                        showBanner("Update check failed: ${err.localizedMessage}")
                    }
                }
            _updateChecking.value = false
        }
    }

    fun openUpdateInstall(info: AppUpdateInfo) {
        updateChecker.openOfficialUpdatePage(info.downloadUrl)
    }

    fun remindLaterUpdate(versionCode: Int) {
        viewModelScope.launch {
            preferencesStore.setDismissedUpdateVersion(versionCode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
    }
}
