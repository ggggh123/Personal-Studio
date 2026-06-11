package com.example.personal_studio.feature.scanner.doc

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.io.File

/**
 * State machine for the in-document capture flow. Each step renders one
 * self-contained composable (Camera / Edge / Enhance) or the strip view.
 * Transitions happen via the composables' existing onConfirm/onCancel
 * callbacks — no nested NavHost, no route plumbing.
 */
private sealed interface BuilderStep {
    object Strip : BuilderStep
    data class Capturing(val nonce: Int) : BuilderStep
    data class EdgeDetect(
        val tmp: String,
        val autoDetect: Boolean,
        val liveNorm: List<PointF>?,
    ) : BuilderStep
    data class Enhance(val tmp: String, val corners: List<PointF>) : BuilderStep
}

/**
 * Host screen for building a multi-page scan document.
 *
 * Coordinates Camera → Edge → Enhance as an internal state machine, shows
 * a thumbnail strip of saved pages, and exposes `[+ add next]` / `[↵ finish]`
 * / `[x cancel]` actions. On finish or cancel, [onExit] fires so the outer
 * nav host can pop back to the library.
 *
 * [resumeDocId] lets us re-enter an existing pending doc (used by Task 19's
 * pending-doc recovery on app restart); null means start a fresh one.
 */
@Composable
fun DocumentBuilderScreen(
    resumeDocId: Long? = null,
    onExit: (docId: Long?) -> Unit,
) {
    val vm: DocumentBuilderViewModel = hiltViewModel(
        creationCallback = { f: DocumentBuilderViewModel.Factory -> f.create(resumeDocId) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tmpDir = remember { File(context.filesDir, "scans/tmp").apply { mkdirs() } }
    var step by remember { mutableStateOf<BuilderStep>(BuilderStep.Strip) }

    // Terminal-state observer — finalize/cancel pops the user out.
    LaunchedEffect(state.mode) {
        when (state.mode) {
            Mode.Saved -> onExit(state.docId)
            Mode.Cancelled -> onExit(null)
            else -> Unit
        }
    }

    when (val s = step) {
        BuilderStep.Strip -> StripView(
            pageCount = state.pages.size,
            pages = state.pages,
            onAddNext = { step = BuilderStep.Capturing(System.identityHashCode(step)) },
            onFinish = { vm.finish() },
            onCancel = { vm.cancel() },
        )
        is BuilderStep.Capturing -> CameraCaptureScreen(
            outputDir = tmpDir,
            onCaptured = { file, auto, liveNorm ->
                step = BuilderStep.EdgeDetect(file.absolutePath, auto, liveNorm)
            },
            onCancel = { step = BuilderStep.Strip },
        )
        is BuilderStep.EdgeDetect -> EdgeDetectAndCropScreen(
            capturedFilePath = s.tmp,
            autoDetect = s.autoDetect,
            preDetectedNormalized = s.liveNorm,
            onConfirm = { corners -> step = BuilderStep.Enhance(s.tmp, corners) },
            onRetake = { step = BuilderStep.Capturing(System.identityHashCode(step)) },
        )
        is BuilderStep.Enhance -> EnhanceReviewScreen(
            capturedFilePath = s.tmp,
            cornersBitmapPx = s.corners,
            onConfirm = { filter, _ ->
                vm.confirmPage(File(s.tmp), s.corners, filter)
                step = BuilderStep.Strip
            },
            onCancel = { step = BuilderStep.Capturing(System.identityHashCode(step)) },
        )
    }
}

@Composable
private fun StripView(
    pageCount: Int,
    pages: List<SavedPageSnapshot>,
    onAddNext: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Void)) {
        TerminalTopBar(
            route = "scans/new",
            trailing = {
                Text(
                    "[x 取消]",
                    color = Carmine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onCancel),
                )
            },
        )

        // Status line under top bar
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                "本文档共 $pageCount 页",
                style = MaterialTheme.typography.bodyMedium,
                color = FoamMute,
            )
        }

        // Thumbnail strip (empty state or LazyRow)
        Box(
            Modifier
                .fillMaxWidth()
                .height(THUMB_HEIGHT_DP.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (pages.isEmpty()) {
                Text(
                    "# 还没有页面 —— 点下方拍摄",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoamDim,
                )
            } else {
                ThumbnailStrip(pages)
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "[+ 添加下一页]",
                style = MaterialTheme.typography.bodyLarge,
                color = Phosphor,
                modifier = Modifier.clickable(onClick = onAddNext),
            )
            Text(
                "[↵ 完成]",
                style = MaterialTheme.typography.bodyLarge,
                color = if (pageCount > 0) Phosphor else FoamDim,
                modifier = Modifier.clickable(enabled = pageCount > 0, onClick = onFinish),
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(pages: List<SavedPageSnapshot>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        items(pages, key = { it.id }) { page ->
            ThumbnailCard(page)
        }
    }
}

@Composable
private fun ThumbnailCard(page: SavedPageSnapshot) {
    // Decode at a small size to keep the strip cheap regardless of the
    // underlying enhanced JPEG's resolution.
    val bitmap = remember(page.enhancedImagePath) {
        runCatching {
            android.graphics.BitmapFactory.decodeFile(
                page.enhancedImagePath,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
            )
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .width(THUMB_WIDTH_DP.dp)
            .border(1.dp, FoamDim)
            .background(Void),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(THUMB_WIDTH_DP.dp, (THUMB_WIDTH_DP * 4 / 3).dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("?", color = FoamDim)
            }
        }
        Text(
            "p${page.ordinal + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = Foam,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

private const val THUMB_HEIGHT_DP = 140
private const val THUMB_WIDTH_DP = 88
