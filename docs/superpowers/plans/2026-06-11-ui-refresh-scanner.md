# UI 翻新 · 第 2 期 scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 翻新 scanner 9 屏:库屏汉化+残留 Material(3 AlertDialog/输入框)换共享 Terminal* 控件+真封面缩略图+精修;拍摄链与详情/选择器纯汉化;PageEdit 删除框换 TerminalConfirmDialog。

**Architecture:** 加一个只读封面聚合查询(首页 enhancedImagePath,无 schema 变更)拿缩略图;库屏复用 Phase1 的 `TerminalBottomSheet/Confirm/Input`;其余屏就地汉化英文字面量(保留 shell/route/`drwx──` 等终端标识)。滤镜枚举名→中文标签用一个共享 helper。

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit4 + Turbine, Gradle(Windows `.\gradlew.bat`)。翻译"平衡"约定见 spec `docs/superpowers/specs/2026-06-11-ui-refresh-chat-design.md`。共享控件:`ui/components/TerminalDialog.kt`(`TerminalConfirmDialog(title,message,confirmLabel,onConfirm,onDismiss)`/`TerminalInputDialog(title,initial,onConfirm,onDismiss)`)、`ui/components/TerminalBottomSheet.kt`(`TerminalBottomSheet(onDismiss,header,content)`)。

---

### Task 1: 数据 — 封面缩略图聚合(ScanDocumentSummary)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/model/ScanModels.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/dao/ScanDocumentDao.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/ScanRepository.kt`
- Modify: `app/src/test/java/com/example/personal_studio/data/repository/FakeScanRepository.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/repository/ScanDocumentSummaryTest.kt`

- [ ] **Step 1: 领域模型 + DAO 投影/查询**

`ScanModels.kt` 末尾追加:
```kotlin
/** 库列表富行:文档 + 封面(首页 enhanced 图)路径。 */
data class ScanDocumentSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPageId: Long?,
    val coverPath: String?,
) {
    val isPending: Boolean get() = coverPageId == null
}
```

`ScanDocumentDao.kt`:在 `@Dao interface ScanDocumentDao {` 上方加投影类:
```kotlin
data class ScanDocumentSummaryRow(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPageId: Long?,
    val coverPath: String?,
)
```
interface 内加查询(放 `observeAllByRecentUpdated` 下):
```kotlin
    @Query(
        "SELECT d.id AS id, d.title AS title, d.createdAt AS createdAt, d.updatedAt AS updatedAt, " +
        "d.pageCount AS pageCount, d.coverPageId AS coverPageId, " +
        "(SELECT enhancedImagePath FROM scan_pages WHERE docId = d.id ORDER BY ordinal ASC LIMIT 1) AS coverPath " +
        "FROM scan_documents d"
    )
    fun observeDocumentSummaries(): Flow<List<ScanDocumentSummaryRow>>
```
(纯只读;排序在仓库层做。)

- [ ] **Step 2: 仓库接口 + 实现 + Fake**

`ScanRepository.kt` interface 加(在 `observeDocuments` 下):
```kotlin
    /** 同 observeDocuments,但带封面缩略图路径,按 [sort] 排序。 */
    fun observeDocumentSummaries(sort: SortMode): Flow<List<ScanDocumentSummary>>
```
impl 加(在 `observeDocuments` override 下):
```kotlin
    override fun observeDocumentSummaries(sort: SortMode): Flow<List<ScanDocumentSummary>> =
        docDao.observeDocumentSummaries().map { rows ->
            val mapped = rows.map {
                ScanDocumentSummary(
                    it.id, it.title, it.createdAt, it.updatedAt,
                    it.pageCount, it.coverPageId, it.coverPath,
                )
            }
            when (sort) {
                SortMode.TIME_DESC -> mapped.sortedByDescending { it.createdAt }
                SortMode.ALPHA_ASC -> mapped.sortedBy { it.title.lowercase() }
                SortMode.RECENT_UPDATED -> mapped.sortedByDescending { it.updatedAt }
            }
        }
```
顶部 import 加 `import com.example.personal_studio.domain.model.ScanDocumentSummary`。

`FakeScanRepository.kt` 加 override(在 `observeDocuments` 下;`pagesByDoc` 已在;import `ScanDocumentSummary`):
```kotlin
    override fun observeDocumentSummaries(sort: SortMode): Flow<List<ScanDocumentSummary>> =
        observeDocuments(sort).map { docs ->
            docs.map { d ->
                val cover = pagesByDoc[d.id]?.value?.minByOrNull { it.ordinal }?.enhancedImagePath
                ScanDocumentSummary(
                    d.id, d.title, d.createdAt, d.updatedAt,
                    d.pageCount, d.coverPageId, cover,
                )
            }
        }
```

- [ ] **Step 3: ScanLibraryViewModel 用 summary**

`ScanLibraryViewModel.kt`:把 `ScanLibraryUiState.docs` 类型与流改为 summary:
```kotlin
import com.example.personal_studio.domain.model.ScanDocumentSummary
```
```kotlin
data class ScanLibraryUiState(
    val sort: SortMode = SortMode.TIME_DESC,
    val docs: List<ScanDocumentSummary> = emptyList(),
)
```
```kotlin
    val uiState = sort
        .flatMapLatest { s ->
            repo.observeDocumentSummaries(s).map { docs -> ScanLibraryUiState(s, docs) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanLibraryUiState())
```
(`onRename`/`onDelete`/`setSort` 不变;它们用 use case,不碰 repo.observeDocuments。)

- [ ] **Step 4: 写并跑 Fake 行为测试**

创建 `ScanDocumentSummaryTest.kt`:
```kotlin
package com.example.personal_studio.data.repository

import app.cash.turbine.test
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.SortMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDocumentSummaryTest {
    @Test fun `cover is first page enhanced path; null when no pages`() = runTest {
        val repo = FakeScanRepository()
        val docA = repo.createPendingDocument("A")
        repo.appendPage(docA, "orig1", "enh1", ScanFilter.COLOR, null)
        repo.appendPage(docA, "orig2", "enh2", ScanFilter.COLOR, null)
        val docB = repo.createPendingDocument("B")   // 无页
        repo.observeDocumentSummaries(SortMode.TIME_DESC).test {
            val list = awaitItem()
            assertEquals("enh1", list.first { it.id == docA }.coverPath)  // 首页(ordinal 0)
            assertNull(list.first { it.id == docB }.coverPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `alpha sort orders by title case-insensitively`() = runTest {
        val repo = FakeScanRepository()
        repo.createPendingDocument("banana")
        repo.createPendingDocument("Apple")
        repo.observeDocumentSummaries(SortMode.ALPHA_ASC).test {
            assertEquals(listOf("Apple", "banana"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.data.repository.ScanDocumentSummaryTest"` → PASS。
然后 `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL(Room 生成聚合查询无误)。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/model/ScanModels.kt app/src/main/java/com/example/personal_studio/data/local/db/dao/ScanDocumentDao.kt app/src/main/java/com/example/personal_studio/data/repository/ScanRepository.kt app/src/test/java/com/example/personal_studio/data/repository/FakeScanRepository.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryViewModel.kt app/src/test/java/com/example/personal_studio/data/repository/ScanDocumentSummaryTest.kt
git commit -m "feat(scanner): 库文档封面缩略图聚合(首页 enhanced 图)"
```

---

### Task 2: 滤镜中文标签 helper(共享,3 屏复用)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/scanner/ScanFilterLabel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/scanner/ScanFilterLabelTest.kt`

- [ ] **Step 1: 失败测试**

创建 `ScanFilterLabelTest.kt`:
```kotlin
package com.example.personal_studio.feature.scanner

import com.example.personal_studio.domain.model.ScanFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFilterLabelTest {
    @Test fun `maps each filter to chinese label`() {
        assertEquals("彩色", scanFilterLabel(ScanFilter.COLOR))
        assertEquals("灰度", scanFilterLabel(ScanFilter.GRAYSCALE))
        assertEquals("黑白", scanFilterLabel(ScanFilter.BW))
    }
}
```
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.scanner.ScanFilterLabelTest"` → 编译失败。

- [ ] **Step 2: 实现**

创建 `ScanFilterLabel.kt`:
```kotlin
package com.example.personal_studio.feature.scanner

import com.example.personal_studio.domain.model.ScanFilter

/** 滤镜中文标签,供各 scanner 屏的 `[滤镜]` chip 复用。 */
fun scanFilterLabel(f: ScanFilter): String = when (f) {
    ScanFilter.COLOR -> "彩色"
    ScanFilter.GRAYSCALE -> "灰度"
    ScanFilter.BW -> "黑白"
}
```
Run 测试 → PASS。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/ScanFilterLabel.kt app/src/test/java/com/example/personal_studio/feature/scanner/ScanFilterLabelTest.kt
git commit -m "feat(scanner): 滤镜中文标签 helper"
```

---

### Task 3: ScanLibraryScreen 汉化 + Terminal* 控件 + 封面 + 精修

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt`

参考:`state.docs` 现为 `List<ScanDocumentSummary>`(Task1);`ScanDocument` 用法换 `ScanDocumentSummary`(字段 id/title/pageCount/createdAt/isPending/coverPath 都在)。

- [ ] **Step 1: 行类型/封面 + DocRow 汉化**

把 `DocRow(doc, ...)` 的参数类型 `ScanDocument` 改为 `ScanDocumentSummary`(import 换 `com.example.personal_studio.domain.model.ScanDocumentSummary`,删 `ScanDocument` import)。`ScanThumbnail(path = null)` 改为 `ScanThumbnail(path = doc.coverPath)`。行内字面量汉化:
- `append("[incomplete] ")` → `append("[未完成] ")`
- `"${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${formatTs(doc.createdAt)}"` → `"${doc.pageCount} 页 · ${formatTs(doc.createdAt)}"`
(保留 `drwx── `。`formatTs` 不变,绝对短日期。)

- [ ] **Step 2: 顶栏 + 排序栏汉化**

- 顶栏 trailing `"[+ new scan]"` → `"[+ 新建扫描]"`
- `SortToolbar` 的 `listOf(... to "time", ... to "alpha", ... to "recent")` 标签改为 `"时间"`/`"名称"`/`"最近"`(active 时仍 `[标签]`)。`buildSortSubtitle` 里同样三处 `"time"/"alpha"/"recent"` → `"时间"/"名称"/"最近"`(保留 `# sort: ` 前缀)。

- [ ] **Step 3: DocActionsDialog → TerminalBottomSheet**

把 `DocActionsDialog` 整个 composable 替换为基于 `TerminalBottomSheet` 的动作菜单(import `com.example.personal_studio.ui.components.TerminalBottomSheet`;删 `androidx.compose.material3.AlertDialog`、`TextButton` 若不再用):
```kotlin
@Composable
private fun DocActionsDialog(
    doc: ScanDocumentSummary,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val header = if (doc.isPending) "「${doc.title}」· 未完成" else "「${doc.title}」"
    TerminalBottomSheet(onDismiss = onDismiss, header = header) {
        if (doc.isPending) {
            ActionLine("▸ 恢复", Phosphor, onResume)
            ActionLine("▸ 丢弃", Amber, onDiscard)
        } else {
            ActionLine("▸ 重命名", Foam, onRename)
            ActionLine("▸ 删除", Carmine, onDelete)
        }
    }
}

@Composable
private fun ActionLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label, color = color, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
```
(需 import `com.example.personal_studio.ui.theme.Carmine`。`DocActionsDialog(...)` 调用处的参数不变。)

- [ ] **Step 4: RenameDialog → TerminalInputDialog;DeleteConfirmDialog → TerminalConfirmDialog**

删除 `RenameDialog` 与 `DeleteConfirmDialog` 两个私有 composable;在调用处直接用共享控件。即把:
```kotlin
    renameTarget?.let { doc ->
        RenameDialog(
            initial = doc.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle -> renameTarget = null; vm.onRename(doc.id, newTitle) },
        )
    }
    deleteTarget?.let { doc ->
        DeleteConfirmDialog(
            title = doc.title,
            onDismiss = { deleteTarget = null },
            onConfirm = { deleteTarget = null; vm.onDelete(doc.id) },
        )
    }
```
替换为:
```kotlin
    renameTarget?.let { doc ->
        TerminalInputDialog(
            title = "重命名扫描", initial = doc.title,
            onConfirm = { newTitle -> renameTarget = null; vm.onRename(doc.id, newTitle) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { doc ->
        TerminalConfirmDialog(
            title = "删除扫描", message = "删除「${doc.title}」？此操作不可撤销。", confirmLabel = "删除",
            onConfirm = { deleteTarget = null; vm.onDelete(doc.id) },
            onDismiss = { deleteTarget = null },
        )
    }
```
import `com.example.personal_studio.ui.components.TerminalInputDialog`、`TerminalConfirmDialog`。删除两个私有 composable 后,确认 `OutlinedTextField`、`AlertDialog`、`TextButton`、`SimpleDateFormat`(formatTs 仍用→保留)等 import 仅删真正不再用的(`OutlinedTextField`、`AlertDialog`、`TextButton` 应可删)。

- [ ] **Step 5: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryScreen.kt
git commit -m "feat(scanner): 扫描库汉化+终端弹窗+真封面缩略图"
```

---

### Task 4: PageEditScreen 汉化 + 删除框换 TerminalConfirmDialog

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/doc/PageEditScreen.kt`

- [ ] **Step 1: 字面量汉化(就地 old→new)**

- `"# page #${it.id}"` → `"# 第 ${it.id} 页"` （保留 `#`）
- `"[↻ retake]"` → `"[↻ 重拍]"`
- `"[+ archive]"` → `"[+ 归档]"`
- `"[x delete page]"` → `"[x 删除此页]"`
- 滤镜 chip:把 `"[${f.name.lowercase()}]"` 改为 `"[${scanFilterLabel(f)}]"`(import `com.example.personal_studio.feature.scanner.scanFilterLabel`;`f` 为 `ScanFilter`)
- 底部 `"[cancel]"` → `"[取消]"`、`"[confirm ↵]"` → `"[确认 ↵]"`

- [ ] **Step 2: 删除 AlertDialog → TerminalConfirmDialog**

把删除确认 `AlertDialog(...)`(title `"delete this page?"`、body `"the image files will be removed. this cannot be undone."`、confirm `"[ delete ]"`、dismiss `"[ cancel ]"`)整块替换为:
```kotlin
        TerminalConfirmDialog(
            title = "删除此页",
            message = "图片文件将被移除，此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = { /* 原 confirmButton onClick 逻辑 */ },
            onDismiss = { /* 原 onDismissRequest / dismiss 逻辑 */ },
        )
```
(把原 `AlertDialog` 的 confirm onClick 体放进 onConfirm、dismiss 体放进 onDismiss。)import `com.example.personal_studio.ui.components.TerminalConfirmDialog`;删 `AlertDialog`、`TextButton`(若不再用)import。

- [ ] **Step 3: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/doc/PageEditScreen.kt
git commit -m "feat(scanner): PageEdit 汉化 + 删除框终端化"
```

---

### Task 5: 其余 6 屏纯汉化(无 Material 控件)

**Files:**
- Modify: `feature/scanner/camera/CameraCaptureScreen.kt`
- Modify: `feature/scanner/edge/EdgeDetectAndCropScreen.kt`
- Modify: `feature/scanner/enhance/EnhanceReviewScreen.kt`
- Modify: `feature/scanner/doc/DocumentBuilderScreen.kt`
- Modify: `feature/scanner/library/ScanDocumentDetailScreen.kt`
- Modify: `feature/scanner/library/ScanLibraryPickerScreen.kt`

逐文件就地汉化(精确 old→new;保留 route 名、`drwx──`、`#` 前缀、`p.N`/`pN` 短页号)。每改完一个文件 `compileDebugKotlin` 一次。

- [ ] **Step 1: CameraCaptureScreen**
- `"[auto ${...}]"`:把 `"auto"` 文案译中——该处形如 `"[auto ${if (x) "✓" else "✗"}]"`,改为 `"[自动 ${if (x) "✓" else "✗"}]"`
- `"[⚡ ${if (x) "on" else "off"}]"` → `"[⚡ ${if (x) "开" else "关"}]"`
- `"[cancel]"`(快门栏)→ `"[取消]"`
- `"[ ● capture ]"` → `"[ ● 拍摄 ]"`
- `"[!] camera permission denied"` → `"[!] 相机权限被拒绝"`
- `"capture requires the camera permission to be granted."` → `"拍摄需要授予相机权限。"`
- `"[grant]"` → `"[授权]"`
- `"[cancel]"`(权限页)→ `"[取消]"`

- [ ] **Step 2: EdgeDetectAndCropScreen**
- `"[↻ retake]"` → `"[↻ 重拍]"`
- `"✓ corners auto-detected"` → `"✓ 已自动识别边角"`
- `"! drag corners to fit"` → `"! 拖动四角对齐"`
- `"[confirm ↵]"` → `"[确认 ↵]"`

- [ ] **Step 3: EnhanceReviewScreen**
- 滤镜 chip `"[${f.name.lowercase()}]"` → `"[${scanFilterLabel(f)}]"`(import scanFilterLabel)
- `"[↻ rot]"` → `"[↻ 旋转]"`
- `"save to scans/"` → `"保存到扫描库"`（`[x]`/`[ ]` 复选字形保留）
- `"[cancel]"` → `"[取消]"`、`"[confirm ↵]"` → `"[确认 ↵]"`

- [ ] **Step 4: DocumentBuilderScreen**
- `"[x cancel]"` → `"[x 取消]"`
- `"$pageCount page${if (pageCount == 1) "" else "s"} in this doc"` → `"本文档共 $pageCount 页"`
- `"# no pages yet — tap capture below"` → `"# 还没有页面 —— 点下方拍摄"`
- `"[+ add next page]"` → `"[+ 添加下一页]"`
- `"[↵ finish]"` → `"[↵ 完成]"`
- `"p${page.ordinal + 1}"` 保留(短页号);`"?"` 解码失败占位保留

- [ ] **Step 5: ScanDocumentDetailScreen**
- `"Share PDF"`(系统分享标题)→ `"分享 PDF"`
- title 加载占位 `"…"` 保留
- 副标题模板 `"# session: $title..."` 的 `session:` → `会话:`;`page${...}` 复数 → `页`(即 `"# 会话: $title\n# $pageCount 页 · ${formatTs(it)}"` 形态)
- `"[< back]"` → `"[< 返回]"`
- 网格标注 `"p.${ordinal + 1} · ${page.filter.name.lowercase()}"` → `"p.${ordinal + 1} · ${scanFilterLabel(page.filter)}"`(import scanFilterLabel)
- `"[... exporting pdf]"` → `"[... 导出 PDF 中]"`
- `"[📄 export pdf]"` → `"[📄 导出 PDF]"`

- [ ] **Step 6: ScanLibraryPickerScreen**
- `"# pick a doc, then a page"` → `"# 选一个文档,再选一页"`
- `"[x cancel]"` → `"[x 取消]"`
- `"[incomplete] "` → `"[未完成] "`（保留前导 `drwx── `）
- `"${doc.pageCount} page${...}"` → `"${doc.pageCount} 页"`
- `"# tap a page to attach"` → `"# 点一页以附加"`
- `"[< back]"` → `"[< 返回]"`
- 网格标注同 Step5:`scanFilterLabel(page.filter)`

- [ ] **Step 7: 全量编译 + 单测 + 提交**

Run: `.\gradlew.bat :app:testDebugUnitTest` → 全绿。
```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/camera/CameraCaptureScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/edge/EdgeDetectAndCropScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/enhance/EnhanceReviewScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/doc/DocumentBuilderScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanLibraryPickerScreen.kt
git commit -m "feat(scanner): 拍摄链+详情/选择器+构建器汉化"
```

---

### Task 6: 装真机验收 + 推分支

- [ ] **Step 1: 全量单测 + 装真机**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:installDebug` → 全绿 + Installed。

- [ ] **Step 2: 真机 DoD(手动,逐屏)**

1. 扫描库:行内**真封面缩略图**、`drwx── 标题`/`N 页 · 日期`、`[+ 新建扫描]`、排序 时间/名称/最近、长按→终端动作菜单(完成件 重命名/删除;未完成件 恢复/丢弃)、重命名(终端输入框)、删除确认(终端弹窗)。
2. 详情/选择器:汉化、缩略图网格、导出/分享中文。
3. 拍摄链:相机/边缘/增强/页编辑各屏汉化、滤镜中文(彩色/灰度/黑白)、图像清晰**无扫描线**;页编辑删除→终端确认。

- [ ] **Step 3: 推分支**

```bash
git push -u origin feat/ui-refresh-scanner
```

---

## Self-Review

**1. Spec coverage:**
- 封面聚合(无 schema 变更)+ repo 排序 + Fake + VM → Task 1 ✓
- 滤镜中文标签(3 屏复用)→ Task 2 ✓
- 库屏:3 AlertDialog/输入框换 Terminal* + 汉化 + 真封面 + 精修 → Task 3 ✓
- PageEdit:汉化 + 删除框换 TerminalConfirm → Task 4 ✓
- 拍摄链 + 详情/选择器 + 构建器汉化(图像面只改 chrome、不铺扫描线)→ Task 5 ✓
- 测试(聚合 Fake + 滤镜 helper)+ 真机 DoD → Task 1/2/6 ✓

**2. Placeholder scan:** Task 4 Step2 的 `onConfirm/onDismiss` 注释("原 confirmButton onClick 逻辑")是指"把现有 AlertDialog 的对应回调体搬过来"——实现者读文件即得;其余均为精确 old→new 或完整代码,无 TBD。

**3. Type consistency:** `ScanDocumentSummary`(Task1)字段 id/title/createdAt/updatedAt/pageCount/coverPageId/coverPath + isPending,在 DAO 投影 `ScanDocumentSummaryRow`(同列)、repo/Fake 映射、VM `docs`、ScanLibraryScreen `DocRow`/`DocActionsDialog` 一致;`scanFilterLabel(ScanFilter)`(Task2)在 Task3/4/5 各 chip 调用一致;`TerminalBottomSheet/Confirm/Input` 签名与调用一致。
