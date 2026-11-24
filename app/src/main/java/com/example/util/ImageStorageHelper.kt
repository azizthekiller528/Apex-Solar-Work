package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.MediaRelayService
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object ImageStorageHelper {

    private const val DOCS_DIR = "docfolio"
    private const val CAPTURES_DIR = "captures"
    private const val THUMBS_DIR = "doc_thumbs"
    private const val MAX_SCAN_EDGE = 1650
    private const val MAX_PREVIEW_EDGE = 800
    private const val MAX_PREVIEW_BASE64_LENGTH = 250_000

    // O(1) in-memory map of cacheKey -> verified File to avoid main-thread disk checks and scroll flicker
    private val thumbnailFileIndex = ConcurrentHashMap<String, File>()

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

    fun getLocalFile(context: Context, fileName: String, docId: String = ""): File? {
        if (fileName.isBlank() && docId.isBlank()) return null
        val docsDir = getDocsDirectory(context)
        val candidateNames = linkedSetOf<String>()
        if (fileName.isNotBlank()) {
            candidateNames.add(fileName)
            if (!fileName.endsWith(".jpg")) candidateNames.add("$fileName.jpg")
        }
        if (docId.isNotBlank()) {
            candidateNames.add("$docId.jpg")
            candidateNames.add(docId)
        }

        // 1. Check if already exists on disk
        for (name in candidateNames) {
            val file = File(docsDir, name)
            if (file.exists() && file.length() > 0L) return file
        }

        // 2. Fallback: Check if bundled in assets/seed_photos
        for (name in candidateNames) {
            val destFile = File(docsDir, if (name.endsWith(".jpg")) name else "$name.jpg")
            try {
                context.assets.open("seed_photos/$name").use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0L) return destFile
            } catch (_: Exception) {}
            if (!name.endsWith(".jpg")) {
                try {
                    context.assets.open("seed_photos/$name.jpg").use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (destFile.exists() && destFile.length() > 0L) return destFile
                } catch (_: Exception) {}
            }
        }

        // 3. Match prefix in seed_photos (e.g. docId = "apex_doc_01" matches "apex_doc_01_slip_13703.jpg")
        if (docId.isNotBlank()) {
            try {
                val seedPhotos = context.assets.list("seed_photos") ?: emptyArray()
                val matched = seedPhotos.firstOrNull { it.startsWith(docId) || docId.startsWith(it.removeSuffix(".jpg")) }
                if (matched != null) {
                    val destFile = File(docsDir, matched)
                    if (!destFile.exists() || destFile.length() == 0L) {
                        context.assets.open("seed_photos/$matched").use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) return destFile
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun deleteLocalFiles(
        context: Context,
        imageFileName: String,
        pdfFileName: String,
        docId: String = ""
    ) {
        if (imageFileName.isNotBlank()) {
            File(getDocsDirectory(context), imageFileName).delete()
        }
        if (pdfFileName.isNotBlank()) {
            File(getDocsDirectory(context), pdfFileName).delete()
        }
        if (docId.isNotBlank()) {
            val keyPrefix = "doc_thumb_${docId}_"
            thumbnailFileIndex.keys.removeIf { it.startsWith(keyPrefix) }
            val safePrefix = "thumb_${sanitizeFileToken(docId)}_"
            val thumbsDir = File(context.cacheDir, THUMBS_DIR)
            thumbsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(safePrefix)) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    /**
     * Non-blocking O(1) RAM lookup for a previously verified thumbnail source file.
     * Prevents a 1-frame placeholder flash when LazyColumn items scroll back into view.
     */
    fun peekThumbnailSourceFile(cacheKey: String): File? = thumbnailFileIndex[cacheKey]

    fun invalidateThumbnailSource(cacheKey: String) {
        thumbnailFileIndex.remove(cacheKey)
    }

    /**
     * Resolves a File source on Dispatchers.IO for Coil to downsample into a low-resolution thumbnail:
     * 1. Prefers the local scanned image file (zero decoding here; Coil subsamples directly from disk).
     * 2. Falls back to materializing the cloud-synced Base64 preview into an atomic cached file.
     */
    suspend fun resolveThumbnailSourceFile(
        context: Context,
        docId: String,
        localImageFileName: String,
        previewBase64: String,
        cacheKey: String
    ): File? = withContext(Dispatchers.IO) {
        val indexed = thumbnailFileIndex[cacheKey]
        if (indexed != null && indexed.exists() && indexed.length() > 0L) {
            return@withContext indexed
        } else if (indexed != null) {
            thumbnailFileIndex.remove(cacheKey, indexed)
        }

        val localFile = getLocalFile(context, localImageFileName, docId)
        if (localFile != null) {
            thumbnailFileIndex[cacheKey] = localFile
            return@withContext localFile
        }

        if (previewBase64.isBlank()) return@withContext null

        val thumbsDir = File(context.cacheDir, THUMBS_DIR).apply { if (!exists()) mkdirs() }
        val safeDocToken = sanitizeFileToken(docId)
        val targetFile = File(thumbsDir, "thumb_${safeDocToken}_${previewBase64.hashCode().toUInt()}.jpg")
        if (targetFile.exists() && targetFile.length() > 0L) {
            thumbnailFileIndex[cacheKey] = targetFile
            return@withContext targetFile
        }

        val bytes = try {
            Base64.decode(previewBase64, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
        if (bytes == null || bytes.isEmpty()) return@withContext null

        val tmpFile = File(thumbsDir, "${targetFile.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmpFile).use { out -> out.write(bytes) }
            if (!tmpFile.renameTo(targetFile)) {
                // Another coroutine may have created targetFile concurrently
                tmpFile.delete()
                if (!targetFile.exists() || targetFile.length() <= 0L) {
                    return@withContext null
                }
            }
            thumbnailFileIndex[cacheKey] = targetFile
            targetFile
        } catch (_: Exception) {
            runCatching { tmpFile.delete() }
            null
        }
    }

    private fun sanitizeFileToken(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48).ifBlank { "doc" }
    }

    suspend fun loadDocumentBitmap(
        context: Context,
        localImageFileName: String,
        previewBase64: String,
        docId: String = ""
    ): Bitmap? = withContext(Dispatchers.IO) {
        val local = getLocalFile(context, localImageFileName, docId)
        if (local != null) {
            val decoded = BitmapFactory.decodeFile(local.absolutePath)
            if (decoded != null) return@withContext decoded
        }
        if (docId.isNotBlank()) {
            val destName = localImageFileName.ifBlank { "${docId}.jpg" }
            val downloadRes = MediaRelayService.downloadMediaToLocal(context, docId, "photo", destName)
            val downloadedFile = downloadRes.getOrNull()
            if (downloadedFile != null && downloadedFile.exists()) {
                val decoded = BitmapFactory.decodeFile(downloadedFile.absolutePath)
                if (decoded != null) return@withContext decoded
            }
        }
        decodeBase64Preview(previewBase64)
    }

    /**
     * Saves a full-resolution bitmap to the device public Gallery (Pictures/ApexSolar).
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifBlank { "slip_photo" }
        val filename = "${cleanTitle}_${System.currentTimeMillis()}.jpg"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ApexSolar")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Saves a generated PDF to the device public Downloads folder (Downloads/ApexSolar).
     */
    fun savePdfToDownloads(context: Context, pdfFile: File, title: String): Uri? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifBlank { "doc" }
        val filename = "${cleanTitle}.pdf"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ApexSolar")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        } ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                pdfFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Bulk exports all documents (photos and PDFs) into public Gallery and Downloads.
     */
    suspend fun exportAllDocumentsToPhone(
        context: Context,
        documents: List<ScannedDocument>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var exportedPhotos = 0
        var exportedPdfs = 0
        val docsDir = getDocsDirectory(context)

        documents.forEachIndexed { index, doc ->
            onProgress(index + 1, documents.size)
            val imgFile = File(docsDir, doc.localImageFileName.ifBlank { "${doc.id}.jpg" })
            if (imgFile.exists() && imgFile.length() > 0L) {
                val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                if (bmp != null) {
                    val saved = saveBitmapToGallery(context, bmp, doc.title.ifBlank { doc.id })
                    if (saved != null) exportedPhotos++
                }
            }
            val pdfFile = File(docsDir, doc.localPdfFileName.ifBlank { "${doc.id}_searchable.pdf" })
            val effectivePdf = if (pdfFile.exists() && pdfFile.length() > 0L) {
                pdfFile
            } else {
                try {
                    SearchablePdfGenerator.generateDocumentPdf(
                        context = context,
                        document = doc,
                        scanBitmap = null,
                        targetFileName = doc.localPdfFileName.ifBlank { "${doc.id}_searchable.pdf" }
                    )
                } catch (_: Exception) { null }
            }
            if (effectivePdf != null && effectivePdf.exists()) {
                val saved = savePdfToDownloads(context, effectivePdf, doc.title.ifBlank { doc.id })
                if (saved != null) exportedPdfs++
            }
        }
        exportedPhotos to exportedPdfs
    }

    /**
     * Rotates an image file 90 degrees clockwise and updates it on disk and in memory.
     */
    suspend fun rotateImageFile90(
        context: Context,
        localImageFileName: String,
        currentBitmap: Bitmap?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val docsDir = getDocsDirectory(context)
        val file = File(docsDir, localImageFileName)
        val sourceBmp = if (file.exists() && file.length() > 0L) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            currentBitmap
        } ?: return@withContext null

        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(sourceBmp, 0, 0, sourceBmp.width, sourceBmp.height, matrix, true)

        val tmpFile = File(docsDir, "${file.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmpFile).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (tmpFile.exists() && tmpFile.length() > 0L) {
                tmpFile.renameTo(file)
            }
            thumbnailFileIndex.clear()
            ThumbnailImageLoader.clearCache(context)
        } catch (_: Exception) {
            runCatching { tmpFile.delete() }
        }
        rotated
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
