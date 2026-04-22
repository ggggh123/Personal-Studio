package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF

interface EdgeDetector {
    /** Detect the dominant 4-corner quadrilateral in [bitmap]. Corners returned
     *  in pixel space of [bitmap], ordered TL/TR/BR/BL. Returns null if nothing
     *  plausible is found (caller should fall back to a default box). */
    suspend fun detectQuadrilateral(bitmap: Bitmap): List<PointF>?
}
