# UI 翻新 · 第 2 期 scanner 设计

日期：2026-06-11
状态：已获批，待写实现计划

## 背景与范围

UI 翻新三期(chat→scanner→kb)的第 2 期。通用约定(翻译"平衡"口味、布局精修原则、相机/画布面只改 chrome 不铺扫描线)见第 1 期 spec `docs/superpowers/specs/2026-06-11-ui-refresh-chat-design.md` 与记忆 [[project_ui_refresh]],不在此重复。复用第 1 期产出的共享终端控件:`ui/components/TerminalDialog.kt`(`TerminalDialog`/`TerminalConfirmDialog`/`TerminalInputDialog`)、`ui/components/TerminalBottomSheet.kt`。

scanner 共 9 屏(`feature/scanner/`),分两组:
- **库组(3 屏,重点精修)**:`library/ScanLibraryScreen`、`library/ScanDocumentDetailScreen`、`library/ScanLibraryPickerScreen`。
- **拍摄链(4 屏,图像主导,chrome-only)**:`camera/CameraCaptureScreen`、`edge/EdgeDetectAndCropScreen`、`enhance/EnhanceReviewScreen`、`doc/PageEditScreen`。
- `chat/QuickCaptureForChatScreen`(纯状态机、无自身 UI)、`doc/DocumentBuilderScreen`(已较终端化)——只汉化,不重排。

现状:全部已是终端风(Void/TerminalTopBar/`[action]`/`drwx──`),**全英文**;库屏有 3 个原生 `AlertDialog` + `OutlinedTextField`;库列表行的缩略图是空占位(`ScanThumbnail(path=null)`)。

## ① 数据:封面缩略图聚合(无 schema 变更)

库列表行要显示真实封面。`ScanDocumentEntity` 已有 `coverPageId`,`scan_pages` 有 `docId`/`ordinal`/`enhancedImagePath`。加一个只读聚合:
- `ScanDocumentDao` 新增投影 `ScanDocumentSummaryRow`(ScanDocument 全字段 + `coverPath: String?`)与查询(每个 sort 一条,或一条 + 仓库排序):
  ```sql
  SELECT d.*, (SELECT enhancedImagePath FROM scan_pages WHERE docId = d.id ORDER BY ordinal ASC LIMIT 1) AS coverPath
  FROM scan_documents d ORDER BY <按 sort>
  ```
  采用**一条无序聚合 + 仓库层按 `SortMode` 排序**(scan 量小,简化):`observeDocumentSummaries(): Flow<List<ScanDocumentSummaryRow>>`。
- 领域模型 `ScanDocumentSummary`(= ScanDocument 字段 + `coverPath: String?`)。
- `ScanRepository.observeDocumentSummaries(sort): Flow<List<ScanDocumentSummary>>`:映射 + 按 sort 排序(time=createdAt 降、alpha=title COLLATE NOCASE 升、recent=updatedAt 降)。`FakeScanRepository` 同步实现(从 docs+pages 派生 coverPath)。
- `ScanLibraryViewModel.uiState.docs` 由 `List<ScanDocument>` 换成 `List<ScanDocumentSummary>`(`ScanDocumentDetail`/`Picker` 的 VM 若也展示缩略图,同法;否则不动)。
- 无 DB 版本变更(纯只读查询)。

## ② 库屏:共享控件 + 汉化 + 封面 + 精修

### ScanLibraryScreen
- **3 个 AlertDialog → 共享 Terminal* 控件**:
  - `DocActionsDialog` → `TerminalBottomSheet`(`── 「{title}」──` 头;完成件 `▸ 重命名`/`▸ 删除`(Carmine);pending 件 `▸ 恢复`/`▸ 丢弃`(Amber))。
  - `RenameDialog`(含 `OutlinedTextField`) → `TerminalInputDialog`。
  - `DeleteConfirmDialog` → `TerminalConfirmDialog`(`删除「{title}」？此操作不可撤销。`)。
- **汉化**:`[+ new scan]→[+ 新建扫描]`;排序 `time/alpha/recent→时间/名称/最近`(`# sort:` 保留);行 `drwx── [incomplete] 标题`→`drwx── [未完成] 标题`;`N page(s) · ts`→`N 页 · {相对时间}`;对话/按钮文案全译(`[ resume ]→[恢复]`、`[ discard ]→[丢弃]`、`[ rename ]→[重命名]`、`[ delete ]→[删除]`、`[ ok ]→[确认]`、`[ cancel ]→[取消]`、`resume the in-progress scan…`、`pick an action…`、`rename scan`、`this cannot be undone.`)。保留 `drwx──`、`# sort:`。
- **封面 + 精修**:行内 `ScanThumbnail(path = summary.coverPath)` 渲真封面;时间**保留绝对短格式**(`MM-dd HH:mm`,文档库看精确日期更有用),不引相对时间、不依赖 chat 包;封面+两行文案的对齐/间距微调。

### ScanDocumentDetailScreen / ScanLibraryPickerScreen
- 汉化全部英文 UI 文案(导出/分享/提示/`[< back]` 等);任何残留 Material 控件(若有 AlertDialog 等)换共享 Terminal*;2 列缩略图网格的间距/标注精修。
- 系统分享 chooser 标题 `Share PDF`→`分享 PDF`。

## ③ 拍摄链:汉化 + chrome(不动图像)

- **CameraCaptureScreen**:汉化 toggles(`[auto ✓]`/`[⚡ off]`→中文档位)、快门栏 `[cancel]→[取消]`、权限页(`[!] camera permission denied`/`[grant]`→中文)。**不动相机预览/角点绘制,不加扫描线。**
- **EdgeDetectAndCropScreen**:汉化 `[↻ retake]→[↻ 重拍]`、状态 `✓ corners auto-detected`/`! drag corners to fit`、`[confirm ↵]→[确认 ↵]`。不动图像/角点拖拽。
- **EnhanceReviewScreen**:汉化滤镜 `[original][bw][contrast…]`(标签译中,保留必要技术词如 bw 可酌情)、`[↻ rot]`、`save to scans/` 复选、`[cancel]/[confirm ↵]`。不动图像。
- **PageEditScreen**:汉化 `scans/edit` 工具栏(`[↻ retake]`/`[+ archive]`/`[x delete page]` 译中)、滤镜行;删除 `AlertDialog`(`delete this page?`/`the image files will be removed…`)→ `TerminalConfirmDialog`。不动图像。
- **DocumentBuilderScreen**:仅汉化(`N page(s) in this doc`、`# no pages yet — tap capture below`、`[+ add next page]`、`[↵ finish]`、`[x cancel]`),已较终端化、不重排。

## 测试

- 封面聚合:`ScanDocumentSummary` 行为测试——`FakeScanRepository.observeDocumentSummaries` 派生 coverPath(首页 enhancedImagePath)、排序正确(time/alpha/recent);真 SQL 的 instrumented 测试(`ScanDaoTest` 同目录,若存在;断言 coverPath 取首页、无页为 null)。
- `ScanLibraryViewModel`:docs 映射 summary + onRename/onDelete/setSort 委托(已有测试则补)。
- Terminal* 控件纯展示,不强测。
- 真机逐屏 DoD:库(封面/分组排序/长按动作/重命名/删除终端弹窗)、详情/选择器、拍摄链各屏(汉化、图像清晰无扫描线)。

## 影响面

改 9 屏(库 3 + 拍摄链 4 + DocumentBuilder 汉化 + QuickCapture 不动);scanner 数据层(summary 聚合查询 + repo + Fake + 模型)+ ScanLibraryViewModel。无 DB schema/网络改动。复用 Phase 1 的 Terminal* 控件。
