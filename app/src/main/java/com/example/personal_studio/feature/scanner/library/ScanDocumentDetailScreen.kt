package com.example.personal_studio.feature.scanner.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TerminalInputDialog
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import android.content.Intent
import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import com.example.personal_studio.feature.scanner.scanFilterLabel
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 编辑器内拍照子流程状态机(同构建器,无嵌套 NavHost)。 */
private sealed interface EditorStep {
    object Viewing : EditorStep
    data class Capturing(val nonce: Int) : EditorStep
    data class EdgeDetect(val tmp: String, val autoDetect: Boolean, val liveNorm: List<PointF>?) : EditorStep
    data class Enhance(val tmp: String, val corners: List<PointF>) : EditorStep
}

/**
 * 统一文档编辑器:任何文档点进来都能加/删/重排/改页 + 导出。docId<=0 = 新建,
 * 进来直接开相机拍第一页;若没拍就退出,自动丢弃空文档。返回只退出,绝不删已有文档。
 * 删除单页:在网格里长按某页拖到底部回收区松手(带确认)——不必点进单页。
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
    val tmpDir = remember { File(context.filesDir, "scans/tmp").apply { mkdirs() } }
    val isNew = docId <= 0L
    var step by remember { mutableStateOf<EditorStep>(EditorStep.Viewing) }
    // 仅"新建文档"首次进入自动开相机拍第一页。用 rememberSaveable 记住已自动开过——否则
    // 从单页编辑(PageEdit)返回时,editor 的 remember(step) 被销毁重建会再次落到 Capturing
    // 误开相机;导航返回应回到 Viewing(网格)。
    var autoCaptureStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isNew && !autoCaptureStarted) {
            autoCaptureStarted = true
            step = EditorStep.Capturing(0)
        }
    }

    var deletePageTarget by remember { mutableStateOf<Long?>(null) }

    val exit = {
        vm.onExit()
        onBack()
    }

    // 系统返回:Viewing→退出(带空文档清理);拍照子流程中→新建且没拍则退出丢弃,否则回 Viewing。
    BackHandler {
        if (step == EditorStep.Viewing) exit()
        else if (isNew && state.pages.isEmpty()) exit()
        else step = EditorStep.Viewing
    }

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

    when (val s = step) {
        EditorStep.Viewing -> EditorView(
            title = state.doc?.title ?: "…",
            createdAt = state.doc?.createdAt,
            pages = state.pages,
            isExporting = state.isExporting,
            onAddPage = { step = EditorStep.Capturing(state.pages.size) },
            onTapPage = onOpenPage,
            onReorder = { ids -> vm.reorderPages(ids) },
            onDelete = { deletePageTarget = it },
            onExport = { vm.exportPdf() },
            onRename = vm::rename,
            onBack = exit,
        )
        is EditorStep.Capturing -> CameraCaptureScreen(
            outputDir = tmpDir,
            onCaptured = { file, auto, liveNorm ->
                step = EditorStep.EdgeDetect(file.absolutePath, auto, liveNorm)
            },
            onCancel = { if (isNew && state.pages.isEmpty()) exit() else step = EditorStep.Viewing },
        )
        is EditorStep.EdgeDetect -> EdgeDetectAndCropScreen(
            capturedFilePath = s.tmp,
            autoDetect = s.autoDetect,
            preDetectedNormalized = s.liveNorm,
            onConfirm = { corners -> step = EditorStep.Enhance(s.tmp, corners) },
            onRetake = { step = EditorStep.Capturing(s.tmp.hashCode()) },
        )
        is EditorStep.Enhance -> EnhanceReviewScreen(
            capturedFilePath = s.tmp,
            cornersBitmapPx = s.corners,
            onConfirm = { filter, _ ->
                vm.confirmPage(File(s.tmp), s.corners, filter)
                step = EditorStep.Viewing
            },
            onCancel = { step = EditorStep.Capturing(s.tmp.hashCode()) },
        )
    }

    deletePageTarget?.let { pid ->
        TerminalConfirmDialog(
            title = "删除此页",
            message = "图片文件将被移除，此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = { deletePageTarget = null; vm.deletePage(pid) },
            onDismiss = { deletePageTarget = null },
        )
    }
}

@Composable
private fun EditorView(
    title: String,
    createdAt: Long?,
    pages: List<ScanPage>,
    isExporting: Boolean,
    onAddPage: () -> Unit,
    onTapPage: (pageId: Long) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onDelete: (pageId: Long) -> Unit,
    onExport: () -> Unit,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Tg.colors
    var renaming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TerminalTopBar(
            route = "scans/$title",
            subtitle = "$title\n${pages.size} 页" + (createdAt?.let { " · ${formatTs(it)}" } ?: ""),
            onBack = onBack,
            trailing = {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "重命名文档") { renaming = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "重命名", tint = c.text, modifier = Modifier.size(22.dp))
                }
            },
        )
        ReorderablePageGrid(
            pages = pages,
            onTapPage = onTapPage,
            onReorder = onReorder,
            onDelete = onDelete,
            onAddPage = onAddPage,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TgPillButton("添加页面", onClick = onAddPage, style = PillStyle.SECONDARY, icon = Icons.Outlined.PhotoCamera, modifier = Modifier.weight(1f))
            TgPillButton(
                if (isExporting) "正在导出…" else "导出 PDF",
                onClick = onExport,
                icon = Icons.Outlined.IosShare,
                enabled = pages.isNotEmpty() && !isExporting,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (renaming) {
        TerminalInputDialog(
            title = "重命名文档",
            initial = title,
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
}

/**
 * Two columns of page cards. Long-press a card to drag: over another card reorders, over
 * the delete strip that rises at the bottom deletes (after confirming). A dashed card at
 * the end adds the next page.
 *
 * Drop detection: the strip and the pointer are both in window coordinates; the reorder
 * library consumes the drag in the Main pass, this box only watches it in the Initial pass.
 */
@Composable
private fun ReorderablePageGrid(
    pages: List<ScanPage>,
    onTapPage: (pageId: Long) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onDelete: (pageId: Long) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    var orderedPages by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) {
        val repoIds = pages.map { it.id }
        val localIds = orderedPages.map { it.id }
        when {
            repoIds.toSet() != localIds.toSet() -> orderedPages = pages
            repoIds == localIds -> orderedPages = pages
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        // The trailing "add" card is not a page; never move pages past it.
        if (to.index < orderedPages.size && from.index < orderedPages.size) {
            orderedPages = orderedPages.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    }
    val haptics = LocalHapticFeedback.current

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var boxTopWindow by remember { mutableFloatStateOf(0f) }
    var trashTopWindow by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var pointerYWindow by remember { mutableFloatStateOf(0f) }
    val overTrash by remember { derivedStateOf { draggingId != null && pointerYWindow >= trashTopWindow } }

    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { boxTopWindow = it.localToWindow(Offset.Zero).y }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.lastOrNull()?.let { pointerYWindow = boxTopWindow + it.position.y }
                    }
                }
            },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(orderedPages, key = { it.id }) { page ->
                ReorderableItem(reorderableState, key = page.id) { isDragging ->
                    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, TgMotion.fast(), label = "page-cell-scale")
                    val ordinal = orderedPages.indexOfFirst { it.id == page.id }
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                                shape = RoundedCornerShape(12.dp)
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    draggingId = page.id
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (draggingId != null && pointerYWindow >= trashTopWindow) {
                                        orderedPages = pages           // undo the optimistic reorder; delete asks first
                                        onDelete(page.id)
                                    } else {
                                        onReorder(orderedPages.map { it.id })
                                    }
                                    draggingId = null
                                },
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "打开第 ${ordinal + 1} 页") { if (!isDragging) onTapPage(page.id) },
                    ) {
                        Box {
                            ScanThumbnail(path = page.enhancedImagePath, width = 160.dp, height = 210.dp, corner = 12.dp)
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .size(26.dp)
                                    .background(if (isDragging) c.accent else c.text, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${ordinal + 1}", style = MaterialTheme.typography.labelSmall, color = if (isDragging) c.onAccent else c.bg)
                            }
                        }
                        Text(
                            scanFilterLabel(page.filter),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                    }
                }
            }
            item(key = "__add__") {
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .clip(shape)
                        .clickable(role = Role.Button, onClick = onAddPage),
                ) {
                    Box(
                        Modifier
                            .size(160.dp, 210.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = c.textTertiary,
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp))
                            Text("拍下一页", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // The delete strip rises while a page is dragged; it warms to the error colour when hovered.
        AnimatedVisibility(
            visible = draggingId != null,
            enter = slideInVertically(TgMotion.standard()) { it } + fadeIn(),
            exit = slideOutVertically(TgMotion.exit()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val bg by animateColorAsState(if (overTrash) c.error else c.surfaceRaised, tween(150), label = "trashBg")
            val fg by animateColorAsState(if (overTrash) c.bg else c.textSecondary, tween(150), label = "trashFg")
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .onGloballyPositioned { trashTopWindow = it.localToWindow(Offset.Zero).y }
                    .graphicsLayer { shadowElevation = 12.dp.toPx(); shape = RoundedCornerShape(32.dp); clip = true }
                    .background(bg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Text(
                    if (overTrash) "松手删除这一页" else "拖到这里删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
