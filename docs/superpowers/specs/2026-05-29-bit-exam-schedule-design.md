# P9 · BIT 考试安排同步到 Timeline 设计文档

> 状态:设计已对齐,待用户复核 → 转 writing-plans
> 日期:2026-05-29
> 数据源:BIT 正方(强智 jsxsd,jwms.bit.edu.cn)考试安排页,与成绩同系统同会话
> 复用:P6 jwms 会话基建 + JsxsdGradeParser 模式;P7 ReplaceImportedDdl 灌 Timeline 模式;P8 统一登录守卫;P4 Timeline 提醒/气泡

---

## 1. Scope

### In scope
- 从 BIT 正方 jsxsd 拉取考试安排(课程 / 考试起止时间 / 考点 / 座位号 / 监考),复用 grades 的 jwms 会话
- 灌进 Timeline `timeline_items` 作为**新 `EXAM` 类型**行(有起止时间段的块,`sourceType=IMPORTED_EXAM`),白嫖提醒 + 气泡 + 逾期/完成
- 新 EXAM 类型:专属配色(Cyan)、专属提醒档(考前 1天/2小时/30分,无逾期 ping)、块状态机(临近/进行中/已过)
- 独立考试列表页:按考试时间排,显示课程/时间段/地点/座位/倒计时;下拉刷新;已考折叠
- 接 P8 统一登录:未登录点同步/进页 → 跳登录、成功回跳
- 入口:Settings「查询考试安排」+ Timeline 顶栏「考试 ↗」+ deeplink `personalstudio://exams`

### Out of scope(YAGNI)
- 后台轮询(考试一学期发布一次、改动极少)→ 仅手动 + 进页刷新
- 学期选择(默认当前学期;真机看是否需要)
- 准考证号 / 校区 / 考试场次等次要字段的专门展示(解析到就存,不强求 UI)

---

## 2. 架构 & 模块布局

```
# 新增
domain/bitexam/model/ExamModels.kt          ExamItem / ExamSyncRequest / ExamSyncStep / ExamSyncError
domain/bitexam/JsxsdExamParser.kt           克隆 JsxsdGradeParser:正则 + 按表头映射列 + 考试时间拆分
domain/bitexam/SyncExamsUseCase.kt          sync(req): Flow<ExamSyncStep>(open→sso→activateService→抠学期→拉→解析→落库)
domain/bitexam/ReplaceImportedExamUseCase.kt 灌 timeline_items(EXAM/IMPORTED_EXAM)去重+保留isDone+重排提醒
feature/bitexam/ExamsViewModel.kt           考试列表页 VM(过滤/排序/刷新/未登录事件)
feature/bitexam/ui/ExamsScreen.kt           考试列表页 UI

# 改
data/network/bit/service/BitJwmsService.kt  加 getExamQueryHtml() + getExamScheduleHtml(@Field xnxqid)
domain/model/TimelineModels.kt              TimelineType +EXAM;TimelineSource +IMPORTED_EXAM;BubbleState +Exam*4
domain/timeline/ScheduleRemindersUseCase.kt slotsFor(EXAM)=[1440,120,30](无逾期)
domain/timeline/ComputeBubbleStateUseCase.kt EXAM 块状态(沿用 COURSE 的 past/inProgress/imminent/upcoming)
feature/timeline/ui/components/TimelineBubble.kt EXAM 配色 Cyan + visualMods
data/local/db/dao/TimelineDao.kt            加 getImportedExams/observeImportedExams;observeDayCounts 右徽标含 EXAM
data/local/db/AppDatabase.kt                VERSION 11→12
ui/navigation/NavRoutes.kt                  EXAMS = "exams" + 已有 login(next)
ui/AppNavHost.kt                            注册 exams composable + deeplink + 登录守卫
feature/timeline/ui/TimelineScreen.kt       顶栏加「考试 ↗」入口
feature/settings/ui/SettingsScreen.kt       ## timeline 区加「查询考试安排」行
```

---

## 3. 数据源 & 网络(复用 jwms 会话)

BIT jwms 是**强智科技 jsxsd**(与成绩同系统同 host)。考试安排:
- `GET jsxsd/xsks/xsksap_query` — 查询页 HTML,内含 `<select id="xnxqid">` 学期下拉(格式 `2025-2026-1`),取当前选中(`selected`)的 `xnxqid`
- `POST jsxsd/xsks/xsksap_list`(`application/x-www-form-urlencoded`,`@Field("xnxqid")`)— 返回考试表 HTML

`BitJwmsService` 加:
```kotlin
@GET("jsxsd/xsks/xsksap_query")
suspend fun getExamQueryHtml(): Response<ResponseBody>

@FormUrlEncoded
@POST("jsxsd/xsks/xsksap_list")
suspend fun getExamScheduleHtml(@Field("xnxqid") term: String): Response<ResponseBody>
```

**会话**:复用 grades 的 `apiClient.cas.activateService(JWMS_SERVICE)`(JWMS_SERVICE = `http://jwms.bit.edu.cn/`)。同 host 同 cookie,无需新 activateService。

> 真机待验(同 P5/P6):BIT 实际 `xsks` 路径、POST 是否只需 `xnxqid`(还是要 `xqlb`/`xqlbmc`)、考试表的 table id 与列名/列序 —— 以真机抓包为准。设计按强智家族通用协议(qfnu/csust/sztu 三处印证)。

---

## 4. JsxsdExamParser

克隆 `JsxsdGradeParser`(正则,不引 jsoup;header-driven 列映射对列序鲁棒)。

- 定位考试表(table id 待真机确认,先按 `dataList` + 兜底"含考试时间表头的表"),逐 `<tr>`,取 `<td>`。
- 表头映射:`col("课程名称","课程")`、`col("考试时间")`、`col("考点","考试地点","地点")`、`col("座位","座位号")`、`col("监考","任课教师")`、`col("课程编号","课程号")`。
- **考试时间拆分**:cell 形如 `2026-01-05 08:00~10:00`(或 `~`/`-`/`－` 分隔)。正则抠日期 + 起止 time → `startAt`/`endAt` epoch millis(+08:00)。只有日期无时间则当天 00:00,endAt=null(退化为点,仍可显示)。
- 去重键 `uid` = `课程编号|xnxqid|场次` 缺则 `课程名|startAt`。
- 单行解析失败跳过;空表("未查询到数据")→ 空列表;非考试页 → ParseFail。

---

## 5. ExamModels

```kotlin
data class ExamItem(
    val uid: String,
    val course: String,
    val startAt: Long,
    val endAt: Long?,        // 起止段;只有日期时为 null
    val location: String?,   // 考点
    val seat: String?,       // 座位号
    val invigilator: String?,// 监考/任课教师
)

data class ExamSyncRequest(val username: String, val password: String, val networkMode: NetworkMode, val rememberPwd: Boolean)

sealed interface ExamSyncStep {
    object LoggingIn : ExamSyncStep
    object FetchingExams : ExamSyncStep
    data class Done(val total: Int) : ExamSyncStep
    data class Failed(val error: ExamSyncError) : ExamSyncStep
}

sealed interface ExamSyncError {
    object WrongCredentials; object AccountLocked; object CaptchaRequired; object NeedReview
    data class ParseFail(val message: String); data class NetworkFail(val cause: String); data class Unexpected(val cause: String)
}
```

---

## 6. EXAM 类型集成(改动面最大)

- `TimelineType { COURSE, TASK, CUSTOM, EXAM }`
- `TimelineSource { ..., IMPORTED_EXAM }`
- `ScheduleRemindersUseCase.slotsFor(EXAM)` = `[ReminderSlot(1440,false), ReminderSlot(120,false), ReminderSlot(30,false)]`(考前1天/2小时/30分,无逾期 ping)
- `ComputeBubbleStateUseCase`:EXAM 沿用 COURSE 的块状态(用 startAt/endAt 判 past/inProgress/imminent/upcoming;考完=past)。但 EXAM 可被勾"已考"(isDone)→ ExamDone 视觉(划线)。
- `BubbleState` 加 `ExamUpcoming / ExamImminent / ExamInProgress / ExamPast`(完成态可复用 past 划线或加 ExamDone)
- `TimelineBubble`:`bubbleBaseColor(EXAM)` = `Cyan`;`visualMods` 沿用块逻辑(临近脉动加边)
- `TimelineDao.observeDayCounts`:右侧 task 徽标 SQL 改为 `SUM(CASE WHEN type IN ('TASK','EXAM') THEN 1 ELSE 0 END)`(EXAM 与作业合并计数)

---

## 7. Timeline 映射(ReplaceImportedExamUseCase)

克隆 `ReplaceImportedDdlUseCase`。映射 ExamItem → TimelineItemEntity:

| 字段 | 来源 |
|---|---|
| type | `EXAM` |
| sourceType | `IMPORTED_EXAM` |
| sourceExternalId | `uid` |
| title | `course` |
| startAt / endAt | `startAt` / `endAt` |
| location | `location`(考点) |
| instructor | `invigilator`(监考) |
| notes | `座位: $seat`(seat 非空时) |
| courseName | `course` |
| isDone / doneAt | 已存在保留旧值,新建 false/null |

算法:读现有 `IMPORTED_EXAM` 行 `uid→entity`;新 uid insert、已存在 update(标题/时间/地点/座位,**保留 isDone/doneAt**)、消失的 delete;落库后 `rescheduleAllUpcoming()`。

DAO:
```kotlin
@Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM'")
suspend fun getImportedExams(): List<TimelineItemEntity>
@Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM' ORDER BY startAt ASC")
fun observeImportedExams(): Flow<List<TimelineItemEntity>>
```

---

## 8. 考试列表页 + 登录守卫 + 入口

### ExamsScreen + ExamsViewModel(克隆 AssignmentsScreen/VM)
- 数据:`observeImportedExams()` → domain。
- 默认区:**即将到来(startAt >= now)按 startAt 升序**;折叠区:已过(已考)。
- 行:课程 + 时间段(MM-dd HH:mm~HH:mm)+ 考点 + 座位 + 相对倒计时("3天后" / "明天 08:00" / 已过)。可勾"已考"(isDone,取消该项提醒)。
- 下拉刷新 → `SyncExamsUseCase.sync(req)`(用存的凭据);未登录 → 发 `ExamsEvent.NeedLogin`,Screen 跳 `login("exams")`。
- 空态:"还没有考试安排 —— 下拉刷新,或学校尚未发布"。

### 登录守卫(P8)
未登录 = `ImportCredentialPrefs.observeAll().value == null`。`onRefresh` creds 为空发 NeedLogin 事件;`AppNavHost` exams composable 传 `onNeedLogin = { navController.navigate(NavRoutes.login("exams")) }`。

### 入口
- `NavRoutes.EXAMS = "exams"`;AppNavHost 注册 + `deepLinks = navDeepLink { uriPattern = "personalstudio://exams" }`。
- Settings `## timeline` 区加 `NavigableRowWithSubtitle("EXAMS", "查询考试安排 →", "考试时间 · 地点 · 座位 同步进 Timeline", onClick = { onNavigate(NavRoutes.EXAMS) })`。
- TimelineScreen 顶栏加「考试 ↗」按钮(同「作业 ↗」)。

---

## 9. 错误处理

`SyncExamsUseCase` 复用 `CasLoginDto` → `ExamSyncError` 映射(WrongCredentials/AccountLocked/CaptchaRequired/UnknownFailure→ParseFail);IOException→NetworkFail;解析不出考试表→ParseFail。列表页 banner 文案对照(密码错/锁号/需验证码网页端/网络错/接口变化等更新)。失败不灌库。

---

## 10. 测试策略(TDD)

| 单元 | 用例 |
|---|---|
| `JsxsdExamParser` | 表头映射、考试时间 `日期 HH:mm~HH:mm` 拆 startAt/endAt、只日期无时间退化、`~`/`-` 分隔、多行、空表("未查询到数据")、非考试页 |
| `extractCurrentTerm` | 从 xsksap_query HTML 抠 selected `xnxqid` |
| `ReplaceImportedExamUseCase` | 新建(EXAM/IMPORTED_EXAM/字段映射)、更新保留 isDone、删除消失、重排提醒 |
| `SyncExamsUseCase` | happy(抠学期→拉→Ok)、密码错 Stop、解析失败、网络错(各错误映射) |
| `ExamsViewModel` | 默认即将到来升序、已过折叠、勾已考、未登录发 NeedLogin |
| `ScheduleRemindersUseCase.slotsFor` | EXAM = [1440,120,30] 无逾期 |

UI 屏(ExamsScreen)+ 气泡 EXAM 配色编译 + 真机目检。

---

## 11. Phase 分解(供 writing-plans)

- **A 数据/解析**:ExamModels、JsxsdExamParser(+单测)
- **B 网络**:BitJwmsService 端点、SyncExamsUseCase(抠学期 + 拉 + 错误映射,+单测)
- **C Timeline 类型**:TimelineType/Source +EXAM/IMPORTED_EXAM、slotsFor、ComputeBubbleState、BubbleState、TimelineBubble 配色、DAO + Room v12
- **D 灌库**:ReplaceImportedExamUseCase(+单测)
- **E UI**:ExamsViewModel(+单测)、ExamsScreen、导航/入口/deeplink/登录守卫、Timeline 顶栏入口、Settings 入口
- **F 真机 DoD**(含协议抓包确认)

---

## 12. Room 迁移

`TimelineType` +EXAM、`TimelineSource` +IMPORTED_EXAM(枚举存为 string)。座位/监考复用现有 notes/instructor 列,**不加新列**。Room VERSION 11→12,`fallbackToDestructiveMigration`(dev 库可丢,无保数据迁移)。

---

## 13. Risk Register

| 风险 | 缓解 |
|---|---|
| BIT 实际 xsks 路径/POST 字段/表结构未真机验证 | 设计按强智通用协议(三处印证);先 GET query 读真实 xnxqid + 隐藏字段,再 POST;解析按表头名映射不硬编码列序;归 ParseFail 不崩 |
| 新 EXAM 类型触及多处(枚举/提醒/气泡/配色/daystrip/查询) | Phase C 单独成阶段,逐处改 + 编译;复用 COURSE 块逻辑降低新代码 |
| 考试时间格式各异(~ / - / 跨日) | 解析器多分隔符兼容 + 单测覆盖;无时间退化为点 |
| daystrip 徽标语义(EXAM 并入 task) | SQL `type IN ('TASK','EXAM')`;真机看是否需要独立徽标(P 以后) |

---

## 14. DoD

1. Settings「查询考试安排」/ Timeline「考试 ↗」入口可达;未登录点 → 跳登录、成功回到考试页
2. 已登录下拉刷新 → 拉到本学期考试,列表按时间排,每条显示课程/时间段/考点/座位/倒计时
3. 考试同步进 Timeline:EXAM 气泡(Cyan,带起止时长块)出现在对应日期,临近脉动,日条右徽标计数 +N
4. 考前 1天/2小时/30分收到提醒
5. 勾"已考" → 移入折叠区 + 提醒取消
6. 错密码 → banner 报错不灌库;真机确认 xsks 协议(路径/字段/列)
7. 全量单测绿

---

## 15. Open Questions(实现期/真机定,非阻塞)
- BIT 实际 `jsxsd/xsks/...` 路径、POST 字段集、考试表 table id 与列名(真机抓包)
- 是否需要学期选择(默认当前;真机看多学期需求)
- EXAM 完成态视觉(复用 past 划线 vs 专门 ExamDone)— 真机目检定
