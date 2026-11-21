package com.example.data.playlist

import com.example.domain.model.AppPlaylist
import com.example.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject

object PlaylistCodec {

    enum class Format(val extension: String, val mimeType: String) {
        M3U("m3u", "audio/x-mpegurl"),
        CSV("csv", "text/csv"),
        JSON("json", "application/json")
    }

    fun export(playlist: AppPlaylist, tracks: List<Track>, format: Format): String {
        return when (format) {
            Format.M3U -> buildString {
                appendLine("#EXTM3U")
                appendLine("#PLAYLIST:${playlist.name}")
                tracks.forEach { t ->
                    val secs = (t.durationMs / 1000L).coerceAtLeast(0L)
                    appendLine("#EXTINF:$secs,${t.artist} - ${t.title}")
                    appendLine(t.mediaUri)
                }
            }
            Format.CSV -> buildString {
                appendLine("id,title,artist,album,genre,durationMs,mediaUri")
                tracks.forEach { t ->
                    appendLine(
                        listOf(t.id, t.title, t.artist, t.album, t.genre, t.durationMs.toString(), t.mediaUri)
                            .joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
                    )
                }
            }
            Format.JSON -> {
                val arr = JSONArray()
                tracks.forEach { t ->
                    arr.put(
                        JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("artist", t.artist)
                            put("album", t.album)
                            put("genre", t.genre)
                            put("durationMs", t.durationMs)
                            put("mediaUri", t.mediaUri)
                        }
                    )
                }
                JSONObject().apply {
                    put("name", playlist.name)
                    put("description", playlist.description)
                    put("tracks", arr)
                }.toString(2)
            }
        }
    }

    data class ImportedPlaylistData(
        val name: String,
        val trackUrisOrTitles: List<String>
    )

    fun parseImport(rawText: String, fallbackName: String): ImportedPlaylistData {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val name = json.optString("name").ifBlank { fallbackName }
            val arr = json.optJSONArray("tracks") ?: JSONArray()
            val items = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val uri = obj?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: obj?.optString("mediaUri")?.takeIf { it.isNotBlank() }
                    ?: obj?.optString("title")?.takeIf { it.isNotBlank() }
                if (uri != null) items.add(uri)
            }
            return ImportedPlaylistData(name, items)
        }

        if (trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")) {
            var name = fallbackName
            val refs = mutableListOf<String>()
            trimmed.lines().forEach { line ->
                val l = line.trim()
                when {
                    l.startsWith("#PLAYLIST:") -> name = l.removePrefix("#PLAYLIST:").trim().ifBlank { fallbackName }
                    l.isNotEmpty() && !l.startsWith("#") -> refs.add(l)
                }
            }
            return ImportedPlaylistData(name, refs)
        }

        // CSV fallback
        val lines = trimmed.lines().filter { it.isNotBlank() }
        val refs = lines.drop(1).mapNotNull { line ->
            val firstCell = line.split(",").firstOrNull()?.trim()?.trim('"')
            firstCell?.takeIf { it.isNotBlank() }
        }
        return ImportedPlaylistData(fallbackName, refs)
    }
}
