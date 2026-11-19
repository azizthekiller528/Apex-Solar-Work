package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageStorageHelper {

    private const val DOCS_DIR = "docfolio"
    private const val CAPTURES_DIR = "captures"
    private const val MAX_SCAN_EDGE = 1650
    private const val MAX_PREVIEW_EDGE = 480
    private const val MAX_PREVIEW_BASE64_LENGTH = 140_000

    fun getDocsDirectory(context: Context): File {
        return File(context.filesDir, DOCS_DIR).apply { if (!exists()) mkdirs() }
    }

    fun createTempCaptureFile(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAPTURES_DIR).apply { if (!exists()) mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    suspend fun loadOrientedBitmapFromUri(context: Context, uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_SCAN_EDGE)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalStateException("Could not read selected image.")

            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                readExifOrientation(stream)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val oriented = applyExifOrientation(rawBitmap, orientation)
            scaleDownIfNeeded(oriented, MAX_SCAN_EDGE)
        }

    suspend fun loadOrientedBitmapFromAsset(context: Context, assetPath: String): Bitmap =
        withContext(Dispatchers.IO) {
            val rawBitmap = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Could not load bundled sample form: $assetPath")
            scaleDownIfNeeded(rawBitmap, MAX_SCAN_EDGE)
        }

    suspend fun saveScanBitmapLocally(context: Context, fileName: String, bitmap: Bitmap): File =
        withContext(Dispatchers.IO) {
            val file = File(getDocsDirectory(context), fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file
        }

    fun getLocalFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(getDocsDirectory(context), fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun deleteLocalFiles(context: Context, imageFileName: String, pdfFileName: String) {
        if (imageFileName.isNotBlank()) {
            File(getDocsDirectory(context), imageFileName).delete()
        }
        if (pdfFileName.isNotBlank()) {
            File(getDocsDirectory(context), pdfFileName).delete()
        }
    }

    suspend fun loadDocumentBitmap(
        context: Context,
        localImageFileName: String,
        previewBase64: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val local = getLocalFile(context, localImageFileName)
        if (local != null) {
            val decoded = BitmapFactory.decodeFile(local.absolutePath)
            if (decoded != null) return@withContext decoded
        }
        decodeBase64Preview(previewBase64)
    }

    suspend fun encodeCompactPreviewBase64(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val thumb = scaleDownIfNeeded(bitmap, MAX_PREVIEW_EDGE)
            var quality = 72
            var encoded = ""
            while (quality >= 35) {
                val baos = ByteArrayOutputStream()
                thumb.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= MAX_PREVIEW_BASE64_LENGTH) break
                quality -= 12
            }
            if (encoded.length > MAX_PREVIEW_BASE64_LENGTH) "" else encoded
        }

    fun decodeBase64Preview(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifOrientation(inputStream: InputStream): Int {
        return try {
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val newW = max(1, (bitmap.width * ratio).toInt())
        val newH = max(1, (bitmap.height * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return min(sample, 16)
    }
}
