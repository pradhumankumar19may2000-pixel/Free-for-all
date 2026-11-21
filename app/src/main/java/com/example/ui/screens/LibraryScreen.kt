package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.playlist.PlaylistCodec
import com.example.domain.model.AppPlaylist
import com.example.domain.model.AudioQuality
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.ListeningHistoryEntry
import com.example.domain.model.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted

@Composable
fun LibraryScreen(
    allTracks: List<Track>,
    localTracks: List<Track>,
    playlists: List<AppPlaylist>,
    history: List<ListeningHistoryEntry>,
    downloads: List<DownloadItem>,
    downloadedBytes: Long,
    downloadQuality: AudioQuality,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onScanDeviceMusic: () -> Unit,
    onImportAudioUri: (android.net.Uri) -> Unit,
    onImportPlaylistUri: (android.net.Uri) -> Unit,
    onExportPlaylist: (AppPlaylist, PlaylistCodec.Format) -> String,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onDuplicatePlaylist: (AppPlaylist) -> Unit,
    onRemoveFromPlaylist: (AppPlaylist, String) -> Unit,
    onReorderPlaylistTrack: (AppPlaylist, Int, Int) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRequestAddToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onSelectDownloadQuality: (AudioQuality) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<AppPlaylist?>(null) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var exportingPlaylist by remember { mutableStateOf<AppPlaylist?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
        if (granted) onScanDeviceMusic()
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onImportAudioUri(uri)
        }
    }

    val pickPlaylistLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPlaylistUri(uri)
    }

    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
    val favoriteTracks = remember(allTracks, favoriteIds) {
        allTracks.filter {
            val safeId = it.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            safeId in favoriteIds || it.id in favoriteIds
        }
    }
    val mostPlayedTracks = remember(history, trackMap) {
        history.sortedByDescending { it.playCount }.mapNotNull { trackMap[it.trackId] }
    }
    val recentlyAddedTracks = remember(allTracks) {
        allTracks.sortedByDescending { it.dateAddedSec }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Library & Studio",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = CyberCyan,
            edgePadding = 16.dp
        ) {
            listOf("Local & Scanner", "Playlists", "Favorites & Smart", "Downloads").forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == idx) CyberCyan else TextMuted,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                val folders = remember(allTracks) {
                    listOf("All") + allTracks.map { it.folderName }.distinct()
                }
                val visibleTracks = remember(allTracks, selectedFolder) {
                    if (selectedFolder == "All") allTracks
                    else allTracks.filter { it.folderName == selectedFolder }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LiquidGlassCard(glowColor = ElectricBlue) {
                            Text("Device Audio Scanner", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (hasAudioPerm) "Indexed ${localTracks.size} device tracks + ${allTracks.size} total tracks."
                                else "Grant audio library access to scan MP3/FLAC/AAC/WAV files on your device, or pick any audio file directly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (hasAudioPerm) onScanDeviceMusic()
                                        else permLauncher.launch(audioPermission)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                    modifier = Modifier.testTag("scan_device_music_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (hasAudioPerm) "Rescan Device" else "Scan Audio")
                                }
                                OutlinedButton(
                                    onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = CyberCyan)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pick Audio", color = CyberCyan)
                                }
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(folders) { folder ->
                                FilterChip(
                                    selected = selectedFolder == folder,
                                    onClick = { selectedFolder = folder },
                                    label = { Text(folder) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                                        selectedLabelColor = CyberCyan
                                    )
                                )
                            }
                        }
                    }

                    items(visibleTracks, key = { "lib_${it.id}" }) { track ->
                        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        TrackRowItem(
                            track = track,
                            isPlayingCurrent = track.id == currentTrackId,
                            isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                            onPlay = { onPlayTrack(track, visibleTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onRequestAddToPlaylist(track) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_playlist_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("New Playlist")
                            }
                            OutlinedButton(
                                onClick = {
                                    pickPlaylistLauncher.launch(
                                        arrayOf("audio/x-mpegurl", "text/csv", "application/json", "text/plain", "*/*")
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.FileOpen, contentDescription = null, tint = CyberCyan)
                                Spacer(Modifier.width(6.dp))
                                Text("Import File", color = CyberCyan)
                            }
                        }
                    }

                    if (playlists.isEmpty()) {
                        item {
                            LiquidGlassCard(glowColor = NeonViolet) {
                                Text("No cloud playlists yet", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text(
                                    "Create a playlist or ask MUSICVERSE AI Assistant to generate one. Playlists sync automatically across your signed-in devices.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        items(playlists, key = { "m_pl_${it.id}" }) { pl ->
                            val isExpanded = expandedPlaylistId == pl.id
                            val resolved = pl.trackIds.mapNotNull { trackMap[it] }
                            LiquidGlassCard(glowColor = if (pl.smartPlaylist) CyberCyan else NeonViolet) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedPlaylistId = if (isExpanded) null else pl.id },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pl.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        Text(
                                            "${resolved.size} tracks" + if (pl.smartPlaylist) " · Smart AI" else " · Cloud Synced",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            resolved.firstOrNull()?.let { onPlayTrack(it, resolved) }
                                        }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = CyberCyan)
                                        }
                                        IconButton(onClick = { renamingPlaylist = pl }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = TextMuted)
                                        }
                                        IconButton(onClick = { onDuplicatePlaylist(pl) }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", tint = TextMuted)
                                        }
                                        IconButton(onClick = { exportingPlaylist = pl }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export M3U/CSV/JSON", tint = TextMuted)
                                        }
                                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(Modifier.height(10.dp))
                                    if (resolved.isEmpty()) {
                                        Text("Empty playlist. Use the menu on any track to add songs.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                    } else {
                                        resolved.forEachIndexed { idx, t ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${idx + 1}. ${t.title} — ${t.artist}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { onPlayTrack(t, resolved) }
                                                )
                                                IconButton(
                                                    onClick = { if (idx > 0) onReorderPlaylistTrack(pl, idx, idx - 1) },
                                                    enabled = idx > 0
                                                ) {
                                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", tint = TextMuted)
                                                }
                                                IconButton(
                                                    onClick = { if (idx < resolved.lastIndex) onReorderPlaylistTrack(pl, idx, idx + 1) },
                                                    enabled = idx < resolved.lastIndex
                                                ) {
                                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", tint = TextMuted)
                                                }
                                                IconButton(onClick = { onRemoveFromPlaylist(pl, t.id) }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Remove from playlist", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("Liked Songs (${favoriteTracks.size})", style = MaterialTheme.typography.titleLarge, color = Color.White) }
                    items(favoriteTracks, key = { "fav_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isPlayingCurrent = track.id == currentTrackId,
                            isFavorite = true,
                            onPlay = { onPlayTrack(track, favoriteTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onRequestAddToPlaylist(track) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Most Played (Smart History)", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                    }
                    items(mostPlayedTracks.take(8), key = { "mp_${it.id}" }) { track ->
                        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        TrackRowItem(
                            track = track,
                            isPlayingCurrent = track.id == currentTrackId,
                            isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                            onPlay = { onPlayTrack(track, mostPlayedTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onRequestAddToPlaylist(track) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Recently Added", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    items(recentlyAddedTracks.take(6), key = { "ra_${it.id}" }) { track ->
                        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        TrackRowItem(
                            track = track,
                            isPlayingCurrent = track.id == currentTrackId,
                            isFavorite = safeId in favoriteIds || track.id in favoriteIds,
                            onPlay = { onPlayTrack(track, recentlyAddedTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onRequestAddToPlaylist(track) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }
                }
            }

            3 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LiquidGlassCard(glowColor = CyberCyan) {
                            Text("Authorized Offline Download Manager", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Storage used: ${String.format("%.2f", downloadedBytes / (1024f * 1024f))} MB · Only Creative Commons / Public Domain authorized tracks are downloaded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Preferred Download Quality", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AudioQuality.entries.forEach { q ->
                                    FilterChip(
                                        selected = downloadQuality == q,
                                        onClick = { onSelectDownloadQuality(q) },
                                        label = { Text(q.label) }
                                    )
                                }
                            }
                        }
                    }

                    if (downloads.isEmpty()) {
                        item {
                            Text(
                                "No downloads yet. Browse Explore -> Open Catalog and choose 'Download Authorized Offline' on any Creative Commons track.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    } else {
                        items(downloads, key = { "dlm_${it.trackId}" }) { item ->
                            LiquidGlassCard(glowColor = NeonViolet) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        Text(
                                            "${item.artist} · ${item.status.name} (${item.progressPercent}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (item.status == DownloadStatus.COMPLETED) SuccessMint else TextMuted
                                        )
                                        if (item.sha256Hex.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Verified, contentDescription = null, tint = CyberCyan, modifier = Modifier.padding(end = 4.dp))
                                                Text(
                                                    "SHA-256: ${item.sha256Hex.take(16)}...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CyberCyan
                                                )
                                            }
                                        }
                                        if (item.errorMessage != null) {
                                            Text(item.errorMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Row {
                                        when (item.status) {
                                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                                IconButton(onClick = { onPauseDownload(item.trackId) }) {
                                                    Icon(Icons.Filled.Pause, contentDescription = "Pause download", tint = CyberCyan)
                                                }
                                            }
                                            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                                                IconButton(onClick = { onResumeDownload(item.trackId) }) {
                                                    Icon(Icons.Filled.Refresh, contentDescription = "Resume download", tint = CyberCyan)
                                                }
                                            }
                                            DownloadStatus.COMPLETED -> {
                                                val t = trackMap[item.trackId]
                                                if (t != null) {
                                                    IconButton(onClick = { onPlayTrack(t, listOf(t)) }) {
                                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play offline", tint = SuccessMint)
                                                    }
                                                }
                                            }
                                        }
                                        IconButton(onClick = { onRemoveDownload(item.trackId) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove download", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = CyberCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Cloud Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist Name") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (optional)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onCreatePlaylist(name, desc)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    renamingPlaylist?.let { pl ->
        var newName by remember(pl.id) { mutableStateOf(pl.name) }
        AlertDialog(
            onDismissRequest = { renamingPlaylist = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New Name") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onRenamePlaylist(pl.id, newName)
                        renamingPlaylist = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPlaylist = null }) { Text("Cancel") }
            }
        )
    }

    exportingPlaylist?.let { pl ->
        AlertDialog(
            onDismissRequest = { exportingPlaylist = null },
            title = { Text("Export '${pl.name}'") },
            text = { Text("Choose a standard playlist export format to share or save:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaylistCodec.Format.entries.forEach { fmt ->
                        TextButton(onClick = {
                            val payload = onExportPlaylist(pl, fmt)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${pl.name}.${fmt.extension}")
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export ${fmt.name}"))
                            exportingPlaylist = null
                        }) {
                            Text(fmt.name)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { exportingPlaylist = null }) { Text("Close") }
            }
        )
    }
}
