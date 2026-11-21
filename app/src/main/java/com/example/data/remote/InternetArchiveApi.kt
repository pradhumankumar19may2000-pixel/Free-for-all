package com.example.data.remote

import android.util.Log
import com.example.domain.model.Track
import com.example.domain.model.TrackSourceType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ArchiveSearchResponse(
    @Json(name = "response") val response: ArchiveResponseBody? = null
)

@JsonClass(generateAdapter = true)
data class ArchiveResponseBody(
    @Json(name = "docs") val docs: List<ArchiveDoc> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArchiveDoc(
    @Json(name = "identifier") val identifier: String = "",
    @Json(name = "title") val title: String? = null,
    @Json(name = "creator") val creator: Any? = null,
    @Json(name = "licenseurl") val licenseUrl: String? = null,
    @Json(name = "downloads") val downloads: Long? = null
)

interface InternetArchiveService {
    @GET("advancedsearch.php")
    suspend fun searchOpenMusic(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = listOf("identifier", "title", "creator", "licenseurl", "downloads"),
        @Query("sort[]") sort: String = "downloads desc",
        @Query("rows") rows: Int = 12,
        @Query("page") page: Int = 1,
        @Query("output") output: String = "json"
    ): ArchiveSearchResponse

    @GET("metadata/{identifier}")
    suspend fun getItemMetadata(@Path("identifier") identifier: String): okhttp3.ResponseBody
}

class InternetArchiveRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: InternetArchiveService = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(InternetArchiveService::class.java)

    suspend fun fetchAuthorizedOpenMusic(
        searchTerm: String,
        genreLabel: String
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val sanitized = searchTerm.replace("\"", "").trim().ifBlank { "ambient electronic" }
            val query = "mediatype:(audio) AND ($sanitized) AND (licenseurl:*creativecommons* OR licenseurl:*publicdomain*)"
            val response = api.searchOpenMusic(query = query, rows = 10)
            val docs = response.response?.docs.orEmpty().take(8)
            val resolvedTracks = mutableListOf<Track>()

            for (doc in docs) {
                if (doc.identifier.isBlank()) continue
                val playable = resolveFirstMp3FromMetadata(doc, genreLabel)
                if (playable != null) {
                    resolvedTracks.add(playable)
                }
                if (resolvedTracks.size >= 6) break
            }
            Result.success(resolvedTracks)
        } catch (e: Exception) {
            Log.w("ArchiveRepo", "Open catalog fetch failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun resolveFirstMp3FromMetadata(doc: ArchiveDoc, genreLabel: String): Track? {
        return try {
            val req = Request.Builder()
                .url("https://archive.org/metadata/${doc.identifier}/files")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val files = json.optJSONArray("result") ?: return null
                for (i in 0 until files.length()) {
                    val fileObj = files.optJSONObject(i) ?: continue
                    val name = fileObj.optString("name")
                    val format = fileObj.optString("format")
                    if (name.endsWith(".mp3", ignoreCase = true) ||
                        format.contains("MP3", ignoreCase = true)
                    ) {
                        val lengthSec = fileObj.optString("length").toDoubleOrNull() ?: 120.0
                        val artistName = when (val c = doc.creator) {
                            is String -> c
                            is List<*> -> c.firstOrNull()?.toString()
                            else -> null
                        }?.takeIf { it.isNotBlank() } ?: "Creative Commons Artist"
                        val encodedName = name.replace(" ", "%20")
                        val streamUrl = "https://archive.org/download/${doc.identifier}/$encodedName"
                        val thumbUrl = "https://archive.org/services/img/${doc.identifier}"
                        val license = doc.licenseUrl?.let {
                            when {
                                it.contains("publicdomain", true) || it.contains("zero", true) -> "Public Domain (CC0)"
                                it.contains("creativecommons", true) -> "Creative Commons Authorized"
                                else -> "Open Archive Audio"
                            }
                        } ?: "Creative Commons Authorized"

                        return Track(
                            id = "ia_${doc.identifier.replace(Regex("[^a-zA-Z0-9_-]"), "_")}",
                            title = doc.title?.takeIf { it.isNotBlank() } ?: doc.identifier,
                            artist = artistName,
                            album = "Internet Archive Open Audio",
                            genre = genreLabel,
                            durationMs = (lengthSec * 1000).toLong().coerceAtLeast(15_000L),
                            mediaUri = streamUrl,
                            artworkUri = thumbUrl,
                            artworkResName = "img_album_neon_pulse",
                            sourceType = TrackSourceType.CATALOG,
                            downloadAuthorized = !doc.licenseUrl.isNullOrBlank(),
                            licenseName = license,
                            audioFormatLabel = "MP3 · Stream",
                            dateAddedSec = System.currentTimeMillis() / 1000L,
                            folderName = "Open Catalog"
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w("ArchiveRepo", "Metadata parse skipped for ${doc.identifier}: ${e.message}")
            null
        }
    }
}
