package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

open class FakeKnowledgeRepository : KnowledgeRepository {
    val allEntries = MutableStateFlow<List<KbEntry>>(emptyList())
    val mistakes = MutableStateFlow<List<KbEntry>>(emptyList())
    val entryFlow = MutableStateFlow<KbEntry?>(null)
    val related = MutableStateFlow<List<KbEntry>>(emptyList())
    val categories = MutableStateFlow<List<KbCategory>>(emptyList())
    val notesCount = MutableStateFlow(0)
    val mistakesCount = MutableStateFlow(0)
    val categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val searchResults = MutableStateFlow<List<KbEntry>>(emptyList())
    var orSearchResults: List<KbEntry> = emptyList()

    var draftToReturn: KbEntryDraft? = null
    var lastDraftSource: KbDraftSource? = null
    var savedEntryIdToReturn: Long = 1L
    var lastSavedDraft: KbEntryDraft? = null
    var lastUpdatedEntry: KbEntry? = null
    var lastDeletedId: Long? = null
    val upsertedCategories: MutableList<String> = mutableListOf()

    override fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>> = allEntries
    override fun observeMistakes(): Flow<List<KbEntry>> = mistakes
    override fun observeEntry(id: Long): Flow<KbEntry?> = entryFlow
    override fun observeRelated(id: Long): Flow<List<KbEntry>> = related
    override fun observeCategories(): Flow<List<KbCategory>> = categories
    override fun observeNotesCount(): Flow<Int> = notesCount
    override fun observeMistakesCount(): Flow<Int> = mistakesCount
    override fun observeCategoryCounts(): Flow<Map<Long, Int>> = categoryCounts
    override fun search(query: String): Flow<List<KbEntry>> = searchResults
    override suspend fun searchOr(query: String): List<KbEntry> = orSearchResults

    override suspend fun saveEntry(draft: KbEntryDraft): Long {
        lastSavedDraft = draft
        return savedEntryIdToReturn
    }
    override suspend fun updateEntry(entry: KbEntry) { lastUpdatedEntry = entry }
    override suspend fun deleteEntry(id: Long) { lastDeletedId = id }
    override suspend fun upsertCategory(name: String): Long {
        upsertedCategories += name
        return name.hashCode().toLong()
    }
    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
        lastDraftSource = source
        return draftToReturn ?: error("draftToReturn not configured")
    }
}
