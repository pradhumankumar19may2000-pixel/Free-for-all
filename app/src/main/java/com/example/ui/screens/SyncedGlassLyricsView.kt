package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PlayerPreferences
import com.example.data.lyrics.LrcParser
import com.example.domain.model.DynamicArtworkTheme
import com.example.domain.model.LyricsLine
import com.example.domain.model.Track
import com.example.domain.model.VisualizerFrame
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted

@Composable
fun SyncedGlassLyricsView(
    track: Track?,
    lyricsLines: List<LyricsLine>,
    lyricsStatus: String,
    positionMs: Long,
    preferences: PlayerPreferences,
    artworkTheme: DynamicArtworkTheme,
    visualizerFrame: VisualizerFrame,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onToggleLyricsOnly: (Boolean) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onSaveManualSync: (Track, List<LyricsLine>) -> Unit,
    onVisualizerPermissionGranted: () -> Unit
) {
    var karaokeMode by remember { mutableStateOf(false) }
    var showManualSyncDialog by remember { mutableStateOf(false) }

    val recordPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onVisualizerPermissionGranted()
    }

    val adjustedPositionMs = (positionMs + preferences.lyricsOffsetMs).coerceAtLeast(0L)
    val activeIdx = remember(lyricsLines, adjustedPositionMs) {
        LrcParser.findActiveLineIndex(lyricsLines, adjustedPositionMs)
    }

    val primaryGlow by animateColorAsState(
        targetValue = artworkTheme.primaryGlow,
        animationSpec = tween(500),
        label = "glowPrimary"
    )
    val highlightColor by animateColorAsState(
        targetValue = artworkTheme.lyricHighlight,
        animationSpec = tween(500),
        label = "highlight"
    )

    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && !karaokeMode) {
            listState.animateScrollToItem(index = activeIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        artworkTheme.backgroundTop.copy(alpha = 0.72f),
                        Color(0xDD070712)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        primaryGlow.copy(alpha = (0.35f + visualizerFrame.bassEnergy * 0.5f).coerceAtMost(0.95f)),
                        highlightColor.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // Real audio-reactive spectrum & waveform Canvas (no fake beats when capture is off)
        if (!preferences.reducedMotionLyrics) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bassPulse = if (visualizerFrame.isLiveAudioCapture) visualizerFrame.bassEnergy else 0f
                if (bassPulse > 0.02f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = (bassPulse * 0.36f).coerceIn(0.05f, 0.45f)),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.35f),
                            radius = size.maxDimension * (0.45f + bassPulse * 0.25f)
                        ),
                        radius = size.maxDimension * 0.7f,
                        center = Offset(size.width * 0.5f, size.height * 0.35f)
                    )
                }

                // Spectrum bars at bottom
                if (visualizerFrame.isLiveAudioCapture && !preferences.batterySaverVisualizer) {
                    val bars = visualizerFrame.spectrumBars
                    val barW = size.width / (bars.size * 1.5f)
                    val gap = barW * 0.5f
                    bars.forEachIndexed { i, mag ->
                        val h = (mag * size.height * 0.16f).coerceAtLeast(4f)
                        val x = gap + i * (barW + gap)
                        drawRoundRect(
                            color = highlightColor.copy(alpha = 0.35f + mag * 0.45f),
                            topLeft = Offset(x, size.height - h - 10f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }

                    // Waveform ribbon
                    val wave = visualizerFrame.waveform
                    if (wave.size > 2) {
                        val path = Path()
                        val midY = size.height * 0.84f
                        val stepX = size.width / (wave.size - 1)
                        wave.forEachIndexed { idx, sample ->
                            val x = idx * stepX
                            val y = midY + sample * 42f
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = primaryGlow.copy(alpha = 0.45f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Controls bar
            if (!preferences.lyricsOnlyKaraoke) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Synced Glass Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "$lyricsStatus · Offset ${preferences.lyricsOffsetMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = highlightColor
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!visualizerFrame.permissionGranted) {
                            FilterChip(
                                selected = false,
                                onClick = { recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                label = { Text("Live Audio FX") },
                                leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) }
                            )
                        }
                        IconButton(onClick = { onToggleBatterySaver(!preferences.batterySaverVisualizer) }) {
                            Icon(
                                Icons.Filled.BatterySaver,
                                contentDescription = "Battery saver visualizer",
                                tint = if (preferences.batterySaverVisualizer) CyberCyan else TextMuted
                            )
                        }
                        IconButton(onClick = { karaokeMode = !karaokeMode }) {
                            Icon(
                                if (karaokeMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Toggle Karaoke Mode",
                                tint = if (karaokeMode) highlightColor else Color.White
                            )
                        }
                    }
                }

                // Timing, Font Size, Manual Sync Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onAdjustOffset(-200L) }) { Text("-0.2s", color = TextMuted) }
                        TextButton(onClick = { onAdjustOffset(200L) }) { Text("+0.2s", color = TextMuted) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onFontScaleChange(preferences.lyricsFontScale - 0.1f) }) {
                            Icon(Icons.Filled.TextDecrease, contentDescription = "Decrease font", tint = TextMuted)
                        }
                        IconButton(onClick = { onFontScaleChange(preferences.lyricsFontScale + 0.1f) }) {
                            Icon(Icons.Filled.TextIncrease, contentDescription = "Increase font", tint = TextMuted)
                        }
                        if (track != null) {
                            IconButton(onClick = { showManualSyncDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Manual Lyrics Sync", tint = highlightColor)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onToggleLyricsOnly(false) }) {
                        Text("Show Controls", color = highlightColor)
                    }
                }
            }

            if (lyricsLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassCard(glowColor = primaryGlow) {
                        Text(
                            text = "No timed lyrics found yet for '${track?.title ?: "this track"}'.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap below to enter lyrics and tap-to-timestamp sync them in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        if (track != null) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showManualSyncDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Text("Manual Sync Editor")
                            }
                        }
                    }
                }
            } else if (karaokeMode) {
                // Full-screen Karaoke Stage Mode
                val prevLine = lyricsLines.getOrNull(activeIdx - 1)
                val currLine = lyricsLines.getOrNull(activeIdx) ?: lyricsLines.first()
                val nextLine = lyricsLines.getOrNull(activeIdx + 1)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (prevLine != null) {
                        Text(
                            text = prevLine.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.42f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    WordSyncedText(
                        line = currLine,
                        adjustedPositionMs = adjustedPositionMs,
                        fontSizeSp = (28f * preferences.lyricsFontScale).sp,
                        highlightColor = highlightColor,
                        baseColor = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )

                    if (nextLine != null) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = nextLine.text,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.62f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    FilterChip(
                        selected = preferences.lyricsOnlyKaraoke,
                        onClick = { onToggleLyricsOnly(!preferences.lyricsOnlyKaraoke) },
                        label = { Text(if (preferences.lyricsOnlyKaraoke) "Exit Lyrics-Only" else "Lyrics-Only Stage") }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("lyrics_scroll_list"),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyricsLines) { idx, line ->
                        val isActive = idx == activeIdx
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isActive) Color.White.copy(alpha = 0.14f) else Color.Transparent
                                )
                                .clickable {
                                    onSeekTo((line.timeMs - preferences.lyricsOffsetMs).coerceAtLeast(0L))
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (isActive) {
                                WordSyncedText(
                                    line = line,
                                    adjustedPositionMs = adjustedPositionMs,
                                    fontSizeSp = (21f * preferences.lyricsFontScale).sp,
                                    highlightColor = highlightColor,
                                    baseColor = Color.White.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Start
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    fontSize = (17f * preferences.lyricsFontScale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (idx < activeIdx) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.68f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualSyncDialog && track != null) {
        ManualLyricsSyncDialog(
            initialLines = lyricsLines,
            currentPositionMs = positionMs,
            onDismiss = { showManualSyncDialog = false },
            onSave = { synced ->
                onSaveManualSync(track, synced)
                showManualSyncDialog = false
            }
        )
    }
}

@Composable
private fun WordSyncedText(
    line: LyricsLine,
    adjustedPositionMs: Long,
    fontSizeSp: androidx.compose.ui.unit.TextUnit,
    highlightColor: Color,
    baseColor: Color,
    textAlign: TextAlign
) {
    if (line.words.isEmpty()) {
        Text(
            text = line.text,
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Bold,
            color = highlightColor,
            textAlign = textAlign
        )
    } else {
        val annotated = buildAnnotatedString {
            line.words.forEachIndexed { idx, word ->
                val active = adjustedPositionMs >= word.startMs
                withStyle(
                    SpanStyle(
                        color = if (active) highlightColor else baseColor,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                ) {
                    append(word.text)
                }
                if (idx < line.words.lastIndex) append(" ")
            }
        }
        Text(
            text = annotated,
            fontSize = fontSizeSp,
            lineHeight = fontSizeSp * 1.32f,
            textAlign = textAlign
        )
    }
}

@Composable
private fun ManualLyricsSyncDialog(
    initialLines: List<LyricsLine>,
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    onSave: (List<LyricsLine>) -> Unit
) {
    var rawText by remember {
        mutableStateOf(
            if (initialLines.isNotEmpty()) initialLines.joinToString("\n") { it.text }
            else "Line 1\nLine 2\nLine 3"
        )
    }
    var syncingMode by remember { mutableStateOf(false) }
    var cursorIdx by remember { mutableIntStateOf(0) }
    val stamped = remember { mutableStateListOf<LyricsLine>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (syncingMode) "Tap to Stamp Playback Time" else "Manual Lyrics Sync") },
        text = {
            if (!syncingMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste plain lyrics (one line per row), then tap 'Start Live Sync' while audio plays.")
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            } else {
                val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val currentLine = lines.getOrNull(cursorIdx)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stamped ${stamped.size} / ${lines.size} lines · Current playback: ${currentPositionMs / 1000}s")
                    if (currentLine != null) {
                        Text(currentLine, style = MaterialTheme.typography.titleMedium, color = CyberCyan)
                        Button(
                            onClick = {
                                stamped.add(LyricsLine(timeMs = currentPositionMs, text = currentLine))
                                cursorIdx++
                            }
                        ) {
                            Text("Stamp Current Line (${currentPositionMs}ms)")
                        }
                    } else {
                        Text("All lines stamped! Tap Save.")
                    }
                }
            }
        },
        confirmButton = {
            if (!syncingMode) {
                Button(onClick = {
                    stamped.clear()
                    cursorIdx = 0
                    syncingMode = true
                }) { Text("Start Live Sync") }
            } else {
                Button(onClick = { onSave(stamped.toList()) }, enabled = stamped.isNotEmpty()) {
                    Text("Save Synced LRC")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
