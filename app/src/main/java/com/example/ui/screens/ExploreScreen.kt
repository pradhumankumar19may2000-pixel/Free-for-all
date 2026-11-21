package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlbumGroup
import com.example.domain.model.GenreCategory
import com.example.domain.model.Track
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextMuted

@Composable
fun ExploreScreen(
    genres: List<GenreCategory>,
    catalogTracks: List<Track>,
    catalogLoading: Boolean,
    albums: List<AlbumGroup>,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onSelectGenre: (GenreCategory) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRequestAddToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Explore & Genres", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text(
                    "Stream & download authorized Creative Commons / Public Domain music by genre",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        // 2-column genre cards rendered cleanly inside LazyColumn rows
        val rows = genres.chunked(2)
        items(rows) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { genre ->
                    val accent = Color(genre.accentHex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(86.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.34f), ObsidianCard)
                                )
                            )
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .clickable { onSelectGenre(genre) }
                            .padding(14.dp)
                            .testTag("genre_${genre.id}")
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart)) {
                            Text(
                                text = genre.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = genre.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Albums Browser
        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums, key = { "album_${it.id}" }) { album ->
                        Column(
                            modifier = Modifier
                                .width(142.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianCard)
                                .clickable {
                                    album.tracks.firstOrNull()?.let { onPlayTrack(it, album.tracks) }
                                }
                                .padding(10.dp)
                        ) {
                            TrackArtwork(
                                track = album.tracks.firstOrNull(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                cornerRadius = 12.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${album.artist} · ${album.tracks.size} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Open Catalog Stream Results
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Authorized Open Catalog", style = MaterialTheme.typography.titleLarge, color = Color.White)
                if (catalogLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberCyan, strokeWidth = 2.dp)
                }
            }
        }

        if (!catalogLoading && catalogTracks.isEmpty()) {
            item {
                Text(
                    text = "Tap any genre above to browse real Creative Commons / Public Domain streams from Internet Archive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        } else {
            items(catalogTracks, key = { "cat_${it.id}" }) { track ->
                val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                TrackRowItem(
                    track = track,
                    isPlayingCurrent = track.id == currentTrackId,
                    isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                    onPlay = { onPlayTrack(track, catalogTracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onRequestAddToPlaylist(track) },
                    onDownload = { onDownloadTrack(track) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}
