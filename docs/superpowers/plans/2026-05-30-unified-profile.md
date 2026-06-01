# 统一 profile 个人中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增统一的 profile 个人中心页,聚合所有需登录的教务功能入口,作为底部 5-tab 的中央凸起 tab 与 App 默认启动页;并把散落在 Settings/Timeline 的教务入口迁出。

**Architecture:** 新建 `ProfileScreen` + `ProfileViewModel`(纯本地源 `combine` 聚合账号/GPA/作业数/考试数/轮询状态);抽 `LogoutUseCase` 复用登出副作用;`TerminalBottomBar` 改造支持中央凸起 tab;`MainScreen.tabs` 居中插入 profile;启动默认页 / tab 锚点 / 登录回跳目标由 `CHAT` 改为 `PROFILE`;`SettingsScreen`/`TimelineScreen` 移除教务入口。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (DAO Flow), DataStore, Navigation-Compose, kotlinx.coroutines (combine/StateFlow), JUnit4 + MockK + Turbine + kotlinx-coroutines-test.

---

## File Structure

**Create:**
- `app/src/main/java/com/example/personal_studio/domain/auth/LogoutUseCase.kt` — 封装登出副作用(清凭据 + 取消两 scheduler + 关两 sync prefs)。
- `app/src/test/java/com/example/personal_studio/domain/auth/LogoutUseCaseTest.kt`
- `app/src/main/java/com/example/personal_studio/feature/profile/ProfileViewModel.kt` — `ProfileUiState` + 状态聚合 + `onLogout`。
- `app/src/main/java/com/example/personal_studio/feature/profile/ui/ProfileScreen.kt` — 账号卡 + 2×2 核心网格 + 后台提醒列表。
- `app/src/test/java/com/example/personal_studio/feature/profile/ProfileViewModelTest.kt`

**Modify:**
- `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt` — 加 `PROFILE`。
- `app/src/main/java/com/example/personal_studio/ui/components/TerminalBottomBar.kt` — `TerminalTab.prominent` + 中央凸起渲染。
- `app/src/main/java/com/example/personal_studio/ui/MainScreen.kt` — tabs 居中插入 profile;`onTabClick` 锚点 `CHAT→PROFILE`。
- `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt` — 加 PROFILE composable;LOGIN `onSucceeded`/`onSkipped` 的 `CHAT→PROFILE`;TIMELINE block 去掉 `onOpenAssignments`/`onOpenExams`。
- `app/src/main/java/com/example/personal_studio/feature/auth/RootViewModel.kt` — `CHAT→PROFILE`。
- `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt` — 移除教务行,保留 SEMESTER/TIMETABLE/NOTIFICATIONS/COURSES。
- `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt` — 删 `onLogout`/`loggedInUsername` 及仅其用的注入。
- `app/src/main/java/com/example/personal_studio/feature/timeline/ui/TimelineScreen.kt` — 移除作业/考试按钮 + 两个参数。

**Conventions（全程遵守）:** 颜色用 `ui.theme`(Void/Deep/Rule/Dim/Foam/FoamMute/FoamDim/Phosphor/Amber/Cyan);终端风格无圆角;CRT 背景 `scanLines()`/`vignette()`;ViewModel 测试用 `Dispatchers.setMain(StandardTestDispatcher())` + collect-in-launch + `advanceUntilIdle()`;`nowProvider: () -> Long = System::currentTimeMillis` 作默认 lambda 注入。提交 trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

### Task 1: NavRoutes 加 PROFILE 常量

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`

- [ ] **Step 1: 加常量**

在 `// Bottom-nav tabs` 区,`TIMELINE` 之后加一行:
```kotlin
    const val TIMELINE = "timeline"
    const val PROFILE = "profile"
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt
git commit -m "p10: NavRoutes 加 PROFILE 路由常量"
```

---

### Task 2: LogoutUseCase(抽登出副作用)— TDD

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/auth/LogoutUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/auth/LogoutUseCaseTest.kt`

背景:现 `SettingsViewModel.onLogout()` = `credPrefs.clear()`(同步)+ `gradesPollScheduler.cancel()` + `ddlPollScheduler.cancel()`(同步)+ `gradesSyncPrefs.setEnabled(false)` + `ddlSyncPrefs.setEnabled(false)`(suspend)。抽成一个 suspend use case。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LogoutUseCaseTest {
    @Test fun `logout clears creds, cancels schedulers, disables sync`() = runTest {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true)
        val gradesSyncPrefs = mockk<GradesSyncPrefs>(relaxed = true)
        val ddlSyncPrefs = mockk<DdlSyncPrefs>(relaxed = true)
        val gradesScheduler = mockk<GradesPollScheduler>(relaxed = true)
        val ddlScheduler = mockk<DdlPollScheduler>(relaxed = true)
        val useCase = LogoutUseCase(credPrefs, gradesSyncPrefs, ddlSyncPrefs, gradesScheduler, ddlScheduler)

        useCase.invoke()

        verify { credPrefs.clear() }
        verify { gradesScheduler.cancel() }
        verify { ddlScheduler.cancel() }
        coVerify { gradesSyncPrefs.setEnabled(false) }
        coVerify { ddlSyncPrefs.setEnabled(false) }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*.LogoutUseCaseTest"`
Expected: FAIL — `Unresolved reference: LogoutUseCase`。

- [ ] **Step 3: 实现**

```kotlin
package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import javax.inject.Inject

/**
 * 登出:清除已存凭据 + 取消两个后台轮询 + 关闭两个自动同步开关。
 * 与原 SettingsViewModel.onLogout 行为一致,抽出供 profile / settings 复用。
 */
class LogoutUseCase @Inject constructor(
    private val credPrefs: ImportCredentialPrefs,
    private val gradesSyncPrefs: GradesSyncPrefs,
    private val ddlSyncPrefs: DdlSyncPrefs,
    private val gradesPollScheduler: GradesPollScheduler,
    private val ddlPollScheduler: DdlPollScheduler,
) {
    suspend operator fun invoke() {
        credPrefs.clear()
        gradesPollScheduler.cancel()
        ddlPollScheduler.cancel()
        gradesSyncPrefs.setEnabled(false)
        ddlSyncPrefs.setEnabled(false)
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*.LogoutUseCaseTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/auth/LogoutUseCase.kt app/src/test/java/com/example/personal_studio/domain/auth/LogoutUseCaseTest.kt
git commit -m "p10: LogoutUseCase 抽登出副作用(清凭据+取消轮询+关同步)+ 单测"
```

---

### Task 3: ProfileViewModel(状态聚合)— TDD

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/profile/ProfileViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/profile/ProfileViewModelTest.kt`

数据源(全本地):`ImportCredentialPrefs.observeAll(): StateFlow<SavedCredentials?>`、`GradesDao.observeAll()`+`observeRanks()` → `ComputeGpaUseCase.invoke(entries, ranks): GradeBook`(`overallGpa: Double`, `isEmpty`)、`TimelineDao.observeLexueDdls()`/`observeImportedExams(): Flow<List<TimelineItemEntity>>`(DDL 截止 = `startAt`,无 `dueAt`)、`GradesSyncPrefs.observe: Flow<GradesSyncState(enabled, intervalHours, lastSyncAt, …)>`、`DdlSyncPrefs.observe: Flow<DdlSyncState(enabled, …)>`。7 源用嵌套 `combine` 收成 5 参。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.profile

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_000_000_000_000L

    private fun ddl(id: Long, start: Long, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = "D$id", startAt = start, endAt = null,
        isDone = done, sourceType = TimelineSource.IMPORTED_LEXUE, sourceExternalId = "d$id",
        createdAt = 1L, updatedAt = 1L,
    )
    private fun exam(id: Long, start: Long) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "E$id", startAt = start, endAt = start + 7200_000L,
        isDone = false, sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = "e$id",
        createdAt = 1L, updatedAt = 1L,
    )

    private fun vm(
        creds: SavedCredentials? = null,
        book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
        ddls: List<TimelineItemEntity> = emptyList(),
        exams: List<TimelineItemEntity> = emptyList(),
        grades: GradesSyncState = GradesSyncState(false, 6, null, emptySet()),
        ddlSync: DdlSyncState = DdlSyncState(false, 12, null, emptySet(), null),
        logout: LogoutUseCase = mockk(relaxed = true),
    ): ProfileViewModel {
        val gradesDao = mockk<GradesDao>(relaxed = true) {
            every { observeAll() } returns flowOf(emptyList())
            every { observeRanks() } returns flowOf(emptyList())
        }
        val timelineDao = mockk<TimelineDao>(relaxed = true) {
            every { observeLexueDdls() } returns flowOf(ddls)
            every { observeImportedExams() } returns flowOf(exams)
        }
        val computeGpa = mockk<ComputeGpaUseCase> { every { invoke(any(), any()) } returns book }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds)
        }
        val gradesSyncPrefs = mockk<GradesSyncPrefs>(relaxed = true) { every { observe } returns flowOf(grades) }
        val ddlSyncPrefs = mockk<DdlSyncPrefs>(relaxed = true) { every { observe } returns flowOf(ddlSync) }
        return ProfileViewModel(
            credPrefs, gradesDao, computeGpa, timelineDao, gradesSyncPrefs, ddlSyncPrefs, logout,
            nowProvider = { now },
        )
    }

    private fun collect(vm: ProfileViewModel) {
        // collect in background so combine starts
    }

    @Test fun `logged out state`() = runTest {
        val vm = vm(creds = null)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.loggedIn)
        assertNull(vm.uiState.value.username)
        job.cancel()
    }

    @Test fun `logged in maps username and network mode`() = runTest {
        val vm = vm(creds = SavedCredentials("1120243640", "pw", NetworkMode.LOCAL))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loggedIn)
        assertEquals("1120243640", vm.uiState.value.username)
        assertEquals(NetworkMode.LOCAL, vm.uiState.value.networkMode)
        job.cancel()
    }

    @Test fun `ddl count is unfinished and future only`() = runTest {
        val vm = vm(ddls = listOf(
            ddl(1, now + 3600_000L, false),   // upcoming
            ddl(2, now - 3600_000L, false),   // overdue -> excluded
            ddl(3, now + 3600_000L, true),    // done -> excluded
        ))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.ddlCount)
        job.cancel()
    }

    @Test fun `exam count is upcoming only`() = runTest {
        val vm = vm(exams = listOf(
            exam(1, now + 2 * 3600_000L),   // upcoming
            exam(2, now - 5 * 3600_000L),   // past (end < now) -> excluded
        ))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.examCount)
        job.cancel()
    }

    @Test fun `gpa null when no grades, value when present`() = runTest {
        val empty = vm(book = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null))
        val j1 = launch { empty.uiState.collect {} }; advanceUntilIdle()
        assertNull(empty.uiState.value.gpa); j1.cancel()

        val withGpa = vm(book = GradeBook(listOf(mockk(relaxed = true)), 3.82, 100.0, null, overallRank = null))
        val j2 = launch { withGpa.uiState.collect {} }; advanceUntilIdle()
        assertEquals(3.82, withGpa.uiState.value.gpa!!, 1e-9); j2.cancel()
    }

    @Test fun `poll states mapped`() = runTest {
        val vm = vm(
            grades = GradesSyncState(true, 6, null, emptySet()),
            ddlSync = DdlSyncState(true, 12, null, emptySet(), null),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.gradesPollEnabled)
        assertEquals(6, vm.uiState.value.gradesPollInterval)
        assertEquals(true, vm.uiState.value.ddlPollEnabled)
        job.cancel()
    }

    @Test fun `onLogout delegates to use case`() = runTest {
        val logout = mockk<LogoutUseCase>(relaxed = true)
        val vm = vm(logout = logout)
        val job = launch { vm.uiState.collect {} }
        vm.onLogout(); advanceUntilIdle()
        coVerify { logout.invoke() }
        job.cancel()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ProfileViewModelTest"`
Expected: FAIL — `Unresolved reference: ProfileViewModel`。

- [ ] **Step 3: 实现**

```kotlin
package com.example.personal_studio.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loggedIn: Boolean = false,
    val username: String? = null,
    val networkMode: NetworkMode? = null,
    val gpa: Double? = null,            // 总 GPA;无成绩为 null
    val ddlCount: Int = 0,              // 未完成且未过期的作业数
    val examCount: Int = 0,             // 即将的考试数
    val gradesPollEnabled: Boolean = false,
    val gradesPollInterval: Int = 6,
    val ddlPollEnabled: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val credPrefs: ImportCredentialPrefs,
    gradesDao: GradesDao,
    computeGpa: ComputeGpaUseCase,
    timelineDao: TimelineDao,
    gradesSyncPrefs: GradesSyncPrefs,
    ddlSyncPrefs: DdlSyncPrefs,
    private val logout: LogoutUseCase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val bookFlow = combine(gradesDao.observeAll(), gradesDao.observeRanks()) { entries, ranks ->
        computeGpa.invoke(entries, ranks)
    }
    private val pollFlow = combine(gradesSyncPrefs.observe, ddlSyncPrefs.observe) { g, d -> g to d }

    val uiState: StateFlow<ProfileUiState> = combine(
        credPrefs.observeAll(),
        bookFlow,
        timelineDao.observeLexueDdls(),
        timelineDao.observeImportedExams(),
        pollFlow,
    ) { creds, book, ddls, exams, (grades, ddlSync) ->
        val now = nowProvider()
        ProfileUiState(
            loggedIn = creds != null,
            username = creds?.username,
            networkMode = creds?.lastMode,
            gpa = if (book.isEmpty) null else book.overallGpa,
            ddlCount = ddls.count { !it.isDone && it.startAt >= now },
            examCount = exams.count { (it.endAt ?: it.startAt) >= now },
            gradesPollEnabled = grades.enabled,
            gradesPollInterval = grades.intervalHours,
            ddlPollEnabled = ddlSync.enabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun onLogout() = viewModelScope.launch { logout.invoke() }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ProfileViewModelTest"`
Expected: PASS（7 个测试）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/profile/ProfileViewModel.kt app/src/test/java/com/example/personal_studio/feature/profile/ProfileViewModelTest.kt
git commit -m "p10: ProfileViewModel 聚合账号/GPA/作业数/考试数/轮询状态 + 7 单测"
```

---

### Task 4: ProfileScreen(账号卡 + 核心网格 + 轮询列表）

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/profile/ui/ProfileScreen.kt`

UI-only,无单测(编译 + 真机验证)。tab 屏:不画自己的 topbar(由 MainShell 提供),内容从账号卡起。回调由 AppNavHost(Task 7)接线。

- [ ] **Step 1: 实现**

```kotlin
package com.example.personal_studio.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.profile.ProfileUiState
import com.example.personal_studio.feature.profile.ProfileViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

@Composable
fun ProfileScreen(
    onOpenImport: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenGradesPoll: () -> Unit,
    onOpenDdlPoll: () -> Unit,
    onLogin: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        AccountCard(st, onLogin = onLogin, onLogout = vm::onLogout)

        // 核心功能 2×2
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(Modifier.weight(1f), "课表导入", null, !st.loggedIn, onOpenImport)
            GridCard(Modifier.weight(1f), "成绩", st.gpa?.let { "GPA %.2f".format(it) }, !st.loggedIn, onOpenGrades)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(Modifier.weight(1f), "作业 DDL", if (st.ddlCount > 0) "${st.ddlCount} 待办" else null, !st.loggedIn, onOpenAssignments)
            GridCard(Modifier.weight(1f), "考试安排", if (st.examCount > 0) "${st.examCount} 即将" else null, !st.loggedIn, onOpenExams)
        }

        Spacer(Modifier.height(2.dp))
        Text("// 后台提醒", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        PollRow("出分提醒", if (st.gradesPollEnabled) "已开 ${st.gradesPollInterval}h" else "已关", onOpenGradesPoll)
        PollRow("作业自动同步", if (st.ddlPollEnabled) "已开" else "已关", onOpenDdlPoll)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountCard(st: ProfileUiState, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .let { if (!st.loggedIn) it.clickable { onLogin() } else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (st.loggedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◆ ", color = Phosphor, style = MaterialTheme.typography.headlineSmall)
                Text(st.username ?: "", color = Foam, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onLogout) { Text("退出登录", color = FoamMute, style = MaterialTheme.typography.labelMedium) }
            }
            Text(
                "已登录 · " + when (st.networkMode) { NetworkMode.LOCAL -> "校园网"; null -> "—"; else -> "外网" },
                color = FoamMute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 22.dp),
            )
        } else {
            Text("◆ 未登录", color = FoamMute, style = MaterialTheme.typography.headlineSmall)
            Text("点此登录 — 成绩 / 课表 / 作业 / 考试免再输密码", color = FoamDim,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun GridCard(modifier: Modifier, name: String, status: String?, dimmed: Boolean, onClick: () -> Unit) {
    Column(
        modifier.border(1.dp, Rule).background(Deep).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text("▦ $name", color = if (dimmed) FoamMute else Foam, style = MaterialTheme.typography.headlineSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(status ?: " ", color = Cyan, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun PollRow(name: String, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▸ $name", color = Foam, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(status, color = if (status.startsWith("已开")) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium)
        Text("  ›", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
    }
}
```

注:`onLogin` 与各 `onOpen*` 在未登录时仍由 AppNavHost 接成「需登录则跳 LOGIN(next)」(Task 7);GridCard 的 `dimmed` 仅做视觉弱化,点击照常触发回调。CRT 背景由 MainShell 提供(`.scanLines().vignette()`),本屏不重复加。

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（ProfileScreen 暂未被引用,仅验证可编译；`"GPA %.2f".format(it)` 用 `kotlin.text` 自带扩展)。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/profile/ui/ProfileScreen.kt
git commit -m "p10: ProfileScreen 账号卡 + 2×2 核心网格 + 后台提醒列表"
```

---

### Task 5: TerminalBottomBar 中央凸起改造

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/components/TerminalBottomBar.kt`

给 `TerminalTab` 加 `prominent` 标志;`prominent` 的 tab 渲染为上移、描边的方块。

- [ ] **Step 1: TerminalTab 加字段**

```kotlin
data class TerminalTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val prominent: Boolean = false,
)
```

- [ ] **Step 2: 改 Row 内渲染**

把 `tabs.forEach { tab -> ... }` 内的 `Box` 渲染替换为按 `prominent` 分流。完整替换 `tabs.forEach` 块为:

```kotlin
        tabs.forEach { tab ->
            val selected = tab.route == selectedRoute
            if (tab.prominent) {
                // 中央凸起方块:上移浮起,描边方块,像实体按钮
                Box(
                    modifier = Modifier
                        .clickable { onTabClick(tab) }
                        .offset(y = (-12).dp)
                        .size(46.dp)
                        .border(1.dp, Phosphor)
                        .background(if (selected) Phosphor.copy(alpha = 0.16f) else Void),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = Phosphor,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(text = tab.label, style = MaterialTheme.typography.labelSmall, color = Phosphor)
                    }
                }
            } else {
                val tint = if (selected) Phosphor else FoamMute
                Box(
                    modifier = Modifier
                        .clickable { onTabClick(tab) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(text = tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(if (selected) 14.dp else 0.dp)
                                .height(2.dp)
                                .drawBehind { if (selected) drawRect(Phosphor) },
                        )
                    }
                }
            }
        }
```

- [ ] **Step 3: 加 imports**

在文件 import 区补:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import com.example.personal_studio.ui.theme.Void
```

- [ ] **Step 4: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/components/TerminalBottomBar.kt
git commit -m "p10: TerminalBottomBar 支持 prominent 中央凸起方块 tab"
```

---

### Task 6: MainScreen tabs 居中插入 profile + 锚点改 PROFILE

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/MainScreen.kt`

- [ ] **Step 1: tabs 居中插入 profile**

`tabs` val 替换为(profile 居中,`prominent = true`,图标 `Icons.Filled.Person`):
```kotlin
private val tabs = listOf(
    TerminalTab(NavRoutes.CHAT, "chat", Icons.Filled.Terminal),
    TerminalTab(NavRoutes.SCANNER, "scan", Icons.Filled.CameraAlt),
    TerminalTab(NavRoutes.PROFILE, "我", Icons.Filled.Person, prominent = true),
    TerminalTab(NavRoutes.KNOWLEDGE, "kb", Icons.AutoMirrored.Filled.MenuBook),
    TerminalTab(NavRoutes.TIMELINE, "day", Icons.Filled.CalendarMonth),
)
```

- [ ] **Step 2: 加 Person import**

在 icon import 区补:
```kotlin
import androidx.compose.material.icons.filled.Person
```

- [ ] **Step 3: onTabClick 锚点 CHAT→PROFILE**

`bottomBar` 的 `onTabClick` 中,`popUpTo(NavRoutes.CHAT)` 改为 `popUpTo(NavRoutes.PROFILE)`:
```kotlin
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(NavRoutes.PROFILE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
```

- [ ] **Step 4: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（PROFILE composable 尚未注册,运行时点中央 tab 会崩 → Task 7 补;此 task 只验证编译）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/MainScreen.kt
git commit -m "p10: MainScreen 5-tab 居中插入 profile(prominent)+ tab 锚点改 PROFILE"
```

---

### Task 7: AppNavHost 注册 PROFILE + 登录回跳目标改 PROFILE + 拆 Timeline lambda

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`

- [ ] **Step 1: 注册 PROFILE composable**

在 TIMELINE 的 `composable(NavRoutes.TIMELINE){...}` 块之后,新增:
```kotlin
        composable(NavRoutes.PROFILE) {
            com.example.personal_studio.feature.profile.ui.ProfileScreen(
                onOpenImport = { navController.navigate(NavRoutes.IMPORT_WIZARD) },
                onOpenGrades = { navController.navigate(NavRoutes.GRADES) },
                onOpenAssignments = { navController.navigate(NavRoutes.ASSIGNMENTS) },
                onOpenExams = { navController.navigate(NavRoutes.EXAMS) },
                onOpenGradesPoll = { navController.navigate(NavRoutes.SETTINGS_GRADES_POLL) },
                onOpenDdlPoll = { navController.navigate(NavRoutes.SETTINGS_DDL_POLL) },
                onLogin = { navController.navigate(NavRoutes.login(null)) },
            )
        }
```
说明:profile 的功能卡直接导航到各功能 route;各功能屏自身已带 `onNeedLogin` 守卫(未登录会自动跳 LOGIN(next));账号卡未登录点击走 `onLogin`(普通登录,登完回 profile)。

- [ ] **Step 2: LOGIN 块的回跳目标 CHAT→PROFILE**

LOGIN composable 内,`onSucceeded`(else 分支)与 `onSkipped` 的 `NavRoutes.CHAT` 改为 `NavRoutes.PROFILE`:
```kotlin
                onSucceeded = {
                    if (next != null) navController.navigate(next) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    } else navController.navigate(NavRoutes.PROFILE) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSkipped = {
                    navController.navigate(NavRoutes.PROFILE) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
```

- [ ] **Step 3: TIMELINE 块移除作业/考试 lambda**

`composable(NavRoutes.TIMELINE){ TimelineScreen(...) }` 中删除 `onOpenAssignments` 与 `onOpenExams` 两行(TimelineScreen 在 Task 10 同步删参):
```kotlin
        composable(NavRoutes.TIMELINE) {
            com.example.personal_studio.feature.timeline.ui.TimelineScreen(
                onAddTask = { navController.navigate(NavRoutes.TIMELINE_ADD_TASK) },
                onAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) },
                onOpenDetail = { id -> navController.navigate(NavRoutes.timelineDetail(id)) },
                onOpenWeekGrid = { navController.navigate(NavRoutes.TIMELINE_WEEK_GRID) },
            )
        }
```

- [ ] **Step 4: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: 失败 — TimelineScreen 仍要求 `onOpenAssignments`/`onOpenExams`(它们有默认值 `= {}`,实际不会失败)。预期 BUILD SUCCESSFUL(因默认值);Task 10 再删 TimelineScreen 的按钮与参数。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "p10: AppNavHost 注册 PROFILE + 登录回跳改 PROFILE + TIMELINE 去作业考试 lambda"
```

---

### Task 8: RootViewModel 默认启动改 PROFILE

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/auth/RootViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/auth/RootViewModelTest.kt`（若已存在则更新,否则新建）

- [ ] **Step 1: 写/改测试**

新建或更新 `RootViewModelTest.kt`:
```kotlin
package com.example.personal_studio.feature.auth

import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.ui.navigation.NavRoutes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RootViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `seen login goes to PROFILE`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(true) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.PROFILE, vm.startDestination.value)
        job.cancel()
    }

    @Test fun `unseen login goes to LOGIN`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(false) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.LOGIN, vm.startDestination.value)
        job.cancel()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RootViewModelTest"`
Expected: FAIL — `seen login goes to PROFILE` 断言失败(当前返回 CHAT)。

- [ ] **Step 3: 改实现**

`RootViewModel` 的 map 中 `NavRoutes.CHAT` 改 `NavRoutes.PROFILE`:
```kotlin
    val startDestination: StateFlow<String?> =
        loginPrefs.observe
            .map { seen -> if (seen) NavRoutes.PROFILE else NavRoutes.LOGIN }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RootViewModelTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/auth/RootViewModel.kt app/src/test/java/com/example/personal_studio/feature/auth/RootViewModelTest.kt
git commit -m "p10: 默认启动页 CHAT→PROFILE + RootViewModel 单测"
```

---

### Task 9: Settings 瘦身(移除教务行 + 抽走登出）

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt`

- [ ] **Step 1: SettingsScreen 删教务行**

把 "## timeline" section 内的内容替换为仅保留非教务配置行(删 BIT 账号/登出、IMPORT、GRADES、GRADES_POLL、DDL_POLL、EXAMS):
```kotlin
            // ── Section: timeline ──────────────────────────────
            SectionHeader("## timeline")

            NavigableRow(
                key = "SEMESTER",
                value = "学期起始日 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_SEMESTER) },
            )
            NavigableRow(
                key = "TIMETABLE",
                value = "13 节作息表 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_TIMETABLE) },
            )
            NavigableRow(
                key = "NOTIFICATIONS",
                value = "通知开关 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_NOTIF) },
            )
            NavigableRow(
                key = "COURSES",
                value = "课程列表 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.TIMELINE_COURSE_LIST) },
            )
```
若 `onNavigateToImport` 参数在删 IMPORT 行后不再被引用,删除 `SettingsScreen` 的 `onNavigateToImport: () -> Unit` 形参,并删 `AppNavHost` 的 SETTINGS 块中对应实参 `onNavigateToImport = { navController.navigate(NavRoutes.IMPORT_WIZARD) }`。

- [ ] **Step 2: SettingsViewModel 删登出 + 账号 + 仅其用的注入**

删 `onLogout()` 方法;删 `loggedInUsername` 的 init 派生(`credPrefs.observeAll().onEach{...}` 块)与 `SettingsUiState.loggedInUsername` 字段;删构造里仅 onLogout/账号 用到的注入 `credPrefs`/`gradesSyncPrefs`/`ddlSyncPrefs`/`gradesPollScheduler`/`ddlPollScheduler`。改后构造为:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val llm: LLMProvider,
) : ViewModel() {
```
（若 `credPrefs` 等在该 VM 其它地方仍被使用,则保留被用到的那几个;以编译器为准 —— 删到「无未使用注入、无悬空引用」。）

- [ ] **Step 3: 更新 SettingsViewModel 测试**

若 `SettingsViewModelTest` 存在且引用了 `onLogout`/`loggedInUsername`/被删注入,删除相关断言与 mock。运行后再修。

- [ ] **Step 4: 编译 + 测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全绿。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/
git commit -m "p10: Settings 瘦身 — 移除教务入口(登录登出/IMPORT/GRADES/EXAMS/两轮询),保留 timeline 配置"
```

---

### Task 10: TimelineScreen 移除作业/考试入口

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/timeline/ui/TimelineScreen.kt`

- [ ] **Step 1: 删 header 两个按钮**

删除 header Row 中这两行:
```kotlin
                TextButton(onClick = onOpenAssignments) { Text("作业 ↗") }
                TextButton(onClick = onOpenExams) { Text("考试 ↗") }
```

- [ ] **Step 2: 删参数**

删 `TimelineScreen` 形参中的:
```kotlin
    onOpenAssignments: () -> Unit = {},
    onOpenExams: () -> Unit = {},
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（AppNavHost 已在 Task 7 不传这两个实参）。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/timeline/ui/TimelineScreen.kt
git commit -m "p10: TimelineScreen 移除顶部作业/考试入口(统一到 profile)"
```

---

### Task 11: 全量验证 + 真机 DoD

**Files:** 无新增。

- [ ] **Step 1: 全量编译 + 单测**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿(含新增 LogoutUseCaseTest、ProfileViewModelTest 7、RootViewModelTest 2)。

- [ ] **Step 2: 装机**

Run: `./gradlew :app:installDebug`
Expected: Installed。

- [ ] **Step 3: 真机 DoD（人工核对）**

- 冷启动直接进 profile(底栏中央「我」凸起方块高亮);非首次不再落 chat。
- 底栏 5 个:`chat · scan · [我] · kb · day`,中央凸起、点击切换正常,其余 4 个切换正常。
- profile:已登录显示学号 + 网络模式 + 退出登录;成绩卡显示 GPA、作业卡显示 N 待办、考试卡显示 N 即将;后台提醒两行状态正确。
- 点成绩/作业/考试/课表 → 进各屏;退出登录后再点 → 触发登录守卫回跳。
- Settings(齿轮)只剩 timeline 配置 + LLM/API/诊断,无教务入口、无登出行。
- Timeline 顶部无「作业 ↗ / 考试 ↗」。
- 通知深链(若可触发)仍直达 grades/exams/assignments,不经 profile。

- [ ] **Step 4: 收尾**

真机 DoD 全过后,用 `superpowers:finishing-a-development-branch` 收尾(分支 `feature/p10-profile` → PR)。

---

## Self-Review

**Spec coverage:** ① 底栏(Task 5+6)✓;② 布局(Task 4)✓;③ 状态聚合(Task 3)✓;④ 导航/启动(Task 1/6/7/8)✓;⑤ 入口迁移 + LogoutUseCase(Task 2/9/10)✓;⑥ 未登录态(Task 4 账号卡 + Task 7 onLogin/守卫)✓;⑦ 测试(Task 2/3/8 + Task 9 回归)✓;⑧ GPA 本地读(Task 3,`overallGpa`/`isEmpty`)✓。deeplink 保留(未改 grades/exams/assignments 块)✓。

**Placeholder scan:** 无 TBD/TODO;每个改动 step 含完整代码或精确删除目标。Task 9 Step 2 用「以编译器为准删到无未使用注入」是有意的(SettingsViewModel 其余字段使用情况需以实际为准),非占位。

**Type consistency:** `LogoutUseCase.invoke()`(suspend operator,无参)在 Task 2 定义、Task 3 `logout.invoke()` 调用一致;`ProfileUiState` 字段(loggedIn/username/networkMode/gpa/ddlCount/examCount/gradesPollEnabled/gradesPollInterval/ddlPollEnabled)在 Task 3 定义、Task 4 ProfileScreen 消费一致;`GradeBook.overallGpa`/`isEmpty`、`GradesSyncState.enabled/intervalHours`、`DdlSyncState.enabled`、`TimelineItemEntity.startAt/endAt/isDone`、`ImportCredentialPrefs.observeAll()`(StateFlow)/`clear()`、调度器 `cancel()`、`TerminalTab.prominent`、`NavRoutes.PROFILE` 均跨 task 一致。DDL 用 `startAt`(无 dueAt)✓。
