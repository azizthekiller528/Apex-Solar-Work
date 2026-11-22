package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client service for DocFolio Telegram Media Relay.
 *
 * Backs up full-resolution scanned photos and generated searchable PDFs
 * to the Telegram private channel storage via the relay server on the Oracle VM.
 */
object MediaRelayService {
    private const val TAG = "DocFolioMediaRelay"
    private const val BASE_URL = "https://docfolio.130.210.30.151.sslip.io"
    const val RELAY_INTERNAL_SECRET = "docfolio_secret_test_key_99812"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private suspend fun getAuthToken(): String {
        return try {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: RELAY_INTERNAL_SECRET
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Using internal relay token fallback: ${e.message}")
            RELAY_INTERNAL_SECRET
        }
    }

    /**
     * Uploads a document media file (photo or pdf) to the relay server.
     */
    suspend fun uploadMedia(
        docId: String,
        fileType: String,
        file: File
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("File does not exist or is empty: ${file.path}"))
        }

        val token = getAuthToken()

        val mimeType = if (fileType.equals("pdf", ignoreCase = true)) {
            "application/pdf"
        } else {
            "image/jpeg"
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file_type", fileType)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/documents/$docId/upload")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully backed up $fileType for doc $docId to Telegram relay")
                    Result.success(body)
                } else {
                    Log.w(TAG, "Relay upload failed ($fileType, code ${response.code}): $body")
                    Result.failure(RuntimeException("Relay error ${response.code}: $body"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Network error uploading $fileType for doc $docId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Backs up both the scanned photo and the generated PDF asynchronously in the background.
     */
    suspend fun backupDocument(
        docId: String,
        photoFile: File?,
        pdfFile: File?
    ) = withContext(Dispatchers.IO) {
        if (photoFile != null && photoFile.exists()) {
            runCatching { uploadMedia(docId, "photo", photoFile) }
        }
        if (pdfFile != null && pdfFile.exists()) {
            runCatching { uploadMedia(docId, "pdf", pdfFile) }
        }
    }

    /**
     * Deletes media associated with a document from the relay and Telegram channel.
     */
    suspend fun deleteDocumentMedia(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getAuthToken()

        val request = Request.Builder()
            .url("$BASE_URL/api/documents/$docId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Delete failed with code ${response.code}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a streaming URL for viewing or downloading the document media from the relay.
     */
    fun getMediaStreamUrl(docId: String, fileType: String, token: String? = null): String {
        val cleanType = fileType.trim().lowercase()
        val base = "$BASE_URL/api/documents/$docId/$cleanType"
        return if (token != null) "$base?token=$token" else base
    }
}
