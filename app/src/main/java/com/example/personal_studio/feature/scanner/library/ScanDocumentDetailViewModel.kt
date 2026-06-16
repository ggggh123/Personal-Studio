package com.example.personal_studio.feature.scanner.library

import android.content.Context
import android.graphics.PointF
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.scanner.AddPageToDocumentUseCase
import com.example.personal_studio.domain.scanner.CaptureAndEnhancePageUseCase
import com.example.personal_studio.domain.scanner.CreateScanDocumentUseCase
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.ExportDocumentToPdfUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.ReorderPagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanDetailUiState(
    val doc: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val isExporting: Boolean = false,
)

/** 仅在"新建文档且一页未拍"时退出才丢弃空壳;已有文档永不自动删。 */
internal fun shouldDiscardOnExit(isNew: Boolean, pageCount: Int): Boolean = isNew && pageCount == 0

/**
 * 统一文档编辑器 VM。navDocId>0 = 打开已有;navDocId<=0 = 新建(VM 现建一篇空文档)。
 * 能力:加页(拍照→增强→append)、重排、单页删除、导出 PDF、退出空文档清理。
 */
@HiltViewModel(assistedFactory = ScanDocumentDetailViewModel.Factory::class)
class ScanDocumentDetailViewModel @AssistedInject constructor(
    @Assisted private val navDocId: Long,
    @ApplicationContext private val context: Context,
    private val repo: ScanRepository,
    private val createDoc: CreateScanDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
    private val captureAndEnhance: CaptureAndEnhancePageUseCase,
    private val deleteDoc: DeleteScanDocumentUseCase,
    private val reorderUc: ReorderPagesUseCase,
    private val removePageUc: RemovePageUseCase,
    private val exportUc: ExportDocumentToPdfUseCase,
) : ViewModel() {

    val isNew: Boolean = navDocId <= 0L
    private var realDocId: Long = navDocId

    private val _state = MutableStateFlow(ScanDetailUiState())
    val state = _state.asStateFlow()

    private val _pendingShareUri = MutableStateFlow<Uri?>(null)
    val pendingShareUri = _pendingShareUri.asStateFlow()

    init {
        viewModelScope.launch {
            realDocId = if (navDocId > 0) navDocId else createDoc(defaultTitle())
            launch {
                repo.observeDocument(realDocId).collect { doc ->
                    _state.value = _state.value.copy(doc = doc)
                }
            }
            launch {
                repo.observePages(realDocId).collect { pages ->
                    _state.value = _state.value.copy(pages = pages)
                }
            }
        }
    }

    /** 拍照→warp+滤镜→append 一页(复用构建器同款流程)。 */
    fun confirmPage(tmpCapture: File, corners: List<PointF>, filter: ScanFilter) = viewModelScope.launch {
        val result = captureAndEnhance(repo.documentDir(realDocId), tmpCapture, corners, filter)
        addPage(realDocId, result.originalImagePath, result.enhancedImagePath, result.filter, result.cornersJson)
    }

    fun reorderPages(orderedIds: List<Long>) = viewModelScope.launch {
        if (orderedIds.isNotEmpty()) reorderUc(realDocId, orderedIds)
    }

    fun deletePage(pageId: Long) = viewModelScope.launch { removePageUc(pageId) }

    fun exportPdf() = viewModelScope.launch {
        if (_state.value.isExporting) return@launch
        _state.value = _state.value.copy(isExporting = true)
        try {
            val file = exportUc(realDocId)
            _pendingShareUri.value = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
        } finally {
            _state.value = _state.value.copy(isExporting = false)
        }
    }

    /** 退出时:新建且 0 页 → 丢弃空壳;否则不动。 */
    fun onExit() = viewModelScope.launch {
        if (shouldDiscardOnExit(isNew, _state.value.pages.size)) deleteDoc(realDocId)
    }

    fun clearShareIntent() { _pendingShareUri.value = null }

    private fun defaultTitle(): String =
        "scan_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())}"

    @AssistedFactory
    interface Factory {
        fun create(docId: Long): ScanDocumentDetailViewModel
    }
}
