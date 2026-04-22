package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF

class FakeEdgeDetector(private val result: List<PointF>? = null) : EdgeDetector {
    override suspend fun detectQuadrilateral(bitmap: Bitmap) = result
}
