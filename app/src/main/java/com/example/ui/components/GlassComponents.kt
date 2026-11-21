package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.Track
import com.example.domain.model.TrackSourceType
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextMuted

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonViolet,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x28FFFFFF),
                        glowColor.copy(alpha = 0.14f),
                        Color(0xCC0D0E1A)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        glowColor.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
fun TrackArtwork(
    track: Track?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val fallbackRes = when (track?.artworkResName) {
        "img_hero_cosmos" -> R.drawable.img_hero_cosmos
        "img_album_acoustic_mist" -> R.drawable.img_album_acoustic_mist
        "img_ai_dj_avatar" -> R.drawable.img_ai_dj_avatar
        else -> R.drawable.img_album_neon_pulse
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(ObsidianElevated)
    ) {
        if (!track?.artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = track?.artworkUri,
                contentDescription = "${track?.title ?: "Track"} artwork",
                placeholder = painterResource(fallbackRes),
                error = painterResource(fallbackRes),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(fallbackRes),
                contentDescription = "${track?.title ?: "Track"} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TrackRowItem(
    track: Track,
    isPlayingCurrent: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .testTag("track_row_${track.id}"),
        color = if (isPlayingCurrent) NeonViolet.copy(alpha = 0.16f) else ObsidianCard.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isPlayingCurrent) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isPlayingCurrent) NeonViolet.copy(alpha = 0.55f) else GlassStroke.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                TrackArtwork(track = track, modifier = Modifier.size(54.dp), cornerRadius = 12.dp)
                if (isPlayingCurrent) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Now playing",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlayingCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlayingCurrent) CyberCyan else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SourceBadge(track)
                }
            }

            Text(
                text = formatDurationMs(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(end = 4.dp)
            )

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Like track",
                    tint = if (isFavorite) Color(0xFFEC4899) else TextMuted
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Track options",
                        tint = TextMuted
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Up Next Queue") },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    if (track.downloadAuthorized && onDownload != null) {
                        DropdownMenuItem(
                            text = { Text("Download Authorized Offline") },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceBadge(track: Track) {
    val (label, tint) = when {
        !track.localDownloadedPath.isNullOrBlank() -> "OFFLINE" to CyberCyan
        track.sourceType == TrackSourceType.BUNDLED -> "STUDIO" to NeonViolet
        track.sourceType == TrackSourceType.LOCAL -> "LOCAL" to ElectricBlue
        else -> "OPEN CC" to Color(0xFF22C55E)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSecs / 60L
    val secs = totalSecs % 60L
    return String.format("%d:%02d", mins, secs)
}
