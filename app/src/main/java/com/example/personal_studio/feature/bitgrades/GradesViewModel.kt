package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.domain.bitgrades.AnalyzeGradesUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.StartGradeChatUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesUiState(
    val book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null),
    val analysis: String = "",
    val analyzing: Boolean = false,
    val analysisError: String? = null,
)

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val dao: GradesDao,
    private val computeGpa: ComputeGpaUseCase,
    private val analyze: AnalyzeGradesUseCase,
    private val startChat: StartGradeChatUseCase,
) : ViewModel() {

    private val _local = MutableStateFlow(GradesUiState())

    val uiState: StateFlow<GradesUiState> = combine(
        dao.observeAll(), dao.observeRanks(), _local,
    ) { grades, ranks, local ->
        local.copy(book = computeGpa.invoke(grades, ranks))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradesUiState())

    fun onAnalyze() {
        val book = uiState.value.book
        if (book.isEmpty) return
        _local.update { it.copy(analyzing = true, analysis = "", analysisError = null) }
        viewModelScope.launch {
            try {
                analyze.invoke(book).collect { delta -> _local.update { it.copy(analysis = it.analysis + delta) } }
            } catch (e: Throwable) {
                _local.update { it.copy(analysisError = e.message ?: "分析失败") }
            } finally {
                _local.update { it.copy(analyzing = false) }
            }
        }
    }

    /** 建会话并回调 sessionId 供导航。 */
    fun onAskInChat(onReady: (Long) -> Unit) {
        val book = uiState.value.book
        if (book.isEmpty) return
        viewModelScope.launch { onReady(startChat.invoke(book)) }
    }
}
