package com.example.domain.model

import androidx.compose.ui.graphics.Color

enum class TrackSourceType {
    BUNDLED,
    LOCAL,
    CATALOG
}

enum class AudioQuality(val label: String, val bitrateLabel: String) {
    NORMAL("Standard", "128 kbps AAC"),
    HIGH("High Fidelity", "320 kbps MP3"),
    LOSSLESS("Studio Lossless", "PCM / FLAC")
}

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}

enum class SubscriptionTier(val label: String) {
    FREE("Free"),
    PREMIUM("MusicVerse Pro")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String? = null,
    val artworkResName: String? = null,
    val sourceType: TrackSourceType = TrackSourceType.BUNDLED,
    val downloadAuthorized: Boolean = false,
    val licenseName: String = "Personal Local File",
    val audioFormatLabel: String = "WAV · 22kHz",
    val dateAddedSec: Long = 0L,
    val folderName: String = "MusicVerse",
    val localDownloadedPath: String? = null,
    val embeddedLyricsRaw: String? = null
)

data class LyricsWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val languageTag: String = "en"
)

data class AlbumGroup(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val artworkResName: String?,
    val tracks: List<Track>
)

data class ArtistGroup(
    val name: String,
    val artworkUri: String?,
    val artworkResName: String?,
    val tracks: List<Track>
)

data class GenreCategory(
    val id: String,
    val name: String,
    val subtitle: String,
    val queryTerm: String,
    val accentHex: Long
)

data class AppPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    val smartPlaylist: Boolean = false,
    val smartRule: String? = null,
    val syncedToCloud: Boolean = false
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val localFilePath: String,
    val quality: AudioQuality,
    val status: DownloadStatus,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val sha256Hex: String = "",
    val licenseName: String = "",
    val errorMessage: String? = null
)

data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = true,
    val activePresetName: String = "Flat",
    val bands: List<EqBand> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val trebleBoostMb: Int = 0,
    val customPresets: List<String> = emptyList()
)

data class VisualizerFrame(
    val isLiveAudioCapture: Boolean = false,
    val permissionGranted: Boolean = false,
    val waveform: List<Float> = List(32) { 0f },
    val spectrumBars: List<Float> = List(24) { 0f },
    val bassEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val overallAmplitude: Float = 0f
)

data class DynamicArtworkTheme(
    val primaryGlow: Color = Color(0xFFA855F7),
    val secondaryGlow: Color = Color(0xFF06F6D4),
    val backgroundTop: Color = Color(0xFF1E1038),
    val backgroundBottom: Color = Color(0xFF05050B),
    val glassTint: Color = Color(0x28A855F7),
    val lyricHighlight: Color = Color(0xFF06F6D4)
)

data class SleepTimerState(
    val active: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0
)

data class AppUpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateAvailable: Boolean,
    val changelog: String,
    val downloadUrl: String,
    val expectedSha256: String,
    val checkedAtMillis: Long
)

data class AiAssistantMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val suggestedTracks: List<Track> = emptyList(),
    val createdPlaylistName: String? = null,
    val isStreaming: Boolean = false
)
