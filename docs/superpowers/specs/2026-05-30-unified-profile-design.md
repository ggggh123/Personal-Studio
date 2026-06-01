# 统一 profile 个人中心 — 设计

## 背景与目标

App 的教务相关功能(课表导入、成绩、作业 DDL、考试、登录、两个后台轮询)已趋完善,但入口散落:大部分挤在 `SettingsScreen` 的「timeline」区(右上角齿轮进入),作业/考试又额外挂在 `TimelineScreen` 顶部(考试甚至两处都有,课表导入在 week-grid 也重复)。缺少一个统一、明显的教务入口。

**目标**:新增「个人中心 / profile」页,聚合所有**需登录**的教务功能入口;App 启动默认进此页;底部 tab 由 4 个改为 5 个,profile 居中凸显。

## 范围

**迁入 profile**(需登录的教务功能):登录/登出、课表导入、成绩查询、作业 DDL、考试安排、出分提醒(开关)、作业自动同步(开关)。

**保留在 Settings 齿轮**:timeline 配置(学期设置 `SETTINGS_SEMESTER`、课表设置 `SETTINGS_TIMETABLE`、通知 `SETTINGS_NOTIF`、课程列表 `TIMELINE_COURSE_LIST`)+ LLM/API/base-url/诊断等应用配置。齿轮入口维持现状(MainShell 全局 topbar),不收进 profile。

**移除**:`TimelineScreen` 顶部的「作业 ↗ / 考试 ↗」入口;`SettingsScreen` 中的教务行(登录/登出、IMPORT、GRADES、EXAMS、GRADES_POLL、DDL_POLL)。

非目标:不改各功能屏自身(GradesScreen / AssignmentsScreen / ExamsScreen / Import 向导 / poll 设置屏均原样复用);不改 deeplink 直达行为。

## 架构总览

- 新建 `feature/profile/ui/ProfileScreen.kt` + `feature/profile/ProfileViewModel.kt`。
- `NavRoutes.PROFILE = "profile"`;`AppNavHost` 加 ProfileScreen 目的地;`MainScreen.tabs` 居中插入 profile。
- `TerminalBottomBar` 改造:支持「中央凸起」tab。
- 启动默认页、tab 切换 `popUpTo` 锚点、LOGIN 成功/跳过目标:全部由 `CHAT` 改为 `PROFILE`。
- 抽取 `domain/auth/LogoutUseCase`,profile 与(原)Settings 复用同一登出逻辑。

ProfileScreen 只画内容;顶栏标题(`$ me`)与右上角齿轮由 MainShell 全局 topbar 提供(与现有 tab 屏一致)。

## ① 底栏 5-tab(TerminalBottomBar)

顺序:`chat · scan · [我] · kb · day`,profile 居中(第 3 位)。

- 中央 profile:用一个**上移、描边的方块**(Phosphor 边框,`Void`/`Deep` 底),像实体按钮浮在栏上方;图标 `Icons.Filled.Person`,标签「我」。
- 其余 4 个维持现样式(icon + label + 选中下划线)。
- 选中态:中央方块高亮填充(Phosphor 底 / Void 字)或边框转亮;其余维持 tint + 下划线。
- 终端风格,无圆角(矩形)。

实现要点:现有 `TerminalBottomBar` 是 `Row` + `SpaceEvenly` 的自定义栏。中央 tab 用更高的 `Box` + 负 `offset y` 上移 + `border`;栏整体高度略增以容纳凸起;`TerminalTab` 数据类加一个 `prominent: Boolean` 或单独渲染中央项。

## ② ProfileScreen 布局(混合:核心网格 + 设置列表)

`Box(背景 Void + scanLines + vignette)` → `Column(padding 16dp)`,内容(`LazyColumn`/`Column`):

1. **账号卡**(直角边框 + Deep 底):
   - 已登录:`◆ {学号}` + `已登录 · {校园网/外网}` + `[退出登录]` 按钮。
   - 未登录:`未登录,点此登录` → 整卡点击 `navigate(NavRoutes.login(next=null))`(普通登录,登完回 profile)。
2. **核心功能 2×2 网格卡**(呼应考试/作业信息卡设计语言:直角边框 + Deep 底 + 图标 + 名称 + 一行状态):
   - 课表导入(无状态,或「已导入」)→ `IMPORT_WIZARD`
   - 成绩(`GPA {总绩点}`,无成绩则不显示数值)→ `GRADES`
   - 作业 DDL(`{n} 待办`)→ `ASSIGNMENTS`
   - 考试安排(`{n} 即将`)→ `EXAMS`
3. **后台提醒列表**(2 行,`▸ 名称 … 状态 ›`):
   - 出分提醒(`已开 {间隔}h` / `已关`)→ `SETTINGS_GRADES_POLL`
   - 作业自动同步(`已开` / `已关`)→ `SETTINGS_DDL_POLL`

未登录时:网格卡仍渲染(状态留空),点击触发登录 guard `onNeedLogin → navigate(NavRoutes.login(next=该功能 route))`,与现有各屏 guard 一致;登录后回到目标功能屏。

## ③ ProfileViewModel 状态聚合

`combine` 多个本地源(全部无网络,纯读本地 + DataStore):

| 字段 | 来源 |
|---|---|
| 学号 / 网络模式 / 是否登录 | `ImportCredentialPrefs.observeAll()`(非空=已登录,`username`/`lastMode`) |
| 总 GPA / 是否有成绩 | `GradesDao.observeAll()` + `observeRanks()` → `ComputeGpaUseCase.invoke()` → `GradeBook` 的总 GPA(`book.isEmpty` 时不显示数值) |
| 作业待办数 | `TimelineDao.observeLexueDdls()` → 未完成且未过期 count |
| 考试即将数 | `TimelineDao.observeImportedExams()` → `(endAt?:startAt) >= now` count |
| 出分提醒开关 / 间隔 | `GradesSyncPrefs` |
| 作业自动同步开关 / 间隔 | `DdlSyncPrefs` |

`onLogout()` 调用 `LogoutUseCase`(见 ⑤)。`ProfileUiState` 暴露上述派生字段供 UI。

## ④ 导航 / 启动改造

- `NavRoutes.PROFILE = "profile"`;加入 `MainScreen.tabs`(居中);`AppNavHost` 加 `composable(PROFILE){ ProfileScreen(...) }`,接 `onNavigateTo*` 回调到各教务 route + `onNeedLogin`。
- **默认启动**:`RootViewModel.startDestination` 由 `CHAT` 改为 `PROFILE`(登录 gate 已 seen 时);真首次仍走 `LOGIN` gate,gate 的 skip/success 目标由 `CHAT` 改为 `PROFILE`。
- **tab 切换锚点**:`MainShell.onTabClick` 的 `popUpTo(NavRoutes.CHAT)` 改为 `popUpTo(NavRoutes.PROFILE)`(新 home 锚点)。
- `MainShell` topbar 标题表 + `isTabRoute` 判定:加入 PROFILE(标题 `$ me`)。
- **deeplink 保留直达**:通知用的 `grades`/`exams`/`assignments`/`settings/*-poll` 深链不变,不强制经由 profile。

需排查并更新所有硬编码 `NavRoutes.CHAT` 作为 home 的引用(RootViewModel、MainShell.onTabClick、BitLoginScreen skip/success)。

## ⑤ 入口迁移与登出抽取

- **`LogoutUseCase`(新,domain)**:封装现 `SettingsViewModel.onLogout` 的副作用 —— 清 `ImportCredentialPrefs` + 取消 `GradesPollScheduler`/`DdlPollScheduler` + 关闭 `GradesSyncPrefs`/`DdlSyncPrefs`。profile 复用;Settings 不再有登出行。
- **SettingsScreen 瘦身**:删教务行(登录/登出、IMPORT、GRADES、EXAMS、GRADES_POLL、DDL_POLL)及其相关 ViewModel 字段(`loggedInUsername` 等若仅此用);保留 timeline 配置 + LLM/API/诊断。
- **TimelineScreen**:删顶部「作业 ↗ / 考试 ↗」按钮及 `onOpenAssignments`/`onOpenExams` 透传。
- 课表导入在 week-grid 的入口可保留(就近便利,非教务杂乱来源),不做改动。

## ⑥ 未登录态

- 账号卡显示引导;核心网格卡点击走登录 guard(回跳目标功能屏)。
- profile 本身**不需要登录即可进入**(它是 tab + 默认页);只有点教务功能才要求登录。这与 P8 统一登录的「首屏门 + 功能拦截回跳」一致。

## ⑦ 错误处理与边界

- 无成绩时成绩卡只显示「成绩」入口(不显示 GPA 数值);各 count 为 0 时状态行留空或显示「—」。
- 登出为即时操作(无二次确认,与现状一致);登出后账号卡即时切回未登录态(observe 驱动)。
- profile 状态全部本地源,无加载失败路径;首帧用默认值。

## ⑧ 测试

- `ProfileViewModelTest`:已登录/未登录账号态映射;作业待办 count、考试即将 count、轮询开关状态;GPA 有/无成绩;`onLogout` 委托 `LogoutUseCase`。
- `LogoutUseCaseTest`:清凭据 + 取消两 scheduler + 关闭两 sync prefs 都被调用。
- 导航:默认目的地为 PROFILE(`RootViewModel` 测试);tab 锚点改动不破坏现有 tab 切换测试。
- 回归:Settings/Timeline 移除入口后相关 ViewModel 测试更新。

## 开放问题

无 —— 范围、底栏凸显形式(中央凸起方块)、布局(混合网格+列表)、GPA 可得性、齿轮去留(维持现状)均已确认。
