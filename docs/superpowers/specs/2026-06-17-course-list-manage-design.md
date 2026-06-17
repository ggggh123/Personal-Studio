# 课程表 · 课程列表管理中心化 + UI 润色 设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与问题

课程表（P4 Timeline）当前新建课程**唯一入口**是 day 界面右下角 FAB →「录入课程」(`AddCourseScreen`)。设置 → 课程列表 (`CourseSeriesListScreen`) 虽然每行**可点**进编辑屏 (`CourseSeriesEditScreen`，可改名称/老师/地点/学分/备注 + 删除整个系列)，但：① 没有"新建课程"入口；② 行只是裸 `Text`、英文 `no courses yet` / `$ ls courses/`、标题左 + 元信息右导致**右列起点随标题长度浮动 → 参差不齐**、没有任何"可点/可编辑"视觉提示，看起来像只读静态列表（用户因此认为"没有编辑功能"）；③ 看不到"什么时候上课"（星期/节次），信息单薄。

目标：把课程列表做成真正的**课程管理中心**（明显的新建 + 编辑 + 删除）并润色 UI，对齐 chat/scanner/kb 终端风家族。

## 决策（已与用户确认）

- **编辑/删除交互**：沿用现有编辑屏（点行进编辑页，编辑屏内含"删除整个系列"）。**不做**内联下拉/长按删除。
- **时间编辑**：保持现状——时间（星期/节次/周次）不可改，编辑屏继续提示"如需调整时间，请删除并重新创建"。编辑只管 名称/老师/地点/学分/备注。
- 仅改课程列表这一屏 + 把它接上"新建课程"入口；编辑屏、AddCourse、数据层 use-case/repo 逻辑**都不动**。

## ① 新建入口（解决"唯一入口"）

- `CourseSeriesListScreen` 签名加 `onAddCourse: () -> Unit`；`AppNavHost` 的 `TIMELINE_COURSE_LIST` composable 传 `onAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) }`（复用现有路由，无新路由）。
- 头部右侧渲染 `[+新建课程]`（Cyan，可点）→ `onAddCourse`。空态 CTA 也指向它。
- 结果：新建课程除 day 界面 FAB 外，课程列表里也能直接发起。

## ② 列表润色（解决"简陋/参差不齐"）

**顶栏**：保留自带 `TerminalTopBar`（含返回），把 `subtitle` 从 `$ ls courses/` 改为 `# 课程管理`，避免与下方内联 `ls courses/` 字面重复。

**内联头部**（对齐 scanner，置于 `TerminalTopBar` 下）：
```
user@study:~$ ls courses/                    [+新建课程]
total 5
```
`user@study`(Amber) + `:~$ `(FoamDim) + `ls courses/`(Foam)；右侧 `[+新建课程]`(Cyan，可点)；下一行 `total N`(FoamMute，N = 课程系列数)。

**每行**从"标题左 + 元信息右对齐"改成**左对齐竖排三段**：
```
▸ 高等数学(上)
  周一/周三 第1-2节 · 第1-16周 · 共32节 · 4.0学分
  张伟 · 教三-201
```
- 第 1 行：`▸ `(FoamDim) + 课名(Phosphor，醒目)。`▸` 既是终端字形也暗示"可点进编辑"。
- 第 2 行：课表元信息(FoamMute)，由若干段以 ` · ` 拼接：`<星期> 第X-Y节`（单节则 `第X节`）、`第A-B周`（单周则 `第A周`）、`共N节`、`C学分`（无学分则省略）。星期由 `weekdays` 列表经 `weekdayCn` 映射并以 `/` 连接（如 `周一/周三`）；星期/节次缺失则省略对应段。
- 第 3 行：`老师 · 地点`(FoamDim)；老师、地点都空则**整行省略**，只有其一则只显示其一。
- 整行 `clickable { onOpenSeries(seriesId) }` → 现有编辑页。
- 行间分隔用 `Rule` 色 1dp 细线（替掉偏亮的 `Divider(FoamDim)`），列表用 `LazyColumn` + `Arrangement.spacedBy`。

**空态**对齐 scanner，替掉英文 `no courses yet`：
```
# 暂无课程
▓ 点 [新建课程] 或 day 界面的 [+] 录入
```
`# 暂无课程`(FoamMute)；`▓ `(Phosphor) + `点 `(FoamDim) + `[新建课程]`(Cyan，可点 → `onAddCourse`) + ` 或 day 界面的 [+] 录入`(FoamDim) + `BlinkingCursor()`。

## ③ 数据层：聚合查询补"星期/节次"投影（无 schema 变更）

当前 `CourseSeriesSummary` 没有星期/节次，故列表看不到上课时间。给 list 的聚合查询补三个投影字段，**只扩投影类与域模型，不碰表结构、无 migration**：

- `TimelineDao.CourseSeriesSummaryRow`（投影类）加：`weekdaysCsv: String?`、`periodStart: Int?`、`periodEnd: Int?`。
- `observeCourseSeriesList()` 的 `@Query` 投影加：
  - `GROUP_CONCAT(DISTINCT weekdayCode) AS weekdaysCsv`（SQLite 默认逗号分隔；课程行 `weekdayCode` 恒非空）
  - `MIN(periodIndex) AS periodStart`
  - `MAX(periodEndIndex) AS periodEnd`
- `CourseSeriesSummary`（域模型）加：`weekdays: List<Int> = emptyList()`、`periodStart: Int? = null`、`periodEnd: Int? = null`（**带默认值**，不破坏现有构造点，如 FakeTimelineRepository / 测试）。
- `TimelineRepositoryImpl.observeCourseSeriesList()` 映射：`weekdays = weekdaysCsv?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.distinct()?.sorted() ?: emptyList()`，`periodStart`/`periodEnd` 直传。

Room 在编译期校验 `@Query` 列名 ↔ 投影类字段，构建即验证（无需 androidTest）。

## 测试

- 列表/头部/空态以汉化 + 纯展示为主，复用控件已测，不强测。
- 抽一个**纯函数** `formatCourseSchedule(weekdays, periodStart, periodEnd, minWeek, maxWeek, occurrenceCount, credits): String` 放可单测的位置（如 `feature/timeline/ui` 顶层或一个 helper 文件），用 JUnit 覆盖几种组合（多星期、单节、单周、无学分、星期/节次缺失），走 TDD。
- 数据层投影改动由 Room 编译期校验 + 构建保证；DAO 真值由既有 androidTest 体系覆盖（本次不强加）。
- 真机 DoD：课程列表（`# 课程管理` 顶栏 + `user@study ls courses/` 头部 + `[+新建课程]` 可建 + 每行三段式显示星期/节次/周次/学分/老师地点 + 点行进编辑 + 空态 chat 同款）。

## 不做 / 保留

- 编辑屏 `CourseSeriesEditScreen` 不动（含"时间不可改、删除重建"约束、Material `OutlinedTextField` 表单）。
- 不做内联下拉/长按删除。
- 数据层 use-case/repo 业务逻辑不动；仅给 list 聚合查询加投影字段 + 域模型加字段。

## 影响面

改：`TimelineDao.kt`（投影类 +3 字段、`observeCourseSeriesList` 查询 +3 投影）、`TimelineModels.kt`（`CourseSeriesSummary` +3 带默认值字段）、`TimelineRepositoryImpl.kt`（映射 +3 字段）、`CourseSeriesListScreen.kt`（重写：头部 + 行 + 空态 + `onAddCourse` 参数 + 调度格式化 helper）、`AppNavHost.kt`（`TIMELINE_COURSE_LIST` 传 `onAddCourse`）。新增：调度格式化 helper 的单测。复用 `BlinkingCursor`/`TerminalTopBar`/颜色。无 DB schema/网络改动。
