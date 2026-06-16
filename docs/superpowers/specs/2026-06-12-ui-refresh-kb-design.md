# UI 翻新 · 第 3 期 kb(知识库)设计

日期：2026-06-12
状态：已获批，待写实现计划

## 背景与范围

UI 翻新三期(chat→scanner→kb)的第 3 期、收官。通用约定(翻译"平衡"口味、布局精修原则、相机/画布只改 chrome、长文本表单全屏 Dialog 补 CRT 纹理)见第 1 期 spec 与记忆 [[project_ui_refresh]],不重复。复用前两期产出:`ui/components/TerminalDialog.kt`(`TerminalConfirmDialog`/`TerminalInputDialog`)、`ui/components/TerminalBottomSheet.kt`、`ui/components/BlinkingCursor.kt`;颜色 Void/Deep/Rule/Foam/FoamMute/FoamDim/Phosphor/Amber/Cyan/Carmine。

kb 现状(`feature/knowledge/`):多数组件已终端风(KbSearchBar/KbEntryRow/CategoryChipRow/RelatedEntriesSection/SummaryMarkdownEditor),但:① 落地页 `KbHomeScreen` **缺** chat/scanner 那种 `user@study:~$ ls …` + total N 头部(只有空态有终端 prompt 且提示过时);② 残留 Material:`KbEntryDetailScreen` 3 个 `AlertDialog` + `DropdownMenu` + `CircularProgressIndicator`、`CategoryPickerSheet` 的 `ModalBottomSheet`;③ `SavePreviewModal` 全屏 Dialog 无 CRT 纹理;④ 中英混杂(菜单/对话框/过滤名英文,正文标签多已中文);⑤ `KbEntryDetailPlaceholderScreen` 死代码。

## ① 落地页 KbHomeScreen:头部对齐 + 空态对齐 + 汉化

- **头部**:顶部加一行(同 chat/scanner)`user@study`(Amber) + `:~$ ` (FoamDim) + `ls kb/`(Foam),右侧无按钮(kb 新建条目来自 chat/scanner 归档,落地页无"新建"动作);其下 `total N`(FoamMute,N=当前列表条数)。再下面**保留**现有 `KbSearchBar`(`$ grep -r "…" kb/`)+ 过滤 chip + 分类 chip + 列表。
- **空态**:弃用 `KnowledgePlaceholder()`(`grep -r . kb/` + `no entries yet` + 过时 P3 提示),改 chat 同款内联空态:`# 暂无条目`(FoamMute) + 一行提示 `▓ `(Phosphor)`从 `(FoamDim)`[聊天]`(Cyan)` 或 `(FoamDim)`[扫描]`(Cyan)` 归档生成`(FoamDim) + `BlinkingCursor()`。kb 无自身"新建"动作(条目来自 chat/scanner 归档),故该 CTA 为**纯文字提示、不可点击**(`[聊天]`/`[扫描]` 仅作视觉指引)。
- **汉化**:过滤 chip `all/notes/mistakes` → `全部/笔记/错题`;分隔符 `matches/recent` → `匹配/最近`。保留 `$ grep -r`、`kb/`、`▎`/`⊕` 字形。

## ② KbEntryDetailScreen:Material 换共享件 + 汉化

- **3 个 AlertDialog → 共享件**:
  - 重命名(`rename entry` + 内嵌 BasicTextField + `save`/`cancel`) → `TerminalInputDialog(title="重命名条目", initial=当前标题, onConfirm, onDismiss)`。
  - 删除(`delete this entry?` + 中文体 + `delete`/`cancel`) → `TerminalConfirmDialog(title="删除此条目", message="将删除 kb_entries 行 + 本地图片 + 关联关系，无法撤销。", confirmLabel="删除", …)`。
  - 重新生成(`regenerate summary?` + 中文体 + `regenerate`/`cancel`) → `TerminalConfirmDialog(title="重新生成摘要", message="将重新调 LLM 覆盖 摘要 + 标准化题目；标题/分类保留。", confirmLabel="重新生成", …)`。
- **DropdownMenu → 新建 `TerminalDropdownMenu`**(见 ④):菜单项 `rename/change category/regenerate/delete` → `▸ 重命名`/`▸ 改分类`/`▸ 重新生成`/`▸ 删除`(删除项 Carmine)。
- **CircularProgressIndicator + 忙碌遮罩终端化**:NotFound 前的加载 spinner 与底部忙碌遮罩(`$ working...`)去掉 Material 转圈,用 `$ 处理中…`/`$ 加载中…` 文字 + `BlinkingCursor()`(遮罩仍是半透明 Void scrim)。
- **汉化**:`! entry not found` → `! 条目未找到`;`$ working...` → `$ 处理中…`;其余正文标签(`题目/摘要/来自:/其它/错题/原图`)已中文,保留。`kb/` 路由保留。

## ③ SavePreviewModal:补 CRT 纹理 + 汉化

- **纹理**:全屏 `Dialog` 内容根容器加 `.scanLines().vignette()`(同各 CRT 屏;`ui/theme/scanLines`/`vignette`)。保留 `Void` 底 + `usePlatformDefaultWidth=false` 全屏。
- **汉化**(保留 `$ ` prompt 前缀与技术词):`$ thinking...`→`$ 思考中…`、`$ writing entry...`→`$ 写入条目中…`、`archive draft`→`归档草稿`、`[save]`→`[保存]`、`[cancel]`→`[取消]`、`[retry]`→`[重试]`、`! llm error: …`→`! LLM 错误：…`、`$ title`→`$ 标题`、`$ category`→`$ 分类`、`$ standardized question`→`$ 标准化题目`、`$ summary`→`$ 摘要`、`$ related (AI-suggested)`→`$ 关联(AI 建议)`、`[edit raw markdown]`→`[编辑源码]`、`[preview]`→`[预览]`。fallback 横幅已中文,保留。

## ④ 新建共享控件 TerminalDropdownMenu

`ui/components/TerminalDropdownMenu.kt`:基于 `androidx.compose.ui.window.Popup` 的终端风下拉。
- 签名:`TerminalDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit)`;锚定在触发处下方(Popup 默认锚 + 适当 offset),`expanded` 控制显隐,点外部/返回触发 `onDismissRequest`。
- 外观:`Column`,背景 `Void`,`border(1.dp, Rule)`,0dp 直角(`RectangleShape`,注意在 `androidx.compose.ui.graphics`),内边距 4-8dp。
- 配套项:`TerminalDropdownItem(label: String, color: Color = Foam, onClick: () -> Unit)` 渲染 `▸ label` 行(fillMaxWidth + clickable + 纵向 padding),供调用方组装。

## ⑤ 删除死代码

- 删 `KbEntryDetailPlaceholderScreen.kt`(未接 nav,grep 仅自身定义)。删除前 grep 复核无引用。

## 不动 / 保留

- KbSearchBar(`$ grep -r`)、KbEntryRow(`▎`/`⊕`/相对时间已中文)、CategoryChipRow(`全部`/`[label]` 已中文/终端)、RelatedEntriesSection(`相关条目` 已中文)——仅在 ① 的汉化项内顺带处理英文残留,布局不重排。
- 数据层 / VM 逻辑 / 网络 不动。

## 测试

- 以汉化 + 纯展示为主,复用控件已测,不强测。
- `TerminalDropdownMenu` 纯展示控件,不强测(真机验交互)。
- 真机逐屏 DoD:KbHome(ls kb/ 头部 + total + 空态 chat 同款 + 过滤中文)、详情(下拉终端化 + 3 对话框终端化 + 忙碌态文字化)、SavePreviewModal(CRT 纹理 + 汉化)、分类 sheet(TerminalBottomSheet + 汉化)。

## 影响面

改 `KbHomeScreen`(+VM 取 total 用现有 state)、`KbEntryDetailScreen`、`SavePreviewModal`、`CategoryPickerSheet`、`SummaryMarkdownEditor`、`KbSearchBar`/`KbEntryRow`/`CategoryChipRow`/`RelatedEntriesSection`(汉化残英);新建 `ui/components/TerminalDropdownMenu.kt`;删 `KbEntryDetailPlaceholderScreen.kt`。复用 Phase 1/2 的 Terminal*/BlinkingCursor。无 DB schema/网络改动。
