package com.example.personal_studio.data.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Writes a PDF with one page per entry in [imagePaths] (in order). Page size
     *  = bitmap pixel size (no A4 forcing; spec §3.4). Returns the output file. */
    suspend fun export(docId: Long, safeTitle: String, imagePaths: List<String>): File =
        withContext(Dispatchers.IO) {
            require(imagePaths.isNotEmpty()) { "need at least one page to export" }
            val outDir = File(context.cacheDir, "export").apply { mkdirs() }
            val outFile = File(outDir, "$docId-$safeTitle.pdf")

            val pdf = PdfDocument()
            try {
                imagePaths.forEachIndexed { idx, path ->
                    val bmp = BitmapFactory.decodeFile(path)
                        ?: error("cannot decode $path for pdf page ${idx + 1}")
                    val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                    val page = pdf.startPage(info)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdf.finishPage(page)
                    bmp.recycle()
                }
                outFile.outputStream().use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
            outFile
        }

    companion object {
        fun safeTitleOf(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fa5_-]"), "_").take(40).ifBlank { "untitled" }
    }
}
