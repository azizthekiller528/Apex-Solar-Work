package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages anonymous device heartbeat reporting for Apex Solar Work.
 *
 * Tracks active devices using the relay server document storage without loops,
 * continuous polling, or UI blocking. Enforces a 4-hour cooldown between reports.
 */
object DeviceTelemetryManager {
    private const val TAG = "ApexDeviceTelemetry"
    private const val PREFS_NAME = "apex_telemetry"
    private const val KEY_INSTALL_ID = "device_install_id"
    private const val KEY_LAST_PING_MILLIS = "last_ping_millis"
    private const val COOLDOWN_MILLIS = 4 * 60 * 60 * 1000L // 4 hours

    private const val BASE_URL = "https://docfolio.130.210.30.151.sslip.io"
    private const val REGISTRY_DOC_ID = "apex_device_registry"
    private const val RELAY_SECRET = "docfolio_secret_test_key_99812"

    @Volatile
    private var hasAttemptedThisSession = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Triggers an asynchronous heartbeat report.
     * Safe to call from UI or ViewModel init; exits immediately if within cooldown period.
     */
    fun recordHeartbeatAsync(context: Context, scope: CoroutineScope) {
        if (hasAttemptedThisSession) return
        hasAttemptedThisSession = true

        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                sendHeartbeatIfDue(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Heartbeat skipped or failed: ${e.message}")
            }
        }
    }

    /**
     * Evaluates cooldown and submits heartbeat if due.
     */
    suspend fun sendHeartbeatIfDue(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastPing = prefs.getLong(KEY_LAST_PING_MILLIS, 0L)

        if (now - lastPing < COOLDOWN_MILLIS) {
            return@withContext false
        }

        var installId = prefs.getString(KEY_INSTALL_ID, null)
        if (installId.isNullOrBlank()) {
            installId = "dev_" + UUID.randomUUID().toString().replace("-", "").take(10)
            prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
        }

        val success = reportDeviceHeartbeat(
            cacheDir = context.cacheDir,
            deviceId = installId,
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Device",
            androidVersion = Build.VERSION.RELEASE ?: "",
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )

        if (success) {
            prefs.edit().putLong(KEY_LAST_PING_MILLIS, now).apply()
        }
        success
    }

    /**
     * Pure worker to fetch registry, update current device, and re-upload.
     */
    internal fun reportDeviceHeartbeat(
        cacheDir: File,
        deviceId: String,
        manufacturer: String,
        model: String,
        androidVersion: String,
        versionName: String,
        versionCode: Int
    ): Boolean {
        val nowIso = currentUtcIso()
        var tempFile: File? = null

        return try {
            // 1. Fetch current registry
            val getUrl = "$BASE_URL/api/documents/$REGISTRY_DOC_ID/photo?token=$RELAY_SECRET"
            val getReq = Request.Builder().url(getUrl).build()
            val existingJson = try {
                httpClient.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "{}" else "{}"
                }
            } catch (_: Exception) {
                "{}"
            }

            // 2. Parse and merge device entry
            val updatedJsonString = mergeDeviceRecord(
                existingJson = existingJson,
                deviceId = deviceId,
                deviceModel = "$manufacturer $model".trim(),
                androidVersion = androidVersion,
                versionName = versionName,
                versionCode = versionCode,
                nowIso = nowIso
            )

            // 3. Write to temporary file for multipart upload
            tempFile = File.createTempFile("reg_upload_", ".json", cacheDir)
            tempFile.writeText(updatedJsonString)

            // 4. Upload updated registry
            val uploadUrl = "$BASE_URL/api/documents/$REGISTRY_DOC_ID/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file_type", "photo")
                .addFormDataPart(
                    "file",
                    "registry.json",
                    tempFile.asRequestBody("application/json".toMediaTypeOrNull())
                )
                .build()

            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $RELAY_SECRET")
                .post(requestBody)
                .build()

            httpClient.newCall(uploadReq).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload heartbeat: ${e.message}")
            false
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Pure testable JSON merger.
     */
    internal fun mergeDeviceRecord(
        existingJson: String,
        deviceId: String,
        deviceModel: String,
        androidVersion: String,
        versionName: String,
        versionCode: Int,
        nowIso: String
    ): String {
        val root = try {
            JSONObject(existingJson)
        } catch (_: Exception) {
            JSONObject()
        }

        val devices = root.optJSONObject("devices") ?: JSONObject().also { root.put("devices", it) }
        val existingDevice = devices.optJSONObject(deviceId)

        val firstSeen = existingDevice?.optString("firstSeen")?.takeIf { it.isNotBlank() } ?: nowIso
        val pingCount = (existingDevice?.optInt("pingCount", 0) ?: 0) + 1

        val deviceObj = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceModel", deviceModel)
            put("androidVersion", androidVersion)
            put("appVersion", versionName)
            put("appBuild", versionCode)
            put("firstSeen", firstSeen)
            put("lastSeen", nowIso)
            put("pingCount", pingCount)
        }

        devices.put(deviceId, deviceObj)

        root.put("totalDevices", devices.length())
        root.put("lastUpdated", nowIso)

        return root.toString(2)
    }

    private fun currentUtcIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
