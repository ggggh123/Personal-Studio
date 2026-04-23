package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the document quadrilateral with a two-stage strategy:
 *
 *  1. **Color-distance segmentation** (primary) — estimate the background color
 *     from the image border, build a foreground mask from per-pixel color
 *     distance, take the largest connected component, approximate its convex
 *     hull. This is robust to low contrast (Lab/RGB distance still separates
 *     similar-but-not-identical hues) and to back-page bleed-through (internal
 *     text is part of the same blob as the paper it's written on).
 *
 *  2. **Canny + contour approx** (fallback) — the classical pipeline, tuned with
 *     CLAHE + Otsu-adaptive thresholds + dilation + multi-epsilon approx. Used
 *     when (1) can't find a valid 4-corner convex quadrilateral (e.g. when the
 *     paper color genuinely matches the surface).
 *
 * Final candidate is accepted only if it passes [isPlausibleDocument] — convex,
 * minimum area fraction, sensible aspect ratio, and all corner angles within
 * a generous rectangular range.
 */
@Singleton
class OpenCvEdgeDetector @Inject constructor() : EdgeDetector {

    override suspend fun detectQuadrilateral(bitmap: Bitmap): List<PointF>? =
        withContext(Dispatchers.Default) {
            OpenCvInitializer.ensureInitialized()

            val src = Mat().also { Utils.bitmapToMat(bitmap, it) }
            try {
                val scaleRatio = min(bitmap.width, bitmap.height).toDouble() / DETECT_SHORT_SIDE
                val small = Mat()
                Imgproc.resize(
                    src, small,
                    Size(bitmap.width / scaleRatio, bitmap.height / scaleRatio),
                )

                val imageArea = (small.rows() * small.cols()).toDouble()
                val minArea = imageArea * AREA_FRACTION_MIN

                val primary = detectByColorSegmentation(small, minArea)
                if (primary != null) {
                    Log.d(TAG, "detected via color-segmentation")
                    return@withContext primary.scale(scaleRatio)
                }

                val fallback = detectByCanny(small, minArea)
                if (fallback != null) {
                    Log.d(TAG, "detected via canny fallback")
                    return@withContext fallback.scale(scaleRatio)
                }

                Log.d(TAG, "no quadrilateral found")
                small.release()
                null
            } finally {
                src.release()
            }
        }

    // ── Primary: color-distance segmentation ──────────────────────────────────

    private fun detectByColorSegmentation(small: Mat, minArea: Double): List<PointF>? {
        val rgb = Mat()
        Imgproc.cvtColor(small, rgb, Imgproc.COLOR_RGBA2RGB)

        // Sample a thin strip on all four borders — assume the surface
        // (not the paper) fills most of that strip, take its mean as bg color.
        val bgColor = estimateBorderMeanColor(rgb)

        // Per-pixel color distance to bg, projected onto a single-channel map
        // (cvtColor BGR2GRAY weights the channels reasonably for "brightness of
        // difference"; it approximates luminance distance).
        val diff = Mat()
        Core.absdiff(rgb, bgColor, diff)
        val dist = Mat()
        Imgproc.cvtColor(diff, dist, Imgproc.COLOR_RGB2GRAY)
        diff.release()

        // Otsu splits dist into "near-bg" vs "far-from-bg" automatically.
        val mask = Mat()
        Imgproc.threshold(dist, mask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        dist.release()

        // Seal any gaps inside the document (bleed-through can punch holes in
        // the mask if a text area happens to land near the bg color) and drop
        // tiny noise blobs outside it.
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 2)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel, Point(-1.0, -1.0), 1)
        kernel.release()

        val quad = largestBlobQuadrilateral(mask, minArea)
        rgb.release(); mask.release()
        return quad
    }

    private fun estimateBorderMeanColor(rgb: Mat): Scalar {
        val h = rgb.rows()
        val w = rgb.cols()
        val strip = (min(h, w) * BORDER_STRIP_FRACTION).toInt().coerceAtLeast(4)

        val top = Mat(rgb, Rect(0, 0, w, strip))
        val bottom = Mat(rgb, Rect(0, h - strip, w, strip))
        val left = Mat(rgb, Rect(0, 0, strip, h))
        val right = Mat(rgb, Rect(w - strip, 0, strip, h))

        // Mean of each strip, averaged — keeps the estimate stable if one side
        // is occluded (e.g. paper touches the frame on one edge).
        val means = listOf(top, bottom, left, right).map { Core.mean(it) }
        top.release(); bottom.release(); left.release(); right.release()

        val r = means.map { it.`val`[0] }.average()
        val g = means.map { it.`val`[1] }.average()
        val b = means.map { it.`val`[2] }.average()
        return Scalar(r, g, b)
    }

    private fun largestBlobQuadrilateral(mask: Mat, minArea: Double): List<PointF>? {
        val labels = Mat(); val stats = Mat(); val centroids = Mat()
        val n = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids)

        try {
            if (n <= 1) return null

            val imageArea = (mask.rows() * mask.cols()).toDouble()
            val imageCx = mask.cols() / 2.0
            val imageCy = mask.rows() / 2.0
            val maxCenterDist = hypot(imageCx, imageCy) * CENTER_BIAS_RADIUS_FRACTION

            var bestLabel = -1
            var bestScore = Double.NEGATIVE_INFINITY
            for (i in 1 until n) {           // skip label 0 = background
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0]
                if (area < minArea) continue
                val cx = centroids.get(i, 0)[0]
                val cy = centroids.get(i, 1)[0]
                val centerDist = hypot(cx - imageCx, cy - imageCy)
                // Score prefers big blobs that are also near-centered.
                val score = area / imageArea - centerDist / maxCenterDist * 0.3
                if (score > bestScore) {
                    bestScore = score
                    bestLabel = i
                }
            }
            if (bestLabel < 0) return null

            val blob = Mat()
            Core.compare(labels, Scalar(bestLabel.toDouble()), blob, Core.CMP_EQ)

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(blob, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            blob.release()
            if (contours.isEmpty()) return null

            val largest = contours.maxBy { Imgproc.contourArea(it) }
            val quad = approxContourToQuad(largest, minArea)
            contours.forEach { it.release() }
            return quad
        } finally {
            labels.release(); stats.release(); centroids.release()
        }
    }

    // ── Fallback: classical Canny + contour approx ────────────────────────────

    private fun detectByCanny(small: Mat, minArea: Double): List<PointF>? {
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)

        val clahe = Imgproc.createCLAHE(CLAHE_CLIP, Size(8.0, 8.0))
        clahe.apply(gray, gray)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val dummy = Mat()
        val otsu = Imgproc.threshold(
            blurred, dummy, 0.0, 255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
        )
        dummy.release()
        val upper = otsu.coerceIn(40.0, 255.0)
        val lower = (upper * 0.5).coerceAtLeast(10.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, lower, upper)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), 1)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val best = contours
            .map { it to Imgproc.contourArea(it) }
            .filter { it.second >= minArea }
            .sortedByDescending { it.second }
            .take(TOP_CONTOUR_COUNT)
            .firstNotNullOfOrNull { (c, _) -> approxContourToQuad(c, minArea) }

        gray.release(); blurred.release(); edges.release(); kernel.release()
        contours.forEach { it.release() }
        return best
    }

    // ── Shared: contour → 4-point approximation with validation ───────────────

    private fun approxContourToQuad(contour: MatOfPoint, minArea: Double): List<PointF>? {
        // Take the convex hull first — smooths wobble from broken/noisy edges
        // and makes approxPolyDP far more likely to settle on 4 corners.
        val hullIdx = MatOfInt()
        Imgproc.convexHull(contour, hullIdx)
        val cArr = contour.toArray()
        val hullPoints = hullIdx.toArray().map { cArr[it] }.toTypedArray()
        hullIdx.release()
        val hull2f = MatOfPoint2f(*hullPoints)

        val peri = Imgproc.arcLength(hull2f, true)
        if (peri <= 0.0) { hull2f.release(); return null }

        for (eps in EPSILON_FACTORS) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(hull2f, approx, eps * peri, true)
            if (approx.total() == 4L) {
                val pts = approx.toArray().map { PointF(it.x.toFloat(), it.y.toFloat()) }
                val ordered = orderTlTrBrBl(pts)
                approx.release()
                if (isPlausibleDocument(ordered, minArea)) {
                    hull2f.release()
                    return ordered
                }
            } else {
                approx.release()
            }
        }
        hull2f.release()
        return null
    }

    /** Reject quadrilaterals that aren't plausibly rectangular documents. */
    private fun isPlausibleDocument(ordered: List<PointF>, minArea: Double): Boolean {
        // Shoelace for area — signed, abs guards against CW/CCW orientation.
        val a = abs(
            (ordered[0].x * ordered[1].y - ordered[1].x * ordered[0].y) +
                (ordered[1].x * ordered[2].y - ordered[2].x * ordered[1].y) +
                (ordered[2].x * ordered[3].y - ordered[3].x * ordered[2].y) +
                (ordered[3].x * ordered[0].y - ordered[0].x * ordered[3].y)
        ) * 0.5
        if (a < minArea) return false

        // Aspect ratio sanity (compare mean of top/bottom widths to mean of
        // left/right heights).
        val wTop = dist(ordered[0], ordered[1])
        val wBot = dist(ordered[3], ordered[2])
        val hLeft = dist(ordered[0], ordered[3])
        val hRight = dist(ordered[1], ordered[2])
        val w = (wTop + wBot) / 2
        val h = (hLeft + hRight) / 2
        if (w <= 0 || h <= 0) return false
        val ar = w / h
        if (ar < ASPECT_MIN || ar > ASPECT_MAX) return false

        // All four interior angles must fall in [CORNER_MIN, CORNER_MAX].
        for (i in 0..3) {
            val prev = ordered[(i + 3) % 4]
            val curr = ordered[i]
            val next = ordered[(i + 1) % 4]
            val ang = angleAt(prev, curr, next)
            if (ang < CORNER_MIN || ang > CORNER_MAX) return false
        }
        return true
    }

    private fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    /** Interior angle at [curr] formed by curr→prev and curr→next, in degrees. */
    private fun angleAt(prev: PointF, curr: PointF, next: PointF): Double {
        val v1x = (prev.x - curr.x).toDouble(); val v1y = (prev.y - curr.y).toDouble()
        val v2x = (next.x - curr.x).toDouble(); val v2y = (next.y - curr.y).toDouble()
        val n1 = sqrt(v1x * v1x + v1y * v1y)
        val n2 = sqrt(v2x * v2x + v2y * v2y)
        if (n1 == 0.0 || n2 == 0.0) return 0.0
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    private fun orderTlTrBrBl(pts: List<PointF>): List<PointF> {
        val sums = pts.sortedBy { it.x + it.y }
        val diffs = pts.sortedBy { it.x - it.y }
        return listOf(sums.first(), diffs.last(), sums.last(), diffs.first())
    }

    private fun List<PointF>.scale(ratio: Double): List<PointF> =
        map { PointF((it.x * ratio).toFloat(), (it.y * ratio).toFloat()) }

    companion object {
        private const val TAG = "OpenCvEdgeDetector"
        const val DETECT_SHORT_SIDE = 1000

        private const val AREA_FRACTION_MIN = 0.15
        private const val BORDER_STRIP_FRACTION = 0.03
        private const val CENTER_BIAS_RADIUS_FRACTION = 1.0
        private const val TOP_CONTOUR_COUNT = 8
        private val EPSILON_FACTORS = doubleArrayOf(0.015, 0.02, 0.025, 0.03, 0.04, 0.05)
        private const val CLAHE_CLIP = 2.0

        // Plausibility bounds — tuned to accept typical A4/letter at 30° yaw.
        private const val ASPECT_MIN = 0.3
        private const val ASPECT_MAX = 3.0
        private const val CORNER_MIN = 55.0
        private const val CORNER_MAX = 125.0
    }
}
