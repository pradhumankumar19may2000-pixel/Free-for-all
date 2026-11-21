package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AudioQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.musicVerseDataStore by preferencesDataStore(name = "musicverse_prefs")

data class PlayerPreferences(
    val crossfadeSeconds: Int = 3,
    val playbackSpeed: Float = 1.0f,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val batterySaverVisualizer: Boolean = false,
    val reducedMotionLyrics: Boolean = false,
    val lyricsFontScale: Float = 1.0f,
    val lyricsOnlyKaraoke: Boolean = false,
    val lyricsOffsetMs: Long = 0L,
    val dismissedUpdateVersionCode: Int = 0
)

class PreferencesStore(private val context: Context) {
    private object Keys {
        val CROSSFADE_SEC = intPreferencesKey("crossfade_sec")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val BATTERY_SAVER_VIZ = booleanPreferencesKey("battery_saver_viz")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val LYRICS_FONT_SCALE = floatPreferencesKey("lyrics_font_scale")
        val LYRICS_ONLY_KARAOKE = booleanPreferencesKey("lyrics_only_karaoke")
        val LYRICS_OFFSET_MS = longPreferencesKey("lyrics_offset_ms")
        val DISMISSED_UPDATE_VER = intPreferencesKey("dismissed_update_ver")
    }

    val preferences: Flow<PlayerPreferences> = context.musicVerseDataStore.data.map { prefs ->
        val qualityStr = prefs[Keys.DOWNLOAD_QUALITY] ?: AudioQuality.HIGH.name
        val quality = AudioQuality.entries.firstOrNull { it.name == qualityStr } ?: AudioQuality.HIGH
        PlayerPreferences(
            crossfadeSeconds = prefs[Keys.CROSSFADE_SEC] ?: 3,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            downloadQuality = quality,
            batterySaverVisualizer = prefs[Keys.BATTERY_SAVER_VIZ] ?: false,
            reducedMotionLyrics = prefs[Keys.REDUCED_MOTION] ?: false,
            lyricsFontScale = prefs[Keys.LYRICS_FONT_SCALE] ?: 1.0f,
            lyricsOnlyKaraoke = prefs[Keys.LYRICS_ONLY_KARAOKE] ?: false,
            lyricsOffsetMs = prefs[Keys.LYRICS_OFFSET_MS] ?: 0L,
            dismissedUpdateVersionCode = prefs[Keys.DISMISSED_UPDATE_VER] ?: 0
        )
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.musicVerseDataStore.edit { it[Keys.CROSSFADE_SEC] = seconds.coerceIn(0, 12) }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.musicVerseDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setDownloadQuality(quality: AudioQuality) {
        context.musicVerseDataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setBatterySaverVisualizer(enabled: Boolean) {
        context.musicVerseDataStore.edit { it[Keys.BATTERY_SAVER_VIZ] = enabled }
    }

    suspend fun setReducedMotionLyrics(enabled: Boolean) {
        context.musicVerseDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setLyricsFontScale(scale: Float) {
        context.musicVerseDataStore.edit { it[Keys.LYRICS_FONT_SCALE] = scale.coerceIn(0.8f, 1.6f) }
    }

    suspend fun setLyricsOnlyKaraoke(enabled: Boolean) {
        context.musicVerseDataStore.edit { it[Keys.LYRICS_ONLY_KARAOKE] = enabled }
    }

    suspend fun setLyricsOffsetMs(offsetMs: Long) {
        context.musicVerseDataStore.edit { it[Keys.LYRICS_OFFSET_MS] = offsetMs.coerceIn(-10000L, 10000L) }
    }

    suspend fun setDismissedUpdateVersion(versionCode: Int) {
        context.musicVerseDataStore.edit { it[Keys.DISMISSED_UPDATE_VER] = versionCode }
    }
}
