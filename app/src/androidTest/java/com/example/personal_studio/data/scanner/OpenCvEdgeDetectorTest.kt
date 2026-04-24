package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvEdgeDetectorTest {

    private val detector = OpenCvEdgeDetector()

    @Test fun `detects black rectangle on white background`() = runTest {
        val w = 800; val h = 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.WHITE)
            c.drawRect(80f, 100f, (w - 80).toFloat(), (h - 100).toFloat(), Paint().apply { color = Color.BLACK })
        }
        val corners = detector.detectQuadrilateral(bmp)
        assertNotNull(corners)
        assertEquals(4, corners!!.size)
        val (tl, tr, br, bl) = corners
        assertTrue("TL x: ${tl.x}", kotlin.math.abs(tl.x - 80) < 30)
        assertTrue("TL y: ${tl.y}", kotlin.math.abs(tl.y - 100) < 30)
        assertTrue("BR x: ${br.x}", kotlin.math.abs(br.x - (w - 80)) < 30)
        assertTrue("BR y: ${br.y}", kotlin.math.abs(br.y - (h - 100)) < 30)
    }

    @Test fun `returns null on uniform field`() = runTest {
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.WHITE)
        }
        val corners = detector.detectQuadrilateral(bmp)
        assertNull(corners)
    }

    @Test fun `returns null when shape area is under 30 percent`() = runTest {
        val w = 800; val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.WHITE)
                drawRect(350f, 350f, 450f, 450f, Paint().apply { color = Color.BLACK })
            }
        }
        val corners = detector.detectQuadrilateral(bmp)
        assertNull(corners)
    }
}
