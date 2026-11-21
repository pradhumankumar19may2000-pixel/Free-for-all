package com.example.data.lyrics

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class LrcLibRecord(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "albumName") val albumName: String? = null,
    @Json(name = "duration") val duration: Double? = null,
    @Json(name = "instrumental") val instrumental: Boolean? = null,
    @Json(name = "plainLyrics") val plainLyrics: String? = null,
    @Json(name = "syncedLyrics") val syncedLyrics: String? = null
)

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null
    ): LrcLibRecord

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibRecord>

    companion object {
        fun create(): LrcLibApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "MusicVerseAI/1.0 Android")
                        .build()
                    chain.proceed(req)
                }
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl("https://lrclib.net/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(LrcLibApi::class.java)
        }
    }
}
