package com.example.util

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import java.io.File

/**
 * Dedicated Coil ImageLoader & request builder for generating and caching small,
 * low-resolution document thumbnail previews in scrollable lists.
 *
 * Uses explicit low-res pixel bounds (160x208), INEXACT precision for fast hardware
 * subsampling (inSampleSize), and RGB_565 bitmaps to keep list scrolling smooth
 * with minimal RAM usage.
 */
object ThumbnailImageLoader {

    const val THUMBNAIL_WIDTH_PX = 160
    const val THUMBNAIL_HEIGHT_PX = 208
    private const val DISK_CACHE_DIR = "coil_thumbnails"
    private const val DISK_CACHE_MAX_BYTES = 20L * 1024L * 1024L // 20 MiB
    private const val MEMORY_CACHE_PERCENT = 0.15

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, DISK_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .crossfade(160)
            .allowHardware(true)
            .build()
    }

    /**
     * Builds a compact, deterministic cache key without retaining the full Base64 string.
     */
    fun buildCacheKey(
        docId: String,
        localImageFileName: String,
        previewBase64: String
    ): String {
        return buildString(64) {
            append("doc_thumb_")
            append(docId)
            append('_')
            append(localImageFileName.hashCode().toUInt())
            append('_')
            append(previewBase64.length)
            append('_')
            append(previewBase64.hashCode().toUInt())
        }
    }

    /**
     * Creates a Coil ImageRequest that downsamples the source image to a compact
     * low-resolution RGB_565 thumbnail and caches it in both memory and disk caches.
     */
    fun buildThumbnailRequest(
        context: Context,
        sourceFile: File,
        cacheKey: String
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(sourceFile)
            .size(Size(THUMBNAIL_WIDTH_PX, THUMBNAIL_HEIGHT_PX))
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(160)
            .build()
    }

    /**
     * Evicts a deleted or replaced document's thumbnail from Coil's memory cache.
     */
    fun evict(context: Context, cacheKey: String) {
        val loader = instance ?: return
        runCatching {
            loader.memoryCache?.remove(MemoryCache.Key(cacheKey))
        }
    }

    /**
     * Clears both memory and disk caches for thumbnails.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: Context) {
        val loader = get(context)
        runCatching {
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }
}
