package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.chat.ChatTitles
import com.example.personal_studio.domain.knowledge.DeleteEntryUseCase
import com.example.personal_studio.domain.knowledge.RegenerateEntryUseCase
import com.example.personal_studio.domain.knowledge.UpdateEntryUseCase
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the 来源 link on the entry page leads. */
sealed interface KbSourceTarget {
    data class Chat(val sessionId: Long) : KbSourceTarget
    data class ScanPage(val pageId: Long) : KbSourceTarget
}

/** The 来源 line: what the entry was saved from, and where tapping it goes (null once that is deleted). */
data class KbSourceLink(val label: String, val target: KbSourceTarget?)

sealed interface KbEntryDetailUiState {
    data object Loading : KbEntryDetailUiState
    data class Loaded(val entry: KbEntry, val related: List<KbEntry>) : KbEntryDetailUiState
    data object NotFound : KbEntryDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KbEntryDetailViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    private val updateUseCase: UpdateEntryUseCase,
    private val deleteUseCase: DeleteEntryUseCase,
    private val regenerateUseCase: RegenerateEntryUseCase,
    private val chatRepo: ChatRepository,
    private val scanRepo: ScanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: 0L

    val uiState: StateFlow<KbEntryDetailUiState> = combine(
        repo.observeEntry(entryId),
        repo.observeRelated(entryId),
    ) { entry, related ->
        if (entry == null) KbEntryDetailUiState.NotFound
        else KbEntryDetailUiState.Loaded(entry, related)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbEntryDetailUiState.Loading)

    /** Resolved again only when the entry's source fields change, not on every edit. */
    val source: StateFlow<KbSourceLink?> = repo.observeEntry(entryId)
        .mapLatest { e -> e?.let { Triple(it.source, it.sourceChatSessionId, it.sourceScanPageId) } }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { (kind, sessionId, pageId) -> resolveSource(kind, sessionId, pageId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveSource(kind: KbSource, sessionId: Long?, pageId: Long?): KbSourceLink = when (kind) {
        KbSource.CHAT_MESSAGE, KbSource.CHAT_SESSION -> {
            val session = sessionId?.let { runCatching { chatRepo.getSession(it) }.getOrNull() }
            if (session == null) KbSourceLink("原对话已删除", null)
            else KbSourceLink(
                "问答 · " + (if (ChatTitles.isPlaceholder(session.title)) ChatTitles.NEW else session.title),
                KbSourceTarget.Chat(session.id),
            )
        }
        KbSource.SCAN -> {
            val page = pageId?.let { runCatching { scanRepo.getPage(it) }.getOrNull() }
            if (page == null) KbSourceLink("原扫描页已删除", null)
            else {
                val doc = runCatching { scanRepo.observeDocument(page.docId).first() }.getOrNull()
                KbSourceLink("扫描 · ${doc?.title ?: "文档"} 第 ${page.ordinal + 1} 页", KbSourceTarget.ScanPage(page.id))
            }
        }
    }

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * Last error message from a failed op (regenerate timeout, save failure, etc.).
     * UI shows it as a toast/banner and calls [clearError] when dismissed. Without
     * this, exceptions in op coroutines would propagate to viewModelScope's default
     * handler and crash the process — which is what regenerate timeouts caused
     * before this field existed.
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    /**
     * Cold passthrough of category list for the CategoryPickerSheet. UI side
     * uses [collectAsStateWithLifecycle] which handles subscription lifecycle.
     */
    val observeCategoriesForUi = repo.observeCategories()

    fun rename(newTitle: String) = mutate { entry -> entry.copy(title = newTitle.trim().ifBlank { entry.title }) }

    fun changeCategory(newCategoryId: Long?) = mutate { entry -> entry.copy(categoryId = newCategoryId) }

    fun saveSummary(newMarkdown: String) = mutate { entry -> entry.copy(summaryMarkdown = newMarkdown) }

    fun saveStandardizedQuestion(newQuestion: String?) = mutate { entry ->
        entry.copy(standardizedQuestion = newQuestion?.takeIf { it.isNotBlank() })
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val ok = runOp("删除失败") { deleteUseCase(entryId) }
            if (ok) onDone()
        }
    }

    fun regenerate() {
        viewModelScope.launch {
            val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return@launch
            runOp("重新生成失败") { regenerateUseCase(current) }
        }
    }

    /**
     * Helper for UI to upsert a brand-new category by name and use it immediately.
     * Returns the category ID synchronously to the suspend caller. UI can then
     * call [changeCategory] with the returned id.
     */
    suspend fun upsertCategoryAndUse(name: String): Long = repo.upsertCategory(name)

    private fun mutate(block: (KbEntry) -> KbEntry) {
        val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return
        viewModelScope.launch {
            runOp("保存失败") { updateUseCase(block(current)) }
        }
    }

    /**
     * Runs [op] under busy state, catches and surfaces any non-cancellation
     * exception via [errorMessage], and ensures busy clears on every path.
     * Returns true on success, false on failure.
     */
    private suspend fun runOp(failurePrefix: String, op: suspend () -> Unit): Boolean {
        _busy.value = true
        return try {
            op()
            true
        } catch (e: CancellationException) {
            // Coroutine cancellation must always re-throw so structured concurrency
            // works as designed. NOT a user-facing error.
            throw e
        } catch (t: Throwable) {
            android.util.Log.e("KbEntryDetailVM", "$failurePrefix: ${t.message}", t)
            _errorMessage.value = "$failurePrefix：${t.message ?: t.javaClass.simpleName}"
            false
        } finally {
            _busy.value = false
        }
    }
}
