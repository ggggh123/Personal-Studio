# P2 Scanner · 设计规格（Design Spec）

**创建日期**：2026-04-22
**目标阶段**：P2（上一阶段 P1 chat 已于 2026-04-22 收官，tag `p1-chat-mvp`，merge commit `c6d95e9`）
**交付粒度**：`feature/p2-scanner` 分支 · 6 phases · 合并到 main 时打 tag `p2-scanner-mvp`

---

## 0. 阶段定位

P2 Scanner 是 Personal-Studio 的**核心功能之一**（与 P1 chat 并列）——把相机拍到的纸页转换成高对比度"扫描件"，组成多页文档，可导出 PDF；同时为 P1 chat 的"拍照搜题"入口提供共享的扫描化能力。

**用户确认的方向（brainstorm 2026-04-22）**：

- Scanner 是 App 的核心功能，"尽可能完善易用"优先于"最小可行"
- 多页文档模型（不是单张扫描集合）
- 拍后自动检测 4 角 + 手动微调（OpenCV）；实时取景自动抓拍（CamScanner 样式）留到 P2.5
- 三档滤镜：Color / Grayscale / B&W
- Chat 与 Scanner 通过 γ 方案共享 `EnhancePipeline`，chat 的 `--from-camera` 路径带一个可选 `[x] save to scans/` 勾选框

### 0.1 非目标（Out of Scope for P2）

- 实时取景边缘 overlay / 稳定后自动抓拍 → P2.5
- Magic 自适应滤镜（第 4 档）→ 按需再加，P3 KB 时重新评估
- OCR 文字识别 → P3+（ML Kit 要 Play Services 规避；Tesseract 需数十 MB 训练数据）
- Library 全文搜索 / 筛选 → P3（KB 会一起做索引）
- 多文档合并 PDF、把页插入现有 doc、云备份、从外部图片导入"变扫描"：全部延后
- 手写笔记支持、签名、涂鸦：不做

### 0.2 成功标准

功能验收（on-device 冒烟）：
1. 拍任意纸页 → 4 角自动落位（成功率 ≥ 80% 在白纸黑字 / 彩色讲义 / 铅笔手写场景）→ 失败能 fallback 到手动
2. 实时切换 3 档滤镜，点按后 ≤ 500ms 看到新预览
3. 能建一份 ≥ 5 页的 doc，重排、删页、重拍任一页都正常
4. 导出 PDF 到系统分享 sheet，微信能打开、邮件附件能打开
5. Chat 两条路径（`--from-camera` / `--from-scans`）能接回 P1 的 crop + send 流程
6. 意外杀进程后 pending doc 可恢复

非功能验收：
- 单元测试全绿（pipeline / repo / VM / UseCase）
- APK 安装后启动 ≤ 3 秒（OpenCV native lib 加载不阻塞首屏）
- 一份 10 页 doc 导出 PDF ≤ 5 秒（测试机：Redmi K70 级别）
- 拍摄 + 矫正 + filter 流程内存峰值 ≤ 150 MB

---

## 1. 架构

### 1.1 模块边界（delta 相对 P1 结构）

```
feature/
  chat/                             # P1 既有，不改动
  scanner/                          # 新增
    camera/                         # CameraCaptureScreen + 权限处理
    edge/                           # EdgeDetectAndCropScreen + CornerDragOverlay
    enhance/                        # EnhanceReviewScreen + 3 档 filter toolbar
    doc/                            # DocumentBuilderScreen + PageEditScreen
    library/                        # ScanLibraryScreen + ScanDocumentDetailScreen + ScanLibraryPickerScreen

domain/
  chat/                             # P1 既有
  scanner/                          # 新增
    CaptureAndEnhancePageUseCase
    AddPageToDocumentUseCase
    RemovePageUseCase
    ReorderPagesUseCase
    RecapturePageUseCase
    ExportDocumentToPdfUseCase
    CreateScanDocumentUseCase
    DeleteScanDocumentUseCase
    RenameScanDocumentUseCase
    CreateDocFromSinglePageUseCase   # chat --from-camera 的 save-to-lib 用

data/
  scanner/                          # 新增
    EnhancePipeline                 # 纯函数: (Bitmap, 4 corners, ScanFilter) -> Bitmap —— chat 也用
    EdgeDetector                    # OpenCV: Bitmap -> List<PointF>? （4 点或 null）
    PdfExporter                     # List<image path> -> PDF File
  repository/
    ChatRepository                  # P1 既有
    ScanRepository                  # 新增
  local/db/
    entity/
      ChatSessionEntity, ChatMessageEntity   # P1
      ScanDocumentEntity, ScanPageEntity     # 新增
    dao/
      ChatSessionDao, ChatMessageDao         # P1
      ScanDocumentDao, ScanPageDao           # 新增
```

依赖方向：feature → domain → data → core。无环。

### 1.2 新外部依赖

| 依赖 | 版本 | 用途 | 体积 |
|---|---|---|---|
| `androidx.camera:camera-core` | 1.4.x | CameraX 基础 | ~1.5 MB |
| `androidx.camera:camera-camera2` | 1.4.x | CameraX 相机 2 实现 | ~2 MB |
| `androidx.camera:camera-lifecycle` | 1.4.x | 生命周期绑定 | ~1 MB |
| `androidx.camera:camera-view` | 1.4.x | PreviewView | ~1 MB |
| `org.opencv:opencv-android` | 4.10.x | 边缘检测 + 透视矫正 + 滤镜 | ~30 MB（含 native libs）|

**ABI 过滤**：`abiFilters "armeabi-v7a", "arm64-v8a"`。放弃 x86 / x86_64（避免 APK 再膨胀 ~15 MB，模拟器一般不是日常工作流）。

**Manifest 增加**（已有 `<uses-permission android:name="android.permission.CAMERA"/>` 和 camera.any feature，不重复添加）：
- `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" ...>` 用于 PDF 分享
- `res/xml/file_paths.xml`：暴露 `cache-path name="export"` + `files-path name="scans"`

### 1.3 Room schema v4

**迁移策略**：本阶段 `fallbackToDestructiveMigration()` 即可（用户已确认当前 DB 内为测试数据）。不再写 `Migration(3, 4)`。

```kotlin
@Entity(tableName = "scan_documents")
data class ScanDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,               // 用户可编辑；默认 "scan_YYYY-MM-DD_HH-mm"
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,              // 去归一化：library 列表不用 JOIN 计数
    val coverPageId: Long?,          // NULL 表示 pending（未 finish）；非 pending 默认 = 第一页
)

@Entity(
    tableName = "scan_pages",
    foreignKeys = [ForeignKey(
        entity = ScanDocumentEntity::class,
        parentColumns = ["id"], childColumns = ["docId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("docId"),
        Index(value = ["docId", "ordinal"], unique = true),
    ],
)
data class ScanPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val ordinal: Int,                // 0-based；reorder 只改这个字段
    val originalImagePath: String,   // 透视矫正后、未 filter 的 JPG
    val enhancedImagePath: String,   // 当前 filter 输出；COLOR 时 = originalImagePath
    val filter: String,              // "COLOR" | "GRAYSCALE" | "BW"
    val cornersJson: String?,        // [[x,y],...] 共 4 点，原始拍摄图坐标系；NULL = 未矫正
    val createdAt: Long,
)
```

**DAO 主要方法**：
- `enum class SortMode { TIME_DESC, ALPHA_ASC, RECENT_UPDATED }` —— 分别对应 `createdAt DESC` / `title ASC COLLATE NOCASE` / `updatedAt DESC`
- `ScanDocumentDao.observeAll(sort: SortMode): Flow<List<ScanDocumentEntity>>`
- `ScanDocumentDao.observe(id): Flow<ScanDocumentEntity?>`
- `ScanDocumentDao.upsert`, `delete`, `rename`, `touchUpdatedAt`
- `ScanPageDao.observePages(docId): Flow<List<ScanPageEntity>>` — ordered by ordinal
- `ScanPageDao.upsertAll`, `deleteById`, `shiftOrdinalsForReorder(...)`

### 1.4 文件系统布局

```
filesDir/
├── chat-attachments/                # P1 既有
│   └── img_<ts>.jpg
└── scans/
    └── <docId>/                     # docId 立刻分配 → 目录立刻可用
        ├── tmp-<uuid>.jpg           # retake 时的临时捕获（finish 时 sweep）
        ├── page-<uuid>-orig.jpg     # 矫正后、未 filter
        ├── page-<uuid>-cooked.jpg   # 当前 filter 输出
        └── thumb-<uuid>.jpg         # 256dp 缩略图（library 列表渲染用）

cacheDir/
└── export/
    └── <docId>-<safeTitle>.pdf     # 临时产物，系统清 cache 时可被删
```

每页存 `orig` + `cooked` 两张图是刻意的：切换 filter 时只重跑 filter 步骤，不重算透视矫正（性能）；用户后续想换 filter 也无损。

---

## 2. 核心采集流程

### 2.1 屏幕树

| 屏幕 | 职责 | 导航进入方式 |
|---|---|---|
| `ScanLibraryScreen` | 文档列表（bottom nav 的 /scans tab） | bottom nav |
| `DocumentBuilderScreen` | 多页 doc 构建根（状态机 host） | Library `[+ new scan]` 或 chat `--from-camera` 的包装 |
| `CameraCaptureScreen` | CameraX 预览 + 快门 | DocumentBuilder 的 `[+ add next]` 或 chat 快捕 |
| `EdgeDetectAndCropScreen` | 自动检 4 角 + 拖拽微调 + 透视矫正 | Camera 返回后自动进入 |
| `EnhanceReviewScreen` | 3 档 filter + 实时预览 + 保存 | Edge confirm 后自动进入 |
| `PageEditScreen` | 已保存页的编辑器（filter 改 / retake / delete） | ScanDocumentDetail 里点某页 |
| `ScanDocumentDetailScreen` | 单 doc 详情：页网格 + 页/doc 操作 | Library 列表 tap |
| `ScanLibraryPickerScreen` | Library 的模态 picker（chat `--from-scans`） | chat AttachmentSheet |

### 2.2 状态机（DocumentBuilder）

```kotlin
data class DocBuilderUiState(
    val pendingDocId: Long,           // 建 doc 时立刻分配（写库一行，coverPageId=NULL 标记 pending）
    val pages: List<PendingPage>,     // 有序，ordinal = index
    val mode: Mode,                   // Building / Saving / Saved / Cancelled
    val toast: String? = null,        // "page 2 deleted" 等反馈
)

enum class Mode { Building, Saving, Saved, Cancelled }

data class PendingPage(
    val tempId: String,               // UUID；finish 时换成 Room 分配的 Long
    val origPath: String,
    val cookedPath: String,
    val filter: ScanFilter,
    val corners: List<PointF>,
)
```

**动作**：
- `addCapturedPage(origPath, cookedPath, filter, corners)` → append 到 pages
- `updatePage(index, cookedPath, filter)` → filter 切档 / retake 回填
- `removePage(index)` → 删除 + 磁盘清理 + ordinal 重排
- `reorder(from, to)` → 内存里移动，finish 时一次性写库
- `finish()` → 同事务：`scan_documents.coverPageId = pages[0].id`, `pageCount = pages.size`, `upsertAll pages`, sweep tmp-*
- `cancel()` → delete `scan_documents where id = pendingDocId`（ON DELETE CASCADE 清 pages），sweep 整个 `<pendingDocId>/` 目录

**意外退出恢复**：app 下次启动 library 查 `coverPageId IS NULL` 的 doc 用 `[incomplete]` 前缀显示，长按 → `[resume]` / `[discard]`。

### 2.3 OpenCV 边缘检测（EdgeDetector.detect）

```
Bitmap input
  ↓ resize short-side to 1000 px (加速)
  ↓ cvtColor → GRAY
  ↓ GaussianBlur(5,5) 去噪
  ↓ Canny(low=75, high=200) 边缘提取
  ↓ findContours(RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)
  ↓ 取最大面积轮廓
  ↓ approxPolyDP(epsilon=0.02 * arcLength) 多边形逼近
  ↓ if vertices == 4 && area ≥ 0.3 * imageArea:
      → sort TL/TR/BR/BL by centroid 方位
      → 返回 4 点（映射回原图坐标系：× scaleRatio）
    else:
      → 返回 null（UI fallback 到四周 10% 内缩的默认框）
```

### 2.4 透视矫正 `perspectiveWarp(bitmap, corners) → Bitmap`

```
w = max(dist(TL, TR), dist(BL, BR))
h = max(dist(TL, BL), dist(TR, BR))
transform = getPerspectiveTransform(
    src = corners (order: TL, TR, BR, BL),
    dst = [(0, 0), (w, 0), (w, h), (0, h)]
)
warped = warpPerspective(bitmap, transform, Size(w, h))
return warped
```

### 2.5 Enhancement `EnhancePipeline.process(warped, filter) → Bitmap`

```kotlin
enum class ScanFilter { COLOR, GRAYSCALE, BW }

suspend fun process(warped: Bitmap, filter: ScanFilter): Bitmap =
    withContext(Dispatchers.Default) {
        when (filter) {
            COLOR     -> warped                                                // 原样返回
            GRAYSCALE -> warped 经 cvtColor GRAY + convertScaleAbs(1.3, 10)
            BW        -> warped 经 cvtColor GRAY + adaptiveThreshold(
                             blockSize = 25, C = 10,
                             method = ADAPTIVE_THRESH_GAUSSIAN_C,
                             type = THRESH_BINARY,
                         )
        }
    }
```

### 2.6 EnhanceReviewScreen 实时预览

用户点 3 档 toolbar 按钮，立即（≤ 500ms）看到新预览。实现：VM 持一个 `Map<ScanFilter, Bitmap>` 缓存，最多 3 项，首次计算后回切同一档秒回。

```kotlin
class EnhanceReviewViewModel(
    private val origPath: String,
    private val pipeline: EnhancePipeline,
) : ViewModel() {
    private val cache = mutableMapOf<ScanFilter, Bitmap>()
    private val _state = MutableStateFlow(EnhanceUiState(currentFilter = BW, isLoading = true))
    val state = _state.asStateFlow()

    fun selectFilter(filter: ScanFilter) = viewModelScope.launch {
        val cached = cache[filter]
        if (cached != null) {
            _state.update { it.copy(currentFilter = filter, current = cached, isLoading = false) }
            return@launch
        }
        _state.update { it.copy(currentFilter = filter, isLoading = true) }
        val bmp = pipeline.process(loadOrig(), filter)
        cache[filter] = bmp
        _state.update { it.copy(current = bmp, isLoading = false) }
    }
}
```

Bitmap 内存：warp 后统一按 `DEFAULT_TARGET_LONG_SIDE = 2000 px` downscale；3 张缓存 ≈ 50 MB，在预算内。

### 2.7 retake 与 tmp 文件管理

- 每次拍 tmp：`filesDir/scans/<pendingDocId>/tmp-<uuid>.jpg`
- VM 维护 `pendingTmpPaths: MutableList<String>`
- confirm 保存时：tmp 升级为 `page-<uuid>-orig.jpg` + 生成 `cooked.jpg`，从 `pendingTmpPaths` 移除
- `finish()` / `cancel()` 时一次性 sweep 剩余 tmp（简单方案；单次会话里 retake 5 次会留 4 个 tmp 直到 finish，可接受）

---

## 3. Library + Doc 详情 + PDF 导出

### 3.1 ScanLibraryScreen

- `TerminalTopBar` route = `scans`，trailing `[+ new scan]`
- 排序：默认 `createdAt DESC`。顶部副标题行带一个小菜单：`# sort: [↓ time] | alpha | recent`，点切换
- LazyColumn，每行：封面缩略图（44×56dp）+ 标题 + pageCount + createdAt
- Pending doc（coverPageId = NULL）显示 `[incomplete]` 前缀
- **long-press 行** → 系统弹 `[resume]`（仅 pending）/ `[rename]` / `[delete]`
- **tap 行** → Detail 屏（pending 的 tap 则等价于 resume）
- 空态：复用现有 `ScannerPlaceholder`

### 3.2 ScanDocumentDetailScreen

- TopBar route = `scans/{title}`
- 副标题双行：`# session: {title}` / `# {pageCount} pages · {createdAt}`
- 页网格：2 列，每项 page 缩略图 + `p.{ordinal+1} · {filter}` 角标
- Tap 页 → `PageEditScreen`
- Reorder 模式：toolbar `[⇅ reorder]` 点击进入 → 每页显示拖拽把手；拖动过程中只改内存中的 List 顺序（不落盘），`[↵ done]` 退出时同事务 `shiftOrdinalsForReorder(docId, newOrder)` + `touchUpdatedAt(docId)` 一次写入；中途返回等同于放弃改动
- 底部：`[📄 export pdf]` / `[⇅ reorder]` / `[✎ rename]` / `[x delete doc]`

### 3.3 PageEditScreen

- 独立屏（不复用 EnhanceReviewScreen 的 mode 分叉）
- 主区：显示当前 page cooked + 3 档 filter toolbar（切档走相同 cache 逻辑）
- 底部：`[↻ retake]` / `[x delete page]` / `[✓ save]`
- retake 走 Camera → Edge → Enhance 流程，但 confirm 时覆盖当前 page 而不是 append

### 3.4 PdfExporter

```kotlin
class PdfExporter(private val context: Context) {
    suspend fun export(doc: ScanDocument, pages: List<ScanPage>): File =
        withContext(Dispatchers.IO) {
            val pdf = PdfDocument()
            pages.forEachIndexed { idx, page ->
                val bmp = BitmapFactory.decodeFile(page.enhancedImagePath)
                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                val pdfPage = pdf.startPage(info)
                pdfPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(pdfPage)
                bmp.recycle()
            }
            val file = File(context.cacheDir, "export/${doc.id}-${safeTitle(doc.title)}.pdf")
                .apply { parentFile?.mkdirs() }
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            file
        }
}
```

PDF 页尺寸 = 每张 enhanced 图的像素尺寸（不强拉 A4，保留原纵横比）。

**分享**：`FileProvider.getUriForFile → Intent(ACTION_SEND).setType("application/pdf").putExtra(EXTRA_STREAM, uri) → startActivity(createChooser)`。

---

## 4. Chat 集成（γ 方案）

P1 `AttachmentSheet` 当前状态：`--from-gallery` 启用；`--from-camera` / `--from-scans` 标 disabled。P2 把这两个启用。

### 4.1 `--from-camera` 路径（5 步）

1. 进 `CameraCaptureScreen`（包一层：wrapper 不走 DocumentBuilder 状态机，直接拿单张 tmp）
2. 进 `EdgeDetectAndCropScreen`
3. 进 `EnhanceReviewScreen` + 底部额外加 `[x] save to scans/` 勾选框（默认不勾）
4. 第 3 步 confirm 时：
   - 始终：把 cooked 复制一份到 `chat-attachments/img_<ts>.jpg`，调 `onImagePicked(path)`
   - 如勾选：`CreateDocFromSinglePageUseCase(origPath, cookedPath, filter, corners)` → 建 1 页 doc 进 library
5. 回到 `ChatDetailScreen`，P1 既有的 `ImageCropOverlay`（框选题目）被触发 → 发送给 LLM

### 4.2 `--from-scans` 路径（4 步）

1. 进 `ScanLibraryPickerScreen`（library 的模态版：tap 行为 = pick；不显示 long-press 菜单、不显示 `[+ new scan]`）
2. 选 doc → 页网格 → tap 某一页
3. 把该页 `cookedPath` 复制到 `chat-attachments/img_<ts>.jpg`，调 `onImagePicked(path)`
4. 回 `ChatDetailScreen`，P1 `ImageCropOverlay` → 发送

### 4.3 对 P1 的改动

**代码层面：AttachmentSheet 之外，P1 完全不动**。
- `ChatDetailViewModel.onAttachImage(path)` 接口保持，image path 的来源对它透明
- `AttachmentSheet.kt` 把两个 `disabled = true` 拆掉，分别拉起新的 nav route / 启动新的 screen
- P1 `ImageCropOverlay` 完整复用

---

## 5. 测试策略

### 5.1 单元测试（JVM，Fake 驱动）

| 测试 | 覆盖点 |
|---|---|
| `EnhancePipelineTest` | 3 档 filter 对固定 bitmap 的输出尺寸一致；GRAYSCALE 单通道、BW 像素集中两端 |
| `EdgeDetectorTest` | 3 张合成样本（黑矩形于白底 / 低对比度 / 无纸）断言返回 4 角 / null；非 byte-exact，用属性断言（面积 ≥ 30%、4 点 non-colinear）|
| `PdfExporterTest` | 2 张位图 → PDF → `PdfRenderer` 读回断言页数 + size |
| `ScanRepositoryImplTest` | Room in-memory；CRUD + ON DELETE CASCADE + 3 种排序 |
| `DocumentBuilderViewModelTest` | Building → addPage*N → reorder → finish → Saved；cancel 路径 sweep；retake 覆盖当前页 |
| `ScanLibraryViewModelTest` | 3 种 sort 切换；incomplete doc 前缀 |
| `ScanDocumentDetailViewModelTest` | reorder / delete page / rename / export 触发 |
| `CaptureAndEnhancePageUseCaseTest` + 另外 7 个 UseCase 的 happy path | - |

### 5.2 On-device 冒烟（每 phase 末尾）

1. 拍纸类型：白纸黑字 / 彩色讲义 / 铅笔手写 / 低对比度（白纸白桌）→ 边缘检测成功率
2. 切档：3 档点按 ≤ 500ms 看到预览
3. 多页：拍 5 页 → 重排 → 删第 3 页 → 导 PDF → 分享到 IM → 对方正常打开
4. Chat：`--from-camera`（含勾选 save 的路径） / `--from-scans` 两路都走通
5. 意外退出：DocumentBuilder 采集到第 3 页时杀 app，重开 → library 看到 `[incomplete]` → resume → 继续

---

## 6. 阶段划分

单分支 `feature/p2-scanner`，每 phase 末尾可 install + 冒烟通过；末尾一次性开 PR 到 main、打 tag `p2-scanner-mvp`。

| Phase | 内容 | 里程碑验证 |
|---|---|---|
| **1. 地基** | OpenCV + CameraX gradle 依赖、ABI 过滤、Room v4 新表 + DAO + Repository、EnhancePipeline / EdgeDetector / PdfExporter 实现、10 个 UseCase 骨架、单元测试全绿 | `./gradlew testDebugUnitTest` 绿；app 启动能调 `OpenCVLoader.initDebug()` logcat 正常 |
| **2. 单页走通** | CameraCaptureScreen + EdgeDetectAndCropScreen + EnhanceReviewScreen + 3 档实时预览 | 能：拍 1 张 → 检 4 角 → 拖微调 → 切 3 档看预览 |
| **3. 多页 + Library 列表** | DocumentBuilderScreen 状态机 + save 事务 + ScanLibraryScreen + ScannerPlaceholder 替换 + 3 种排序 + pending doc 恢复 | 能：建 doc → 连拍 N 页 → finish → library 看到 doc；意外退出恢复 |
| **4. Doc 详情 + 页操作** | ScanDocumentDetailScreen 网格 + PageEditScreen 独立屏 + reorder/delete/retake/rename/delete doc | 能：进 detail → 页操作 / doc 操作全部走通 |
| **5. PDF 导出** | PdfExporter 接 UI + FileProvider + Manifest + file_paths.xml + 分享 Intent | 能：detail `[📄 export pdf]` → 分享 sheet → 接收方能打开 |
| **6. Chat 集成** | 启用 AttachmentSheet 两个 disabled 项 + ScanLibraryPickerScreen + `[x] save to scans/` 勾选 | 能：chat `--from-camera` / `--from-scans` 走通，P1 crop + send 原样复用 |

---

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| OpenCV 首次集成 ABI / `initDebug()` 失败 | Phase 1 第一步先单独验证 `OpenCVLoader.initDebug()` → logcat `Loaded library` 通过，再做任何逻辑 |
| 低对比度边缘检测失败（白纸白桌） | 自动失败 → 返回 null → UI fallback 到四周 10% 内缩默认框 + 手动拖调 |
| Bitmap 内存 / OOM | `DEFAULT_TARGET_LONG_SIDE = 2000 px`，所有 pipeline 在 downscaled 版本上跑；EnhanceReview 的 3 档缓存总计 ≤ 60 MB |
| APK 尺寸膨胀到 ~70 MB | 用户已明示可接受；ABI 过滤已丢 x86/x86_64 |
| CameraX 权限拒绝 | ScanLibraryScreen 顶部显示 `[!] camera permission denied — grant` 引导进系统设置 |
| PDF 在低端机上合成缓慢 | `Dispatchers.IO` 跑，UI 显示进度；cacheDir 写入避免占用 filesDir 存储 |
| P1 回归 | P1 代码几乎不动（只改 AttachmentSheet 两个 disabled flag），P1 测试套件应持续全绿 |

---

## 8. 不在本 Spec 范围

以下属于"未来 phase"的 hook，不在 P2 实现但设计要为此留位：

- 实时取景边缘 overlay（P2.5）：扩展 `EdgeDetector` 支持 `detectFrame(ImageProxy)`，CameraX `ImageAnalysis` 注入
- Magic 自适应滤镜（P2.5 / P3）：`ScanFilter` 新增 `MAGIC` 枚举值，`EnhancePipeline.process` 加 case
- OCR（P3+）：给 `ScanPageEntity` 预留 `ocrText: String?` 字段（本 phase 先不建）
- Library 全文搜索（P3）：配合 P3 KB 的 FTS4 虚表一起建
- 从外部图片"变扫描"（future）：对接 `AttachmentSheet --from-gallery` 把已有图拖进 pipeline

---

## 9. Changelog

- **2026-04-22**: 初版。基于当日 brainstorming 4 节（doc model / edge detection / chat integration / filter / scope checklist）用户确认。
