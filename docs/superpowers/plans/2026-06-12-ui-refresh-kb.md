# UI 翻新 · 第 3 期 kb Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。Steps 用 checkbox。

**Goal:** kb 界面翻新收官:落地页加 `ls kb/` 头部 + chat 同款空态;详情页 3 AlertDialog/DropdownMenu/转圈 换共享终端控件;SavePreviewModal 补 CRT 纹理;分类 sheet 换 TerminalBottomSheet;全面汉化残英;删死代码。

**Architecture:** 复用 Phase1/2 共享件(`TerminalConfirmDialog`/`TerminalInputDialog`/`TerminalBottomSheet`/`BlinkingCursor`);新建 Popup 版 `TerminalDropdownMenu`。无数据/网络改动。

**Tech Stack:** Kotlin/Compose/Hilt。Windows `.\gradlew.bat`。翻译"平衡"约定见 spec。

共享件签名(已存在):`TerminalConfirmDialog(title,message,confirmLabel,onConfirm,onDismiss)`(取消恒 `[取消]`,确认为 `[confirmLabel]` Carmine)、`TerminalInputDialog(title,initial,onConfirm:(String)->Unit,onDismiss)`(自带输入+`[确认]`)、`TerminalBottomSheet(onDismiss,header,content:@Composable ()->Unit)`(内部 Column,**勿放 LazyColumn**)、`BlinkingCursor()`。

---

### Task 1: 新建 TerminalDropdownMenu(共享终端下拉)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/ui/components/TerminalDropdownMenu.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

/** 终端风下拉菜单:Popup + Void 底 + 0dp 直角 + Rule 边框,锚在触发处右上、下落 ~44dp。 */
@Composable
fun TerminalDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(180.dp)
                .background(Void, RectangleShape)
                .border(1.dp, Rule, RectangleShape)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

/** 下拉项:`▸ label`。 */
@Composable
fun TerminalDropdownItem(label: String, color: Color = Foam, onClick: () -> Unit) {
    Text(
        "▸ $label",
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
```

- [ ] **Step 2: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/ui/components/TerminalDropdownMenu.kt
git commit -m "feat(ui): 共享终端下拉控件 TerminalDropdownMenu(Popup)"
```

---

### Task 2: KbHomeScreen 头部 + 空态 + 汉化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbHomeScreen.kt`

- [ ] **Step 1: import 调整**

删 `import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder`。在 theme imports 区加:
```kotlin
import androidx.compose.foundation.layout.height
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamMute
```
(`Foam`/`FoamDim`/`Phosphor`/`Void` 已在;`Spacer`/`width`/`padding`/`fillMaxWidth` 已在。)

- [ ] **Step 2: 顶部加 `ls kb/` 头部 + total**

把 `Column(Modifier.fillMaxSize()) {` 内、`KbSearchBar(...)` 之前插入:
```kotlin
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Amber)) { append("user@study") }
                        withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                        withStyle(SpanStyle(color = Foam)) { append("ls kb/") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "total ${state.notesCount + state.mistakesCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoamMute,
                )
            }
            Spacer(Modifier.height(10.dp))
            KbSearchBar(query = state.searchQuery, onQueryChange = vm::onSearchChange)
```
(即在原 `KbSearchBar(...)` 上方加这段;原 KbSearchBar 行保留。)

- [ ] **Step 3: 过滤 chip + 分隔符汉化**

- `StatChip("all", total, ...)` → `StatChip("全部", total, ...)`
- `StatChip("notes", ...)` → `StatChip("笔记", ...)`
- `StatChip("mistakes", ...)` → `StatChip("错题", ...)`
- 分隔符:`"─────────── ${if (state.isSearching) "matches" else "recent"} ───────────"` → `"─────────── ${if (state.isSearching) "匹配" else "最近"} ───────────"`

- [ ] **Step 4: 空态换 chat 同款**

把:
```kotlin
            if (displayedEntries.isEmpty()) {
                KnowledgePlaceholder()
            } else {
```
改为:
```kotlin
            if (displayedEntries.isEmpty()) {
                KbEmptyState()
            } else {
```
并在文件末尾(`StatChip` 之后)加:
```kotlin
@Composable
private fun KbEmptyState() {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text("# 暂无条目", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("从 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[聊天]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 或 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[扫描]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 归档生成") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            BlinkingCursor()
        }
    }
}
```

- [ ] **Step 5: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbHomeScreen.kt
git commit -m "feat(kb): 落地页加 ls kb/ 头部+total + chat 同款空态 + 过滤汉化"
```

---

### Task 3: KbEntryDetailScreen 终端化 + 汉化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt`

- [ ] **Step 1: import 调整**

删:`import androidx.compose.material3.AlertDialog`、`androidx.compose.material3.CircularProgressIndicator`、`androidx.compose.material3.DropdownMenu`、`androidx.compose.material3.DropdownMenuItem`、`androidx.compose.material3.TextButton`、`androidx.compose.foundation.text.BasicTextField`、`androidx.compose.ui.graphics.SolidColor`。
加:
```kotlin
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalDropdownItem
import com.example.personal_studio.ui.components.TerminalDropdownMenu
import com.example.personal_studio.ui.components.TerminalInputDialog
```
删 `renameDraft` 状态:`var renameDraft by remember { mutableStateOf("") }`(整行删)。

- [ ] **Step 2: DropdownMenu → TerminalDropdownMenu**

把 `DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) { 4×DropdownMenuItem }` 整块替换为:
```kotlin
                            TerminalDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                TerminalDropdownItem("重命名") { showRename = true; menuExpanded = false }
                                TerminalDropdownItem("改分类") { showCategorySheet = true; menuExpanded = false }
                                TerminalDropdownItem("重新生成") { showRegenerateConfirm = true; menuExpanded = false }
                                TerminalDropdownItem("删除", Carmine) { showDeleteConfirm = true; menuExpanded = false }
                            }
```
(重命名项不再设 `renameDraft`;改由对话框取当前标题。)

- [ ] **Step 3: 加载/未找到/忙碌 终端化**

- Loading:`is KbEntryDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Phosphor) }` → `is KbEntryDetailUiState.Loading -> Centered { Text("$ 加载中…", color = FoamDim) }`
- NotFound 文案:`Text("! entry not found", color = Carmine)` → `Text("! 条目未找到", color = Carmine)`
- 忙碌遮罩内:把
  ```kotlin
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      CircularProgressIndicator(color = Phosphor)
                      Spacer(Modifier.height(12.dp))
                      Text("$ working...", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
                  }
  ```
  改为:
  ```kotlin
                  Row(verticalAlignment = Alignment.CenterVertically) {
                      Text("$ 处理中…", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
                      BlinkingCursor()
                  }
  ```

- [ ] **Step 4: 3 个 AlertDialog → 共享件**

把 `if (showRename) { AlertDialog(...) }` 整块替换为:
```kotlin
        if (showRename) {
            TerminalInputDialog(
                title = "重命名条目",
                initial = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty(),
                onConfirm = { vm.rename(it); showRename = false },
                onDismiss = { showRename = false },
            )
        }
```
把 `if (showDeleteConfirm) { AlertDialog(...) }` 整块替换为:
```kotlin
        if (showDeleteConfirm) {
            TerminalConfirmDialog(
                title = "删除此条目",
                message = "将删除 kb_entries 行 + 本地图片 + 关联关系，无法撤销。",
                confirmLabel = "删除",
                onConfirm = { vm.delete(onBack); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false },
            )
        }
```
把 `if (showRegenerateConfirm) { AlertDialog(...) }` 整块替换为:
```kotlin
        if (showRegenerateConfirm) {
            TerminalConfirmDialog(
                title = "重新生成摘要",
                message = "将重新调 LLM 覆盖 摘要 + 标准化题目；标题/分类保留。",
                confirmLabel = "重新生成",
                onConfirm = { vm.regenerate(); showRegenerateConfirm = false },
                onDismiss = { showRegenerateConfirm = false },
            )
        }
```

- [ ] **Step 5: summary 标签汉化**

`Loaded` 内 `SummaryMarkdownEditor(initial = e.summaryMarkdown, label = "summary", ...)` → `label = "摘要"`。(`MistakeHeader` 的 `label = "题目"` 已中文,不动。)

- [ ] **Step 6: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt
git commit -m "feat(kb): 详情页下拉/对话框/忙碌态终端化 + 汉化"
```

---

### Task 4: SavePreviewModal 补 CRT 纹理 + 汉化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/SavePreviewModal.kt`

- [ ] **Step 1: 纹理**

加 import:
```kotlin
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
```
把 `Box(Modifier.fillMaxSize().background(Void)) {`(Dialog 内根容器)改为:
```kotlin
        Box(Modifier.fillMaxSize().background(Void).scanLines().vignette()) {
```

- [ ] **Step 2: 汉化(精确 old→new)**

- `label = "$ thinking..."` → `label = "$ 思考中…"`
- `label = "$ writing entry..."` → `label = "$ 写入条目中…"`
- `"[cancel]"`(InflightSpinner 内)→ `"[取消]"`
- `"! llm error: $message"` → `"! LLM 错误：$message"`
- `"[retry]"` → `"[重试]"`
- `"[cancel]"`(ErrorBlock 内)→ `"[取消]"`
- `"archive draft"` → `"归档草稿"`
- `"[save]"` → `"[保存]"`
- `FieldLabel("title")` → `FieldLabel("标题")`
- `FieldLabel("category")` → `FieldLabel("分类")`
- `FieldLabel("standardized question", Modifier.weight(1f))` → `FieldLabel("标准化题目", Modifier.weight(1f))`
- `FieldLabel("summary", Modifier.weight(1f))` → `FieldLabel("摘要", Modifier.weight(1f))`
- `FieldLabel("related (AI-suggested)")` → `FieldLabel("关联(AI 建议)")`
- 两处 `if (editingStandardizedQuestionRaw) "[preview]" else "[edit raw markdown]"` / `if (editingSummaryRaw) "[preview]" else "[edit raw markdown]"` → `"[预览]"` / `"[编辑源码]"`
(`FieldLabel` 内部已加 `"$ "` 前缀,故只译括号内文案;`其它` 兜底、fallback 横幅已中文,不动。)

- [ ] **Step 3: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/SavePreviewModal.kt
git commit -m "feat(kb): SavePreviewModal 补 CRT 纹理 + 字段/按钮汉化"
```

---

### Task 5: CategoryPickerSheet → TerminalBottomSheet + 汉化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryPickerSheet.kt`

- [ ] **Step 1: 换 sheet 容器 + 列表改 Column**

删 import:`androidx.compose.material3.ExperimentalMaterial3Api`、`androidx.compose.material3.ModalBottomSheet`、`androidx.compose.material3.rememberModalBottomSheetState`、`androidx.compose.foundation.lazy.LazyColumn`、`androidx.compose.foundation.lazy.items`、`com.example.personal_studio.ui.theme.Void`。
加 import:`com.example.personal_studio.ui.components.TerminalBottomSheet`。
删 `@OptIn(ExperimentalMaterial3Api::class)` 注解。
把函数体从 `val sheetState = ...` 起、`ModalBottomSheet(...) { Column(...) { ... } }` 整体改为(TerminalBottomSheet 自带 Column+header;分类用 forEach 不用 LazyColumn):
```kotlin
    var newName by remember { mutableStateOf("") }
    TerminalBottomSheet(onDismiss = onDismiss, header = "选择分类") {
        categories.forEach { c ->
            val sel = c.id == selectedId
            Text(
                if (sel) "▎ ${c.name}  ✓" else "▎ ${c.name}",
                color = if (sel) Phosphor else Foam,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(c); onDismiss() }
                    .padding(vertical = 8.dp),
            )
        }
        Text(
            "$ + 新建分类",
            color = FoamDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.weight(1f).padding(8.dp),
            )
            Text(
                "[新建]",
                color = if (newName.isBlank()) FoamDim else Phosphor,
                modifier = Modifier
                    .clickable(enabled = newName.isNotBlank()) {
                        onCreate(newName.trim())
                        newName = ""
                        onDismiss()
                    }
                    .padding(8.dp),
            )
        }
    }
```
(去掉了原 `$ select category`——已由 TerminalBottomSheet 的 `── 选择分类 ──` 头替代;`$ + 新建分类` 保留中文。)

- [ ] **Step 2: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryPickerSheet.kt
git commit -m "feat(kb): 分类选择器换 TerminalBottomSheet + 汉化"
```

---

### Task 6: SummaryMarkdownEditor 按钮汉化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/SummaryMarkdownEditor.kt`

- [ ] **Step 1: 汉化**

- `"[edit]"` → `"[编辑]"`
- `"[cancel]"` → `"[取消]"`
- `"[save]"` → `"[保存]"`
(`## $label` 的 label 由调用方传中文,不动。)

- [ ] **Step 2: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL。
```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/SummaryMarkdownEditor.kt
git commit -m "feat(kb): SummaryMarkdownEditor 按钮汉化"
```

---

### Task 7: 删死代码 KbEntryDetailPlaceholderScreen

**Files:**
- Delete: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailPlaceholderScreen.kt`

- [ ] **Step 1: 复核无引用 + 删除**

先 `Grep "KbEntryDetailPlaceholderScreen"`(应仅命中将删文件自身)。删:
```bash
git rm app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailPlaceholderScreen.kt
```

- [ ] **Step 2: 全量编译 + 单测 + 提交**

Run: `.\gradlew.bat :app:testDebugUnitTest` → 全绿。
```bash
git add -A
git commit -m "refactor(kb): 删除死代码 KbEntryDetailPlaceholderScreen"
```

---

### Task 8: 装真机验收 + 推分支

- [ ] **Step 1: 全量单测 + 装真机**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:installDebug` → 全绿 + Installed;`adb shell monkey -p com.example.personal_studio -c android.intent.category.LAUNCHER 1`。

- [ ] **Step 2: 真机 DoD**

1. kb tab:顶部 `user@study:~$ ls kb/` + `total N`;过滤 `[全部]/[笔记]/[错题]`;分隔符 `匹配/最近`;空态 `# 暂无条目` + `▓ 从 [聊天] 或 [扫描] 归档生成` + 闪烁光标。
2. 进一条条目:右上溢出 → 终端下拉(`▸ 重命名/▸ 改分类/▸ 重新生成/▸ 删除`);重命名(终端输入框)、删除/重新生成(终端确认框);忙碌时 `$ 处理中…` + 光标;摘要标签 `## 摘要`。
3. 改分类 → 终端底部弹层(`── 选择分类 ──`),可选/新建(`[新建]`)。
4. 从 chat/scanner 归档 → SavePreviewModal:有 CRT 纹理;字段 `$ 标题/$ 分类/$ 摘要/$ 标准化题目/$ 关联(AI 建议)`、按钮 `归档草稿/[保存]/[预览]/[编辑源码]`。

- [ ] **Step 3: 推分支**

```bash
git push -u origin feat/ui-refresh-kb
```

---

## Self-Review

**1. Spec coverage:** ① KbHome 头部+total+空态+过滤汉化→Task2;② 详情 3 AlertDialog→Terminal* + DropdownMenu→TerminalDropdownMenu + 转圈→文字 + 汉化→Task3;③ SavePreviewModal 纹理+汉化→Task4;④ TerminalDropdownMenu 新建→Task1;⑤ 删死代码→Task7;CategoryPickerSheet→TerminalBottomSheet→Task5;SummaryMarkdownEditor 汉化→Task6。✓

**2. Placeholder scan:** 无 TBD;删除步先 Grep 复核;每步精确 old→new 或完整代码。

**3. Type consistency:** `TerminalDropdownMenu(expanded,onDismissRequest,content)` + `TerminalDropdownItem(label,color=Foam,onClick)`(Task1)在 Task3 调用一致;`TerminalConfirmDialog`/`TerminalInputDialog`/`TerminalBottomSheet` 用现有签名;`state.notesCount`/`state.mistakesCount`/`state.entries`/`state.isSearching`(KbHomeUiState)与 KbHomeScreen 现有读法一致;`vm.rename/delete/regenerate/changeCategory/upsertCategoryAndUse`(KbEntryDetailViewModel)沿用现有调用,未改 VM。
