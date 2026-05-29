# P7 · BIT 作业 DDL(乐学拉取 + Timeline 集成 + 后台轮询)设计文档

> 状态:设计已对齐,待用户复核 → 转 writing-plans
> 日期:2026-05-28
> 数据源:乐学 lexue.bit.edu.cn(BIT Moodle),iCal 日历导出
> 复用:P5 CAS 登录基建 / BitApiClient / 加密凭据;P4 Timeline(TASK 行 + 提醒 + 气泡);M4 轮询基建(Worker/Scheduler/Prefs/Notifier)模式

---

## 1. Scope

### In scope
- 从乐学拉取课程作业/活动 DDL(Moodle 日历 iCal 导出),解析为结构化 DDL 条目
- DDL 灌进 Timeline `timeline_items` 当 `TASK` 行(`sourceType=IMPORTED_LEXUE`),白嫖现有提醒(1440/120/30/0 分)+ 时间线气泡 + 逾期/完成状态
- 独立「作业清单页」:默认未完成且未过期、按截止升序;可展开已完成/已过期;下拉刷新;完成勾选
- 后台轮询(可选 6/12/24h)+ 发现新作业通知(克隆 M4 模式,独立开关/任务)
- Settings 加「作业自动同步」开关页

### Out of scope
- 不拉作业正文/附件/提交入口(只拉 DDL 元数据)
- 不追踪"是否已提交"真实状态(Moodle iCal 不给;完成 = 本地手动勾选)
- 非乐学来源的 DDL(群通知 / 其它平台)—— 明确不做
- "截止时间改了"的单独通知(YAGNI;静默更新 + 重排提醒)
- WebVPN 离校访问:配置层支持,但与成绩一致保持 best-effort、不做真机验证保证

---

## 2. 架构 & 模块布局

镜像 `bitgrades` 的分层(UseCase → parser → dao/timeline → ViewModel → Screen)。新增/修改文件:

```
# 网络层
data/network/bit/service/BitLexueService.kt          新:3 端点(index/export/ics)
data/network/bit/BitUrlsConfig.kt                    改:Hosts 加 lexue(LOCAL + WEBVPN)
data/network/bit/BitApiClient.kt                     改:lexueRetrofit + val lexue 访问器

# 领域层
domain/bitddl/model/DdlModels.kt                     新:DdlEvent / DdlSyncRequest / DdlSyncStep / DdlSyncError / BackgroundDdlResult
domain/bitddl/LexueIcalParser.kt                     新:VEVENT 正则解析(折行/转义/时区)
domain/bitddl/GenerateLexueIcalUrlUseCase.kt         新:login→sesskey→export.php→.ics URL
domain/bitddl/SyncAssignmentsUseCase.kt              新:sync(Flow) + syncForBackground
domain/bitddl/ReplaceImportedDdlUseCase.kt           新:映射 timeline_items + 去重 + 保留 isDone + 排提醒
domain/bitddl/DetectNewDdlUseCase.kt                 新:新 UID diff

# 持久化
data/local/datastore/DdlSyncPrefs.kt                 新:enabled/interval/lastSeenUids/lastSyncAt/icalUrl
data/local/db/entity/TimelineItemEntity.kt           改:加 courseName: String?
domain/model/TimelineModels.kt                       改:TimelineItem 加 courseName;TimelineSource 加 IMPORTED_LEXUE
data/local/db/dao/TimelineDao.kt                     改:加 IMPORTED_LEXUE 的读/删 + observeAssignments
data/repository/TimelineRepositoryImpl.kt            改:courseName 映射
data/local/db/AppDatabase.kt(或等价)               改:Room 版本 +1(开发库可丢,fallbackToDestructive)

# 后台 + 通知
core/workers/DdlPollWorker.kt                        新:@HiltWorker 主流程
feature/bitddl/DdlPollScheduler.kt                   新:WorkManager 包装(WORK_NAME=ddl-poll)
core/notification/NotificationChannels.kt            改:加 DDL_ID="assignment_ddl"
core/notification/DdlNotifier.kt                     新:N 个新作业通知
core/workers/BootCompletedReceiver.kt                改:再重排 ddl-poll

# UI
feature/bitddl/AssignmentsViewModel.kt               新:清单页 VM
feature/bitddl/ui/AssignmentsScreen.kt               新:作业清单页
feature/settings/vm/DdlPollSettingsViewModel.kt      新:作业自动同步 VM
feature/settings/ui/DdlPollSettingsScreen.kt         新:作业自动同步设置页
ui/navigation/NavRoutes.kt                           改:ASSIGNMENTS + SETTINGS_DDL_POLL
ui/AppNavHost.kt                                     改:注册 + deeplink(personalstudio://assignments)
feature/timeline/ui/TimelineScreen.kt                改:加「作业 ↗」入口
feature/settings/ui/SettingsScreen.kt                改:加「作业自动同步」入口
```

---

## 3. 乐学接入(网络层)

### 3.1 BitLexueService(3 端点)

```kotlin
interface BitLexueService {
    /** 乐学首页 HTML,用于正则抠 sesskey。 */
    @GET(".")
    suspend fun getIndexHtml(): Response<ResponseBody>

    /** 生成 iCal 订阅 URL。返回 HTML,.calendarurl 元素里含生成的 URL。 */
    @FormUrlEncoded
    @POST("calendar/export.php")
    suspend fun exportCalendar(
        @Field("sesskey") sesskey: String,
        @Field("_qf__core_calendar_export_form") formMarker: String = "1",
        @Field("events[exportevents]") events: String = "all",
        @Field("period[timeperiod]") period: String = "recentupcoming",
        @Field("generateurl") generate: String = "获取日历网址",
    ): Response<ResponseBody>

    /** 直接 GET 持久化的 .ics 订阅 URL(authtoken 自带鉴权,免会话)。 */
    @GET
    suspend fun getIcs(@Url url: String): Response<ResponseBody>
}
```

### 3.2 Host 配置

`BitUrlsConfig.Hosts` 加 `lexue` 字段,`LOCAL` = `https://lexue.bit.edu.cn/`;`WEBVPN` = webvpn 编码前缀(best-effort)。`BitApiClient.open()` 里建 `lexueRetrofit`,`val lexue: BitLexueService get() = ...`。

### 3.3 CAS service 激活

派生 URL 前需登录乐学会话:`apiClient.cas.activateService(LEXUE_SERVICE)`。
**LEXUE_SERVICE 必须逐字节匹配乐学在 CAS 注册的 service URL**(http/https + 末尾斜杠)—— 实现期对照真机抓包确认,占位 `"https://lexue.bit.edu.cn/login/index.php"`(待验证)。

### 3.4 策略 Y:.ics URL 持久化 + 免登录刷新

`.ics` 订阅 URL 形如 `https://lexue.bit.edu.cn/calendar/export_execute.php?userid=...&authtoken=...&preset_what=all&preset_time=recentupcoming`,其中 `authtoken` 是长期、URL 内嵌的日历 feed token,**独立鉴权、不依赖会话 cookie**。

- 首次:`GenerateLexueIcalUrlUseCase` 完整登录(CAS → activateService → getIndexHtml 抠 sesskey → exportCalendar 抠 URL)→ 持久化到 `DdlSyncPrefs.icalUrl`
- 之后:轮询/刷新只 `getIcs(icalUrl)`,**完全不登录**
- 失效兜底:`getIcs` 返回 403 / 非 VCALENDAR 内容 → 重新派生一次 URL(走登录)→ 再 GET;仍失败 → Stop/Transient(见 §9)

---

## 4. DdlSyncPrefs(DataStore)

镜像 `GradesSyncPrefs`。

```kotlin
data class DdlSyncState(
    val enabled: Boolean,
    val intervalHours: Int,         // 6 / 12 / 24,默认 12
    val lastSyncAt: Long?,
    val lastSeenUids: Set<String>,  // 新作业 diff 基线
    val icalUrl: String?,           // 策略 Y 持久化订阅 URL
)
```

keys:`ddl_poll_enabled` / `ddl_poll_interval_hours` / `ddl_last_sync_at` / `ddl_last_seen_uids`(`\n` join)/ `ddl_ical_url`。
方法:`observe: Flow`、`snapshot()`、`setEnabled`、`setIntervalHours`、`setLastSyncAt`、`setLastSeenUids`、`setIcalUrl`、`clearIcalUrl`。
默认:enabled=false、interval=12。

---

## 5. LexueIcalParser

输入 `.ics` 文本,输出 `List<DdlEvent>`。手写正则(贴合 `JsxsdGradeParser` 风格,不引 ical4j)。

### 必须处理的 iCal 细节
1. **折行展开(unfold)**:RFC 5545 行超 75 字节折行,续行以单个空格或 Tab 开头。先全局 unfold:把 `\r\n[ \t]` 替换成空串。
2. **切 VEVENT**:`BEGIN:VEVENT ... END:VEVENT` 之间为一条。
3. **取字段**:
   - `UID:` → uid(必须;无则跳过该条)
   - `SUMMARY:` → title
   - `DESCRIPTION:` → description
   - `CATEGORIES:` → course(可空)
   - `DTSTART...:` → dueAt
4. **TEXT 转义还原**:`\\n`→换行、`\\,`→`,`、`\\;`→`;`、`\\\\`→`\\`(顺序:先 `\\\\` 占位)。
5. **DTSTART 时区**:
   - `DTSTART:20260530T235900Z`(UTC,Z 结尾)→ 按 UTC 解析再转本地 millis
   - `DTSTART;TZID=Asia/Shanghai:20260530T235900` → 按 +08:00
   - `DTSTART;VALUE=DATE:20260530`(全天)→ 当天 00:00 +08:00
   - 兜底:无 Z 无 TZID 一律按 +08:00(BIT101 同款)

### 健壮性
- 单条解析失败(缺 UID / DTSTART 解析不出)→ 跳过该条,不中断整体
- 全部解析为 0 条且文本非 VCALENDAR → 视为 ParseFail(见 §9)

---

## 6. DdlEvent 数据模型

```kotlin
// domain/bitddl/model/DdlModels.kt
data class DdlEvent(
    val uid: String,
    val title: String,
    val description: String,
    val course: String?,   // CATEGORIES
    val dueAt: Long,       // epoch millis(本地)
)

data class DdlSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

sealed interface DdlSyncStep {
    object LoggingIn : DdlSyncStep
    object FetchingCalendar : DdlSyncStep
    data class Done(val total: Int, val newCount: Int) : DdlSyncStep
    data class Failed(val error: DdlSyncError) : DdlSyncStep
}

sealed interface DdlSyncError {
    object WrongCredentials : DdlSyncError
    object AccountLocked : DdlSyncError
    object CaptchaRequired : DdlSyncError
    object NeedReview : DdlSyncError
    data class ParseFail(val message: String) : DdlSyncError
    data class NetworkFail(val cause: String) : DdlSyncError
    data class Unexpected(val cause: String) : DdlSyncError
}

sealed interface BackgroundDdlResult {
    data class Ok(val events: List<DdlEvent>) : BackgroundDdlResult
    data class Stop(val reason: DdlSyncError) : BackgroundDdlResult
    object Transient : BackgroundDdlResult
}
```

---

## 7. ReplaceImportedDdlUseCase(Timeline 映射 + 去重 + 保留 isDone + 排提醒)

### 映射规则(DdlEvent → TimelineItemEntity)
| TimelineItem 字段 | 来源 |
|---|---|
| `type` | `TASK` |
| `sourceType` | `IMPORTED_LEXUE` |
| `sourceExternalId` | `uid` |
| `startAt` | `dueAt` |
| `endAt` | `null`(点时刻 DDL) |
| `title` | `title` |
| `description` | `description` |
| `courseName` | `course`(**新列**) |
| `isDone` / `doneAt` | 已存在则保留旧值,新建为 false/null |

### 算法
1. 读现有 `IMPORTED_LEXUE` 行,建 `uid → entity` 映射
2. 对每个 `DdlEvent`:
   - 存在 → `update` 标题/描述/courseName/startAt,**保留 isDone/doneAt**
   - 不存在 → `insertOne`(isDone=false)
3. 乐学侧消失的 uid(在映射里但不在本次 events)→ `deleteById`
4. 落库后,对**未完成 且 startAt > now** 的受影响 TASK 调 `ScheduleRemindersUseCase`(或 `RescheduleAllUpcomingUseCase`);完成/过期的不排

### DAO 新增
```kotlin
@Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_LEXUE'")
suspend fun getLexueDdls(): List<TimelineItemEntity>

@Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_LEXUE' ORDER BY startAt ASC")
fun observeLexueDdls(): Flow<List<TimelineItemEntity>>
```
(去重/更新走现有 `insertOne`/`update`/`deleteById`/`setDone`。)

---

## 8. DetectNewDdlUseCase(新 UID diff)

对照 M4 `DetectNewGradesUseCase`。

```kotlin
data class DdlDiffResult(
    val newEvents: List<DdlEvent>,
    val fullUids: Set<String>,
    val isFirstRun: Boolean,
)
```
- `lastSeen = prefs.snapshot().lastSeenUids`
- `newUids = currentUids - lastSeen`
- `isFirstRun = lastSeen.isEmpty()` → 首次只建基线,`newEvents = emptyList()`(防首轮通知风暴)
- 已存在 uid 但 dueAt 改变:不算"新",在 §7 静默更新

---

## 9. SyncAssignmentsUseCase + 错误映射

### 两入口
- `sync(req): Flow<DdlSyncStep>` —— 前台手动刷新,带进度
- `suspend syncForBackground(): BackgroundDdlResult` —— 后台静默(策略 Y:优先用持久化 URL)

### syncForBackground 流程(策略 Y)
```
url = prefs.icalUrl
if url == null:
    deriveResult = generateUrl()          // 登录派生
    if deriveResult is Stop → return Stop
    url = deriveResult.url; prefs.setIcalUrl(url)
resp = lexue.getIcs(url)
if resp 403 / 非 VCALENDAR:
    重新 generateUrl() 一次 → 成功则 setIcalUrl + 再 getIcs;失败 → Stop/Transient
events = parser.parse(body)
if events empty 且 body 非日历 → Stop(ParseFail)
return Ok(events)
```

### 错误映射表
| 场景 | 结果 |
|---|---|
| 派生时密码错 | Stop(WrongCredentials) → 清凭据 |
| 派生时锁号 | Stop(AccountLocked) → 清凭据 |
| 派生时验证码 | Stop(CaptchaRequired) |
| 派生时未评教拦截 | Stop(NeedReview) |
| .ics 解析不出日历 | Stop(ParseFail) |
| 网络 IO 异常 | Transient(retry) |
| 其它未知异常 | Transient |

(`apiClient.open/close` 由 Worker 管;`generateUrl` 假设按需 open。)

---

## 10. 后台轮询 + 通知(克隆 M4)

### DdlPollWorker(@HiltWorker)
```
if !prefs.snapshot().enabled → success
apiClient.open(LOCAL)
try:
  result = sync.syncForBackground()
  handle(result):
    Stop(WrongCredentials|AccountLocked) → credPrefs.clear(); prefs.setEnabled(false); scheduler.cancel(); notifier.notifyStop(reason); success
    Stop(其它) → prefs.setEnabled(false); scheduler.cancel(); notifier.notifyStop(reason); success
    Transient → retry
    Ok(events):
      diff = detector.invoke(events)
      replace.invoke(events)                 // 落库 + 排提醒(无论是否首次,保持 Timeline 最新)
      if !diff.isFirstRun && diff.newEvents.isNotEmpty():
          notifier.notifyNewDdls(diff.newEvents)
      prefs.setLastSeenUids(diff.fullUids)
      prefs.setLastSyncAt(now)
      success
finally: apiClient.close()
```
注:与 M4 的差异 —— `replace` 在首次也执行(把 DDL 灌进 Timeline 建基线),只是首次不通知。

### DdlPollScheduler
`WORK_NAME="ddl-poll"`;`enqueue(intervalHours)` / `cancel()` / `rescheduleFromPrefs()`;`buildPeriodicRequest`(NetworkType.CONNECTED + 指数退避 30min)伴生函数,供单测。

### 通知
`NotificationChannels.DDL_ID = "assignment_ddl"`(名称"作业提醒",IMPORTANCE_DEFAULT)。`DdlNotifier.notifyNewDdls(events)`:标题"N 个新作业",bigText 列前 5 条(课程名 + 标题 + 相对截止),deeplink `personalstudio://assignments`;`notifyStop(reason)` deeplink `personalstudio://settings/ddl-poll`。同 M4,静态 notificationId 覆盖不堆叠。

### Boot
`BootCompletedReceiver` 再加 `ddlPollScheduler.rescheduleFromPrefs()`(goAsync)。

---

## 11. 作业清单页 + 入口

### AssignmentsScreen + AssignmentsViewModel
- 数据:`observeLexueDdls()` → 映射 domain
- 默认区:**未完成 且 startAt >= now**,按 startAt 升序
- 可展开折叠区:已完成 / 已过期(startAt < now 且未完成)
- 行:`title` + `courseName` + 相对截止("明天 23:59" / "3 天后" / 逾期红字) + 完成 checkbox(`setDone` + 取消该项提醒)
- 下拉刷新:触发前台 `SyncAssignmentsUseCase.sync(req)`(用保存的凭据;无凭据 → 引导先做 BIT 登录)
- 空态:"还没有作业 —— 去「作业自动同步」开启,或下拉刷新一次"
- 终端风格,沿用现有配色/组件

### DdlPollSettingsScreen(克隆 GradesPollSettingsScreen)
开关(需先存凭据,灰显逻辑同 M4)+ 6/12/24 chip + 上次同步 + 风险说明。

### 导航
- `NavRoutes.ASSIGNMENTS = "assignments"`、`SETTINGS_DDL_POLL = "settings/ddl-poll"`
- `AppNavHost`:注册两屏 + `navDeepLink { uriPattern = "personalstudio://assignments" }` 和 `personalstudio://settings/ddl-poll`
- Timeline 页加「作业 ↗」按钮跳 ASSIGNMENTS;Settings 页加「作业自动同步」行跳 SETTINGS_DDL_POLL

---

## 12. Room 迁移

`TimelineItemEntity` 加 `val courseName: String? = null`;`TimelineSource` 加 `IMPORTED_LEXUE`。Room 版本号 +1。**开发库数据可丢弃**(用户既定原则),用 `fallbackToDestructiveMigration`,不写保数据迁移。

---

## 13. 测试策略(TDD,与 M4 同密度)

| 单元 | 用例 |
|---|---|
| `LexueIcalParser` | 折行展开、TEXT 转义、UTC(Z)时区、TZID 时区、VALUE=DATE 全天、多 VEVENT、缺 UID 跳过、空/非日历输入 |
| `DdlSyncPrefs` | 默认值、UID 集合往返、icalUrl 存取/清除 |
| `DetectNewDdlUseCase` | 首次基线无新、无新增、新 UID、消失 UID 不算新 |
| `ReplaceImportedDdlUseCase` | 新建、更新保留 isDone、删除消失项、只给未完成未来项排提醒 |
| `SyncAssignmentsUseCase.syncForBackground` | 有 URL 直拉 Ok、无 URL 派生、403 重新派生、派生密码错 Stop、解析失败 Stop、网络 Transient |
| `DdlPollWorker` | disabled、无凭据自关、Stop 清凭据+停轮+通知、Transient retry、首次基线无通知、有新作业通知 |
| `DdlPollScheduler` | buildPeriodicRequest 间隔/约束/退避 |
| `AssignmentsViewModel` | 默认过滤未完成升序、勾选完成、展开已完成 |
| `DdlPollSettingsViewModel` | 无凭据禁用、开/关 scheduler 联动、改间隔重排 |

UI 屏(AssignmentsScreen / DdlPollSettingsScreen)编译 + 真机目检,不写 Compose 单测。

---

## 14. Phase 分解(供 writing-plans 细化)

- **A 数据/解析**:DdlModels、LexueIcalParser、DdlSyncPrefs(纯 JVM 单测)
- **B 网络**:BitLexueService、Hosts、BitApiClient.lexue、GenerateLexueIcalUrlUseCase
- **C 领域**:DetectNewDdlUseCase、ReplaceImportedDdlUseCase(+ courseName 列/枚举/DAO/Room 迁移)、SyncAssignmentsUseCase
- **D 后台**:DdlNotifier + 渠道、DdlPollScheduler、DdlPollWorker、BootReceiver
- **E UI**:AssignmentsViewModel/Screen、DdlPollSettingsViewModel/Screen、导航/入口/deeplink
- **F 真机 DoD**

---

## 15. Risk Register

| 风险 | 缓解 |
|---|---|
| LEXUE_SERVICE 注册 URL 逐字节匹配(P6 jwms 同坑) | 实现期真机抓包确认精确串;先占位待验证 |
| Moodle 改版导致 sesskey 正则 / .calendarurl 选择器失效 | 解析失败归 Stop(ParseFail),通知用户等更新;只影响派生,不影响已持久化 URL |
| authtoken 被用户在乐学重置导致 URL 失效 | getIcs 403 → 自动重新派生一次 |
| 作业量大 → AlarmManager 提醒过多 | 只给未完成未来项排;复用 Timeline 既有调度(已实践 OEM 兼容) |
| iCal 时区/全天事件解析错 | 单测覆盖 Z/TZID/VALUE=DATE 三种;+08:00 兜底 |
| 离校 WebVPN 未验证 | 与成绩一致,best-effort,不保证 |

---

## 16. DoD

1. 装机 → Settings →「作业自动同步」:无凭据时开关灰显
2. 作业清单页下拉刷新(用已存凭据)→ 首次登录派生 .ics URL → 列出未完成作业,按截止升序;每条显示课程名 + 相对截止
3. DDL 同步出现在 Timeline 时间线(TASK 气泡,逾期/完成状态正确)+ 日条计数右徽标
4. 勾选完成 → 该项移到折叠区 + 提醒取消
5. 启用后台开关 → `dumpsys jobscheduler | grep ddl-poll` 有调度;手动 Run Now 首次只建基线无通知
6. 清 lastSeenUids 后再 Run Now → "N 个新作业"通知,点按跳作业清单页
7. 错密码触发派生失败 → 停轮 + 清凭据 + 通知
8. 关开关 → ddl-poll 任务取消
9. 全量单测绿

---

## 17. Open Questions(实现期解决,非阻塞)
- LEXUE_SERVICE 精确注册串(真机抓包)
- 乐学首页 sesskey 的精确正则(可能与 BIT101 略有版本差异)
- export.php 表单字段名是否随 Moodle 版本变化(以真机响应为准)
