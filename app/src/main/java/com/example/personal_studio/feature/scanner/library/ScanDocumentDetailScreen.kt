package com.example.personal_studio.feature.scanner.library

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.feature.scanner.scanFilterLabel
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail view of a scan document. Shows a 2-col grid of page thumbnails
 * with drag-reorder and an export action.
 *
 * Rename and delete-doc are intentionally NOT surfaced here — the library
 * row's long-press already owns those ops (via DocActionsDialog). Keeping
 * them out avoids duplicate entry points and leaves room for additional
 * page-level actions later.
 */
@Composable
fun ScanDocumentDetailScreen(
    docId: Long,
    onBack: () -> Unit,
    onOpenPage: (pageId: Long) -> Unit = {},
) {
    val vm: ScanDocumentDetailViewModel = hiltViewModel(
        creationCallback = { f: ScanDocumentDetailViewModel.Factory -> f.create(docId) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val shareUri by vm.pendingShareUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // When the VM emits a share URI, fire the system ACTION_SEND chooser
    // and immediately clear the URI so the effect doesn't re-fire on
    // recomposition / config change.
    LaunchedEffect(shareUri) {
        shareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享 PDF"))
            vm.clearShareIntent()
        }
    }

    val title = state.doc?.title ?: "…"
    val createdAt = state.doc?.createdAt
    val pageCount = state.pages.size

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(
            route = "scans/$title",
            subtitle = "# 会话: $title\n# $pageCount 页" +
                (createdAt?.let { " · ${formatTs(it)}" } ?: ""),
            trailing = {
                Text(
                    "[< 返回]",
                    color = FoamMute,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp).clickable(onClick = onBack),
                )
            },
        )

        ReorderablePageGrid(
            pages = state.pages,
            onTapPage = onOpenPage,
            onReorder = { orderedIds -> vm.reorderPages(orderedIds) },
            modifier = Modifier.weight(1f),
        )

        ReadActionBar(
            canExport = pageCount > 0 && !state.isExporting,
            isExporting = state.isExporting,
            onExport = { vm.exportPdf() },
        )
    }
}

/**
 * 2-col grid where each cell supports long-press drag reorder.
 *
 * [orderedPages] is the drag-time truth — we mutate it optimistically in
 * onMove so siblings animate instantly, and commit to the repository
 * once on drag release. We re-sync from [pages] only when the id-set
 * changes (page added/removed) or when the underlying page objects
 * change (filter/path edit) while our draft order still matches repo —
 * that preserves an in-progress drag against transient re-emits.
 */
@Composable
private fun ReorderablePageGrid(
    pages: List<ScanPage>,
    onTapPage: (pageId: Long) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var orderedPages by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) {
        val repoIds = pages.map { it.id }
        val localIds = orderedPages.map { it.id }
        when {
            repoIds.toSet() != localIds.toSet() -> orderedPages = pages  // add/remove
            repoIds == localIds -> orderedPages = pages                  // object refresh
            // else: repo order differs from our optimistic order (mid-drag);
            // keep the optimistic one until commit round-trip finishes.
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        orderedPages = orderedPages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    val haptics = LocalHapticFeedback.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(orderedPages, key = { it.id }) { page ->
            ReorderableItem(reorderableState, key = page.id) { isDragging ->
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "page-cell-scale",
                )
                val ordinal = orderedPages.indexOfFirst { it.id == page.id }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReorder(orderedPages.map { it.id })
                            },
                        )
                        .clickable { if (!isDragging) onTapPage(page.id) },
                ) {
                    ScanThumbnail(
                        path = page.enhancedImagePath,
                        width = 140.dp,
                        height = 180.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "p.${ordinal + 1} · ${scanFilterLabel(page.filter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDragging) Phosphor else FoamMute,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadActionBar(
    canExport: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Rule),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (isExporting) "[... 导出 PDF 中]" else "[📄 导出 PDF]"
        val color = when {
            isExporting -> Amber
            canExport -> Phosphor
            else -> FoamDim
        }
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(enabled = canExport, onClick = onExport),
        )
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
