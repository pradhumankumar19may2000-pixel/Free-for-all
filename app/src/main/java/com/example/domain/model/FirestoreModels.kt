package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val subscriptionTier: String = "FREE",
    val analyticsEnabled: Boolean = true,
    val personalizationEnabled: Boolean = true,
    val preferredQuality: String = "HIGH",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80).ifBlank { "MusicVerse Listener" },
        "email" to email.take(120).ifBlank { "listener@musicverse.app" },
        "photoUrl" to photoUrl?.take(500),
        "subscriptionTier" to if (subscriptionTier in listOf("FREE", "PREMIUM")) subscriptionTier else "FREE",
        "analyticsEnabled" to analyticsEnabled,
        "personalizationEnabled" to personalizationEnabled,
        "preferredQuality" to if (preferredQuality in listOf("NORMAL", "HIGH", "LOSSLESS")) preferredQuality else "HIGH",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class CloudPlaylist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String? = null,
    val trackIds: List<String> = emptyList(),
    val smartPlaylist: Boolean = false,
    val smartRule: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.take(80).ifBlank { "Untitled Mix" },
        "description" to description?.take(300),
        "trackIds" to trackIds.take(200),
        "smartPlaylist" to smartPlaylist,
        "smartRule" to smartRule?.take(120),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FavoriteTrack(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val sourceType: String = "BUNDLED",
    val artworkUri: String? = null,
    val streamUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "trackId" to trackId.take(128),
        "title" to title.take(160).ifBlank { "Unknown Track" },
        "artist" to artist.take(120).ifBlank { "Unknown Artist" },
        "album" to album.take(120).ifBlank { "Single" },
        "durationMs" to durationMs.coerceAtLeast(0L),
        "sourceType" to if (sourceType in listOf("BUNDLED", "LOCAL", "CATALOG")) sourceType else "BUNDLED",
        "artworkUri" to artworkUri?.take(500),
        "streamUrl" to streamUrl?.take(500),
        "createdAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ListeningHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "Music",
    val playCount: Long = 1L,
    @ServerTimestamp val lastPlayedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "trackId" to trackId.take(128),
        "title" to title.take(160).ifBlank { "Unknown Track" },
        "artist" to artist.take(120).ifBlank { "Unknown Artist" },
        "album" to album.take(120).ifBlank { "Single" },
        "genre" to genre.take(60).ifBlank { "Music" },
        "playCount" to playCount.coerceAtLeast(1L),
        "lastPlayedAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp()
    )
}
