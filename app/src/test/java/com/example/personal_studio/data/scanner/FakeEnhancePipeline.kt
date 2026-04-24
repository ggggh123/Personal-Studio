package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.personal_studio.domain.model.ScanFilter

/** Returns the input unchanged. Fine for VM tests that don't care about pixels. */
class FakeEnhancePipeline : EnhancePipeline {
    override suspend fun warpAndFilter(original: Bitmap, corners: List<PointF>, filter: ScanFilter): Bitmap = original
    override suspend fun applyFilter(warped: Bitmap, filter: ScanFilter): Bitmap = warped
}
