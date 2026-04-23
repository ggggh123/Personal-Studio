package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.example.personal_studio.domain.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
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
            warpedMat.release(); output.release()
            srcQuad.release(); dstQuad.release(); transform.release()
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
            ScanFilter.GRAYSCALE -> enhancedSharpGrayscale(warped)
            ScanFilter.BW -> bwFilter(warped)
        }

    /**
     * CamScanner-style "增强锐化" path (GCMODE). Ported from
     * sourabhkhemka/DocumentScanner (which claims to mirror CamScanner /
     * Adobe Scan / MS Lens behaviour) — same parameters, same three stages:
     *
     *   1. **High-pass filter** (kernel 51): `img − boxFilter(img,51) + 127`.
     *      A mean (box) filter — NOT morph-close — estimates the local low-
     *      frequency (paper brightness + illumination) and we subtract it.
     *      The `+ 127` centres paper around mid-grey so the two contrast-
     *      stretches that follow have equal headroom on both ends. Earlier
     *      versions shifted by 255 ("paper stays near top"), leaving no room
     *      for the white-point pull.
     *   2. **White point 127**: truncate everything above 127 to 127, then
     *      linearly stretch `[0,127] → [0,255]`. This is exactly Photoshop's
     *      "pull the white slider in to 127".
     *   3. **Black point 66**: linearly stretch `[66,255] → [0,255]`, with
     *      values < 66 saturated to 0 by CV_8U. Darkens ink without bloating
     *      it — we don't sharpen strokes, we just crush shadows.
     *
     * Result: uniform white paper, deep black ink, anti-alias edges still
     * gradient (so thin strokes and small fonts stay crisp, unlike Sauvola).
     */
    private fun enhancedSharpGrayscale(warped: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_RGBA2GRAY)
        val out = gcmodePipeline(gray)
        val rgba = Mat()
        Imgproc.cvtColor(out, rgba, Imgproc.COLOR_GRAY2RGBA)
        gray.release(); out.release()
        return rgba
    }

    /**
     * Pure black-and-white path via **supersampled binarisation** — the
     * standard technique most document scanners use to produce a clean BW
     * that doesn't jag small fonts:
     *
     *   1. upscale 2× (INTER_CUBIC)
     *   2. run GCMODE at 2× (kernel scaled proportionally)
     *   3. Otsu threshold — histogram is near-bimodal after GCMODE, so the
     *      automatic level lands on the stroke/paper valley
     *   4. MORPH_OPEN 3×3 patches single-pixel paper specks inside strokes
     *   5. downsample back with INTER_AREA — pixel averaging turns the
     *      binary edge into gradient gray, which reads as smooth anti-alias
     *
     * Small fonts that were 2 source-pixels tall become 4 target-pixels at
     * 2×, so the threshold sees a meaningful neighbourhood and doesn't force
     * the whole character to one colour. The downsample then restores
     * resolution with soft edges.
     */
    private fun bwFilter(warped: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_RGBA2GRAY)
        val originalSize = Size(gray.cols().toDouble(), gray.rows().toDouble())

        val scale = BW_SUPERSAMPLE.toDouble()
        val upscaled = Mat()
        Imgproc.resize(gray, upscaled, Size(), scale, scale, Imgproc.INTER_CUBIC)
        // Drop the 1× gray immediately — at high supersample factors the
        // peak working set (float32 mats inside gcmodePipeline) is already
        // the memory bottleneck; we don't need gray again.
        gray.release()

        // Scale GCMODE kernel with the resolution so the spatial-frequency
        // cutoff stays equivalent to the 1× grayscale path we already tuned.
        val kernelSs = (GC_KERNEL * BW_SUPERSAMPLE).let { if (it % 2 == 0) it + 1 else it }
        val gcmode = gcmodePipeline(upscaled, kernelSs)
        upscaled.release()

        val bw = Mat()
        val otsuLevel = Imgproc.threshold(
            gcmode, bw, 0.0, 255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
        )
        gcmode.release()

        val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val cleaned = Mat()
        Imgproc.morphologyEx(bw, cleaned, Imgproc.MORPH_OPEN, morphKernel)
        bw.release(); morphKernel.release()

        val downsampled = Mat()
        Imgproc.resize(cleaned, downsampled, originalSize, 0.0, 0.0, Imgproc.INTER_AREA)
        cleaned.release()

        Log.d(
            TAG,
            "BW — supersample=${BW_SUPERSAMPLE}×, Otsu=$otsuLevel, " +
                "out=${downsampled.cols()}×${downsampled.rows()}",
        )

        val rgba = Mat()
        Imgproc.cvtColor(downsampled, rgba, Imgproc.COLOR_GRAY2RGBA)
        downsampled.release()
        return rgba
    }

    /** GCMODE: high-pass(kernel) + white-point(127) + black-point(66). */
    private fun gcmodePipeline(gray8u: Mat, kernel: Int = GC_KERNEL): Mat {
        val k = if (kernel % 2 == 0) kernel + 1 else kernel
        // Stage 1 — high-pass via box filter mean.
        val src32 = Mat(); gray8u.convertTo(src32, CvType.CV_32F)
        val lowPass = Mat()
        Imgproc.boxFilter(
            src32, lowPass, CvType.CV_32F,
            Size(k.toDouble(), k.toDouble()),
        )
        val highPass = Mat()
        Core.subtract(src32, lowPass, highPass)
        Core.add(highPass, Scalar(127.0), highPass)
        val hp8u = Mat()
        highPass.convertTo(hp8u, CvType.CV_8U)  // saturates to [0, 255]

        // Stage 2 — white point: truncate to 127 then linearly stretch to 255.
        val truncated = Mat()
        Imgproc.threshold(hp8u, truncated, GC_WHITE_POINT.toDouble(), 255.0, Imgproc.THRESH_TRUNC)
        val stretched = Mat()
        truncated.convertTo(stretched, CvType.CV_8U, 255.0 / GC_WHITE_POINT, 0.0)

        // Stage 3 — black point: linear remap [66,255] → [0,255]. Values
        // below 66 go negative → CV_8U saturation clamps them to 0.
        val alpha = 255.0 / (255.0 - GC_BLACK_POINT)
        val beta = -GC_BLACK_POINT * alpha
        val out = Mat()
        stretched.convertTo(out, CvType.CV_8U, alpha, beta)

        src32.release(); lowPass.release(); highPass.release()
        hp8u.release(); truncated.release(); stretched.release()
        return out
    }

    companion object {
        private const val TAG = "OpenCvEnhancePipeline"

        // GCMODE parameters — ported verbatim from
        // https://github.com/sourabhkhemka/DocumentScanner (scan.py). The
        // three are tightly coupled: kernel 51 sets the low-pass cutoff so
        // the text survives subtraction, and the 127 + 66 points crush the
        // dynamic range to the two extremes without pre-clipping anti-alias.
        private const val GC_KERNEL = 51
        private const val GC_WHITE_POINT = 127
        private const val GC_BLACK_POINT = 66

        // BW supersampling factor. 2× already smooths jaggies noticeably; 3×
        // pushes further. Memory-wise each bump squares the working set, and
        // the float32 mats inside gcmodePipeline at 3× on a 2000-px input
        // touch ~300 MB peak — still within a modern phone's app heap.
        private const val BW_SUPERSAMPLE = 3
    }
}
