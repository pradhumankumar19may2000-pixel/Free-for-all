package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.domain.model.AppPlaylist
import com.example.domain.model.CloudPlaylist
import com.example.domain.model.FavoriteTrack
import com.example.domain.model.ListeningHistoryEntry
import com.example.domain.model.Track
import com.example.domain.model.UserProfile
import com.example.security.OperationType
import com.example.security.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MusicCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "item_${System.currentTimeMillis()}" }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            path = ref.path
            val snap = ref.get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.take(80).ifBlank { "MusicVerse Listener" },
                    email = email.take(120).ifBlank { "listener@musicverse.app" },
                    photoUrl = photoUrl?.take(500),
                    subscriptionTier = "FREE",
                    analyticsEnabled = true,
                    personalizationEnabled = true,
                    preferredQuality = "HIGH"
                )
                ref.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        subscriptionTier: String? = null,
        analyticsEnabled: Boolean? = null,
        personalizationEnabled: Boolean? = null,
        preferredQuality: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            path = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (subscriptionTier != null && subscriptionTier in listOf("FREE", "PREMIUM")) {
                updates["subscriptionTier"] = subscriptionTier
            }
            if (analyticsEnabled != null) updates["analyticsEnabled"] = analyticsEnabled
            if (personalizationEnabled != null) updates["personalizationEnabled"] = personalizationEnabled
            if (preferredQuality != null && preferredQuality in listOf("NORMAL", "HIGH", "LOSSLESS")) {
                updates["preferredQuality"] = preferredQuality
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePlaylists(userId: String): Flow<List<AppPlaylist>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/playlists"
        emitAll(
            db.collection("users").document(uid).collection("playlists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudPlaylist::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { cp ->
                        AppPlaylist(
                            id = cp.id,
                            name = cp.name,
                            description = cp.description.orEmpty(),
                            trackIds = cp.trackIds,
                            smartPlaylist = cp.smartPlaylist,
                            smartRule = cp.smartRule,
                            syncedToCloud = true
                        )
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPlaylist(
        name: String,
        description: String?,
        trackIds: List<String>,
        smartPlaylist: Boolean = false,
        smartRule: String? = null
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/playlists"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80).ifBlank { "Untitled Mix" }
            val ref = db.collection("users").document(uid).collection("playlists").document()
            path = ref.path
            val model = CloudPlaylist(
                userId = uid,
                name = cleanName,
                description = description?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                trackIds = trackIds.take(200),
                smartPlaylist = smartPlaylist,
                smartRule = smartRule?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
            )
            ref.set(model.toCreateMap()).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        trackIds: List<String>? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            require(playlistId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("playlists").document(playlistId)
            path = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (name != null) updates["name"] = name.trim().take(80).ifBlank { "Playlist" }
            if (trackIds != null) updates["trackIds"] = trackIds.take(200)
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("playlists").document(playlistId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeFavorites(userId: String): Flow<List<FavoriteTrack>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/favorites"
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .snapshots()
                .map { it.toObjects(FavoriteTrack::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun toggleFavorite(track: Track, currentlyFavorite: Boolean): Result<Unit> {
        val safeId = sanitizeDocId(track.id)
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/favorites/$safeId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("favorites").document(safeId)
            path = ref.path
            if (currentlyFavorite) {
                ref.delete().await()
            } else {
                val fav = FavoriteTrack(
                    userId = uid,
                    trackId = safeId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    sourceType = track.sourceType.name,
                    artworkUri = track.artworkUri?.takeIf { it.startsWith("https://") },
                    streamUrl = track.mediaUri.takeIf { it.startsWith("https://") }
                )
                ref.set(fav.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeHistory(userId: String): Flow<List<ListeningHistoryEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/history"
        emitAll(
            db.collection("users").document(uid).collection("history")
                .snapshots()
                .map { it.toObjects(ListeningHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordTrackPlayed(track: Track): Result<Unit> {
        val safeId = sanitizeDocId(track.id)
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/history/$safeId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("history").document(safeId)
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(
                    mapOf(
                        "title" to track.title.take(160).ifBlank { "Unknown Track" },
                        "artist" to track.artist.take(120).ifBlank { "Unknown Artist" },
                        "album" to track.album.take(120).ifBlank { "Single" },
                        "genre" to track.genre.take(60).ifBlank { "Music" },
                        "playCount" to FieldValue.increment(1L),
                        "lastPlayedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val entry = ListeningHistoryEntry(
                    userId = uid,
                    trackId = safeId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    genre = track.genre,
                    playCount = 1L
                )
                ref.set(entry.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountAndData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val userDoc = db.collection("users").document(uid)
            for (sub in listOf("playlists", "favorites", "history")) {
                val snap = userDoc.collection(sub).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            }
            userDoc.delete().await()
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
