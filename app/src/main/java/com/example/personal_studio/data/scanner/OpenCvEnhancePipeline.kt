package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.personal_studio.domain.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

@Singleton
class OpenCvEnhancePipeline @Inject constructor() : EnhancePipeline {

    override suspend fun warpAndFilter(
        original: Bitmap,
        corners: List<PointF>,
        filter: ScanFilter,
    ): Bitmap = withContext(Dispatchers.Default) {
        OpenCvInitializer.ensureInitialized()
        require(corners.size == 4) { "corners must have 4 points" }

        val src = Mat().also { Utils.bitmapToMat(original, it) }
        try {
            val (tl, tr, br, bl) = corners
            val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
            val widthBot = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
            val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
            val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
            val w = maxOf(widthTop, widthBot).toInt().coerceAtLeast(1)
            val h = maxOf(heightLeft, heightRight).toInt().coerceAtLeast(1)

            val srcQuad = MatOfPoint2f(
                Point(tl.x.toDouble(), tl.y.toDouble()),
                Point(tr.x.toDouble(), tr.y.toDouble()),
                Point(br.x.toDouble(), br.y.toDouble()),
                Point(bl.x.toDouble(), bl.y.toDouble()),
            )
            val dstQuad = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(w.toDouble(), 0.0),
                Point(w.toDouble(), h.toDouble()),
                Point(0.0, h.toDouble()),
            )
            val transform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
            val warpedMat = Mat(h, w, src.type())
            Imgproc.warpPerspective(src, warpedMat, transform, Size(w.toDouble(), h.toDouble()))

            val output = applyFilterMat(warpedMat, filter)
            val result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, result)
            warpedMat.release()
            output.release()
            srcQuad.release()
            dstQuad.release()
            transform.release()
            result
        } finally {
            src.release()
        }
    }

    override suspend fun applyFilter(warped: Bitmap, filter: ScanFilter): Bitmap =
        withContext(Dispatchers.Default) {
            OpenCvInitializer.ensureInitialized()
            val mat = Mat().also { Utils.bitmapToMat(warped, it) }
            try {
                val output = applyFilterMat(mat, filter)
                val result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(output, result)
                output.release()
                result
            } finally {
                mat.release()
            }
        }

    private fun applyFilterMat(warped: Mat, filter: ScanFilter): Mat =
        when (filter) {
            ScanFilter.COLOR -> warped.clone()
            ScanFilter.GRAYSCALE -> {
                val gray = Mat()
                Imgproc.cvtColor(warped, gray, Imgproc.COLOR_RGBA2GRAY)
                val boosted = Mat()
                gray.convertTo(boosted, -1, 1.3, 10.0)
                val rgba = Mat()
                Imgproc.cvtColor(boosted, rgba, Imgproc.COLOR_GRAY2RGBA)
                gray.release(); boosted.release()
                rgba
            }
            ScanFilter.BW -> {
                val gray = Mat()
                Imgproc.cvtColor(warped, gray, Imgproc.COLOR_RGBA2GRAY)
                val bw = Mat()
                Imgproc.adaptiveThreshold(
                    gray, bw, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    25, 10.0,
                )
                val rgba = Mat()
                Imgproc.cvtColor(bw, rgba, Imgproc.COLOR_GRAY2RGBA)
                gray.release(); bw.release()
                rgba
            }
        }
}
