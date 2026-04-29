package com.example.personal_studio.data.kb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `filesDir/kb/` directory: staging temp copies during draft preview,
 * promoting them to `<entryId>.jpg` on commit, and cleaning up on entry delete.
 */
@Singleton
class KbImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File get() = File(context.filesDir, "kb").apply { mkdirs() }

    /** Copies [sourcePath] to a temp file in kb/. Returns the temp file's absolute path. */
    fun stageCopy(sourcePath: String): String {
        val src = File(sourcePath)
        require(src.exists()) { "source image not found: $sourcePath" }
        val dst = File(baseDir, "tmp_${UUID.randomUUID()}.jpg")
        src.copyTo(dst, overwrite = true)
        return dst.absolutePath
    }

    /** Renames a staged temp file to `<entryId>.jpg`. Returns the final absolute path. */
    fun promote(stagedPath: String, entryId: Long): String {
        val staged = File(stagedPath)
        require(staged.exists()) { "staged image not found: $stagedPath" }
        val final = File(baseDir, "$entryId.jpg")
        if (final.exists()) final.delete()
        check(staged.renameTo(final)) { "failed to promote $stagedPath -> ${final.absolutePath}" }
        return final.absolutePath
    }

    /** Best-effort delete; safe to call even if file is missing. */
    fun deleteForEntry(entryId: Long) {
        File(baseDir, "$entryId.jpg").delete()
    }

    /** Best-effort cleanup of a staged temp (cancel path). */
    fun deleteStaged(stagedPath: String?) {
        if (stagedPath.isNullOrBlank()) return
        File(stagedPath).delete()
    }
}
