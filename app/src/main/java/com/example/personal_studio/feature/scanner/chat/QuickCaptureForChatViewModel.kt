package com.example.personal_studio.feature.scanner.chat

import android.content.Context
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.scanner.AddPageToDocumentUseCase
import com.example.personal_studio.domain.scanner.CaptureAndEnhancePageUseCase
import com.example.personal_studio.domain.scanner.CreateScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Backs [QuickCaptureForChatScreen]. On finalize:
 *
 * - If `saveToLib`: create a scan doc first, run warp+filter into the
 *   doc's canonical dir (`filesDir/scans/<docId>/`) so paths match what
 *   ScanRepository.deleteDocument later wipes, then append + finalize.
 * - Otherwise: run against a throwaway scratch dir under
 *   `filesDir/scans/tmp-chat/<uuid>/` and delete it after copying the
 *   enhanced JPEG out.
 *
 * Either way, copy the enhanced JPEG into
 * `filesDir/chat-attachments/img_<ts>.jpg` — same home the gallery
 * picker uses, so the downstream ImageCropOverlay + attach path is
 * identical. Publish that path on [pickedPath] so the screen's
 * LaunchedEffect fires the caller's onImagePicked.
 */
@HiltViewModel
class QuickCaptureForChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ScanRepository,
    private val captureAndEnhance: CaptureAndEnhancePageUseCase,
    private val createDoc: CreateScanDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
) : ViewModel() {

    private val _pickedPath = MutableStateFlow<String?>(null)
    val pickedPath = _pickedPath.asStateFlow()

    fun finalize(
        tmpFile: File,
        corners: List<PointF>,
        filter: ScanFilter,
        saveToLib: Boolean,
    ) = viewModelScope.launch {
        val (dir, isScratch) = if (saveToLib) {
            val docId = createDoc(defaultTitle())
            repo.documentDir(docId) to false
        } else {
            File(
                context.filesDir,
                "scans/tmp-chat/${UUID.randomUUID().toString().take(8)}",
            ) to true
        }
        val result = captureAndEnhance(dir, tmpFile, corners, filter)

        if (saveToLib) {
            // dir here is repo.documentDir(docId) — back-derive the docId
            // from the dir name so we don't have to thread it through the
            // branch above.
            val docId = dir.name.toLongOrNull() ?: return@launch
            addPage(
                docId,
                result.originalImagePath,
                result.enhancedImagePath,
                result.filter,
                result.cornersJson,
            )
        }

        val chatDir = File(context.filesDir, "chat-attachments").apply { mkdirs() }
        val chatFile = File(chatDir, "img_${System.currentTimeMillis()}.jpg")
        File(result.enhancedImagePath).copyTo(chatFile, overwrite = true)

        if (isScratch) dir.deleteRecursively()

        _pickedPath.value = chatFile.absolutePath
    }

    fun clearPicked() { _pickedPath.value = null }

    private fun defaultTitle(): String =
        "scan_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())}"
}
