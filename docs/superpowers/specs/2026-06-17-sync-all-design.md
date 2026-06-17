# 教务数据一键批量同步（首启自动 + 手动）设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与目标

四类教务数据（课表 / 作业 DDL / 考试 / 成绩）各有独立拉取入口，用户需逐个进页面手动刷新。目标：**首次登录后自动一次性把四类全部拉取入库**，并在个人中心提供「一键全部同步」手动入口。

**现有设施（全复用，不改其逻辑）**：
- 课表 `ImportCoursesUseCase.importAuto(req, channelFor, onModeSucceeded): Flow<ImportStep>`（带 Preview 确认）。
- 作业 `SyncAssignmentsUseCase.syncAuto(req, onModeSucceeded): Flow<DdlSyncStep>`（无 ics URL 会登录派生）。
- 考试 `SyncExamsUseCase.syncAuto(req, onModeSucceeded): Flow<ExamSyncStep>`。
- 成绩 `SyncGradesUseCase.syncAuto(req, onModeSucceeded): Flow<SyncGradesStep>`（写 `grade_entries`/`term_ranks`）。
- 这四个 `*Auto` 变体**完全自包含**：各自 `apiClient.open → SsoLogin → 拉取 → 落库 → close`，内置 `autoNetworkFallback` 校内↔校外回退。（`syncForBackground` 变体假设已 open 且不落库，不适合本编排，**不用**。）
- 凭据 `ImportCredentialPrefs.observeAll().value`（加密，`SavedCredentials(username, password, lastMode)`）。
- 初始网络模式 `ResolveNetworkModeUseCase.invoke(lastMode): NetworkMode`。

## 决策（已与用户确认）

- **呈现**：进度页（可跳过）。
- **触发**：首启自动 + 个人中心手动一键同步。
- **顺序拉取**（共享单例 `BitApiClient`，必须串行）、**部分失败容忍**、**凭据读 `ImportCredentialPrefs`**（无凭据则优雅降级）。

## ① 编排器 `SyncAllUseCase`

新建 `domain/bitimport/SyncAllUseCase.kt` + 模型 `domain/bitimport/model/SyncAllModels.kt`：

```
enum class SyncSource { COURSES, DDL, EXAMS, GRADES }
enum class SyncSourceStatus { PENDING, RUNNING, OK, FAILED }
data class SyncSourceState(val status: SyncSourceStatus, val detail: String? = null)
data class SyncAllProgress(val states: Map<SyncSource, SyncSourceState>, val done: Boolean, val noCredentials: Boolean = false)
```

`fun run(): Flow<SyncAllProgress>`：
1. 读 `credPrefs.observeAll().value`；为空 → 发 `SyncAllProgress(全 PENDING, done=true, noCredentials=true)` 终态返回。
2. `mode = resolveMode(creds.lastMode)`；`onModeSucceeded = { m -> credPrefs.save(creds.username, creds.password, m) }`（顺带刷新 lastMode）。
3. 串行跑 COURSES→DDL→EXAMS→GRADES：每源先置 `RUNNING` 发快照 → 收集对应 `*Auto` Flow 到终态 → 置 `OK(detail)`/`FAILED` 发快照。每源 `try/catch` 包裹，单源异常/失败**不中断**其余。
   - 课表：`importAuto(ImportRequest(ImportCredentials(u,p), mode, rememberPwd=true), channelFor = { Channel<Boolean>(1).apply{ trySend(true) } }, onModeSucceeded)`——预置 `true` 自动确认 Preview。`Done(result)→OK("${successCount} 节")`，`Failed→FAILED`。
   - 作业/考试/成绩：各自 `syncAuto(req, onModeSucceeded)`，`Done→OK(计数)`，`Failed→FAILED`。
4. 末尾发 `done=true` 快照。

## ② 进度页 `SyncAllScreen` + `SyncAllViewModel`

`feature/bitimport/ui/SyncAllScreen.kt` + VM。终端 CRT 风（`.scanLines().vignette()`，Void 底），标题 `$ 初始化教务数据`，四行：
```
[课表]  ✓ 32 节
[作业]  ✓ 12 条
[考试]  ▸ 拉取中…▓
[成绩]  ○ 待拉取
```
状态字形：`○`(FoamDim) PENDING / `▸`+`BlinkingCursor` RUNNING / `✓`(Phosphor)+detail OK / `✗`(Carmine)+「可在该页手动刷新」FAILED。
- VM `init` 即 `collect(syncAllUseCase.run())` → uiState；`done` 时（或 skip 时）`loginPrefs.setFirstSyncDone(true)`（幂等）。
- 运行中显 `[跳过]`（取消 collect 协程 + 收尾置 done）；`done` 后显 `[进入]`；有 FAILED 时显 `[重试失败项]`（重跑 `run()`，已 OK 的会再拉一遍——M1 简单起见整体重跑，可接受）。
- `noCredentials` → 显「未保存凭据，无法自动同步；请到各页手动刷新，或重新登录并勾选记住密码」+ `[进入]`（仍置 firstSyncDone=true）。
- `SyncAllScreen(onFinish: () -> Unit)`——`[进入]`/`[跳过]` 调 `onFinish`。

## ③ 触发与标记

- `LoginPrefs` 加 `firstSyncDone`（`booleanPreferencesKey("first_sync_done")` + `observeFirstSyncDone`/`snapshotFirstSyncDone`/`setFirstSyncDone`）。
- 路由 `NavRoutes.SYNC_ALL = "sync-all?first={first}"`（`first` Bool，默认 false）+ helper `syncAll(first)`。
- **首启自动**：登录成功路径上，若该登录是首屏门入口（**无** `next` 守卫目标）且 `firstSyncDone==false` → 导航 `syncAll(first=true)`，`onFinish` = 导航 PROFILE 并 `popUpTo(LOGIN){inclusive}`；否则照旧（有 `next` 走 next；已 done 走 PROFILE）。守卫式重登录（带 `next`）**不触发**。
- **手动**：个人中心加「⟳ 一键全部同步」入口 → `syncAll(first=false)`，`onFinish = popBackStack()`。

## 边界 / 错误

- 单源失败仅标 `FAILED`，不阻断其余；用户可在该页手动刷新或点 `[重试失败项]`。
- 课表 `importAuto` 自动确认会覆写 `IMPORTED_PORTAL`（首启空库无碍；手动即"刷新全部"，符合预期）。
- 无凭据：优雅降级（见 ②），不崩、不卡。

## 测试

- 单测 `SyncAllUseCase`（mock 四个用例返回构造的 Flow + mock credPrefs/resolveMode）：① 全成功→四源 OK + done；② 某源 Failed/抛异常→该源 FAILED 其余仍 OK；③ 无凭据→noCredentials 终态。纯协程编排逻辑，turbine/runTest 可测。
- 进度页/路由/触发以真机 DoD 验。
- 真机 DoD：清数据首启→登录(勾记住密码)→进度页四行依次 ✓→`[进入]`→课表/作业/考试/成绩各页已有数据；个中「一键全部同步」可重跑；断网/某源失败时该源 ✗ 其余照常、可重试。

## 不做 / 保留

- 不做并行拉取（共享单例 client 必串行）、不做"转后台继续"（YAGNI）。
- 不改四个拉取用例的内部逻辑、不改 DB schema、不改网络接口。
- 不动既有各功能页的手动刷新入口与后台轮询（DDL/成绩 poller）。

## 影响面

新建：`domain/bitimport/SyncAllUseCase.kt`、`domain/bitimport/model/SyncAllModels.kt`、`feature/bitimport/ui/SyncAllScreen.kt`、对应 `SyncAllViewModel`、`SyncAllUseCaseTest`。改：`LoginPrefs`(+firstSyncDone)、`NavRoutes`(+SYNC_ALL)、`AppNavHost`(+composable + 登录成功路由判断)、登录成功回调处、个人中心入口。复用四个 `*Auto` 用例 + `ImportCredentialPrefs`/`ResolveNetworkModeUseCase`/`BlinkingCursor`/CRT 纹理。无 DB/网络接口改动。
