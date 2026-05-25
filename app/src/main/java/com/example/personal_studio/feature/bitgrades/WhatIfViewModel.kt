package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class WhatIfMode { REVERSE, PREDICT }

data class WhatIfUiState(
    val book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null, null),
    val mode: WhatIfMode = WhatIfMode.REVERSE,
    val targetGpa: String = "",
    val remainingCredits: String = "",
    val assumedPoint: String = "",
)

@HiltViewModel
class WhatIfViewModel @Inject constructor(
    dao: GradesDao,
    computeGpa: ComputeGpaUseCase,
) : ViewModel() {

    private val _input = MutableStateFlow(WhatIfUiState())

    val uiState: StateFlow<WhatIfUiState> = combine(
        dao.observeAll(), dao.observeRanks(), _input,
    ) { grades, ranks, input ->
        input.copy(book = computeGpa.invoke(grades, ranks))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WhatIfUiState())

    fun onModeChange(m: WhatIfMode) = _input.update { it.copy(mode = m) }
    fun onTargetChange(s: String) = _input.update { it.copy(targetGpa = s) }
    fun onRemainingChange(s: String) = _input.update { it.copy(remainingCredits = s) }
    fun onAssumedChange(s: String) = _input.update { it.copy(assumedPoint = s) }

    /** 目标反推结果；输入不全返回 null。 */
    fun reverseResult(st: WhatIfUiState): GpaCalculator.RequiredAvg? {
        val target = st.targetGpa.toDoubleOrNull() ?: return null
        val remaining = st.remainingCredits.toDoubleOrNull() ?: return null
        val done = st.book.totalCredits
        return GpaCalculator.requiredAverageForTarget(done, done * st.book.overallGpa, remaining, target)
    }

    /** 预测：剩余 remaining 学分按 assumed 绩点，预计新总 GPA；输入不全返回 null。 */
    fun predictResult(st: WhatIfUiState): Double? {
        val remaining = st.remainingCredits.toDoubleOrNull() ?: return null
        val assumed = st.assumedPoint.toDoubleOrNull() ?: return null
        val done = st.book.totalCredits
        return GpaCalculator.project(
            currentItems = listOf(done to st.book.overallGpa),
            predicted = listOf(remaining to assumed),
        )
    }
}
