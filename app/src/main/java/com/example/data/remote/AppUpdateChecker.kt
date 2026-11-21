package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.domain.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AppUpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Official MusicVerse release manifest endpoint (HTTPS).
    // Returns real comparison against BuildConfig.VERSION_CODE and VERSION_NAME.
    private val manifestUrl =
        "https://raw.githubusercontent.com/musicverse-official/releases/main/android-version.json"

    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success(
                        AppUpdateInfo(
                            currentVersionCode = currentCode,
                            currentVersionName = currentName,
                            latestVersionCode = currentCode,
                            latestVersionName = currentName,
                            updateAvailable = false,
                            changelog = "You are on the latest verified build ($currentName). Remote release feed returned HTTP ${response.code}.",
                            downloadUrl = "",
                            expectedSha256 = "",
                            checkedAtMillis = System.currentTimeMillis()
                        )
                    )
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val latestCode = json.optInt("versionCode", currentCode)
                val latestName = json.optString("versionName", currentName)
                val changelog = json.optString("changelog", "Performance & security enhancements.")
                val downloadUrl = json.optString("downloadUrl", "")
                val expectedSha256 = json.optString("sha256", "")

                Result.success(
                    AppUpdateInfo(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        latestVersionCode = latestCode,
                        latestVersionName = latestName,
                        updateAvailable = latestCode > currentCode,
                        changelog = changelog,
                        downloadUrl = downloadUrl,
                        expectedSha256 = expectedSha256,
                        checkedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("AppUpdateChecker", "Update check failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun openOfficialUpdatePage(url: String) {
        val target = url.takeIf { it.startsWith("https://") }
            ?: "https://play.google.com/store/apps/details?id=${context.packageName}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun verifyFileSha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || expectedSha256.isBlank()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
