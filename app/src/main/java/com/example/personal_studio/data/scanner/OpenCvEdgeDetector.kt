package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class OpenCvEdgeDetector @Inject constructor() : EdgeDetector {

    override suspend fun detectQuadrilateral(bitmap: Bitmap): List<PointF>? =
        withContext(Dispatchers.Default) {
            OpenCvInitializer.ensureInitialized()

            val src = Mat().also { Utils.bitmapToMat(bitmap, it) }
            try {
                // Downscale so short side = DETECT_SHORT_SIDE for speed
                val scaleRatio = min(bitmap.width, bitmap.height).toDouble() / DETECT_SHORT_SIDE
                val small = Mat()
                Imgproc.resize(
                    src, small,
                    Size(bitmap.width / scaleRatio, bitmap.height / scaleRatio),
                )

                val gray = Mat()
                Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
                val blurred = Mat()
                Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
                val edges = Mat()
                Imgproc.Canny(blurred, edges, 75.0, 200.0)

                val contours = mutableListOf<MatOfPoint>()
                Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

                val imageArea = small.rows() * small.cols()
                val best = contours
                    .asSequence()
                    .map { c ->
                        val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
                        val approx = MatOfPoint2f()
                        Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
                        approx to Imgproc.contourArea(c)
                    }
                    .filter { it.first.total() == 4L && it.second >= imageArea * 0.3 }
                    .maxByOrNull { it.second }

                gray.release(); blurred.release(); edges.release()
                contours.forEach { it.release() }

                val result = best?.let { (approx, _) ->
                    val pts = approx.toList().map { PointF((it.x * scaleRatio).toFloat(), (it.y * scaleRatio).toFloat()) }
                    orderTlTrBrBl(pts)
                }
                small.release()
                result
            } finally {
                src.release()
            }
        }

    /** Sort 4 points into TL/TR/BR/BL by comparing sums/diffs of (x+y)/(x-y). */
    private fun orderTlTrBrBl(pts: List<PointF>): List<PointF> {
        val sums = pts.sortedBy { it.x + it.y }
        val diffs = pts.sortedBy { it.x - it.y }
        return listOf(
            sums.first(),   // TL: smallest (x+y)
            diffs.last(),   // TR: largest (x - y)
            sums.last(),    // BR: largest (x+y)
            diffs.first(),  // BL: smallest (x - y)
        )
    }

    companion object {
        const val DETECT_SHORT_SIDE = 1000
    }
}
