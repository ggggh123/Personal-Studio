package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.domain.model.ScanFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvEnhancePipelineTest {

    private val pipeline = OpenCvEnhancePipeline()

    private fun rectBitmap(w: Int = 400, h: Int = 600, rectColor: Int = Color.BLACK): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
        canvas.drawRect(40f, 60f, (w - 40).toFloat(), (h - 60).toFloat(), Paint().apply { color = rectColor })
        return bmp
    }

    @Test fun `warpAndFilter COLOR returns bitmap with expected aspect`() = runTest {
        val src = rectBitmap()
        val corners = listOf(PointF(40f, 60f), PointF(360f, 60f), PointF(360f, 540f), PointF(40f, 540f))
        val out = pipeline.warpAndFilter(src, corners, ScanFilter.COLOR)
        assertEquals(320, out.width)   // src width - 80
        assertEquals(480, out.height)  // src height - 120
    }

    @Test fun `applyFilter BW produces bimodal intensity`() = runTest {
        val src = rectBitmap()
        val out = pipeline.applyFilter(src, ScanFilter.BW)
        val histogram = IntArray(256)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        for (p in pixels) histogram[(p and 0xff)]++
        val extremes = histogram.slice(0..10).sum() + histogram.slice(245..255).sum()
        val total = pixels.size
        assertTrue("Expected >95% pixels at extremes, got ${extremes.toFloat() / total}", extremes > total * 0.95)
    }

    @Test fun `applyFilter GRAYSCALE produces equal RGB channels`() = runTest {
        val src = rectBitmap(rectColor = Color.RED)
        val out = pipeline.applyFilter(src, ScanFilter.GRAYSCALE)
        val middle = out.getPixel(out.width / 2, out.height / 2)
        val r = (middle shr 16) and 0xff
        val g = (middle shr 8) and 0xff
        val b = middle and 0xff
        assertEquals("R==G", g, r)
        assertEquals("G==B", b, g)
    }
}
