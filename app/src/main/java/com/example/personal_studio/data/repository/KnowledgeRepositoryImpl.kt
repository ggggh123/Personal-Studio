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
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val entryDao: KbEntryDao,
    private val categoryDao: KbCategoryDao,
    private val ftsDao: KbFtsDao,
    private val imageStore: KbImageStore,
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
        val ftsQ = BigramTokenizer.tokenizeForQuery(query).replace(" AND ", " OR ")
        if (ftsQ.isBlank()) return emptyList()
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

    // -- LLM bridge (Phase 2 will replace this body) --

    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft =
        throw NotImplementedError("draftFromSource implemented in Phase 2")

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
}
