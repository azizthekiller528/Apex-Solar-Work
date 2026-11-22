package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {
    private const val TAG = "ApexAppUpdateManager"
    private const val PRIMARY_MANIFEST_URL = "https://raw.githubusercontent.com/azizthekiller2/New-apk-/main/app-version.json"
    private const val FALLBACK_MANIFEST_URL = "https://docfolio.130.210.30.151.sslip.io/api/app/version"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Checks if a newer version of the app is available.
     * Returns AppUpdateInfo if remote versionCode > currentVersionCode, or null otherwise.
     */
    suspend fun checkForUpdate(currentVersionCode: Long): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        val urls = listOf(PRIMARY_MANIFEST_URL, FALLBACK_MANIFEST_URL)
        var lastException: Exception? = null

        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val remoteCode = json.optLong("versionCode", 0L)
                    val remoteName = json.optString("versionName", "")
                    val apkUrl = json.optString("apkUrl", "")
                    val notes = json.optString("releaseNotes", "")

                    if (remoteCode > currentVersionCode && apkUrl.isNotBlank()) {
                        val info = AppUpdateInfo(
                            versionCode = remoteCode,
                            versionName = remoteName,
                            apkUrl = apkUrl,
                            releaseNotes = notes
                        )
                        return@withContext Result.success(info)
                    } else {
                        return@withContext Result.success(null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed for $url: ${e.message}")
                lastException = e
            }
        }

        Result.failure(lastException ?: IllegalStateException("Unable to fetch update manifest"))
    }

    /**
     * Downloads the APK file into app's cache/updates directory with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val targetFile = File(updatesDir, "ApexSolarWork-v${info.versionName}.apk")
            if (targetFile.exists()) targetFile.delete()

            val request = Request.Builder().url(info.apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code} downloading update"))
                }
                val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty response body"))
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                onProgress(percent)
                            }
                        }
                        output.flush()
                    }
                }
            }

            onProgress(100)
            Result.success(targetFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the system package installer intent to install or update the APK.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalArgumentException("Downloaded APK file is missing or empty"))
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Result.failure(e)
        }
    }
}
