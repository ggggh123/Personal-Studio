# UI 翻新 · 第 1 期 chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 翻新 chat 三屏：ChatList 重做为终端风「会话管理概览」(分组/相对时间/末条预览+消息数/重命名+删除)，ChatDetail/AttachmentSheet/ImageCropOverlay 汉化+字形化，并立起共享终端控件(Dialog/ConfirmDialog/InputDialog/BottomSheet) 供后两期复用。

**Architecture:** 新增一个只读聚合查询拿会话摘要(无 schema 变更)；纯函数做日期分组/相对时间(可单测)；4 个共享终端控件替掉残留 Material；逐屏汉化按 spec 字符串映射就地改。

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit4 + Turbine + mockk, Gradle (Windows `.\gradlew.bat`)。颜色: Void/Deep/Rule/Foam/FoamMute/FoamDim/Phosphor/Amber/Cyan/Carmine (in `ui/theme/Color.kt`)。终端约定见 spec `docs/superpowers/specs/2026-06-11-ui-refresh-chat-design.md`。

---

### Task 1: ChatListGrouping — 日期分组 + 行内相对时间(纯函数)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatListGrouping.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/chat/ui/ChatListGroupingTest.kt`

依赖一个尚未建的 `ChatSessionSummary`(Task 3 建)。本任务先只用它的 `updatedAt` 字段做分组，故**先在本文件内最小定义**会冲突——改为：本任务的纯函数对 `List<Pair<Long, T>>` 泛型操作以避免顺序依赖。最终签名:

- [ ] **Step 1: 写失败测试**

创建 `ChatListGroupingTest.kt`:
```kotlin
package com.example.personal_studio.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListGroupingTest {
    // now = 2024-06-15 12:00 固定时刻(本地)，用相对偏移构造时间戳避免时区脆弱。
    private val now = 1_718_424_000_000L

    @Test fun `rowTime today shows clock, yesterday shows 昨天, older shows md`() {
        assertEquals("HH:mm 形态", 5, ChatListGrouping.rowTime(now - 60_000, now).length) // "11:59" 长度5
        assertEquals("昨天", ChatListGrouping.rowTime(now - 26 * 3_600_000L, now))
        assertEquals("3天前", ChatListGrouping.rowTime(now - 3 * 86_400_000L, now))
    }

    @Test fun `group splits into 今天 and 更早 in order`() {
        val groups = ChatListGrouping.group(
            listOf(now - 3_600_000L, now - 3 * 86_400_000L, now - 60_000L), now,
        ) { it }
        assertEquals(listOf("今天", "更早"), groups.map { it.label })
        assertEquals(2, groups[0].items.size)   // 两条今天
        assertEquals(1, groups[1].items.size)   // 一条更早
    }

    @Test fun `empty yields no groups`() {
        assertEquals(emptyList<ChatListGrouping.Group<Long>>(), ChatListGrouping.group(emptyList<Long>(), now) { it })
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.chat.ui.ChatListGroupingTest"`
Expected: 编译失败(`Unresolved reference: ChatListGrouping`)。

- [ ] **Step 3: 实现**

创建 `ChatListGrouping.kt`:
```kotlin
package com.example.personal_studio.feature.chat.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 会话概览的日期分组(今天/更早)与行内相对时间。纯函数,泛型于"取 updatedAt"以便单测。 */
object ChatListGrouping {
    data class Group<T>(val label: String, val items: List<T>)

    fun <T> group(items: List<T>, now: Long, updatedAt: (T) -> Long): List<Group<T>> {
        if (items.isEmpty()) return emptyList()
        val startToday = startOfDay(now)
        val sorted = items.sortedByDescending(updatedAt)
        val today = sorted.filter { updatedAt(it) >= startToday }
        val earlier = sorted.filter { updatedAt(it) < startToday }
        return buildList {
            if (today.isNotEmpty()) add(Group("今天", today))
            if (earlier.isNotEmpty()) add(Group("更早", earlier))
        }
    }

    /** 今天→HH:mm;昨天→"昨天";一周内→"N天前";更早→MM-dd。 */
    fun rowTime(ts: Long, now: Long): String {
        val startToday = startOfDay(now)
        if (ts >= startToday) return SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
        val days = ((startToday - startOfDay(ts)) / 86_400_000L).toInt()
        return when (days) {
            1 -> "昨天"
            in 2..6 -> "${days}天前"
            else -> SimpleDateFormat("MM-dd", Locale.US).format(Date(ts))
        }
    }

    private fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.chat.ui.ChatListGroupingTest"`
Expected: PASS。(注:`rowTime today` 断言用长度=5 兜 HH:mm,避开具体时分的时区脆弱。)

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatListGrouping.kt app/src/test/java/com/example/personal_studio/feature/chat/ui/ChatListGroupingTest.kt
git commit -m "feat(chat-ui): 会话概览日期分组+相对时间纯函数"
```

---

### Task 2: 数据 — ChatSessionSummary 聚合(模型 + DAO + repo + Fake)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/model/ChatModels.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/dao/ChatSessionDao.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/ChatRepository.kt`
- Modify: `app/src/test/java/com/example/personal_studio/data/repository/FakeChatRepository.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/repository/ChatSessionSummaryTest.kt`

- [ ] **Step 1: 领域模型 + DAO 投影**

`ChatModels.kt` 末尾追加:
```kotlin
/** 会话概览富行:会话本体 + 消息数 + 末条预览。 */
data class ChatSessionSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val msgCount: Int,
    val lastSnippet: String?,
)
```

`ChatSessionDao.kt`:顶部加 `import androidx.room.Embedded`?——不用 Embedded,直接投影到一个 POJO。在文件内(interface 外、同包)新增投影类,并在 interface 加查询:
```kotlin
// 文件顶部 imports 保持;在 interface 上方加投影 data class:
data class ChatSessionSummaryRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val msgCount: Int,
    val lastSnippet: String?,
)
```
interface 内加:
```kotlin
    @Query(
        "SELECT s.id AS id, s.title AS title, s.updatedAt AS updatedAt, " +
        "(SELECT COUNT(*) FROM chat_messages WHERE sessionId = s.id) AS msgCount, " +
        "(SELECT contentMarkdown FROM chat_messages WHERE sessionId = s.id ORDER BY createdAt DESC LIMIT 1) AS lastSnippet " +
        "FROM chat_sessions s ORDER BY s.updatedAt DESC"
    )
    fun observeSessionSummaries(): Flow<List<ChatSessionSummaryRow>>
```
(纯只读查询,无 schema/版本变更。)

- [ ] **Step 2: repo 接口 + 实现 + 映射**

`ChatRepository.kt` interface 加(在 `observeSessions()` 下一行):
```kotlin
    fun observeSessionSummaries(): Flow<List<ChatSessionSummary>>
```
impl 加(在 `observeSessions()` override 下):
```kotlin
    override fun observeSessionSummaries(): Flow<List<ChatSessionSummary>> =
        sessionDao.observeSessionSummaries().map { rows -> rows.map { it.toDomain() } }
```
文件底部映射区加:
```kotlin
private fun com.example.personal_studio.data.local.db.dao.ChatSessionSummaryRow.toDomain() =
    ChatSessionSummary(
        id = id, title = title, updatedAt = updatedAt,
        msgCount = msgCount, lastSnippet = lastSnippet,
    )
```
顶部 import 加 `import com.example.personal_studio.domain.model.ChatSessionSummary`。

- [ ] **Step 3: FakeChatRepository 实现(供单测)**

`FakeChatRepository.kt`:加 import `com.example.personal_studio.domain.model.ChatSessionSummary` 与 `kotlinx.coroutines.flow.combine`。加 override(放 `observeSessions()` 下):
```kotlin
    override fun observeSessionSummaries(): Flow<List<ChatSessionSummary>> =
        combine(sessions, messagesBySession) { ss, msgs ->
            ss.sortedByDescending { it.updatedAt }.map { s ->
                val m = msgs[s.id].orEmpty()
                ChatSessionSummary(
                    id = s.id, title = s.title, updatedAt = s.updatedAt,
                    msgCount = m.size, lastSnippet = m.lastOrNull()?.contentMarkdown,
                )
            }
        }
```

- [ ] **Step 4: 写并跑 Fake 行为测试**

创建 `ChatSessionSummaryTest.kt`:
```kotlin
package com.example.personal_studio.data.repository

import app.cash.turbine.test
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionSummaryTest {
    @Test fun `summary reports message count and last snippet`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("数学作业")
        repo.appendMessage(id, MessageRole.USER, "第一条", null)
        repo.appendMessage(id, MessageRole.AI, "末条预览", null)
        repo.observeSessionSummaries().test {
            val row = awaitItem().first { it.id == id }
            assertEquals(2, row.msgCount)
            assertEquals("末条预览", row.lastSnippet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `empty session has zero count and null snippet`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("空会话")
        repo.observeSessionSummaries().test {
            val row = awaitItem().first { it.id == id }
            assertEquals(0, row.msgCount)
            assertEquals(null, row.lastSnippet)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.data.repository.ChatSessionSummaryTest"`
Expected: PASS。

- [ ] **Step 5: 编译全模块(确保 Room 投影查询编译通过)**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL(Room 注解处理器对 `observeSessionSummaries` 生成实现无报错)。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/model/ChatModels.kt app/src/main/java/com/example/personal_studio/data/local/db/dao/ChatSessionDao.kt app/src/main/java/com/example/personal_studio/data/repository/ChatRepository.kt app/src/test/java/com/example/personal_studio/data/repository/FakeChatRepository.kt app/src/test/java/com/example/personal_studio/data/repository/ChatSessionSummaryTest.kt
git commit -m "feat(chat): 会话摘要聚合查询(消息数+末条预览)"
```

---

### Task 3: ChatListViewModel — 摘要 + 重命名/删除

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/vm/ChatListViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/chat/vm/ChatListViewModelTest.kt`

- [ ] **Step 1: 改 VM**

`ChatListViewModel.kt` 整体替换为:
```kotlin
package com.example.personal_studio.feature.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.model.ChatSessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val sessions: List<ChatSessionSummary> = emptyList(),
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatListUiState> = repo.observeSessionSummaries()
        .map { ChatListUiState(sessions = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatListUiState())

    fun createNewSession(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val nextNumber = (repo.countSessions() + 1).toString().padStart(3, '0')
            val id = repo.createSession(initialTitle = "session #$nextNumber")
            onCreated(id)
        }
    }

    fun onRename(id: Long, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { repo.renameSession(id, t) }
    }

    fun onDelete(id: Long) = viewModelScope.launch { repo.deleteSession(id) }
}
```

- [ ] **Step 2: 改测试(uiState 现为 summary;补 rename/delete)**

`ChatListViewModelTest.kt` 里把第一个测试替换并新增两个:
```kotlin
    @Test fun `sessions flow maps summaries to ui state`() = runTest {
        val repo = FakeChatRepository()
        val s1 = repo.createSession("alpha")
        val vm = ChatListViewModel(repo)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.sessions.size)
            assertEquals(s1, state.sessions[0].id)
            assertEquals("alpha", state.sessions[0].title)
            assertEquals(0, state.sessions[0].msgCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `onRename updates title`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("old")
        val vm = ChatListViewModel(repo)
        vm.onRename(id, "新名字")
        assertEquals("新名字", repo.getSession(id)?.title)
    }

    @Test fun `onDelete removes session`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("x")
        val vm = ChatListViewModel(repo)
        vm.onDelete(id)
        assertEquals(0, repo.countSessions())
    }
```
(`createNewSession yields...` 测试不动。)
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.chat.vm.ChatListViewModelTest"`
Expected: PASS。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/vm/ChatListViewModel.kt app/src/test/java/com/example/personal_studio/feature/chat/vm/ChatListViewModelTest.kt
git commit -m "feat(chat): ChatListViewModel 用会话摘要 + 重命名/删除"
```

---

### Task 4: 共享终端控件(Dialog/Confirm/Input/BottomSheet)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/ui/components/TerminalDialog.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/components/TerminalBottomSheet.kt`

无单测(纯展示);以编译 + 后续真机验收。

- [ ] **Step 1: TerminalDialog.kt(含 Confirm/Input)**

创建 `TerminalDialog.kt`:
```kotlin
package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

/** 终端风弹窗骨架:0dp 直角 + Void/Deep 背景 + Rule 边框 + 可选 `── TITLE ──` 头。 */
@Composable
fun TerminalDialog(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Deep, RectangleShape)
                .border(1.dp, Rule, RectangleShape)
                .padding(16.dp),
        ) {
            if (title != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = FoamDim)) { append("── ") }
                        withStyle(SpanStyle(color = Phosphor)) { append(title) }
                        withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(top = 10.dp))
            }
            content()
        }
    }
}

/** 确认弹窗:`[取消] [confirmLabel]`。 */
@Composable
fun TerminalConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TerminalDialog(onDismiss = onDismiss, title = title) {
        Text(message, color = Foam, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("[取消]", color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Text("[$confirmLabel]", color = Carmine, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onConfirm() }.padding(8.dp))
        }
    }
}

/** 输入弹窗:终端风单行输入 + `[取消] [确认]`。 */
@Composable
fun TerminalInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    TerminalDialog(onDismiss = onDismiss, title = title) {
        Row(Modifier.fillMaxWidth()) {
            Text("> ", color = Phosphor, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("[取消]", color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Text("[确认]", color = Phosphor, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onConfirm(text) }.padding(8.dp))
        }
    }
}
```

- [ ] **Step 2: TerminalBottomSheet.kt**

创建 `TerminalBottomSheet.kt`:
```kotlin
package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/** 终端风底部弹层:Void + 0dp 直角 + 无拖柄 + 可选 `── header ──`。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalBottomSheet(onDismiss: () -> Unit, header: String? = null, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Void,
        shape = RectangleShape,
        dragHandle = {},
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (header != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = FoamDim)) { append("── ") }
                        withStyle(SpanStyle(color = Phosphor)) { append(header) }
                        withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(top = 14.dp))
            }
            content()
        }
    }
}
```

- [ ] **Step 3: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/ui/components/TerminalDialog.kt app/src/main/java/com/example/personal_studio/ui/components/TerminalBottomSheet.kt
git commit -m "feat(ui): 共享终端控件 Dialog/Confirm/Input/BottomSheet"
```

---

### Task 5: ChatListScreen 重做(会话管理概览)

**Files:**
- Modify (整体重写): `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatListScreen.kt`

依赖 Task 1(grouping)、Task 3(VM summary + onRename/onDelete)、Task 4(TerminalBottomSheet/Confirm/Input)。

- [ ] **Step 1: 整体重写 ChatListScreen.kt**

```kotlin
package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ChatSessionSummary
import com.example.personal_studio.feature.chat.vm.ChatListViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalInputDialog
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onOpenSession: (Long) -> Unit,
    vm: ChatListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val now = remember(state.sessions) { System.currentTimeMillis() }

    // 长按动作菜单 / 重命名 / 删除 的局部状态
    var menuFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ChatSessionSummary?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // 头部:ls 提示 + 右侧 [+ 新建]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Amber)) { append("user@study") }
                    withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                    withStyle(SpanStyle(color = Foam)) { append("ls sessions/") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "[+ 新建]", color = Cyan, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { vm.createNewSession(onCreated = onOpenSession) }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("total ${state.sessions.size}", style = MaterialTheme.typography.bodySmall, color = FoamMute)
        Spacer(Modifier.height(14.dp))

        if (state.sessions.isEmpty()) {
            EmptyState(onOpenNew = { vm.createNewSession(onCreated = onOpenSession) })
        } else {
            val groups = remember(state.sessions, now) {
                ChatListGrouping.group(state.sessions, now) { it.updatedAt }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                groups.forEach { g ->
                    item(key = "grp-${g.label}") { GroupHeader(g.label) }
                    items(g.items, key = { it.id }) { s ->
                        SessionRow(
                            s = s, now = now,
                            onClick = { onOpenSession(s.id) },
                            onLongClick = { menuFor = s },
                        )
                    }
                }
            }
        }
    }

    // 长按动作菜单
    menuFor?.let { s ->
        TerminalBottomSheet(onDismiss = { menuFor = null }, header = "会话「${s.title}」") {
            ActionLine("▸ 重命名", Foam) { renameFor = s; menuFor = null }
            ActionLine("▸ 删除", Carmine) { deleteFor = s; menuFor = null }
        }
    }
    // 重命名
    renameFor?.let { s ->
        TerminalInputDialog(
            title = "重命名会话", initial = s.title,
            onConfirm = { vm.onRename(s.id, it); renameFor = null },
            onDismiss = { renameFor = null },
        )
    }
    // 删除确认
    deleteFor?.let { s ->
        TerminalConfirmDialog(
            title = "删除会话", message = "删除会话「${s.title}」？此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = { vm.onDelete(s.id); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable private fun GroupHeader(label: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = FoamDim)) { append("── ") }
            withStyle(SpanStyle(color = FoamMute)) { append(label) }
            withStyle(SpanStyle(color = FoamDim)) { append(" ───────────────") }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SessionRow(
    s: ChatSessionSummary, now: Long, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("▸ ${s.title}", color = Foam, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.height(0.dp))
            Text(ChatListGrouping.rowTime(s.updatedAt, now), color = FoamMute,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("最近: ${s.lastSnippet?.replace("\n", " ") ?: "—"}", color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${s.msgCount} 条", color = FoamMute, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ActionLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

@Composable private fun EmptyState(onOpenNew: () -> Unit) {
    Column {
        Text("# 暂无会话", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("点 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[新建]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 开始第一个会话") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onOpenNew),
            )
            Spacer(Modifier.height(0.dp))
            BlinkingCursor()
        }
    }
}
```

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatListScreen.kt
git commit -m "feat(chat-ui): ChatList 重做为会话管理概览(分组/富行/重命名/删除)"
```

---

### Task 6: ChatDetailScreen 汉化 + 字形化

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt`

- [ ] **Step 1: 字符串汉化(就地改字面量)**

按 spec 字符串映射改这些字面量(精确 old→new):
- `"[+ archive session]"` → `"[+ 归档会话]"`
- `contentDescription = "back"` → `contentDescription = "返回"`
- `"[+ archive]"` → `"[+ 归档]"`
- `append("[err] ")` → `append("[错误] ")`
- `append("[dismiss]")` → `append("[关闭]")`
- `"[img] ${File(attachedPath).name}"` → `"[图片] ${File(attachedPath).name}"`
- `"type something here..."` → `"在这里输入…"`
- `"↵ send"` → `"↵ 发送"`
- `contentDescription = "attach"` → `contentDescription = "附件"`
- `?: "(untitled)"` → `?: "(未命名)"`
- `"# session: $sessionLabel\n# model: $model"` → `"# 会话: $sessionLabel\n# 模型: $model"`
- `return "── done ──"` → `return "── 完成 ──"`
- `return "── done · $formatted · $tokens tokens ──"` → `return "── 完成 · $formatted · $tokens tokens ──"`
(保留 `[x]`、`> `、`tokens`、模型 id。)

- [ ] **Step 2: 返回键字形化**

把顶栏 trailing 里的:
```kotlin
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
```
替换为:
```kotlin
                    Text(
                        "←",
                        color = FoamMute,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickableNoRipple(onBack).padding(8.dp),
                    )
```

- [ ] **Step 3: 附件键字形化**

把输入栏末尾的:
```kotlin
            IconButton(onClick = { showAttachmentSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "附件", tint = Cyan)
            }
```
替换为:
```kotlin
            Text(
                "[+]",
                color = Cyan,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickableNoRipple { showAttachmentSheet = true },
            )
```
并加 import `androidx.compose.foundation.layout.padding`(若未导入);删除现在未用的 import:`androidx.compose.material.icons.Icons`、`androidx.compose.material.icons.automirrored.filled.ArrowBack`、`androidx.compose.material.icons.filled.Add`、`androidx.compose.material3.Icon`、`androidx.compose.material3.IconButton`、`androidx.compose.ui.Alignment`(若 Row 的 verticalAlignment 仍用 Alignment.CenterVertically 则保留 Alignment——核查后再删)。

- [ ] **Step 4: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL(若报未用 import 仅为 warning 不阻断;报 Alignment 未定义则恢复其 import)。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt
git commit -m "feat(chat-ui): ChatDetail 汉化 + 返回/附件按钮字形化"
```

---

### Task 7: AttachmentSheet 汉化 + TerminalBottomSheet

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/AttachmentSheet.kt`

- [ ] **Step 1: 换 sheet + 汉化**

把 `ModalBottomSheet(... containerColor = Void) { Column(...) { Text(prompt) ... Option(...) } }` 整块改为用 `TerminalBottomSheet`:
- 删 `ModalBottomSheet`/`rememberModalBottomSheetState`/`ExperimentalMaterial3Api`/`Void` 相关 import,加 `import com.example.personal_studio.ui.components.TerminalBottomSheet`。
- sheet 体改为:
```kotlin
    TerminalBottomSheet(onDismiss = onDismiss, header = "attach") {
        Option(line = "--from-gallery     从相册选取", onClick = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        Option(line = "--from-camera      拍一张新照片(扫描)", onClick = { onRequestQuickCapture(); onDismiss() })
        Option(line = "--from-scans       从扫描库选取", onClick = { onRequestPickFromScans(); onDismiss() })
    }
```
(`attach --source` 那行提示并入 header `attach`;若想保留命令行提示,header 用 `"attach --source"` 亦可——本计划用 `attach`。`Option` 私有组件不动。删除原 `user@study:~$ attach --source` 那段 Text 与外层 Column。)

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/AttachmentSheet.kt
git commit -m "feat(chat-ui): AttachmentSheet 终端 sheet 化 + 汉化"
```

---

### Task 8: ImageCropOverlay 汉化 + 提示

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ImageCropOverlay.kt`

- [ ] **Step 1: 汉化 + 加提示行**

- `"could not decode image"` → `"无法解码图片"`
- `"[cancel]"` → `"[取消]"`
- `"[confirm ↵]"` → `"[确认 ↵]"`
- 在底部 action `Row` 之前(同为 `align(Alignment.BottomCenter)` 区域上方),加一行提示。最简做法:把底部 Row 包进一个 `Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Void))`,Column 第一行:
```kotlin
                Text(
                    "# 拖动四角裁剪",
                    color = FoamDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                )
```
然后原 Row 去掉自身的 `align(...).background(Void)`(由父 Column 提供),保留内边距。加 import `androidx.compose.foundation.layout.Column`、`com.example.personal_studio.ui.theme.FoamDim`。
(图像区不铺扫描线,保画质——本面不动图像绘制。)

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ImageCropOverlay.kt
git commit -m "feat(chat-ui): ImageCropOverlay 汉化 + 裁剪提示"
```

---

### Task 9: 全量验收 + 装真机 + 推分支

**Files:** 无(验收)

- [ ] **Step 1: 全量单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全绿。

- [ ] **Step 2: 装真机**

Run: `.\gradlew.bat :app:installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 3: 真机 DoD(手动,逐屏)**

1. 会话概览:分组(今天/更早)、富行(▸标题 + 相对时间 + 末条预览 + N条)、`[+ 新建]`、长按行→重命名/删除(终端弹窗)、空态。
2. 对话页:`←` 返回、`[+]` 附件、汉化(归档/错误/关闭/图片/在这里输入…/↵ 发送/# 会话:/# 模型:/── 完成 ──)、错误条、附图行。
3. 附件 sheet:终端化(无拖柄、`── attach ──`)、旗标保留、描述中文。
4. crop:`无法解码图片`/`[取消]`/`[确认 ↵]` + `# 拖动四角裁剪`,图像清晰无扫描线。

- [ ] **Step 4: 推分支(开 PR)**

```bash
git push -u origin feat/ui-refresh-chat
```

---

## Self-Review

**1. Spec coverage:**
- 翻译约定(平衡口味,保留 shell/技术词) → Task 6/7/8 字符串映射 ✓
- ChatList 重做(分组/相对时间/富行/管理操作) → Task 1+2+3+5 ✓
- 共享控件(Dialog/Confirm/Input/BottomSheet) → Task 4(+Task5/7 复用) ✓
- 数据(摘要聚合,无 schema 变更) → Task 2 ✓
- ChatDetail 最小改(汉化+字形) → Task 6 ✓
- AttachmentSheet(汉化+sheet) → Task 7 ✓
- ImageCropOverlay(汉化+提示+图像不铺扫描线) → Task 8 ✓
- 测试(聚合/分组/VM) → Task 1/2/3 ✓
- 布局精修原则 → 体现在 Task 5 重做 ✓
- spec 提的通用 `relativeTime` util 收敛为 Task 1 的 `ChatListGrouping.rowTime`(YAGNI:本期仅 chat 需要;教务屏 fmt 收敛留到有第二处需求时,不在本期强做)。

**2. Placeholder scan:** 无 TBD;每步给出完整代码或精确 old→new 字面量。

**3. Type consistency:** `ChatSessionSummary`(Task2 domain) 字段 id/title/updatedAt/msgCount/lastSnippet 在 DAO 投影 `ChatSessionSummaryRow`(同字段)、repo 映射、VM `ChatListUiState.sessions`、ChatListScreen `SessionRow` 一致;`ChatListGrouping.group/rowTime`(Task1)签名与 Task5 调用一致;`TerminalBottomSheet/TerminalConfirmDialog/TerminalInputDialog`(Task4)签名与 Task5/7 调用一致;`onRename(id,title)`/`onDelete(id)`(Task3)与 Task5 调用一致。
