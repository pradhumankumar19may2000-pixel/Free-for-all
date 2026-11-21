package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PlayerPreferences
import com.example.domain.model.DynamicArtworkTheme
import com.example.domain.model.EqualizerState
import com.example.domain.model.LyricsLine
import com.example.domain.model.RepeatModeState
import com.example.domain.model.Track
import com.example.domain.model.VisualizerFrame
import com.example.player.AudioEffectManager
import com.example.player.PlaybackUiState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.TrackArtwork
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextMuted

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    artworkTheme: DynamicArtworkTheme,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        artworkTheme.backgroundTop.copy(alpha = 0.94f),
                        Color(0xEE121428)
                    )
                )
            )
            .border(1.dp, artworkTheme.primaryGlow.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand)
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(track = track, modifier = Modifier.size(44.dp), cornerRadius = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${track.audioFormatLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_play_pause")
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = artworkTheme.lyricHighlight,
            trackColor = Color.White.copy(alpha = 0.14f)
        )
    }
}

@Composable
fun FullPlayerOverlay(
    playbackState: PlaybackUiState,
    artworkTheme: DynamicArtworkTheme,
    lyricsLines: List<LyricsLine>,
    lyricsStatus: String,
    preferences: PlayerPreferences,
    equalizerState: EqualizerState,
    audioEffectManager: AudioEffectManager,
    visualizerFrame: VisualizerFrame,
    isFavorite: Boolean,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetCrossfade: (Int) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onJumpQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onToggleLyricsOnly: (Boolean) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onSaveManualSync: (Track, List<LyricsLine>) -> Unit,
    onVisualizerPermissionGranted: () -> Unit
) {
    BackHandler(onBack = onCollapse)

    val track = playbackState.currentTrack
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(artworkTheme.backgroundTop, Color(0xFF070712), Color(0xFF040409))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.testTag("collapse_player_button")) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize player", tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = artworkTheme.lyricHighlight)
                Text(
                    track?.audioFormatLabel ?: "MUSICVERSE AUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            IconButton(onClick = { showSleepDialog = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (playbackState.sleepTimer.active) CyberCyan else Color.White
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = artworkTheme.lyricHighlight
        ) {
            listOf("Player", "Glass Lyrics", "Equalizer", "Queue").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            label,
                            color = if (selectedTab == idx) artworkTheme.lyricHighlight else TextMuted,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    TrackArtwork(
                        track = track,
                        modifier = Modifier
                            .size(250.dp)
                            .border(
                                1.5.dp,
                                artworkTheme.primaryGlow.copy(alpha = 0.65f),
                                RoundedCornerShape(26.dp)
                            ),
                        cornerRadius = 26.dp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = track?.title ?: "Select a track",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = track?.let { "${it.artist} · ${it.album}" } ?: "MUSICVERSE AI",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playbackState.sleepTimer.active) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sleep timer: ${playbackState.sleepTimer.remainingSeconds / 60}m ${playbackState.sleepTimer.remainingSeconds % 60}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                        }
                    }

                    // Progress Scrubber
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val dur = playbackState.durationMs.coerceAtLeast(1L)
                        Slider(
                            value = (playbackState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac -> onSeekTo((frac * dur).toLong()) },
                            colors = SliderDefaults.colors(
                                thumbColor = artworkTheme.lyricHighlight,
                                activeTrackColor = artworkTheme.primaryGlow
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDurationMs(playbackState.positionMs), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            Text(formatDurationMs(playbackState.durationMs), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        }
                    }

                    // Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) CyberCyan else TextMuted
                            )
                        }
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(artworkTheme.primaryGlow, artworkTheme.secondaryGlow)))
                                .clickable(onClick = onTogglePlayPause)
                                .testTag("full_player_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = onCycleRepeat) {
                            Icon(
                                imageVector = if (playbackState.repeatMode == RepeatModeState.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repeat mode",
                                tint = if (playbackState.repeatMode != RepeatModeState.OFF) CyberCyan else TextMuted
                            )
                        }
                    }

                    // Utility Row: Like, Speed, Crossfade, Download
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (track != null) {
                            IconButton(onClick = { onToggleFavorite(track) }) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color(0xFFEC4899) else TextMuted
                                )
                            }
                        }
                        TextButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = CyberCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("${playbackState.playbackSpeed}x · CF ${playbackState.crossfadeSeconds}s", color = Color.White)
                        }
                        if (track != null && track.downloadAuthorized) {
                            IconButton(onClick = { onDownloadTrack(track) }) {
                                Icon(Icons.Filled.Download, contentDescription = "Download authorized track", tint = CyberCyan)
                            }
                        }
                    }
                }
            }

            1 -> {
                Box(modifier = Modifier.weight(1f)) {
                    SyncedGlassLyricsView(
                        track = track,
                        lyricsLines = lyricsLines,
                        lyricsStatus = lyricsStatus,
                        positionMs = playbackState.positionMs,
                        preferences = preferences,
                        artworkTheme = artworkTheme,
                        visualizerFrame = visualizerFrame,
                        onSeekTo = onSeekTo,
                        onAdjustOffset = onAdjustLyricsOffset,
                        onFontScaleChange = onFontScaleChange,
                        onToggleLyricsOnly = onToggleLyricsOnly,
                        onToggleBatterySaver = onToggleBatterySaver,
                        onSaveManualSync = onSaveManualSync,
                        onVisualizerPermissionGranted = onVisualizerPermissionGranted
                    )
                }
            }

            2 -> {
                EqualizerTab(
                    equalizerState = equalizerState,
                    audioEffectManager = audioEffectManager,
                    modifier = Modifier.weight(1f)
                )
            }

            3 -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(playbackState.queue) { idx, qTrack ->
                        val active = idx == playbackState.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) NeonViolet.copy(alpha = 0.2f) else ObsidianCard)
                                .clickable { onJumpQueueIndex(idx) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, color = CyberCyan, modifier = Modifier.width(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(qTrack.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(qTrack.artist, style = MaterialTheme.typography.bodyMedium, color = TextMuted, maxLines = 1)
                            }
                            IconButton(onClick = { if (idx > 0) onMoveQueueItem(idx, idx - 1) }, enabled = idx > 0) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", tint = TextMuted)
                            }
                            IconButton(onClick = { if (idx < playbackState.queue.lastIndex) onMoveQueueItem(idx, idx + 1) }, enabled = idx < playbackState.queue.lastIndex) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", tint = TextMuted)
                            }
                            IconButton(onClick = { onRemoveQueueItem(idx) }, enabled = playbackState.queue.size > 1) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove from queue", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playback will smoothly fade out and pause when the timer completes.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (mins, label) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onStartSleepTimer(mins)
                                    showSleepDialog = false
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed & Crossfade") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playback Speed: ${playbackState.playbackSpeed}x")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                            FilterChip(
                                selected = playbackState.playbackSpeed == spd,
                                onClick = { onSetSpeed(spd) },
                                label = { Text("${spd}x") }
                            )
                        }
                    }
                    Text("Crossfade Duration: ${playbackState.crossfadeSeconds}s")
                    Slider(
                        value = playbackState.crossfadeSeconds.toFloat(),
                        onValueChange = { onSetCrossfade(it.toInt()) },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSpeedDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun EqualizerTab(
    equalizerState: EqualizerState,
    audioEffectManager: AudioEffectManager,
    modifier: Modifier = Modifier
) {
    var showSavePreset by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LiquidGlassCard(glowColor = CyberCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hardware DSP Equalizer", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            if (equalizerState.available) "Attached to active ExoPlayer session"
                            else "Start playback to attach device AudioEffect DSP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = equalizerState.enabled,
                        onCheckedChange = { audioEffectManager.setEnabled(it) }
                    )
                }
            }
        }

        item {
            Text("Presets", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(audioEffectManager.builtInPresets) { preset ->
                    FilterChip(
                        selected = equalizerState.activePresetName == preset,
                        onClick = { audioEffectManager.applyBuiltInPreset(preset) },
                        label = { Text(preset) }
                    )
                }
                items(equalizerState.customPresets) { custom ->
                    FilterChip(
                        selected = equalizerState.activePresetName == custom,
                        onClick = { audioEffectManager.loadCustomPreset(custom) },
                        label = { Text(custom) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showSavePreset = true }) {
                Text("Save Custom Preset")
            }
        }

        item {
            LiquidGlassCard(glowColor = NeonViolet) {
                Text("Frequency Bands", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                equalizerState.bands.forEach { band ->
                    val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz"
                    val dbLabel = String.format("%+.1f dB", band.currentLevelMb / 100f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabel, style = MaterialTheme.typography.labelMedium, color = CyberCyan, modifier = Modifier.width(64.dp))
                        Slider(
                            value = band.currentLevelMb.toFloat(),
                            onValueChange = { audioEffectManager.setBandLevel(band.index, it.toInt()) },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            modifier = Modifier.weight(1f),
                            enabled = equalizerState.enabled
                        )
                        Text(dbLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(58.dp))
                    }
                }
            }
        }

        item {
            LiquidGlassCard(glowColor = CyberCyan) {
                Text("Bass Boost (${equalizerState.bassBoostStrength / 10}%)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Slider(
                    value = equalizerState.bassBoostStrength.toFloat(),
                    onValueChange = { audioEffectManager.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = equalizerState.enabled
                )
                Spacer(Modifier.height(8.dp))
                Text("Spatial Virtualizer (${equalizerState.virtualizerStrength / 10}%)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Slider(
                    value = equalizerState.virtualizerStrength.toFloat(),
                    onValueChange = { audioEffectManager.setVirtualizer(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = equalizerState.enabled
                )
                Spacer(Modifier.height(8.dp))
                Text("Treble Boost (${String.format("%+.1f dB", equalizerState.trebleBoostMb / 100f)})", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Slider(
                    value = equalizerState.trebleBoostMb.toFloat(),
                    onValueChange = { audioEffectManager.setTrebleBoost(it.toInt()) },
                    valueRange = -1500f..1500f,
                    enabled = equalizerState.enabled
                )
            }
        }
    }

    if (showSavePreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save Custom EQ Preset") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    audioEffectManager.saveCustomPreset(name)
                    showSavePreset = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePreset = false }) { Text("Cancel") }
            }
        )
    }
}
