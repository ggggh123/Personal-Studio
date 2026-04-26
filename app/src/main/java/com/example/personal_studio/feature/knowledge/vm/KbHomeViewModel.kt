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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryWithCount(val category: KbCategory, val count: Int)

data class KbHomeUiState(
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val showNotes: Boolean = true,
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
    private val showNotes = MutableStateFlow(true)
    private val rescuedEntries = MutableStateFlow<List<KbEntry>?>(null)

    /**
     * When [searchQuery] is blank we observe the browse-mode list (filtered by
     * category + notes-only flag); otherwise we forward to FTS search.
     * The 150 ms debounce on [searchQuery] keeps the FTS path off every keystroke
     * while still feeling responsive.
     *
     * Semantics: [showNotes] = true means "notes section, hide mistakes". The DAO
     * predicate is `(:notesOnly = 0 OR standardizedQuestion IS NULL)`, so passing
     * `notesOnly = showNotes` (no inversion) gives:
     *   - showNotes=true  → notesOnly=true  → only rows with NULL standardizedQuestion
     *   - showNotes=false → notesOnly=false → everything
     * Matches user intent. Don't add a `!`.
     */
    private val entriesFlow = combine(
        searchQuery.debounce(150).distinctUntilChanged(),
        selectedCategoryId,
        showNotes,
    ) { q, catId, notes -> Triple(q, catId, notes) }
        .flatMapLatest { (q, catId, notes) ->
            if (q.isBlank()) repo.observeAllEntries(catId, notesOnly = notes)
            else repo.search(q)
        }

    val uiState: StateFlow<KbHomeUiState> = combine(
        searchQuery,
        selectedCategoryId,
        showNotes,
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
        val notes = values[2] as Boolean
        val notesCount = values[3] as Int
        val mistakesCount = values[4] as Int
        val cats = values[5] as List<KbCategory>
        val counts = values[6] as Map<Long, Int>
        val entries = values[7] as List<KbEntry>
        val rescued = values[8] as List<KbEntry>?
        KbHomeUiState(
            searchQuery = q,
            selectedCategoryId = catId,
            showNotes = notes,
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
    fun onToggleNotes() { showNotes.value = !showNotes.value }

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
