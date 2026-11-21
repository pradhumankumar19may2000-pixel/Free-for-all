package com.example.data.download

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.domain.model.AudioQuality
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TrackDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadsDir: File
        get() = File(context.filesDir, "authorized_downloads").apply {
            if (!exists()) mkdirs()
        }

    fun observeDownloads(): Flow<List<DownloadItem>> =
        downloadDao.observeDownloads().map { list ->
            list.map { entity -> entity.toDomain() }
        }

    suspend fun enqueueDownload(track: Track, quality: AudioQuality): Result<Unit> {
        if (!track.downloadAuthorized) {
            return Result.failure(
                IllegalStateException("Offline downloading is only permitted for authorized Creative Commons or Public Domain tracks.")
            )
        }
        if (!track.mediaUri.startsWith("https://")) {
            return Result.failure(
                IllegalArgumentException("Track does not have an HTTPS authorized stream URL.")
            )
        }

        val targetFile = File(downloadsDir, "${track.id}.mp3")
        val existing = downloadDao.getDownload(track.id)
        val initialBytes = if (existing?.statusName == DownloadStatus.PAUSED.name && targetFile.exists()) {
            targetFile.length()
        } else {
            if (targetFile.exists()) targetFile.delete()
            0L
        }

        val entity = DownloadEntity(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            genre = track.genre,
            durationMs = track.durationMs,
            streamUrl = track.mediaUri,
            artworkUri = track.artworkUri,
            localFilePath = targetFile.absolutePath,
            qualityName = quality.name,
            statusName = DownloadStatus.QUEUED.name,
            progressPercent = existing?.progressPercent ?: 0,
            bytesDownloaded = initialBytes,
            totalBytes = existing?.totalBytes ?: 0L,
            sha256Hex = "",
            licenseName = track.licenseName
        )
        downloadDao.upsert(entity)
        startDownloadJob(entity)
        return Result.success(Unit)
    }

    fun pauseDownload(trackId: String) {
        activeJobs.remove(trackId)?.cancel()
        scope.launch {
            val current = downloadDao.getDownload(trackId) ?: return@launch
            if (current.statusName == DownloadStatus.DOWNLOADING.name ||
                current.statusName == DownloadStatus.QUEUED.name
            ) {
                downloadDao.upsert(
                    current.copy(
                        statusName = DownloadStatus.PAUSED.name,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun resumeOrRetryDownload(trackId: String) {
        scope.launch {
            val current = downloadDao.getDownload(trackId) ?: return@launch
            val updated = current.copy(
                statusName = DownloadStatus.QUEUED.name,
                errorMessage = null,
                updatedAtMs = System.currentTimeMillis()
            )
            downloadDao.upsert(updated)
            startDownloadJob(updated)
        }
    }

    fun removeDownload(trackId: String) {
        activeJobs.remove(trackId)?.cancel()
        scope.launch {
            val current = downloadDao.getDownload(trackId)
            if (current != null) {
                runCatching { File(current.localFilePath).delete() }
            }
            downloadDao.deleteById(trackId)
        }
    }

    suspend fun getDownloadedStorageBytes(): Long = withContext(Dispatchers.IO) {
        downloadsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun startDownloadJob(entity: DownloadEntity) {
        activeJobs[entity.trackId]?.cancel()
        val job = scope.launch {
            val file = File(entity.localFilePath)
            var currentBytes = if (file.exists()) file.length() else 0L
            try {
                downloadDao.upsert(
                    entity.copy(
                        statusName = DownloadStatus.DOWNLOADING.name,
                        errorMessage = null,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )

                val requestBuilder = Request.Builder().url(entity.streamUrl)
                if (currentBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$currentBytes-")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("Download server returned HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty download stream")
                    val contentLength = body.contentLength()
                    val isPartial = response.code == 206
                    if (!isPartial) {
                        currentBytes = 0L
                        if (file.exists()) file.delete()
                    }
                    val totalSize = if (contentLength > 0L) currentBytes + contentLength else 0L

                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(currentBytes)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            var lastUpdate = System.currentTimeMillis()
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                raf.write(buffer, 0, bytesRead)
                                currentBytes += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 300L) {
                                    lastUpdate = now
                                    val pct = if (totalSize > 0L) {
                                        ((currentBytes * 100) / totalSize).toInt().coerceIn(0, 99)
                                    } else 0
                                    downloadDao.upsert(
                                        entity.copy(
                                            statusName = DownloadStatus.DOWNLOADING.name,
                                            bytesDownloaded = currentBytes,
                                            totalBytes = totalSize,
                                            progressPercent = pct,
                                            updatedAtMs = now
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val sha256 = computeSha256(file)
                    downloadDao.upsert(
                        entity.copy(
                            statusName = DownloadStatus.COMPLETED.name,
                            bytesDownloaded = file.length(),
                            totalBytes = file.length(),
                            progressPercent = 100,
                            sha256Hex = sha256,
                            errorMessage = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TrackDownloadManager", "Download failed for ${entity.trackId}", e)
                downloadDao.upsert(
                    entity.copy(
                        statusName = DownloadStatus.FAILED.name,
                        bytesDownloaded = currentBytes,
                        errorMessage = e.localizedMessage ?: "Download interrupted",
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            } finally {
                activeJobs.remove(entity.trackId)
            }
        }
        activeJobs[entity.trackId] = job
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun DownloadEntity.toDomain(): DownloadItem {
        val status = DownloadStatus.entries.firstOrNull { it.name == statusName } ?: DownloadStatus.FAILED
        val quality = AudioQuality.entries.firstOrNull { it.name == qualityName } ?: AudioQuality.HIGH
        return DownloadItem(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            streamUrl = streamUrl,
            localFilePath = localFilePath,
            quality = quality,
            status = status,
            progressPercent = progressPercent,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            sha256Hex = sha256Hex,
            licenseName = licenseName,
            errorMessage = errorMessage
        )
    }
}
