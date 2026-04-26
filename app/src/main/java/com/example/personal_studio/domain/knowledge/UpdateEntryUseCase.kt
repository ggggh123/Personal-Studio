package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import javax.inject.Inject

/**
 * Persists a user-edited KB entry. Repository handles FTS rewrite + updatedAt
 * timestamp internally; this is a thin domain wrapper for VM injection.
 */
class UpdateEntryUseCase @Inject constructor(private val repo: KnowledgeRepository) {
    suspend operator fun invoke(entry: KbEntry) = repo.updateEntry(entry)
}
