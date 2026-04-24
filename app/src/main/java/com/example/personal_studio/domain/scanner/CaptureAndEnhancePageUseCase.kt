package com.example.personal_studio.domain.scanner

import android.graphics.PointF
import com.example.personal_studio.data.scanner.BitmapIo
import com.example.personal_studio.data.scanner.EnhancePipeline
import com.example.personal_studio.domain.model.ScanFilter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Given a freshly captured tmp photo + the user's confirmed 4 corners + filter
 * selection, produces the orig + enhanced JPGs on disk under [dir] and returns
 * their paths. Does NOT touch the DB; the caller decides whether to append to
 * a scan doc, replace an existing page, or surface the path to chat.
 *
 * [dir] is typically `repo.documentDir(docId)` for scan-library captures, or a
 * scratch directory under `filesDir/scans/tmp-chat/` for one-shot chat uses.
 */
class CaptureAndEnhancePageUseCase @Inject constructor(
    private val pipeline: EnhancePipeline,
) {
    data class Result(
        val originalImagePath: String,
        val enhancedImagePath: String,
        val cornersJson: String,
        val filter: ScanFilter,
    )

    suspend operator fun invoke(
        dir: File,
        tmpCaptureFile: File,
        corners: List<PointF>,
        filter: ScanFilter,
    ): Result {
        dir.mkdirs()
        val stem = UUID.randomUUID().toString().take(8)

        val rawBmp = BitmapIo.decodeDownscaled(tmpCaptureFile)
        val warpedColor = pipeline.warpAndFilter(rawBmp, corners, ScanFilter.COLOR)
        val origFile = BitmapIo.writeJpeg(warpedColor, File(dir, "page-$stem-orig.jpg"))

        val cooked = if (filter == ScanFilter.COLOR) warpedColor else pipeline.applyFilter(warpedColor, filter)
        val cookedFile =
            if (filter == ScanFilter.COLOR) origFile
            else BitmapIo.writeJpeg(cooked, File(dir, "page-$stem-cooked.jpg"))

        val cornersJson = Json.encodeToString(corners.map { listOf(it.x, it.y) })

        // Sweep the tmp capture — it's been fully consumed
        runCatching { tmpCaptureFile.delete() }

        return Result(origFile.absolutePath, cookedFile.absolutePath, cornersJson, filter)
    }
}
