# 统一登录界面设计文档

> 状态:设计已对齐,待用户复核 → 转 writing-plans
> 日期:2026-05-29
> 目标:把现有"每个教务功能各自现场输入学号密码"的零散体验,统一成一个门面级登录界面;首次打开引导登录(可跳过),教务功能未登录时拦截要求登录。

---

## 1. Scope

### In scope
- 统一登录屏 `BitLoginScreen`,**门面级视觉**(终端/磷光绿风格做出高级感),复用现有视觉组件
- **立即验证**:点登录真去 BIT CAS 验一次,成功才存凭据;失败按原因提示、不存
- **首屏门**:首次打开 app → 登录屏(带显式"跳过");跳过/登录成功都置 `hasSeenLogin`,以后不再首屏弹
- **未登录拦截**:成绩 / 课表导入 / 作业 DDL 入口未登录时跳登录屏,登录成功回到目标功能
- **全部收敛**:成绩同步页 + 导入向导的两份重复内联登录表单退休,统一走 `BitLoginScreen`
- Settings「BIT 账号」行:已登录显学号 + 退出登录;退出 = 清凭据 + 关两个后台轮询
- 冷启动 splash(终端风格)决定首屏起始目的地

### Out of scope(YAGNI)
- 多账号(凭据仍单条)
- 持久化 cookie 会话(仍每次 sync `open/close`)
- 改后台 worker 的 `NetworkMode` 硬编码 LOCAL(维持现状)
- 生物识别 / 登录态超时 / 自动重登

---

## 2. 架构 & 模块布局

复用现有单一凭据 store —— 统一登录只当**权威写入方**,5 个消费方(两个 Worker、AssignmentsVM、两个轮询设置 VM、sync 用例)一行不改。

```
# 新增
feature/auth/ui/BitLoginScreen.kt              门面登录屏(Compose UI)
feature/auth/ui/components/ (按需)              登录屏专用视觉小组件(终端输入框等)
feature/auth/BitLoginViewModel.kt              登录态/输入/验证编排
domain/bitimport/ValidateCredentialsUseCase.kt 仅验证用例(open→sso→map→close)
domain/bitimport/model/LoginModels.kt          LoginRequest / LoginOutcome
data/local/datastore/LoginPrefs.kt             hasSeenLogin flag(DataStore)
feature/auth/AuthStatus.kt                     已登录状态来源(包装 ImportCredentialPrefs)

# 改
ui/navigation/NavRoutes.kt                     LOGIN 路由 + login(next) 构造
ui/AppNavHost.kt                               注册 login;startDestination 条件;入口守卫
ui/MainScreen.kt 或 MainActivity.kt            冷启动读 hasSeenLogin → splash → 起始目的地
feature/bitgrades/ui/GradesScreen.kt           同步按钮走守卫 + 内联同步进度
feature/bitgrades/GradesViewModel.kt           暴露 loggedIn + 触发同步(吸收 GradesSync 进度)
feature/bitimport/...ImportNavGraph/ImportViewModel  已登录自动用存凭据导入(Credentials 步退休)
feature/bitddl/AssignmentsViewModel.kt         onRefresh 未登录发"去登录"导航事件
feature/bitddl/ui/AssignmentsScreen.kt         收事件导航 login(next=assignments)
feature/settings/ui/SettingsScreen.kt          「BIT 账号」行
feature/settings/SettingsViewModel.kt          登录态 + 退出(清凭据 + 关两轮询)

# 退休(删除或改为薄壳重定向)
feature/bitgrades/ui/GradesSyncScreen.kt       登录表单部分删除,同步进度并入 GradesScreen
feature/bitimport/ui/ImportCredentialsScreen.kt 凭据表单让位给 BitLoginScreen
```

---

## 3. 登录态语义 + 数据流

### "已登录"的定义
**`ImportCredentialPrefs` 有 creds 即已登录。** 因为立即验证后才写入,所以"存在 = 验证过"。无新建 session 概念。`AuthStatus`(注入 `ImportCredentialPrefs`)暴露:
```kotlin
val isLoggedIn: Flow<Boolean>          // observeAll().map { it != null }
val username: Flow<String?>            // observeAll().map { it?.username }
```
各入口 VM 注入它(或直接注入 `ImportCredentialPrefs`)读登录态。

### 首屏门
- `LoginPrefs.hasSeenLogin: Boolean`(默认 false)。
- 冷启动:读一次 `hasSeenLogin`;读取期间显示终端 splash(见 §6)。
- 起始目的地:`hasSeenLogin == false` → `LOGIN`(skip 可见);`== true` → `CHAT`。
- **跳过或登录成功都 `setHasSeenLogin(true)`**,首屏门只出现一次。

### 守卫 + 回跳
- 路由:`LOGIN = "login?next={next}"`,`next` 为登录后要去的 route(URL-encoded),空则回 CHAT。
- 入口点击(成绩同步 / 导入课表 / DDL 刷新):`isLoggedIn` 为 false → `navigate("login?next=<target>")`;true → 直接进功能。
- 登录成功:`next` 非空 → `navigate(next)`;均 `popUpTo(LOGIN){inclusive=true}` 把登录页弹出回退栈。
- 首屏「跳过」:置 flag → `navigate(CHAT){ popUpTo(LOGIN){inclusive} }`。

---

## 4. ValidateCredentialsUseCase + 错误

```kotlin
// domain/bitimport/model/LoginModels.kt
data class LoginRequest(val username: String, val password: String, val networkMode: NetworkMode)

sealed interface LoginOutcome {
    object Success : LoginOutcome
    object WrongCredentials : LoginOutcome
    object AccountLocked : LoginOutcome
    object CaptchaRequired : LoginOutcome
    data class NetworkFail(val cause: String) : LoginOutcome
    data class Unexpected(val cause: String) : LoginOutcome
}
```

```kotlin
// domain/bitimport/ValidateCredentialsUseCase.kt
class ValidateCredentialsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
) {
    suspend fun invoke(req: LoginRequest): LoginOutcome {
        return try {
            apiClient.open(req.networkMode)
            when (val dto = ssoLogin.invoke(apiClient, req.username, req.password)) {
                CasLoginDto.Success -> LoginOutcome.Success
                CasLoginDto.WrongCredentials -> LoginOutcome.WrongCredentials
                CasLoginDto.AccountLocked -> LoginOutcome.AccountLocked
                CasLoginDto.CaptchaRequired -> LoginOutcome.CaptchaRequired
                is CasLoginDto.UnknownFailure -> LoginOutcome.Unexpected(dto.body)
            }
        } catch (io: java.io.IOException) {
            LoginOutcome.NetworkFail(io.message ?: "io")
        } catch (e: Throwable) {
            LoginOutcome.Unexpected(e.message ?: e.javaClass.simpleName)
        } finally {
            apiClient.close()
        }
    }
}
```

`BitLoginViewModel.onLogin()`:置 loading → `validate.invoke(req)` → Success:`credPrefs.save(username, password, mode)` + `loginPrefs.setHasSeenLogin(true)` + 发回跳事件;失败:置 error banner(不存)。错误文案:
- WrongCredentials → "学号或密码错误"
- AccountLocked → "账号已锁定,请稍后或网页端处理"
- CaptchaRequired → "需要验证码,请在网页端登录一次后重试"(banner 带"打开网页端"动作)
- NetworkFail → "连接失败,检查网络或切换校内/校外后重试"
- Unexpected → "登录失败,请重试"

---

## 5. 收敛改造(三处)

> 收敛后各功能不再单独选网络模式,同步统一用登录时存的 `lastMode`(无则默认 LOCAL)。

- **成绩**:`GradesScreen`「同步成绩」按钮 → `loggedIn` 为 false → `navigate("login?next=grades")`;true → 触发 `SyncGradesUseCase`,**内联显示精简同步进度**(复用 `SyncGradesStep`:登录中/取成绩/落库/完成)。`GradesSyncScreen` 整屏退休,登录表单删除,同步进度反馈并入 `GradesScreen`(或一个无表单的 `GradesSyncProgress` 区块)。
- **课表导入**:「导入课表」入口走守卫(未登录 → `login?next=import`);已登录进向导后**自动用存的凭据触发导入**(读 `ImportCredentialPrefs` 构造 `ImportRequest`,直接进 Progress → Preview),Credentials 凭据步退休。注:向导原 `TermPicker`「选学期」步是未接线死代码(从无地方填学期列表),本次不启用,沿用现有"当前学期"导入。
- **作业 DDL**:`AssignmentsViewModel.onRefresh` 未登录 → 发 `NavigateToLogin` 事件;`AssignmentsScreen` 收到 → `navigate("login?next=assignments")`。已登录直接同步(现状)。

收敛后,三处都不再各自渲染登录表单;唯一的凭据入口是 `BitLoginScreen`。

---

## 6. UI & 视觉(门面级,基于现有终端语言)

**原则**:不引入新配色/字体/图片资源,只用现有 `Color.kt` 色板 + `Typography`(含 VT323)+ 现有视觉组件(`scanLines`/`vignette`/`TerminalBackdrop`/`BlinkingCursor`/`AiFrame`)。高级感来自**层次、留白、动效节制、复古终端仪式**,不靠堆装饰。

### 6.1 冷启动 splash(门面第一印象)
- 读 `hasSeenLogin` 期间显示:`Void` 底 + `TerminalBackdrop`(扫描线 + 暗角辉光),居中 VT323 大字 logo(`displayLarge`,`Phosphor`),下方一行 `FoamDim` 小字(如 `personal-studio`)。
- 极短(读 DataStore 通常 <100ms);不强行拉长。若加引导动画(下条),splash 平滑过渡到登录屏。

### 6.2 BitLoginScreen 布局
```
┌─ TerminalBackdrop(scanLines + vignette),Void 底 ────┐
│                                                       │
│   PERSONAL // STUDIO       ← VT323 displayLarge,磷光  │
│   > 统一身份认证                ← FoamMute 副题          │
│                                                       │
│   ┌─ ── AUTH ───────────────────┐  ← AiFrame 式磷光框 │
│   │ $ 学号                       │                     │
│   │ [ 2024xxxxxx              ]  │  ← 终端风格输入框    │
│   │ $ 密码                       │                     │
│   │ [ ••••••••          👁 ]     │                     │
│   │ [✓] 记住密码（Keystore 加密）│                     │
│   │ ( 校内 ) ( 校外 )            │  ← FilterChip,默认校内│
│   └──────────────────────────────┘                    │
│                                                       │
│   [    登录 →    ]            ← 磷光描边主按钮          │
│   跳过                        ← FoamDim 文本按钮(仅首屏)│
└───────────────────────────────────────────────────────┘
```

### 6.3 视觉细节(实现指引)
- **背景**:整屏套 `TerminalBackdrop`(或手动 `scanLines().vignette()`),`Void` 底。
- **Hero**:`PERSONAL // STUDIO`(或 app 名)用 `MaterialTheme.typography.displayLarge`(VT323)+ `Phosphor`;副题 `> 统一身份认证` 用 `bodyMedium`/`FoamMute`,前导 `>` 提示符。
- **表单容器**:`AiFrame` 风格(`Phosphor.copy(alpha=0.04f)` 填充 + `Phosphor.copy(alpha=0.45f)` 描边 + `── AUTH ──` 横幅)。
- **输入框**:**不用裸 OutlinedTextField 默认样式**;做终端风格——`$ ` 提示符标签(`FoamMute`),输入区下划线/细框 `Dim`,**聚焦时边框转 `Phosphor` 并加微辉光**(`Phosphor.copy(alpha)` 描边),光标用 `Phosphor`。可用 `BasicTextField` + 自绘装饰,或 OutlinedTextField + 主题色覆盖(`OutlinedTextFieldDefaults.colors` focusedBorderColor = Phosphor)。优先后者(省事且一致)。
- **记住密码**:Checkbox 着色 `Phosphor`。
- **校内/校外**:`FilterChip`(跟成绩/DDL 设置页一致),默认校内。
- **主按钮**:磷光描边 +(可选)`Phosphor.copy(alpha=0.08f)` 填充;loading 态文字变 `验证中` + 尾随 `BlinkingCursor`;成功瞬时 `验证通过 ✓`(`Olive`)再回跳。
- **错误 banner**:复用现有 `ErrorBanner`(import 包里),`Carmine` 系;CaptchaRequired 带"打开网页端"动作。
- **跳过**:仅首屏场景显示,`FoamDim` 文本按钮,低调置底。

### 6.4 可选引导动效(门面加分,克制)
首次进入登录屏时,表单出现前播一段极短"开机自检"序列(2–3 行终端日志逐行淡入,如 `> 初始化安全模块…` `> 就绪`),末行带 `BlinkingCursor`,随后表单淡入。**单次、可跳过(点击即跳到表单)、≤1.2s**,不打断输入。若实现成本高可降级为纯淡入。

### 6.5 Settings「BIT 账号」行
放在 Settings `## timeline` 区顶部(或新 `## 账号` 区):
- 未登录:`NavigableRowWithSubtitle` "BIT 账号" / "未登录,点此登录 →" → `navigate(login?next=settings)`。
- 已登录:显学号 + "退出登录"动作(`Carmine` 文本/按钮),点击弹确认 → 见 §7。

---

## 7. 退出登录

`SettingsViewModel.onLogout()`:
1. `credPrefs.clear()`
2. `gradesPollScheduler.cancel()` + `gradesSyncPrefs.setEnabled(false)`
3. `ddlPollScheduler.cancel()` + `ddlSyncPrefs.setEnabled(false)`
4. (`hasSeenLogin` 不重置 —— 用户见过登录门了,退出不该让首屏门重现)

退出后 `isLoggedIn` 转 false,各功能入口恢复"未登录 → 跳登录"行为。Worker 即使漏关也会因无凭据自愈(`setEnabled(false)`)。

---

## 8. 测试策略(TDD)

| 单元 | 用例 |
|---|---|
| `LoginPrefs` | 默认 hasSeenLogin=false;setHasSeenLogin(true) 持久化 |
| `ValidateCredentialsUseCase` | Success→Success;各 CasLoginDto 失败映射;IOException→NetworkFail;**`close()` 总被调用**(finally) |
| `BitLoginViewModel` | 输入态;登录成功 → save creds + setHasSeenLogin + 发回跳事件;各失败 → banner + 不 save;跳过 → setHasSeenLogin + 不 save;loading 态切换 |
| `SettingsViewModel.onLogout` | clear creds + 两个 scheduler.cancel + 两个 prefs.setEnabled(false)(coVerify) |
| `AuthStatus` | isLoggedIn 随 creds 变化 |
| `GradesViewModel`(收敛后) | loggedIn 派生;已登录触发同步 / 未登录发导航(若 VM 承载守卫) |

UI 屏(`BitLoginScreen`、splash、Settings 账号行)编译 + 真机目检(视觉门面以真机为准)。

---

## 9. Phase 分解(供 writing-plans)

- **A 数据/用例**:LoginModels、ValidateCredentialsUseCase(+单测)、LoginPrefs(+单测)、AuthStatus(+单测)
- **B 登录 VM + 屏**:BitLoginViewModel(+单测)、BitLoginScreen(门面视觉)
- **C 导航接入**:NavRoutes LOGIN、AppNavHost 注册 + 守卫、冷启动 splash + 起始目的地
- **D 收敛**:成绩(退休 GradesSyncScreen,进度并入)、导入(凭据步退休)、DDL(守卫事件)
- **E 账号管理 + 退出**:SettingsScreen 账号行、SettingsViewModel.onLogout(+单测)
- **F 真机 DoD**(含视觉门面验收)

---

## 10. 风险

| 风险 | 缓解 |
|---|---|
| 立即验证依赖网络,校外 WEBVPN best-effort | 登录页带校内/校外切换;NetworkFail 文案提示切换重试 |
| 冷启动 splash 决策引入闪屏 | 读 DataStore 极快;splash 用终端 logo 兜底,过渡平滑;读到前不构建 NavHost |
| 收敛改动面大(动 3 个功能的入口 + 退休 2 屏) | Phase D 单独成阶段,逐功能改 + 编译;消费侧(凭据 store)不动降低风险 |
| 终端风格输入框自绘成本 | 优先 OutlinedTextField + 主题色覆盖(focusedBorderColor=Phosphor),非必要不自绘 |
| 引导动效过度/打断输入 | 单次、可点击跳过、≤1.2s;成本高则降级纯淡入 |

---

## 11. DoD

1. 全新安装 → 首次打开进登录屏(门面视觉:CRT 底 + VT323 logo),可输入登录或跳过
2. 输错密码 → 当场 banner 报错,不进 app、不存凭据
3. 正确登录 → 存凭据 + 进 app(或回 next 目标);重开 app 不再首屏弹登录
4. 跳过 → 进 app;重开不再弹;Settings「BIT 账号」显"未登录"
5. 未登录点成绩/导入/DDL → 跳登录屏,登录成功回到该功能
6. 已登录:成绩页直接同步(内联进度)、导入课表直接自动导入(用存凭据)、DDL 刷新直接拉
7. Settings「BIT 账号」已登录显学号;退出 → 清凭据 + 两个轮询关闭 + 各功能恢复未登录拦截
8. 视觉门面真机验收:不偏离终端/磷光绿风格,有高级感
9. 全量单测绿

---

## 12. Open Questions(实现期定,非阻塞)
- Hero logo 文案最终字样(`PERSONAL // STUDIO` 还是别的)— 真机看 VT323 渲染效果定
- 引导动效是否保留 / 降级 — 真机看观感定
- 「BIT 账号」放 `## timeline` 区顶部还是新建 `## 账号` 区 — 实现时按 Settings 现状定
