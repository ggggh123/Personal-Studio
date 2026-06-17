# 设置页重构 设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与问题

`SettingsScreen` 仍是早期样式、信息杂乱：① 三大块 LLM 开发者配置（API 密钥 / 接口地址 / 模型）各带**超长 `#` 注释墙**（接口列 5 行示例 URL、模型列 4 行示例 id）；② 无层级——开发者 LLM 配置与用户向教务设置（学期/作息/通知/课程）全平铺同一级；③ 中英混杂（`save`/`clear`/`reset to default`、`SEMESTER`/`TIMETABLE`、`# bearer token` 等）；④ 早期残留 `## coming later` + `THEME (locked)` 死占位。

## 决策（已与用户确认）

- **信息架构 B**：主设置页只留干净分组导航清单；整块 LLM 配置挪进新子页「AI 模型设置」。
- 两页都：汉化、裁示例墙、删死占位、加「关于/版本」。

## ① 主设置页 `SettingsScreen`（重写为分组导航清单）

- 顶栏 `TerminalTopBar(route="settings", subtitle="# 偏好设置")`（去掉原 `# configure: … model=…`）。
- 三组,组头 `── 组名 ──`（FoamDim 破折号 + 标签）;每行 `▸ label … →`：
  - **教务 / 通用**：`学期起始日`(→SETTINGS_SEMESTER)、`作息表(13 节)`(→SETTINGS_TIMETABLE)、`通知与后台提醒`(→SETTINGS_NOTIF)、`课程列表`(→TIMELINE_COURSE_LIST)。
  - **AI**：`AI 模型设置`(→SETTINGS_LLM),副行 `密钥 · 接口 · 模型 · 测试`(FoamDim)。
  - **关于**：`版本 ${BuildConfig.VERSION_NAME} · 终端风主题`（替掉死占位;VERSION_NAME = "0.1.0-p0"）。
- 主页**不再注入 `SettingsViewModel`**（LLM 状态都搬走了）。新私有件 `SettingsGroupHeader(title)` + `SettingsNavRow(label, hint=null, onClick)`。

## ② 新子页 `LlmSettingsScreen`（搬入 + 瘦身 + 汉化）

- 把当前主页的 **API 密钥 / 接口地址 / 模型 / 诊断(测试连接)** 整体搬入,自带 `TerminalTopBar(route="settings/llm", subtitle="# AI 模型")` + 返回 + 可滚动正文(`navigationBarsPadding`)。**复用现有 `SettingsViewModel`**（hiltViewModel(),本就只装这些 LLM 状态/动作）。原助手件 `terminalFieldColors`/`terminalPrimaryButton`/`SectionHeader`/`KeyValueRow`/`StatusLine`/`DashedDivider` 随之迁到本文件。
- **裁示例墙**：
  - 接口地址 5 行 URL 示例 → 一行 `# OpenAI 兼容地址,自动补 /chat/completions  例: openrouter.ai/api/v1`。
  - 模型 4 行 id 示例 → 一行 `# 端点支持的模型 id  例: google/gemini-2.0-flash-exp:free`。
  - API 密钥注释 → 一行 `# OpenAI 兼容端点密钥;留空用内置默认`。
- **汉化**：`save→保存`、`clear→清除`、`reset to default→恢复默认`、`test llm ↵→测试连接 ↵`、`pinging…→连接中…`、`[ok]/[err]→[成功]/[失败]`、`(default)→(默认)`、`ACTIVE→当前`、section 头 `## api key/## api base url/## model/## diagnostic→## 密钥/## 接口地址/## 模型/## 连接测试`。字段标签 `API_KEY`/`API_BASE_URL`/`LLM_MODEL` 保留(技术词,终端风)。诊断说明文字汉化。

## ③ 路由 / 接线

- `NavRoutes.SETTINGS_LLM = "settings/llm"`。
- `AppNavHost` 加 `composable(SETTINGS_LLM) { LlmSettingsScreen(onBack = { navController.popBackStack() }) }`。
- 主页 AI 行 `onClick = { onNavigate(NavRoutes.SETTINGS_LLM) }`（沿用 SettingsScreen 既有 `onNavigate` 通道,AppNavHost 已 `onNavigate = { navController.navigate(it) }`）。

## 测试

- 纯 UI 重排 + 汉化 + 一新子屏,复用既有 VM/逻辑,不强测。真机 DoD：主设置页三组导航清单清爽、各行可点进对应页;`AI 模型设置` 进子页可改 密钥/接口/模型 + 测试连接(成功/失败显中文);关于显真实版本号;原英文/示例墙/死占位均消失。

## 不做 / 保留

- 不改 `SettingsViewModel` 逻辑/字段、不动各教务子页、不改数据/DB/网络。
- 不重命名 `SettingsViewModel`（虽现仅服务 LLM 子页;YAGNI,减少 DI/测试改动）。
- 字段技术标签(API_KEY 等)保留。

## 影响面

重写 `SettingsScreen.kt`（分组导航清单,去 VM 依赖）；新建 `LlmSettingsScreen.kt`（搬 LLM 配置 + 瘦身 + 汉化 + 迁助手件）；`NavRoutes`(+SETTINGS_LLM)、`AppNavHost`(+1 composable)。复用 `SettingsViewModel`/`TerminalTopBar`/主题色/`BuildConfig.VERSION_NAME`。无数据/DB/网络改动。
