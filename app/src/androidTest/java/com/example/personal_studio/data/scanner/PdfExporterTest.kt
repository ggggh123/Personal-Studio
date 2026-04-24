package com.example.personal_studio.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val exporter = PdfExporter(ctx)

    private fun writeBitmap(name: String, w: Int, h: Int, color: Int): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(color)
        }
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test fun `export produces 2-page pdf with correct page sizes`() = runTest {
        val p1 = writeBitmap("p1.jpg", 400, 600, Color.WHITE)
        val p2 = writeBitmap("p2.jpg", 500, 500, Color.GRAY)
        val pdf = exporter.export(42L, "test", listOf(p1.absolutePath, p2.absolutePath))
        assert(pdf.exists() && pdf.length() > 0)

        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            assertEquals(2, renderer.pageCount)
            renderer.openPage(0).use { assertEquals(400, it.width); assertEquals(600, it.height) }
            renderer.openPage(1).use { assertEquals(500, it.width); assertEquals(500, it.height) }
        } finally {
            renderer.close(); pfd.close()
        }
    }
}
