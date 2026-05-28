# P6 · M4 出分提醒 — Design Spec

**Status:** Approved (brainstorm completed 2026-05-27)
**Owner:** ggggh123
**Builds on:** P5 BIT 登录基建 + P6 M1 jsxsd 同步 + F1 cjfx 详情 + P4 WorkManager/Notification 基建

---

## 1 · Scope & Goals

后台定时静默登录 BIT 教务,对比上一次签名集发现**新成绩**(包括重修/补考产生的新条目),通过通知告知用户。M1 同步是用户主动触发;M4 是被动、低频、风险敏感的后台版本。

**In scope:**
- Settings 里 opt-in 开关 + 3 档间隔(3h / 6h / 12h),默认 off、默认 6h
- `WorkManager.PeriodicWorkRequest` 周期任务,约束 `NetworkType.CONNECTED`
- 静默登录 → 拉 cjcx_list → diff 签名集 → 仅对**新增条目**并发拉 cjfx 详情 → upsert → 推聚合通知
- 严格失败停轮:任意 CAS 鉴权失败(WrongCreds / Captcha / Locked)立即 `pollEnabled=false` + 清凭据 + 推送"自动查分失败,请重新登录"
- 首次启用建立基线(不发通知)
- 通知 deeplink 进成绩单页
- `BootCompletedReceiver` 已存在,顺带重排此任务

**Explicitly out of scope:**
- "安静时间"/小时段屏蔽 — YAGNI(3-12h 间隔很少正好砸睡眠时段)
- WebView/captcha 自动破解 — 显式停轮,推通知让用户去解决
- iOS 后台(项目 Android-only)
- 多账号(沿用 P5 单凭据存储)

---

## 2 · Architecture — Module Layout

```
core/notification/
├─ NotificationChannels.kt     — 【改】新增 GRADES_ID = "grade_updates"
└─ GradesNotifier.kt           — 【新】发"N 门新成绩"聚合通知

core/workers/
├─ GradePollWorker.kt          — 【新】@HiltWorker, 周期 worker 主逻辑
└─ BootCompletedReceiver.kt    — 【改】重排本任务(同已有的 reschedule)

data/local/datastore/
└─ GradesSyncPrefs.kt          — 【新】pollEnabled / pollIntervalHours / lastSyncAt /
                                    lastSeenSignature(Set<String>)

domain/bitgrades/
├─ DetectNewGradesUseCase.kt   — 【新】签名 diff
└─ SyncGradesUseCase.kt        — 【改】拆出 `syncCore(skipFullDetail: Boolean)`
                                    后台 Worker 直接调,跳过全量 cjfx;只对 diff
                                    出的新条目并发拉 cjfx 详情

feature/bitgrades/
└─ GradesPollScheduler.kt      — 【新】WorkManager enqueue/cancel/reschedule
                                    薄封装(VM 调用,与 BootReceiver 复用)

feature/settings/
├─ ui/GradesPollSettingsScreen.kt — 【新】开关 + 3 档 chip + "上次同步于"
└─ vm/GradesPollSettingsViewModel.kt — 【新】

ui/navigation/NavRoutes.kt     — 【改】SETTINGS_GRADES_POLL = "settings/grades-poll"
ui/AppNavHost.kt               — 【改】注册路由
feature/settings/ui/SettingsScreen.kt — 【改】「成绩更新提醒 →」入口行
```

---

## 3 · Data Model — `GradesSyncPrefs`(DataStore Preferences,无 Room 变更)

| Key | Type | Default | 含义 |
|---|---|---|---|
| `grades_poll_enabled` | Boolean | `false` | 用户是否开启后台轮询 |
| `grades_poll_interval_hours` | Int | `6` | 3 / 6 / 12 之一 |
| `grades_last_sync_at` | Long? | null | 上次成功 sync(成功落库或首次基线)的时间戳 |
| `grades_last_seen_signature` | String? | null | 上次签名集,`\n`-分隔序列化的 `termCode|courseCode|attemptType|score` |

**签名格式**:`"${termCode}|${courseCode}|${attemptType}|${score}"`,组成 `Set<String>`。
**diff**:`currentSet − lastSeenSet` = 新增条目签名集 → 由 entries 反查得到新 `GradeEntryEntity` 列表。
**为什么含 `attemptType` + `score`**:重修出分(同课不同 attemptType)算新条目;同课同 attemptType 但 score 变了(教师修正成绩)也算新条目。

---

## 4 · `SyncGradesUseCase` 拆分以复用

当前 `SyncGradesUseCase.sync(req)` 是主线同步:登录 → 拉 list → 并发拉所有课 cjfx → 落库 → 推 Flow<SyncGradesStep>。

**改造**:抽出 `syncCore(req, fullDetail: Boolean)`,新增可选参数:
- `fullDetail = true` → 旧行为(主线手动同步用)
- `fullDetail = false` → 跳过全量 cjfx,只落基础 entries;**返回新落库的 entries 给 Worker**(Worker 决定后续是否并发拉详情)

**生命周期约定**:`syncForBackground` **不管 apiClient 的 open/close**——由 Worker 持有(Worker 在 try-finally 里 open/close,期间 syncForBackground 和后续 enrichDetailsConcurrently 共享同一个 session)。

```kotlin
// In SyncGradesUseCase (新增方法,与既有 sync(req): Flow<...> 并列)
suspend fun syncForBackground(req: GradesSyncRequest): BackgroundSyncResult {
    // 假设 apiClient 已 open;不在此处 close
    val login = ssoLogin.invoke(apiClient, req.username, req.password)
    val authErr = login.toGradesError()
    if (authErr != null) return BackgroundSyncResult.Stop(authErr)

    return try {
        apiClient.cas.activateService(JWMS_SERVICE)
        val resp = apiClient.jwms.getScoreListHtml()
        val html = (resp.body() ?: resp.errorBody())?.string().orEmpty()
        if (parser.isReviewGated(html)) return BackgroundSyncResult.Stop(GradesSyncError.NeedReview)
        val now = System.currentTimeMillis()
        val entries = parser.parse(html, now)
        BackgroundSyncResult.Ok(entries)
    } catch (io: IOException) {
        BackgroundSyncResult.Transient
    } catch (e: Throwable) {
        BackgroundSyncResult.Transient
    }
}
```

> 注:M1 主线 sync 是覆盖式(`ReplaceGradesUseCase` clear + insert)。后台 worker 应当**仅 upsert 新增/变化条目**,不覆盖既有(Room 的 `@Insert(REPLACE)` + 唯一索引 `(termCode, courseCode, attemptType)` 行为是:同键自动替换、不同键插入)。这天然支持增量,不会清掉用户上次同步的详情字段。

---

## 5 · `DetectNewGradesUseCase`

```kotlin
class DetectNewGradesUseCase @Inject constructor(
    private val prefs: GradesSyncPrefs,
) {
    suspend fun invoke(currentEntries: List<GradeEntryEntity>): DiffResult {
        val currentSig = currentEntries.map { it.toSignature() }.toSet()
        val lastSig = prefs.lastSeenSignature()
        val newSigs = currentSig - lastSig
        val newEntries = currentEntries.filter { it.toSignature() in newSigs }
        return DiffResult(newEntries = newEntries, fullSignature = currentSig, isFirstRun = lastSig.isEmpty())
    }
    private fun GradeEntryEntity.toSignature() = "$termCode|$courseCode|$attemptType|$score"
}
data class DiffResult(val newEntries: List<GradeEntryEntity>, val fullSignature: Set<String>, val isFirstRun: Boolean)
```

**首次运行**(`lastSig` 为空):返回 `isFirstRun=true`,Worker 据此**只建基线、不通知**。

---

## 6 · `GradePollWorker`

```kotlin
@HiltWorker
class GradePollWorker @AssistedInject constructor(
    @Assisted appContext: Context, @Assisted params: WorkerParameters,
    private val pollPrefs: GradesSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val sync: SyncGradesUseCase,
    private val detector: DetectNewGradesUseCase,
    private val detailParser: JsxsdDetailParser,
    private val apiClient: BitApiClient,
    private val gradesDao: GradesDao,
    private val notifier: GradesNotifier,
    private val scheduler: GradesPollScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.isEnabled()) return Result.success()             // 自我退队
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        return try {
            apiClient.open(NetworkMode.LOCAL)
            val result = sync.syncForBackground(
                GradesSyncRequest(creds.username, creds.password, NetworkMode.LOCAL, rememberPwd = true),
            )
            when (result) {
                is BackgroundSyncResult.Stop -> {
                    // 包含 WrongCreds/Captcha/Locked/NeedReview/ParseFail 等
                    // 任何用户介入才能修复的状态 — 立即停轮,通知用户。
                    if (result.reason is GradesSyncError.WrongCredentials
                        || result.reason is GradesSyncError.AccountLocked) {
                        credPrefs.clear()
                    }
                    pollPrefs.setEnabled(false)
                    scheduler.cancel()
                    notifier.notifyStop(result.reason)
                    Result.success()
                }
                is BackgroundSyncResult.Transient -> Result.retry()
                is BackgroundSyncResult.Ok -> {
                    val diff = detector.invoke(result.entries)
                    if (!diff.isFirstRun && diff.newEntries.isNotEmpty()) {
                        val enriched = enrichDetailsConcurrently(diff.newEntries)
                        gradesDao.upsertAll(enriched)
                        notifier.notifyNewGrades(enriched)
                    }
                    // 首次基线:仅写 signature,不通知。
                    pollPrefs.setLastSeenSignature(diff.fullSignature)
                    pollPrefs.setLastSyncAt(System.currentTimeMillis())
                    Result.success()
                }
            }
        } catch (e: Throwable) {
            Result.retry()
        } finally {
            apiClient.close()
        }
    }

    private suspend fun enrichDetailsConcurrently(entries: List<GradeEntryEntity>): List<GradeEntryEntity> =
        coroutineScope {
            entries.map { e ->
                async {
                    val path = e.detailPath ?: return@async e
                    runCatching {
                        val r = apiClient.jwms.getCourseDetailHtml(path)
                        if (r.isSuccessful) detailParser.parse((r.body() ?: r.errorBody())?.string().orEmpty())
                        else null
                    }.getOrNull()?.let { info ->
                        e.copy(courseAvg = info.courseAvg, classRankText = info.classRankText, majorRankText = info.majorRankText)
                    } ?: e
                }
            }.awaitAll()
        }
}
```

**返回值约定**(`SyncGradesUseCase.syncForBackground`):
```kotlin
sealed interface BackgroundSyncResult {
    data class Ok(val entries: List<GradeEntryEntity>) : BackgroundSyncResult   // 含空表 — 没有成绩也是 Ok
    data class Stop(val reason: GradesSyncError) : BackgroundSyncResult         // 需用户介入 → cancel + notify
    object Transient : BackgroundSyncResult                                     // 网络/未知错 → Result.retry()
}
```

`Stop` 包含:`WrongCredentials` / `CaptchaRequired` / `AccountLocked` / `NeedReview` / `ParseFail`(BIT 接口可能改版,用户需要看到反馈而不是无声卡住)。
`Transient` 包含:`NetworkFail`(IOException) / `Unexpected`(其它异常,可能瞬时)。

(主线 `sync(req): Flow<SyncGradesStep>` 保留不变,继续给前台用。`syncForBackground` 直接返回,不走 Flow。)

---

## 7 · `GradesPollScheduler`

```kotlin
@Singleton
class GradesPollScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GradesSyncPrefs,
) {
    fun rescheduleFromPrefs() {
        if (prefs.isEnabledBlocking()) enqueue(prefs.intervalHoursBlocking()) else cancel()
    }
    fun enqueue(hours: Int) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val req = PeriodicWorkRequestBuilder<GradePollWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req,
        )
    }
    fun cancel() { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    companion object { const val WORK_NAME = "grades-poll" }
}
```

`BootCompletedReceiver` 在已有 `RescheduleRemindersWorker` 后面再加一行调 `gradesPollScheduler.rescheduleFromPrefs()`。

---

## 8 · `GradesNotifier`

仿 `TimelineNotifier`。两类通知,GRADES_ID 渠道(IMPORTANCE_DEFAULT,"成绩更新"):

```kotlin
fun notifyNewGrades(entries: List<GradeEntryEntity>) {
    val title = "${entries.size} 门新成绩"
    val lines = entries.take(5).joinToString("\n") { "• ${it.courseName} ${it.score}" }
    val more = if (entries.size > 5) "\n…还有 ${entries.size - 5} 门" else ""
    // BigTextStyle: title + lines + more
    // tap → deeplink to GradesScreen(NavRoutes.GRADES) via PendingIntent
}

fun notifyStop(reason: GradesSyncError) {
    val text = when (reason) {
        GradesSyncError.WrongCredentials -> "密码错误,凭据已清 — 请打开 App 重新登录"
        GradesSyncError.AccountLocked    -> "账号已锁定,请稍后或修改密码后再启用"
        GradesSyncError.CaptchaRequired  -> "教务系统要求验证码,请到网页端手动登录一次后重启"
        GradesSyncError.NeedReview       -> "教务提示未完成评教,请先评教后再启用"
        is GradesSyncError.ParseFail     -> "教务接口结构可能变化,请等 App 更新"
        else                              -> "未知错误,请打开 App 查看"
    }
    // Title: "成绩自动查询已停止"
    // Text: 上面文案
    // tap → SETTINGS_GRADES_POLL
}
```

ID 冲突:同 type 用同 ID(覆盖式),不堆叠多条。

---

## 9 · Settings UI(`GradesPollSettingsScreen`)

```
$ grades-poll

  [✓] 后台自动查分                          ← 总开关(默认 off)

  间隔:    [3h]  [6h✓]  [12h]               ← 三个 FilterChip
  
  上次同步:  2 小时前 / 2026-05-27 18:30     ← lastSyncAt formatted
  
  ⓘ 后台将以你保存的凭据每 N 小时静默登录教务,
    比对发现新成绩后通知你。若登录失败(密码错/
    锁号/需要验证码) 将立即停止,需手动重启。
    
  ⚠ 必须先在「从教务系统查询成绩」时勾选记住密码
```

未保存凭据 → 总开关灰显 + 显示提示"先在成绩查询时勾选记住密码"。开关 on 时立即 `scheduler.enqueue(prefs.interval)` ; off 时 `scheduler.cancel()` 。改间隔时如已 enabled 则用新值重新 enqueue。

Settings 主页加入口行:`「成绩更新提醒 →」副标题「后台查分新成绩并通知」`。

---

## 10 · 错误处理

| 情况 | 行为 | 备注 |
|---|---|---|
| `pollEnabled=false`(用户关了) | Worker `Result.success()` 自动结束 | UI 关时 `scheduler.cancel()` 彻底清掉 |
| 凭据被清(用户手动) | Worker 自我退队 + `pollEnabled=false` | 下次开 UI 重新提示 |
| `WrongCredentials` / `AccountLocked` | **Stop** → 清凭据 + `pollEnabled=false` + `cancel()` + 通知 | 严格停,避免锁号循环 |
| `CaptchaRequired` | **Stop** → `pollEnabled=false` + `cancel()` + 通知 | 不清凭据(密码可能仍对) |
| `NeedReview`(评教未完) | **Stop** → 通知"教务提示评教未完成,请先评教" | 评教完成后用户重启 |
| `ParseFail`(BIT 改页) | **Stop** → 通知"接口结构变化,请等更新" | 避免无声卡住;不清凭据 |
| `IOException`(网络故障) | **Transient** → `Result.retry()` | WorkManager 指数退避 30min 起 |
| `Unexpected`(其它异常) | **Transient** → `Result.retry()` | 避免吞 bug;若连续 N 次失败 WorkManager 自动停 |
| 成绩列表空 | **Ok**(空)→ 写空 signature + 不通知 | 无成绩是正常初始态 |

---

## 11 · 测试

| 层 | 方法 | 重点 |
|---|---|---|
| `DetectNewGradesUseCase` | JVM 单测 | 基线/无新增/纯新增/重修产生新签名 |
| `GradePollWorker` | JVM 单测(mockk) | 4 条主路径:未启用→success、登录失败→停 + 通知、首次基线→无通知、有新增→通知 |
| `GradesSyncPrefs` | JVM 单测 | Signature Set ↔ String 序列化 round-trip |
| `GradesPollScheduler` | JVM 单测 | enqueue 参数正确性(间隔/约束/退避);**WorkManager 行为本身不测**(库内部) |
| `GradesPollSettingsViewModel` | JVM 单测 | 开关与 prefs/scheduler 联动 |
| 真机 DoD | 手动 | 开启后**手动加速触发**(WorkManager `enqueueUniqueWork` + OneTime 触发一次)看通知能否真发出 |

---

## 12 · Phase Breakdown(单一里程碑,~15 任务)

1. `GradesSyncPrefs`(DataStore + Signature 序列化)+ 单测
2. `DetectNewGradesUseCase` + 单测
3. `SyncGradesUseCase.syncForBackground(req)` 抽取 + 单测(已有主 sync 不动)
4. `NotificationChannels.GRADES_ID` + `GradesNotifier` + 通知 PendingIntent deeplink
5. `GradesPollScheduler`(WorkManager enqueue/cancel) + 单测
6. `GradePollWorker`(HiltWorker) + 单测(mockk 4 路径)
7. `BootCompletedReceiver` 加一行 reschedule(改动 1 行)
8. `GradesPollSettingsScreen` + `GradesPollSettingsViewModel` + 单测
9. Settings 主页入口行 + NavRoutes + AppNavHost 注册
10. 真机 DoD(校内 + 手动加速触发验证)

---

## 13 · Risk Register

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| BIT 检测高频自动登录 → 锁号 | 中 | 高 | 间隔 ≥ 3h、严格停轮、智能增量(总请求数最小) |
| 用户改密码后忘开关 → 持续失败 | 中 | 低 | 单次失败即停 + 推送提示,用户察觉后手动重启 |
| 通知打扰过度 | 低 | 低 | 同 type 同 ID,聚合一条;只在 newEntries 非空时推 |
| WorkManager 在国产 ROM(MIUI/EMUI) 被杀 | 中 | 中 | 已是 `ExistingPeriodicWorkPolicy.UPDATE`;若用户报错,提示加入电池白名单(未来 polish) |
| 凭据加密存储泄漏 | 低 | 高 | 复用 P5 EncryptedSharedPreferences(Android Keystore 后端) |

---

## 14 · DoD(真机)

- [ ] Settings →「成绩更新提醒」入口可见
- [ ] 未存凭据时开关灰显 + 提示
- [ ] 存有凭据时开关可开 → `scheduler.enqueue` 成功(`adb shell dumpsys jobscheduler | grep grades-poll`)
- [ ] 间隔 chip 切换实时生效
- [ ] **手动触发一次** Worker:有新成绩 → 通知能弹、点开 deeplink 进成绩单页
- [ ] 故意输错密码触发 → 严格停 + 通知"自动查询已停止"
- [ ] 关掉开关 → `cancel()` → 周期任务从 jobscheduler 消失
- [ ] 全量单测绿
- [ ] committed code 日志级别为 BASIC

---

## 15 · Open Questions(不阻塞实现)

- 是否给国产 ROM 加白名单引导?后续 polish 再说
- Worker 是否在 `setExpedited()` 优先级跑?默认普通即可,3h+ 间隔不急
- 多账号支持?M4 单账号(同 P5),未来 P7+ 再说
