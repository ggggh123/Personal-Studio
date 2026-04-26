package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryWithCount(val category: KbCategory, val count: Int)

/**
 * Three-state browse-list filter for KbHome. Mutually exclusive — selecting one
 * deselects the others. ALL is the default so the user sees everything inline
 * (notes + mistakes together) without having to discover a separate sub-screen.
 */
enum class KbHomeFilter { ALL, NOTES_ONLY, MISTAKES_ONLY }

data class KbHomeUiState(
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val filter: KbHomeFilter = KbHomeFilter.ALL,
    val notesCount: Int = 0,
    val mistakesCount: Int = 0,
    val categories: List<CategoryWithCount> = emptyList(),
    val entries: List<KbEntry> = emptyList(),
    val isSearching: Boolean = false,
    /**
     * Non-null when the strict AND-search returned < SPARSE_THRESHOLD hits AND
     * the OR rescue found more. UI shows these instead of [entries] when present.
     * Cleared on every [onSearchChange] so a stale rescue from a previous query
     * can't leak into a new one.
     */
    val rescuedEntries: List<KbEntry>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class KbHomeViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val filter = MutableStateFlow(KbHomeFilter.ALL)
    private val rescuedEntries = MutableStateFlow<List<KbEntry>?>(null)

    /**
     * When [searchQuery] is blank we observe the browse-mode list (filtered by
     * category + the active [KbHomeFilter]); otherwise we forward to FTS search.
     * The 150 ms debounce on [searchQuery] keeps the FTS path off every keystroke
     * while still feeling responsive.
     *
     * Filter mapping:
     *   - ALL           → observeAllEntries(catId, notesOnly = false)  (everything)
     *   - NOTES_ONLY    → observeAllEntries(catId, notesOnly = true)   (DAO predicate
     *                     `(:notesOnly = 0 OR standardizedQuestion IS NULL)` keeps
     *                     only rows with NULL standardizedQuestion).
     *   - MISTAKES_ONLY → observeMistakes(); the dedicated mistakes flow is
     *                     category-agnostic, so we post-filter by [catId] when set.
     */
    private val entriesFlow = combine(
        searchQuery.debounce(150).distinctUntilChanged(),
        selectedCategoryId,
        filter,
    ) { q, catId, f -> Triple(q, catId, f) }
        .flatMapLatest { (q, catId, f) ->
            if (q.isNotBlank()) {
                repo.search(q)
            } else {
                when (f) {
                    KbHomeFilter.ALL -> repo.observeAllEntries(catId, notesOnly = false)
                    KbHomeFilter.NOTES_ONLY -> repo.observeAllEntries(catId, notesOnly = true)
                    KbHomeFilter.MISTAKES_ONLY ->
                        if (catId == null) repo.observeMistakes()
                        else repo.observeMistakes().map { all -> all.filter { it.categoryId == catId } }
                }
            }
        }

    val uiState: StateFlow<KbHomeUiState> = combine(
        searchQuery,
        selectedCategoryId,
        filter,
        repo.observeNotesCount(),
        repo.observeMistakesCount(),
        repo.observeCategories(),
        repo.observeCategoryCounts(),
        entriesFlow,
        rescuedEntries,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val q = values[0] as String
        val catId = values[1] as Long?
        val f = values[2] as KbHomeFilter
        val notesCount = values[3] as Int
        val mistakesCount = values[4] as Int
        val cats = values[5] as List<KbCategory>
        val counts = values[6] as Map<Long, Int>
        val entries = values[7] as List<KbEntry>
        val rescued = values[8] as List<KbEntry>?
        KbHomeUiState(
            searchQuery = q,
            selectedCategoryId = catId,
            filter = f,
            notesCount = notesCount,
            mistakesCount = mistakesCount,
            categories = cats.map { CategoryWithCount(it, counts[it.id] ?: 0) },
            entries = entries,
            isSearching = q.isNotBlank(),
            rescuedEntries = rescued,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbHomeUiState())

    fun onSearchChange(q: String) {
        searchQuery.value = q
        // Stale rescue from the previous query must not leak into a new search.
        rescuedEntries.value = null
    }
    fun onSelectCategory(id: Long?) { selectedCategoryId.value = id }
    fun setFilter(newFilter: KbHomeFilter) { filter.value = newFilter }

    /**
     * AND-mode FTS search returned < [SPARSE_THRESHOLD] hits — fall back to OR
     * for one-shot rescue. Result lands in [KbHomeUiState.rescuedEntries].
     */
    fun rescueSearchIfSparse() {
        val q = searchQuery.value
        if (q.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.entries
            if (current.size < SPARSE_THRESHOLD) {
                val or = repo.searchOr(q)
                if (or.size > current.size) rescuedEntries.value = or
            }
        }
    }

    companion object {
        /** Threshold below which AND-mode search results trigger an OR-mode rescue. */
        const val SPARSE_THRESHOLD = 5
    }
}
