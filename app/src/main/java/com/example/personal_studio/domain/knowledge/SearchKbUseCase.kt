package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * AND-mode FTS search; if the AND result set is below [SPARSE_THRESHOLD] hits the
 * use case returns the OR-mode result instead (one-shot, not reactive — re-emits
 * happen only when the upstream Flow emits).
 *
 * Empty/blank query short-circuits to an empty Flow.
 */
class SearchKbUseCase @Inject constructor(private val repo: KnowledgeRepository) {

    operator fun invoke(query: String): Flow<List<KbEntry>> {
        if (query.isBlank()) return flowOf(emptyList())
        return repo.search(query).map { andResults ->
            if (andResults.size >= SPARSE_THRESHOLD) andResults
            else {
                val or = repo.searchOr(query)
                if (or.size > andResults.size) or else andResults
            }
        }
    }

    companion object {
        const val SPARSE_THRESHOLD = 5
    }
}
