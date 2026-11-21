package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppPlaylist
import com.example.domain.model.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextMuted

@Composable
fun SearchScreen(
    allTracks: List<Track>,
    playlists: List<AppPlaylist>,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRequestAddToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onSearchOnlineCatalog: (String) -> Unit,
    onSendToAiAssistant: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                query = spoken
            }
        }
    }

    val filteredTracks = remember(query, allTracks) {
        val q = query.trim()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true) ||
                it.genre.contains(q, ignoreCase = true)
        }
    }

    val filteredPlaylists = remember(query, playlists) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else playlists.filter { it.name.contains(q, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Search", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    placeholder = { Text("Songs, albums, artists, playlists...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CyberCyan) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = TextMuted)
                                }
                            }
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, mood, or artist")
                                    }
                                    runCatching { voiceLauncher.launch(intent) }
                                }
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = NeonViolet)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (query.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSearchOnlineCatalog(query) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                        ) {
                            Text("Search Open Catalog")
                        }
                        OutlinedButton(
                            onClick = { onSendToAiAssistant(query) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CyberCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Ask AI DJ", color = CyberCyan)
                        }
                    }
                }
            }
        }

        if (filteredPlaylists.isNotEmpty()) {
            item {
                Text(
                    "Matching Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberCyan,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            items(filteredPlaylists, key = { "spl_${it.id}" }) { pl ->
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    glowColor = NeonViolet
                ) {
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("${pl.trackIds.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }

        item {
            Text(
                if (query.isBlank()) "All Indexed Tracks (${filteredTracks.size})"
                else "Track Results (${filteredTracks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        items(filteredTracks, key = { "sr_${it.id}" }) { track ->
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            TrackRowItem(
                track = track,
                isPlayingCurrent = track.id == currentTrackId,
                isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                onPlay = { onPlayTrack(track, filteredTracks) },
                onToggleFavorite = { onToggleFavorite(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onRequestAddToPlaylist(track) },
                onDownload = { onDownloadTrack(track) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
