package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.GradesAnalysisCache
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.AnalyzeGradesUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.StartGradeChatUseCase
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesUiState(
    val book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
    val analysis: String = "",
    val analysisAt: Long? = null,            // 上次生成/缓存时间戳;null=没缓存过
    val analysisFromCache: Boolean = false,  // 当前显示的是缓存(true) 还是新流式生成(false)
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
    val loggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncSteps: List<String> = emptyList(),
    val syncError: GradesSyncError? = null,
)

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val dao: GradesDao,
    private val computeGpa: ComputeGpaUseCase,
    private val analyze: AnalyzeGradesUseCase,
    private val startChat: StartGradeChatUseCase,
    private val analysisCache: GradesAnalysisCache,
    private val syncGrades: SyncGradesUseCase,
    private val credPrefs: ImportCredentialPrefs,
) : ViewModel() {

    private val _local = MutableStateFlow(GradesUiState())

    init {
        // 启动时加载缓存(若有),进入成绩单时 AI 报告就有内容可看,无需触发生成。
        analysisCache.observe.onEach { cached ->
            if (cached != null) _local.update {
                // 只在 _local 还没有更新过的 analysis 时填充缓存,避免覆盖正在流式的新内容。
                if (it.analyzing || it.analysis.isNotBlank()) it
                else it.copy(analysis = cached.text, analysisAt = cached.at, analysisFromCache = true)
            }
        }.launchIn(viewModelScope)
    }

    private data class SyncLocalState(
        val syncing: Boolean = false,
        val steps: List<String> = emptyList(),
        val error: GradesSyncError? = null,
    )
    private val syncLocal = MutableStateFlow(SyncLocalState())

    val uiState: StateFlow<GradesUiState> = combine(
        dao.observeAll(), dao.observeRanks(), _local, credPrefs.observeAll(), syncLocal,
    ) { grades, ranks, local, creds, sync ->
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
            loggedIn = creds != null,
            syncing = sync.syncing,
            syncSteps = sync.steps,
            syncError = sync.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradesUiState())

    /** 打开 AI 分析:有缓存或正在生成 → 仅展示(不重跑);否则触发一次新生成。 */
    fun onAnalyze() {
        val st = uiState.value
        if (st.analyzing || st.analysis.isNotBlank()) return
        runAnalysis()
    }

    /** 用户主动「重新生成」:清屏 → 流式重跑 → 写回缓存。 */
    fun onRegenerate() {
        if (uiState.value.analyzing) return
        runAnalysis()
    }

    private fun runAnalysis() {
        val book = uiState.value.book
        if (book.isEmpty) return
        _local.update { it.copy(analyzing = true, analysis = "", analysisAt = null, analysisFromCache = false, analysisError = null) }
        viewModelScope.launch {
            try {
                analyze.invoke(book).collect { delta -> _local.update { it.copy(analysis = it.analysis + delta) } }
                val finalText = _local.value.analysis
                if (finalText.isNotBlank()) {
                    analysisCache.save(finalText)
                    _local.update { it.copy(analysisAt = System.currentTimeMillis()) }
                }
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

    /** 用已存凭据触发一次成绩同步,进度并入成绩页内联展示。未登录(无凭据)时静默忽略。 */
    fun onSyncNow() {
        val creds = credPrefs.observeAll().value ?: return
        if (syncLocal.value.syncing) return
        syncLocal.value = SyncLocalState(syncing = true)
        viewModelScope.launch {
            val req = GradesSyncRequest(
                creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL, true,
            )
            syncGrades.sync(req).collect { step ->
                val cur = syncLocal.value
                syncLocal.value = when (step) {
                    SyncGradesStep.LoggingIn -> cur.copy(steps = cur.steps + "登录中…")
                    SyncGradesStep.FetchingGrades -> cur.copy(steps = cur.steps + "拉取成绩…")
                    SyncGradesStep.FetchingRanks -> cur.copy(steps = cur.steps + "拉取排名…")
                    SyncGradesStep.Persisting -> cur.copy(steps = cur.steps + "落库…")
                    is SyncGradesStep.Done -> cur.copy(syncing = false, steps = cur.steps + "完成")
                    is SyncGradesStep.Failed -> cur.copy(syncing = false, error = step.err)
                }
            }
        }
    }
}
