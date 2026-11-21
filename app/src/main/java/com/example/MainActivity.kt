package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.download.TrackDownloadManager
import com.example.data.local.LocalMediaScanner
import com.example.data.local.MusicVerseDatabase
import com.example.data.local.PreferencesStore
import com.example.data.lyrics.LrcLibApi
import com.example.data.remote.AppUpdateChecker
import com.example.data.remote.InternetArchiveRepository
import com.example.data.repository.GeminiAssistantRepository
import com.example.data.repository.MusicCloudRepository
import com.example.domain.model.Track
import com.example.player.AudioEffectManager
import com.example.player.AudioVisualizerEngine
import com.example.security.configureAppCheck
import com.example.ui.screens.AccountAndSettingsDialog
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.FullPlayerOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MiniPlayerBar
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.signOutUser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.MusicVerseTheme
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.MusicVerseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize App Check immediately before any Firebase calls or UI setup
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MusicVerseTheme {
                AppContent()
            }
        }
    }
}

private data class NavDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    NavDestination(R.string.tab_explore, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    NavDestination(R.string.tab_search, Icons.Filled.Search, Icons.Outlined.Search, "nav_search"),
    NavDestination(R.string.tab_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_library"),
    NavDestination(R.string.tab_assistant, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_dj")
)

@Composable
private fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            MusicVerseMainShell(
                currentUser = user,
                onSignedOut = { currentUser = Firebase.auth.currentUser }
            )
        }
    }
}

@Composable
private fun MusicVerseMainShell(
    currentUser: FirebaseUser,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MusicVerseViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val db = MusicVerseDatabase.getInstance(appContext)
                MusicVerseViewModel(
                    appContext = appContext,
                    currentUserId = currentUser.uid,
                    userDisplayName = currentUser.displayName.orEmpty(),
                    userEmail = currentUser.email.orEmpty(),
                    userPhotoUrl = currentUser.photoUrl?.toString(),
                    cloudRepo = MusicCloudRepository(appContext),
                    geminiRepo = GeminiAssistantRepository(),
                    localScanner = LocalMediaScanner(appContext),
                    archiveRepo = InternetArchiveRepository(),
                    lrcLibApi = LrcLibApi.create(),
                    cachedLyricsDao = db.cachedLyricsDao(),
                    downloadManager = TrackDownloadManager(appContext, db.downloadDao()),
                    preferencesStore = PreferencesStore(appContext),
                    updateChecker = AppUpdateChecker(appContext),
                    audioEffectManager = AudioEffectManager(db.eqPresetDao()),
                    visualizerEngine = AudioVisualizerEngine(appContext)
                )
            }
        }
    )

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val allTracks by viewModel.allKnownTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val catalogTracks by viewModel.catalogTracks.collectAsStateWithLifecycle()
    val catalogLoading by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadsState.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val lyricsLines by viewModel.lyricsLines.collectAsStateWithLifecycle()
    val lyricsStatus by viewModel.lyricsStatus.collectAsStateWithLifecycle()
    val artworkTheme by viewModel.artworkTheme.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateChecking by viewModel.updateChecking.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val playlistsState by viewModel.cloudPlaylistsState.collectAsStateWithLifecycle()
    val favoritesState by viewModel.favoritesState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()

    val userProfile = (profileState as? CloudUiState.Success)?.data
    val playlists = (playlistsState as? CloudUiState.Success)?.data.orEmpty()
    val favorites = (favoritesState as? CloudUiState.Success)?.data.orEmpty()
    val history = (historyState as? CloudUiState.Success)?.data.orEmpty()
    val favoriteIds = remember(favorites) { favorites.map { it.trackId }.toSet() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var trackPendingPlaylist by remember { mutableStateOf<Track?>(null) }
    var showAutoUpdateBannerDialog by rememberSaveable { mutableStateOf(false) }
    var requestedNotificationPermOnce by rememberSaveable { mutableStateOf(false) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(updateInfo, preferences.dismissedUpdateVersionCode) {
        val info = updateInfo
        if (info != null && info.updateAvailable && info.latestVersionCode > preferences.dismissedUpdateVersionCode) {
            showAutoUpdateBannerDialog = true
        }
    }

    val playWithNotificationCheck: (Track, List<Track>) -> Unit = { track, queue ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedNotificationPermOnce) {
            requestedNotificationPermOnce = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.playTrack(track, queue)
    }

    if (selectedTab != 0 && !isPlayerExpanded && !showAccountDialog) {
        BackHandler { selectedTab = 0 }
    }

    val currentTrack = playbackState.currentTrack
    val currentIsFavorite = remember(currentTrack, favoriteIds) {
        currentTrack?.let {
            val safe = it.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            safe in favoriteIds || it.id in favoriteIds
        } ?: false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(artworkTheme.backgroundTop.copy(alpha = 0.45f), ObsidianVoid, ObsidianVoid)
                )
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = ObsidianSurface.copy(alpha = 0.94f),
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Destinations.forEachIndexed { index, dest ->
                            val selected = selectedTab == index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = NeonViolet.copy(alpha = 0.28f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = if (currentTrack != null) 72.dp else 8.dp)
                        )
                    },
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = ObsidianSurface.copy(alpha = 0.96f),
                                tonalElevation = 8.dp
                            ) {
                                Destinations.forEachIndexed { index, dest ->
                                    val selected = selectedTab == index
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = CyberCyan,
                                            selectedTextColor = CyberCyan,
                                            indicatorColor = NeonViolet.copy(alpha = 0.28f),
                                            unselectedIconColor = TextMuted,
                                            unselectedTextColor = TextMuted
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                            .let {
                                if (isWideScreen) it.navigationBarsPadding() else it
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 960.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            when (selectedTab) {
                                0 -> HomeScreen(
                                    userDisplayName = userProfile?.displayName ?: currentUser.displayName.orEmpty(),
                                    allTracks = allTracks,
                                    catalogTracks = catalogTracks,
                                    playlists = playlists,
                                    history = history,
                                    downloads = downloads,
                                    artists = artists,
                                    currentTrackId = currentTrack?.id,
                                    favoriteIds = favoriteIds,
                                    updateInfo = updateInfo,
                                    onPlayTrack = playWithNotificationCheck,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToQueue = viewModel::addToQueue,
                                    onRequestAddToPlaylist = { trackPendingPlaylist = it },
                                    onDownloadTrack = viewModel::downloadTrack,
                                    onOpenAiAssistant = { selectedTab = 4 },
                                    onOpenAccountSettings = { showAccountDialog = true },
                                    onOpenPlaylistTab = { selectedTab = 3 }
                                )

                                1 -> ExploreScreen(
                                    genres = viewModel.genres,
                                    catalogTracks = catalogTracks,
                                    catalogLoading = catalogLoading,
                                    albums = albums,
                                    currentTrackId = currentTrack?.id,
                                    favoriteIds = favoriteIds,
                                    onSelectGenre = { genre ->
                                        viewModel.loadOpenCatalog(genre.queryTerm, genre.name)
                                    },
                                    onPlayTrack = playWithNotificationCheck,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToQueue = viewModel::addToQueue,
                                    onRequestAddToPlaylist = { trackPendingPlaylist = it },
                                    onDownloadTrack = viewModel::downloadTrack
                                )

                                2 -> SearchScreen(
                                    allTracks = allTracks,
                                    playlists = playlists,
                                    currentTrackId = currentTrack?.id,
                                    favoriteIds = favoriteIds,
                                    onPlayTrack = playWithNotificationCheck,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToQueue = viewModel::addToQueue,
                                    onRequestAddToPlaylist = { trackPendingPlaylist = it },
                                    onDownloadTrack = viewModel::downloadTrack,
                                    onSearchOnlineCatalog = { q ->
                                        viewModel.loadOpenCatalog(q, "Search")
                                        selectedTab = 1
                                    },
                                    onSendToAiAssistant = { q ->
                                        selectedTab = 4
                                        viewModel.sendAiPrompt(q)
                                    }
                                )

                                3 -> LibraryScreen(
                                    allTracks = allTracks,
                                    localTracks = localTracks,
                                    playlists = playlists,
                                    history = history,
                                    downloads = downloads,
                                    downloadedBytes = downloadedBytes,
                                    downloadQuality = preferences.downloadQuality,
                                    currentTrackId = currentTrack?.id,
                                    favoriteIds = favoriteIds,
                                    onScanDeviceMusic = viewModel::scanDeviceMusic,
                                    onImportAudioUri = viewModel::importPickedAudio,
                                    onImportPlaylistUri = viewModel::importPlaylistFromUri,
                                    onExportPlaylist = viewModel::exportPlaylistText,
                                    onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                                    onRenamePlaylist = viewModel::renamePlaylist,
                                    onDeletePlaylist = viewModel::deletePlaylist,
                                    onDuplicatePlaylist = viewModel::duplicatePlaylist,
                                    onRemoveFromPlaylist = viewModel::removeTrackFromPlaylist,
                                    onReorderPlaylistTrack = viewModel::reorderPlaylistTrack,
                                    onPlayTrack = playWithNotificationCheck,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToQueue = viewModel::addToQueue,
                                    onRequestAddToPlaylist = { trackPendingPlaylist = it },
                                    onDownloadTrack = viewModel::downloadTrack,
                                    onPauseDownload = viewModel::pauseDownload,
                                    onResumeDownload = viewModel::resumeDownload,
                                    onRemoveDownload = viewModel::removeDownload,
                                    onSelectDownloadQuality = viewModel::setDownloadQuality
                                )

                                4 -> AiAssistantScreen(
                                    messages = aiMessages,
                                    aiBusy = aiBusy,
                                    onSendCommand = viewModel::sendAiPrompt,
                                    onStreamQuestion = viewModel::streamAiExplanation,
                                    onPlayTracks = { tracks ->
                                        tracks.firstOrNull()?.let { first ->
                                            playWithNotificationCheck(first, tracks)
                                        }
                                    }
                                )
                            }
                        }

                        if (currentTrack != null) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                artworkTheme = artworkTheme,
                                onExpand = { isPlayerExpanded = true },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSkipNext = viewModel::skipNext,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .widthIn(max = 680.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded && currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            FullPlayerOverlay(
                playbackState = playbackState,
                artworkTheme = artworkTheme,
                lyricsLines = lyricsLines,
                lyricsStatus = lyricsStatus,
                preferences = preferences,
                equalizerState = equalizerState,
                audioEffectManager = viewModel.audioEffectManager,
                visualizerFrame = visualizerFrame,
                isFavorite = currentIsFavorite,
                onCollapse = { isPlayerExpanded = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onDownloadTrack = viewModel::downloadTrack,
                onSetSpeed = viewModel::setPlaybackSpeed,
                onSetCrossfade = viewModel::setCrossfadeSeconds,
                onStartSleepTimer = viewModel::startSleepTimer,
                onJumpQueueIndex = viewModel::jumpToQueueIndex,
                onMoveQueueItem = viewModel::moveQueueItem,
                onRemoveQueueItem = viewModel::removeFromQueue,
                onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
                onFontScaleChange = viewModel::setLyricsFontScale,
                onToggleLyricsOnly = viewModel::setLyricsOnlyKaraoke,
                onToggleBatterySaver = viewModel::setBatterySaverVisualizer,
                onSaveManualSync = viewModel::saveManualSyncedLyrics,
                onVisualizerPermissionGranted = viewModel::refreshVisualizerPermission
            )
        }
    }

    if (showAccountDialog) {
        AccountAndSettingsDialog(
            userProfile = userProfile,
            fallbackName = currentUser.displayName.orEmpty(),
            fallbackEmail = currentUser.email.orEmpty(),
            updateInfo = updateInfo,
            updateChecking = updateChecking,
            onDismiss = { showAccountDialog = false },
            onUpdatePrivacy = viewModel::updatePrivacyAndQuality,
            onCheckUpdates = { viewModel.checkAppUpdates(silent = false) },
            onOpenUpdateInstall = viewModel::openUpdateInstall,
            onRemindLater = { ver ->
                viewModel.remindLaterUpdate(ver)
                showAutoUpdateBannerDialog = false
            },
            onSignOut = {
                showAccountDialog = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignedOut
                )
            },
            onDeleteAccount = {
                viewModel.deleteAccount {
                    showAccountDialog = false
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = onSignedOut
                    )
                }
            }
        )
    }

    trackPendingPlaylist?.let { pendingTrack ->
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { trackPendingPlaylist = null },
            title = { Text("Add '${pendingTrack.title}' to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (playlists.isEmpty()) {
                        Text(
                            "No cloud playlists yet. Create one below and this track will be added automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height((playlists.size * 52).coerceAtMost(220).dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists, key = { "add_pl_${it.id}" }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianCard)
                                        .clickable {
                                            viewModel.addTrackToPlaylist(pl, pendingTrack.id)
                                            trackPendingPlaylist = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pl.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${pl.trackIds.size} tracks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Or create new playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(
                                name = newPlaylistName.trim(),
                                description = "",
                                trackIds = listOf(pendingTrack.id)
                            )
                            trackPendingPlaylist = null
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackPendingPlaylist = null }) { Text("Close") }
            }
        )
    }

    val availableUpdate = updateInfo
    if (showAutoUpdateBannerDialog && availableUpdate != null && availableUpdate.updateAvailable) {
        AlertDialog(
            onDismissRequest = { showAutoUpdateBannerDialog = false },
            title = { Text("MUSICVERSE AI Update Available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version ${availableUpdate.latestVersionName} (Build ${availableUpdate.latestVersionCode}) is available.")
                    Text(availableUpdate.changelog, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAutoUpdateBannerDialog = false
                    viewModel.openUpdateInstall(availableUpdate)
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showAutoUpdateBannerDialog = false
                    viewModel.remindLaterUpdate(availableUpdate.latestVersionCode)
                }) {
                    Text("Remind Later")
                }
            }
        )
    }
}
