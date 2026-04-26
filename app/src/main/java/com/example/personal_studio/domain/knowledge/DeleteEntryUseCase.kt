package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import javax.inject.Inject

/**
 * Removes a KB entry. Repository CASCADE-deletes FTS row, kb_relations rows,
 * and the staged image file (if any). Idempotent if the entry doesn't exist.
 */
class DeleteEntryUseCase @Inject constructor(private val repo: KnowledgeRepository) {
    suspend operator fun invoke(entryId: Long) = repo.deleteEntry(entryId)
}
