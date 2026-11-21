package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppPlaylist
import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.ArtistGroup
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.ListeningHistoryEntry
import com.example.domain.model.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextMuted
import java.util.Calendar

@Composable
fun HomeScreen(
    userDisplayName: String,
    allTracks: List<Track>,
    catalogTracks: List<Track>,
    playlists: List<AppPlaylist>,
    history: List<ListeningHistoryEntry>,
    downloads: List<DownloadItem>,
    artists: List<ArtistGroup>,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    updateInfo: AppUpdateInfo?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRequestAddToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenPlaylistTab: () -> Unit
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
    val recentlyPlayed = remember(history, trackMap, allTracks) {
        val resolved = history.sortedByDescending { it.lastPlayedAt?.seconds ?: 0L }
            .mapNotNull { entry -> trackMap[entry.trackId] }
        resolved.ifEmpty { allTracks.take(4) }
    }
    val downloadedTracks = remember(downloads, trackMap) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
            .mapNotNull { trackMap[it.trackId] }
    }
    val trendingTracks = remember(catalogTracks, allTracks) {
        (catalogTracks + allTracks).distinctBy { it.id }.take(6)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Text(
                        text = userDisplayName.ifBlank { "MUSICVERSE Listener" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberCyan
                    )
                }

                IconButton(
                    onClick = onOpenAccountSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(ObsidianCard)
                        .border(
                            1.dp,
                            if (updateInfo?.updateAvailable == true) CyberCyan else GlassStroke,
                            CircleShape
                        )
                        .testTag("account_settings_button")
                ) {
                    Icon(
                        imageVector = if (updateInfo?.updateAvailable == true) Icons.Filled.SystemUpdate else Icons.Filled.Person,
                        contentDescription = "Account, Settings & Updates",
                        tint = if (updateInfo?.updateAvailable == true) CyberCyan else Color.White
                    )
                }
            }
        }

        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(R.drawable.img_hero_cosmos),
                    contentDescription = "MusicVerse AI Studio Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xE605050B), Color(0x880D0B26), Color(0x22000000))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MUSICVERSE AI STUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                        Text(
                            text = "Sync Lyrics, Liquid Glass & Gemini DJ",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    allTracks.firstOrNull()?.let { onPlayTrack(it, allTracks) }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                modifier = Modifier.testTag("hero_play_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play Mix")
                            }
                            Button(
                                onClick = onOpenAiAssistant,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("hero_ai_dj_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ask AI DJ")
                            }
                        }
                    }
                }
            }
        }

        // Recently Played
        item {
            SectionHeader("Recently Played")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                    SquareTrackCard(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        onClick = { onPlayTrack(track, recentlyPlayed) }
                    )
                }
            }
        }

        // Made For You / Smart Mix Card
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { onOpenAiAssistant() },
                glowColor = CyberCyan
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_ai_dj_avatar),
                        contentDescription = "AI DJ Orb",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Made For You · Gemini AI DJ", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "Generate mood playlists, Bhojpuri/Hindi mixes, or 30-min meditation sets by voice or text.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Trending & New Releases
        item {
            SectionHeader("Trending & New Releases")
        }
        items(trendingTracks.take(5), key = { "trend_${it.id}" }) { track ->
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            TrackRowItem(
                track = track,
                isPlayingCurrent = track.id == currentTrackId,
                isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                onPlay = { onPlayTrack(track, trendingTracks) },
                onToggleFavorite = { onToggleFavorite(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onRequestAddToPlaylist(track) },
                onDownload = { onDownloadTrack(track) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Recommended Artists
        if (artists.isNotEmpty()) {
            item {
                SectionHeader("Recommended Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artists.take(8), key = { "artist_${it.name}" }) { artist ->
                        Column(
                            modifier = Modifier
                                .width(108.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    artist.tracks.firstOrNull()?.let { onPlayTrack(it, artist.tracks) }
                                }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TrackArtwork(
                                track = artist.tracks.firstOrNull(),
                                modifier = Modifier.size(88.dp),
                                cornerRadius = 44.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artist.tracks.size} tracks",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Your Cloud Playlists
        item {
            SectionHeader("Your Cloud Playlists")
            if (playlists.isEmpty()) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable { onOpenPlaylistTab() },
                    glowColor = ElectricBlue
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = CyberCyan)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "No cloud playlists yet. Open Library to create or import M3U/CSV/JSON playlists.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { "pl_${it.id}" }) { pl ->
                        LiquidGlassCard(
                            modifier = Modifier
                                .width(172.dp)
                                .clickable {
                                    val resolved = pl.trackIds.mapNotNull { trackMap[it] }
                                    resolved.firstOrNull()?.let { onPlayTrack(it, resolved) }
                                        ?: onOpenPlaylistTab()
                                },
                            glowColor = NeonViolet
                        ) {
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${pl.trackIds.size} tracks · Cloud Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                }
            }
        }

        // Downloaded Music
        item {
            SectionHeader("Downloaded Offline Music")
            if (downloadedTracks.isEmpty()) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    glowColor = NeonViolet
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = NeonViolet)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Authorized Creative Commons & Public Domain tracks you download appear here for offline playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(downloadedTracks, key = { "dl_${it.id}" }) { track ->
                        SquareTrackCard(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, downloadedTracks) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SquareTrackCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(
                1.dp,
                if (isPlaying) CyberCyan else GlassStroke.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        TrackArtwork(
            track = track,
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
            cornerRadius = 12.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isPlaying) CyberCyan else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
