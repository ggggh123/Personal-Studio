package com.example.personal_studio.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Largest power-of-two sample size whose result still covers [targetPx] on the shorter
 * side, so thumbnails stay sharp without decoding a 12-megapixel photo at full size.
 */
fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    if (width <= 0 || height <= 0 || targetPx <= 0) return 1
    val shorter = minOf(width, height)
    var sample = 1
    while (shorter / (sample * 2) >= targetPx) sample *= 2
    return sample
}

/** Decoded thumbnails keyed by path, size and file time, so a rewritten file is decoded again. */
private object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
}

private fun decodeSampled(path: String, targetPx: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val key = "$path|$targetPx|${file.lastModified()}"
    ThumbnailCache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx) }
    val bmp = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull() ?: return null
    ThumbnailCache.put(key, bmp)
    return bmp
}

/**
 * The image at [path] decoded off the main thread at roughly [targetPx] (shorter side);
 * null while loading or when the file cannot be read.
 */
@Composable
fun rememberSampledBitmap(path: String?, targetPx: Int): ImageBitmap? {
    val bitmap = remember(path, targetPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, targetPx) {
        val decoded = if (path == null) null else withContext(Dispatchers.IO) { decodeSampled(path, targetPx) }
        bitmap.value = decoded?.asImageBitmap()
    }
    return bitmap.value
}
