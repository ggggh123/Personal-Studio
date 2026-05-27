package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 仅给「成绩单分享卡片」用的轻量 VM：把 GradesDao 的成绩 + 排名喂给 ComputeGpaUseCase
 * 转成 GradeBook,与 GradesViewModel 同步观测。
 *
 * 不复用 GradesViewModel 的原因:它带 AI 分析/选择过滤等会话态,分享卡片只需要静态
 * GradeBook;独立 VM 让 ShareCardScreen 的 graphicsLayer 捕获路径更纯净。
 */
@HiltViewModel
class ShareCardViewModel @Inject constructor(
    dao: GradesDao,
    computeGpa: ComputeGpaUseCase,
) : ViewModel() {
    val book: StateFlow<GradeBook> = combine(dao.observeAll(), dao.observeRanks()) { g, r ->
        computeGpa.invoke(g, r)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
    )
}
