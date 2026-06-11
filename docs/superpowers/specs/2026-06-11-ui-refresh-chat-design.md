# UI 翻新 · 第 1 期 chat 设计（含三期通用约定）

日期：2026-06-11
状态：已获批，待写实现计划

## 背景与总目标

chat / scanner / knowledge 三个早期功能：功能正常，但相比后期教务功能存在**风格与语言不一致**。调研结论：三者**其实已是终端风**（主题色、TerminalTopBar、`[action]` 括号按钮、Void 背景、monospace idiom），不是默认 Material。真正的不一致来自四层：① 英文界面（chat/scanner 几乎全英文、kb 混杂）；② 少数残留的原生 Material 控件（AlertDialog 圆角非 Void、OutlinedTextField、DropdownMenu、转圈 spinner、ModalBottomSheet 默认形状）；③ 全屏 Dialog 缺 CRT 纹理；④ 布局精修不足（**用户最看重**——会向教务屏的精致度看齐）。

**分三期，按功能切，每期独立 spec→plan→真机验**：
- **第 1 期 chat（本 spec）**：4 文件 + 顺带立起共享终端控件。
- 第 2 期 scanner（9 屏，复用控件）。
- 第 3 期 knowledge（10 文件，复用控件）。

## 三期通用约定

### 翻译约定（已确认「平衡」口味）
- **译**：人类可读文案 + 元数据标签（会话/模型/完成/归档/发送/在这里输入…）；括号动作内词（`[new]→[新建]`、`[cancel]→[取消]`、`[dismiss]→[关闭]`、`[confirm ↵]→[确认 ↵]`、`[+ archive]→[+ 归档]`）；contentDescription 等无障碍文案。
- **保留(不译)**：命令行骨架与纯技术词——`user@study:~$`、`studio:~/<route> $`、`ls`、`grep -r`、`--from-gallery` 等旗标、route 头部名、模型 id、`tokens`、`drwx──`、`[x]`、`↵`/`▸`/`▓` 等字形。
- 字体 `MapleMonoCnFamily` 全 CJK 覆盖，汉化字体安全。
- 字符串全部内联硬编码（全 App 如此，仅 strings.xml 有 app_name）→ 就地改字面量，**不引入字符串资源体系**。

### 布局精修原则（贯穿三期）
- 向教务屏精致度看齐：清晰的视觉层级、信息密度、分组/分隔、相对时间、过渡动效（400ms tween，同概览卡）、留白节律（16/20dp）。
- 相机/画布主导面（scanner 相机、edge/enhance、chat crop）：仅改 chrome（顶栏/按钮/提示），**不**给图像区铺扫描线，保画质。
- 长文本/表单类全屏 Dialog（kb SavePreviewModal 等）：补 CRT 纹理。

## 第 1 期 chat 设计

### A. 数据：会话富行（小聚合查询）
ChatList 富行需要「消息数 + 末条预览」，加一个轻量聚合：
- `ChatSessionDao` 新增（Room 投影，无 schema 变更——只读查询）：
  ```sql
  SELECT s.id AS id, s.title AS title, s.updatedAt AS updatedAt, s.iconHint AS iconHint,
    (SELECT COUNT(*) FROM chat_messages WHERE sessionId = s.id) AS msgCount,
    (SELECT contentMarkdown FROM chat_messages WHERE sessionId = s.id ORDER BY createdAt DESC LIMIT 1) AS lastSnippet
  FROM chat_sessions s ORDER BY s.updatedAt DESC
  ```
  返回 `Flow<List<ChatSessionSummaryRow>>`（@Entity 外的投影 data class，字段名与 AS 对齐）。
- 领域模型 `ChatSessionSummary(id, title, updatedAt, iconHint, msgCount, lastSnippet)`。
- `ChatRepository` 加 `observeSessionSummaries(): Flow<List<ChatSessionSummary>>`（投影→领域映射）；`renameSession`/`deleteSession` 已存在，直接用。`FakeChatRepository` 同步加该方法（测试用）。
- 无 DB 版本变更（纯新增只读查询）。

### B. ChatList 重设计（会话管理概览）
- 顶部：`user@study:~$ ls sessions/` + 右侧 `[+ 新建]`（Cyan，点击 createNewSession）；下一行 `total N`（FoamMute）。
- **按日期分组**：纯函数 `groupSessionsByDate(list, now): List<DateGroup>`（今天/昨天/本周/更早），每组 `── 今天 ───────` 分隔（Phosphor/FoamDim，flex-fill rule）。
- **富行（两行）**：
  - 行1：`▸ {title}`（Foam）+ 右对齐相对时间（FoamMute，`relativeTime` 工具）。
  - 行2：`最近: {lastSnippet 单行截断}`（FoamDim）+ 右 `{msgCount} 条`（FoamMute）。无消息时 line2 显 `最近: —`。
  - 整行 tap → 打开；长按 → 弹终端动作菜单。
- **管理操作**：长按行 → `TerminalBottomSheet` 动作菜单（`── 会话「{title}」──` 头 + `▸ 重命名` / `▸ 删除` 两行，复用共享 sheet，不另造组件）。
  - 重命名 → `TerminalInputDialog`（预填标题，`[取消]/[确认]`）→ `vm.onRename(id, title)`。
  - 删除 → `TerminalConfirmDialog`（`删除会话「{title}」？此操作不可撤销。`，`[取消]/[删除]` 后者 Carmine）→ `vm.onDelete(id)`。
- **空态**：`# 暂无会话` + `▓ 点 [新建] 开始第一个会话` + 收尾 BlinkingCursor。
- `ChatListViewModel`：`uiState` 换 `observeSessionSummaries()`；加 `onRename(id, title)`、`onDelete(id)`（委托 repo）。

### C. 共享终端控件（本期立起，scanner/kb 复用）
放 `ui/components/`：
- `TerminalDialog(onDismiss, content)`：`Dialog`（usePlatformDefaultWidth=false）+ Box(Void, border 1dp Rule, **0dp 直角**) + 内边距 + 可选 `── TITLE ──` 头。替代残留 Material `AlertDialog`。
- `TerminalConfirmDialog(title, message, confirmLabel, confirmColor=Carmine, onConfirm, onDismiss)`：基于 TerminalDialog，底部 `[取消] [confirmLabel]` 文字按钮。
- `TerminalInputDialog(title, initial, label, onConfirm, onDismiss)`：TerminalDialog + 终端风 `BasicTextField`（Phosphor 光标、`> ` 前缀、占位 FoamDim），底部 `[取消] [确认]`。替代残留 `OutlinedTextField`。
- `TerminalBottomSheet(onDismiss, header, content)`：包 `ModalBottomSheet`（containerColor=Void、shape 0dp、`dragHandle={}`、顶部 `── header ──`）。AttachmentSheet/CategoryPickerSheet 复用。
- `relativeTime(ts, now): String` util（刚刚/N分钟前/N小时前/昨天/N天前/yyyy-MM-dd）+ `RelativeTimeTest`——并把教务屏里重复的本地 `fmt` 收敛到此（仅顺手 DRY，不强求改全部）。

### D. ChatDetailScreen（最小改：已是参考实现）
- 汉化（见下「字符串映射」）。
- `IconButton(ArrowBack)` → 文字 `←`（FoamMute，对齐设置屏返回键）；`IconButton(Add)` → 文字 `[+]`（Cyan，与旁 `↵ 发送` 一致）。删 `Icons`/`IconButton`/相关 import。
- 间距对齐 16/10dp 节律（已基本符合，微调）。

### E. AttachmentSheet
- 汉化（旗标保留、描述译中）。
- 换用 `TerminalBottomSheet`（0dp、无 drag-handle、`── attach ──` 头）。

### F. ImageCropOverlay
- 汉化：`could not decode image→无法解码图片`、`[cancel]→[取消]`、`[confirm ↵]→[确认 ↵]`。
- 底栏上方加提示 `# 拖动四角裁剪`（FoamDim）。
- **图像主导面**：不铺扫描线（保裁剪画质）；保持 Void + 既有暗化遮罩即可（本面不算"补 CRT 纹理"的对象）。

### 字符串映射（chat 全量）
- ChatList：`no sessions yet→暂无会话`；`tap [new] to start a session→点 [新建] 开始一个会话`；`[new]→[新建]`；`— tap here to start your first session→— 点这里开始第一个会话`。保留 `ls sessions/`、`total N`。
- AttachmentSheet：保留 `attach --source` 与三个 `--from-*` 旗标；描述 `open photo picker→从相册选取`、`take a new photo (scanned)→拍一张新照片(扫描)`、`pick from scan library→从扫描库选取`。
- ChatDetail：`[+ archive session]→[+ 归档会话]`、`[+ archive]→[+ 归档]`、`[err]→[错误]`、`[dismiss]→[关闭]`、`[img]→[图片]`、`type something here...→在这里输入…`、`↵ send→↵ 发送`、`(untitled)→(未命名)`、`# session:→# 会话:`、`# model:→# 模型:`、`── done ──→── 完成 ──`、`── done · {t} · {n} tokens ──→── 完成 · {t} · {n} tokens ──`。保留 `[x]`、模型 id、`tokens`。contentDescription `back→返回`、`attach→附件`。
- ImageCropOverlay：见 F。

## 测试
- `ChatSessionSummary` 聚合查询：androidTest（`ChatDaoTest` 同目录）——插会话+若干消息，断言 msgCount 与 lastSnippet（按 createdAt DESC 取最后一条）。
- `groupSessionsByDate`：纯函数单测（今天/昨天/本周/更早 边界）。
- `relativeTime`：纯函数单测（各档边界）。
- `ChatListViewModelTest`：补 `observeSessionSummaries` 映射、`onRename`/`onDelete` 委托（`FakeChatRepository` 加对应方法 + 记录调用）。
- 共享控件（TerminalDialog/Confirm/Input/BottomSheet）纯展示，不强制单测；以编译 + 真机 DoD 验收。
- 真机 DoD：会话概览（分组/富行/相对时间/新建/长按重命名+删除确认）、对话页（字形返回/附件、汉化、错误条、附图）、附件 sheet、crop 汉化+提示，逐屏对照。

## 影响面
新增：`ChatSessionSummary` 模型 + DAO 投影/查询 + repo 方法、`ui/components/{TerminalDialog,TerminalConfirmDialog,TerminalInputDialog,TerminalBottomSheet}.kt`、`core/util/RelativeTime.kt`、`feature/chat/ui/components/`（分组/富行/动作菜单可拆小件）。改：4 个 chat 屏 + ChatListViewModel + ChatRepository(+Fake) + ChatSessionDao。无 DB schema 变更、无网络改动。scanner/kb 不在本期。
