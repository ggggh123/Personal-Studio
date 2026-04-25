package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {

    // -- read --
    fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>>
    fun observeMistakes(): Flow<List<KbEntry>>
    fun observeEntry(id: Long): Flow<KbEntry?>
    fun observeRelated(id: Long): Flow<List<KbEntry>>
    fun observeCategories(): Flow<List<KbCategory>>
    fun observeNotesCount(): Flow<Int>
    fun observeMistakesCount(): Flow<Int>
    fun observeCategoryCounts(): Flow<Map<Long, Int>>

    /** AND-mode FTS search; empty input → empty list. */
    fun search(query: String): Flow<List<KbEntry>>
    /** OR-mode FTS search used as fallback when AND returns < 5 results. */
    suspend fun searchOr(query: String): List<KbEntry>

    // -- write --
    suspend fun saveEntry(draft: KbEntryDraft): Long
    suspend fun updateEntry(entry: KbEntry)
    suspend fun deleteEntry(id: Long)
    suspend fun upsertCategory(name: String): Long

    // -- LLM bridge (implemented in Phase 2; throws NotImplementedError until then) --
    suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft
}
