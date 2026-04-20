# Personal-Studio · 设计规格（Design Spec）

**创建日期**：2026-04-20
**目标交付**：2026 年 6 月底（~2 个月）
**角色**：Android 结课作业 + 个人长期使用的学习生活助手 App
**作者**：项目所有者（via AI 辅助设计）

---

## 0. 项目总览

### 0.1 项目定位

Personal-Studio 是一款 Android 端的**个人学习生活助手 App**，核心亮点是**以大语言模型（LLM）能力为驱动，贯穿"学习内容采集 → 问答辅导 → 知识沉淀 → 日程管理"全流程**。

### 0.2 目标用户

- 大学生（首期只适配作者本人的学校与课程体系）
- 有用 AI 辅助学习习惯
- 需要统一管理教科书扫描件、AI 问答记录、课表/作业 DDL 的用户

### 0.3 项目背景

- 结课作业的 DDL 宽松（~2 个月），作者将项目视为个人野心级的学习工具，不是最小演示
- 代码几乎全部由 AI 编程工具生成（作者主要负责方向、审核、验收）
- 因此 spec 的精确度与验证检查点是项目成败的关键

### 0.4 非目标（Out of Scope）

- 不做多用户 / 账号系统 / 云端同步（P6 stretch 可考虑 JSON 导出作为备份）
- 不做跨平台（Android only）
- 不做付费 / 订阅
- 不提供 AI 代理式多工具链编排（当前只做单次 LLM 调用 + prompt engineering，不是真正 agent）

---

## 1. 核心功能与优先级

项目初期规划 5 个功能，优先级已确认：

| 优先级 | 功能 | 阶段 |
|---|---|---|
| ① | **多模态 AI 问答**（含框选图提问） | P1（MVP） |
| ② | **知识库**（条目化沉淀 + 数学渲染 + 可选图谱） | P3 |
| ③ | **教科书扫描**（对比度增强 + PDF 导出） | P2 |
| ④ | **时间轴日程**（气泡形式 + 推送提醒） | P4 |
| ⑤ | **课表 / DDL 导入**（三路径 C→B→A） | P5 |

---

## 2. 架构决策

### 2.1 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Navigation Compose + Material3 |
| 状态管理 | AndroidX ViewModel + StateFlow + `collectAsStateWithLifecycle` |
| DI | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 本地存储 | Room + SQLite FTS4（全文搜索）+ DataStore（偏好） |
| 文件存储 | 内部存储 `filesDir/scans/`、公共 Downloads（PDF 导出）|
| 网络 | OkHttp（底层）+ Gemini 官方 SDK（`com.google.ai.client.generativeai`） |
| 图像 | CameraX + OpenCV（opencv-mobile 变体） |
| PDF 生成 | Android 原生 `PdfDocument` |
| 后台任务 | WorkManager |
| 数学公式 | KaTeX 本地资源 + WebView 包装成 Composable |
| Markdown | `marked.js`（与 KaTeX 同一 WebView） |
| ICS 解析 | `biweekly` 库（Plan B） |
| 序列化 | kotlinx.serialization |
| 测试 | JUnit4、MockK、Coroutines Test、Compose UI Testing |

**Kotlin 最低版本**：2.0+
**minSdk**：30（已在脚手架中设置）
**targetSdk**：36

### 2.2 Gradle 模块

**单模块**（`:app`）+ 严格的包划分。理由：
- 单人 + 2 个月 + AI 驱动开发，多模块 Gradle 配置负担大，AI 在跨模块依赖上错误率较高
- 包划分一样能强制分层，后期若需拆分为多模块成本低
- 构建更快，有利于快速迭代

**包结构**（`com.example.personal_studio.*`）：

```
ui/
  theme/               // Color.kt, Typography.kt, Shape.kt
  navigation/          // AppNavHost.kt, NavRoutes.kt
  components/          // 共享 Composable（MarkdownKatexView, LoadingSpinner...）

feature/
  chat/
    ui/                // ChatListScreen, ChatDetailScreen, ImageCropOverlay
    vm/                // ChatListViewModel, ChatDetailViewModel
    model/             // UiState
  scanner/
    ui/                // ScanLibraryScreen, CameraCaptureScreen, ScanEditScreen, PdfExportScreen
    vm/
    model/
  knowledge/
    ui/                // KBHomeScreen, KBEntryDetailScreen, KBGraphScreen
    vm/
    model/
  timeline/
    ui/                // TimelineScreen, TaskDetailScreen, AddTaskScreen
    vm/
    model/
  schedule/
    ui/                // ImportHomeScreen, WebViewLoginScreen, ImportResultScreen, AddCourseScreen
    vm/
    model/
  settings/
    ui/, vm/

domain/
  chat/                // SendMessageUseCase, StreamLLMUseCase
  knowledge/           // SummarizeToKBUseCase, SearchKBUseCase
  scanner/             // EnhanceScanUseCase, ExportPdfUseCase
  timeline/            // ScheduleReminderUseCase, ExpandRecurrenceUseCase
  schedule/            // ImportScheduleUseCase, ParseIcsUseCase, ScrapePortalUseCase
  model/               // 纯 Kotlin 数据类（无 Android 依赖）

data/
  local/
    db/                // AppDatabase, 各 DAO
    entity/            // Room entity
    datastore/         // UserPreferences
  remote/
    llm/
      LLMProvider.kt   // 接口
      GeminiProvider.kt
      PromptTemplates.kt
  file/
    ScanFileStore.kt
    PdfExporter.kt
  repository/
    ChatRepository.kt
    KnowledgeRepository.kt
    TimelineRepository.kt
    ScanRepository.kt
    ScheduleRepository.kt

core/
  di/                  // Hilt Modules
  util/                // extensions, DateTime utils
  common/              // Result 封装、错误类型
  workers/             // WorkManager Workers
```

### 2.3 分层契约

```
Compose Screen
  │  通过 hiltViewModel() 获得 ViewModel
  │  用 collectAsStateWithLifecycle 订阅 UiState
  ↓
ViewModel
  │  持有 MutableStateFlow<UiState>
  │  暴露 onEvent(UiEvent) 接收交互
  │  调用 UseCase（构造注入）
  ↓
UseCase（Domain，纯 Kotlin）
  │  operator fun invoke(params): Flow<T> 或 suspend fun invoke()
  │  调用 Repository
  ↓
Repository（接口在 domain，实现在 data）
  │  组合 Local + Remote + File
  ↓
数据源
  │  Room DAO / DataStore / LLMProvider / 文件系统
```

**关键原则**：
- UI 层不得直接引用 Repository 或 LLMProvider
- Domain 层不得有 Android 依赖（不 import `androidx.*`、`android.*`）
- Repository 是唯一能跨多个数据源的层
- 所有耗时操作走 `suspend fun` 或 `Flow`

### 2.4 LLM 抽象

```kotlin
interface LLMProvider {
    val name: String

    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk>

    suspend fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String? = null,
    ): Flow<LlmChunk>

    suspend fun generateStructured(
        prompt: String,
        schema: String,  // JSON schema as string
    ): String  // 返回完整 JSON
}

sealed interface LlmChunk {
    data class Text(val delta: String) : LlmChunk
    data class Done(val totalTokens: Int?) : LlmChunk
    data class Error(val message: String, val retryable: Boolean) : LlmChunk
}
```

MVP 阶段只实现 `GeminiProvider`。后续要加 OpenAI / Claude 只实现新类即可，上层无需改动。

---

## 3. 导航结构

### 3.1 底部导航（4 Tab）

启动默认 Tab：**Chat**

| Icon | Tab | 路由 |
|---|---|---|
| 💬 | Chat | `chat/list` |
| 📷 | Scan | `scanner/library` |
| 📚 | Knowledge | `knowledge/home` |
| 📅 | Timeline | `timeline/today` |

**Settings** 不占 Tab，通过每个 Screen 顶栏齿轮图标进入 `settings`。

### 3.2 各 Tab 屏幕栈

**Chat 栈**：
- `ChatListScreen` - 历史会话列表（默认页）
- `ChatDetailScreen` - 对话界面
- `ImageCropOverlay` - 模态

**Scanner 栈**：
- `ScanLibraryScreen` - 文档网格（默认页）
- `CameraCaptureScreen` - 相机取景
- `ScanEditScreen` - 边缘调整 + 滤镜
- `PdfExportScreen` - 多页排序 + 导出

**Knowledge 栈**：
- `KBHomeScreen` - 搜索 + 分类 + 最近（默认页）
- `KBEntryDetailScreen` - 条目详情
- `KBGraphScreen` - 知识图谱（P6 才启用；P3 前按钮隐藏）

**Timeline 栈**：
- `TimelineScreen` - 当日气泡视图（默认页）
- `TaskDetailScreen`
- `AddTaskScreen`
- `ImportHomeScreen` → `WebViewLoginScreen` / `IcsImportScreen` / `AddCourseScreen` → `ImportResultScreen`

**Settings**（全局）：
- `SettingsScreen` - API Key、模型、作息时间表、通知权限

### 3.3 跨 Tab 流程（教科书题目问答 → 加入知识库）

```
ScanLibrary → 选一张已扫的书页
  ↓
ImageCropOverlay → 框选某一题
  ↓
Chat/Detail（savedStateHandle 带 scanId + cropRect）
  → 自动附图 → AI 流式解答
  ↓
"加入知识库" 按钮（AI 完成流式后出现）
  → 二次 LLM 调用生成结构化摘要
  ↓
分类选择模态 → 保存到 kb_entries
  ↓
跳转到 KBEntryDetail
```

---

## 4. Feature 详细设计

### 4.1 Chat Feature（P1 · MVP）

**屏幕**：`ChatListScreen`、`ChatDetailScreen`、`ImageCropOverlay`

**数据模型**：

```kotlin
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,              // AI 生成的会话标题，可编辑
    val iconHint: String?,          // 自动分配的图标："📘" / "💬" / "📐"
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: Role,                 // USER / AI / SYSTEM
    val contentMarkdown: String,
    val attachedImagePath: String?, // 可选，指向 filesDir 中的裁剪图
    val sourceScanPageId: Long?,    // 如果来自扫描库，记录原页 id
    val createdAt: Long,
    val isStreaming: Boolean = false,
)
```

**流式实现**：
- `ChatDetailViewModel` 持有 `MutableStateFlow<ChatUiState>`
- UiState 包含：`messages: List<Message>`、`streamingMessage: Message?`、`inputText: String`
- 发送时：调用 `SendMessageUseCase(sessionId, text, imageBytes)`
- UseCase 返回 `Flow<LlmChunk>`，VM 订阅：
  - `Text(delta)` → append 到 `streamingMessage.content`
  - `Done` → 把 streamingMessage push 到 messages，清空 streamingMessage，写 Room
  - `Error` → 显示错误 banner，提供重试
- UI 侧用 `AnimatedContent` 做轻微淡入

**附件 Bottom Sheet**：
- "拍一张" → 启动 `CameraCaptureScreen`（复用 Scanner 的相机逻辑）→ 增强后直接进 Crop
- "从扫描库选" → 启动 `ScanLibraryScreen`（选择模式）→ 选某页 → 进 Crop
- "从相册" → Android PhotoPicker → 进 Crop

**ImageCropOverlay**：
- 全屏模态（DialogFragment 风格，但 Compose 实现）
- 核心：`Box` + `drawWithContent` 画 dim 蒙版 + crop box + 4 角拖拽 handle
- 支持双指缩放、单指平移 crop box
- 确认后返回 `CroppedImage(bitmap, sourceRect)` 给上游

**Markdown + KaTeX 渲染**：
- `MarkdownKatexView(markdown: String)` Composable
- 内部：`AndroidView` 包一个 `WebView`
- 加载本地 `assets://katex/page.html`（包含 marked.js + KaTeX auto-render）
- 内容通过 `evaluateJavascript("setContent('...')")` 注入
- 高度通过 JS → JavaScript Bridge 回传
- 背景透明，主题色通过 CSS 变量与 App 主题绑定

**Prompt**（主对话，MVP）：

```
你是一位学习助手。回答用户问题时：
1. 若用户附了图片，优先基于图片内容作答
2. 数学公式用 LaTeX 包裹：行内 $...$，块级 $$...$$
3. 中文为主，准确、简洁、可追问
4. 若问题超出图片/文字范围，主动询问补充信息
```

**MVP 范围**：
- ✅ 会话 CRUD、流式回复、多模态（文 + 图）、KaTeX 公式
- ❌ token 截断策略（P1+）、语音输入、会话搜索、会话分享
- ❌ "加入知识库"按钮（属 P3）

### 4.2 Scanner Feature（P2）

**屏幕**：`ScanLibraryScreen`、`CameraCaptureScreen`、`ScanEditScreen`、`PdfExportScreen`

**数据模型**：

```kotlin
@Entity(tableName = "scan_documents")
data class ScanDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
)

@Entity(
    tableName = "scan_pages",
    foreignKeys = [ForeignKey(
        entity = ScanDocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["docId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ScanPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val order: Int,
    val originalPath: String,   // filesDir/scans/<docId>/<id>_orig.jpg
    val enhancedPath: String,   // filesDir/scans/<docId>/<id>_enh.jpg
    val filter: Filter,         // ORIGINAL | ENHANCED | BW | GRAYSCALE
    val quadJson: String,       // 4 角点 JSON，用于"重新编辑"
)
```

**图像处理流水线**：

| 步骤 | 技术 | 备注 |
|---|---|---|
| 取景 | CameraX Preview + ImageAnalysis | 帧率限制 ~5 fps 给边缘检测 |
| 实时边缘检测 | OpenCV：灰度 + 高斯模糊 + Canny + findContours | 取最大四边形 |
| 拍照 | CameraX ImageCapture | 保存原图到 `filesDir/scans/<docId>/<id>_orig.jpg` |
| 透视校正 | OpenCV `getPerspectiveTransform` + `warpPerspective` | 输出正面矩形图 |
| 对比增强 | OpenCV CLAHE（`createCLAHE(clipLimit=2.5, tileGridSize=(8,8))`）+ 自适应二值化 | 产出"增强"版 |
| PDF 生成 | Android `PdfDocument`，每页 A4 | 用 `MediaStore.Downloads` 写公共目录 |

**选 OpenCV 理由**：代码好生成（AI 训练样例多）、纯本地、参数可调、APK 增 ~15MB 可接受。

**范围**：
- ✅ 边缘检测 + 透视 + 增强 + 多页 + 排序 + PDF 导出
- ❌ OCR（留 P6）、云同步、批注、二维码

### 4.3 Knowledge Base Feature（P3）

**屏幕**：`KBHomeScreen`、`KBEntryDetailScreen`、`KBGraphScreen`（P3 内隐藏）

**数据模型**：

```kotlin
@Entity(tableName = "kb_categories")
data class KbCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val name: String,
    val colorArgb: Int?,
)

@Entity(tableName = "kb_entries")
data class KbEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long?,
    val sourceType: KbSource,      // CHAT_MESSAGE | CHAT_SESSION | SCAN
    val sourceId: Long,
    val originalText: String?,
    val originalImagePath: String?,
    val summaryMarkdown: String,   // AI 生成的 5 节结构化内容
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long?,     // 复习功能预留
)

@Entity(
    tableName = "kb_relations",
    primaryKeys = ["fromEntryId", "toEntryId"]
)
data class KbRelationEntity(
    val fromEntryId: Long,
    val toEntryId: Long,
    val weight: Float = 1f,        // LLM 推荐的关联强度
)

// FTS4 虚拟表，索引 title + summaryMarkdown
@Fts4(contentEntity = KbEntryEntity::class)
@Entity(tableName = "kb_entries_fts")
data class KbEntryFts(
    val title: String,
    val summaryMarkdown: String,
)
```

**"加入知识库" 二次 LLM Prompt**：

```
以下是一段学习对话。请生成标准化的知识卡片，返回严格的 JSON：

{
  "title": "简洁标题（10 字内）",
  "categorySuggestion": "从列表中选一个最合适的分类名；若都不合适，提议新建",
  "summaryMarkdown": "Markdown 格式，必须按以下 5 节组织：\n## 核心概念\n...\n## 推导过程\n...\n## 关键公式\n...\n## 易错点\n...\n## 应用场景\n...\n\n公式用 $...$ 或 $$...$$ 包裹。",
  "relatedEntryTitles": ["已有条目标题1", "已有条目标题2"]
}

已有分类：{existingCategories}
已有条目标题：{existingEntryTitles}

对话内容：
{conversation}

只输出 JSON，不要额外解释。
```

处理：
- 用 `generateStructured` 调用，期望返回 JSON
- 应用侧解析；若解析失败，重试一次；仍失败则将原始字符串作为 `summaryMarkdown` 直接保存，`title` 用会话标题
- 用户可在预览模态里编辑任一字段后确认保存
- 保存时根据 `relatedEntryTitles` 查找已有 entry 写入 `kb_relations`

**搜索**：
- MVP：Room FTS4，`MATCH` 查询 `title + summaryMarkdown`
- 中文分词：按字符 bigram 切分作为 tokenizer 输入（简单但够用）
- 后期（P6）：可升级为向量检索

**知识图谱方案**（P6 stretch）：

用户已选 **C 方案：真·力导向图**。实现要点：
- `KBGraphScreen` 用 Compose Canvas + 自定义 `ForceSimulation` 类
- 节点数据：所有 `kb_entries`；边数据：`kb_relations` + 同分类隐式连接
- Force-directed 算法：Fruchterman-Reingold 简化版，每帧 `simulate(deltaTime)`
- 支持双指缩放、拖动平移、点击节点跳 Detail
- 性能预算：< 500 节点时稳 60 fps，超过则按分类聚合

**P3 主线范围**：
- ✅ 数据模型、Room FTS、加入 KB 流、KBHome、KBEntryDetail、"相关条目"列表跳转
- ❌ `KBGraphScreen`（P6 才做）、向量检索、复习排程

### 4.4 Timeline Feature（P4）

**屏幕**：`TimelineScreen`、`TaskDetailScreen`、`AddTaskScreen`

**统一数据模型**（课/任务/自定义共用一张表）：

```kotlin
@Entity(tableName = "timeline_items")
data class TimelineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TimelineType,         // COURSE | TASK | CUSTOM
    val title: String,
    val description: String?,

    val startAt: Long,              // 时间戳（ms）
    val endAt: Long?,               // COURSE/CUSTOM 有值；TASK == startAt（即 DDL）

    val isDone: Boolean = false,    // COURSE 恒 false
    val doneAt: Long?,

    val repeatRule: String?,        // RRULE (RFC 5545)，如 FREQ=WEEKLY;BYDAY=MO

    val source: TimelineSource,     // MANUAL | IMPORTED_ICS | IMPORTED_PORTAL | FROM_CHAT
    val sourceId: String?,          // 教务系统原 id

    val remindersMinBefore: List<Int>,   // e.g. [1440, 120, 30]
    val kbEntryIds: List<Long> = emptyList(),

    val colorOverride: Int? = null,
    val userModified: Boolean = false,   // Import 重新同步时跳过
)
```

**TimelineScreen 设计要点**：
- 垂直时间轴 08:00~22:00（或按当日实际项自适应）
- 气泡 Y 坐标 = 起始时间线性映射
- 5 种状态色（课程蓝 / 任务黄 / 完成绿删除线 / 过期红 pulse / 自定义紫）
- "此刻"红色横线 + 右侧红点
- 气泡不做真正的上下浮动；仅"即将到期 < 2h"和"过期"做 scale 呼吸 pulse
- 入场时气泡从轴向右滑出，配合 stagger
- 状态变化用 `animateColorAsState` 平滑过渡
- 左右滑切日；顶栏"📅"按钮开月视图选择弹窗

**RRULE 展开**：
- `ExpandRecurrenceUseCase(item: TimelineItem, dateRange: ClosedRange<LocalDate>): List<TimelineItem>`
- 实现：使用 `biweekly` 库的 `Recurrence` / `RecurrenceRule` API
- 用同一个库同时服务 Timeline 的重复展开与 ICS 导入解析，避免引入双解析路径
- 写入时校验只使用支持子集：`FREQ=WEEKLY;BYDAY=*;COUNT=N` 或 `UNTIL=...`
- 需要 1 套单元测试覆盖：普通每周、指定周次、结束日期、DST 过渡场景

**通知**：
- 创建 timeline_item 时，按 `remindersMinBefore` 为每条排期一个 `OneTimeWorkRequest`
- unique work name = `"reminder_${itemId}_${minBefore}"`，便于更新/取消
- Worker 触发时：
  1. 查 Room，若 item 被删除 → skip
  2. 若 `isDone == true` → skip
  3. 发通知：标题 = title，正文 = "距 DDL 还剩 X"
  4. deep link Intent 指向 `personalstudio://task/<id>`
- 额外的"已过期"通知：DDL 到时再检查一次，未完成则再发
- 用户标完成时：取消所有相关 Worker
- Settings 里有"通知权限"引导（Android 13+ `POST_NOTIFICATIONS`）

**默认作息时间表**（seed data，首次启动写入 DataStore）：

```kotlin
object DefaultTimetable {
    val periods = listOf(
        Period( 1, "08:00", "08:45"),
        Period( 2, "08:50", "09:35"),
        Period( 3, "09:55", "10:40"),
        Period( 4, "10:45", "11:30"),
        Period( 5, "11:35", "12:20"),
        Period( 6, "13:20", "14:05"),
        Period( 7, "14:10", "14:55"),
        Period( 8, "15:15", "16:00"),
        Period( 9, "16:05", "16:50"),
        Period(10, "16:55", "17:40"),
        Period(11, "18:30", "19:15"),
        Period(12, "19:20", "20:05"),
        Period(13, "20:10", "20:55"),
    )
}
```

用户可在 `SettingsScreen > 作息时间表` 编辑任一节的起止时间。

### 4.5 Schedule Import Feature（P5）

**屏幕**：`ImportHomeScreen`、`WebViewLoginScreen`、`IcsImportScreen`、`AddCourseScreen`（week-grid）、`ImportResultScreen`

**三路径策略 + 执行顺序**：

1. **Plan C（Day 1-2，100% 可用的兜底）** — `AddCourseScreen`
   - UI：横向 7 列（周一~日）× 纵向 13 行（节次）的网格
   - 点格子 → 弹出课程输入（名称 + 地点 + 周次范围）
   - 一次填入整周的课，批量创建 `timeline_items`（type=COURSE + RRULE=FREQ=WEEKLY）
   - 这条路径必须先完成，作为所有 Import 的保底

2. **Plan B（Day 3，安全网）** — ICS 导入
   - UI：文件选择器 → 解析 → 预览 → 确认导入
   - 使用 `biweekly` 库解析 VEVENT
   - 映射：VEVENT.summary → title；DTSTART/DTEND → startAt/endAt；RRULE → repeatRule

3. **Plan A（Day 4-7，主攻）** — WebView 爬取
   - UI：`WebViewLoginScreen` 嵌入教务系统登录页
   - 登录态检测：监听 `onPageFinished` 中的 URL 变化 + `CookieManager` 读取 `JSESSIONID`
   - 数据抓取优先级：
     - **XHR 拦截**（首选）：`WebViewClient.shouldInterceptRequest` 抓 JSON API 响应
     - **DOM 抓取**（fallback）：`evaluateJavascript` 注入 JS 读取 table
   - 登录成功后关闭可见 WebView，切换到不可见 WebView 做静默抓取
   - Cookie 缓存 7 天；失效时自动跳回登录页
   - 重新同步时跳过 `userModified=true` 的 item

**技术细节 · Plan A 登录检测伪代码**：

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        if (url.contains("main") || hasJSessionId()) {
            onLoginSuccess(view)
        }
    }
    override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
        if (req.url.path?.endsWith("/course/list") == true) {
            // 捕获请求，让它继续执行，同时记下 URL
            pendingApis.add(req.url.toString())
        }
        return null
    }
}
```

**时间映射**：
- "周一 1-2 节" → 查作息表 → "周一 08:00-09:35"
- "第 1-16 周" → 结合 Settings 中的学期起始日期 → `RRULE=FREQ=WEEKLY;COUNT=16`
- 学期起始日期：首次导入时让用户确认

**风险提示**：
- Plan A 失败立即止损，保留 B+C
- Plan A 需要用户在 P5 前提供：教务系统 URL + 课表页登录后截图/HTML + 作业系统 URL
- 不在 App 里存用户账号密码

---

## 5. Settings

**必备项**：

- **API Key**
  - 默认：内置一个 API key（BuildConfig 字段）
  - 可覆盖：用户可在 Settings 中配置自己的 key，设置后优先使用
  - 用户明确表示不担心 key 泄露；该决策已确认
- **LLM 模型选择**：Gemini 1.5 Pro / 2.0 Flash / 自定义 endpoint（预留）
- **作息时间表编辑**：13 节起止时间
- **学期起始日期**：用于 RRULE 展开
- **通知权限**（引导到系统设置）
- **数据管理**：清空 Chat / 清空 KB / 导出 JSON（P6）
- **关于**：版本号、开源许可

---

## 6. 横切关注点

### 6.1 测试策略

| 层 | 覆盖目标 |
|---|---|
| Domain（UseCase） | 100% 单元测试，尤其 RRULE 展开、LLM JSON 解析、edge 检测参数边界 |
| Repository | 集成测试（Room 真库 + mock Remote） |
| ViewModel | 用 `kotlinx-coroutines-test` 断言 UiState 演化 |
| UI | 关键流程 1-2 个 Compose UI 测试（`ComposeTestRule`） |
| E2E | 每阶段 DoD 前手工跑一次，截图归档 |

### 6.2 验证检查点（AI 驱动开发关键）

每阶段结束时必须完成：
1. `./gradlew build` 绿
2. `./gradlew test` 全过
3. 装到真机 → 手跑 DoD 里的核心流程 → 截图
4. 截图保存到 `docs/superpowers/checkpoints/P<N>/`，在 PR 描述里引用

失败或行为偏离时，**停下来定位原因，不要继续堆代码**。

### 6.3 依赖管理

- 所有版本集中在 `gradle/libs.versions.toml`
- 国内 Maven 镜像（阿里云）优先
- 新依赖加入前在 PR 描述里说明：用途、APK 体积影响、维护状态

### 6.4 Git 分支策略

- `main` 始终可构建、DoD 达标
- 每阶段一条 `feature/p<N>-<name>` 分支
- 阶段完成 → PR 到 `main`，review 后合并
- DoD 检查点的截图作为 PR 附件

### 6.5 错误处理策略

- 用户错误（无网、LLM 限流、Key 无效）→ 非阻塞 banner + 重试按钮
- 数据错误（Room 损坏、文件丢失）→ 阻塞弹窗 + "清空数据重置"兜底
- 崩溃：`Thread.setDefaultUncaughtExceptionHandler` 写本地日志到 `filesDir/crash-logs/`，Settings 提供"导出日志"便于 debug（不引入 Firebase，避免外部依赖）
- LLM JSON 解析失败：自动重试 1 次 + fallback 保存原始文本

---

## 7. 阶段执行计划（P0→P6）

| 阶段 | 时长 | 目标 | DoD |
|---|---|---|---|
| **P0 地基** | ~1 周 | 脚手架、DI、LLM 抽象、导航骨架、Settings 壳 | App 启动，4 Tab 可切，填 key 后调通 Gemini 返回一行 |
| **P1 Chat MVP** | ~2 周 | 会话 + 流式多模态 + KaTeX 渲染 + 裁剪 | 文字/图片提问正常，流式回复公式无误，历史持久化 |
| **P2 Scanner** | ~1 周 | CameraX + OpenCV + PDF 导出 + Chat 附件接通 | 拍 3 页 → 增强 → 导 PDF；从 Chat 附件能选到 |
| **P3 Knowledge** | ~2 周 | KB 数据 + 二次 LLM 摘要 + Home/Detail + 相关跳转 | 加入 KB → 5 节结构化 → 搜到 → 相关可跳 |
| **P4 Timeline** | ~1 周 | 气泡视图 + WorkManager 通知 + 默认作息表 | 手加一课一 DDL → 正确显示 + 前 2h 收推送 |
| **P5 Import** | ~1 周 | Plan C → B → A 递进 | 至少 C 可用；A/B 尽力而为 |
| **P6 打磨** | 剩余 | 力导向知识图谱 + 润色 | 图谱可用；其它打磨选做 |

**缓冲策略**：若某阶段滑 3-5 天，先砍 P6 打磨项；**不得压缩 P1-P5 的测试或验证环节**。

---

## 8. 风险登记（Risk Register）

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| Plan A 爬取失败 | 中 | 中 | P5 优先级 C→B→A，A 失败不影响主功能 |
| Gemini API 国内访问不稳 | 中 | 高 | 由用户自行解决网络；开发/演示前备网 |
| OpenCV APK 体积 +15MB | 确定 | 低 | 选 opencv-mobile，ABI 只保留 arm64/arm32 |
| LLM JSON 解析不稳定 | 中 | 中 | schema + 1 次重试 + fallback 纯文本 |
| WebView KaTeX 主题不一致 | 中 | 低 | 用 CSS 变量，在 theme 切换时 reload |
| 厂商 ROM 阻断 WorkManager | 中 | 中 | 引导用户开"自启动"；过期再补发一次通知 |
| 力导向图性能不达标 | 中 | 低 | P6 stretch，按节点数聚合 |
| AI 生成代码偏离 spec | 高 | 中 | 每阶段 DoD + 截图归档，发现偏离立即停 |

---

## 9. 命名与编码约定

- **Kotlin**：官方风格；4 空格缩进；文件名与主类名一致
- **Composable**：大驼峰，名词/名词短语（`ChatDetailScreen`、`MessageBubble`）
- **ViewModel**：`<Screen>ViewModel`
- **UiState**：`<Screen>UiState`，data class，放在 VM 同文件
- **UiEvent**：sealed interface，放在 VM 同文件
- **Room Entity**：后缀 `Entity`
- **Domain 模型**：无后缀（`ChatMessage`、`KbEntry`）
- **UseCase**：`<Verb><Object>UseCase`
- **资源命名**：`<feature>_<type>_<name>`（如 `chat_bg_bubble`）

---

## 10. 附录：关键文件清单（粗略）

项目完工后的主要代码文件估算：

- 47 个 Composable Screen/组件
- 14 个 ViewModel
- ~30 个 UseCase
- 6 个 Repository
- 1 个 AppDatabase + ~10 个 DAO
- 1 个 LLMProvider 接口 + 1 个 GeminiProvider
- ~15 个 Prompt 模板（按文件组织）
- ~30 个测试文件

**总代码量估算**：~15,000 – 23,000 行 Kotlin（含测试）

---

## 变更记录

- 2026-04-20：初稿，所有决策经 brainstorming 收敛后落盘
