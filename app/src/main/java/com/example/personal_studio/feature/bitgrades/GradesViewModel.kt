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
    val book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
    val analysis: String = "",
    val analyzing: Boolean = false,
    val analysisError: String? = null,
    val excludedIds: Set<Long> = emptySet(),
    val selectedGpa: Double = 0.0,
    val selectedAvgScore: Double? = null,
    val selectedCredits: Double = 0.0,
    val selectedCount: Int = 0,
    val filtering: Boolean = false,   // true when excludedIds non-empty
    val selectedPeerAvgScore: Double? = null,
    val selectedPeerAvgGpa: Double? = null,
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
        val book = computeGpa.invoke(grades, ranks)
        val allCourses = book.terms.flatMap { it.courses }
        val included = allCourses.filter { it.id !in local.excludedIds }
        val selGpa = com.example.personal_studio.core.util.GpaCalculator.weightedGpa(included.map { it.credit to it.gradePoint })
        val selCredits = included.filter { it.gradePoint != null }.sumOf { it.credit }
        val selAvg = run {
            var sc = 0.0; var sw = 0.0
            included.forEach { c -> com.example.personal_studio.core.util.BitGpaConverter.toScore(c.score)?.let { sc += c.credit; sw += c.credit * it } }
            if (sc == 0.0) null else sw / sc
        }
        val (selPeerScore, selPeerGpa) = run {
            var c = 0.0; var ss = 0.0; var sg = 0.0
            included.forEach { course ->
                val avg = course.courseAvg ?: return@forEach
                val sigma = com.example.personal_studio.core.util.PeerGpaEstimator.estimateSigma(
                    mean = avg, max = course.courseMaxScore, n = course.courseStudyCount,
                )
                val gpa = com.example.personal_studio.core.util.PeerGpaEstimator.correctedGradePoint(avg, sigma)
                c += course.credit; ss += course.credit * avg; sg += course.credit * gpa
            }
            if (c == 0.0) (null to null) else (ss / c) to (sg / c)
        }
        local.copy(
            book = book,
            selectedGpa = selGpa,
            selectedAvgScore = selAvg,
            selectedCredits = selCredits,
            selectedCount = included.size,
            filtering = local.excludedIds.isNotEmpty(),
            selectedPeerAvgScore = selPeerScore,
            selectedPeerAvgGpa = selPeerGpa,
        )
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

    fun onToggleCourse(id: Long) = _local.update {
        it.copy(excludedIds = if (id in it.excludedIds) it.excludedIds - id else it.excludedIds + id)
    }

    /** Toggle a whole term: if every course in it is currently included → exclude them all; else include them all. */
    fun onToggleTerm(courseIds: List<Long>) = _local.update { st ->
        val allIncluded = courseIds.none { it in st.excludedIds }
        st.copy(excludedIds = if (allIncluded) st.excludedIds + courseIds else st.excludedIds - courseIds.toSet())
    }

    fun onClearSelection() = _local.update { it.copy(excludedIds = emptySet()) }
}
