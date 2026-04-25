# P3 Knowledge Base · Design Spec

**创建日期**：2026-04-25
**目标交付**：2026-05 上旬（~9 天工作量）
**前置依赖**：P0 / P1 / P2 已 shipped（tag `p2-scanner-mvp`，main 上 PR #4 已合并）
**作者**：项目所有者 + AI brainstorm
**前 spec 关系**：本 spec 是 `docs/superpowers/specs/2026-04-20-personal-studio-design.md` §4.3 的 P3 章节细化与执行版，沿用其大方向，补全 brainstorm 中确定的细节。

---

## 0. 总览

### 0.1 目标

让用户把 chat 对话和 scanner 扫描页一键沉淀为结构化知识卡片（KB 条目）。AI 负责：
- 从源内容里抽出 5 节知识点摘要（核心概念 / 推导过程 / 关键公式 / 易错点 / 应用场景）
- 如果是题目（拍题搜题、photo question、扫描页含题目），额外生成"规范化后的题目文字"（错题集场景）
- 给出分类建议 + 相关已有条目建议

用户在保存预览模态里可改任一字段后落库。落库后的条目支持：浏览、按分类筛、中文 bigram 全文搜索、错题集独立二级入口、改名、改分类、改 markdown、删除、重新调 LLM 生成。

### 0.2 范围（IN）

- 数据层：4 张 Room 表 + FTS4 影子表（v4 → v5，destructive）
- LLM 二次调用：扩展 `LLMProvider.generateStructured` 支持 messages + images，single call 返回完整 JSON
- 3 个 source 入口：CHAT_MESSAGE（per-turn）、CHAT_SESSION（整段会话）、SCAN（单张扫描页）
- 3 个屏幕：`KbHomeScreen`、`KbMistakesScreen`、`KbEntryDetailScreen` + 1 个 `SavePreviewModal`
- 完整编辑套件：rename / recategorize / delete / edit markdown / regenerate
- 默认学科分类预填 + AI 提议新分类需用户确认
- 中文 bigram FTS4 全文搜索
- LLM 给出的 `relatedEntryTitles` 自动 exact-title 匹配，写 `kb_relations`

### 0.3 范围（OUT，留给 P3.5 / P6）

- 知识图谱 (`KBGraphScreen` 力导向图) — 留 P6
- 向量检索 / Embedding — 留 P6
- 复习排程（`lastReviewedAt` 字段预留但不接业务）— 留 P6
- 手动编辑 `kb_relations`（只能由 LLM 自动写）— 留 P3.5
- 多张图同时归档（一次只送 1 张图给 LLM）— 留 P3.5
- 知识库导出 / 备份 — 留 P6

---

## 1. 数据模型

### 1.1 Room 升级

`AppDatabase.VERSION` 从 `4` 升到 `5`，**destructive**（开发期数据可丢，参考 memory `feedback_dev_db_data.md`）。`AppDatabase` 注册 4 个新 entity + 3 个新 DAO + 1 个 onCreate seed callback。

### 1.2 实体定义

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.*

// ---------- 分类 ----------

@Entity(
    tableName = "kb_categories",
    indices = [Index("name", unique = true)],
)
data class KbCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,         // P3 MVP 全 null；schema 预留层级
    val name: String,
    val seeded: Boolean = false,        // 区分内置 vs 用户/AI 创建（删除策略不同）
    val createdAt: Long,
)

// ---------- 来源类型 ----------

enum class KbSourceType { CHAT_MESSAGE, CHAT_SESSION, SCAN }

// ---------- KB 条目主表 ----------

@Entity(
    tableName = "kb_entries",
    indices = [
        Index("createdAt"),
        Index("categoryId"),
        Index("sourceType"),
    ],
)
data class KbEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                       // AI 生成 + 用户可改
    val categoryId: Long?,                   // null 在数据上允许；UI 兜底归"其它"
    val sourceType: KbSourceType,
    val sourceChatMessageId: Long?,          // CHAT_MESSAGE 时填
    val sourceChatSessionId: Long?,          // CHAT_MESSAGE / CHAT_SESSION 时填（便于回跳）；SCAN 为 null
    val sourceScanPageId: Long?,             // SCAN 时填
    val originalImagePath: String?,          // 复制到 filesDir/kb/<entryId>.jpg；CHAT_SESSION 永远 null
    val standardizedQuestion: String?,       // 非 null = 进错题集（discriminator）
    val summaryMarkdown: String,             // 5 节 markdown，KaTeX 渲染
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long? = null,        // 复习功能预留（P6）
)

// ---------- 条目关联 ----------

@Entity(
    tableName = "kb_relations",
    primaryKeys = ["fromEntryId", "toEntryId"],
    foreignKeys = [
        ForeignKey(KbEntryEntity::class, ["id"], ["fromEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(KbEntryEntity::class, ["id"], ["toEntryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("toEntryId")],
)
data class KbRelationEntity(
    val fromEntryId: Long,
    val toEntryId: Long,
    val weight: Float = 1f,
)

// ---------- FTS4 影子表（bigram 预切后写入） ----------

@Fts4(tokenizer = "simple")
@Entity(tableName = "kb_entries_fts")
data class KbEntryFtsEntity(
    @ColumnInfo(name = "rowid") val rowid: Long,    // 与 kb_entries.id 对齐
    val titleBigrams: String,
    val summaryBigrams: String,
    val standardizedQuestionBigrams: String,
)
```

**关键设计点**：

- **错题集判定**：`standardizedQuestion != null` 是唯一 discriminator。CHAT_MESSAGE 有图 / SCAN 两种 source 时，AI 在 prompt 里被要求判断"是不是题目"，是题目就填 `standardizedQuestion`；CHAT_SESSION 永远不填（session 是主题摘要不是题目）。
- **图片解耦**：`originalImagePath` 把源图复制到 `filesDir/kb/<entryId>.jpg`。原 chat message 或 scan page 删掉不会让 KB 条目变残。
- **FTS 文本预切**：用 `tokenizer = "simple"` + 应用层 bigram 预处理，避开 SQLite 打包 ICU/jieba 的麻烦。详见 §5。
- **`categoryId` 可空**：数据上允许撑住"用户暂未分类"。UI 把 null 归到 `其它` chip 下。
- **seeded 分类不可删**：UI 在删除按钮上判断 `seeded == true` 时 disable（DAO 层 SQL 也 guard `WHERE seeded = 0`）。

### 1.3 默认分类 seed

`AppDatabase.Builder.addCallback` 的 `onCreate` 时机插入 7 条：

```
数学 / 物理 / 化学 / 生物 / 英语 / 编程 / 其它   (seeded = true, parentId = null)
```

Migration v4 → v5 destructive，所以 `onCreate` 在升级时会触发，时机正常。`其它` 同时作为：
- LLM 解析 fallback 时的 default category
- `categoryId IS NULL` 时 UI 兜底归类
- 用户/AI 自建分类被删后的迁移目标

### 1.4 DAO 层

```kotlin
package com.example.personal_studio.data.local.db.dao

import androidx.room.*
import com.example.personal_studio.data.local.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KbCategoryDao {
    @Query("SELECT * FROM kb_categories ORDER BY seeded DESC, name")
    fun observeAll(): Flow<List<KbCategoryEntity>>

    @Query("SELECT * FROM kb_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): KbCategoryEntity?

    @Insert
    suspend fun insert(c: KbCategoryEntity): Long

    @Query("DELETE FROM kb_categories WHERE id = :id AND seeded = 0")
    suspend fun delete(id: Long): Int
}

@Dao
interface KbEntryDao {
    @Insert
    suspend fun insert(e: KbEntryEntity): Long

    @Update
    suspend fun update(e: KbEntryEntity)

    @Query("DELETE FROM kb_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    fun observe(id: Long): Flow<KbEntryEntity?>

    @Query("""SELECT * FROM kb_entries
              WHERE (:categoryId IS NULL OR categoryId = :categoryId)
              ORDER BY createdAt DESC""")
    fun observeAll(categoryId: Long?): Flow<List<KbEntryEntity>>

    @Query("""SELECT * FROM kb_entries
              WHERE standardizedQuestion IS NOT NULL
              ORDER BY createdAt DESC""")
    fun observeMistakes(): Flow<List<KbEntryEntity>>

    @Query("""SELECT e.* FROM kb_entries e
              JOIN kb_entries_fts f ON f.rowid = e.id
              WHERE kb_entries_fts MATCH :ftsQuery
              ORDER BY e.createdAt DESC""")
    fun search(ftsQuery: String): Flow<List<KbEntryEntity>>

    @Query("""SELECT e.* FROM kb_entries e
              JOIN kb_relations r ON r.toEntryId = e.id
              WHERE r.fromEntryId = :id ORDER BY r.weight DESC""")
    fun observeRelated(id: Long): Flow<List<KbEntryEntity>>

    @Query("SELECT * FROM kb_entries WHERE title IN (:titles)")
    suspend fun findByTitles(titles: List<String>): List<KbEntryEntity>

    @Query("SELECT title FROM kb_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentTitles(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NULL")
    fun countNotes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NOT NULL")
    fun countMistakes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: Long): Flow<Int>

    // 关联表 ops（regenerate 不重建关联，所以不需要 clearRelationsFrom）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(rels: List<KbRelationEntity>)
}

@Dao
interface KbFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fts: KbEntryFtsEntity)

    @Query("DELETE FROM kb_entries_fts WHERE rowid = :id")
    suspend fun delete(id: Long)
}
```

### 1.5 Repository 契约

`domain/knowledge/KnowledgeRepository.kt`（接口）+ `data/repository/KnowledgeRepositoryImpl.kt`（实现）：

```kotlin
package com.example.personal_studio.domain.knowledge

import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    // 读
    fun observeAllEntries(categoryId: Long?): Flow<List<KbEntry>>
    fun observeMistakes(): Flow<List<KbEntry>>
    fun observeEntry(id: Long): Flow<KbEntry?>
    fun observeRelated(id: Long): Flow<List<KbEntry>>
    fun observeCategories(): Flow<List<KbCategory>>
    fun observeNotesCount(): Flow<Int>
    fun observeMistakesCount(): Flow<Int>
    fun observeCategoryCount(categoryId: Long): Flow<Int>
    fun search(query: String): Flow<List<KbEntry>>

    // 写
    /** 落库 + FTS + relations 一次性事务。返回新 entryId。 */
    suspend fun saveEntry(draft: KbEntryDraft): Long

    /** 全字段更新；FTS 同步重写。relations 不动。 */
    suspend fun updateEntry(entry: KbEntry)

    /** 删除条目；CASCADE 清 fts + relations + 关联的 originalImagePath 文件。 */
    suspend fun deleteEntry(id: Long)

    /** 找名字找不到就建；返回 categoryId。 */
    suspend fun upsertCategory(name: String): Long

    // LLM 桥
    /** 调 LLM + 解析 + 重试 + fallback。返回 KbEntryDraft（永不抛出 JSON 错误，最坏给 fallback）。 */
    suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft
}
```

`KbEntry` / `KbCategory` / `KbEntryDraft` / `KbDraftSource` 在 `domain/knowledge/` 下，纯 Kotlin 数据类（不依赖 androidx）。

---

## 2. LLM 契约

### 2.1 接口扩展

现有 `LLMProvider.generateStructured(prompt: String, jsonSchema: String): String` 不接图，扩展为接 messages + images：

```kotlin
// data/remote/llm/LLMProvider.kt
interface LLMProvider {
    val name: String

    fun generate(messages: List<LlmMessage>, temperature: Float = 0.7f): Flow<LlmChunk>

    /** 现版本：单 USER 文本 + schema。保留作 backward compat default。 */
    suspend fun generateStructured(prompt: String, jsonSchema: String): String =
        generateStructured(
            messages = listOf(LlmMessage(LlmRole.USER, prompt)),
            jsonSchema = jsonSchema,
        )

    /** 新主签名：支持多消息 + 图片。OpenAI 兼容 provider 走 vision + JSON mode。 */
    suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
    ): String

    // generateText / generateMultimodal 保持不变
}
```

`OpenAiCompatibleProvider.generateStructured(messages, schema)`：
- 走 `/chat/completions` 同 endpoint
- 请求 body 加 `"response_format": {"type": "json_object"}`
- messages 里有 image 的 USER 消息组装成 vision content parts (`{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}` + `{"type":"text","text":...}`)
- 非流式（一次拿完整 JSON 字符串）
- 默认 temperature = 0.3（结构化输出降随机）

### 2.2 JSON Schema（对所有 3 种 source 通用）

```json
{
  "title": "string，≤15 字，能 1 秒看懂这条 KB 关于什么",
  "categorySuggestion": "string，下面分类列表里的某一个，或全新分类名",
  "isQuestion": "boolean，是否为可独立成立的题目",
  "standardizedQuestion": "string|null，isQuestion=true 时必填；规范化清晰可独立阅读的题面，LaTeX 包公式；isQuestion=false 填 null",
  "summaryMarkdown": "string，5 节 Markdown：## 核心概念\\n…\\n## 推导过程\\n…\\n## 关键公式\\n…\\n## 易错点\\n…\\n## 应用场景\\n…",
  "relatedEntryTitles": "array of string，从给定的已有标题列表精确挑出最多 3 个；若无则空数组"
}
```

### 2.3 Prompt 模板

**SystemPrompt**（固定，注入为 `LlmMessage(SYSTEM, …)`）：

```
你是学习知识库的归档助手。任务：把给定的对话或扫描页浓缩为一张知识卡片。
只输出严格的 JSON（不带 markdown 代码围栏、不带任何解释文字）。
所有字段必填；不知道就给空字符串、null 或空数组（按 schema 类型）。
公式用 $...$（行内）或 $$...$$（块级）。
中文为主。
```

**UserPrompt** 拼装顺序：

```
[已有分类]
数学, 物理, 化学, 生物, 英语, 编程, 其它   (+ 用户/AI 已建的分类，逗号分隔)

[已有条目标题（按 createdAt DESC 取最近 50 条）]
- 二次函数判别式
- 牛顿第二定律
- ...

[输出 schema]
<2.2 节那段说明>

[内容]
<下面三种 source 各自的内容块>
```

### 2.4 三种 source 的内容拼装

| Source | LlmMessage 序列 |
|---|---|
| **CHAT_MESSAGE** | 1 条 USER：`### 用户问题\n{userMsg.contentMarkdown}\n\n### AI 回答\n{aiMsg.contentMarkdown}`，images = `userMsg.attachedImagePath` 转 ByteArray（如有，否则空 list） |
| **CHAT_SESSION** | 多条 LlmMessage 还原 session 的 user/assistant 对话序列；最后追加 1 条 USER：`请把以上对话归档为一张知识卡片`。token 超限时**从最早消息开始丢弃**保留最后 N 条（estimator: codepoints / 1.5） |
| **SCAN** | 1 条 USER：`请识别图中内容并归档为一张知识卡片。如果是题目，按题目处理（isQuestion=true）。文档标题：{ScanDocument.title}`，images = `ScanPage.enhancedImagePath` 的 JPEG bytes |

所有 source 之外都加上 SystemPrompt + Categories context 作为最前两条 LlmMessage。

### 2.5 错误处理

```
KnowledgeRepository.draftFromSource(source)
  ↓
拼装 messages + schema → LLMProvider.generateStructured(messages, schema)
  ↓
拿到 String
  ├─ JSON 解析成功 + 必填字段齐全 → 返回 KbEntryDraft（success）
  │
  ├─ 解析失败 / 字段缺失
  │    └─ 重试 1 次（同 messages，temperature 降到 0.1）
  │         ├─ 成功 → 返回 KbEntryDraft（success）
  │         └─ 仍失败 → 返回 KbEntryDraft.fallback：
  │               title = sourceFallbackTitle()  // chat session.title / scan doc.title / "未命名条目"
  │               categorySuggestion = "其它"
  │               isQuestion = false / standardizedQuestion = null
  │               summaryMarkdown = "## 原始内容\n\n" + rawLlmText
  │               relatedEntryTitles = []
  │               isFallback = true   // ViewModel 用此 flag 显示黄色 banner
  │
  └─ 网络/Provider 抛 IOException → 上抛给 ViewModel；UI 显示错误 banner + [重试]，不进 modal
```

---

## 3. 工作流

### 3.1 Add-to-KB（3 个入口，1 个 ViewModel）

3 个触发点构造 `KbDraftSource`，统一交给 `SaveToKnowledgeViewModel`：

```kotlin
sealed class KbDraftSource {
    data class FromChatMessage(val sessionId: Long, val aiMessageId: Long) : KbDraftSource()
    data class FromChatSession(val sessionId: Long) : KbDraftSource()
    data class FromScanPage(val docId: Long, val pageId: Long) : KbDraftSource()
}
```

**触发流程**：

```
trigger UI
  ↓
SaveToKnowledgeViewModel.startDraft(source)
  state = Loading (spinner)
  ↓
SaveToKnowledgeUseCase.invoke(source) → KbEntryDraft
  ├─ 1. 读取来源原文 + 图片
  ├─ 2. 拼装 LlmMessages + schema
  ├─ 3. KnowledgeRepository.draftFromSource(source) → JSON / fallback → KbEntryDraft
  └─ 4. 把 source 信息附在 draft 上
  ↓
state = Preview(draft)
  ↓
SavePreviewModal 渲染 draft（fallback 时顶部黄 banner），所有字段可改
  ↓
[保存] → SaveToKnowledgeUseCase.commit(draft)
  ├─ 1. 复制 originalImage → filesDir/kb/<未来entryId>.jpg
  │     - 由于 entryId 要 insert 后才知道，做法：先写一个临时 File(filesDir/kb/tmp_<uuid>.jpg)，
  │       insert 拿到 entryId 后 rename 为 <entryId>.jpg，然后 update entry.originalImagePath
  ├─ 2. upsertCategory(draft.category) → categoryId
  ├─ 3. insert KbEntryEntity → entryId
  ├─ 4. KbEntryDao.findByTitles(draft.relatedTitles) → 命中的写 kb_relations
  ├─ 5. bigram-tokenize 三个文本字段，写 kb_entries_fts
  └─ 6. 关闭 modal + navigate(NavRoutes.kbDetail(entryId))
```

**3 个入口的 UI 改动点**：

| 位置 | 触发 UI |
|---|---|
| `ChatDetailScreen` AI 气泡（streaming 完成后） | 气泡下方一行出现 `[+ archive]`（终端风文本按钮，phosphor 色） |
| `ChatDetailScreen` TerminalTopBar trailing | 现有齿轮旁加一个 `[+ archive session]` IconButton |
| `ScanDocumentDetailScreen` 页 thumbnail 长按菜单 | long-press 菜单加一项 `[archive page to KB]` |
| `PageEditScreen`（scanner 页编辑） | 顶部加一个 `[+ archive]` 按钮（与现有 [retake]/[delete] 同行） |

### 3.2 Edit ops（KbEntryDetail overflow 菜单）

- `[rename]` → `AlertDialog` 内嵌 `BasicTextField` → `repo.updateEntry(entry.copy(title=…))` → FTS 重写
- `[change category]` → `ModalBottomSheet` 显示分类列表 + 搜索 + `[+ 新建...]` → `repo.upsertCategory + updateEntry`
- `[delete]` → `AlertDialog` 二次确认 → `repo.deleteEntry()`（CASCADE 清 fts + relations + 删 originalImagePath 文件）
- `[regenerate]` → `AlertDialog` "覆盖摘要 + 标准化题目，保留标题/分类，确定？" → `RegenerateEntryUseCase`：
  - 重建原 source 的 `KbDraftSource`（从 entry 字段反推：sourceType + sourceChatMessageId/etc）
  - 调一遍 `KnowledgeRepository.draftFromSource`
  - 用新 draft 的 `summaryMarkdown` + `standardizedQuestion` 直接覆盖现有 entry（**不进 SavePreviewModal**，因为是显式重做）
  - `title` / `categoryId` 保留（避免覆盖用户已 rename / recategorize 的成果）
  - `relations` 不重建（避免抖动）
  - FTS 重写

`KbEntryDetailScreen` 摘要 section 右上角的 `[edit]` 切换按钮（不在 overflow 菜单里）：
- 切换 `MathMarkdownView` ↔ `BasicTextField` 编辑模式
- 编辑模式下顶部加一个 `[preview]` 切换按钮可看 KaTeX 渲染效果
- `[save]` 写回 + 重建 FTS

错题 entry 的"标准化题目"section 同理：右上角 `[edit]` 切换文字编辑模式。

---

## 4. UI 屏幕

所有屏幕沿用现有终端美学：`TerminalTopBar` + phosphor (#9CFEAF) + scan-lines + monospace。配色 / 控件参考 `docs/design/terminal/spec.md`。

### 4.1 `KbHomeScreen` — kb tab 默认页

```
┌─ TerminalTopBar (由 MainScreen 提供) ──────────────────────┐
│ user@study:~$ kb                          ⚙ 齿轮          │
└──────────────────────────────────────────────────────────┘
  $ grep -r "________" kb/                      ← 搜索 TextField

  ┌──────────────┬─────────────────┐
  │ [notes] 32   │ [mistakes] 8    │            ← 顶部双切片，两个数字
  └──────────────┴─────────────────┘            (notes=非错题数，mistakes=错题数)

  分类  [全部] [数学 12] [物理 6] [化学 3] ...    ← 横向滚动 chip

  ─────────── 最近 ───────────                  ← LazyColumn
  ▎二次函数判别式               · 数学 · 2h前
  ▎  原图👁 牛顿第二定律         · 物理 · 1d前
  ▎  Compose 状态恢复           · 编程 · 3d前
  ...
```

**交互**：
- 搜索框非空 → list 切换为 FTS 命中（同 row 渲染 + 关键词高亮，底部 status line 显示 `21 matches`）
- 分类 chip：tap 筛选；再 tap 取消（恢复 `[全部]`）
- `[notes]` chip：高亮 = 隐藏 standardizedQuestion 非空的；
- `[mistakes]` chip：tap 跳到 `KbMistakesScreen`
- Empty state：保留现有 `KnowledgePlaceholder` 的终端风（`grep -r . kb/` `no entries yet` `archive a chat reply to begin`）

**ViewModel state**：

```kotlin
data class KbHomeUiState(
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,    // null = "全部"
    val showNotes: Boolean = true,           // [notes] chip 状态
    val notesCount: Int = 0,
    val mistakesCount: Int = 0,
    val categories: List<CategoryWithCount> = emptyList(),
    val entries: List<KbEntry> = emptyList(),
)
```

### 4.2 `KbMistakesScreen`（二级路由）

```
┌─ TerminalTopBar ──────────────────────────────────────────┐
│ [< back]   user@study:~/kb/mistakes/                       │
└──────────────────────────────────────────────────────────┘
  $ ls mistakes/    8 entries

  ┌──────┐ 二次函数判别式      · 数学 · 2h前
  │ IMG  │ Δ = b² − 4ac，求 Δ 的取值...
  │      │
  └──────┘
  ┌──────┐ 单摆周期推导        · 物理 · 1d前
  │ IMG  │ 一个长 L 的单摆，求...
  └──────┘
  ...
```

**每行**：左侧 80×80 原图缩略图 + 右侧 title + standardizedQuestion 首行截断 + meta（分类 · 相对时间）。Tap → KBEntryDetailScreen。

### 4.3 `KbEntryDetailScreen`（两变体）

`standardizedQuestion == null` 时是普通条目；非空时是错题条目。

**普通条目**：

```
┌─ TerminalTopBar ──────────────────────────────────────────┐
│ [< back]  二次函数判别式               [edit] [⋮]           │
└──────────────────────────────────────────────────────────┘
  ▎数学  ·  CHAT_MESSAGE  ·  来自: session #003 [↗]

  ## 核心概念                          ← MathMarkdownView
  …
  ## 推导过程
  …
  ## 关键公式
  $$\Delta = b^2 - 4ac$$
  ## 易错点
  …
  ## 应用场景
  …

  ─────── 相关条目 ───────
  ▎一元二次方程求根公式  →
  ▎韦达定理              →
```

**错题条目**：

```
┌─ TerminalTopBar ──────────────────────────────────────────┐
│ [< back]  二次函数判别式               [edit] [⋮]           │
└──────────────────────────────────────────────────────────┘
  ▎数学  ·  错题  ·  来自: session #003 [↗]

  ┌─────────────────┐    ## 题目
  │   原图          │    （AI 规范化后的题面，KaTeX 渲染）
  │   (tap 全屏)    │    已知 $f(x) = ax^2 + bx + c$ 且 $a \neq 0$，
  │                 │    求判别式 $\Delta$ 的值，并判断...
  └─────────────────┘

  ## 核心概念
  …（同上 5 节摘要）

  ─────── 相关条目 ───────
  …
```

**交互**：
- `[edit]`（摘要 / 标准化题目 section 内嵌）：切换 KaTeX 预览 ↔ BasicTextField；保存写回 + 重建 FTS
- `[⋮]`：rename / change category / delete / regenerate
- 来源 `[↗]`：CHAT_MESSAGE / CHAT_SESSION 跳 `chatDetail(sessionId)`；SCAN 跳 `scannerDetail(docId)`
- 原图 tap 进全屏 viewer（复用现有 `ChatImageThumbnail` 的 fullscreen 模式）

### 4.4 `SavePreviewModal`

全屏 Dialog（`Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`），三态：Loading → Preview → Saving。

```
┌─ AppBar ──────────────────────────────────────────────────┐
│ [✕]   archive draft                              [save]   │
└──────────────────────────────────────────────────────────┘
  ⚠ AI 解析失败，已用原文兜底，请检查              ← fallback 时显示

  $ title         [二次函数判别式______________]
  $ category      [数学 ▼]   (AI 推荐: 数学)     ← 下拉，AI 建议高亮
                  └─ + 新建...

  $ standardized  [已知 f(x) = ax² + bx + c..._]  ← 错题时显示
                  TextField, multiline

  $ summary       (KaTeX 渲染只读预览)            ← 默认只读
                  [edit raw markdown]            ← toggle

  $ related       [二次函数 ✕] [一元二次方程 ✕]   ← AI 命中的，可叉
```

Loading 态：phosphor 色 spinner + `$ thinking...`
Saving 态：spinner + `$ writing entry...`
Error 态（LLM 网络错）：替换正文为 `! llm error: <msg>` + `[retry]` `[cancel]`

---

## 5. 搜索（bigram FTS4）

### 5.1 BigramTokenizer

`core/bigram/BigramTokenizer.kt`：

```kotlin
package com.example.personal_studio.core.bigram

object BigramTokenizer {
    private val SEP = Regex("[\\s\\p{Punct}]+")

    /** Index 时用：原文 → 空格分隔的 token 串，写入 FTS。 */
    fun tokenizeForIndex(text: String): String {
        if (text.isBlank()) return ""
        val out = StringBuilder()
        for (chunk in text.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                out.append(chunk.lowercase()).append(' ')
            } else {
                bigrams(chunk, out)
            }
        }
        return out.toString().trim()
    }

    /** Search 时用：构造 FTS MATCH query。同 chunk 内 bigram 用 AND，chunk 之间也用 AND。 */
    fun tokenizeForQuery(input: String): String {
        if (input.isBlank()) return ""
        val parts = mutableListOf<String>()
        for (chunk in input.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                parts.add("\"${chunk.lowercase()}\"")
            } else {
                val sb = StringBuilder()
                bigrams(chunk, sb)
                val bg = sb.toString().trim().split(' ').filter { it.isNotBlank() }
                if (bg.isNotEmpty()) parts.add(bg.joinToString(" AND ") { "\"$it\"" })
            }
        }
        return parts.joinToString(" AND ")
    }

    private fun bigrams(word: String, out: StringBuilder) {
        if (word.length == 1) { out.append(word).append(' '); return }
        for (i in 0 until word.length - 1) {
            out.append(word, i, i + 2).append(' ')
        }
    }
}
```

### 5.2 已知妥协

跨"词"边界的 bigram 是噪声（`微积分极限` → `分极` 不在任何文档中 → AND 查询 miss）。MVP 应对：
1. 鼓励用户加空格分词（`微积分 极限`）
2. 命中数 < 5 时 ViewModel 自动降级为 OR 查询重试一次（兜底召回）

未来（P3.5+）可换 jieba / SmartChineseAnalyzer / 直接 SQLite ICU 扩展。

### 5.3 写路径

`KnowledgeRepositoryImpl.saveEntry` / `updateEntry` 在事务里：
1. insert/update `KbEntryEntity`
2. `KbFtsDao.upsert(KbEntryFtsEntity(rowid = entryId, titleBigrams = …, summaryBigrams = …, standardizedQuestionBigrams = …))`

`deleteEntry` 显式 `KbFtsDao.delete(id)`（FTS4 不接 ForeignKey CASCADE，得手动）。

### 5.4 读路径

```kotlin
override fun search(q: String): Flow<List<KbEntry>> {
    val ftsQ = BigramTokenizer.tokenizeForQuery(q)
    if (ftsQ.isBlank()) return flowOf(emptyList())
    return entryDao.search(ftsQ).map { rows -> rows.map { it.toDomain() } }
}
```

ViewModel 层做 < 5 命中降级：

```kotlin
// 在 KbHomeViewModel
fun onSearchChanged(q: String) {
    viewModelScope.launch {
        val primary = repo.search(q).first()
        val results = if (primary.size < 5) repo.searchOr(q).first() else primary
        // ...
    }
}
```

`searchOr` 是 repo 上的辅助，把 AND 换成 OR 再走一次。

---

## 6. 文件清单 + 包结构

### 6.1 新文件

```
ui/navigation/NavRoutes.kt              UPDATE — 加 KB_HOME, KB_MISTAKES, KB_DETAIL 路由 + 构造函数
ui/AppNavHost.kt                        UPDATE — 注册 3 个新 destination
ui/MainScreen.kt                        (无变化，KNOWLEDGE tab 已在)

feature/knowledge/
  ui/
    KbHomeScreen.kt                     新
    KbMistakesScreen.kt                 新
    KbEntryDetailScreen.kt              新（含两变体逻辑）
    SavePreviewModal.kt                 新
    components/
      KbEntryRow.kt                     新（KbHome 列表行）
      KbMistakeRow.kt                   新（带原图缩略图的行）
      CategoryChipRow.kt                新（横向滚动 chip）
      RelatedEntriesSection.kt          新
      SummaryMarkdownEditor.kt          新（KaTeX 预览 ↔ TextField 切换）
      CategoryPickerSheet.kt            新（ModalBottomSheet 选/新建分类）
      SearchBar.kt                      新（终端风 grep -r 输入）
  vm/
    KbHomeViewModel.kt                  新
    KbMistakesViewModel.kt              新
    KbEntryDetailViewModel.kt           新
    SaveToKnowledgeViewModel.kt         新

domain/knowledge/
  KbEntry.kt                            新
  KbCategory.kt                         新
  KbEntryDraft.kt                       新（含 fallback flag）
  KbDraftSource.kt                      新（sealed class）
  KnowledgeRepository.kt                新（interface）
  SaveToKnowledgeUseCase.kt             新
  RegenerateEntryUseCase.kt             新
  UpdateEntryUseCase.kt                 新
  DeleteEntryUseCase.kt                 新
  ObserveKbHomeUseCase.kt               新
  ObserveMistakesUseCase.kt             新
  ObserveEntryDetailUseCase.kt          新
  SearchKbUseCase.kt                    新

data/
  local/db/
    AppDatabase.kt                      UPDATE — VERSION 5, 注册新 entity + DAO + onCreate seed callback
    Converters.kt                        UPDATE — KbSourceType ↔ String
    entity/
      KbCategoryEntity.kt               新
      KbEntryEntity.kt                  新
      KbRelationEntity.kt               新
      KbEntryFtsEntity.kt               新
    dao/
      KbCategoryDao.kt                  新
      KbEntryDao.kt                     新
      KbFtsDao.kt                       新
  repository/
    KnowledgeRepositoryImpl.kt          新
  remote/llm/
    LLMProvider.kt                      UPDATE — 加 generateStructured(messages, schema) 主签名
    OpenAiCompatibleProvider.kt         UPDATE — vision + JSON mode 的实现

core/
  bigram/BigramTokenizer.kt             新
  di/
    KnowledgeModule.kt                  新（bind KnowledgeRepository）

feature/chat/ui/
  ChatDetailScreen.kt                   UPDATE — AI 气泡下加 [+ archive]; topbar trailing 加 [+ archive session]

feature/scanner/...
  ScanDocumentDetailScreen.kt           UPDATE — 长按菜单加 [archive page to KB]
  PageEditScreen.kt                     UPDATE — 顶部加 [+ archive]

ui/placeholder/FeaturePlaceholders.kt   保留 `KnowledgePlaceholder` 用作 KbHome 的 empty state（不再被 NavHost 直接渲染）
```

### 6.2 路由

```kotlin
object NavRoutes {
    // 已有...
    const val KB_HOME     = "knowledge"           // tab 默认（已存在）
    const val KB_MISTAKES = "knowledge/mistakes"
    const val KB_DETAIL   = "knowledge/detail/{entryId}"
    fun kbDetail(entryId: Long) = "knowledge/detail/$entryId"
}
```

`SavePreviewModal` 不是导航 destination，而是 `Dialog` Composable，由各触发屏 host。

---

## 7. 测试策略

### 7.1 单元测试

| 文件 | 覆盖 |
|---|---|
| `core/bigram/BigramTokenizerTest.kt` | 中文 / 英文 / 混合 / 标点 / 单字 / 空串；index vs query 一致性 |
| `domain/knowledge/SaveToKnowledgeUseCaseTest.kt` | 三种 source 的 messages 拼装；JSON 解析成功；解析失败 → retry → fallback；isQuestion 字段处理 |
| `domain/knowledge/RegenerateEntryUseCaseTest.kt` | 重建 source；title / categoryId 保留；relations 不动 |
| `domain/knowledge/UpdateEntryUseCaseTest.kt` | FTS 同步 |
| `domain/knowledge/DeleteEntryUseCaseTest.kt` | CASCADE + 文件删除 |
| `data/repository/KnowledgeRepositoryImplTest.kt` | Room 真库；事务（insert + relations + FTS）；search 路径 |
| `feature/knowledge/vm/KbHomeViewModelTest.kt` | searchQuery 防抖 + < 5 降级；分类切换；count Flow |
| `feature/knowledge/vm/SaveToKnowledgeViewModelTest.kt` | Loading → Preview → Saved；fallback banner 状态；error 重试 |
| `feature/knowledge/vm/KbEntryDetailViewModelTest.kt` | 编辑 / regenerate / delete 状态机 |

### 7.2 Instrumented (Compose UI)

| 文件 | 覆盖 |
|---|---|
| `KbHomeScreenTest.kt` | 空态 / 列表 / 分类筛 / 搜索 |
| `KbEntryDetailScreenTest.kt` | 普通 vs 错题双变体；overflow 菜单；edit 切换 |
| `SavePreviewModalTest.kt` | fallback banner 显示；字段编辑回流 |
| `KbMistakesScreenTest.kt` | 只显示 standardizedQuestion 非空的 |

### 7.3 验证检查点（AI 驱动开发）

每个 phase 收尾：
1. `./gradlew assembleDebug` 绿
2. `./gradlew test` 全过
3. 装真机 → 手跑该 phase DoD
4. 截图存 `docs/superpowers/checkpoints/P3/phase<N>/`

不达 DoD 不进下一 phase。

---

## 8. Phase 切分

| Phase | 内容 | 估算 | DoD |
|---|---|---|---|
| **1 · 数据层** | Room v5 + 4 实体 + 3 DAO + Converter + KnowledgeRepositoryImpl 的 CRUD（无 LLM）+ BigramTokenizer + seed 默认分类 + Hilt 绑定 | ~1.5 天 | 单测全绿；启动后 DB inspector 见 7 条 seeded 分类；CRUD smoke OK |
| **2 · LLM 契约** | 扩展 `generateStructured(messages, schema)` + OpenAiCompatibleProvider 改写（vision + JSON mode）+ SaveToKnowledgeUseCase + JSON 解析/重试/fallback | ~1 天 | mock LLM 单测全绿；真 provider 手 ping 一条文本 source 拿到 JSON |
| **3 · Add-to-KB · chat per-message** | SaveToKnowledgeViewModel + SavePreviewModal + ChatDetailScreen 上 [+ archive] 按钮 + 跳新建 entry 详情（详情页占位） | ~1 天 | 真机：从 chat AI 回复点 archive → 模态 → 保存 → 跳详情占位页 |
| **4 · KbHome + KbEntryDetail 基础版** | KbHomeViewModel + KbHomeScreen（搜索框先占位，分类 chip + 列表）+ KbEntryDetailScreen（普通变体，5 节渲染）+ NavHost 接通 + 空态 | ~1.5 天 | 真机：浏览列表，按分类筛，进详情看 KaTeX 渲染 5 节 |
| **5 · 错题集 + per-session + scan** | KbMistakesScreen + KbHome 加 [Mistakes] 入口 + Detail 错题变体（图+题面双栏）+ ChatDetailScreen 加 [+ archive session] + ScanDocumentDetailScreen / PageEditScreen 加 [archive page] | ~1.5 天 | 真机：3 种 source 都能 archive；mistakes 列表只显示有 standardizedQuestion 的；错题详情双栏 OK |
| **6 · 编辑 / regenerate / 搜索 / related** | overflow 菜单 4 项 + SummaryMarkdownEditor + 3 个 use case + KbHome 搜索框接 FTS（含 < 5 降级）+ RelatedEntriesSection | ~1.5 天 | 真机：rename / recategorize / delete / regenerate / edit summary 全 OK；中文搜索找到条目；related 跳转 |
| **7 · DoD + PR** | instrumented smoke（3 屏 happy path）+ 截图归档 + PR + tag `p3-knowledge-mvp` | ~0.5 天 | PR 合并 main + tag |

**总计 ~8.5 天**（保守估算，按 P2 实际节奏推算）。

---

## 9. 风险登记

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| LLM JSON 解析不稳 | 中 | 中 | 1 次重试 + fallback 进 modal 让用户手改 |
| 跨词 bigram 噪声让 AND 查询 miss | 高 | 中 | 鼓励空格 + 命中 < 5 自动降级 OR 重试 |
| Multimodal vision 调用 quota 高 | 中 | 中 | 一次只送 1 张图；per-session archive 跳过历史 image |
| 长 session per-session archive 触达上下文窗口 | 中 | 中 | 截断尾 N 条 + token estimator 留 1k 余量 |
| Room v5 destructive 影响他人 dev | 低 | 低 | dev 专属，feedback `dev_db_data.md` 已确认数据可丢 |
| KaTeX WebView 多次实例化性能 | 低 | 低 | 沿用 P1 方案；KbEntryDetailScreen 一屏只挂一个 WebView |
| Vision + JSON mode 的 provider 兼容 | 中 | 中 | OpenAI / OpenRouter 都明确支持；本地 Ollama / LM Studio 视模型而定，失败时 fallback 到 fallback path |
| originalImage 复制后源被删 | 低 | 低 | 复制到 filesDir/kb/ 后与源解耦；删条目时手动删文件 |

---

## 10. 决策记录（来自 2026-04-25 brainstorm）

按时间顺序：

1. **沿用 2026-04-20 P3 spec 大方向**，本次只补细节
2. **错题集独立二级入口 + 特化详情页**（不只是 chip filter）
3. **AI 单次 LLM 调用同时返回 standardizedQuestion + 5 节 summaryMarkdown**（不分两次调用）
4. **Add-to-KB 同时支持 per-message + per-session 两个入口**
5. **预填默认学科分类，AI 提议新分类需用户确认**（非自动入表）
6. **完整编辑套件**：rename / recategorize / delete / edit markdown / regenerate
7. **执行用方案 A**（一次到位，~8.5 天）
8. **保留 SCAN sourceType**，走"整页扫描 → LLM OCR + 摘要"路线（per-page 触发）
9. **默认分类 seed**：数学 / 物理 / 化学 / 生物 / 英语 / 编程 / 其它

---

## 变更记录

- 2026-04-25：初稿，brainstorm 收敛后落盘
