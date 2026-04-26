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
import kotlinx.coroutines.flow.asStateFlow
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
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class KbHomeViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val showNotes = MutableStateFlow(true)

    /**
     * When [searchQuery] is blank we observe the browse-mode list (filtered by
     * category + notes-only flag); otherwise we forward to FTS search.
     * The 150 ms debounce on [searchQuery] keeps the FTS path off every keystroke
     * while still feeling responsive.
     *
     * Note: [showNotes] = true means "show notes only, hide mistakes" → repo's
     * `notesOnly = !showNotes` is intentionally inverted. (When showNotes is
     * true, the user wants the notes section, which excludes mistakes; when
     * false, they want everything.)
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
        KbHomeUiState(
            searchQuery = q,
            selectedCategoryId = catId,
            showNotes = notes,
            notesCount = notesCount,
            mistakesCount = mistakesCount,
            categories = cats.map { CategoryWithCount(it, counts[it.id] ?: 0) },
            entries = entries,
            isSearching = q.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbHomeUiState())

    fun onSearchChange(q: String) { searchQuery.value = q }
    fun onSelectCategory(id: Long?) { selectedCategoryId.value = id }
    fun onToggleNotes() { showNotes.value = !showNotes.value }

    /**
     * AND-mode FTS search returned few hits — fall back to OR for one-shot rescue.
     * Surfaces results via [rescuedEntries] so the UI can swap in the broader set
     * without disturbing the strict-search Flow.
     */
    private val _rescuedEntries = MutableStateFlow<List<KbEntry>?>(null)
    val rescuedEntries: StateFlow<List<KbEntry>?> = _rescuedEntries.asStateFlow()

    fun rescueSearchIfSparse() {
        val q = searchQuery.value
        if (q.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.entries
            if (current.size < 5) {
                val or = repo.searchOr(q)
                if (or.size > current.size) _rescuedEntries.value = or
            }
        }
    }
}
