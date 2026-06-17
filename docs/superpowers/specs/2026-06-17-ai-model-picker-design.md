# AI 模型选择（基础选择器 + 高级门）设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与目标

当前 AI 配置只有「自定义 URL/API/Model」的开发者表单（`LlmSettingsScreen`，已是设置重构的高级页）。目标：给普通用户一个**内置默认模型选择器**——从一份精选名单里切换模型,端点+密钥作为 App 内置默认**隐藏**;原始 URL/API/Model 自定义降级为**「AI 模型高级设置」**,点击前弹警告确认。

数据来源 `AI-models` 文件：11 个模型(显示名↔实际代号),共用同一聚合接口(地址 + key 见仓库外 AI-models 文件,不写入任何提交)。

## 决策（已与用户确认）

- 基础选择器：**独立子页**。
- 内置默认模型：**Gemini 3.5 Flash**（`gemini-3.5-flash`）。
- 端点+密钥内置隐藏;高级自定义加警告门。

## ① 模型名单 + 内置端点/密钥

- **名单**（公开,提交）：新 `core/llm/CuratedModels.kt`：
  - `data class CuratedModel(val display: String, val code: String)`
  - `object CuratedModels { val ALL = listOf(... 11 条 ...); fun displayFor(code: String?): String? }`
  - 11 条：Claude Opus 4.8→claude-opus-4-8、Claude Sonnet 4.6→claude-sonnet-4-6、GPT 5.5→gpt-5.5、Gemini 3.5 Flash→gemini-3.5-flash、Gemini 3.1 Pro→gemini-3.1-pro-preview、Deepseek V4 Flash→deepseek-v4-flash、Deepseek V4 Pro→deepseek-v4-pro、GLM 5.2→glm-5.2、Kimi 2.6→kimi-2.6、Minimax M3→MiniMax-M3、豆包→doubao-seed-2-0-lite-260428。
- **内置端点+密钥**（**真值不写进任何提交文件**）：实际端点 URL 与 API key 取自 `AI-models` 文件,写进 `local.properties`（已 gitignore;build.gradle 既有机制 `localProps.getProperty("API_KEY"/"API_BASE_URL"/"DEFAULT_LLM_MODEL")` 注入 `BuildConfig.DEFAULT_*`）：`API_BASE_URL=<AI-models 接口地址去掉尾部 /chat/completions>`、`API_KEY=<AI-models 的 sk-… key>`、`DEFAULT_LLM_MODEL=gemini-3.5-flash`。（App 自动补 `/chat/completions`。）
- `AI-models` 文件（含明文密钥）加入 `.gitignore`,防误提交。**本 spec / 计划 / 提交里一律不出现 key 与端点真值。**

## ② 基础选择器子页 `AiModelScreen`（route `settings/ai-model`）

- `TerminalTopBar(route="settings/ai-model", subtitle="# AI 模型")` + 返回 + 可滚动正文(navigationBarsPadding)。复用 `SettingsViewModel`。
- 当前生效代号 `active = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL`。
- **11 行单选**：每行 `● 显示名`(当前=Phosphor 实心点+Foam)/`○ 显示名`(其余=FoamMute),整行可点 → `vm.selectCuratedModel(code)`。
- 若 `active` 不在名单 → 顶部提示行「当前：⟨active⟩（自定义）」(Amber)。
- 底部 `⚙ AI 模型高级设置`(FoamMute,可点)→ 弹警告弹窗。

## ③ 切模型逻辑 `SettingsViewModel.selectCuratedModel(code)`

```
setModelName(code); setApiBaseUrl(null); setApiKey(null)
```
切到名单模型时清掉任何自定义 url/key → 回退到内置 `BuildConfig.DEFAULT_API_BASE_URL`/`DEFAULT_API_KEY`(内置端点)。`LLMProvider` 既有 null→default 解析(现有 reset 行为印证)负责生效。

## ④ 高级门：警告弹窗 → 现有 `LlmSettingsScreen`

点「AI 模型高级设置」弹 `TerminalConfirmDialog(title="高级设置", message="进行此操作之前，请确保你已经对通过 API 调用大模型的流程十分熟悉。", confirmLabel="我已了解,继续")`：确认 → `onOpenAdvanced()` → 导航 `SETTINGS_LLM`(现有自定义页,逻辑不动);取消 → 关闭。

## ⑤ 主设置 AI 行改向

`SettingsScreen` 的 AI 组行由 `AI 模型设置→SETTINGS_LLM` 改为 `AI 模型→SETTINGS_AI_MODEL`,副行「切换内置模型 · 高级自定义」。主页不再直链 LlmSettingsScreen(只能经选择器→高级门进)。

## ⑥ 路由

- `NavRoutes.SETTINGS_AI_MODEL = "settings/ai-model"`。
- `AppNavHost`：`composable(SETTINGS_AI_MODEL) { AiModelScreen(onBack=popBackStack, onOpenAdvanced={ navigate(SETTINGS_LLM) }) }`。`SETTINGS_LLM` 已有,保留。

## 测试

- 单测 `CuratedModelsTest`：`displayFor("gemini-3.5-flash")="Gemini 3.5 Flash"`、未知代号→null、null→null;`ALL.size==11`。
- 选择器交互/门/内置端点生效走真机 DoD。

## 真机 DoD

设置→AI 模型：① 11 模型单选,默认勾 Gemini 3.5 Flash;点别的即切换、聊天/分析用新模型(端点密钥不露);② 顶部不显"自定义"(除非进高级改过);③ 点高级设置 → 弹警告 → 确认进自定义页 / 取消留下;④ 自定义改 URL/model 后回选择器顶部显「当前：⟨…⟩(自定义)」,再选名单模型可切回内置。

## 不做 / 保留

- 不改 `LlmSettingsScreen` 内部逻辑、不改 `LLMProvider`/数据层、不改 DB/网络。
- 名单为静态(11 条),不做远程拉取/动态增删(YAGNI)。
- 不在主设置页内联模型选择(放子页)。

## 影响面 & 安全

新建 `core/llm/CuratedModels.kt`、`feature/settings/ui/AiModelScreen.kt`、`CuratedModelsTest`;改 `SettingsViewModel`(+selectCuratedModel)、`SettingsScreen`(AI 行)、`NavRoutes`/`AppNavHost`(+SETTINGS_AI_MODEL)、`local.properties`(端点/密钥/默认,**不提交**)、`.gitignore`(+AI-models)。复用 `SettingsViewModel`/`TerminalConfirmDialog`/`TerminalTopBar`。**安全：真密钥仅入 gitignore 的 local.properties,源码/提交零明文密钥。** 接 `feat/settings-refresh` 分支做。
