package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.personal_studio.domain.model.ScanFilter

/**
 * Pure(-ish) image processing pipeline. Implementations may use native libs
 * (OpenCV) but must not touch disk, DB, or DI.
 */
interface EnhancePipeline {
    /** Warp [original] using [corners] (4 points TL/TR/BR/BL in pixel space of
     *  [original]) and apply [filter]. Returns a new Bitmap. Safe to run on
     *  Dispatchers.Default. */
    suspend fun warpAndFilter(
        original: Bitmap,
        corners: List<PointF>,
        filter: ScanFilter,
    ): Bitmap

    /** Re-apply [filter] to an already-warped bitmap (used when user toggles the
     *  filter on an existing page without re-running perspective correction). */
    suspend fun applyFilter(
        warped: Bitmap,
        filter: ScanFilter,
    ): Bitmap
}
