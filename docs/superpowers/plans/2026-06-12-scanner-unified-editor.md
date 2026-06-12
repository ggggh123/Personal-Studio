# scanner 统一文档编辑器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。Steps 用 checkbox。

**Goal:** 去掉扫描文档"已完成/未完成"区分,任何文档点进去都进同一个编辑器(加/删/重排/改页+导出);新建直接开相机;退役独立的"新建向导"(Builder)及其"取消即删整篇"隐患。

**Architecture:** `ScanDocumentDetailScreen`(+VM)升级为唯一编辑器,吸收 Builder 的 拍照→边缘→增强 状态机(复用现有 use case 与 Camera/Edge/Enhance 复合屏)。编辑器路由复用 `scanner/detail/{docId}`,`docId==0` 约定为新建(VM 现建文档)。退役 Builder 屏+VM+`scanner/new` 路由+`onResumeDoc`。移除 `isPending` 全部用处。

**Tech Stack:** Kotlin/Compose/Hilt(AssistedInject)/Room/JUnit4。Windows `.\gradlew.bat`。

参考签名:`CreateScanDocumentUseCase(title): Long`、`CaptureAndEnhancePageUseCase(dir,tmp,corners,filter): Result(originalImagePath,enhancedImagePath,filter,cornersJson)`、`AddPageToDocumentUseCase(docId,orig,enh,filter,cornersJson)`、`DeleteScanDocumentUseCase(docId)`、`ReorderPagesUseCase(docId,ids)`、`RemovePageUseCase(pageId)`、`ExportDocumentToPdfUseCase(docId): File`。复合屏:`CameraCaptureScreen(outputDir:File, onCaptured:(File,Boolean,List<PointF>?)->Unit, onCancel)`、`EdgeDetectAndCropScreen(capturedFilePath:String, autoDetect:Boolean, preDetectedNormalized:List<PointF>?, onConfirm:(List<PointF>)->Unit, onRetake)`、`EnhanceReviewScreen(capturedFilePath:String, cornersBitmapPx:List<PointF>, onConfirm:(ScanFilter,Any?)->Unit, onCancel)`。

---

### Task 1: 统一编辑器 VM + Screen(一并改,紧耦合)

**Files:**
- Rewrite: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailViewModel.kt`
- Rewrite: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/scanner/library/ScanEditorExitTest.kt`

- [ ] **Step 1: 失败测试(退出清理纯函数)**

`ScanEditorExitTest.kt`:
```kotlin
package com.example.personal_studio.feature.scanner.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEditorExitTest {
    @Test fun `discard only when new and empty`() {
        assertTrue(shouldDiscardOnExit(isNew = true, pageCount = 0))   // 新建+没拍 → 丢弃空壳
        assertFalse(shouldDiscardOnExit(isNew = true, pageCount = 1))  // 新建+已拍 → 保留
        assertFalse(shouldDiscardOnExit(isNew = false, pageCount = 0)) // 已有文档 → 永不删
        assertFalse(shouldDiscardOnExit(isNew = false, pageCount = 3))
    }
}
```
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.scanner.library.ScanEditorExitTest"` → 编译失败(函数未定义)。

- [ ] **Step 2: 重写 ViewModel**

整文件替换 `ScanDocumentDetailViewModel.kt`:
```kotlin
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
```

- [ ] **Step 3: 跑测试 → PASS**

Run 同 Step 1 → PASS。

- [ ] **Step 4: 重写 Screen**

整文件替换 `ScanDocumentDetailScreen.kt`。保留 `ReorderablePageGrid`(原样)、`formatTs`;新增 `EditorStep` 状态机宿主 + `EditorView` + `EditorActionBar`(含 `[+ 添加页面]`):
```kotlin
package com.example.personal_studio.feature.scanner.library

import android.content.Intent
import android.graphics.PointF
import androidx.activity.compose.BackHandler
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
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
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
    var step by remember { mutableStateOf<EditorStep>(if (isNew) EditorStep.Capturing(0) else EditorStep.Viewing) }

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

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
```

- [ ] **Step 5: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL(此时 AppNavHost 仍传真实 docId 打开已有文档,新建仍走 Builder——均兼容)。
```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailViewModel.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt app/src/test/java/com/example/personal_studio/feature/scanner/library/ScanEditorExitTest.kt
git commit -m "feat(scanner): 详情页升级为统一编辑器(加页/删/重排/导出 + 空文档清理)"
```

---

### Task 2: 导航改线 + 库屏签名(新建→编辑器,退役 resume/Builder 路由)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt`

- [ ] **Step 1: ScanLibraryScreen 去 onResumeDoc + 菜单统一**

`ScanLibraryScreen.kt`:
- 函数签名删 `onResumeDoc` 参数:
  ```kotlin
  fun ScanLibraryScreen(
      onOpenDoc: (docId: Long) -> Unit,
      onNewDoc: () -> Unit,
  ) {
  ```
- `actionTarget?.let { doc -> DocActionsDialog(...) }` 调用处删 `onResume`/`onDiscard` 两个实参,只留 `onRename`/`onDelete`:
  ```kotlin
      actionTarget?.let { doc ->
          DocActionsDialog(
              doc = doc,
              onDismiss = { actionTarget = null },
              onRename = { actionTarget = null; renameTarget = doc },
              onDelete = { actionTarget = null; deleteTarget = doc },
          )
      }
  ```
- `DocActionsDialog` 改为不分 pending、永远 重命名/删除:
  ```kotlin
  @Composable
  private fun DocActionsDialog(
      doc: ScanDocumentSummary,
      onDismiss: () -> Unit,
      onRename: () -> Unit,
      onDelete: () -> Unit,
  ) {
      TerminalBottomSheet(onDismiss = onDismiss, header = "「${doc.title}」") {
          ActionLine("▸ 重命名", Foam, onRename)
          ActionLine("▸ 删除", Carmine, onDelete)
      }
  }
  ```
  (`Phosphor`/`Amber` 若因此变未使用,保留无妨;`Amber` 仍被 `[未完成]` 标签用——该标签 Task 3 才删,故此步 `Amber` 仍在用。)

- [ ] **Step 2: NavRoutes 删 scanner/new**

`NavRoutes.kt` 删除:
```kotlin
    // Scanner multi-page flow host (Phase 3).
    const val SCANNER_NEW_DOC = "scanner/new?resumeDocId={resumeDocId}"
    fun scannerNewDoc(resumeDocId: Long? = null) =
        if (resumeDocId == null) "scanner/new?resumeDocId=" else "scanner/new?resumeDocId=$resumeDocId"
```

- [ ] **Step 3: AppNavHost 改线**

`AppNavHost.kt`:
- `ScanLibraryScreen(...)` 块:`onNewDoc` 指向编辑器新建(`scannerDetail(0L)`)、删 `onResumeDoc`:
  ```kotlin
          composable(NavRoutes.SCANNER) {
              ScanLibraryScreen(
                  onNewDoc = { navController.navigate(NavRoutes.scannerDetail(0L)) },
                  onOpenDoc = { docId -> navController.navigate(NavRoutes.scannerDetail(docId)) },
              )
          }
  ```
- 删除整个 `composable(NavRoutes.SCANNER_NEW_DOC) { ... DocumentBuilderScreen ... }` 块(AppNavHost.kt:88-100)。
- 删 `import ...DocumentBuilderScreen`(若有)。

- [ ] **Step 4: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt
git commit -m "feat(scanner): 新建→统一编辑器(0=新建);退役 resume/new 路由 + 库菜单统一为重命名/删除"
```

---

### Task 3: 移除 isPending(模型 + 库标签 + 选择器标签)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/model/ScanModels.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryPickerScreen.kt`

- [ ] **Step 1: 库行去 `[未完成]` 标签**

`ScanLibraryScreen.kt` 的 `DocRow` 内,删除 pending 分支(只留 `drwx── ` + 标题):
```kotlin
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) { append("drwx── ") }
                    withStyle(SpanStyle(color = Foam)) { append(doc.title) }
                },
```
若 `Amber` 由此变未使用,删其 import。

- [ ] **Step 2: 选择器去 `[未完成]` 标签**

`ScanLibraryPickerScreen.kt` 的 `PickerDocRow` 内,删除:
```kotlin
                    if (doc.isPending) {
                        withStyle(SpanStyle(color = Amber)) { append("[未完成] ") }
                    }
```
若 `Amber` 由此变未使用,删其 import。

- [ ] **Step 3: 模型删 isPending**

`ScanModels.kt`:删除 `ScanDocument` 与 `ScanDocumentSummary` 的 `val isPending: Boolean get() = coverPageId == null`(两处)。`coverPageId` 字段保留。

- [ ] **Step 4: 编译 + 提交**

先 `Grep "isPending"`(应只剩无)确认无残留引用。
Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/domain/model/ScanModels.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryPickerScreen.kt
git commit -m "feat(scanner): 移除已完成/未完成区分(去 isPending 与 [未完成] 标签)"
```

---

### Task 4: QuickCaptureForChat 去 finalize

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/chat/QuickCaptureForChatViewModel.kt`

- [ ] **Step 1: 删 finalize 调用 + 依赖**

- 构造函数删 `private val finalizeDoc: FinalizeScanDocumentUseCase,` 与其 import `import com.example.personal_studio.domain.scanner.FinalizeScanDocumentUseCase`。
- `finalize(...)` 体内删 `finalizeDoc(docId)` 一行(仍 `createDoc` + `addPage`)。

- [ ] **Step 2: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/chat/QuickCaptureForChatViewModel.kt
git commit -m "feat(scanner): QuickCapture 不再 finalize(统一编辑器后无完成态)"
```

---

### Task 5: 退役 DocumentBuilder

**Files:**
- Delete: `app/src/main/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderScreen.kt`
- Delete: `app/src/main/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderViewModel.kt`
- Delete: `app/src/test/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderViewModelTest.kt`

- [ ] **Step 1: 确认无引用 + 删除**

先 `Grep "DocumentBuilder"`(应仅命中将删的 3 文件)。删除三文件:
```bash
git rm app/src/main/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderViewModel.kt app/src/test/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderViewModelTest.kt
```
(若 `DocumentBuilderViewModelTest.kt` 路径不同,以 Grep 结果为准。)

- [ ] **Step 2: 全量编译 + 单测 + 提交**

Run: `.\gradlew.bat :app:testDebugUnitTest` → 全绿。
```bash
git add -A
git commit -m "refactor(scanner): 删除已退役的 DocumentBuilder 屏+VM(+测试)"
```

---

### Task 6: 装真机验收 + 推分支

- [ ] **Step 1: 全量单测 + 装真机**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:installDebug` → 全绿 + Installed;`adb shell monkey -p com.example.personal_studio -c android.intent.category.LAUNCHER 1`。

- [ ] **Step 2: 真机 DoD**

1. 库:不再有 `[未完成]` 标签;长按任意文档 → 仅 重命名/删除(带确认)。
2. 点 `[+ 新建扫描]` → **直接开相机**;拍第一页 → 进编辑器(网格 1 页);相机首屏直接取消 → 回库且**无空文档残留**。
3. 点**任意**已有文档 → 进编辑器:`[+ 添加页面]` 可继续拍页;长按拖动重排;点单页可改滤镜/重拍/删页;`[📄 导出 PDF]` 正常。
4. 返回不删已有文档。

- [ ] **Step 3: 推分支**

```bash
git push -u origin feat/ui-refresh-scanner
```

---

## Self-Review

**1. Spec coverage:** 统一编辑器(详情吸收加页)→Task1;新建→编辑器+0=新建+相机优先+空文档清理→Task1(VM/onExit)+Task2(nav);退役 Builder/resume→Task2+5;去 isPending(模型+库+选择器+菜单统一)→Task2(菜单)+Task3;去 finalize→Task4;测试(onExit 纯函数)+真机 DoD→Task1+6。✓

**2. Placeholder scan:** 无 TBD;删除步均先 Grep 确认引用。Task2 注明 `Amber` 在 Task3 前仍被 `[未完成]` 用,避免误删 import。

**3. Type consistency:** `ScanDocumentDetailViewModel.Factory.create(docId: Long)` 不变(navDocId 同为 Long,0=新建);`shouldDiscardOnExit(isNew,pageCount)` Task1 定义+测试+VM.onExit 调用一致;`DocActionsDialog(doc,onDismiss,onRename,onDelete)` Task2 改签名后库调用处同步;`ScanLibraryScreen(onOpenDoc,onNewDoc)` 与 AppNavHost 调用一致。`EditorStep`/`EditorView`/`EditorActionBar` 自洽。
