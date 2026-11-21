package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.R
import com.example.domain.model.Track
import com.example.domain.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaScanner(private val context: Context) {

    fun getBundledTracks(): List<Track> {
        val pkg = context.packageName
        return listOf(
            Track(
                id = "bundled_neon_horizon",
                title = "Neon Horizon",
                artist = "Aarav Synth",
                album = "Liquid Cosmos",
                genre = "Electronic",
                durationMs = 36_000L,
                mediaUri = "android.resource://$pkg/${R.raw.track_neon_horizon}",
                artworkResName = "img_album_neon_pulse",
                sourceType = TrackSourceType.BUNDLED,
                downloadAuthorized = false,
                licenseName = "Bundled MusicVerse Studio Master",
                audioFormatLabel = "PCM WAV · 22kHz",
                dateAddedSec = 1763600000L,
                folderName = "MusicVerse Studio"
            ),
            Track(
                id = "bundled_dil_ki_dhadkan",
                title = "Dil Ki Dhadkan",
                artist = "Meera & Kabir",
                album = "Noor Sessions",
                genre = "Hindi Romantic",
                durationMs = 36_000L,
                mediaUri = "android.resource://$pkg/${R.raw.track_dil_ki_dhadkan}",
                artworkResName = "img_hero_cosmos",
                sourceType = TrackSourceType.BUNDLED,
                downloadAuthorized = false,
                licenseName = "Bundled MusicVerse Studio Master",
                audioFormatLabel = "PCM WAV · 22kHz",
                dateAddedSec = 1763550000L,
                folderName = "MusicVerse Studio"
            ),
            Track(
                id = "bundled_midnight_raag",
                title = "Midnight Raag (Meditation)",
                artist = "Zoya Ensemble",
                album = "Acoustic Mist",
                genre = "Meditation",
                durationMs = 36_000L,
                mediaUri = "android.resource://$pkg/${R.raw.track_midnight_raag}",
                artworkResName = "img_album_acoustic_mist",
                sourceType = TrackSourceType.BUNDLED,
                downloadAuthorized = false,
                licenseName = "Bundled MusicVerse Studio Master",
                audioFormatLabel = "PCM WAV · 22kHz",
                dateAddedSec = 1763500000L,
                folderName = "MusicVerse Studio"
            )
        )
    }

    suspend fun scanDeviceAudio(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 2000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol).orEmpty().ifBlank { "Untitled Audio" }
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)
                        ?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Device Audio"
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val dateAdded = cursor.getLong(dateCol)
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val folder = rawPath?.let { File(it).parentFile?.name } ?: "Device Storage"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val sidecarLrc = rawPath?.let { path ->
                        val lrcFile = File(path.substringBeforeLast('.') + ".lrc")
                        if (lrcFile.exists() && lrcFile.canRead()) {
                            runCatching { lrcFile.readText() }.getOrNull()
                        } else null
                    }

                    val formatBadge = mime.substringAfter('/').uppercase().ifBlank { "AUDIO" }

                    tracks.add(
                        Track(
                            id = "local_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            genre = "Local Library",
                            durationMs = durationMs,
                            mediaUri = contentUri.toString(),
                            artworkUri = artworkUri,
                            artworkResName = "img_album_neon_pulse",
                            sourceType = TrackSourceType.LOCAL,
                            downloadAuthorized = false,
                            licenseName = "Device Storage",
                            audioFormatLabel = formatBadge,
                            dateAddedSec = dateAdded,
                            folderName = folder,
                            embeddedLyricsRaw = sidecarLrc
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w("LocalMediaScanner", "Audio permission not granted yet: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocalMediaScanner", "Failed scanning device audio", e)
        }
        tracks
    }

    suspend fun extractPickedAudioTrack(uri: Uri): Track? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: "Picked Track"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Local Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Picked Folder"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() } ?: "Local Audio"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.div(1000)

            Track(
                id = "saf_${uri.toString().hashCode().toUInt()}",
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = durationMs,
                mediaUri = uri.toString(),
                artworkResName = "img_album_neon_pulse",
                sourceType = TrackSourceType.LOCAL,
                downloadAuthorized = false,
                licenseName = "Picked Local Audio",
                audioFormatLabel = if (bitrate != null) "${bitrate} kbps" else "LOCAL",
                dateAddedSec = System.currentTimeMillis() / 1000L,
                folderName = "Imported Audio"
            )
        } catch (e: Exception) {
            Log.e("LocalMediaScanner", "Failed reading SAF audio URI: $uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
