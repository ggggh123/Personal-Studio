package com.example.personal_studio.feature.chat.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream

/**
 * Reads the image at [sourcePath], crops to [rect] in source-image pixel space, and
 * writes a new JPEG alongside it. Returns the new file's path.
 */
fun cropImageToFile(sourcePath: String, rect: Rect): String {
    val src = BitmapFactory.decodeFile(sourcePath)
        ?: error("Failed to decode image at $sourcePath")
    val safeRect = Rect(
        rect.left.coerceAtLeast(0),
        rect.top.coerceAtLeast(0),
        rect.right.coerceAtMost(src.width),
        rect.bottom.coerceAtMost(src.height),
    )
    val cropped = Bitmap.createBitmap(
        src,
        safeRect.left,
        safeRect.top,
        safeRect.width().coerceAtLeast(1),
        safeRect.height().coerceAtLeast(1),
    )
    val dest = File(File(sourcePath).parentFile, "${File(sourcePath).nameWithoutExtension}_crop.jpg")
    FileOutputStream(dest).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    src.recycle()
    cropped.recycle()
    return dest.absolutePath
}
