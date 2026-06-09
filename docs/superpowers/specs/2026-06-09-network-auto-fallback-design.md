# 教务网络自动回退（校内↔校外）全量化 — 设计

## 背景与目标

教务功能（登录/课表导入/成绩/考试/作业/空教室）此前要求用户在登录页**手动选「校内/校外」**，且该选择被存成 `lastMode` 一直沿用。痛点：

- 这是认知负担，且**不是按真实网络自适应的**——选了「校外」连着校园网照样走 webvpn，选了「校内」到校外就连不上且不自愈。
- 成绩页已落地一套**自动回退**（先按 `lastMode` 试，仅连接级失败时自动换另一模式重试一次、记住生效模式），真机验证手感 OK（PR #16）。

**目标**：把这套自动回退抽成共享能力，铺到**全部教务功能 + 登录本身**，从而**取消登录页对校内/校外的强制选择**（默认 Auto，手动选项降级为可选覆盖）。`lastMode` 全功能共享 → 任一功能在校外成功一次，全局自愈。

**非目标 / 明确取舍**：
- **不做** SSID/IP 主动探测（读 SSID 在 Android 10+ 需定位权限，脆且重）。沿用已验证的 **try-fallback**（先试一个、连接级失败再换）。
- 回退**只在连接级失败**（`NetworkFail` = `IOException`：UnknownHost/Connect/Timeout）触发；`EmptyGrades/ParseFail/WrongCredentials` 等**不**回退（可能是真实状态，翻网会掩盖真因）。此取舍已在成绩侧用注释+测试固化，全量沿用。

## 现状勘察结论（已并行勘察 4 功能 + 复用成绩经验）

| 功能 | 同步入口形态 | 跨 host `activateService` | 绝对 `@Url` 风险 | WEBVPN 就绪（代码层面） |
|---|---|---|---|---|
| 登录(auth) | `ValidateCredentialsUseCase`（suspend → 结果） | 不用 | 无 | 复用 SsoLogin，结构无同类 bug |
| 课表导入 | `Flow<ImportStep>`（含 Preview confirmChannel 闸门 + Cancelled 终态） | 不用 | 无 | likely-works |
| 成绩 | `Flow<SyncGradesStep>`（**已做完回退**，作参照） | 用（**已修**为穿网关） | 有（**已修**为相对路径） | ✅ 真机验证 |
| 考试 | `Flow<ExamSyncStep>` | 不用 | 无 | likely-works |
| 作业 DDL | `Flow<DdlSyncStep>` + `GenerateLexueIcalUrlUseCase`（suspend） | **用**（`activateService(LEXUE_SERVICE)` 直连 sso，成绩旧 bug 同形） | 一处但传完整绝对 URL（不丢前缀） | 见下注 |
| 空教室 | `EmptyRoomRepository` suspend 返回结果（VM 编排 + `sessionMutex` 串行） | 不用 | 无 | likely-works\* |

**关键结论：**

1. **导入/考试/空教室/登录结构上避开了成绩那两类坑**（不做跨 host 二次会话激活、无"从响应抽绝对路径再 @Url 请求"）。代码层面无同类 bug。唯一保留：**整个 WEBVPN 在本项目从未真机验证**（P5 遗留）。

2. **DDL 乐学**：`activateService(LEXUE_SERVICE)` 直连 sso，是成绩旧 bug 同形。已确认**乐学校外可公网直连**（`WEBVPN.lexue == LOCAL.lexue`，不经网关）。因此：
   - **不需要**给乐学塞 webvpn 网关映射。
   - **但仍需**把这步改成穿网关版 `activateServiceAt(casLoginEndpoint(mode), LEXUE_SERVICE)`——因为校外登录后 TGC cookie 在 `webvpn.bit.edu.cn` 域，直连 sso 带不上、换不到 service ticket。`service=` 仍是直连乐学，ticket 回跳到公网乐学直接生效。**这正是复用成绩已建的 `casLoginEndpoint`/`activateServiceAt`，零新机制。**

3. **空教室是真缺口**：它**没有任何网络模式 UI、无回退、恒 `lastMode ?: LOCAL`**——所以**校外现在根本没法用空教室**。加自动回退是**修一个真 bug**，不只是便利。

4. **`lastMode` 全功能共享**（同一份 `SavedCredentials`）→ 任一功能在校外成功一次、回写 `lastMode = WEBVPN`，其它功能下次直接走对的。自动回退一上，等于给所有功能装「校外自愈」。

## 设计：共享回退抽象

把成绩 `SyncGradesUseCase.kt` 里的 `autoGradesFallback`（internal 纯函数）**上提并泛型化**到网络层，与具体 Step 类型解耦。两种形态：

### A. Flow 形态（登录除外的同步类：导入/成绩/考试/作业）

放 `data/network/bit/NetworkFallback.kt`：

```kotlin
/** 校内↔校外自动回退的纯编排(Flow 形态)。先按 [first] 跑 [attempt];若以"连接级失败"
 *  ([isConnFail] 判真)收尾且还有另一 mode,抑制该失败、emit [switchingStep],换 mode 重试一次。
 *  [isDone] 判真即停并回告 [onModeSucceeded];其余终态(非连接级失败/Cancelled)也停、原样 surface。
 *  纯函数,不依赖 BitApiClient,便于单测。 */
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
            val suppress = isConnFail(step) && hasFallbackLeft   // 憋住首选的连接级失败
            if (!suppress) emit(step)
        }
        val t = terminal ?: return@flow                          // 空流,无终态,停
        when {
            isDone(t) -> { onModeSucceeded(mode); return@flow }
            isConnFail(t) && hasFallbackLeft -> emit(switchingStep(otherMode(mode)))
            else -> return@flow
        }
    }
}

fun otherMode(m: NetworkMode): NetworkMode =
    if (m == NetworkMode.LOCAL) NetworkMode.WEBVPN else NetworkMode.LOCAL
```

成绩侧 `syncAuto`/`autoGradesFallback`/`otherMode` **删除本地实现，改调共享版**（行为不变，回归测试 `AutoGradesFallbackTest` 改成测共享 `autoNetworkFallback` 或保留对 `syncAuto` 的端到端测）。

### B. suspend 形态（空教室；以及登录）

放同文件：

```kotlin
/** 校内↔校外自动回退的纯编排(suspend 形态)。先按 [first] 跑 [block];抛出 [isConnFail] 判真的
 *  异常且还有另一 mode → 换 mode 重试一次。成功(正常返回)即回告 [onModeSucceeded] 并返回结果;
 *  非连接级异常直接抛出(不回退)。 [onSwitching] 可选,用于 UI 提示正在切换。 */
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
            val r = block(mode)
            onModeSucceeded(mode)
            return r
        } catch (e: Throwable) {
            val canRetry = idx < modes.lastIndex && isConnFail(e)
            if (!canRetry) throw e
            onSwitching(otherMode(mode))
        }
    }
    error("unreachable")
}
```

## 各功能接入

> 每个 `Flow<…Step>` 的 sealed step 都要**新增一个 `SwitchingMode(to: NetworkMode)`** 态（成绩已有 `SyncGradesStep.SwitchingMode`），UI 据此显示「校内不可达，改用校外重试…」而不是闪「失败」。各 ViewModel 的 `when(step)` 需补该分支（sealed → 编译器强制）。

**① 登录 (auth) —— 最关键,因为它决定能否取消手动开关**
- `ValidateCredentialsUseCase`（suspend，复用 SsoLogin）用 **B 形态**包一层：先试 `lastMode ?: LOCAL`，连接级失败换另一 mode。
- `BitLoginViewModel.onLogin()`：去掉对 `st.networkMode` 的依赖，改走自动版；成功后 `credPrefs.save(u, p, winningMode)`（`onModeSucceeded` 回写）。
- **登录页 UI**：默认隐藏校内/校外选择（默认 Auto）。保留一个「高级 / 手动指定网络」可展开项作为覆盖（少数 webvpn 抽风场景）。`BitLoginUiState.networkMode` 仍存在但默认值语义改为「Auto 首选」。

**② 课表导入 (import)**
- `ImportCoursesUseCase` 用 **A 形态**包 `import(req.copy(networkMode=mode), confirmChannel)`。
- `ImportStep` 加 `SwitchingMode`；终态 `Cancelled` 落入「else → 停」（不重试，正确）。
- **confirmChannel 约束（重要）**：回退仅安全于「Preview 之前的连接级失败」（即登录阶段，校外主流场景）。若 Preview 之后再失败，`confirmChannel` 已被消费，第二次 attempt 会卡在 `receive()`。**处理**：A 形态对 import 而言，conn-fail 现实只发生在登录（首个网络请求），Preview 后是 Writing（已确认）。为稳妥，`importAuto` 包装里**每次 attempt 用新的 `confirmChannel`**（ViewModel 改为按 attempt 提供 channel），或在文档约束「仅登录阶段 conn-fail 触发回退」。本 spec 采用**每 attempt 新 channel**。
- `ImportViewModel`：`ImportStep.Done` 分支的 `credPrefs.save(..., st.networkMode)` 改存**生效 mode**（`onModeSucceeded` 喂回）。

**③ 成绩 (grades)** —— 已做完，仅把本地 `autoGradesFallback` 改调共享 `autoNetworkFallback`（重构，无行为变更）。

**④ 考试 (exam)**
- `SyncExamsUseCase` 加 `syncAuto(req, onModeSucceeded): Flow<ExamSyncStep>`，内部 `autoNetworkFallback{ mode -> sync(req.copy(networkMode=mode)) }`。
- `ExamSyncStep` 加 `SwitchingMode`；`ExamSyncError.NetworkFail` 已存在（catch IOException），作 `isConnFail` 判据。
- `ExamsViewModel.onRefresh()` 改调 `syncAuto` 并在回调里 `credPrefs.save` 回写生效 mode（当前只读 `lastMode` 从不回写）。

**⑤ 作业 DDL**
- **先修 webvpn bug**：`GenerateLexueIcalUrlUseCase.kt:24` `apiClient.cas.activateService(LEXUE_SERVICE)` → `apiClient.cas.activateServiceAt(apiClient.casLoginEndpoint(), LEXUE_SERVICE)`（复用成绩已建工具；service 仍 `LEXUE_SERVICE` 直连乐学，**不**给乐学配网关前缀）。
- `SyncAssignmentsUseCase` 加 `syncAuto(req, onModeSucceeded): Flow<DdlSyncStep>`（A 形态）。`DdlSyncStep` 加 `SwitchingMode`；`DdlSyncError.NetworkFail` 作判据。
- `AssignmentsViewModel.onRefresh()` 改调 `syncAuto` + 回写 `lastMode`。后台 `DdlPollWorker` 维持硬编码 LOCAL（后台不做交互回退，沿用成绩 `GradePollWorker` 取舍）。

**⑥ 空教室 (emptyroom)** —— 用 **B 形态**，工作量最大
- **前置**：让 `EmptyRoomRepository` 在连接级失败时**显式抛 `IOException`/产出可辨识的网络错误**（现状 IOException 笼统冒泡到 VM 的 `catch(Throwable)`，回退器无法判定）。
- `EmptyRoomViewModel` 三处 `sessionMutex.withLock{ ensureSession(mode); …; finally close }` 改为在 **withLock 内**调 `withSessionAutoFallback(first=lastMode?:LOCAL, onModeSucceeded={save}, onSwitching={UI提示}) { mode -> open(mode)→login→block }`。回退必须在同一 `withLock` 内完成（否则换 mode 后会被别的操作 `close`）。
- 空教室因此**首次获得校外能力**（补缺口）。`lastMode` 共享 → 通常其它功能已把它置为 WEBVPN，空教室第一发就走对。

## lastMode 协同与持久化

- 所有功能成功后经 `onModeSucceeded(mode)` → `ImportCredentialPrefs.save(username, password, mode)` 回写共享 `lastMode`（仅当 `mode != firstMode` 时写，避免冗余）。
- 因 `SavedCredentials` 全功能共享：一处校外自愈成功，全局 `lastMode=WEBVPN`，其余功能零额外失败地走对。

## 回退触发条件（全量一致）

- 仅 `…Error.NetworkFail`（即 `catch(IOException)`）触发回退。
- `EmptyGrades/ParseFail/Wrong­Credentials/AccountLocked/Captcha/NeedReview/Cancelled` 等**不**回退。
- 每个功能配一条边界测试固化「非连接级失败不回退」（仿 `AutoGradesFallbackTest`）。

## 文件结构（创建/修改）

- **Create** `data/network/bit/NetworkFallback.kt` — `autoNetworkFallback`(A) + `withSessionAutoFallback`(B) + `otherMode`。
- **Modify** `domain/bitgrades/SyncGradesUseCase.kt` — 删本地 `autoGradesFallback/otherMode`,`syncAuto` 改调共享。
- **Modify** 各 `…SyncModels`/`ImportModels`/`ExamModels`/`DdlModels` — 各 Step sealed 加 `SwitchingMode(to: NetworkMode)`。
- **Modify** `SyncExamsUseCase` / `SyncAssignmentsUseCase` / `ImportCoursesUseCase` — 加 `syncAuto`(或 `importAuto`)。
- **Modify** `GenerateLexueIcalUrlUseCase.kt` — `activateService` → `activateServiceAt(casLoginEndpoint(), …)`。
- **Modify** `domain/emptyroom/EmptyRoomRepository.kt` — 显式网络错误;`feature/emptyroom/EmptyRoomViewModel.kt` — 三处接 `withSessionAutoFallback`。
- **Modify** `domain/bitimport/ValidateCredentialsUseCase.kt` + `feature/auth/BitLoginViewModel.kt` + `feature/auth/ui/BitLoginScreen.kt` — 登录自动化 + 隐藏强制开关（保留高级覆盖）。
- **Modify** 各 ViewModel 的 `when(step)` 补 `SwitchingMode` 分支 + 回写 `lastMode`。

## 测试

- `NetworkFallbackTest`（核心，纯函数）：A 形态 = 现有 `AutoGradesFallbackTest` 的泛型版（首选成功不回退 / 连接级失败回退并回告 / 非连接级不回退 / 双双失败 surface 最终错误 / 空流停）；B 形态 = 成功回告 / IOException 换 mode / 非 IO 直接抛 / 双双 IO 抛最终。
- 各功能：`…ViewModelTest` 验「无凭据/首选成功/回退后回写 lastMode」；各 sealed `SwitchingMode` 的 UI 文案映射。
- DDL：`GenerateLexueIcalUrlUseCaseTest` 验 `activateServiceAt` 用的是 `casLoginEndpoint()`（LOCAL 不变、WEBVPN 走网关）。
- 空教室：`EmptyRoomRepositoryTest` 验连接级失败显式产出网络错误;`EmptyRoomViewModelTest` 验回退在 withLock 内、回写 lastMode。

## 实现分阶段（plan 拆 task）

1. **抽共享层**：`NetworkFallback.kt`（A+B）+ 单测；成绩重构到共享版（零行为变更，回归测试绿）。
2. **考试**：最干净的 Flow 接入,作第二个样板。
3. **作业 DDL**：先修 `activateService` 穿网关,再接 `syncAuto`。
4. **课表导入**：含 confirmChannel 每-attempt-新-channel 处理 + Cancelled 终态。
5. **空教室**：Repository 显式网络错误 → `withSessionAutoFallback`（withLock 内）→ 补校外能力。
6. **登录 + 去开关**：登录自动化 + 登录页默认 Auto、手动降级为高级覆盖。
7. **真机 DoD**：逐功能校外（流量）验证 WEBVPN 端到端（这也是各功能 WEBVPN 路径的首次真机验证；配合已有错误细化定位卡点）。

## 真机 DoD（每功能校外验证）

切到校外网络（流量）、`lastMode` 起于 LOCAL：每个功能首发应「LOCAL 连接级失败 → 自动切 WEBVPN → 成功 → 回写」；之后同会话/跨功能直接走 WEBVPN。重点观察：导入的 Preview 在回退后仍正常确认；空教室首次即可用；DDL 乐学详情可拉。

## 开放问题

1. ~~登录页手动开关的最终形态~~ **已定（2026-06-10）：默认 Auto + 保留「高级/手动指定网络」覆盖**。登录页不再强制选校内/校外;手动选项收进可展开的高级区作为逃生口。
2. **空教室 Repository 错误模型改造范围**：需新增 `EmptyRoomError.Network`（区别于 Parse/Auth）才能让回退器判定连接级失败;改造面落在 `EmptyRoomRepository` + 其测试。
3. **首次校外的回退等待**：首发那次 LOCAL 连接级失败要等连接超时（取决于 OkHttp connectTimeout）才切。是否为「首选 mode 的 attempt 用更短 connectTimeout」做优化,留作后续 polish（非本期）。
4. **后台 Worker（成绩/DDL poll）**：维持硬编码 LOCAL、不做回退（后台非交互、避免锁号循环），与现状一致;若要后台也自愈,另议。
