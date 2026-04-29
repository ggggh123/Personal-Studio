package com.example.personal_studio.feature.scanner.doc

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.rememberZoomableBoxState
import com.example.personal_studio.ui.components.zoomTransform
import com.example.personal_studio.ui.components.zoomable
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
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
    var showDelete by remember { mutableStateOf(false) }

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
            onRetake = { step = EditStep.Capturing(System.identityHashCode(step)) },
            onDelete = { showDelete = true },
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

    if (showDelete) {
        DeletePageDialog(
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                vm.deletePage()
                onBack()
            },
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
    onDelete: () -> Unit,
    onArchive: () -> Unit,
) {
    val zoom = rememberZoomableBoxState()
    // Filter switches reset zoom so a lingering pan/scale from the previous
    // filter doesn't feel glued to the wrong image.
    remember(state.currentFilter) {
        zoom.reset()
        state.currentFilter
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        // Secondary actions (retake / archive / delete) live in the topbar's
        // trailing slot so the area below the image stays focused on the
        // filter chips + primary [cancel]/[confirm] pair.
        TerminalTopBar(
            route = "scans/edit",
            subtitle = state.page?.let { "# page #${it.id}" },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "[↻ retake]",
                        color = Amber,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onRetake),
                    )
                    Text(
                        "[+ archive]",
                        color = Phosphor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onArchive),
                    )
                    Text(
                        "[x delete page]",
                        color = Carmine,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onDelete),
                    )
                }
            },
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
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomTransform(zoom),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanFilter.entries.forEach { f ->
                val active = state.currentFilter == f
                Text(
                    "[${f.name.lowercase()}]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) Phosphor else FoamDim,
                    modifier = Modifier.clickable { onSelectFilter(f) },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "[cancel]",
                color = FoamDim,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(
                "[confirm ↵]",
                color = if (state.isLoading) FoamDim else Phosphor,
                modifier = Modifier.clickable(enabled = !state.isLoading, onClick = onConfirm),
            )
        }
    }
}

@Composable
private fun DeletePageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        containerColor = Void,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "delete this page?",
                color = Amber,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        text = {
            Text(
                "the image files will be removed. this cannot be undone.",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("[ delete ]", color = Carmine, style = MaterialTheme.typography.bodyMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("[ cancel ]", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
