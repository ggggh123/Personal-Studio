package com.example.personal_studio.data.scanner

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML-based corner detector using DocsaidLab's DocAligner (heatmap regression,
 * FastViT-T8 backbone, 256×256 input). Model ported from
 * https://github.com/DocsaidLab/DocAligner (Apache-2.0).
 *
 * Pipeline (mirrors the upstream Python inference):
 *
 *   1. Resize input bitmap to 256×256, convert to float32 BGR-NCHW, /255.
 *   2. Run the ONNX session. Output: heatmap tensor shape (1, 4, H, W) —
 *      one 2-D confidence map per corner.
 *   3. For each corner's heatmap: upscale to the original image size,
 *      threshold at 0.3, binarise, find the largest contour, take its
 *      moment centroid → corner pixel coordinate.
 *   4. Reorder the 4 points into TL/TR/BR/BL.
 *
 * If the ONNX session fails to initialise (corrupt asset, unsupported
 * device) or inference throws, falls back to [OpenCvEdgeDetector] so the
 * feature stays usable.
 */
@Singleton
class OnnxEdgeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fallback: OpenCvEdgeDetector,
) : EdgeDetector {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // Lazy-loaded so model IO happens off the composable-tree-creation path.
    // Null means init failed — we route through the classical fallback.
    private val session: OrtSession? by lazy {
        try {
            val bytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            env.createSession(bytes, opts).also {
                Log.i(TAG, "loaded fastvit_t8 session (${bytes.size / 1024 / 1024} MB)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ONNX session init failed — will fall back to OpenCvEdgeDetector", e)
            null
        }
    }

    override suspend fun detectQuadrilateral(bitmap: Bitmap): List<PointF>? =
        withContext(Dispatchers.Default) {
            val sess = session ?: return@withContext fallback.detectQuadrilateral(bitmap)
            try {
                runInference(bitmap, sess) ?: fallback.detectQuadrilateral(bitmap)
            } catch (e: Throwable) {
                Log.w(TAG, "inference failed — falling back to OpenCV", e)
                fallback.detectQuadrilateral(bitmap)
            }
        }

    private fun runInference(bitmap: Bitmap, sess: OrtSession): List<PointF>? {
        val origW = bitmap.width
        val origH = bitmap.height
        val start = System.nanoTime()
        Log.d(TAG, "detect input ${origW}×${origH}")

        val tensor = preprocess(bitmap)
        try {
            val outputs = sess.run(mapOf(INPUT_NAME to tensor))
            try {
                val heatmapOut = outputs.firstOrNull { it.key == OUTPUT_NAME }?.value
                    ?: outputs.firstOrNull()?.value
                    ?: return null
                val onnxTensor = heatmapOut as? OnnxTensor ?: return null

                // Shape: (1, 4, H, W). Take a flat FloatBuffer for fast access.
                val shape = onnxTensor.info.shape
                if (shape.size != 4 || shape[1] != 4L) {
                    Log.w(TAG, "unexpected heatmap shape ${shape.contentToString()}")
                    return null
                }
                val hmH = shape[2].toInt()
                val hmW = shape[3].toInt()
                val heatmapSize = hmH * hmW
                val buffer = onnxTensor.floatBuffer

                val corners = (0 until 4).mapNotNull { cornerIdx ->
                    cornerCentroid(buffer, cornerIdx, heatmapSize, hmH, hmW, origW, origH)
                }
                if (corners.size != 4) return null

                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                Log.d(TAG, "detect OK in ${elapsedMs}ms (heatmap $hmW×$hmH → $origW×$origH)")
                return orderTlTrBrBl(corners)
            } finally {
                outputs.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        // Use OpenCV INTER_AREA for the downscale instead of Bitmap's bilinear.
        //
        // At post-capture the input is ~1536×2048; stretching that to 256×256
        // with bilinear (Bitmap.createScaledBitmap filter=true) aliases badly
        // at 6–8× reduction — the network sees noisy features and the
        // heatmaps it emits are much weaker than for the ~640×480 live-
        // preview frames. INTER_AREA averages the contributing source pixels
        // per target pixel, which is the canonical anti-aliased downscale and
        // makes the model's input representation consistent across both paths.
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        val resized = Mat()
        Imgproc.resize(
            srcMat, resized,
            Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA,
        )
        srcMat.release()
        val scaled = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resized, scaled)
        resized.release()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        scaled.recycle()

        // DocAligner's reference inference runs cv2.cvtColor(RGB2BGR) before
        // feeding the model, so channel order is B, G, R in NCHW layout.
        val plane = INPUT_SIZE * INPUT_SIZE
        val buf = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xff) / 255f
            val g = ((px shr 8) and 0xff) / 255f
            val b = (px and 0xff) / 255f
            buf.put(0 * plane + i, b)
            buf.put(1 * plane + i, g)
            buf.put(2 * plane + i, r)
        }
        buf.rewind()
        return OnnxTensor.createTensor(
            env, buf,
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )
    }

    /**
     * Extract the corner position for one of the 4 heatmap channels.
     *
     * Two-stage localisation:
     *   1. **Primary** — threshold + largest-contour centroid. Matches the
     *      upstream Python inference and is most precise when the model emits
     *      a confident blob.
     *   2. **Fallback** — `minMaxLoc` argmax of the raw heatmap. Used when
     *      the threshold step finds no blob (smaller backbones like lcnet100
     *      / fastvit_t8 often produce confidence peaks below 0.3 even on
     *      clear documents). Guarded by [MIN_PEAK_CONFIDENCE] so we don't
     *      hallucinate corners on images with no document at all.
     *
     * Returning a PointF here means "we're giving it a shot"; the outer
     * plausibility filter (corner ordering + downstream VM) decides if the
     * four points together form a usable quadrilateral.
     */
    private fun cornerCentroid(
        buf: FloatBuffer,
        cornerIdx: Int,
        heatmapSize: Int,
        hmH: Int,
        hmW: Int,
        origW: Int,
        origH: Int,
    ): PointF? {
        val floats = FloatArray(heatmapSize)
        val offset = cornerIdx * heatmapSize
        for (i in 0 until heatmapSize) floats[i] = buf.get(offset + i)

        val hmMat = Mat(hmH, hmW, CvType.CV_32F)
        hmMat.put(0, 0, floats)
        val upscaled = Mat()
        Imgproc.resize(hmMat, upscaled, Size(origW.toDouble(), origH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        hmMat.release()

        try {
            // Primary: threshold + centroid of largest blob.
            val centroid = thresholdAndCentroid(upscaled)
            if (centroid != null) return centroid

            // Fallback: argmax of the raw (upscaled) heatmap. Accept only if
            // the peak itself clears a soft confidence floor — otherwise this
            // corner isn't confidently anywhere and we should fall back to
            // classical CV.
            val minMax = Core.minMaxLoc(upscaled)
            return if (minMax.maxVal >= MIN_PEAK_CONFIDENCE) {
                PointF(minMax.maxLoc.x.toFloat(), minMax.maxLoc.y.toFloat())
            } else null
        } finally {
            upscaled.release()
        }
    }

    private fun thresholdAndCentroid(heatmap32f: Mat): PointF? {
        val scaled = Mat()
        Core.multiply(heatmap32f, Scalar(255.0), scaled)
        Imgproc.threshold(scaled, scaled, HEATMAP_THRESHOLD * 255.0, 255.0, Imgproc.THRESH_TOZERO)
        val bin = Mat()
        scaled.convertTo(bin, CvType.CV_8U)
        scaled.release()
        Imgproc.threshold(bin, bin, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        bin.release()

        return try {
            val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
            val moments = Imgproc.moments(largest)
            if (moments.m00 == 0.0) return null
            PointF(
                (moments.m10 / moments.m00).toFloat(),
                (moments.m01 / moments.m00).toFloat(),
            )
        } finally {
            contours.forEach { it.release() }
        }
    }

    private fun orderTlTrBrBl(pts: List<PointF>): List<PointF> {
        val sums = pts.sortedBy { it.x + it.y }
        val diffs = pts.sortedBy { it.x - it.y }
        return listOf(sums.first(), diffs.last(), sums.last(), diffs.first())
    }

    companion object {
        private const val TAG = "OnnxEdgeDetector"
        private const val MODEL_PATH = "models/fastvit_t8_h_e_bifpn_256_fp32.onnx"
        private const val INPUT_SIZE = 256
        private const val INPUT_NAME = "img"
        private const val OUTPUT_NAME = "heatmap"

        // Threshold for the primary "blob-centroid" localisation. Matches
        // DocAligner's Python reference. Peaks below this survive via the
        // argmax fallback, not the centroid path.
        private const val HEATMAP_THRESHOLD = 0.3f

        // Soft confidence floor for the argmax fallback — the whole corner
        // heatmap must still peak above this value or we report no corner.
        // 0.1 is deliberately lenient; the downstream plausibility filter
        // will reject geometrically implausible quadrilaterals.
        private const val MIN_PEAK_CONFIDENCE = 0.1
    }
}
