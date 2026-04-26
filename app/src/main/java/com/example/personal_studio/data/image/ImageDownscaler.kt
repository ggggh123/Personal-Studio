package com.example.personal_studio.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Downsamples an image file to a target long-side size and re-encodes to JPEG
 * for sending to LLM vision models. Vision providers (OpenAI, Gemini, Claude)
 * internally cap input around 1024-1568 long side, so sending originals
 * (often 3000+ px from the scanner) wastes uplink bandwidth and tokens.
 *
 * Sample-decode (BitmapFactory.Options.inSampleSize) is used to avoid loading
 * the full bitmap into memory; the resulting bitmap is then either returned
 * as-is (if already ≤ target) or scaled with createScaledBitmap.
 */
object ImageDownscaler {

    /** Default long-side cap that matches typical vision-model intake. */
    const val DEFAULT_MAX_LONG_SIDE_PX = 1568

    /** Default JPEG quality. 85 is the sweet spot — ~80% of file-size savings of q70 with negligible visual difference. */
    const val DEFAULT_JPEG_QUALITY = 85

    /**
     * Read [path], downsample if larger than [maxLongSidePx], re-encode JPEG
     * at [quality], return the bytes ready to send. Throws IllegalArgumentException
     * if the file isn't decodable.
     */
    fun downscaleToBytes(
        path: String,
        maxLongSidePx: Int = DEFAULT_MAX_LONG_SIDE_PX,
        quality: Int = DEFAULT_JPEG_QUALITY,
    ): ByteArray {
        val file = File(path)
        require(file.exists()) { "image file not found: $path" }

        // 1. Probe dimensions cheaply (no pixel decode).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "could not decode image: $path" }

        // 2. Pick a power-of-2 sample factor so the half-decode lands at-or-above the target.
        //    Stops short of the target so the final scale step has fidelity headroom.
        var sample = 1
        while (maxOf(srcW, srcH) / (sample * 2) >= maxLongSidePx) {
            sample *= 2
        }

        // 3. Decode at the chosen sample factor.
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = BitmapFactory.decodeFile(path, opts)
            ?: error("BitmapFactory.decodeFile returned null for $path")

        // 4. If still too big after sampling, scale exactly to target.
        val sampledLong = maxOf(sampled.width, sampled.height)
        val finalBitmap = if (sampledLong <= maxLongSidePx) {
            sampled
        } else {
            val ratio = maxLongSidePx.toFloat() / sampledLong
            val targetW = (sampled.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (sampled.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            if (scaled !== sampled) sampled.recycle()
            scaled
        }

        // 5. JPEG-encode + recycle.
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        finalBitmap.recycle()
        return out.toByteArray()
    }
}
