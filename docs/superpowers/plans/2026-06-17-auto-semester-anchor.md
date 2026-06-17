# 首启空课表自动定锚 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 首次启动空课表不再强制手设学期起始日,而是引导一键导入(自动定锚)+ 手动兜底;并修跨学期旧锚点(导入按所选学期刷新)。

**Architecture:** 三处改动。(1) `ResolveSemesterAnchorUseCase` 由"已设则沿用"改为总是用 BIT 周1日期覆盖(导入按所选学期刷新)。(2) `CourseWeekGridViewModel` 由 `init` 一次性读取学期起始日改为 `combine(startDate, periods)` 响应式,导入/手设后课表即刻刷新,并加 `onSemesterStartPicked`。(3) `CourseWeekGridScreen` 的 `needsSemesterStart` 死胡同提示换成 `SemesterSetupCta`(一键导入 + 手动兜底 SemesterStartModal)。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, JUnit4 + mockk。

**约定（项目级）：** 提交信息结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。回复中文。Windows + Git Bash,gradle 用 `./gradlew`。真机装 `./gradlew :app:installDebug`。

---

### Task 1: 导入总是按 BIT 周1刷新学期锚点(TDD)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitimport/ResolveSemesterAnchorUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitimport/ResolveSemesterAnchorUseCaseTest.kt:16-29`

- [ ] **Step 1: 改 test 1 为"已设也覆盖"(先让测试反映新行为)**

把 `ResolveSemesterAnchorUseCaseTest.kt` 的第一个测试(第 16-29 行 `respects existing startDate when already set`)整体替换为:

```kotlin
    @Test fun `overwrites existing startDate with BIT week-1 Monday`() = runBlocking {
        // 跨学期场景:prefs 里残留上学期的旧锚点,导入应按 BIT 周1覆盖刷新。
        val prefs = mockk<SemesterPreferences>(relaxed = true) {
            coEvery { startDate } returns flowOf(LocalDate.of(2025, 9, 1))
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        val anchor = useCase.invoke(
            week1Days = listOf(WeekDateDto(weekday = 1, date = "2026-02-23")),
        )

        assertEquals(LocalDate.of(2026, 2, 23), anchor)
        coVerify(exactly = 1) { prefs.setStartDate(LocalDate.of(2026, 2, 23)) }
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitimport.ResolveSemesterAnchorUseCaseTest"`
Expected: FAIL — 旧实现遇到已设的 startDate 会直接返回旧值、不调用 setStartDate,断言 `2026-02-23` 与 `coVerify(exactly=1)` 不满足。

- [ ] **Step 3: 改用例为总是覆盖**

把 `ResolveSemesterAnchorUseCase.kt` 整文件替换为:

```kotlin
package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves & persists the semester anchor (week-1 Monday) from BIT's cxzkbrq.do
 * response. The caller requests `"ZC":"1"` for the picked term, so the response
 * lists week 1's seven days with dates; the `weekday == 1` (Monday) entry is the
 * semester's first day.
 *
 * Always overwrites [SemesterPreferences.startDate] with the BIT-derived date so
 * a re-import refreshes the anchor to the picked term (fixes stale anchor across
 * semesters). Manual override, if any, is intentionally superseded by import.
 */
class ResolveSemesterAnchorUseCase @Inject constructor(
    private val semesterPrefs: SemesterPreferences,
) {
    suspend operator fun invoke(week1Days: List<WeekDateDto>): LocalDate {
        require(week1Days.isNotEmpty()) { "week-1 day list must be non-empty" }

        val monday = week1Days.firstOrNull { it.weekday == 1 }
            ?: throw IllegalStateException("cxzkbrq response had no Monday (XQ=1) entry")
        val semesterMonday = LocalDate.parse(monday.date)
        semesterPrefs.setStartDate(semesterMonday)
        return semesterMonday
    }
}
```

- [ ] **Step 4: 运行该测试类确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitimport.ResolveSemesterAnchorUseCaseTest"`
Expected: PASS（3 个测试全绿：已设覆盖、未设取周一、乱序取周一）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitimport/ResolveSemesterAnchorUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitimport/ResolveSemesterAnchorUseCaseTest.kt
git commit -m "feat(bitimport): 导入总是按所选学期周1覆盖学期锚点(修跨学期旧锚)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 课表 VM 改响应式观察起始日 + 手设入口

**Files:**
- Modify（整文件替换）: `app/src/main/java/com/example/personal_studio/feature/timeline/vm/CourseWeekGridViewModel.kt`

> 本任务无新单测(响应式流 + DataStore 偏好,真机 DoD 验);验证靠编译。

- [ ] **Step 1: 整体替换 CourseWeekGridViewModel.kt**

把 `CourseWeekGridViewModel.kt` 整文件替换为（`bootstrap` 改 `combine(...).stateIn` 响应式;`init` 只做首个非空起始日时定位当前周;新增 `onSemesterStartPicked`）:

```kotlin
package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * UI state for the 7×N traditional course-table view.
 *
 * - [displayWeekIndex]: 1-based week relative to [SemesterPreferences.startDate].
 * - [coursesByCell]: keyed by (weekday 1..7, periodIndex) using the START period.
 *   Multi-period rows still appear once and the renderer computes the visual span
 *   from `(periodIndex, periodEndIndex)`.
 */
data class CourseWeekGridUiState(
    val loading: Boolean = true,
    val needsSemesterStart: Boolean = false,
    val semesterStart: LocalDate? = null,
    val displayWeekIndex: Int = 1,
    val weekStart: LocalDate = LocalDate.now(),
    val weekEnd: LocalDate = LocalDate.now(),
    val isCurrentWeek: Boolean = true,
    val periods: List<TimetablePeriod> = emptyList(),
    val coursesByCell: Map<Pair<Int, Int>, TimelineItem> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourseWeekGridViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val semester: SemesterPreferences,
    private val timetable: TimetablePreferences,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** 1-based week to display. */
    private val displayWeekIndex = MutableStateFlow(1)

    private data class Bootstrap(
        val semesterStart: LocalDate?,
        val periods: List<TimetablePeriod>,
    )

    /**
     * Reactive bootstrap: re-emits whenever the semester start or period table
     * changes. Critical so that after an import (or manual pick) writes the
     * anchor, this grid flips out of the "needs semester start" state live —
     * even if the screen's ViewModel survived on the back stack.
     */
    private val bootstrap: StateFlow<Bootstrap?> =
        combine(semester.startDate, timetable.periods) { start, periods ->
            Bootstrap(start, periods)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Position at the current week the first time a non-null start arrives.
        viewModelScope.launch {
            val start = semester.startDate.first { it != null } ?: return@launch
            val today = LocalDate.now(zone)
            val days = ChronoUnit.DAYS.between(start, today)
            displayWeekIndex.value = ((days / 7L).toInt() + 1).coerceAtLeast(1)
        }
    }

    val uiState: StateFlow<CourseWeekGridUiState> =
        bootstrap.flatMapLatest { boot ->
            displayWeekIndex.flatMapLatest { weekIdx ->
                val semesterStart = boot?.semesterStart
                val periods = boot?.periods ?: emptyList()
                if (semesterStart == null) {
                    kotlinx.coroutines.flow.flowOf(
                        CourseWeekGridUiState(
                            loading = boot == null,
                            needsSemesterStart = boot != null,
                            semesterStart = null,
                            displayWeekIndex = weekIdx,
                            periods = periods,
                        )
                    )
                } else {
                    val ws = semesterStart.plusWeeks((weekIdx - 1).toLong())
                    val we = ws.plusDays(6)
                    val startEpoch = ws.atStartOfDay(zone).toInstant().toEpochMilli()
                    val endEpoch = we.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val today = LocalDate.now(zone)
                    val isCurrent = !today.isBefore(ws) && !today.isAfter(we)
                    repo.observeItemsInRange(startEpoch, endEpoch).map { rows ->
                        val courseRows = rows.filter { it.type == TimelineType.COURSE }
                        val byCell: Map<Pair<Int, Int>, TimelineItem> = courseRows
                            .filter { it.weekdayCode != null && it.periodIndex != null }
                            .associateBy { it.weekdayCode!! to it.periodIndex!! }
                        CourseWeekGridUiState(
                            loading = false,
                            needsSemesterStart = false,
                            semesterStart = semesterStart,
                            displayWeekIndex = weekIdx,
                            weekStart = ws,
                            weekEnd = we,
                            isCurrentWeek = isCurrent,
                            periods = periods,
                            coursesByCell = byCell,
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseWeekGridUiState())

    /** 手设兜底:持久化用户选的学期起始日(已由 SemesterStartModal 归一到周一)。
     *  响应式 bootstrap 会随之刷新,课表立即渲染。 */
    fun onSemesterStartPicked(date: LocalDate) {
        viewModelScope.launch { semester.setStartDate(date) }
    }

    fun onPrevWeek() = displayWeekIndex.update { (it - 1).coerceAtLeast(1) }
    fun onNextWeek() = displayWeekIndex.update { it + 1 }
    fun onCurrentWeek() {
        val start = bootstrap.value?.semesterStart ?: return
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(start, today)
        val weekIdx = ((days / 7L).toInt() + 1).coerceAtLeast(1)
        displayWeekIndex.value = weekIdx
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/timeline/vm/CourseWeekGridViewModel.kt
git commit -m "feat(timeline): 课表 VM 改响应式观察学期起始日(导入/手设后即刻刷新)+ onSemesterStartPicked

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 空课表引导导入 CTA + 手动兜底(去死胡同)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseWeekGridScreen.kt`（导入 SemesterStartModal/runtime state、改 needsSemesterStart 分支、加状态与 modal、加 SemesterSetupCta 组合件）

- [ ] **Step 1: 加导入**

在 `CourseWeekGridScreen.kt` 的 `import androidx.compose.runtime.remember` 行后补两个 runtime 导入,并在 `import androidx.compose.material.icons.filled.Today` 行后补 SemesterStartModal 导入。

把:
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
```
改为:
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

并在 `import com.example.personal_studio.feature.timeline.vm.CourseWeekGridViewModel` 行前加一行:
```kotlin
import com.example.personal_studio.feature.timeline.ui.components.SemesterStartModal
```

- [ ] **Step 2: 改 needsSemesterStart 分支 + 加手设 modal 状态**

在 `CourseWeekGridScreen` 函数体,把顶部 `val ui by vm.uiState.collectAsStateWithLifecycle()` 一行替换为(加一个本地 state):

```kotlin
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var showManualPicker by remember { mutableStateOf(false) }
```

把 `when` 块里的 needsSemesterStart 分支(当前):
```kotlin
            ui.needsSemesterStart -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "请先到 Settings → 学期设置 设置学期起始日期",
                        color = Foam,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
```
替换为:
```kotlin
            ui.needsSemesterStart -> {
                SemesterSetupCta(
                    onNavigateToImport = onNavigateToImport,
                    onPickManually = { showManualPicker = true },
                )
            }
```

- [ ] **Step 3: 在屏函数末尾(最外层 Column 之后)加手设 modal**

在 `CourseWeekGridScreen` 最外层 `Column { ... }` 闭合之后、函数右括号之前,加:

```kotlin
    if (showManualPicker) {
        SemesterStartModal(
            onPicked = { date -> showManualPicker = false; vm.onSemesterStartPicked(date) },
            onDismiss = { showManualPicker = false },
        )
    }
```

- [ ] **Step 4: 加 SemesterSetupCta 组合件**

在 `EmptyCoursesCta` 组合件定义之前(或之后)加一个新的 private 组合件:

```kotlin
/** 首启/未定锚时的引导:主推从教务一键导入(自动定锚+灌课程),次选手动设置学期起始日。 */
@Composable
private fun SemesterSetupCta(
    onNavigateToImport: () -> Unit,
    onPickManually: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "# 课程表尚未初始化",
            color = Foam,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "从教务系统导入会自动获取学期起始日与课程",
            color = FoamDim,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onNavigateToImport,
            colors = ButtonDefaults.buttonColors(
                containerColor = Phosphor,
                contentColor = Void,
            ),
        ) {
            Text("从教务系统一键导入", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onPickManually) {
            Text("[手动设置学期起始日]", color = FoamDim, style = MaterialTheme.typography.labelLarge)
        }
    }
}
```

> 说明:`TextAlign` 已在文件导入(`import androidx.compose.ui.text.style.TextAlign`);`Button`/`ButtonDefaults`/`TextButton`/`Arrangement`/`Spacer`/`height`/`Column`/`Foam`/`FoamDim`/`Phosphor`/`Void` 均已在 `CourseWeekGridScreen.kt` 现有导入中。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 全量单测回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL（全绿,尤其 `ResolveSemesterAnchorUseCaseTest`）。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseWeekGridScreen.kt
git commit -m "feat(timeline): 首启空课表去死胡同,改引导一键导入(自动定锚)+ 手动设置兜底

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完成后

全部 Task 完成且 `./gradlew :app:testDebugUnitTest` 全绿后,用 **superpowers:finishing-a-development-branch** 收尾(真机 DoD → 合并 main → 推 GitHub → 删分支 → 更新记忆)。

**真机 DoD（逐项过）：**
1. 清应用数据后首启 → 进课程表 → 看到 `SemesterSetupCta`（`# 课程表尚未初始化` + 「从教务系统一键导入」+ `[手动设置学期起始日]`），**不再**是"请先到 Settings 设置"死胡同。
2. 点「从教务系统一键导入」→ 走导入(未登录则拦截回跳登录)→ 导入完成返回课表 → 课表**即刻**渲染本周课程(学期起始日已自动定锚,无需手设)。
3. 点 `[手动设置学期起始日]` → 弹日期选择(归一到周一)→ 选定 → 课表立即渲染。
4. 已有课表的用户再次「一键导入」(如新学期)→ 锚点按新学期周1刷新,周次/日期正确。

## Self-Review 记录

- **Spec 覆盖**：① 空课表引导导入去死胡同 → Task 3;② VM 响应式 + onSemesterStartPicked → Task 2;③ 导入按所选学期刷新锚点 → Task 1。全覆盖。
- **占位符**：无 TBD/TODO;每步含完整代码与确切命令。
- **类型一致**：`onSemesterStartPicked(date: LocalDate)` 在 Task 2 定义、Task 3 调用一致;`SemesterSetupCta(onNavigateToImport, onPickManually)` 在 Task 3 内定义并调用一致;`SemesterStartModal(onPicked, onDismiss)` 与现有签名一致;`ResolveSemesterAnchorUseCase.invoke(week1Days)` 签名未变(仅行为变),`ImportCoursesUseCase` 调用点无需改。
