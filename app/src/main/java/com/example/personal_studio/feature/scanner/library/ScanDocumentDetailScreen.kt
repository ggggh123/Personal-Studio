package com.example.personal_studio.feature.scanner.library

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
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
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
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(
            route = "scans/$title",
            subtitle = "# 会话: $title\n# ${pages.size} 页" +
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
            pages = pages,
            onTapPage = onTapPage,
            onReorder = onReorder,
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
        )
        EditorActionBar(
            canExport = pages.isNotEmpty() && !isExporting,
            isExporting = isExporting,
            onAddPage = onAddPage,
            onExport = onExport,
        )
    }
}

@Composable
private fun EditorActionBar(
    canExport: Boolean,
    isExporting: Boolean,
    onAddPage: () -> Unit,
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "[+ 添加页面]",
            color = Phosphor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = onAddPage),
        )
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

/**
 * 2 列网格,每格支持长按拖动:拖到其它格=重排;拖到底部回收区松手=删除(请求确认)。
 * orderedPages 是拖动期的真值(onMove 乐观改);松手提交一次,或落在回收区时撤销乐观重排并请求删除。
 *
 * 落点检测:回收区与指针都换算到窗口坐标(LayoutCoordinates.localToWindow)。重排库的拖动在 Main
 * pass 消费,本组件在 Initial pass 旁观指针位置(不消费),故拖动期可知是否悬停回收区。
 */
@Composable
private fun ReorderablePageGrid(
    pages: List<ScanPage>,
    onTapPage: (pageId: Long) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onDelete: (pageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        orderedPages = orderedPages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    val haptics = LocalHapticFeedback.current

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var boxTopWindow by remember { mutableFloatStateOf(0f) }
    var trashTopWindow by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var pointerYWindow by remember { mutableFloatStateOf(0f) }
    val overTrash by remember {
        derivedStateOf { draggingId != null && pointerYWindow >= trashTopWindow }
    }

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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 84.dp),
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
                                    draggingId = page.id
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (draggingId != null && pointerYWindow >= trashTopWindow) {
                                        orderedPages = pages           // 撤销拖动期乐观重排,等确认
                                        onDelete(page.id)
                                    } else {
                                        onReorder(orderedPages.map { it.id })
                                    }
                                    draggingId = null
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

        // 拖动时浮出的回收区(网格底部);悬停其上时高亮 Carmine。
        if (draggingId != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .onGloballyPositioned { trashTopWindow = it.localToWindow(Offset.Zero).y }
                    .background(if (overTrash) Carmine.copy(alpha = 0.30f) else Void.copy(alpha = 0.92f))
                    .border(1.dp, if (overTrash) Carmine else FoamDim),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (overTrash) "松手删除此页" else "🗑 拖到此处删除",
                    color = if (overTrash) Carmine else FoamMute,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
