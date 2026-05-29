# P9 · BIT 考试安排同步到 Timeline 实现计划

> **⚠️ 实现变更(2026-05-30):数据源由 正方 jsxsd 改为 ehall studentWdksapApp。** 真机验证发现 BIT 已停用 jsxsd 的 xsks 考试模块;最终实现改用本硕博一体化教学中心(ehall,jxzxehallapp.bit.edu.cn)的 `studentWdksapApp`,接口 `POST jwapp/sys/studentWdksapApp/WdksapController/cxxsksap.do`(body `requestParamStr={"XNXQDM":"<学期>","*order":"-KSRQ,-KSSJMS"}`,返回 JSON `datas.cxxsksap.rows`),复用 P5 课表的 jwapp 会话与 warm-up(getAppConfig/switchLang/getCurrentTerm → getExamAppConfig/switchLangExam)。下方任务体中所有关于 `JsxsdExamParser` / jwms / `activateService(JWMS)` / jsxsd HTML 解析的内容均已废弃,仅作历史记录,不代表已落地实现。实际代码:`ExamRowDto` / `ExamRowMapper` / `SyncExamsUseCase` / `BitJwappService.getExamSchedule`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 BIT 正方 jsxsd 拉考试安排(课程/起止时间/考点/座位/监考),作为新 `EXAM` 类型灌进 Timeline,并提供独立考试列表页;接 P8 统一登录守卫。

**Architecture:** 复用 P6 的 jwms 会话(CAS→activateService→jsxsd HTML);`JsxsdExamParser` 克隆 grades 解析器(正则 + 按表头映射列 + 考试时间拆 startAt/endAt)。新 `EXAM` `TimelineType`(专属 Cyan 配色、考前 1天/2小时/30分提醒、块状态机)。`ReplaceImportedExamUseCase` 克隆 P7 的 `ReplaceImportedDdl`(按 sourceExternalId 去重、保留本地 isDone、重排提醒)。考试列表页 + 登录守卫克隆 P7/P8。手动刷新,不做后台轮询。

**Tech Stack:** Kotlin、Hilt、Retrofit/OkHttp(jwms 会话)、Room、Jetpack Compose、AlarmManager 提醒、mockk + runTest + Turbine。

**前置:** 分支 `feature/p9-exam-schedule`。BIT `xsks` 协议(路径/字段/表头)待真机抓包(同 P5/P6),代码按强智通用协议 + 按表头名映射(对列序鲁棒)。

**实现注记(读现状得出):**
- `JWMS_SERVICE = "http://jwms.bit.edu.cn/"`;`SyncGradesUseCase` 的 open→sso→`apiClient.cas.activateService(JWMS_SERVICE)`→`apiClient.jwms.xxx()` 是会话模板,考试同 host 同 session。
- `JsxsdGradeParser` 用 `TABLE/ROW/CELL` 正则 + `col(vararg keys)` 表头映射 + `clean()`,无 jsoup。考试解析器克隆这套。
- 加 `TimelineType.EXAM` 会让所有穷尽 `when(type)` 编译失败,必须同时改全(见 Task 3 列出的 4 处)。`BubbleState` 加 `Exam*` 同理改 `TimelineBubble` 的 2 处 `when(state)`。
- `ReminderAlarmReceiver` 的 `when(type)` 把提醒 gate 在通知开关:EXAM 复用 `switches.task`(考试/作业同属学业提醒;不扩 NotifPreferences)。
- dev 库可丢:Room v11→12 用 `fallbackToDestructiveMigration`,不写迁移。

---

## 文件结构

```
# 新增
domain/bitexam/model/ExamModels.kt           ExamItem / ExamSyncRequest / ExamSyncStep / ExamSyncError
domain/bitexam/JsxsdExamParser.kt            正则 + 表头映射 + 考试时间拆分
domain/bitexam/ReplaceImportedExamUseCase.kt 灌 timeline_items(EXAM/IMPORTED_EXAM)
domain/bitexam/SyncExamsUseCase.kt           sync(Flow):open→sso→activateService→抠学期→拉→解析→replace
feature/bitexam/ExamsViewModel.kt            列表页 VM
feature/bitexam/ui/ExamsScreen.kt            列表页 UI

# 改(EXAM 类型集成)
domain/model/TimelineModels.kt               TimelineType+EXAM;TimelineSource+IMPORTED_EXAM;BubbleState+Exam*5
domain/timeline/ComputeBubbleStateUseCase.kt EXAM 分支
domain/timeline/ScheduleRemindersUseCase.kt  slotsFor(EXAM)
core/workers/ReminderAlarmReceiver.kt        when(type) +EXAM→switches.task
core/notification/TimelineNotifier.kt        when(type) +EXAM 文案
feature/timeline/ui/components/TimelineBubble.kt bubbleBaseColor/visualMods/textDecoration +Exam*
data/local/db/dao/TimelineDao.kt             observeImportedExams/getImportedExams;observeDayCounts +EXAM
data/local/db/AppDatabase.kt                 VERSION 11→12

# 改(网络/导航/入口)
data/network/bit/service/BitJwmsService.kt   getExamQueryHtml + getExamScheduleHtml(xnxqid)
ui/navigation/NavRoutes.kt                   EXAMS = "exams"
ui/AppNavHost.kt                             exams composable + deeplink + 登录守卫
feature/settings/ui/SettingsScreen.kt        ## timeline 加「查询考试安排」行
feature/timeline/ui/TimelineScreen.kt        顶栏加「考试 ↗」
```

---

## Phase A · 数据 / 解析

### Task 1: `ExamModels`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitexam/model/ExamModels.kt`

- [ ] **Step 1: 写模型**

```kotlin
package com.example.personal_studio.domain.bitexam.model

import com.example.personal_studio.data.network.bit.NetworkMode

/** 解析自 jsxsd 考试安排页的一条考试。 */
data class ExamItem(
    val uid: String,          // 去重键:"$term|$courseCode|$startAt"
    val course: String,
    val startAt: Long,        // 考试开始 epoch millis(本地)
    val endAt: Long?,         // 结束;只有日期无时间时为 null
    val location: String?,    // 考点
    val seat: String?,        // 座位号
    val invigilator: String?, // 监考/任课教师
)

data class ExamSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

sealed interface ExamSyncStep {
    object LoggingIn : ExamSyncStep
    object FetchingExams : ExamSyncStep
    data class Done(val total: Int) : ExamSyncStep
    data class Failed(val error: ExamSyncError) : ExamSyncStep
}

sealed interface ExamSyncError {
    object WrongCredentials : ExamSyncError
    object AccountLocked : ExamSyncError
    object CaptchaRequired : ExamSyncError
    object NeedReview : ExamSyncError
    data class ParseFail(val message: String) : ExamSyncError
    data class NetworkFail(val cause: String) : ExamSyncError
    data class Unexpected(val cause: String) : ExamSyncError
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitexam/model/ExamModels.kt
git commit -m "p9: ExamModels(ExamItem/Request/Step/Error)"
```

---

### Task 2: `JsxsdExamParser`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitexam/JsxsdExamParser.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitexam/JsxsdExamParserTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitexam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class JsxsdExamParserTest {
    private val parser = JsxsdExamParser()

    private fun plus8(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun page(vararg rows: String): String {
        val header = "<tr><th>序号</th><th>校区</th><th>课程编号</th><th>课程名称</th>" +
            "<th>任课教师</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>"
        return """<table id="dataList">$header${rows.joinToString("")}</table>"""
    }
    private fun row(code: String, name: String, teacher: String, time: String, place: String, seat: String) =
        "<tr><td>1</td><td>中关村</td><td>$code</td><td>$name</td>" +
            "<td>$teacher</td><td>$time</td><td>$place</td><td>$seat</td></tr>"

    @Test fun `parses a full exam row with start-end range`() {
        val html = page(row("A101", "高等数学", "张老师", "2026-01-05 08:00~10:00", "中教401", "23"))
        val r = parser.parse(html, term = "2025-2026-1")
        assertEquals(1, r.size)
        val e = r.single()
        assertEquals("高等数学", e.course)
        assertEquals(plus8(2026, 1, 5, 8, 0), e.startAt)
        assertEquals(plus8(2026, 1, 5, 10, 0), e.endAt)
        assertEquals("中教401", e.location)
        assertEquals("23", e.seat)
        assertEquals("张老师", e.invigilator)
        assertEquals("2025-2026-1|A101|${plus8(2026,1,5,8,0)}", e.uid)
    }

    @Test fun `accepts hyphen and fullwidth tilde separators`() {
        val a = parser.parse(page(row("c","x","t","2026-01-05 08:00-10:00","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,10,0), a.endAt)
        val b = parser.parse(page(row("c","x","t","2026-01-05 08:00～10:00","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,10,0), b.endAt)
    }

    @Test fun `date only with no time degrades to midnight and null end`() {
        val e = parser.parse(page(row("c","x","t","2026-01-05","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,0,0), e.startAt)
        assertNull(e.endAt)
    }

    @Test fun `maps columns by header regardless of order`() {
        // 表头顺序换:考试时间在前
        val header = "<tr><th>课程名称</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>"
        val body = "<tr><td>线性代数</td><td>2026-01-06 14:00~16:00</td><td>理406</td><td>7</td></tr>"
        val e = parser.parse("""<table id="dataList">$header$body</table>""", "t").single()
        assertEquals("线性代数", e.course)
        assertEquals("理406", e.location)
        assertEquals("7", e.seat)
    }

    @Test fun `multiple rows`() {
        val html = page(
            row("a","课1","t","2026-01-05 08:00~10:00","r1","1"),
            row("b","课2","t","2026-01-06 14:00~16:00","r2","2"),
        )
        assertEquals(listOf("课1", "课2"), parser.parse(html, "t").map { it.course })
    }

    @Test fun `empty result yields empty list`() {
        assertTrue(parser.parse("""<table id="dataList"><tr><td>未查询到数据</td></tr></table>""", "t").isEmpty())
        assertTrue(parser.parse("<html>no table</html>", "t").isEmpty())
    }

    @Test fun `extractCurrentTerm reads selected xnxqid option`() {
        val html = """<select id="xnxqid" name="xnxqid">
            <option value="2024-2025-2">2024-2025-2</option>
            <option value="2025-2026-1" selected>2025-2026-1</option></select>"""
        assertEquals("2025-2026-1", JsxsdExamParser.extractCurrentTerm(html))
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*JsxsdExamParserTest*"`
Expected: FAIL(未定义)

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.domain.bitexam.model.ExamItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 解析 BIT 正方(强智 jsxsd)考试安排页 HTML → [ExamItem]。
 * 表 `<table id="dataList">`,首行表头按名映射列(对列序鲁棒);考试时间 cell 形如
 * `2026-01-05 08:00~10:00`(分隔符 ~ / ～ / -)拆 startAt/endAt(+08:00)。
 * 克隆 JsxsdGradeParser 的正则风格(无 jsoup)。表 id / 列名待真机确认。
 */
class JsxsdExamParser @Inject constructor() {

    fun parse(html: String, term: String): List<ExamItem> {
        val table = TABLE.find(html)?.groupValues?.get(1) ?: return emptyList()
        val rows = ROW.findAll(table).map { it.groupValues[1] }.toList()
        if (rows.size < 2) return emptyList()

        val headers = CELL.findAll(rows[0]).map { clean(it.groupValues[1]) }.toList()
        fun col(vararg keys: String): Int = headers.indexOfFirst { h -> keys.any { it in h } }
        val ciName = col("课程名")
        val ciCode = col("课程编号", "课程号")
        val ciTime = col("考试时间", "考试日期")
        val ciPlace = col("考点", "考试地点", "地点")
        val ciSeat = col("座位")
        val ciTeacher = col("监考", "任课教师", "教师")

        return rows.drop(1).mapNotNull { r ->
            val cells = CELL.findAll(r).map { clean(it.groupValues[1]) }.toList()
            fun at(i: Int): String = if (i in cells.indices) cells[i] else ""
            val name = at(ciName).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val (start, end) = parseTime(at(ciTime)) ?: return@mapNotNull null
            val code = at(ciCode)
            ExamItem(
                uid = "$term|${code.ifBlank { name }}|$start",
                course = name,
                startAt = start,
                endAt = end,
                location = at(ciPlace).ifBlank { null },
                seat = at(ciSeat).ifBlank { null },
                invigilator = at(ciTeacher).ifBlank { null },
            )
        }
    }

    /** 拆 `2026-01-05 08:00~10:00` → (start,end);只日期 → (当天00:00, null);拆不出 → null。 */
    private fun parseTime(cell: String): Pair<Long, Long?>? = runCatching {
        val m = RANGE.find(cell)
        if (m != null) {
            val date = LocalDate.parse(m.groupValues[1], DATE)
            val s = LocalTime.parse(pad(m.groupValues[2]), TIME)
            val e = LocalTime.parse(pad(m.groupValues[3]), TIME)
            LocalDateTime.of(date, s).toInstant(ZoneOffset.ofHours(8)).toEpochMilli() to
                LocalDateTime.of(date, e).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        } else {
            val d = DATE_ONLY.find(cell)?.groupValues?.get(1) ?: return null
            LocalDate.parse(d, DATE).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli() to null
        }
    }.getOrNull()

    /** `8:00` → `08:00`,供 HH:mm 解析。 */
    private fun pad(t: String): String = if (t.length == 4) "0$t" else t

    private fun clean(cell: String): String = cell
        .replace(TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(WS, " ").trim()

    companion object {
        private val TABLE = Regex("""<table[^>]*\bid=["']dataList["'][^>]*>(.*?)</table>""", RegexOption.DOT_MATCHES_ALL)
        private val ROW = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        private val CELL = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("""<[^>]*>""")
        private val WS = Regex("""\s+""")
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val RANGE = Regex("""(\d{4}-\d{1,2}-\d{1,2})\s+(\d{1,2}:\d{2})\s*[~～\-－]\s*(\d{1,2}:\d{2})""")
        private val DATE_ONLY = Regex("""(\d{4}-\d{1,2}-\d{1,2})""")
        private val XNXQID_SELECTED = Regex("""<option[^>]*value=["']([^"']+)["'][^>]*\bselected\b""")
        private val XNXQID_FIRST = Regex("""<select[^>]*\bid=["']xnxqid["'][^>]*>.*?<option[^>]*value=["']([^"']+)["']""", RegexOption.DOT_MATCHES_ALL)

        /** 从 xsksap_query 页抠当前学期 xnxqid:优先 selected option,否则 xnxqid select 的首个 option。 */
        fun extractCurrentTerm(html: String): String? =
            XNXQID_SELECTED.find(html)?.groupValues?.get(1)
                ?: XNXQID_FIRST.find(html)?.groupValues?.get(1)
    }
}
```

注:`DATE` 用 `yyyy-MM-dd` 但正则允许 `\d{1,2}` 月日;若真机出现 `2026-1-5` 非补零,`LocalDate.parse` 会失败 → 该行跳过。真机若如此,把 DATE 改 `yyyy-M-d`。先按补零格式(测试用例补零)。

- [ ] **Step 4: 运行 → PASS**(7 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*JsxsdExamParserTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitexam/JsxsdExamParser.kt \
        app/src/test/java/com/example/personal_studio/domain/bitexam/JsxsdExamParserTest.kt
git commit -m "p9: JsxsdExamParser(表头映射 + 考试时间拆分 + 学期抠取)+ 7 单测"
```

---

## Phase B · EXAM 类型集成

### Task 3: `TimelineType.EXAM` 全链集成

**Files:**
- Modify: `domain/model/TimelineModels.kt`
- Modify: `domain/timeline/ComputeBubbleStateUseCase.kt`
- Modify: `domain/timeline/ScheduleRemindersUseCase.kt`
- Modify: `core/workers/ReminderAlarmReceiver.kt`
- Modify: `core/notification/TimelineNotifier.kt`
- Modify: `feature/timeline/ui/components/TimelineBubble.kt`
- Modify: `data/local/db/dao/TimelineDao.kt`
- Modify: `data/local/db/AppDatabase.kt`

> 加 `TimelineType.EXAM` 后,**所有穷尽 `when(type)` 必须同时加 EXAM 分支否则不编译**。本任务一次改全 + 编译过 + 跑既有 Timeline 单测。

- [ ] **Step 1: TimelineModels** — 枚举 + BubbleState

`enum class TimelineType { COURSE, TASK, CUSTOM, EXAM }`
`enum class TimelineSource { MANUAL, IMPORTED_ICS, IMPORTED_PORTAL, FROM_CHAT, IMPORTED_LEXUE, IMPORTED_EXAM }`
在 `sealed class BubbleState` 里(Custom* 之后)加:
```kotlin
    object ExamUpcoming   : BubbleState()
    object ExamImminent   : BubbleState()
    object ExamInProgress : BubbleState()
    object ExamPast       : BubbleState()
    object ExamDone       : BubbleState()
```

- [ ] **Step 2: ComputeBubbleStateUseCase** — 加 EXAM 分支

在 `when (item.type)` 里加(`customImminent` 旁加 `examImminent`):
```kotlin
    private val examImminent = 2 * 60 * MIN_MS
```
```kotlin
            TimelineType.EXAM -> {
                val end = item.endAt ?: item.startAt
                when {
                    item.isDone -> BubbleState.ExamDone
                    end <= now -> BubbleState.ExamPast
                    item.startAt <= now -> BubbleState.ExamInProgress
                    item.startAt - now <= examImminent -> BubbleState.ExamImminent
                    else -> BubbleState.ExamUpcoming
                }
            }
```

- [ ] **Step 3: ScheduleRemindersUseCase.slotsFor** — 加 EXAM

```kotlin
            TimelineType.EXAM -> listOf(
                ReminderSlot(1440, false),
                ReminderSlot(120, false),
                ReminderSlot(30, false),
            )
```

- [ ] **Step 4: ReminderAlarmReceiver** — when(type) 加 EXAM

```kotlin
                    TimelineType.EXAM -> switches.task
```
(EXAM 复用 task 通知开关,不扩 NotifPreferences。)

- [ ] **Step 5: TimelineNotifier.postReminder** — when(type) 加 EXAM

```kotlin
            TimelineType.EXAM -> "${humanDuration(minBefore)}后开考${item.location?.let { "  ·  $it" } ?: ""}"
```

- [ ] **Step 6: TimelineBubble** — 配色 + visualMods + 划线

`bubbleBaseColor` when(state) 加(import `Cyan`):
```kotlin
    BubbleState.ExamUpcoming, BubbleState.ExamImminent,
    BubbleState.ExamInProgress, BubbleState.ExamPast, BubbleState.ExamDone -> Cyan
```
`visualMods` when(state) 把 Exam* 并入对应档:
```kotlin
    BubbleState.CourseUpcoming, BubbleState.TaskUpcoming, BubbleState.CustomUpcoming,
    BubbleState.ExamUpcoming -> VisualMods(1, 1f, false)
    BubbleState.CourseImminent, BubbleState.TaskImminent, BubbleState.CustomImminent,
    BubbleState.ExamImminent -> VisualMods(2, 1f, true)
    BubbleState.CourseInProgress, BubbleState.CustomInProgress,
    BubbleState.ExamInProgress -> VisualMods(2, 1f, false)
    BubbleState.CoursePast, BubbleState.ExamPast -> VisualMods(1, 0.5f, false)
    BubbleState.TaskOverdue, BubbleState.CustomOverdue -> VisualMods(2, 1f, true)
    BubbleState.TaskDone, BubbleState.CustomDone, BubbleState.ExamDone -> VisualMods(1, 0.8f, false)
```
(把现有 `CoursePast -> VisualMods(1,0.5f,false)` 合并到上面的 `CoursePast, ExamPast` 行,删原单独行,避免重复 when 分支编译错。)
划线行(line 89):
```kotlin
            textDecoration = if (state is BubbleState.TaskDone || state is BubbleState.CustomDone ||
                state is BubbleState.ExamDone)
                TextDecoration.LineThrough else TextDecoration.None,
```

- [ ] **Step 7: TimelineDao** — 考试读取 + daystrip 计数

加:
```kotlin
    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM'")
    suspend fun getImportedExams(): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM' ORDER BY startAt ASC")
    fun observeImportedExams(): kotlinx.coroutines.flow.Flow<List<TimelineItemEntity>>
```
`observeDayCounts` 的 taskCount SQL 改为含 EXAM:
```kotlin
               SUM(CASE WHEN type IN ('TASK','EXAM') THEN 1 ELSE 0 END) AS taskCount
```

- [ ] **Step 8: AppDatabase** — VERSION 11→12

`const val VERSION = 12`

- [ ] **Step 9: 编译 + 全量单测**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿(既有 Timeline/bubble 测试不破)。若某穷尽 when 仍报错,补该处 EXAM/Exam* 分支。

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "p9: TimelineType.EXAM 全链集成(枚举/状态机/提醒档/通知/气泡Cyan/DAO/Room v12)"
```

---

## Phase C · 灌库

### Task 4: `ReplaceImportedExamUseCase`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitexam/ReplaceImportedExamUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitexam/ReplaceImportedExamUseCaseTest.kt`

注:`TimelineDao` 有 `getImportedExams()`(T3)、`insertOne(item): Long`、`update(item)`、`deleteById(id)`。`RescheduleAllUpcomingUseCase @Inject(wm)` `operator fun invoke()`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitexam.model.ExamItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceImportedExamUseCaseTest {
    private fun examRow(id: Long, uid: String, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "old", startAt = 100L, endAt = 200L,
        isDone = done, doneAt = if (done) 50L else null,
        sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = uid,
        createdAt = 1L, updatedAt = 1L,
    )
    private fun item(uid: String, start: Long) =
        ExamItem(uid, "高数", start, start + 7200_000L, "中教401", "23", "张老师")

    @Test fun `inserts new exam as EXAM IMPORTED_EXAM with block + seat`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getImportedExams() } returns emptyList() }
        val resched = mockk<RescheduleAllUpcomingUseCase>(relaxed = true)
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(slotItem)) } returns 1L
        ReplaceImportedExamUseCase(dao, resched).invoke(listOf(item("u1", 1000L)), now = 9L)
        with(slotItem.captured) {
            assertEquals(TimelineType.EXAM, type)
            assertEquals(TimelineSource.IMPORTED_EXAM, sourceType)
            assertEquals("u1", sourceExternalId)
            assertEquals(1000L, startAt)
            assertEquals(1000L + 7200_000L, endAt)
            assertEquals("中教401", location)
            assertEquals("张老师", instructor)
            assertEquals("座位: 23", notes)
        }
        coVerify { resched.invoke() }
    }

    @Test fun `updates existing preserving isDone`() = runTest {
        val existing = examRow(7L, "u1", done = true)
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getImportedExams() } returns listOf(existing) }
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(slotItem)) } just Runs
        ReplaceImportedExamUseCase(dao, mockk(relaxed = true)).invoke(listOf(item("u1", 300L)), now = 9L)
        assertEquals(7L, slotItem.captured.id)
        assertEquals(300L, slotItem.captured.startAt)
        assertEquals(true, slotItem.captured.isDone)
        coVerify(exactly = 0) { dao.insertOne(any()) }
    }

    @Test fun `deletes vanished exam`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            coEvery { getImportedExams() } returns listOf(examRow(7L, "gone", done = false))
        }
        ReplaceImportedExamUseCase(dao, mockk(relaxed = true)).invoke(listOf(item("u1", 300L)), now = 9L)
        coVerify { dao.deleteById(7L) }
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*ReplaceImportedExamUseCaseTest*"`

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitexam.model.ExamItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import javax.inject.Inject

/**
 * 把考试同步进 timeline_items(EXAM + IMPORTED_EXAM),按 sourceExternalId(=uid)去重:
 *   新→insert;已存在→update 标题/时间/地点/座位,**保留本地 isDone/doneAt**;消失→delete。
 *   落库后 RescheduleAllUpcomingUseCase 重排提醒。
 */
class ReplaceImportedExamUseCase @Inject constructor(
    private val dao: TimelineDao,
    private val rescheduleAllUpcoming: RescheduleAllUpcomingUseCase,
) {
    suspend fun invoke(exams: List<ExamItem>, now: Long = System.currentTimeMillis()) {
        val existing = dao.getImportedExams().associateBy { it.sourceExternalId }
        val incoming = exams.map { it.uid }.toSet()

        existing.values.filter { it.sourceExternalId !in incoming }.forEach { dao.deleteById(it.id) }

        for (e in exams) {
            val prior = existing[e.uid]
            val notes = e.seat?.let { "座位: $it" }
            if (prior == null) {
                dao.insertOne(
                    TimelineItemEntity(
                        type = TimelineType.EXAM,
                        title = e.course,
                        startAt = e.startAt,
                        endAt = e.endAt,
                        isDone = false,
                        location = e.location,
                        instructor = e.invigilator,
                        notes = notes,
                        sourceType = TimelineSource.IMPORTED_EXAM,
                        sourceExternalId = e.uid,
                        courseName = e.course,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                dao.update(
                    prior.copy(
                        title = e.course,
                        startAt = e.startAt,
                        endAt = e.endAt,
                        location = e.location,
                        instructor = e.invigilator,
                        notes = notes,
                        courseName = e.course,
                        updatedAt = now,
                    ),
                )
            }
        }
        rescheduleAllUpcoming()
    }
}
```

- [ ] **Step 4: 运行 → PASS**(3 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*ReplaceImportedExamUseCaseTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitexam/ReplaceImportedExamUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitexam/ReplaceImportedExamUseCaseTest.kt
git commit -m "p9: ReplaceImportedExamUseCase(EXAM 块+座位/去重/保留isDone/重排)+ 3 单测"
```

---

## Phase D · 网络 / 同步

### Task 5: `BitJwmsService` 端点 + `SyncExamsUseCase`

**Files:**
- Modify: `data/network/bit/service/BitJwmsService.kt`
- Create: `app/src/main/java/com/example/personal_studio/domain/bitexam/SyncExamsUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitexam/SyncExamsUseCaseTest.kt`

注:`SyncGradesUseCase` 构造 `(apiClient, ssoLogin, parser, detailParser, replacer)`,流程 open→sso→`apiClient.cas.activateService(JWMS_SERVICE)`→fetch。`JWMS_SERVICE="http://jwms.bit.edu.cn/"`(在 SyncGradesUseCase 伴生)。`SsoLoginUseCase.invoke(api,u,p): CasLoginDto`。`CasLoginDto`:Success/WrongCredentials/AccountLocked/CaptchaRequired/UnknownFailure(body)。

- [ ] **Step 1: BitJwmsService 加端点**

读 `BitJwmsService.kt`(现有 `getScoreListHtml()` + `getCourseDetailHtml(@Url)`),追加:
```kotlin
    @GET("jsxsd/xsks/xsksap_query")
    suspend fun getExamQueryHtml(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("jsxsd/xsks/xsksap_list")
    suspend fun getExamScheduleHtml(@retrofit2.http.Field("xnxqid") term: String): Response<ResponseBody>
```
(import `retrofit2.http.FormUrlEncoded`、`retrofit2.http.POST`、`retrofit2.http.Field` 若缺。)

- [ ] **Step 2: 写失败测试**(syncForeground 各路径)

```kotlin
package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import com.example.personal_studio.data.network.bit.service.BitJwmsService
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncExamsUseCaseTest {
    private fun resp(body: String) = Response.success(body.toResponseBody("text/html".toMediaType()))
    private fun req() = ExamSyncRequest("u", "p", NetworkMode.LOCAL, true)

    private val queryHtml = """<select id="xnxqid"><option value="2025-2026-1" selected>x</option></select>"""
    private val listHtml = """<table id="dataList"><tr><th>课程名称</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>
        <tr><td>高数</td><td>2026-01-05 08:00~10:00</td><td>中教401</td><td>23</td></tr></table>"""

    private fun useCase(
        sso: SsoLoginUseCase,
        lexueOk: Boolean = true,
        replacer: ReplaceImportedExamUseCase = mockk(relaxed = true),
    ): SyncExamsUseCase {
        val jwms = mockk<BitJwmsService> {
            coEvery { getExamQueryHtml() } returns resp(queryHtml)
            coEvery { getExamScheduleHtml(any()) } returns resp(if (lexueOk) listHtml else "<html>x</html>")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk<BitCasService>(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        return SyncExamsUseCase(api, sso, JsxsdExamParser(), replacer)
    }

    @Test fun `happy path emits Done and replaces`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Done && it.total == 1 })
    }

    @Test fun `wrong password emits Failed WrongCredentials`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Failed && it.error is ExamSyncError.WrongCredentials })
    }
}
```

- [ ] **Step 3: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncExamsUseCaseTest*"`

- [ ] **Step 4: 写实现**

```kotlin
package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 考试安排同步:open→sso→activateService(jwms)→GET xsksap_query 抠学期→POST xsksap_list→解析→灌 Timeline。
 * 复用 grades 的 jwms 会话(同 host)。手动触发,无后台轮询。
 */
class SyncExamsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val parser: JsxsdExamParser,
    private val replacer: ReplaceImportedExamUseCase,
) {
    fun sync(req: ExamSyncRequest): Flow<ExamSyncStep> = flow {
        try {
            apiClient.open(req.networkMode)
            emit(ExamSyncStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toExamError()?.let { emit(ExamSyncStep.Failed(it)); return@flow }

            apiClient.cas.activateService(SyncGradesUseCase.JWMS_SERVICE)

            emit(ExamSyncStep.FetchingExams)
            val queryHtml = (apiClient.jwms.getExamQueryHtml().let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val term = JsxsdExamParser.extractCurrentTerm(queryHtml)
                ?: run { emit(ExamSyncStep.Failed(ExamSyncError.ParseFail("无学期"))); return@flow }
            val listHtml = (apiClient.jwms.getExamScheduleHtml(term).let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val exams = parser.parse(listHtml, term)
            replacer.invoke(exams)
            emit(ExamSyncStep.Done(exams.size))
        } catch (io: IOException) {
            emit(ExamSyncStep.Failed(ExamSyncError.NetworkFail(io.message ?: "io")))
        } catch (e: Throwable) {
            emit(ExamSyncStep.Failed(ExamSyncError.Unexpected(e.message ?: e.javaClass.simpleName)))
        } finally {
            apiClient.close()
        }
    }

    private fun CasLoginDto.toExamError(): ExamSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> ExamSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> ExamSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> ExamSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> ExamSyncError.ParseFail("CAS: $body")
    }
}
```
确认 `JWMS_SERVICE` 是 `SyncGradesUseCase` 的 public companion const(若是 private,改为在 ExamModels 或本类定义同值 `"http://jwms.bit.edu.cn/"`)。

- [ ] **Step 5: 运行 → PASS**(2 用例)+ 编译

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncExamsUseCaseTest*"`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/network/bit/service/BitJwmsService.kt \
        app/src/main/java/com/example/personal_studio/domain/bitexam/SyncExamsUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitexam/SyncExamsUseCaseTest.kt
git commit -m "p9: BitJwmsService xsks 端点 + SyncExamsUseCase(抠学期+拉+错误映射)+ 单测"
```

---

## Phase E · UI / 接入

### Task 6: `ExamsViewModel`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitexam/ExamsViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitexam/ExamsViewModelTest.kt`

注:克隆 `AssignmentsViewModel`(P7)。依赖:`TimelineDao.observeImportedExams()`、`ToggleDoneUseCase`、`CancelRemindersUseCase`、`ScheduleRemindersUseCase`、`TimelineRepository`、`SyncExamsUseCase`、`ImportCredentialPrefs`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.bitexam

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
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
import org.junit.Before
import org.junit.Test

class ExamsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_000_000_000_000L
    private fun row(id: Long, start: Long, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "E$id", startAt = start, endAt = start + 7200_000L,
        isDone = done, location = "r", notes = "座位: 1",
        sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = "u$id", createdAt = 1L, updatedAt = 1L,
    )
    private fun vm(dao: TimelineDao) = ExamsViewModel(
        dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
        scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true), sync = mockk(relaxed = true),
        credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
        nowProvider = { now },
    )

    @Test fun `splits upcoming vs past`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeImportedExams() } returns flowOf(listOf(
                row(1, now + 2 * 3600_000L, false),  // upcoming
                row(2, now - 3 * 3600_000L, false),  // past (考完)
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.uiState.value.upcoming.map { it.id })
        assertEquals(listOf(2L), vm.uiState.value.past.map { it.id })
        job.cancel()
    }

    @Test fun `refresh without creds emits NeedLogin`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val vm = vm(dao)
        vm.events.test { vm.onRefresh(); advanceUntilIdle(); assertEquals(ExamsEvent.NeedLogin, awaitItem()) }
    }

    @Test fun `toggle done delegates`() = runTest {
        val toggle = mockk<com.example.personal_studio.domain.timeline.ToggleDoneUseCase>(relaxed = true)
        val cancel = mockk<com.example.personal_studio.domain.timeline.CancelRemindersUseCase>(relaxed = true)
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val vm = ExamsViewModel(dao, toggle, cancel, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) }, { now })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleDone(5L, true); advanceUntilIdle()
        coVerify { toggle.invoke(5L, true) }
        coVerify { cancel.invoke(5L) }
        job.cancel()
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

- [ ] **Step 3: 写实现**(克隆 AssignmentsViewModel,past 用考完)

```kotlin
package com.example.personal_studio.feature.bitexam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.bitexam.SyncExamsUseCase
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamRow(
    val id: Long, val course: String, val startAt: Long, val endAt: Long?,
    val location: String?, val seat: String?, val isDone: Boolean,
)

data class ExamsUiState(
    val upcoming: List<ExamRow> = emptyList(),
    val past: List<ExamRow> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
    val credsSaved: Boolean = false,
)

sealed interface ExamsEvent { object NeedLogin : ExamsEvent }

@HiltViewModel
class ExamsViewModel @Inject constructor(
    private val dao: TimelineDao,
    private val toggleDone: ToggleDoneUseCase,
    private val cancelReminders: CancelRemindersUseCase,
    private val scheduleReminders: ScheduleRemindersUseCase,
    private val repo: TimelineRepository,
    private val sync: SyncExamsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val transient = MutableStateFlow(ExamsUiState())
    private val _events = MutableSharedFlow<ExamsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ExamsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ExamsUiState> = combine(
        dao.observeImportedExams(), credPrefs.observeAll(), transient,
    ) { rows, creds, t ->
        val now = nowProvider()
        val mapped = rows.map { it.toRow() }
        val (past, upcoming) = mapped.partition { (it.endAt ?: it.startAt) < now }
        t.copy(
            upcoming = upcoming.sortedBy { it.startAt },
            past = past.sortedByDescending { it.startAt },
            credsSaved = creds != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExamsUiState())

    fun onToggleDone(id: Long, done: Boolean) = viewModelScope.launch {
        toggleDone(id, done)
        cancelReminders(id)
        if (!done) {
            val item = repo.findById(id)
            if (item != null && item.startAt > nowProvider()) scheduleReminders(item)
        }
    }

    fun onRefresh() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(ExamsEvent.NeedLogin) }
            return
        }
        sync.sync(ExamSyncRequest(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL, true))
            .onEach { step ->
                transient.value = when (step) {
                    ExamSyncStep.LoggingIn, ExamSyncStep.FetchingExams -> transient.value.copy(syncing = true, error = null)
                    is ExamSyncStep.Done -> transient.value.copy(syncing = false, error = null)
                    is ExamSyncStep.Failed -> transient.value.copy(syncing = false, error = step.error.toMessage())
                }
            }
            .launchIn(viewModelScope)
    }

    private fun TimelineItemEntity.toRow() = ExamRow(
        id, title, startAt, endAt, location,
        notes?.removePrefix("座位: ")?.takeIf { it != notes }, isDone,
    )

    private fun ExamSyncError.toMessage(): String = when (this) {
        is ExamSyncError.WrongCredentials -> "密码错误"
        is ExamSyncError.AccountLocked -> "账号锁定"
        is ExamSyncError.CaptchaRequired -> "需验证码,请网页端登录一次"
        is ExamSyncError.NeedReview -> "请先完成评教"
        is ExamSyncError.ParseFail -> "教务返回异常"
        is ExamSyncError.NetworkFail -> "网络错误,请重试"
        is ExamSyncError.Unexpected -> "未知错误"
    }
}
```

- [ ] **Step 4: 运行 → PASS**(3 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*ExamsViewModelTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitexam/ExamsViewModel.kt \
        app/src/test/java/com/example/personal_studio/feature/bitexam/ExamsViewModelTest.kt
git commit -m "p9: ExamsViewModel(即将/已考分组/勾已考/刷新/未登录事件)+ 3 单测"
```

---

### Task 7: `ExamsScreen`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitexam/ui/ExamsScreen.kt`

- [ ] **Step 1: 写实现**(克隆 AssignmentsScreen 风格,显示时间段/地点/座位/倒计时)

```kotlin
package com.example.personal_studio.feature.bitexam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitexam.ExamRow
import com.example.personal_studio.feature.bitexam.ExamsEvent
import com.example.personal_studio.feature.bitexam.ExamsViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExamsScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: ExamsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showPast by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.events.collect { if (it is ExamsEvent.NeedLogin) onNeedLogin() }
    }

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ exams", color = Cyan, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::onRefresh, enabled = !st.syncing) {
                Text(if (st.syncing) "同步中…" else "↻ 刷新", color = if (st.syncing) FoamDim else Phosphor)
            }
        }
        st.error?.let { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(8.dp))

        if (st.upcoming.isEmpty() && st.past.isEmpty()) {
            Text("还没有考试安排 —— 下拉刷新,或学校尚未发布", color = FoamDim, style = MaterialTheme.typography.labelMedium)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(st.upcoming, key = { it.id }) { ExamRowView(it) { d -> vm.onToggleDone(it.id, d) } }
            if (st.past.isNotEmpty()) {
                item {
                    TextButton(onClick = { showPast = !showPast }) {
                        Text((if (showPast) "▾ 收起" else "▸ 已考") + " (${st.past.size})", color = FoamMute)
                    }
                }
                if (showPast) items(st.past, key = { it.id }) { ExamRowView(it) { d -> vm.onToggleDone(it.id, d) } }
            }
        }
    }
}

@Composable
private fun ExamRowView(row: ExamRow, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!row.isDone) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = row.isDone, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(row.course, color = if (row.isDone) FoamDim else Foam)
            Text(
                timeRange(row.startAt, row.endAt) +
                    (row.location?.let { "  ·  $it" } ?: "") + (row.seat?.let { "  ·  座位$it" } ?: ""),
                color = FoamMute, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun timeRange(start: Long, end: Long?): String {
    val d = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(start))
    val e = end?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it)) }
    val rel = relative(start)
    return if (e != null) "$d~$e · $rel" else "$d · $rel"
}

private fun relative(t: Long): String {
    val diff = t - System.currentTimeMillis()
    if (diff < 0) return "已结束"
    val min = diff / 60_000
    return when {
        min < 60 -> "${min}分钟后"
        min < 1440 -> "${min / 60}小时后"
        else -> "${min / 1440}天后"
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`（主题色名不符则查 `ui/theme/Color.kt` 调整;`Cyan` 已存在)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitexam/ui/ExamsScreen.kt
git commit -m "p9: ExamsScreen(即将/已考 + 时间段/地点/座位/倒计时 + 刷新 + 勾已考)"
```

---

### Task 8: 导航 + 入口

**Files:**
- Modify: `ui/navigation/NavRoutes.kt`
- Modify: `ui/AppNavHost.kt`
- Modify: `feature/settings/ui/SettingsScreen.kt`
- Modify: `feature/timeline/ui/TimelineScreen.kt`

- [ ] **Step 1: NavRoutes 加 EXAMS**

在 `ASSIGNMENTS` 旁加:`const val EXAMS = "exams"`

- [ ] **Step 2: AppNavHost 注册 exams + deeplink + 守卫**

参照 `composable(NavRoutes.ASSIGNMENTS, deepLinks=...)`,追加:
```kotlin
        composable(
            NavRoutes.EXAMS,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://exams" }),
        ) {
            com.example.personal_studio.feature.bitexam.ui.ExamsScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("exams")) },
            )
        }
```

- [ ] **Step 3: SettingsScreen 加入口行**

在 `## timeline` 区(GRADES 行附近)加:
```kotlin
            NavigableRowWithSubtitle(
                key = "EXAMS",
                value = "查询考试安排 →",
                subtitle = "考试时间 · 地点 · 座位 同步进 Timeline",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.EXAMS) },
            )
```

- [ ] **Step 4: TimelineScreen 顶栏加「考试 ↗」**

找到 P7 加的「作业 ↗」入口(`onOpenAssignments` 参数 + TextButton);照搬加 `onOpenExams: () -> Unit = {}` 参数 + 一个 `TextButton(onClick = onOpenExams) { Text("考试 ↗", color = Cyan) }`(放「作业 ↗」旁;import `Cyan`)。在 AppNavHost 的 TimelineScreen 实例化处接 `onOpenExams = { navController.navigate(NavRoutes.EXAMS) }`。(若 TimelineScreen 入口结构与此不符,照该文件「作业 ↗」的真实写法镜像。)

- [ ] **Step 5: 编译 + 全量单测**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "p9: NavRoutes/AppNavHost 考试页+deeplink+守卫;Settings/Timeline 入口"
```

---

## Phase F · 真机 DoD

### Task 9: 真机端到端验证

**Files:** 无代码。

- [ ] **Step 1: 装机** — `./gradlew :app:installDebug`
- [ ] **Step 2: 入口 + 守卫** — Settings「查询考试安排」/ Timeline「考试 ↗」可达;未登录点刷新 → 跳登录、成功回考试页。
- [ ] **Step 3: 拉取(协议确认)** — 已登录刷新 → 抓包/logcat 确认 `xsksap_query`/`xsksap_list` 路径、POST 字段、考试表 table id 与列名;列出本学期考试,按时间排,每条显示课程/时间段/考点/座位/倒计时。
  - 若解析为空:对照真机 HTML 修 `JsxsdExamParser` 的 TABLE id / 表头关键词 / 时间格式(同 P5/P6 的协议修正)。
- [ ] **Step 4: Timeline 集成** — EXAM 气泡(Cyan,带起止时长块)出现在对应日期,临近脉动;日条右徽标 +N。
- [ ] **Step 5: 提醒** — 考前 1天/2小时/30分收到提醒(`adb shell dumpsys alarm | grep personal_studio` 或等触发)。
- [ ] **Step 6: 勾已考** — 列表勾选 → 移入「已考」折叠区 + 提醒取消。
- [ ] **Step 7: 错误** — 错密码刷新 → banner 报错不灌库。
- [ ] **Step 8: 全量单测** — `./gradlew :app:testDebugUnitTest` 全绿。准备 PR。

---

## Self-Review

**Spec 覆盖**(对照 `2026-05-29-bit-exam-schedule-design.md`):
- §3 数据源/网络 → Task 5(端点 + sync + 抠学期)
- §4 解析器 → Task 2
- §5 ExamModels → Task 1
- §6 EXAM 类型集成 → Task 3(枚举/slotsFor/bubble state/配色/daystrip/Room)
- §7 Timeline 映射 → Task 4
- §8 列表页 + 守卫 + 入口 → Task 6(VM)+ 7(Screen)+ 8(导航/Settings/Timeline)
- §9 错误处理 → Task 5(映射)+ 6(banner 文案)
- §10 测试 → 各 TDD 任务
- §14 DoD → Task 9

**占位符**:无 TBD。协议细节(xsks 路径/字段/表头/table id)明确标注真机 DoD(Task 9 Step 3)修正,同 P5/P6;非计划内空白。Task 8 Step 4 对 TimelineScreen 入口给"镜像既有作业入口"兜底(该文件入口结构实现时对照,已给明确参照)。

**Type 一致性**:`ExamItem`/`ExamSyncRequest`/`ExamSyncStep`/`ExamSyncError`(T1)在 T2/T4/T5/T6 一致;`JsxsdExamParser.parse(html,term)` + `extractCurrentTerm`(T2)在 T5 用;`TimelineType.EXAM`/`IMPORTED_EXAM`/`BubbleState.Exam*`(T3)在 T4/T6/Timeline 渲染用;`getImportedExams`/`observeImportedExams`(T3)在 T4/T6 用;`ReplaceImportedExamUseCase.invoke(exams,now)`(T4)在 T5 用;`SyncExamsUseCase.sync(req): Flow<ExamSyncStep>`(T5)在 T6 用;`ExamsEvent.NeedLogin`(T6)在 T7 用;`NavRoutes.EXAMS`/`login("exams")`(T8)。`JWMS_SERVICE` 复用 `SyncGradesUseCase` 公开常量(T5 注明若 private 则本地定义同值)。
