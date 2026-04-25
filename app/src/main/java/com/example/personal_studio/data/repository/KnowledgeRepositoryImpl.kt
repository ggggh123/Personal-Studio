package com.example.personal_studio.data.repository

import androidx.room.withTransaction
import com.example.personal_studio.core.bigram.BigramTokenizer
import com.example.personal_studio.data.kb.KbImageStore
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.KbSourceType
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val entryDao: KbEntryDao,
    private val categoryDao: KbCategoryDao,
    private val ftsDao: KbFtsDao,
    private val imageStore: KbImageStore,
    private val llm: com.example.personal_studio.data.remote.llm.LLMProvider,
    private val contextLoader: SourceContextLoader,
) : KnowledgeRepository {

    // -- read --

    override fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>> =
        combine(
            entryDao.observeAll(categoryId, notesOnly),
            categoryDao.observeAll(),
        ) { entries, cats -> entries.toDomain(cats) }

    override fun observeMistakes(): Flow<List<KbEntry>> =
        combine(
            entryDao.observeMistakes(),
            categoryDao.observeAll(),
        ) { entries, cats -> entries.toDomain(cats) }

    override fun observeEntry(id: Long): Flow<KbEntry?> =
        combine(entryDao.observe(id), categoryDao.observeAll()) { e, cats ->
            e?.let { listOf(it).toDomain(cats).firstOrNull() }
        }

    override fun observeRelated(id: Long): Flow<List<KbEntry>> =
        combine(entryDao.observeRelated(id), categoryDao.observeAll()) { es, cats ->
            es.toDomain(cats)
        }

    override fun observeCategories(): Flow<List<KbCategory>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeNotesCount(): Flow<Int> = entryDao.countNotes()
    override fun observeMistakesCount(): Flow<Int> = entryDao.countMistakes()
    override fun observeCategoryCounts(): Flow<Map<Long, Int>> =
        entryDao.countByCategory().map { rows -> rows.associate { it.id to it.count } }

    override fun search(query: String): Flow<List<KbEntry>> {
        val ftsQ = BigramTokenizer.tokenizeForQuery(query)
        if (ftsQ.isBlank()) return flowOf(emptyList())
        return combine(entryDao.searchFlow(ftsQ), categoryDao.observeAll()) { es, cats ->
            es.toDomain(cats)
        }
    }

    override suspend fun searchOr(query: String): List<KbEntry> {
        val baseTokens = BigramTokenizer.tokenizeForQuery(query)
        if (baseTokens.isBlank()) return emptyList()
        // baseTokens is now space-separated quoted bigrams (implicit AND); convert to explicit OR.
        val ftsQ = baseTokens.split(' ').filter { it.isNotBlank() }.joinToString(" OR ")
        val cats = categoryDao.observeAll().first()
        return entryDao.searchOnce(ftsQ).toDomain(cats)
    }

    // -- write --

    override suspend fun saveEntry(draft: KbEntryDraft): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val categoryId = upsertCategory(draft.categorySuggestion)

        val (sourceType, chatMsgId, chatSessionId, scanPageId) = unpackSource(draft.source)

        // 1. Insert with placeholder image path; we'll patch after promote.
        val entryId = entryDao.insert(
            KbEntryEntity(
                title = draft.title,
                categoryId = categoryId,
                sourceType = sourceType,
                sourceChatMessageId = chatMsgId,
                sourceChatSessionId = chatSessionId,
                sourceScanPageId = scanPageId,
                originalImagePath = null,
                standardizedQuestion = draft.standardizedQuestion,
                summaryMarkdown = draft.summaryMarkdown,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // 2. Promote staged image (if any) to <entryId>.jpg, then patch the entry row.
        if (!draft.originalImagePath.isNullOrBlank()) {
            val finalPath = imageStore.promote(draft.originalImagePath, entryId)
            entryDao.update(
                entryDao.get(entryId)!!.copy(originalImagePath = finalPath),
            )
        }

        // 3. Write FTS row.
        ftsDao.upsert(
            KbEntryFtsEntity(
                rowid = entryId,
                titleBigrams = BigramTokenizer.tokenizeForIndex(draft.title),
                summaryBigrams = BigramTokenizer.tokenizeForIndex(draft.summaryMarkdown),
                standardizedQuestionBigrams = BigramTokenizer.tokenizeForIndex(draft.standardizedQuestion.orEmpty()),
            ),
        )

        // 4. Resolve relatedEntryTitles → existing entryIds → kb_relations rows.
        if (draft.relatedEntryTitles.isNotEmpty()) {
            val matched = entryDao.findByTitles(draft.relatedEntryTitles)
            if (matched.isNotEmpty()) {
                entryDao.insertRelations(
                    matched.map { KbRelationEntity(fromEntryId = entryId, toEntryId = it.id) },
                )
            }
        }

        entryId
    }

    override suspend fun updateEntry(entry: KbEntry) = db.withTransaction {
        val existing = entryDao.get(entry.id) ?: return@withTransaction
        val now = System.currentTimeMillis()
        entryDao.update(
            existing.copy(
                title = entry.title,
                categoryId = entry.categoryId,
                standardizedQuestion = entry.standardizedQuestion,
                summaryMarkdown = entry.summaryMarkdown,
                updatedAt = now,
            ),
        )
        ftsDao.upsert(
            KbEntryFtsEntity(
                rowid = entry.id,
                titleBigrams = BigramTokenizer.tokenizeForIndex(entry.title),
                summaryBigrams = BigramTokenizer.tokenizeForIndex(entry.summaryMarkdown),
                standardizedQuestionBigrams = BigramTokenizer.tokenizeForIndex(entry.standardizedQuestion.orEmpty()),
            ),
        )
    }

    override suspend fun deleteEntry(id: Long) = db.withTransaction {
        ftsDao.delete(id)
        entryDao.delete(id)
        imageStore.deleteForEntry(id)
    }

    override suspend fun upsertCategory(name: String): Long {
        val trimmed = name.trim().ifBlank { "其它" }
        categoryDao.findByName(trimmed)?.let { return it.id }
        val id = categoryDao.insert(
            KbCategoryEntity(name = trimmed, seeded = false, createdAt = System.currentTimeMillis()),
        )
        // findByName() above raced with a concurrent insert? IGNORE strategy returns -1; refetch.
        return if (id > 0) id else categoryDao.findByName(trimmed)!!.id
    }

    // -- LLM bridge --

    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
        val context = contextLoader.load(source)
        val schema = JSON_SCHEMA
        val systemMsg = com.example.personal_studio.data.remote.llm.LlmMessage(
            role = com.example.personal_studio.data.remote.llm.LlmRole.SYSTEM,
            text = SYSTEM_PROMPT,
        )
        val recentTitles = entryDao.recentTitles(50)
        val categories = categoryDao.observeAll().first().map { it.name }
        val contextHeader = com.example.personal_studio.data.remote.llm.LlmMessage(
            role = com.example.personal_studio.data.remote.llm.LlmRole.USER,
            text = buildContextHeader(categories, recentTitles),
        )
        val finalMessages = listOf(systemMsg, contextHeader) + context.messages

        // First attempt
        val first = runCatching { llm.generateStructured(finalMessages, schema, temperature = 0.3f) }
            .getOrElse { throw it }                           // network error -> propagate
        parseStrict(first, source, context)?.let { return it }

        // Retry with lower temp
        val second = runCatching { llm.generateStructured(finalMessages, schema, temperature = 0.1f) }
            .getOrElse { throw it }
        parseStrict(second, source, context)?.let { return it }

        // Fallback skeleton
        return fallbackDraft(
            source = source,
            context = context,
            rawText = second.ifBlank { first },
            reason = if (canParseJson(second) || canParseJson(first))
                KbDraftFallbackReason.MISSING_REQUIRED_FIELDS
            else KbDraftFallbackReason.JSON_PARSE_FAILED,
        )
    }

    private fun buildContextHeader(categories: List<String>, recentTitles: List<String>): String =
        buildString {
            append("[已有分类]\n")
            append(if (categories.isEmpty()) "(空)" else categories.joinToString(", "))
            append("\n\n[已有条目标题（最多 50 条，按 createdAt DESC）]\n")
            if (recentTitles.isEmpty()) append("(空)") else recentTitles.forEach { append("- ").append(it).append('\n') }
            append("\n\n[输出 schema]\n").append(JSON_SCHEMA)
        }

    private fun canParseJson(s: String): Boolean = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(s)
    }.isSuccess

    private fun parseStrict(raw: String, source: KbDraftSource, ctx: SourceContextLoader.LoadedContext): KbEntryDraft? {
        val obj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val cat = obj["categorySuggestion"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val summary = obj["summaryMarkdown"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val isQuestion = obj["isQuestion"]?.jsonPrimitive?.booleanOrNull ?: false
        val stdQ = obj["standardizedQuestion"]?.jsonPrimitive?.contentOrNull?.takeIf { isQuestion && it.isNotBlank() }
        val related = obj["relatedEntryTitles"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return KbEntryDraft(
            source = source,
            title = title,
            categorySuggestion = cat,
            standardizedQuestion = stdQ,
            summaryMarkdown = summary,
            relatedEntryTitles = related.take(3),
            originalImagePath = ctx.stagedImagePath,
        )
    }

    private fun fallbackDraft(
        source: KbDraftSource,
        context: SourceContextLoader.LoadedContext,
        rawText: String,
        reason: KbDraftFallbackReason,
    ): KbEntryDraft = KbEntryDraft(
        source = source,
        title = context.fallbackTitle.ifBlank { "未命名条目" },
        categorySuggestion = "其它",
        standardizedQuestion = null,
        summaryMarkdown = "## 原始内容\n\n$rawText",
        relatedEntryTitles = emptyList(),
        originalImagePath = context.stagedImagePath,
        isFallback = true,
        fallbackReason = reason,
    )

    // -- mappers --

    private fun List<KbEntryEntity>.toDomain(cats: List<KbCategoryEntity>): List<KbEntry> {
        val byId = cats.associateBy { it.id }
        return map { e ->
            KbEntry(
                id = e.id,
                title = e.title,
                categoryId = e.categoryId,
                categoryName = e.categoryId?.let { byId[it]?.name },
                source = e.sourceType.toDomain(),
                sourceChatMessageId = e.sourceChatMessageId,
                sourceChatSessionId = e.sourceChatSessionId,
                sourceScanPageId = e.sourceScanPageId,
                originalImagePath = e.originalImagePath,
                standardizedQuestion = e.standardizedQuestion,
                summaryMarkdown = e.summaryMarkdown,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
        }
    }

    private fun KbCategoryEntity.toDomain() = KbCategory(id = id, name = name, seeded = seeded)
    private fun KbSourceType.toDomain() = when (this) {
        KbSourceType.CHAT_MESSAGE -> KbSource.CHAT_MESSAGE
        KbSourceType.CHAT_SESSION -> KbSource.CHAT_SESSION
        KbSourceType.SCAN -> KbSource.SCAN
    }

    private data class SourceParts(
        val sourceType: KbSourceType,
        val chatMsgId: Long?,
        val chatSessionId: Long?,
        val scanPageId: Long?,
    )

    private fun unpackSource(s: KbDraftSource): SourceParts = when (s) {
        is KbDraftSource.FromChatMessage -> SourceParts(KbSourceType.CHAT_MESSAGE, s.aiMessageId, s.sessionId, null)
        is KbDraftSource.FromChatSession -> SourceParts(KbSourceType.CHAT_SESSION, null, s.sessionId, null)
        is KbDraftSource.FromScanPage -> SourceParts(KbSourceType.SCAN, null, null, s.pageId)
    }

    companion object {
        private const val SYSTEM_PROMPT = """你是学习知识库的归档助手。任务：把给定的对话或扫描页浓缩为一张知识卡片。
只输出严格的 JSON（不带 markdown 代码围栏、不带任何解释文字）。
所有字段必填；不知道就给空字符串、null 或空数组（按 schema 类型）。
公式用 ${'$'}...${'$'}（行内）或 ${'$'}${'$'}...${'$'}${'$'}（块级）。
中文为主。"""

        private const val JSON_SCHEMA = """{
  "title": "string，≤15 字，能 1 秒看懂这条 KB 关于什么",
  "categorySuggestion": "string，下面分类列表里的某一个，或全新分类名",
  "isQuestion": "boolean，是否为可独立成立的题目",
  "standardizedQuestion": "string|null，isQuestion=true 时必填；规范化清晰可独立阅读的题面，LaTeX 包公式；isQuestion=false 填 null",
  "summaryMarkdown": "string，5 节 Markdown：## 核心概念 / ## 推导过程 / ## 关键公式 / ## 易错点 / ## 应用场景",
  "relatedEntryTitles": "array<string>，从给定的已有标题列表精确挑出最多 3 个；若无则空数组"
}"""
    }
}
