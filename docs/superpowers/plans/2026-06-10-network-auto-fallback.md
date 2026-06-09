# 教务网络自动回退全量化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把成绩已验证的「校内↔校外自动回退」抽成共享能力,铺到登录 + 课表导入 + 考试 + 作业 + 空教室,并把登录页改为默认 Auto(保留手动覆盖),最终一个大 PR。

**Architecture:** 新增纯函数 `NetworkFallback`(Flow 形态 `autoNetworkFallback` + suspend 形态 `withSessionAutoFallback`),各功能在其同步入口包一层、各 sealed step 加 `SwitchingMode`,成功后经 `onModeSucceeded` 回写共享 `lastMode`。仅连接级失败(IOException/`NetworkFail`)触发回退。设计见 `docs/superpowers/specs/2026-06-09-network-auto-fallback-design.md`。

**Tech Stack:** Kotlin, Coroutines/Flow, Retrofit/OkHttp(`BitApiClient` 共享会话), Hilt, JUnit4 + mockk + turbine。

**分支与提交:** 全程在新分支 `feat/network-auto-fallback`(基于最新 `main`,即含 PR #16 merge 后),每 Task 一个 commit,末尾一个大 PR。**前置:PR #16(fix/webvpn-grades)须已 merge 进 main**——本计划依赖它引入的 `casLoginEndpoint`/`activateServiceAt`/`SyncGradesStep.SwitchingMode`/`autoGradesFallback`。

**通用约定(每个功能接入重复用到):**
- 各 `…Step` sealed class 加 `data class SwitchingMode(val to: NetworkMode) : …Step()`。
- 各 ViewModel 的 `when(step)` 补 `SwitchingMode` 分支,文案:`to == WEBVPN → "校内不可达，改用校外(WEBVPN)重试…" else "校外不可达，改用校内重试…"`。
- `onModeSucceeded = { winning -> if (winning != firstMode) credPrefs.save(username, password, winning) }`。
- `firstMode = creds.lastMode ?: NetworkMode.LOCAL`。
- 触发回退判据:该功能 `…Error.NetworkFail`(由 `catch(IOException)` 产生)。

---

## Phase 0 — 分支

### Task 0: 建分支

**Files:** 无(git)

- [ ] **Step 1: 确认 PR #16 已 merge,建分支**

Run:
```bash
git fetch origin
git log --oneline -1 origin/main   # 应见 fix/webvpn-grades 的 merge
git switch -c feat/network-auto-fallback origin/main
```
Expected: 在 `feat/network-auto-fallback`,且 `casLoginEndpoint`/`activateServiceAt` 已在 `origin/main`。若 PR #16 未 merge,先停下与用户确认。

---

## Phase 1 — 共享抽象 + 成绩重构

### Task 1: 新建 `NetworkFallback.kt`(两种形态)+ 单测

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/network/bit/NetworkFallback.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/network/bit/NetworkFallbackTest.kt`

- [ ] **Step 1: 写失败测试** `NetworkFallbackTest.kt`

```kotlin
package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private sealed class S {
    object Work : S(); object Done : S(); object ConnFail : S(); object OtherFail : S()
    data class Switch(val to: NetworkMode) : S()
}

class NetworkFallbackTest {
    private val isConn: (S) -> Boolean = { it is S.ConnFail }
    private val isDone: (S) -> Boolean = { it is S.Done }
    private val sw: (NetworkMode) -> S = { S.Switch(it) }

    @Test fun `flow - first conn-fail switches, surfaces no fail, reports winner`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val out = autoNetworkFallback(NetworkMode.LOCAL, isConn, isDone, sw, { won += it }) { m ->
            if (m == NetworkMode.LOCAL) flowOf(S.Work, S.ConnFail) else flowOf(S.Work, S.Done)
        }.toList()
        assertTrue(out.none { it is S.ConnFail })
        assertTrue(out.any { it is S.Switch && it.to == NetworkMode.WEBVPN })
        assertTrue(out.last() is S.Done)
        assertEquals(listOf(NetworkMode.WEBVPN), won)
    }

    @Test fun `flow - first success no fallback`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val out = autoNetworkFallback(NetworkMode.WEBVPN, isConn, isDone, sw, { won += it }) { _ -> flowOf(S.Done) }.toList()
        assertEquals(listOf(NetworkMode.WEBVPN), won)
        assertTrue(out.none { it is S.Switch })
    }

    @Test fun `flow - non-conn failure does not retry`() = runTest {
        var attempts = 0
        val out = autoNetworkFallback(NetworkMode.LOCAL, isConn, isDone, sw, {}) { _ -> attempts++; flowOf(S.OtherFail) }.toList()
        assertEquals(1, attempts)
        assertTrue(out.none { it is S.Switch })
        assertTrue(out.last() is S.OtherFail)
    }

    @Test fun `suspend - IOException switches then succeeds, reports winner`() = runTest {
        val won = mutableListOf<NetworkMode>(); val switched = mutableListOf<NetworkMode>()
        val r = withSessionAutoFallback(NetworkMode.LOCAL, onModeSucceeded = { won += it }, onSwitching = { switched += it }) { m ->
            if (m == NetworkMode.LOCAL) throw IOException("x") else "ok"
        }
        assertEquals("ok", r); assertEquals(listOf(NetworkMode.WEBVPN), won); assertEquals(listOf(NetworkMode.WEBVPN), switched)
    }

    @Test fun `suspend - non-IO rethrows without retry`() = runTest {
        var attempts = 0
        try {
            withSessionAutoFallback(NetworkMode.LOCAL, onModeSucceeded = {}) { _ -> attempts++; throw IllegalStateException("no") }
            assertTrue("should have thrown", false)
        } catch (e: IllegalStateException) { /* ok */ }
        assertEquals(1, attempts)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*NetworkFallbackTest"`
Expected: FAIL(未定义 `autoNetworkFallback`/`withSessionAutoFallback`)。

- [ ] **Step 3: 实现 `NetworkFallback.kt`**

```kotlin
package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun otherMode(m: NetworkMode): NetworkMode =
    if (m == NetworkMode.LOCAL) NetworkMode.WEBVPN else NetworkMode.LOCAL

/** 校内↔校外自动回退(Flow 形态)。详见 spec。仅在 [isConnFail] 收尾且还有另一 mode 时回退一次。 */
fun <S> autoNetworkFallback(
    first: NetworkMode,
    isConnFail: (S) -> Boolean,
    isDone: (S) -> Boolean,
    switchingStep: (NetworkMode) -> S,
    onModeSucceeded: (NetworkMode) -> Unit,
    attempt: (NetworkMode) -> Flow<S>,
): Flow<S> = flow {
    val modes = listOf(first, otherMode(first))
    for ((idx, mode) in modes.withIndex()) {
        val hasFallbackLeft = idx < modes.lastIndex
        var terminal: S? = null
        attempt(mode).collect { step ->
            terminal = step
            val suppress = isConnFail(step) && hasFallbackLeft
            if (!suppress) emit(step)
        }
        val t = terminal ?: return@flow
        when {
            isDone(t) -> { onModeSucceeded(mode); return@flow }
            isConnFail(t) && hasFallbackLeft -> emit(switchingStep(otherMode(mode)))
            else -> return@flow
        }
    }
}

/** 校内↔校外自动回退(suspend 形态)。连接级异常([isConnFail],默认 IOException)换 mode 重试一次。 */
suspend fun <T> withSessionAutoFallback(
    first: NetworkMode,
    isConnFail: (Throwable) -> Boolean = { it is java.io.IOException },
    onModeSucceeded: (NetworkMode) -> Unit,
    onSwitching: (NetworkMode) -> Unit = {},
    block: suspend (NetworkMode) -> T,
): T {
    val modes = listOf(first, otherMode(first))
    for ((idx, mode) in modes.withIndex()) {
        try {
            val r = block(mode); onModeSucceeded(mode); return r
        } catch (e: Throwable) {
            if (!(idx < modes.lastIndex && isConnFail(e))) throw e
            onSwitching(otherMode(mode))
        }
    }
    error("withSessionAutoFallback: unreachable")
}
```

- [ ] **Step 4: 跑测试确认通过** — `./gradlew.bat :app:testDebugUnitTest --tests "*NetworkFallbackTest"` → PASS。
- [ ] **Step 5: Commit** — `git add app/...NetworkFallback.kt app/...NetworkFallbackTest.kt && git commit -m "feat(net): shared 校内↔校外 auto-fallback (Flow + suspend forms)"`

### Task 2: 成绩重构到共享版(零行为变更)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt`
- Modify(可能): `app/src/test/java/com/example/personal_studio/domain/bitgrades/AutoGradesFallbackTest.kt`

- [ ] **Step 1:** 删 `SyncGradesUseCase.kt` 里顶层 `autoGradesFallback` 与 `otherMode`;`syncAuto` 改调共享：
```kotlin
fun syncAuto(req: GradesSyncRequest, onModeSucceeded: (NetworkMode) -> Unit): Flow<SyncGradesStep> =
    autoNetworkFallback(
        first = req.networkMode,
        isConnFail = { it is SyncGradesStep.Failed && it.err is GradesSyncError.NetworkFail },
        isDone = { it is SyncGradesStep.Done },
        switchingStep = { SyncGradesStep.SwitchingMode(it) },
        onModeSucceeded = onModeSucceeded,
    ) { mode -> sync(req.copy(networkMode = mode)) }
```
加 import `com.example.personal_studio.data.network.bit.autoNetworkFallback`。
- [ ] **Step 2:** `AutoGradesFallbackTest` 若直接调用了被删的 `autoGradesFallback`,改为调 `autoNetworkFallback`(用 grades 的 lambda)或迁移成对 `SyncGradesUseCase.syncAuto` 的端到端测;否则保留。
- [ ] **Step 3:** 跑成绩相关全测 — `./gradlew.bat :app:testDebugUnitTest --tests "*bitgrades*"` → PASS(行为不变)。
- [ ] **Step 4: Commit** — `refactor(grades): 复用共享 autoNetworkFallback,删本地副本`

---

## Phase 2 — 考试(最干净的 Flow 样板)

### Task 3: 考试自动回退

**Files:**
- Modify: `app/.../domain/bitexam/model/ExamModels.kt`(`ExamSyncStep` 加 `SwitchingMode`)
- Modify: `app/.../domain/bitexam/SyncExamsUseCase.kt`(加 `syncAuto`)
- Modify: `app/.../feature/bitexam/ExamsViewModel.kt`(`onRefresh` 改调 + 回写 + `when` 分支)
- Test: `app/src/test/.../feature/bitexam/ExamsViewModelTest.kt`(或对应已存测试)

- [ ] **Step 1:** 读 `ExamModels.kt` 确认 `ExamSyncStep` 各变体名 + `ExamSyncError.NetworkFail` 存在(survey 已述);读 `ExamsViewModel.onRefresh()`(约 :73)确认 `creds.lastMode`、`sync.sync(...)` 调用形态、`when(step)` 位置。
- [ ] **Step 2: 写失败测试**(VM):无凭据不跑;首选 NetworkFail 回退到另一 mode 后 `Done`,且回写 `lastMode`。仿 `GradesViewModelTest.onSyncNow`,mock `sync.syncAuto(any(), any())` 返回 `flowOf(SwitchingMode(WEBVPN), Done(..))`,verify `credPrefs.save(.., WEBVPN)`。
- [ ] **Step 3: 实现**:
  - `ExamSyncStep` 加 `data class SwitchingMode(val to: NetworkMode) : ExamSyncStep()`。
  - `SyncExamsUseCase.syncAuto(req, onModeSucceeded)` = `autoNetworkFallback{ isConnFail=Failed&&NetworkFail; isDone=Done; switchingStep=ExamSyncStep.SwitchingMode; attempt={ mode -> sync(req.copy(networkMode=mode)) } }`。
  - `ExamsViewModel.onRefresh`:`firstMode = creds.lastMode ?: LOCAL`;改调 `syncAuto(req, onModeSucceeded={ if(it!=firstMode) credPrefs.save(u,p,it) })`;`when` 补 `SwitchingMode` 分支(通用文案)。
- [ ] **Step 4:** 跑 `--tests "*bitexam*"` → PASS。
- [ ] **Step 5: Commit** — `feat(exam): 校内↔校外自动回退 + 记住生效模式`

---

## Phase 3 — 作业 DDL(先修 webvpn bug,再接回退)

### Task 4: 乐学 activateService 改穿网关

**Files:**
- Modify: `app/.../domain/bitddl/GenerateLexueIcalUrlUseCase.kt:24`
- Test: `app/src/test/.../domain/bitddl/GenerateLexueIcalUrlUseCaseTest.kt`

- [ ] **Step 1: 写/改失败测试**:验证激活走的是 `apiClient.casLoginEndpoint()` + `activateServiceAt`(而非旧 `activateService`)。mock `apiClient.casLoginEndpoint()` 返回某串,`coVerify { cas.activateServiceAt(that串, LEXUE_SERVICE) }`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现** — 第 24 行:
```kotlin
apiClient.cas.activateServiceAt(apiClient.casLoginEndpoint(), LEXUE_SERVICE)
```
(`LEXUE_SERVICE` 不变,仍直连乐学;**不**改 `BitUrlsConfig.WEBVPN.lexue`。)
- [ ] **Step 4:** 跑 `--tests "*GenerateLexueIcalUrlUseCase*"` → PASS。
- [ ] **Step 5: Commit** — `fix(ddl): 乐学 CAS 激活穿 webvpn 网关(校外可达,乐学 host 仍直连)`

### Task 5: 作业自动回退

**Files:**
- Modify: `app/.../domain/bitddl/model/DdlModels.kt`(`DdlSyncStep` 加 `SwitchingMode`)
- Modify: `app/.../domain/bitddl/SyncAssignmentsUseCase.kt`(加 `syncAuto`)
- Modify: `app/.../feature/bitddl/AssignmentsViewModel.kt`(`onRefresh` ~:95 改调 + 回写 + `when`)
- Test: 对应 VM/UseCase 测试

- [ ] **Step 1:** 读 `DdlModels.kt` 确认 `DdlSyncStep`/`DdlSyncError.NetworkFail`;读 `AssignmentsViewModel.onRefresh`。
- [ ] **Step 2: 写失败测试**(同考试模式)。
- [ ] **Step 3: 实现**:`DdlSyncStep` 加 `SwitchingMode`;`SyncAssignmentsUseCase.syncAuto`(`autoNetworkFallback`,attempt 调 `sync(req.copy(networkMode=mode))`);VM 改调 + 回写 + `when` 分支。后台 `DdlPollWorker` 不动(维持 LOCAL)。
- [ ] **Step 4:** 跑 `--tests "*bitddl*"` → PASS。
- [ ] **Step 5: Commit** — `feat(ddl): 校内↔校外自动回退 + 记住生效模式`

---

## Phase 4 — 课表导入(含 confirmChannel 处理)

### Task 6: 导入自动回退(每 attempt 新 channel)

**Files:**
- Modify: `app/.../domain/bitimport/model/ImportModels.kt`(`ImportStep` 加 `SwitchingMode`)
- Modify: `app/.../domain/bitimport/ImportCoursesUseCase.kt`(加 `importAuto`)
- Modify: `app/.../feature/bitimport/ImportViewModel.kt`(改调 + 每 attempt 新 channel + 回写)
- Test: `app/src/test/.../feature/bitimport/ImportViewModelTest.kt`

- [ ] **Step 1:** 读 `ImportCoursesUseCase.import(req, confirmChannel)`、`ImportStep`(确认 `Cancelled`/`Failed(NetworkFail)`/`Done`)、`ImportViewModel.onLogin`(~:103)与 `Done` 分支的 `credPrefs.save`(~:146)。
- [ ] **Step 2: 写失败测试**:首选 NetworkFail(登录阶段)→ 回退另一 mode → 到 Preview → confirm → Done;验证用了**新 channel**、回写生效 mode、Cancelled 不重试。
- [ ] **Step 3: 实现**:
  - `ImportStep` 加 `SwitchingMode`。
  - `ImportCoursesUseCase` 加:
```kotlin
fun importAuto(
    req: ImportRequest,
    channelFor: (NetworkMode) -> Channel<Boolean>,   // 每 attempt 取一个新 channel
    onModeSucceeded: (NetworkMode) -> Unit,
): Flow<ImportStep> = autoNetworkFallback(
    first = req.networkMode,
    isConnFail = { it is ImportStep.Failed && it.err is ImportError.NetworkFail },
    isDone = { it is ImportStep.Done },
    switchingStep = { ImportStep.SwitchingMode(it) },
    onModeSucceeded = onModeSucceeded,
) { mode -> import(req.copy(networkMode = mode), channelFor(mode)) }
```
  - `ImportViewModel`:把单一 `confirmChannel` 改为 `channelFor = { Channel(Channel.RENDEZVOUS) }`(每 attempt 新建并记到 state 以便 UI 的确认/取消发往当前 attempt 的 channel);`onLogin` 改调 `importAuto`;`Done` 分支 `credPrefs.save` 用生效 mode;`when` 补 `SwitchingMode`。
  - **注意**:UI 的 confirm/cancel 必须发到「当前正在等待的那个 channel」。state 持一个 `currentConfirmChannel`,`channelFor` 里更新它,confirm 按钮 send 到它。
- [ ] **Step 4:** 跑 `--tests "*bitimport*"` → PASS。
- [ ] **Step 5: Commit** — `feat(import): 校内↔校外自动回退(每 attempt 新 confirmChannel)`

---

## Phase 5 — 空教室(suspend 形态,工作量最大)

### Task 7: Repository 显式网络错误

**Files:**
- Modify: `app/.../domain/emptyroom/EmptyRoomRepository.kt`(+ 其 error/result 模型)
- Test: `app/src/test/.../`(若有 RepositoryTest;否则在 VM 测试覆盖)

- [ ] **Step 1:** 读 `EmptyRoomRepository.kt`(openAndLogin:38、campuses/buildings/occupancy)、其 `EmptyRoomResult`/`EmptyRoomError` 定义,确认现状把 IOException 笼统冒泡。
- [ ] **Step 2: 写失败测试**:`openAndLogin`/取数遇 IOException 时产出可辨识的「网络错误」(`EmptyRoomError.Network` 或直接让其向上抛 IOException 由回退器捕获)。
- [ ] **Step 3: 实现**:最简方案——让会话块在连接级失败时**向上抛 IOException**(不在 Repository 吞),交由 Task 8 的 `withSessionAutoFallback`(默认 `isConnFail = IOException`)判定;非连接级(登录失败/解析)仍走原 `EmptyRoomError`。若现状已吞 IOException 成通用错误,改为对 `IOException` 重新抛出/包成 `EmptyRoomError.Network` 并在 Task 8 用 `isConnFail = { it is EmptyRoomError.Network ... }`。**实现时按实际错误模型择一,保持其余错误语义不变。**
- [ ] **Step 4:** 跑 `--tests "*emptyroom*"` → PASS。
- [ ] **Step 5: Commit** — `refactor(emptyroom): 连接级失败显式可辨(为自动回退铺路)`

### Task 8: 空教室会话自动回退(withLock 内)

**Files:**
- Modify: `app/.../feature/emptyroom/EmptyRoomViewModel.kt`(loadCampuses/loadBuildings/onQuery 三处)
- Test: `app/src/test/.../feature/emptyroom/EmptyRoomViewModelTest.kt`

- [ ] **Step 1:** 读 `EmptyRoomViewModel` 三处 `sessionMutex.withLock{ ensureSession(creds.lastMode?:LOCAL); …; finally repo.close() }`。
- [ ] **Step 2: 写失败测试**:首选 mode 连接级失败 → 自动换另一 mode → 成功 → 回写 `lastMode`;且回退发生在同一 withLock 内(不被并发 close 破坏)。
- [ ] **Step 3: 实现**:三处会话块改为：
```kotlin
sessionMutex.withLock {
    val first = creds.lastMode ?: NetworkMode.LOCAL
    withSessionAutoFallback(
        first = first,
        onModeSucceeded = { if (it != first) credPrefs.save(creds.username, creds.password, it) },
        onSwitching = { /* 可选:state 加一行"改用…重试…" */ },
    ) { mode ->
        repo.openAndLogin(creds.username, creds.password, mode)   // 抛 IOException → 回退
        // …本操作的取数(campuses/buildings/occupancy)…
    }.also { /* 用结果更新 state */ }
    // finally close 仍由 repo/末尾处理
}
```
  - 注意:`openAndLogin` + 取数要在**同一个 block 内**(回退重试整段);`close` 放在 block 结束/finally,保证每次 attempt 自带 open→…→close。
- [ ] **Step 4:** 跑 `--tests "*emptyroom*"` → PASS。
- [ ] **Step 5: Commit** — `feat(emptyroom): 校外自愈(会话自动回退,补校外能力缺口)`

---

## Phase 6 — 登录默认 Auto + 保留覆盖

### Task 9: 登录自动回退

**Files:**
- Modify: `app/.../domain/bitimport/ValidateCredentialsUseCase.kt`(suspend 自动版)
- Modify: `app/.../feature/auth/BitLoginViewModel.kt`(`onLogin` 改调 + 回写生效 mode)
- Test: `app/src/test/.../feature/auth/BitLoginViewModelTest.kt`

- [ ] **Step 1:** 读 `ValidateCredentialsUseCase`(签名 + 是否吞 IOException)、`BitLoginViewModel.onLogin`(:70-86 区域,`validate.invoke` + `credPrefs.save`)。
- [ ] **Step 2: 写失败测试**:Auto 下,LOCAL 登录连接级失败 → 自动试 WEBVPN → 成功 → 保存 `lastMode=WEBVPN`;手动指定 mode 时不回退(覆盖优先)。
- [ ] **Step 3: 实现**:
  - `ValidateCredentialsUseCase` 加一个返回(成功+生效 mode)的自动版,内部 `withSessionAutoFallback(first){ mode -> validateOnce(mode) }`(`validateOnce` 连接级失败抛 IOException)。**若手动覆盖了 mode,则不走 Auto,直接单次该 mode。**
  - `BitLoginViewModel.onLogin`:`st.networkMode` 语义改为「Auto(默认)/手动覆盖」;Auto 时调自动版并保存生效 mode,手动时按选中 mode 单次。
- [ ] **Step 4:** 跑 `--tests "*BitLoginViewModel*"` → PASS。
- [ ] **Step 5: Commit** — `feat(auth): 登录默认 Auto 自动回退 + 记住生效模式`

### Task 10: 登录页 UI — 默认 Auto,手动收进高级

**Files:**
- Modify: `app/.../feature/auth/ui/BitLoginScreen.kt`
- Modify(可能): `BitLoginViewModel`/`BitLoginUiState`(加 `mode override` 状态:Auto / 强制 LOCAL / 强制 WEBVPN)

- [ ] **Step 1:** 读 `BitLoginScreen.kt` 现有校内/校外控件 + `onNetworkModeChange`。
- [ ] **Step 2:** UI 改造:默认不显示校内/校外二选一;加一个可展开「高级 / 手动指定网络」区,内含三态(自动 / 校内 / 校外)。`BitLoginUiState` 用一个 `modeOverride: NetworkMode?`(null=Auto)。
- [ ] **Step 3:** 接到 Task 9:override 非空→单次该 mode;null→Auto 回退。
- [ ] **Step 4:** `./gradlew.bat :app:assembleDebug` 通过 + 相关 VM 测试绿。
- [ ] **Step 5: Commit** — `feat(auth-ui): 登录页默认 Auto,手动选网络收进高级覆盖`

---

## Phase 7 — 收尾

### Task 11: 全量验证 + 真机 DoD + PR

- [ ] **Step 1:** `./gradlew.bat :app:testDebugUnitTest` 全绿。
- [ ] **Step 2:** `./gradlew.bat :app:installDebug` 装真机。
- [ ] **Step 3: 真机 DoD(校外网络,lastMode 起于 LOCAL)** 逐项:登录(自动切 WEBVPN 成功)→ 成绩 → 考试 → 作业(乐学详情可拉)→ 导入(回退后 Preview 仍正常确认)→ 空教室(首次即可用)。每项观察 SwitchingMode 提示 + 最终成功。
- [ ] **Step 4:** 把 spec + plan 一并纳入(`docs/superpowers/...`),`git add docs && git commit -m "docs: 网络自动回退 spec + plan"`。
- [ ] **Step 5:** push + 开 PR(base main,大 PR),body 概述全量改动 + 真机 DoD 结果 + 引用 spec。留用户 merge。

---

## Self-Review 备注(写计划时已核)

- **Spec 覆盖**:登录/导入/成绩/考试/作业/空教室 + 共享抽象 + lastMode + 去开关(默认Auto+覆盖)+ 测试 + 真机 DoD —— 各有对应 Task。
- **类型一致**:`autoNetworkFallback`/`withSessionAutoFallback`/`otherMode`/`SwitchingMode(to)`/`onModeSucceeded`/`casLoginEndpoint()`/`activateServiceAt` 全计划一致;后者来自 PR #16(前置依赖)。
- **执行期须先读再改的点**(已在各 Task Step 1 标注):各功能 sealed `…Step`/`…Error` 的确切变体名、各 VM 的 `onRefresh/onLogin` 行号、空教室错误模型、导入 confirmChannel 接法、登录 validate 签名 —— 实现该 Task 时先 Read 对应文件确认,再按模板落地。
