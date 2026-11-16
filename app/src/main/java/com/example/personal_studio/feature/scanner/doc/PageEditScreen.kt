package com.example.personal_studio.feature.scanner.doc

import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.feature.knowledge.ui.SavePreviewModal
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModel
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import com.example.personal_studio.feature.scanner.scanFilterLabel
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.rememberZoomableBoxState
import com.example.personal_studio.ui.components.zoomTransform
import com.example.personal_studio.ui.components.zoomable
import java.io.File

/**
 * State machine for PageEdit's retake sub-flow. Review is the default
 * state showing the existing page with filter controls; tapping retake
 * transitions into Camera → Edge → Enhance, then back out (with the new
 * capture applied via RecapturePageUseCase).
 */
private sealed interface EditStep {
    object Review : EditStep
    data class Capturing(val nonce: Int) : EditStep
    data class EdgeDetect(
        val tmp: String,
        val autoDetect: Boolean,
        val liveNorm: List<PointF>?,
    ) : EditStep
    data class Enhance(val tmp: String, val corners: List<PointF>) : EditStep
}

/**
 * Edit an existing scan page: change its filter, retake, or delete it.
 * Opened from ScanDocumentDetailScreen via a page thumbnail tap.
 */
@Composable
fun PageEditScreen(
    pageId: Long,
    onBack: () -> Unit,
    onNavigateToKbEntry: (Long) -> Unit,
) {
    val vm: PageEditViewModel = hiltViewModel(
        creationCallback = { f: PageEditViewModel.Factory -> f.create(pageId) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tmpDir = remember { File(context.filesDir, "scans/tmp").apply { mkdirs() } }
    var step by remember { mutableStateOf<EditStep>(EditStep.Review) }

    // KB archive flow — same pattern as ChatDetailScreen (Task 21).
    val saveVm: SaveToKnowledgeViewModel = hiltViewModel()
    val saveState by saveVm.uiState.collectAsStateWithLifecycle()
    // Holds the source the modal is currently working with so retry from the
    // Error state knows what to re-run. Same invariant as ChatDetailScreen:
    // Error is only entered from Loading/Saving for this exact source.
    val activeSourceState = remember { mutableStateOf<KbDraftSource?>(null) }

    when (val s = step) {
        EditStep.Review -> ReviewView(
            state = state,
            onSelectFilter = vm::selectFilter,
            onConfirm = {
                vm.confirmFilterChange()
                onBack()
            },
            onCancel = onBack,
            onDelete = {
                vm.deletePage()
                onBack()
            },
            onRetake = { step = EditStep.Capturing(System.identityHashCode(step)) },
            onArchive = {
                // Only archive once the page has loaded; docId + pageId both
                // come off the loaded ScanPage so SourceContextLoader has a
                // direct mapping (no -1L inference fallback needed here).
                val page = state.page ?: return@ReviewView
                val src = KbDraftSource.FromScanPage(docId = page.docId, pageId = page.id)
                activeSourceState.value = src
                saveVm.startDraft(src)
            },
        )
        is EditStep.Capturing -> CameraCaptureScreen(
            outputDir = tmpDir,
            onCaptured = { file, auto, liveNorm ->
                step = EditStep.EdgeDetect(file.absolutePath, auto, liveNorm)
            },
            onCancel = { step = EditStep.Review },
        )
        is EditStep.EdgeDetect -> EdgeDetectAndCropScreen(
            capturedFilePath = s.tmp,
            autoDetect = s.autoDetect,
            preDetectedNormalized = s.liveNorm,
            onConfirm = { corners -> step = EditStep.Enhance(s.tmp, corners) },
            onRetake = { step = EditStep.Capturing(System.identityHashCode(step)) },
        )
        is EditStep.Enhance -> EnhanceReviewScreen(
            capturedFilePath = s.tmp,
            cornersBitmapPx = s.corners,
            onConfirm = { filter, _ ->
                vm.recapture(File(s.tmp), s.corners, filter)
                onBack()
            },
            onCancel = { step = EditStep.Capturing(System.identityHashCode(step)) },
        )
    }

    SavePreviewModal(
        state = saveState,
        onCancel = { saveVm.reset() },
        onConfirm = { draft -> saveVm.commit(draft) },
        onRetry = { activeSourceState.value?.let { saveVm.retry(it) } },
    )
    LaunchedEffect(saveState) {
        val s = saveState
        if (s is SaveToKnowledgeUiState.Saved) {
            onNavigateToKbEntry(s.entryId)
            saveVm.reset()
        }
    }
}

@Composable
private fun ReviewView(
    state: PageEditUiState,
    onSelectFilter: (ScanFilter) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetake: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) = com.example.personal_studio.ui.theme.DarkCanvas {
    val c = com.example.personal_studio.ui.theme.Tg.colors
    val zoom = rememberZoomableBoxState()
    var confirmDelete by remember { mutableStateOf(false) }
    // Filter switches reset zoom so a lingering pan/scale doesn't stick to the new image.
    remember(state.currentFilter) {
        zoom.reset()
        state.currentFilter
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TgTopBar(
            route = "scans/edit",
            subtitle = state.page?.let { "第 ${it.ordinal + 1} 页\n${scanFilterLabel(state.currentFilter)}" },
            onBack = onCancel,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .zoomable(zoom),
            contentAlignment = Alignment.Center,
        ) {
            state.displayedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "第 ${(state.page?.ordinal ?: 0) + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomTransform(zoom),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            com.example.personal_studio.ui.components.PhotoChip("重拍", selected = false, onClick = onRetake,
                icon = androidx.compose.material.icons.Icons.Outlined.PhotoCamera, role = androidx.compose.ui.semantics.Role.Button)
            com.example.personal_studio.ui.components.PhotoChip("存入知识库", selected = false, onClick = onArchive,
                icon = androidx.compose.material.icons.Icons.Outlined.BookmarkAdd, role = androidx.compose.ui.semantics.Role.Button)
            com.example.personal_studio.ui.components.PhotoChip("删除此页", selected = false, onClick = { confirmDelete = true },
                icon = androidx.compose.material.icons.Icons.Outlined.DeleteOutline, role = androidx.compose.ui.semantics.Role.Button)
        }
        com.example.personal_studio.ui.components.PhotoActionBar(
            secondary = "取消",
            onSecondary = onCancel,
            primary = if (state.isLoading) "处理中…" else "保存",
            onPrimary = onConfirm,
            primaryEnabled = !state.isLoading,
            above = {
                ScanFilter.entries.forEach { f ->
                    com.example.personal_studio.ui.components.PhotoChip(scanFilterLabel(f), selected = state.currentFilter == f, onClick = { onSelectFilter(f) })
                }
            },
        )
    }
    if (confirmDelete) {
        com.example.personal_studio.ui.components.TgConfirmDialog(
            title = "删除这一页？",
            message = "这页的原图和处理后的图片都会被删除，其余页面会顺移。",
            confirmLabel = "删除",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
