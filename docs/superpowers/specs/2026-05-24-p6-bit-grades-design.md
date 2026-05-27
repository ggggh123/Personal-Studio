# P6 · BIT 教务成绩查询 + 可视化 + AI 分析 — Design Spec

**Status:** Approved (brainstorm completed 2026-05-24)
**Owner:** ggggh123
**References:** [BIT101-Android](https://github.com/BIT101-dev/BIT101-Android) (AGPL-3.0 — **studied for protocol shape only, no code copied**). Builds directly on the P5 BIT 登录基建（`SsoLoginUseCase` / `BitApiClient` / `NetworkMode`）。

---

## 1 · Scope & Goals

在 P5 已打通的 BIT 统一身份认证之上，新增一条"从教务系统查询成绩 → 落库 → 可视化 → AI 分析"的完整链路。成绩查询模块（cjcx）与课表模块（wdkbby）共享同一套登录 / cookie / 网络模式基建。

**In scope（按里程碑分，见 §12）：**

- **M1 核心**：成绩查询（cjcx app）两步拉取（成绩列表 + 每学期"获取详细信息"取班级/专业排名）→ 落 Room → 成绩单页（概览卡 + GPA 趋势折线 + 成绩分布柱状 + 按学期课程列表）→ AI 学情报告（流式）+ "在聊天里追问"跳转 P1 → **挂科/重修高亮**（功能 D）
- **M2**：What-if / 目标 GPA 计算器（功能 A，纯本地）
- **M3**：成绩单分享卡片（功能 C，Compose → bitmap → 分享）
- **M4**：出分提醒（功能 B，WorkManager 后台静默登录 + diff + 通知）

**所有图表手写 Compose Canvas**（phosphor-green 终端风，零图表库依赖）。**成绩落 Room**（离线可看、跨学期累积趋势）。

**Explicitly out of scope：**

- 考试安排（ksap）、培养方案完成度、学业预警 —— 后续小阶段，本 spec 不含
- 课程难度 / 给分参考等需外部数据的功能
- BIT 以外学校 —— 同 P5，硬编码 BIT 端点
- 历史成绩的"版本对比"（每次同步覆盖式写入，不存历史快照；趋势来自当前库内的多学期数据）

---

## 2 · Architecture — Module Layout

```
core/util/
├─ GpaCalculator.kt          — 学分加权 GPA + What-if 反推/预测（纯函数）
└─ GradeBucketer.kt          — 成绩 → 分布桶（等级或分段计数）

core/charts/                 — 新子包：可复用的终端风 Canvas 图表
├─ LineChart.kt              — GPA 趋势折线（带点/网格/坐标轴标签）
└─ BarChart.kt              — 成绩分布柱状（水平条 + 计数标签）

core/notification/
├─ NotificationChannels.kt   — 【改】新增 GRADES_ID 渠道
└─ GradesNotifier.kt         — 【新】出分通知（功能 B）

core/workers/
└─ GradePollWorker.kt        — 【新】HiltWorker，定时静默查分（功能 B）

data/network/bit/            — 复用 P5 子包；仅扩展
├─ BitApiClient.kt           — 【改】新增 val cjcx 暴露成绩服务
├─ dto/{GradeRowDto, GradeRankDto, GradeTermDto}.kt   — 【新】（响应包装
│                              GradeListResponse/GradeRankResponse 同文件，沿用 P5 约定）
└─ service/BitCjcxService.kt — 【新】成绩查询 ehall app 端点 + 自有 warm-up

data/local/db/
├─ AppDatabase.kt            — 【改】version 7→8，注册 2 个新实体 + DAO
├─ entity/{GradeEntryEntity, TermRankEntity}.kt        — 【新】
└─ dao/GradesDao.kt          — 【新】

data/local/datastore/
├─ ImportCredentialPrefs.kt  — 【复用】P5 的 EncryptedSharedPreferences 凭据
└─ GradesSyncPrefs.kt        — 【新】DataStore：pollEnabled / pollIntervalHours /
                                 lastSyncAt / lastSeenSignature

domain/bitgrades/
├─ model/
│  ├─ SyncGradesStep.kt      — sealed；同步进度事件
│  ├─ GradesSyncError.kt     — sealed；复用 P5 ImportError 的分类思路
│  ├─ GradeBook.kt           — 内存聚合：List<TermGrades> + overall
│  ├─ TermGrades.kt          — 单学期：courses + weightedGpa + rank?
│  ├─ GradeItem.kt           — 单门课领域模型
│  └─ WhatIfPlan.kt          — 目标反推 / 预测的输入与结果
├─ SyncGradesUseCase.kt      — Flow<SyncGradesStep> 编排（登录→列表→详情→落库）
├─ MapGradeUseCase.kt        — GradeRowDto → GradeEntryEntity
├─ ComputeGpaUseCase.kt      — 库内成绩 → GradeBook（每学期加权 GPA + 总 GPA）
├─ ReplaceGradesUseCase.kt   — 覆盖式写入（wipe + rewrite，同 P5 策略 B）
├─ GpaPlannerUseCase.kt      — What-if 反推 / 预测（功能 A，纯本地，调 GpaCalculator）
├─ BuildGradeSummaryUseCase.kt — 脱敏成绩摘要文本（AI prompt + 聊天 seed 共用）
├─ AnalyzeGradesUseCase.kt   — Flow<String> 流式 AI 报告（调 LLMProvider）
├─ StartGradeChatUseCase.kt  — 建 chat session + seed 成绩上下文，返回 sessionId
└─ DetectNewGradesUseCase.kt — 签名 diff，找出本次新增成绩（功能 B）

feature/bitgrades/
├─ GradesNavGraph.kt         — 内部 NavHost：同步向导 + 成绩单 + What-if + 分享 + AI
├─ GradesViewModel.kt        — 成绩单 / 同步状态机
├─ GradesSyncViewModel.kt    — 同步向导状态机（复用 P5 wizard 模式）
└─ ui/
   ├─ GradesScreen.kt              — 成绩单主页
   ├─ GradesSyncScreen.kt          — 凭据 + 进度（复用 WizardScaffold / ErrorBanner）
   ├─ AiAnalysisSheet.kt           — AI 报告流式展示 + "在聊天里追问"
   ├─ WhatIfScreen.kt              — 目标 GPA 计算器（功能 A）
   ├─ ShareCardScreen.kt           — 分享卡片（功能 C）
   └─ components/
      ├─ GpaOverviewCard.kt
      ├─ TermGradeSection.kt       — 单学期分组（含挂科/重修高亮，功能 D）
      └─ (GPA/分布图表来自 core/charts)
```

**为什么复用 `data/network/bit/` 而非新建：** 成绩与课表是同一所学校、同一套 CAS 会话、同一个 cookie jar。两步拉取共用 `BitApiClient` 的 `@Named("bit")` OkHttpClient。仅 `BitCjcxService` 是成绩特有（不同 ehall appId + 不同 module path + 自有 warm-up）。

---

## 3 · Data Model — Room（version 7 → 8）

新增两张表。dev 期沿用 `fallbackToDestructiveMigration()`（项目既定：开发库数据可丢弃，不写保数据迁移）。

### 3.1 `GradeEntryEntity`

```kotlin
@Entity(
    tableName = "grade_entries",
    indices = [Index(value = ["termCode", "courseCode", "attemptType"], unique = true)],
)
data class GradeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termCode: String,        // XNXQDM, e.g. "2024-2025-2"
    val termName: String,        // 显示名，e.g. "2024-2025学年 第二学期"
    val courseName: String,      // KCM
    val courseCode: String,      // KCH / KCDM
    val credit: Double,          // XF
    val score: String,           // CJ —— 可能是数字"92"或等级"优"/"通过"
    val gradePoint: Double?,     // JD / XFJD —— BIT 直接返回的绩点；P/NP 课为 null
    val gradeLetter: String?,    // DJCJ 等级（若有）
    val category: String?,       // KCXZ 课程性质（必修/选修/...）
    val attemptType: String,     // 正常 / 重修 / 补考 —— 同课多次成绩靠它区分
    val isPass: Boolean,         // 派生：是否及格（挂科高亮用）
    val fetchedAt: Long,
)
```

唯一索引 `(termCode, courseCode, attemptType)` 保证幂等 upsert：重修/补考与正常成绩共存，再次同步同一条不会重复。

### 3.2 `TermRankEntity`

```kotlin
@Entity(tableName = "term_ranks")
data class TermRankEntity(
    @PrimaryKey val termCode: String,   // 单学期；总排名用保留键 "OVERALL"
    val termName: String,
    val weightedGpa: Double,            // 我们按学分加权算的（趋势用）
    val classRank: Int?,                // BJPM —— 详细信息端点返回；拿不到为 null
    val classTotal: Int?,
    val majorRank: Int?,                // ZYPM
    val majorTotal: Int?,
    val fetchedAt: Long,
)
```

排名来自"获取详细信息"端点（§4.4 两步拉取的第二步）。**字段名 BJPM/ZYPM 是假设，按 P5 方法真机确认。** 拿不到时整列留 null，UI 降级（只显示成绩与我们自算的 GPA，排名区显示"—"）。

### 3.3 `GradesDao`

```kotlin
@Query("SELECT * FROM grade_entries ORDER BY termCode DESC, courseName")
fun observeAll(): Flow<List<GradeEntryEntity>>

@Query("SELECT * FROM grade_entries")
suspend fun listAll(): List<GradeEntryEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAll(rows: List<GradeEntryEntity>)

@Query("DELETE FROM grade_entries")
suspend fun clearGrades(): Int

@Query("SELECT * FROM term_ranks") fun observeRanks(): Flow<List<TermRankEntity>>
@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRanks(rows: List<TermRankEntity>)
@Query("DELETE FROM term_ranks") suspend fun clearRanks(): Int
```

**AppDatabase 改动：** `entities += [GradeEntryEntity::class, TermRankEntity::class]`、`VERSION = 8`、新增 `abstract fun gradesDao(): GradesDao`。

---

## 4 · 网络层 — 复用 + `BitCjcxService`

### 4.1 复用（零改动）

`SsoLoginUseCase`（CAS 登录，AES-ECB / service-bound POST / 分类）、`BitApiClient.open(mode)/close()`、`@Named("bit")` OkHttpClient + `BitCookieJar`、`NetworkMode`、`BitUrlsConfig`。CAS 登录后种在 `.bit.edu.cn` 的父域 cookie 同样授权 cjcx 访问。

### 4.2 `BitApiClient` 扩展

```kotlin
val cjcx: BitCjcxService
    get() = jwappRetrofit?.create() ?: error("BitApiClient: session not open")
```

cjcx 与 wdkbby 同属 `jxzxehallapp.bit.edu.cn`，复用 `jwappRetrofit`（同 host、同 baseUrl）。无需第三个 Retrofit 实例。

### 4.3 `BitCjcxService` — 成绩查询 ehall app

```kotlin
interface BitCjcxService {
    // —— warm-up：每个 ehall app 各需自己的 appId 预热，否则 403 openresty ——
    @GET("jwapp/sys/cjcx/*default/index.do")
    suspend fun getIndex(): Response<ResponseBody>

    @GET("jwapp/sys/funauthapp/api/getAppConfig/cjcx-{appId}.do")
    suspend fun getAppConfig(@Path("appId") appId: String = "<TBD-真机确认>"): Response<ResponseBody>

    @GET("jwapp/i18n.do")
    suspend fun switchLang(
        @Query("appName") appName: String = "cjcx",
        @Query("EMAP_LANG") emapLang: String = "zh",
    ): Response<ResponseBody>

    // —— 第一步：成绩列表（某学期或全部）——
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/<TBD>.do")
    suspend fun getGrades(@Field("requestParamStr") requestParamStr: String): Response<GradeListResponse>

    // —— 第二步：获取详细信息（班级/专业排名），按学期 ——
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/<TBD-详情>.do")
    suspend fun getRankDetail(@Field("requestParamStr") requestParamStr: String): Response<GradeRankResponse>

    // 成绩查询 app 的学期列表（也可能复用 wdkbby 的；真机确认后取其一）
    @GET("jwapp/sys/cjcx/modules/cjcx/<TBD-学期>.do")
    suspend fun getTerms(): Response<TermListResponse>
}
```

**已知未知（按 P5 方法真机确认，§11 风险登记）：** cjcx 的 `appId`、各 module 的 `.do` 路径、`requestParamStr` 的 JSON 形状、成绩/排名响应的字段名（KCM/XF/CJ/JD/BJPM/ZYPM…）。P5 的经验：**单测全绿只证明代码符合假设，不证明假设正确**——这些必须真机抓包验证后再固化 fixture。

### 4.4 两步拉取流程（对应用户观察："点查成绩只见分数，点'获取详细信息'才有排名"）

```
warm-up(cjcx) → getTerms()                         // 学期列表
for each term (或一次性全量，真机确认接口能力):
    getGrades(term)        → GradeRowDto[]          // 第一步：分数/学分/绩点
getRankDetail(term/overall)→ GradeRankResponse      // 第二步：班级/专业排名百分比
```

成绩列表与排名解耦：第二步失败/无权限时，第一步结果照常落库，`TermRankEntity` 留 null。

---

## 5 · Domain 层

### 5.1 `GpaCalculator`（core/util，纯函数，功能 A 的数学核心）

```kotlin
object GpaCalculator {
    /** 学分加权 GPA：Σ(credit×point) / Σ(credit)，仅计入有绩点的课（排除 P/NP）。 */
    fun weightedGpa(items: List<Pair<Double, Double?>>): Double  // (credit, point?)

    /** 目标反推：要把总 GPA 提到 target，剩余 remainingCredits 学分平均需多少绩点。
     *  required = (target×(done+remaining) − weightedSumDone) / remaining
     *  返回 RequiredAvg(value, achievable) —— value>满分上限时 achievable=false。 */
    fun requiredAverageForTarget(
        doneCredits: Double, weightedSumDone: Double,
        remainingCredits: Double, targetGpa: Double, maxPoint: Double = 4.0,
    ): RequiredAvg

    /** 预测：给未出分课程填假设绩点，算预计新总 GPA。 */
    fun project(currentItems: List<Pair<Double, Double?>>,
                predicted: List<Pair<Double, Double>>): Double
}
```

`maxPoint` 默认 4.0，真机确认 BIT 绩点上限后改默认值。全部纯函数 → 直接 JVM 单测，覆盖：无已修课、target 不可达、remaining=0（除零保护）。

### 5.2 `MapGradeUseCase`

`GradeRowDto → GradeEntryEntity`。字段映射（**TBD 字段名真机确认**）：

| BIT 字段(假设) | 我方字段 | 备注 |
|---|---|---|
| KCM | courseName | 必填，缺失→ParseFail |
| KCH / KCDM | courseCode | |
| XF | credit | toDouble，缺失→0.0 |
| CJ | score | 原样保留字符串（"92"/"优"/"通过"） |
| JD / XFJD | gradePoint | toDouble?；P/NP 为 null |
| DJCJ | gradeLetter | 可空 |
| KCXZ | category | 可空 |
| (重修标记字段) | attemptType | 默认"正常" |
| 派生 | isPass | gradePoint!=null && >0，或 score∈{优,良,中,及格,通过} |

### 5.3 `ComputeGpaUseCase`

库内 `List<GradeEntryEntity>` → `GradeBook`：按 termCode 分组 → 每组 `weightedGpa` + 课程列表 → 总 GPA（全量加权）。趋势折线、概览卡、分布图、AI 摘要都消费 `GradeBook`，与排名表 join 出 `TermGrades.rank`。

### 5.4 `ReplaceGradesUseCase`（覆盖式，策略 B）

```kotlin
suspend fun replace(entries: List<GradeEntryEntity>, ranks: List<TermRankEntity>) {
    dao.clearGrades(); dao.clearRanks()
    dao.upsertAll(entries); dao.upsertRanks(ranks)
}
```

成绩全量覆盖（不像课表要保 MANUAL，成绩无手输来源）。事务内执行。

### 5.5 `SyncGradesUseCase` — 编排（Flow<SyncGradesStep>）

```kotlin
sealed interface SyncGradesStep {
    object LoggingIn : SyncGradesStep
    object FetchingTerms : SyncGradesStep
    data class FetchingGrades(val termCode: String) : SyncGradesStep
    object FetchingRanks : SyncGradesStep
    object Persisting : SyncGradesStep
    data class Done(val termCount: Int, val courseCount: Int) : SyncGradesStep
    data class Failed(val err: GradesSyncError) : SyncGradesStep
}
```

`finally { apiClient.close() }` 始终执行（成功/失败/取消都丢 Retrofit + cookie）。与 P5 不同：成绩同步**无 Preview 确认环节**（不覆盖手输数据，直接落库），流程更短。

### 5.6 AI 分析（功能：报告 + 可追问）

- **`BuildGradeSummaryUseCase`**：`GradeBook` → 脱敏紧凑文本。**不含姓名/学号**；含：各学期课程名+学分+成绩+绩点、每学期 GPA、总 GPA、专业排名百分比（若有）。课程名非 PII，保留以利分析。
- **`AnalyzeGradesUseCase`**：`flow<String>`，调 `LLMProvider.generate()` 流式输出。system prompt 约束输出结构（趋势 / 强项 / 弱项 / 可执行建议），中文、终端风、无客套（沿用 P1 SYSTEM_PROMPT 风格）。
- **`StartGradeChatUseCase`**：`chatRepo.createSession("成绩分析 · <最近学期>")` → `appendMessage(SYSTEM, gradeSummary)` → 返回 sessionId。SYSTEM 消息在聊天 UI 隐藏、但进入 LLM 上下文（`SendMessageUseCase` 已把 SYSTEM 历史发给模型）。UI 拿 sessionId 走 AppNavHost 跳 P1 聊天。

### 5.7 `DetectNewGradesUseCase`（功能 B）

成绩签名 = `sortedSet of "termCode|courseCode|attemptType|score"`。`GradesSyncPrefs.lastSeenSignature` 存上次集合（持久化为换行拼接的字符串或 hash 集）。本次拉取后求差集 → 新增条目列表 → 供 `GradesNotifier` 组装通知文案。首次同步（无 lastSeen）不通知，只建基线。

---

## 6 · UI（feature.bitgrades）

终端风沿用全局主题（phosphor-green / 扫描线 / 等宽）。所有图表手写 Canvas。

### 6.1 成绩单主页 `GradesScreen`

```
$ transcript

  总 GPA  3.62      总学分 92.5      专业排名  前 15%
  ┌──────────────────────────────────────────┐
  │ GPA 趋势                                   │
  │ 4.0┤            ╭─●                         │
  │ 3.5┤      ╭─●──╯                            │
  │ 3.0┤  ●──╯                                  │
  │    └──┬───┬───┬───┬                         │
  │      大01 大02 夫01 夫02                     │  ← Canvas LineChart
  ├──────────────────────────────────────────┤
  │ 成绩分布                                    │
  │ A ████████████ 12                          │
  │ B ███████ 7                                │  ← Canvas BarChart
  │ C ██ 2   D ▌1(重修)                        │
  └──────────────────────────────────────────┘

  ▾ 2024-2025-2   GPA 3.71   班级 5/32  专业 18/120
     高等数学A   5.0  92  4.0
     线性代数    3.0  88  3.7
     大学物理    4.0  55  0.0   ⚠挂科         ← 功能 D 高亮
  ▸ 2024-2025-1   GPA 3.55
  ...

  [ 生成 AI 分析 ]   [ 目标 GPA 计算器 ]   [ 分享成绩单 ]   [ ↻ 同步 ]
```

- 空库态：显示"还没有成绩数据 [从教务系统查询成绩]" CTA → 进同步向导
- 学期分组可折叠；挂科（!isPass）红色描边 + ⚠，重修标 attemptType
- 数据来自 `observeAll()` + `observeRanks()` → `ComputeGpaUseCase` → `GradeBook`（响应式，落库后自动刷新）

### 6.2 同步向导 `GradesSyncScreen`

复用 P5 的 `WizardScaffold` + `ErrorBanner` + 凭据/进度模式与 `ImportCredentialPrefs` 预填/记住密码逻辑。比 P5 少一屏——无学期选择 Preview（默认全量或当前+历史，真机确认接口后定），无确认覆盖环节。进度屏映射 `SyncGradesStep` 打勾。

### 6.3 AI 报告 `AiAnalysisSheet`

底部 sheet / 全屏：点"生成 AI 分析"→ 流式渲染 markdown 报告（复用聊天的 markdown 渲染）。底部 `[在聊天里追问 →]` → `StartGradeChatUseCase` → 跳 P1 带成绩上下文的新会话。

### 6.4 What-if 计算器 `WhatIfScreen`（功能 A，M2）

```
$ gpa-planner

  当前  总学分 92.5   总 GPA 3.62

  模式  ● 目标反推   ○ 预测

  [目标反推]
   目标总 GPA   [3.80]
   剩余学分     [30]
   → 剩余课程平均需达  3.92 绩点   ✓可行
                       （若 >4.0 显示"✗ 已不可达"）
```

纯本地，输入即算（`GpaPlannerUseCase` → `GpaCalculator`）。两模式：目标反推 / 按假设分数预测。

### 6.5 分享卡片 `ShareCardScreen`（功能 C，M3）

终端风成绩摘要卡（总 GPA / 排名 / mini 趋势 / 学期数）→ Compose `rememberGraphicsLayer().toImageBitmap()` 截图 → 存 cacheDir PNG → 复用 P2 扫描已有的 FileProvider 走 `ACTION_SEND` 分享。默认脱敏（不含姓名/学号，用户可选是否含课程明细）。

---

## 7 · 出分提醒（功能 B，M4）

### 7.1 调度

- `GradePollWorker : CoroutineWorker`（HiltWorker），`PeriodicWorkRequest`，默认间隔 6h（`GradesSyncPrefs.pollIntervalHours`，最小 WorkManager 限制 15min；UI 给 3/6/12h 档），约束 `NetworkType.CONNECTED`，指数退避。
- 仅当 `pollEnabled && ImportCredentialPrefs 有保存密码` 才入队；否则设置项灰显并提示"需先在查询时勾选记住密码"。
- 复用 P4 的 WorkManager 初始化（manifest provider 那套）；`BootCompletedReceiver` 已存在 → 顺带重排周期任务。

### 7.2 Worker 逻辑

```
若 !pollEnabled → return success（自我退队）
open(LOCAL) → SsoLogin(saved creds)
  ├─ WrongCredentials/Captcha/Locked → 关 pollEnabled + 发"自动查分失败，请重新登录"通知 + 停止（避免锁号循环）
  └─ Success → warm-up(cjcx) → getGrades(全量)
DetectNewGradesUseCase(新签名 vs lastSeenSignature)
  ├─ 有新增 → upsert 落库 + GradesNotifier 发"N 门新成绩：线性代数 92 …"
  └─ 无新增 → 静默
更新 lastSeenSignature + lastSyncAt → close()
```

首次（无基线）只建基线不发通知。`finally { apiClient.close() }`。

### 7.3 通知

`NotificationChannels` 新增 `GRADES_ID = "grade_updates"`（IMPORTANCE_DEFAULT，"成绩更新"）。`GradesNotifier` 仿 `TimelineNotifier`，点击 deeplink 进 `GradesScreen`。

### 7.4 风险

重复自动登录可能触发 BIT 验证码 / 锁号。缓解：默认低频（6h）、单次 worker 内复用一次会话、**任何登录失败立即停轮并通知**（不重试硬撞）、间隔可调但有下限。

---

## 8 · Error Handling

复用 P5 `ImportError` 分类思路，新建 `GradesSyncError`：

| 变体 | 触发 | UX |
|---|---|---|
| `WrongCredentials` / `AccountLocked` / `CaptchaRequired` | CAS 登录失败（同 P5 分类） | 同步屏红/黄 banner + 清 Keystore（凭据错时）+ 复用 P5 文案与"打开浏览器"按钮 |
| `NetworkFail` | IOException/超时 | banner + 重试 |
| `ParseFail(msg)` | 成绩/排名 JSON 结构变 | banner + 反馈入口 + 截断 msg |
| `EmptyGrades` | 成绩接口 0 行 | 成绩单空态"教务系统暂无成绩" |
| `RankUnavailable` | 仅第二步失败 | **非致命**：成绩照常落库，排名留 null，成绩单排名区显示"—" |

后台 worker 的失败不弹 banner，按 §7.2 处理（停轮 + 通知）。

---

## 9 · Testing Strategy

| 层 | 类型 | fixtures |
|---|---|---|
| `GpaCalculator` | JVM 单测 | 加权/反推/预测/除零/不可达/空集 |
| `GradeBucketer` | JVM 单测 | 数字分段、等级、含重修 |
| `MapGradeUseCase` | JVM 单测 | 典型课、P/NP 课、重修课、缺字段 |
| `ComputeGpaUseCase` | JVM 单测 | 多学期分组、总 GPA、与排名 join |
| `BitCjcxService` | JVM 单测 | MockWebServer + 脱敏真机 JSON |
| `SyncGradesUseCase` | JVM 单测 | mockk 全栈，断言 `Flow<SyncGradesStep>` 序列 + 失败/排名降级路径 |
| `DetectNewGradesUseCase` | JVM 单测 | 首次基线、有新增、无新增、重修新增 |
| `ReplaceGradesUseCase` | Room instrumented | 覆盖式写入正确性 |
| `GradesViewModel` | JVM 单测 | 状态机 + 错误映射 |
| `GradePollWorker` | JVM 单测 | mockk，登录失败停轮、新成绩发通知、首次建基线 |
| 真机 DoD | 手动 | 真账号端到端：成绩 + 排名两步拉取（校内） |

**Fixture 协议同 P5**：真机 DoD 时用 `HttpLoggingInterceptor` 抓真实响应 → 脱敏 → 存 `app/src/test/resources/bit-fixtures/cjcx-*.json` → 单测离线加载。**committed code 中日志级别永远不得为 BODY**（会泄露加密密码与 Set-Cookie）。

---

## 10 · 入口

- **Settings →「从教务系统查询成绩」**（常驻入口，仿 P5 导入入口）→ 有数据进 `GradesScreen`，无数据进同步向导
- **Settings →「成绩更新提醒」**（开关 + 间隔，功能 B）；未记住密码时灰显
- 成绩单主页底部按钮：AI 分析 / 目标计算器 / 分享 / 同步

是否新增底部导航 Tab 暂不做（YAGNI）——成绩是低频查看，Settings 入口足够；后续若高频再提。

---

## 11 · Risk Register

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| cjcx appId / 端点 / 字段名与假设不符 | 高 | 高（拉取失败） | **真机抓包验证后再固化**（P5 同款方法论）；ParseFail 给用户反馈路径 |
| "获取详细信息"排名接口形状未知 | 高 | 中（无排名） | 两步解耦，排名降级 null，成绩仍可用 |
| 后台轮询触发验证码/锁号 | 中 | 高 | 低频 + 失败即停轮 + 通知，不硬重试 |
| 成绩为等级制（优/良/通过）无数字绩点 | 中 | 中（GPA 偏） | gradePoint 可空，加权只计有绩点的课；分布图按等级桶 |
| BIT 绩点上限非 4.0（可能 4.5/5.0） | 中 | 低（What-if 可达判断偏） | maxPoint 真机确认后设默认；UI 标注口径 |
| 成绩数据发给第三方 LLM 的隐私顾虑 | 中 | 中 | 默认脱敏（无姓名/学号）；分析为用户主动触发；沿用 P1 已接受的 LLM 通道 |
| Compose 截图 API 版本兼容 | 低 | 低 | 主用 `rememberGraphicsLayer`（Compose 1.7+，本项目 BOM 满足）；否则 View.drawToBitmap 兜底 |

---

## 12 · Milestone / Phase Breakdown

单 PR `feature/p6-bit-grades`，按里程碑分阶段（每阶段 spec-compliance + code-review 子代理两道关，沿用 P4/P5 节奏）：

### M1 · 核心成绩闭环（tag `p6-grades-core`）
- 网络：`BitCjcxService` + DTO + `BitApiClient.cjcx` + MockWebServer 单测
- 数据：2 实体 + `GradesDao` + AppDatabase 7→8
- 领域：`MapGradeUseCase` / `ComputeGpaUseCase` / `ReplaceGradesUseCase` / `SyncGradesUseCase`
- 图表：`core/charts` LineChart + BarChart（Canvas）
- UI：`GradesScreen` + `GradesSyncScreen`（复用 P5 wizard）+ 功能 D 挂科高亮
- AI：`BuildGradeSummaryUseCase` / `AnalyzeGradesUseCase` / `StartGradeChatUseCase` + `AiAnalysisSheet` + 跳聊天
- Settings 入口 + **真机 DoD（成绩 + 排名两步拉取，校内）**

### M2 · What-if 计算器（tag `p6-whatif`）
- `GpaCalculator` 反推/预测 + `GpaPlannerUseCase` + `WhatIfScreen`（纯本地，单测重）

### M3 · 分享卡片（tag `p6-sharecard`）
- `ShareCardScreen` + Compose 截图 + FileProvider 分享

### M4 · 出分提醒（tag `p6-grade-poll`）
- `GradesSyncPrefs` + `DetectNewGradesUseCase` + `GradePollWorker` + `GradesNotifier` + GRADES_ID 渠道 + Settings 开关 + worker 单测

**M1 先闭环可合并**；M2–M4 增量叠加。校外 WebVPN 真机验证沿用 P5 标 pending。

---

## 13 · Open Questions（不阻塞实现）

- cjcx 是否支持一次性全量拉取，还是必须逐学期？→ 真机确认；接口若只给单学期则循环拉。
- 排名是学期级还是累计级（或两者）？→ 真机确认后决定 `TermRankEntity` 是否需要额外"累计排名"行（已用 OVERALL 键预留）。
- AI 报告是否需要可选"含/不含课程明细"开关？→ 默认含；若用户在意隐私再加，暂不做。

---

## 14 · Definition of Done

M1 真机 DoD：
- [ ] Settings →「从教务系统查询成绩」启动向导；空库显示 CTA
- [ ] 校内真账号端到端：登录 → 拉成绩列表 → 拉排名详情 → 落库 → 成绩单显示概览/趋势/分布/分学期列表
- [ ] 挂科/重修在列表中正确高亮
- [ ] GPA 趋势折线、成绩分布柱状用 Canvas 正确渲染（多学期）
- [ ] 排名详情拿不到时优雅降级（成绩照常，排名"—"）
- [ ] 「生成 AI 分析」流式输出报告；「在聊天里追问」跳 P1 且带成绩上下文
- [ ] 密码错误等失败走正确 banner（复用 P5）
- [ ] 抓取的脱敏 fixture 已 commit；新单测 + 既有全部单测通过
- [ ] committed code 日志级别为 BASIC（非 BODY）

后续里程碑各自 DoD：What-if 反推数学正确（含不可达判定）；分享卡片能生成并分享；出分提醒能在后台检出新成绩并通知、登录失败能停轮。
```