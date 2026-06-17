# 课程列表管理中心化 + UI 润色 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把"设置 → 课程列表"做成课程管理中心（新建 + 编辑 + 删除入口齐全）并润色为终端风，行内显示星期/节次。

**Architecture:** 三层改动。(1) 纯函数 `formatCourseSchedule` + `weekdayCn` 把课表元信息格式化成一行字符串（可单测，TDD）。(2) 数据层给 `observeCourseSeriesList()` 聚合查询补 `GROUP_CONCAT(DISTINCT weekdayCode)` / `MIN(periodIndex)` / `MAX(periodEndIndex)` 三个投影，灌进 `CourseSeriesSummary`（新字段带默认值，不破坏现有构造点；Room 编译期校验查询）。(3) 重写 `CourseSeriesListScreen`：自带 `TerminalTopBar`(subtitle `# 课程管理`) + 内联 `user@study ls courses/` 头部 + `[+新建课程]` + `total N` + 三段式行 + chat 同款空态；`AppNavHost` 传 `onAddCourse` 复用现有 `TIMELINE_ADD_COURSE` 路由。

**Tech Stack:** Kotlin, Jetpack Compose, Room(@Query 投影), Hilt, Coroutines/Flow, JUnit4。

**约定（项目级）：** 提交信息结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。开发期不写保数据 Room migration（本计划无 schema 变更）。回复中文。Windows + Git Bash，gradle 用 `./gradlew`。

---

### Task 1: 课表元信息格式化纯函数 + 单测（TDD）

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormat.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormatTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormatTest.kt`：

```kotlin
package com.example.personal_studio.feature.timeline.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseScheduleFormatTest {

    @Test fun `full schedule with multiple weekdays period range and credits`() {
        val result = formatCourseSchedule(
            weekdays = listOf(1, 3), periodStart = 1, periodEnd = 2,
            minWeek = 1, maxWeek = 16, occurrenceCount = 32, credits = 3.5f,
        )
        assertEquals("周一/周三 第1-2节 · 第1-16周 · 共32节 · 3.5学分", result)
    }

    @Test fun `single period single weekday no credits`() {
        val result = formatCourseSchedule(
            weekdays = listOf(2), periodStart = 3, periodEnd = 3,
            minWeek = 1, maxWeek = 18, occurrenceCount = 18, credits = null,
        )
        assertEquals("周二 第3节 · 第1-18周 · 共18节", result)
    }

    @Test fun `single week and whole-number credits drop trailing zero`() {
        val result = formatCourseSchedule(
            weekdays = listOf(5), periodStart = 1, periodEnd = 2,
            minWeek = 4, maxWeek = 4, occurrenceCount = 1, credits = 2.0f,
        )
        assertEquals("周五 第1-2节 · 第4周 · 共1节 · 2学分", result)
    }

    @Test fun `missing weekday and period omits the when segment`() {
        val result = formatCourseSchedule(
            weekdays = emptyList(), periodStart = null, periodEnd = null,
            minWeek = 1, maxWeek = 16, occurrenceCount = 16, credits = null,
        )
        assertEquals("第1-16周 · 共16节", result)
    }

    @Test fun `weekdayCn maps codes to chinese`() {
        assertEquals("周一", weekdayCn(1))
        assertEquals("周日", weekdayCn(7))
    }
}
```

- [ ] **Step 2: 运行测试确认失败（编译不过）**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.timeline.ui.CourseScheduleFormatTest"`
Expected: FAIL — `unresolved reference: formatCourseSchedule` / `weekdayCn`（函数还没写）。

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormat.kt`：

```kotlin
package com.example.personal_studio.feature.timeline.ui

import com.example.personal_studio.domain.model.formatCredits

/** 周N:1→周一 … 7→周日。越界裁剪到 [0,6]。 */
fun weekdayCn(dow: Int): String =
    "周" + listOf("一", "二", "三", "四", "五", "六", "日")[(dow - 1).coerceIn(0, 6)]

/**
 * 课程列表行第 2 行的课表元信息,形如
 * `周一/周三 第1-2节 · 第1-16周 · 共32节 · 4学分`。
 * 各段以 ` · ` 连接;星期/节次缺失则省略首段,学分为 null 则省略学分段。
 */
fun formatCourseSchedule(
    weekdays: List<Int>,
    periodStart: Int?,
    periodEnd: Int?,
    minWeek: Int,
    maxWeek: Int,
    occurrenceCount: Int,
    credits: Float?,
): String {
    val parts = mutableListOf<String>()

    val weekdayStr = weekdays.distinct().sorted().joinToString("/") { weekdayCn(it) }
    val periodStr = when {
        periodStart == null || periodEnd == null -> ""
        periodStart == periodEnd -> "第${periodStart}节"
        else -> "第${periodStart}-${periodEnd}节"
    }
    val whenSeg = listOf(weekdayStr, periodStr).filter { it.isNotEmpty() }.joinToString(" ")
    if (whenSeg.isNotEmpty()) parts += whenSeg

    parts += if (minWeek == maxWeek) "第${minWeek}周" else "第${minWeek}-${maxWeek}周"
    parts += "共${occurrenceCount}节"
    credits?.let { parts += "${formatCredits(it)}学分" }

    return parts.joinToString(" · ")
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.timeline.ui.CourseScheduleFormatTest"`
Expected: PASS（5 个测试全绿）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormat.kt \
        app/src/test/java/com/example/personal_studio/feature/timeline/ui/CourseScheduleFormatTest.kt
git commit -m "feat(timeline): 课表元信息格式化纯函数 formatCourseSchedule + weekdayCn(单测)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 聚合查询补"星期/节次"投影（无 schema 变更）

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/dao/TimelineDao.kt:7-16`（投影类）、`:122-133`（查询）
- Modify: `app/src/main/java/com/example/personal_studio/domain/model/TimelineModels.kt:39-48`（域模型）
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/TimelineRepositoryImpl.kt:29-43`（映射）

> 说明：`CourseSeriesSummary` 新增字段**带默认值**，不破坏任何现有构造点（如 `FakeTimelineRepository`、各测试）。`CourseSeriesSummaryRow` 是 Room 投影类，仅 Room 生成构造，无手写构造点。本任务无对应纯逻辑单测，验证靠 Room 编译期 `@Query` 列名↔字段校验 + Kotlin 编译。

- [ ] **Step 1: 给投影类加 3 字段**

在 `TimelineDao.kt` 顶部，把 `CourseSeriesSummaryRow`（第 7-16 行）改为：

```kotlin
data class CourseSeriesSummaryRow(
    val seriesId: Long,
    val title: String,
    val instructor: String?,
    val location: String?,
    val credits: Float?,
    val occurrenceCount: Int,
    val minWeek: Int,
    val maxWeek: Int,
    val weekdaysCsv: String?,
    val periodStart: Int?,
    val periodEnd: Int?,
)
```

- [ ] **Step 2: 给查询加 3 个投影**

把 `observeCourseSeriesList()` 的 `@Query`（第 122-133 行）改为（仅 SELECT 列表新增三列，其余不变）：

```kotlin
    @Query(
        """
        SELECT seriesId, MIN(title) AS title, MIN(instructor) AS instructor, MIN(location) AS location,
               MIN(credits) AS credits,
               COUNT(*) AS occurrenceCount, MIN(weekIndexInSemester) AS minWeek, MAX(weekIndexInSemester) AS maxWeek,
               GROUP_CONCAT(DISTINCT weekdayCode) AS weekdaysCsv,
               MIN(periodIndex) AS periodStart, MAX(periodEndIndex) AS periodEnd
        FROM timeline_items
        WHERE type = 'COURSE' AND seriesId IS NOT NULL
        GROUP BY seriesId
        ORDER BY MIN(startAt) ASC
        """
    )
    fun observeCourseSeriesList(): Flow<List<CourseSeriesSummaryRow>>
```

> 备注：SQLite `GROUP_CONCAT(DISTINCT x)` 用默认逗号分隔（DISTINCT 下不可自定义分隔符），NULL 被跳过；课程行 `weekdayCode` 恒非空。`MIN/MAX(periodIndex/periodEndIndex)` 忽略 NULL。

- [ ] **Step 3: 给域模型加 3 个带默认值字段**

把 `TimelineModels.kt` 的 `CourseSeriesSummary`（第 39-48 行）改为：

```kotlin
/** Aggregate summary of a course series (Settings → 课程列表). */
data class CourseSeriesSummary(
    val seriesId: Long,
    val title: String,
    val instructor: String?,
    val location: String?,
    val credits: Float? = null,
    val occurrenceCount: Int,
    val minWeek: Int,
    val maxWeek: Int,
    val weekdays: List<Int> = emptyList(),
    val periodStart: Int? = null,
    val periodEnd: Int? = null,
)
```

- [ ] **Step 4: 映射新字段（解析 csv→List<Int>）**

把 `TimelineRepositoryImpl.kt` 的 `observeCourseSeriesList()`（第 29-43 行）改为：

```kotlin
    override fun observeCourseSeriesList(): Flow<List<CourseSeriesSummary>> =
        dao.observeCourseSeriesList().map { rows ->
            rows.map {
                CourseSeriesSummary(
                    seriesId = it.seriesId,
                    title = it.title,
                    instructor = it.instructor,
                    location = it.location,
                    credits = it.credits,
                    occurrenceCount = it.occurrenceCount,
                    minWeek = it.minWeek,
                    maxWeek = it.maxWeek,
                    weekdays = it.weekdaysCsv
                        ?.split(",")
                        ?.mapNotNull { code -> code.trim().toIntOrNull() }
                        ?.distinct()
                        ?.sorted()
                        ?: emptyList(),
                    periodStart = it.periodStart,
                    periodEnd = it.periodEnd,
                )
            }
        }
```

- [ ] **Step 5: 编译验证（含 Room @Query 校验）**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。若 Room 报列名不匹配（如投影类字段与 SELECT 列名对不上），按报错修正列别名。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/db/dao/TimelineDao.kt \
        app/src/main/java/com/example/personal_studio/domain/model/TimelineModels.kt \
        app/src/main/java/com/example/personal_studio/data/repository/TimelineRepositoryImpl.kt
git commit -m "feat(timeline): 课程列表聚合查询补 星期/节次 投影(GROUP_CONCAT weekdayCode + MIN/MAX period)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 重写 CourseSeriesListScreen（头部 + 三段式行 + 空态 + 新建入口）

**Files:**
- Modify（整文件替换）: `app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseSeriesListScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt:183-188`（`TIMELINE_COURSE_LIST` 传 `onAddCourse`）

- [ ] **Step 1: 整体替换 CourseSeriesListScreen.kt**

把 `CourseSeriesListScreen.kt` 全文替换为：

```kotlin
package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.feature.timeline.vm.CourseSeriesListViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

/**
 * 设置 → 课程列表。课程管理中心:新建([+新建课程])/编辑(点行进编辑页)/删除(编辑页内)。
 * 头部对齐 chat/scanner 的 user@study 提示;每行三段式显示 课名 / 星期·节次·周次·学分 / 老师·地点。
 */
@Composable
fun CourseSeriesListScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onAddCourse: () -> Unit,
    vm: CourseSeriesListViewModel = hiltViewModel(),
) {
    val series by vm.series.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Void)) {
        TerminalTopBar(route = "courses", subtitle = "# 课程管理", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        // 内联头部:user@study:~$ ls courses/ + [+新建课程] + total N(对齐 scanner)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Amber)) { append("user@study") }
                        withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                        withStyle(SpanStyle(color = Foam)) { append("ls courses/") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[+新建课程]",
                    color = Cyan,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onAddCourse).padding(4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("total ${series.size}", style = MaterialTheme.typography.bodySmall, color = FoamMute)
        }
        Spacer(Modifier.height(14.dp))
        if (series.isEmpty()) {
            CourseEmptyState(onAddCourse = onAddCourse)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(series, key = { it.seriesId }) { s ->
                    CourseRow(s, onClick = { onOpenSeries(s.seriesId) })
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                }
            }
        }
    }
}

/** 三段式行:▸ 课名 / 星期·节次·周次·共N节·学分 / 老师·地点(都空则省第三行)。整行可点进编辑页。 */
@Composable
private fun CourseRow(s: CourseSeriesSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("▸ ") }
                withStyle(SpanStyle(color = Phosphor)) { append(s.title) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatCourseSchedule(
                weekdays = s.weekdays,
                periodStart = s.periodStart,
                periodEnd = s.periodEnd,
                minWeek = s.minWeek,
                maxWeek = s.maxWeek,
                occurrenceCount = s.occurrenceCount,
                credits = s.credits,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = FoamMute,
        )
        val who = listOfNotNull(
            s.instructor?.takeIf { it.isNotBlank() },
            s.location?.takeIf { it.isNotBlank() },
        )
        if (who.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(who.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FoamDim)
        }
    }
}

/** 空态:对齐 chat/scanner(`# 暂无课程` + `▓ 点 [新建课程] 或 day 界面的 [+] 录入` + 闪烁光标)。 */
@Composable
private fun CourseEmptyState(onAddCourse: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("# 暂无课程", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("点 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[新建课程]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 或 day 界面的 [+] 录入") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onAddCourse),
            )
            BlinkingCursor()
        }
    }
}
```

- [ ] **Step 2: AppNavHost 传 onAddCourse**

在 `AppNavHost.kt` 把 `TIMELINE_COURSE_LIST` 的 composable（第 183-188 行）改为：

```kotlin
        composable(NavRoutes.TIMELINE_COURSE_LIST) {
            com.example.personal_studio.feature.timeline.ui.CourseSeriesListScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { sid -> navController.navigate(NavRoutes.timelineCourseSeriesEdit(sid)) },
                onAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) },
            )
        }
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 全量单测回归（确保 Task 2 字段改动未波及其他测试）**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL（全绿；尤其 `CourseScheduleFormatTest` 与任何引用 `CourseSeriesSummary` 的测试）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/timeline/ui/CourseSeriesListScreen.kt \
        app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "feat(timeline): 课程列表管理中心化 + 终端风润色(user@study头部 + [+新建课程] + 三段式行显示星期/节次 + chat 同款空态)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完成后

所有 Task 完成且 `./gradlew :app:testDebugUnitTest` 全绿后，用 **superpowers:finishing-a-development-branch** 收尾（真机 DoD 逐项验 → 合并 main → 推 GitHub → 删分支 → 更新记忆）。

**真机 DoD（逐项过）：**
1. 设置 → 课程列表：顶栏 `studio:~/courses` + `# 课程管理`；下方 `user@study:~$ ls courses/` 黄字头部 + 右侧 `[+新建课程]` + `total N`。
2. 点 `[+新建课程]` → 进 `AddCourseScreen`，能新建一门课，返回列表可见新课。
3. 每行三段式：`▸ 课名`(绿) / `周X/周Y 第A-B节 · 第M-N周 · 共K节 · C学分`(灰) / `老师 · 地点`(暗灰，缺则省)；右列不再参差。
4. 点某行 → 进编辑页改 名称/老师/地点/学分/备注 → 保存 → 列表更新；编辑页"删除整个系列"仍可删。
5. 删光所有课 → 空态 `# 暂无课程` + `▓ 点 [新建课程] 或 day 界面的 [+] 录入` + 闪烁光标；点空态 CTA 能进新建。

## Self-Review 记录

- **Spec 覆盖**：① 新建入口 → Task 3 Step 1/2（头部 `[+新建课程]` + 空态 CTA + NavHost `onAddCourse`）；② 列表润色（头部/三段式行/空态/Rule 分隔）→ Task 3 Step 1 + Task 1（格式化）；③ 数据层投影 → Task 2。全覆盖。
- **占位符**：无 TBD/TODO；每步含完整代码与确切命令。
- **类型一致**：`formatCourseSchedule(weekdays,periodStart,periodEnd,minWeek,maxWeek,occurrenceCount,credits)` 签名在 Task 1 定义、Task 3 调用一致；`CourseSeriesSummary` 新字段 `weekdays/periodStart/periodEnd` 在 Task 2 定义、Task 3 读取一致；`CourseSeriesSummaryRow` 字段名 `weekdaysCsv/periodStart/periodEnd` 与 `@Query` 别名一致。
