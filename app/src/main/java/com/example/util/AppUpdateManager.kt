package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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

sealed interface DownloadStatus {
    data class Running(val percent: Int) : DownloadStatus
    data class Completed(val file: File?) : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus
}

object AppUpdateManager {
    private const val TAG = "ApexAppUpdateManager"
    private const val PRIMARY_MANIFEST_URL = "https://docfolio.130.210.30.151.sslip.io/api/documents/apex_update_manifest/photo?token=docfolio_secret_test_key_99812"
    private const val FALLBACK_MANIFEST_URL = "https://raw.githubusercontent.com/azizthekiller528/Apex-Solar-Work/main/app-version.json"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
     * Starts a background download via Android's system DownloadManager service.
     * This runs outside the app process, so minimizing the app or turning off the screen
     * will never cancel or abort the download.
     */
    fun startBackgroundDownload(context: Context, info: AppUpdateInfo): Result<Long> {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return Result.failure(IllegalStateException("DownloadManager service not available"))

            val fileName = "ApexSolarWork-v${info.versionName}.apk"
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates").also { if (!it.exists()) it.mkdirs() }
            destDir.listFiles()?.forEach { existing ->
                if (existing.name.startsWith("ApexSolarWork-v") && existing.name.endsWith(".apk")) {
                    runCatching { existing.delete() }
                }
            }
            val destFile = File(destDir, fileName)

            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Apex Solar Work v${info.versionName}")
                .setDescription("Downloading update in background...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "Enqueued DownloadManager download ID $downloadId for ${info.apkUrl}")
            Result.success(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue DownloadManager download", e)
            Result.failure(e)
        }
    }

    /**
     * Queries the status of an ongoing DownloadManager task.
     */
    fun queryDownloadStatus(
        context: Context,
        downloadId: Long,
        expectedFileName: String = ""
    ): DownloadStatus {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return DownloadStatus.Failed("DownloadManager not available")

        fun resolveExpectedFile(): File? {
            if (expectedFileName.isBlank()) return null
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates")
            val f = File(destDir, expectedFileName)
            return if (f.exists() && f.length() > 1_000_000L) f else null
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        try {
            dm.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx < 0) return DownloadStatus.Running(0)
                    val status = cursor.getInt(statusIdx)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val uriStr = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                            if (uriStr != null) {
                                val uri = Uri.parse(uriStr)
                                val path = uri.path
                                if (path != null) {
                                    val f = File(path)
                                    if (f.exists() && f.length() > 1_000_000L) return DownloadStatus.Completed(f)
                                }
                            }
                            return resolveExpectedFile()?.let { DownloadStatus.Completed(it) }
                                ?: DownloadStatus.Failed("Downloaded APK file incomplete or missing")
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            return DownloadStatus.Failed("Download failed (reason $reason)")
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                            val bytesSoFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalBytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val bytesSoFar = if (bytesSoFarIdx >= 0) cursor.getLong(bytesSoFarIdx) else 0L
                            val totalBytes = if (totalBytesIdx >= 0) cursor.getLong(totalBytesIdx) else 0L
                            val percent = if (totalBytes > 0) ((bytesSoFar * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            return DownloadStatus.Running(percent)
                        }
                        else -> return DownloadStatus.Running(0)
                    }
                } else {
                    return resolveExpectedFile()?.let { DownloadStatus.Completed(it) }
                        ?: DownloadStatus.Failed("Download was cancelled or cleared")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query download status", e)
            return resolveExpectedFile()?.let { DownloadStatus.Completed(it) }
                ?: DownloadStatus.Failed("Query error: ${e.message}")
        }
        return DownloadStatus.Running(0)
    }

    /**
     * Downloads the APK file into app's cache/updates directory with progress reporting (in-app fallback).
     */
    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()
        val targetFile = File(updatesDir, "ApexSolarWork-v${info.versionName}.apk")
        try {
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
                        val buffer = ByteArray(16384)
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

            if (!targetFile.exists() || targetFile.length() < 1_000_000L) {
                runCatching { targetFile.delete() }
                return@withContext Result.failure(IllegalStateException("Downloaded APK is incomplete"))
            }
            onProgress(100)
            Result.success(targetFile)
        } catch (e: CancellationException) {
            runCatching { targetFile.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
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
