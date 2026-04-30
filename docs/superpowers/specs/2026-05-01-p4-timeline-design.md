# P4 Timeline · Design Spec

**创建日期**：2026-05-01
**目标交付**：2026-05 中旬（~10 天工作量，分 5 个 phase tag）
**前置依赖**：P0 / P1 / P2 / P3 已 shipped（tag `p3-knowledge-mvp`，main 上 PR #5 已合并）
**作者**：项目所有者 + AI brainstorm
**前 spec 关系**：本 spec 是 `docs/superpowers/specs/2026-04-20-personal-studio-design.md` §4.4 的 P4 章节细化与执行版，对原 spec 做了关键调整：
- **吸收 P5 Plan C**（周课表手动批量录入）进入 P4，让 P4 一上来就有真实数据
- **砍掉 RRULE / biweekly 库**，课程在录入时直接展开为 N 行（C 方案）
- **砍掉单/双周支持**（连续周次范围已覆盖 95% 场景）
- **改通知策略为 type-default + 全局开关**（简化 spec 原方案的 per-item `remindersMinBefore: List<Int>`）

P5 的 ICS 导入和教务爬虫保留为后续阶段。

---

## 0. 总览

### 0.1 目标

让用户管理一个学期内的课程、作业 DDL 和自定义事件，在统一的"今日时间线"视图上一眼看到当天的全部安排，并按 type 默认值在合适的提前量收到通知提醒。

具体能力：
- **录入**：单条 DDL / 单条事件 / 整门课（"周一周三第 3-4 节高数 1-16 周" → 录一次展开为多周多行）
- **浏览**：单日时间轴 07:00-23:30 + 7 日小色条概览 + "此刻"红线
- **状态**：13 种气泡状态（含"未到 / 临近 / 进行中 / 过去 / 完成 / 过期"等）按 type × 时间相对位置自动转换
- **通知**：按 type 默认提前量发本地推送（COURSE 上课前 10 分钟 / TASK 24h+2h+30min+过期 / CUSTOM 30min）
- **编辑**：单条编辑（"本次"颗粒度）+ 系列管理（从 Settings → 课程列表进入，改名 / 改老师 / 批量删整学期）
- **作息表自适应**：用户在 Settings 改作息节次时间 → 自动重算所有未来 COURSE 行的 startAt/endAt
- **重启恢复**：MainActivity.onCreate + BootCompletedReceiver 双触发 RescheduleRemindersWorker，国内 ROM 杀后台后仍恢复提醒

### 0.2 范围（IN）

- 数据层：单表 `timeline_items` + 3 组 DataStore key（v5 → v6 destructive，参考 memory `feedback_dev_db_data.md`）
- 5 个屏幕：`TimelineScreen` / `AddTaskScreen` / `AddCourseScreen` / `TaskDetailScreen` / `CourseSeriesEditScreen`
- 3 个支撑屏：`CourseWeekGridScreen`（录入完整一周课表后的网格预览/编辑）/ `CourseSeriesListScreen`（系列管理列表）/ `TimetableEditorScreen`（作息表编辑）
- 2 个模态：`SemesterStartModal` / `DeleteScopeDialog`
- 通知系统：2 个 NotificationChannel + ReminderWorker + RescheduleRemindersWorker + BootCompletedReceiver + Permission 引导
- 课程颜色：`hash(title) → phosphor 派生 6-8 色` 自动派色，`colorOverride` 字段预留但 P4 不写

### 0.3 范围（OUT，留给 P5 / P6）

- ICS 文件导入 — 留 P5
- 教务系统 WebView 爬取 — 留 P5
- 单/双周课程支持 — 留 P5（如 ICS 数据有 `BYDAY=MO/TU` 复杂规则时）
- 提醒颗粒度 per-item 覆写（自定义 `remindersMinBefore`）— 留 P6
- 作息表跨学期切换 / 多套作息表预设 — 留 P6
- COURSE 自动签到 / 出勤记录 — 不做
- KB / Chat 跨特性集成（`kbEntryIds: List<Long>` 字段保留为 JSON `"[]"`）— 留 P6
- 月视图 / 周视图 / 列表视图切换（仅做单日 + 7 日 strip）— 留 P6
- 重复事件用户级（"每周三 19:00 健身"）— 留 P5/P6（用户可手动展开多条 CUSTOM）

---

## 1. 数据模型

### 1.1 Room 升级

`AppDatabase.VERSION` 从 `5` 升到 `6`，**destructive**。`AppDatabase` 注册 1 个新 entity（`TimelineItemEntity`）+ 1 个新 DAO（`TimelineDao`）。

### 1.2 实体定义

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.*

enum class TimelineType { COURSE, TASK, CUSTOM }
enum class TimelineSource { MANUAL, IMPORTED_ICS, IMPORTED_PORTAL, FROM_CHAT } // P4 只写 MANUAL

@Entity(
    tableName = "timeline_items",
    indices = [
        Index("startAt"),
        Index("seriesId"),
        Index(value = ["endAt", "type"]),
    ],
)
data class TimelineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val type: TimelineType,
    val title: String,
    val description: String?,             // 选填，纯文本（不上 Markdown）

    val startAt: Long,                    // epoch ms
    val endAt: Long?,                     // TASK == null（DDL 单点）；COURSE/CUSTOM 有值

    val isDone: Boolean = false,          // COURSE 永远 false（业务约束）
    val doneAt: Long? = null,

    val location: String? = null,         // COURSE / CUSTOM 选填
    val instructor: String? = null,       // COURSE only
    val notes: String? = null,            // COURSE only

    // 系列 / 课节字段（COURSE 专用，TASK / CUSTOM 全 null）
    val seriesId: Long? = null,           // 同一门课 N 行共享 ID（用户在录入时由 use case 分配）
    val periodIndex: Int? = null,         // 节次起始（如 1-3 节连段存 1）
    val periodEndIndex: Int? = null,      // 节次结束（如 1-3 节连段存 3）
    val weekdayCode: Int? = null,         // 1..7 (Mon=1, Sun=7)
    val weekIndexInSemester: Int? = null, // 1..N（"第 X 周"，便于系列内排序与展示）

    val colorOverride: Int? = null,       // ARGB int；P4 不写，由 hash(title) 派色

    val sourceType: TimelineSource = TimelineSource.MANUAL,
    val sourceExternalId: String? = null, // P5 用
    val kbEntryIdsJson: String = "[]",    // 预留 P6 跨特性挂载，存 JSON 数组

    val createdAt: Long,
    val updatedAt: Long,
)
```

### 1.3 Room DAO 关键方法

```kotlin
@Dao
interface TimelineDao {

    // ---- Insert / Update / Delete ----

    @Insert
    suspend fun insertAll(items: List<TimelineItemEntity>): List<Long>

    @Update
    suspend fun update(item: TimelineItemEntity)

    @Query("UPDATE timeline_items SET startAt = :startAt, endAt = :endAt, updatedAt = :now WHERE id = :id")
    suspend fun updateTime(id: Long, startAt: Long, endAt: Long?, now: Long)

    @Query("UPDATE timeline_items SET isDone = :done, doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    @Query("DELETE FROM timeline_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM timeline_items WHERE seriesId = :seriesId")
    suspend fun deleteSeriesAll(seriesId: Long)

    @Query("DELETE FROM timeline_items WHERE seriesId = :seriesId AND endAt > :now")
    suspend fun deleteSeriesFuture(seriesId: Long, now: Long)

    // ---- 系列管理 ----

    @Query("SELECT MAX(seriesId) FROM timeline_items")
    suspend fun maxSeriesId(): Long?

    @Query("UPDATE timeline_items SET title = :title, instructor = :instructor, location = :location, notes = :notes, updatedAt = :now WHERE seriesId = :seriesId")
    suspend fun updateSeriesAttributes(seriesId: Long, title: String, instructor: String?, location: String?, notes: String?, now: Long)

    @Query("""
        SELECT seriesId, MIN(title) AS title, MIN(instructor) AS instructor, MIN(location) AS location,
               COUNT(*) AS count, MIN(weekIndexInSemester) AS minWeek, MAX(weekIndexInSemester) AS maxWeek
        FROM timeline_items
        WHERE type = 'COURSE' AND seriesId IS NOT NULL
        GROUP BY seriesId
        ORDER BY MIN(startAt) ASC
    """)
    fun observeCourseSeriesList(): Flow<List<CourseSeriesSummary>>

    // ---- 时间轴查询 ----

    @Query("SELECT * FROM timeline_items WHERE startAt < :endExclusive AND COALESCE(endAt, startAt) >= :startInclusive ORDER BY startAt ASC")
    fun observeItemsInRange(startInclusive: Long, endExclusive: Long): Flow<List<TimelineItemEntity>>

    @Query("""
        SELECT date(startAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
        FROM timeline_items
        WHERE startAt >= :startInclusive AND startAt < :endExclusive
        GROUP BY day
    """)
    fun observeDayCounts(startInclusive: Long, endExclusive: Long): Flow<List<DayCount>>

    // ---- WorkManager 重排用 ----

    @Query("SELECT * FROM timeline_items WHERE isDone = 0 AND startAt >= :now AND startAt < :until")
    suspend fun getUpcomingItems(now: Long, until: Long): List<TimelineItemEntity>

    // ---- 改作息表后批量重算 future COURSE ----

    @Query("SELECT * FROM timeline_items WHERE type = 'COURSE' AND COALESCE(endAt, startAt) > :now")
    suspend fun getFutureCourses(now: Long): List<TimelineItemEntity>
}

data class CourseSeriesSummary(
    val seriesId: Long,
    val title: String,
    val instructor: String?,
    val location: String?,
    val count: Int,
    val minWeek: Int,
    val maxWeek: Int,
)

data class DayCount(val day: String, val count: Int)
```

### 1.4 DataStore Keys

```kotlin
// data/local/datastore/SemesterPreferences.kt
val SEMESTER_START_DATE = stringPreferencesKey("semester_start_date") // ISO "2026-09-01"，未设 = null

// data/local/datastore/TimetablePreferences.kt
val TIMETABLE_PERIODS_JSON = stringPreferencesKey("timetable_periods_json")
// JSON: [{"index":1,"startHHmm":"08:00","endHHmm":"08:45"}, ...]
// 首次读取若为空 → 写入 DefaultTimetable.SEED_JSON

// data/local/datastore/NotifPreferences.kt
val NOTIF_COURSE_ENABLED = booleanPreferencesKey("notif_course_enabled")  // default true
val NOTIF_TASK_ENABLED   = booleanPreferencesKey("notif_task_enabled")    // default true
val NOTIF_CUSTOM_ENABLED = booleanPreferencesKey("notif_custom_enabled")  // default true
val NOTIF_BANNER_DISMISSED = booleanPreferencesKey("notif_banner_dismissed_session") // 单会话
```

### 1.5 默认作息表（seed）

沿用 spec §4.4 的 13 节默认表：

```kotlin
object DefaultTimetable {
    val PERIODS = listOf(
        Period(1,  "08:00", "08:45"),
        Period(2,  "08:50", "09:35"),
        Period(3,  "09:55", "10:40"),
        Period(4,  "10:45", "11:30"),
        Period(5,  "11:35", "12:20"),
        Period(6,  "13:20", "14:05"),
        Period(7,  "14:10", "14:55"),
        Period(8,  "15:15", "16:00"),
        Period(9,  "16:05", "16:50"),
        Period(10, "16:55", "17:40"),
        Period(11, "18:30", "19:15"),
        Period(12, "19:20", "20:05"),
        Period(13, "20:10", "20:55"),
    )
    val SEED_JSON: String  // 序列化后写入 DataStore
}

data class Period(val index: Int, val startHHmm: String, val endHHmm: String)
```

---

## 2. 包结构

```
feature/timeline/
  ui/
    TimelineScreen.kt              // 主视图（默认 today）
    DayStripBar.kt                 // 7 日小色条（dot density）
    TimelineAxis.kt                // 07:00-23:30 纵轴 + 刻度
    TimelineBubble.kt              // 单个气泡 + 状态色 + pulse
    NowIndicator.kt                // "此刻"红线 + "$ now: HH:mm" 标签
    AddTaskScreen.kt               // type=TASK | CUSTOM 切换
    AddCourseScreen.kt             // 列表 form
    CourseWeekGridScreen.kt        // 7×N 网格预览（录完后调用）
    TaskDetailScreen.kt            // 详情 + 编辑
    CourseSeriesListScreen.kt      // 系列聚合列表
    CourseSeriesEditScreen.kt      // 系列改名 / 批量删
    SemesterStartModal.kt          // 首次录课弹学期起始日
    DeleteScopeDialog.kt           // "全部 / 仅未来"
    NotifPermissionBanner.kt       // 顶部权限 banner
  vm/
    TimelineViewModel.kt
    AddTaskViewModel.kt
    AddCourseViewModel.kt
    TaskDetailViewModel.kt
    CourseSeriesListViewModel.kt
    CourseSeriesEditViewModel.kt
  model/
    TimelineUiState.kt
    AddCourseUiState.kt
    AddTaskUiState.kt
    BubbleState.kt                 // sealed class 13 种状态
    CourseFormDraft.kt             // 录入草稿（多 weekday × 多 period × weekRange）

domain/timeline/
  AddTaskUseCase.kt
  AddCourseSeriesUseCase.kt        // 展开为 N 行
  UpdateItemUseCase.kt
  UpdateCourseSeriesUseCase.kt
  DeleteItemUseCase.kt
  DeleteCourseSeriesUseCase.kt     // 全部 / 仅未来
  ToggleDoneUseCase.kt
  RecalculateCoursesAfterTimetableChangeUseCase.kt
  ScheduleRemindersUseCase.kt
  CancelRemindersUseCase.kt
  RescheduleAllUpcomingUseCase.kt
  ComputeBubbleStateUseCase.kt     // 纯函数式（无 IO），单测覆盖 13 个分支
  CheckCourseConflictUseCase.kt    // 录课时同周同节冲突检测
  TimelineRepository.kt            // interface

data/
  local/
    entity/TimelineItemEntity.kt
    db/TimelineDao.kt
    db/AppDatabase.kt              // v5 → v6
    datastore/{Semester,Timetable,Notif}Preferences.kt
  repository/TimelineRepositoryImpl.kt

core/
  workers/
    ReminderWorker.kt              // 单条提醒触发
    RescheduleRemindersWorker.kt   // 启动 / 重启时重排
    BootCompletedReceiver.kt       // BOOT_COMPLETED → enqueue Reschedule
  notification/
    NotificationChannels.kt        // TIMELINE_REMINDERS / TIMELINE_OVERDUE
    TimelineNotifier.kt            // 构造 + post 通知
  util/
    SemesterTimeMapper.kt          // (semesterStart, weekIdx, weekday, period) → (startAt, endAt)
    CourseColorPalette.kt          // hash(title) → 6-8 phosphor 派生色

feature/settings/ui/
  TimetableEditorScreen.kt         // 13 节列表 inline TimePicker
  SemesterSettingsScreen.kt        // 学期起始日 DatePicker
  NotifSettingsScreen.kt           // 3 个全局开关 + 权限按钮
```

**导航 routes 新增**：

```kotlin
object NavRoutes {
    // ...existing...

    const val TIMELINE_ADD_TASK   = "timeline/add-task"
    const val TIMELINE_ADD_COURSE = "timeline/add-course"
    const val TIMELINE_DETAIL     = "timeline/detail/{itemId}"
    fun timelineDetail(itemId: Long) = "timeline/detail/$itemId"
    const val TIMELINE_COURSE_LIST = "timeline/course-list"
    const val TIMELINE_COURSE_SERIES_EDIT = "timeline/course-series/{seriesId}"
    fun timelineCourseSeriesEdit(seriesId: Long) = "timeline/course-series/$seriesId"
    const val TIMELINE_WEEK_GRID  = "timeline/week-grid"

    const val SETTINGS_TIMETABLE = "settings/timetable"
    const val SETTINGS_SEMESTER  = "settings/semester"
    const val SETTINGS_NOTIF     = "settings/notif"
}
```

DeepLink scheme：`personalstudio://timeline/detail/{id}`（通知 PendingIntent 用）。

---

## 3. 用户流程

### 3.1 首次录入课程（onboarding 路径）

```
Timeline tab → "+" FAB → BottomSheet [加 DDL/事件] [录入课程]
  ↓ (点 [录入课程])

[首次] semester_start_date == null
  → 弹 SemesterStartModal
  → DatePicker 选学期起始日（约束：必须为周一；如选了非周一，回退到该日所在周的周一）
  → 存 DataStore SEMESTER_START_DATE
  → 关闭 modal

AddCourseScreen — 列表 form：
  字段                     UI
  ---                      ---
  课名（必填）              TextField
  老师                     TextField
  地点                     TextField
  备注                     多行 TextField
  星期                     多选 Chip [一 二 三 四 五 六 日]
  节次起 - 节次止           两个数字下拉（1..maxPeriod，止 ≥ 起，连续段）
  周次起 - 周次止           两个数字下拉（1..maxWeek，止 ≥ 起，连续段）

  说明：节次和周次都只支持连续范围。非连续场景（"高数第 1-2 节 + 第 5-6 节"）
        请录入两个独立 series；学期中段加课（"第 8 周开始的选修课"）正常 weekRange=[8, 16]。

  ↓ 点击 "保存"
CheckCourseConflictUseCase 扫描 (weekdays × periods × weekRange) 是否与现有 COURSE 重叠
  → 有冲突 → 表单上方显示 warning chip "周二第 3 节已有课：高数（仍可保存）" — 不阻断
AddCourseSeriesUseCase 展开：
  seriesId = (max(seriesId) ?: 0) + 1
  for week in weekRange:
    for weekday in weekdays:
      startAt = mapPeriodToStartAt(semesterStart, week, weekday, periodStart)
      endAt   = mapPeriodToEndAt(semesterStart, week, weekday, periodEnd)
      insertRow(...)
  返回 (seriesId, count)
  ↓
[首次保存] 触发 POST_NOTIFICATIONS 请求（SDK_INT >= 33）
  授权 → ScheduleRemindersUseCase 排所有 future 行的 [10] 分钟前 worker
  拒绝 → 跳过 schedule，显示顶栏 banner

绿色 Toast "已添加 X 节高数"，留在 AddCourseScreen 让用户继续录下一门课
顶栏 [完成] 返回 Timeline
```

### 3.2 添加单条 DDL / 自定义事件

```
Timeline tab → "+" FAB → BottomSheet → [加 DDL/事件] → AddTaskScreen

顶部 segmented control: [DDL] [事件]
字段（DDL=TASK）:
  - 标题（必填）
  - 描述（选填）
  - 截止时间（DateTimePicker）
字段（事件=CUSTOM）:
  - 标题（必填）
  - 描述（选填）
  - 起止时间（两个 DateTimePicker）— 校验 endAt > startAt
  - 地点（选填）

↓ 保存
AddTaskUseCase → insertOne(...)
ScheduleRemindersUseCase 按 type 默认值排：
  TASK   → [1440, 120, 30] 分钟前 + 0 分钟（"已过期"）共 4 个 worker
  CUSTOM → [30] 分钟前 1 个 worker

[首次创建] 触发 POST_NOTIFICATIONS 请求
返回 Timeline
```

### 3.3 浏览（TimelineScreen）

```
顶栏：
  [<]  2026-05-01 周五  [>]    📅(月份选择)    ⚙(Settings)
  ─────────────────────────────────────────
  [7-day strip]: 4-25 4-26 4-27 4-28 4-29 4-30 5-01
                  ·    ··   .    ··   ·    ·    ···
                                                 ↑ today high

主体（垂直滚动）：
  07:00 ─
  08:00 ─ [bubble: 高数 08:00-09:35]
  09:00 ─
  10:00 ─ [bubble: 大物 09:55-11:30]
  ...
  14:30 ─━━━━━ "$ now: 14:32" ━━━━━━━ (红横线，仅 today)
  ...
  18:00 ─ [bubble: 作业 DDL 18:00] (TASK，当前临近态)
  ...
  23:30 ─

气泡内容（紧凑）:
  COURSE: title (1 行) + location (1 行小字)
  TASK:   title (1 行) + "DDL HH:mm" (1 行小字) [✓ 完成 button 右下角]
  CUSTOM: title (1 行) + location/时长 (1 行小字) [✓ 完成 button]

气泡交互:
  点击 → TaskDetailScreen
  右滑（仅 TASK/CUSTOM）→ toggle done
  COURSE 气泡无 swipe
```

**渲染细节**：
- Y 坐标线性映射：`y = (startAt 时分 - 07:00) * pxPerHour`
- 时间轴范围固定 07:00-23:30（16.5 小时；早于/晚于此区间的 item 在该日被裁切——边界情况：起 < 07:00 用红色边角警示标，止 > 23:30 同理）
- 气泡同时段重叠时左右并列分两列（最多 2 列；3+ 列时缩窄字体）
- 7-day strip 锚定显示日所在的周（周一到周日，ISO 周）；用户切到下周某天时整条 strip 跟着滚到下周
- 7-day strip dot 密度：count == 0 → 无 dot；1-2 → "·"；3-4 → "··"；5+ → "···"
- "此刻"红线每 60s tick 重算位置（`produceState` + `delay(60_000)`）
- 状态机也每 60s tick 重算（同上）

**空日**：完整空轴 + 中央水印 "今日无安排"（phosphor green，低饱和度）。

### 3.4 编辑

**TaskDetailScreen**（点击气泡进入）：

显示：type 标签 / title / 时间 / description / location / instructor / notes / 状态指示器

操作栏（按 type 不同）：

| type | 按钮 |
|---|---|
| COURSE | [改时间(本次)] [改地点(本次)] [删除本次] |
| COURSE bottom hint | "改名 / 改老师 / 改整学期，请到 ⚙ Settings → 课程列表" |
| TASK | [✓ 完成 / ↻ 取消完成] [编辑] [删除] |
| CUSTOM | [✓ 完成 / ↻ 取消完成] [编辑] [删除] |

编辑 TASK / CUSTOM → 复用 `AddTaskScreen`，预填字段，保存时触发 `UpdateItemUseCase` + 取消旧 reminder + 重 schedule。

**CourseSeriesListScreen**（Settings → 课程列表）：

```
$ ls courses/
  高数        32 节   第 1-16 周   →
  大物        16 节   第 1-16 周   →
  线代        16 节   第 1-16 周   →
  ...
```

点击 → `CourseSeriesEditScreen`：

可编辑：title / instructor / location / notes（保存调用 `UpdateCourseSeriesUseCase` 批量更新所有 seriesId 行）

不可编辑：weekdays / periods / weekRange（hint："如需调整时间，请删除并重新创建"）

操作：[删除整个系列] → 弹 `DeleteScopeDialog`

```
DeleteScopeDialog:
  "高数 共 32 节"
  ( ) 删除全部（包括已上的 X 节）
  ( ) 仅删除未来（保留 X 节历史）
  [取消]  [确认]
```

### 3.5 通知触发

**ReminderWorker.doWork()** 流程：

```
inputData: itemId: Long, minBefore: Int, isOverdue: Boolean

1. item = repo.findById(itemId)
   if item == null → return Result.success() (已删，silent skip)
2. if isOverdue:
     if item.isDone → skip
     post 通知 (channel=TIMELINE_OVERDUE):
       title: "已过期 · ${item.title}"
       text:  "DDL 已过 ${formatDuration(now - item.startAt)}"
   else:
     if item.isDone → skip
     enabled = when(item.type) {
       COURSE -> notifPrefs.courseEnabled
       TASK   -> notifPrefs.taskEnabled
       CUSTOM -> notifPrefs.customEnabled
     }
     if !enabled → skip
     post 通知 (channel=TIMELINE_REMINDERS):
       title: item.title
       text: 按 type:
         COURSE: "${minBefore} 分钟后 · ${item.location ?: ""}"
         TASK:   "DDL 还剩 ${humanDuration(minBefore)}"
         CUSTOM: "${minBefore} 分钟后 · ${item.location ?: ""}"
3. PendingIntent → deeplink personalstudio://timeline/detail/${itemId}
```

**ScheduleRemindersUseCase**（创建或编辑后调用）：

```
data class ReminderSlot(val minBefore: Int, val isOverdue: Boolean)
  // minBefore=0, isOverdue=true 表示 DDL 当刻补发；其余为提前 N 分钟

slotsFor(type) = when(type) {
  COURSE -> [Slot(10, false)]
  TASK   -> [Slot(1440, false), Slot(120, false), Slot(30, false), Slot(0, true)]
  CUSTOM -> [Slot(30, false)]
}

1. CancelRemindersUseCase(itemId) — 先取消该 item 全部已排 worker
2. for slot in slotsFor(item.type):
     fireAt = if (slot.isOverdue) item.startAt else item.startAt - slot.minBefore * 60_000
     if fireAt <= now → skip （fireAt 已过去，不排）
     workName = "reminder_${itemId}_${slot.minBefore}_${slot.isOverdue}"
     OneTimeWorkRequestBuilder<ReminderWorker>()
       .setInitialDelay(fireAt - now, MILLISECONDS)
       .setInputData(itemId, slot.minBefore, slot.isOverdue)
       .build()
     enqueueUniqueWork(workName, REPLACE, request)
```

**注**：当用户创建一条 startAt 已过去的 item（如手动补录昨天的 DDL）时，所有 fireAt ≤ now → 全部 skip，不排任何 reminder（也不补发"已过期"通知，避免轰炸）。

**RescheduleRemindersWorker**（启动 / 重启触发）：

```
1. items = dao.getUpcomingItems(now, now + 24h)
2. for item in items:
     CancelRemindersUseCase(item.id)
     ScheduleRemindersUseCase(item)
```

**触发点**：
- `BootCompletedReceiver.onReceive` → `WorkManager.enqueue(OneTimeWorkRequest<RescheduleRemindersWorker>)`
- `MainActivity.onCreate` → 同上（防国内 ROM 杀后台后 BOOT_COMPLETED 不触发）

**POST_NOTIFICATIONS 引导**：
- 在 `AddTaskViewModel` / `AddCourseViewModel` 暴露 `requestNotifPermissionEvent: SharedFlow<Unit>`
- 保存成功 + `Build.VERSION.SDK_INT >= 33` + 权限未授 → 发 emit
- UI 用 `rememberLauncherForActivityResult(RequestPermission())` 接住，调用 `permissionLauncher.launch(POST_NOTIFICATIONS)`
- 拒绝 → 设置 `NOTIF_BANNER_DISMISSED = false`，Timeline 顶部 `NotifPermissionBanner` 显示
- 用户点 banner → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` Intent
- 点 X → `NOTIF_BANNER_DISMISSED = true`（单会话），下次冷启动若仍未授权再现

---

## 4. 状态机

`ComputeBubbleStateUseCase(item, now): BubbleState` 是纯函数（无 IO），便于单测。

```kotlin
sealed class BubbleState {
    object CourseUpcoming : BubbleState()      // > now+15min
    object CourseImminent : BubbleState()      // now+15min ≥ startAt > now
    object CourseInProgress : BubbleState()    // startAt ≤ now < endAt
    object CoursePast : BubbleState()          // endAt ≤ now

    object TaskUpcoming : BubbleState()        // !isDone, > now+2h
    object TaskImminent : BubbleState()        // !isDone, now+2h ≥ startAt > now
    object TaskOverdue : BubbleState()         // !isDone, startAt ≤ now
    object TaskDone : BubbleState()            // isDone

    object CustomUpcoming : BubbleState()      // !isDone, > now+30min
    object CustomImminent : BubbleState()      // !isDone, now+30min ≥ startAt > now
    object CustomInProgress : BubbleState()    // !isDone, startAt ≤ now < endAt
    object CustomOverdue : BubbleState()       // !isDone, endAt ≤ now
    object CustomDone : BubbleState()          // isDone
}
```

### 4.1 视觉映射

| BubbleState | 底色 | 边框 | 透明度 | 动画 |
|---|---|---|---|---|
| CourseUpcoming | hash 派生色 | 单层正常 | 100% | 无 |
| CourseImminent | hash 派生色 | 高亮粗边框 | 100% | scale pulse 2s |
| CourseInProgress | hash 派生色 | 双层呼吸边框 | 100% | 边框透明度循环 |
| CoursePast | hash 派生色 | 无 | 50% | 无 |
| TaskUpcoming | phosphor 黄 #C8B400 | 单层 | 100% | 无 |
| TaskImminent | phosphor 黄 | 单层 | 100% | scale pulse |
| TaskOverdue | phosphor 红 #FF4444 | 双层 | 100% | scale pulse 加速 |
| TaskDone | phosphor 绿 #4FFF4F | 无 | 80% | 文字删除线 |
| CustomUpcoming | phosphor 紫 #B488FF | 单层 | 100% | 无 |
| CustomImminent | phosphor 紫 | 单层 | 100% | scale pulse |
| CustomInProgress | phosphor 紫 | 双层呼吸 | 100% | 边框呼吸 |
| CustomOverdue | phosphor 红 | 双层 | 100% | scale pulse |
| CustomDone | phosphor 绿 | 无 | 80% | 文字删除线 |

具体颜色值最终对齐 `docs/design/terminal/spec.md` 的 phosphor palette。

### 4.2 课程颜色派生（`CourseColorPalette`）

```kotlin
object CourseColorPalette {
    // 6 个 phosphor green 派生色相，保持饱和度统一在 65%
    private val HUES = listOf(120f, 140f, 100f, 160f, 80f, 180f) // green ± 偏移

    fun colorFor(courseTitle: String): Color {
        val h = HUES[abs(courseTitle.hashCode()) % HUES.size]
        return Color.hsl(hue = h, saturation = 0.65f, lightness = 0.55f)
    }
}
```

---

## 5. Settings 增项

`SettingsScreen` 现有项目下方追加（按现有 REPL 风格分组）：

```
$ 学期设置
  当前学期起始日：2026-09-01    [→ 选择]
  
$ 作息时间表
  默认 13 节                     [→ 编辑]

$ 通知
  □ 课程提醒（默认开）
  □ DDL 提醒（默认开）
  □ 自定义事件提醒（默认开）
  ──
  通知权限：[已授权 / 未授权 → 去设置]

$ 课程列表
  → CourseSeriesListScreen
```

**TimetableEditorScreen**（点击"作息时间表 → 编辑"）：

```
$ ls timetable/
  1   08:00 ─ 08:45    (点击行 → inline TimePicker)
  2   08:50 ─ 09:35
  3   09:55 ─ 10:40
  ...
  13  20:10 ─ 20:55

  [+ 添加节次]   [- 删除最后一节]   [恢复默认]   [保存]
```

保存时：
- 校验：每节 endHHmm > startHHmm；前节 endHHmm ≤ 后节 startHHmm（不允许时段重叠/倒退）
- 弹确认 dialog "改作息表会更新所有未来课程时间，确认？"
- 确认后写 DataStore + 触发 `RecalculateCoursesAfterTimetableChangeUseCase`：
  ```
  Room transaction:
    futureCourses = dao.getFutureCourses(now)
    for each:
      newStart = mapPeriodToStartAt(semesterStart, weekIndex, weekday, periodIndex)
      newEnd   = mapPeriodToEndAt(semesterStart, weekIndex, weekday, periodEndIndex)
      dao.updateTime(id, newStart, newEnd, now)
  // 事务外（避免长事务持锁），逐条重排提醒：
  for each updated id:
    CancelRemindersUseCase(id)
    ScheduleRemindersUseCase(updated_item)
  ```
  事务失败 → 回滚，DataStore 不写，Toast "更新失败"。

---

## 6. 错误处理

| 场景 | 行为 |
|---|---|
| 学期起始日未设 + 用户进 AddCourseScreen | 拦截弹 SemesterStartModal，未选不让继续；选了非周一日期自动回退到该日所在周一 |
| 录课时同周同节冲突 | 不阻断保存，form 顶 warning chip "周X 第N节已有课：XX（仍可保存）" |
| weekRange 颠倒 / 起 > 止 | inline 校验，禁用保存按钮 |
| weekdays 空 / periods 空 | 禁用保存按钮 |
| AddCourseSeriesUseCase 展开后行数 == 0 | "至少需要 1 节课，请检查输入" |
| 改作息表起止反转 | inline 校验阻止 |
| 改作息表重算事务失败 | rollback，Toast "更新失败"，DataStore 不写 |
| 改作息表时删除某节，但有 future 课程引用该 periodIndex | 保存按钮 disable + 提示 "节次 N 仍被 X 门未来课程使用，请先删除或调整这些课程"；点提示跳转 CourseSeriesListScreen |
| WorkManager enqueue 抛异常 | log + Toast "提醒可能未生效"，不阻断 item 保存 |
| ReminderWorker 触发但 item 已删 | silent return success |
| ReminderWorker 触发但 isDone | silent return success |
| ReminderWorker 触发但全局开关关 | silent return success |
| 通知权限被拒 | 仍允许加 item，banner 持续提示，下次冷启动且未授权时再现 |
| 删除整个系列 | DeleteScopeDialog 选 "全部 / 仅未来"，取消则不动 |
| AddTask 时 endAt < startAt（CUSTOM）| inline 校验阻止 |
| TimelineScreen 切到 09 月之类的远期空日 | 显示空轴 + "今日无安排" |
| `personalstudio://timeline/detail/{id}` 但 id 不存在 | TimelineScreen 弹 Toast "条目已删除" |
| 学期跨年（startDate=2026-12-30，week 5 跨到 2027-01）| `LocalDate.plusWeeks().plusDays()` 自然处理，无特殊代码 |

---

## 7. 测试策略

### 7.1 Domain 单测（重点 ~25 个）

- `AddCourseSeriesUseCase`:
  - 周一周三 × 1-16 周 = 32 行
  - 跨月边界（学期起始 8-30，至 9-2 周一）
  - 学期起始日选了周二自动回退到上周一
  - 节次连续段 1-3：1 行，periodIndex=1, periodEndIndex=3，startAt=第1节起、endAt=第3节止
  - 节次单点 1-1：1 行，periodIndex=periodEndIndex=1
  - 用户想要非连续节段（如第 1-2 节 + 第 5-6 节同一门课）→ 录入两次（两个 series）
  - weekRange [3, 3] 单周 = 仅展开 weekdays 数量
  - DST 边界（如果启用，但中国时区无 DST，仅留注释说明）
- `RecalculateCoursesAfterTimetableChangeUseCase`:
  - 只更新 endAt > now 的 COURSE 行
  - TASK / CUSTOM 不动
  - 改第 3 节时间，第 1 节高数（periodIndex=1, periodEndIndex=2）endAt 不变
  - 改第 3 节时间，连段第 1-3 节高数 endAt 跟随
- `ComputeBubbleStateUseCase`：13 个 BubbleState 各 1 测 + 边界（now == startAt、now == endAt、now == startAt - 15min 等）
- `ScheduleRemindersUseCase`:
  - mock WorkManager（用 fake 实现），断言 unique work name 数量与格式
  - 已过去的时间点（fireAt ≤ now）跳过
  - REPLACE 策略覆盖旧 worker
- `ToggleDoneUseCase`:
  - done → cancel reminders
  - undone + future → re-enqueue
  - undone + past → 不 re-enqueue
- `DeleteCourseSeriesUseCase`:
  - 全部 → DELETE WHERE seriesId
  - 仅未来 → DELETE WHERE seriesId AND endAt > now，过去保留
- `CheckCourseConflictUseCase`:
  - 同周同节同 weekday 与现有 COURSE 冲突 → 返回冲突列表
  - 同 seriesId 自身不算冲突（但 P4 录入时 seriesId 还未生成，对比时只看时间）

### 7.2 Repository 测试

- Room in-memory + `runTest`
- `observeItemsInRange`：跨 00:00 边界、跨 23:59:59 边界
- `observeDayCounts`：UTC vs localtime 日期边界（用 SQLite 的 `'localtime'` 修饰符）
- `observeCourseSeriesList`：聚合正确性
- `getUpcomingItems`：过滤已完成 + 时间窗口

### 7.3 ViewModel 测试

- `TimelineViewModel`：
  - 切日 → state.items 切换
  - 切日 → 7-day strip dot 数同步
  - 选未来日 → "此刻"线隐藏
- `AddCourseViewModel`：
  - weekdays 空时 saveEnabled = false
  - 冲突检测 → conflicts state 暴露
- `TaskDetailViewModel`:
  - toggle done → state 更新 + reminder cancel 调用
  - delete → 导航事件 emit

### 7.4 UI 测试（最小化）

仅 1 个 `TimelineScreenTest`：
- 注入 fake repository 提供 3 个 items（COURSE InProgress / TASK Imminent / TASK Done）
- 断言：3 个气泡渲染、"$ now:" 标签存在、 done 气泡有删除线 modifier

不写：CourseSeriesEdit / Settings / AddTask 的 UI test。

### 7.5 真机 DoD（每 phase tag 完成时跑）

```
[ ] 录入 16 周课表后时间线显示当天的课，气泡颜色按 hash 分布
[ ] 7-day strip 显示一周内的"有事"密度点
[ ] 切日（左右滑）→ 时间线刷新流畅
[ ] 上课前 14 分钟 → COURSE 气泡显示"临近"高亮 + pulse
[ ] 上课中 → COURSE 气泡显示"进行中"双边框呼吸
[ ] 课程过完 → 50% 不透明度
[ ] 加 1 个 DDL 设在 32 分钟后 → 30 分钟前收到通知
[ ] 加 1 个 DDL 设在 1 分钟后，不标完成 → 1 分钟后收到"已过期"通知
[ ] 标 TASK 完成 → 气泡变绿删除线、未来 reminder worker 取消
[ ] 改作息表第 3 节起止 5 分钟 → 未来 16 周高数全部位移 5 分钟
[ ] 删除整个系列选"仅未来" → 学期前 X 周历史保留、之后清空
[ ] 重启手机 → 通知仍按时触发
[ ] 拒绝通知权限 → Timeline 顶部 banner 持续显示，点 X 后本会话隐藏，重启 App 再现
[ ] 通知点击 → 跳转到对应 TaskDetailScreen
[ ] 截图归档至 docs/superpowers/checkpoints/P4/
```

---

## 8. 阶段切片（5 个 phase tag）

| phase | 内容 | tag | 工作量 |
|---|---|---|---|
| P4-Phase 1 | DB v6 schema + DAO + DataStore + Repository + 默认作息表 seed + SemesterTimeMapper + CourseColorPalette | `p4-data` | ~1 天 |
| P4-Phase 2 | AddCourseScreen + AddTaskScreen + SemesterStartModal + AddCourseSeriesUseCase + AddTaskUseCase + CheckCourseConflictUseCase + 表单 VM | `p4-input` | ~2 天 |
| P4-Phase 3 | TimelineScreen + TimelineAxis + TimelineBubble + DayStripBar + NowIndicator + ComputeBubbleStateUseCase + 7-day strip + 60s tick | `p4-view` | ~2 天 |
| P4-Phase 4 | TaskDetailScreen + CourseSeriesListScreen + CourseSeriesEditScreen + DeleteScopeDialog + UpdateItemUseCase + UpdateCourseSeriesUseCase + DeleteCourseSeriesUseCase + ToggleDoneUseCase + TimetableEditorScreen + RecalculateCoursesAfterTimetableChangeUseCase | `p4-edit` | ~2 天 |
| P4-Phase 5 | NotificationChannels + ReminderWorker + RescheduleRemindersWorker + BootCompletedReceiver + ScheduleRemindersUseCase + CancelRemindersUseCase + Permission flow + NotifPermissionBanner + NotifSettingsScreen | `p4-notify` | ~2 天 |
| 收尾 | 真机 DoD 全跑 + 截图归档 + PR + memory 更新 | `p4-timeline-mvp` | ~0.5 天 |

每 phase tag 之前必须：build 绿 + 该 phase 单测全过 + 真机走该 phase 子集 DoD + 截图归档。

---

## 9. 风险登记（仅 P4 新增风险）

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 国内 ROM 杀后台导致通知不触发 | 高 | 高 | MainActivity.onCreate + BootCompletedReceiver 双触发 RescheduleRemindersWorker；"通知未到"在 banner 给用户引导自启动设置 |
| WorkManager `setInitialDelay` 长延迟（24h+）精度漂移 | 中 | 中 | 接受 ±15 分钟漂移；过期补发可冗余兜底 |
| POST_NOTIFICATIONS 拒绝后流程 | 中 | 中 | banner 持续提示 + 仍允许 item 保存（不强制） |
| 课程颜色 hash 碰撞导致两门课同色 | 中 | 低 | 6 个色相 + 一周课通常 ≤ 8 门，碰撞接受；后期可加 colorOverride |
| 改作息表事务失败导致 partial update | 低 | 高 | Room transaction 包住 update，失败回滚；再加 try/catch + Toast |
| 学期跨年 DST | 低 | 低 | 中国时区无 DST，无影响；代码用 `LocalDateTime` + 系统 ZoneId 避免 |
| 7-day strip 数据库查询频繁 | 低 | 低 | observeDayCounts 走 GROUP BY day，查询窗口固定 7 天；`distinctUntilChanged` 防抖 |
| 60s tick 与 BubbleState 抖动 | 低 | 低 | 状态机基于"now + 阈值"判断，无抖动；tick 仅触发重绘 |
| 用户先录课后改学期起始日 | 中 | 中 | 学期起始日改后弹"是否重算所有 future 课程？"，确认则跑 RecalculateCoursesAfterTimetableChangeUseCase |
| 节次起=止+周次起=止 单点单周课，dao 插入 1 行 | 低 | 低 | 正确行为，无需特殊处理 |

---

## 10. 与 P3 / P5 的接口预留

**P3 → P4**：
- 暂无主动入口（spec §0.3 已声明集成留 P6）
- `kbEntryIdsJson` 字段保留为 `"[]"`，未来 KB 条目可挂载 timeline_item

**P4 → P5**：
- `sourceType: TimelineSource` 已经枚举 `IMPORTED_ICS / IMPORTED_PORTAL`，P5 直接写
- `sourceExternalId` 字段供 P5 教务系统课程 ID 用，重新同步时 `userModified` 行为按 spec §4.5 逻辑处理（P4 不需要 `userModified` 字段，P5 引入）
- 系列 management UI 已落地，P5 引入 ICS / 教务源时复用

---

## 变更记录

- 2026-05-01：初稿，brainstorming 收敛后落盘。关键决策与原 spec 偏差：
  - 吸收 P5 Plan C 周课表手动批量录入到 P4
  - 课程展开 vs RRULE → 改为录入时全展开（C 方案），删 biweekly 库依赖
  - 砍单/双周支持（连续周次范围已覆盖大多数场景）
  - 通知策略改为 type-default + 全局开关（spec 原方案是 per-item `remindersMinBefore: List<Int>`）
  - 课程颜色 hash 自动派色（spec 原方案是 5 种状态色统一）
  - COURSE 气泡 `isDone` 永远 false（spec 原方案不明确）
  - 编辑颗粒度：单条详情页 = 本次；系列管理页 = 全系列
  - 改作息表后自动重算 future COURSE startAt/endAt（spec 原方案未提）
  - MainActivity.onCreate + BootCompletedReceiver 双触发重排（spec 原方案只提 BootCompletedReceiver）
