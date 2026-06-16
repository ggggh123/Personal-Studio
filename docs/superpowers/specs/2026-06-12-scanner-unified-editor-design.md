# scanner 统一文档编辑器设计

日期：2026-06-12
状态：已获批，待写实现计划

## 背景与目标

当前扫描文档分"已完成/未完成"两态(`ScanDocument.isPending = coverPageId == null`)。点已完成文档进**详情页**只能重排+导出+点单页改/删,**不能添加页面**;只有"未完成"文档能经长按菜单"恢复"进**构建器**继续拍照加页,而构建器的 `[取消]` 会**删掉整篇文档**(隐患)。

目标:**去掉"已完成/未完成"区分,任何文档点进去都能随时编辑(增/删/重排/改页),并导出。**

## 关键发现(调研)

- `isPending`/`coverPageId` **纯粹是 UI 标记**,只驱动:库行 `[未完成]` 标签、库长按菜单(恢复/丢弃 vs 重命名/删除)、聊天选择器 `[未完成]` 标签。**不影响导航、导出、封面缩略图、KB。**
- 导航从不按 pending 分流:库行点按 → `onOpenDoc` → **总是**进详情页(`scanner/detail/{docId}`)。只有长按菜单按 `isPending` 给"恢复"。
- **能力割裂**:详情页(`ScanDocumentDetailScreen`)=重排+导出+点单页进 `PageEdit`(改滤镜/重拍/删页),**缺"添加页面"**;构建器(`DocumentBuilderScreen`)=拍照加页(内置 拍照→边缘→增强 状态机)+`finish`(调 finalize)+`cancel`(**删整篇**),**缺重排/导出/单页编辑**。
- `finalizeDocument` 只把 `coverPageId` 设为首页 id;封面缩略图用 `observeDocumentSummaries` 的"首页 ordinal"子查询,**不依赖 coverPageId**。`pageCount` 由 `recountPages` 每次增删自动维护,也不依赖 finalize。
- finalize 调用方:`DocumentBuilderViewModel.finish()`(将退役)、`QuickCaptureForChatViewModel.finalize()`(去掉该调用即可)。

关键文件:`feature/scanner/library/ScanDocumentDetailScreen.kt` + `ScanDocumentDetailViewModel.kt`、`feature/scanner/doc/DocumentBuilderScreen.kt` + `DocumentBuilderViewModel.kt`、`ui/AppNavHost.kt`(scanner 块)、`ui/navigation/NavRoutes.kt`、`feature/scanner/library/ScanLibraryScreen.kt`、`ScanLibraryPickerScreen.kt`、`domain/model/ScanModels.kt`、`feature/scanner/chat/QuickCaptureForChatViewModel.kt`。

## 设计:统一编辑器

### 架构
- **`ScanDocumentDetailScreen` 升级为唯一的文档编辑器**(保留文件名,职责扩为"编辑")。打开任何文档都进它。
- 它**吸收构建器的"拍照加页"能力**:`ScanDocumentDetailViewModel` 增加编辑状态机 `EditorStep`(`Viewing`/`Capturing`/`EdgeDetect`/`Enhance`)与加页逻辑(复用构建器现用的 `CaptureAndEnhancePageUseCase` + `AddPageToDocumentUseCase`)。其余沿用:`reorderPages`、`exportPdf`、点单页 `onOpenPage` → `PageEdit`。
- **退役** `DocumentBuilderScreen` + `DocumentBuilderViewModel`(及其测试)、`scanner/new` 路由、`onResumeDoc`。拍照子流程只存编辑器一处。

### 导航(`AppNavHost` + `NavRoutes`)
- 编辑器路由保留 `scanner/detail/{docId}`(LongType)。约定 **`docId == 0L` = 新建**。
- `ScanLibraryScreen` 的 `onNewDoc` → 导航到 `scannerDetail(0L)`;`onOpenDoc(id)` → `scannerDetail(id)`(不变)。
- 删除 `scannerNewDoc()` 路由 + `onResumeDoc` 回调 + 库长按"恢复"分支。
- 编辑器 VM 在 init:`val realId = if (navDocId > 0) navDocId else createPendingDocument(默认标题()); isNew = navDocId <= 0`。`isNew` 时编辑状态机起始为 `Capturing`(**自动开相机拍第一页**),否则 `Viewing`。

### 编辑器能力(详情页新貌)
- `Viewing` 态:2 列网格(长按拖动重排,已有)、点一页 → `PageEdit`(已有)、顶部/底部动作 `[+ 添加页面]`(→ `Capturing`)与 `[导出 PDF]`(已有)。
- `Capturing`/`EdgeDetect`/`Enhance` 态:内嵌 `CameraCaptureScreen`/`EdgeDetectAndCropScreen`/`EnhanceReviewScreen`(与构建器同样的串接),确认增强后 `vm.confirmPage(...)` → append 一页 → 回 `Viewing`。相机/边缘可取消回 `Viewing`(已有文档)或触发空文档清理(新文档无页时)。
- **返回:仅退出,绝不删已有文档**(消除构建器"取消即删整篇"的隐患)。

### 新建 + 空文档清理
- 新建文档带 `isNew=true` 进编辑器、直接开相机。
- 退出(顶栏 `[< 返回]` 与系统返回,均经 `BackHandler`)走 `vm.onExit { 回退 }`:**若 `isNew && 当前 0 页` → `deleteDocument(realId)`(丢弃空壳)**;否则正常退出。已有文档(`isNew=false`)永不自动删。
- 相机首屏直接取消(新文档、还没拍页)= 0 页退出 → 丢弃。

### 去掉"已完成/未完成"区分
- `ScanModels`:删除 `ScanDocument.isPending` 与 `ScanDocumentSummary.isPending` 派生属性及其全部引用。
- `ScanLibraryScreen`:删行 `[未完成]` 标签;长按菜单**统一为 `▸ 重命名`/`▸ 删除`(带确认)**,删除"恢复/丢弃"分支与 pending 头部后缀;移除 `onResumeDoc` 参数。
- `ScanLibraryPickerScreen`:删 `[未完成]` 标签(`isPending` 引用)。
- 不再调用 `finalizeDocument`:`QuickCaptureForChatViewModel` 去掉 finalize 调用(仍创建文档+1页)。`coverPageId` 列**保留不用**(封面取首页 ordinal,不依赖它)——**无 DB schema 改动**。`finalizeDocument`/`FinalizeScanDocumentUseCase` 可留作死代码(本期不强删)。

## 测试

- `ScanDocumentDetailViewModel`(用 `FakeScanRepository`):
  - 新建(navDocId=0)→ 创建文档 + `isNew=true` + 起始 `Capturing`;`confirmPage` 后 append 一页且回 `Viewing`。
  - 打开已有(navDocId>0)→ `isNew=false` + 起始 `Viewing` + 串现有页。
  - `onExit`:`isNew && 0 页` → 删文档;有页或非新 → 不删。
  - `reorderPages`/`exportPdf` 委托不回归(已有则保留)。
- 库菜单统一:长按任意文档(不分 pending)→ 重命名/删除(纯展示,不强测;真机验)。
- 真机 DoD:任意文档点进去能加/删/重排页 + 导出;新建直开相机;相机首屏取消→空文档被丢弃;库不再有 `[未完成]`,长按只有重命名/删除。

## 影响面

- **改**:`ScanDocumentDetailScreen` + `ScanDocumentDetailViewModel`(吸收加页+空文档清理+BackHandler)、`AppNavHost`(新建→`scannerDetail(0L)`、VM 处理 docId=0、删 resume/new 路由)、`NavRoutes`(删 `scannerNewDoc`)、`ScanLibraryScreen`(去 isPending、菜单统一、去 onResumeDoc)、`ScanLibraryPickerScreen`(去标签)、`ScanModels`(去 isPending)、`QuickCaptureForChatViewModel`(去 finalize)。
- **删**:`DocumentBuilderScreen` + `DocumentBuilderViewModel`(+ `DocumentBuilderViewModelTest`)。
- 无 DB schema / 网络改动。复用现有 capture 用例与 Camera/Edge/Enhance 复合屏。
