package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.domain.bitgrades.GradeBookSource
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 仅给「成绩单分享卡片」用的轻量 VM：读取 GradeBookSource 算好的 GradeBook，与成绩页共用同一份计算。
 *
 * 不复用 GradesViewModel 的原因:它带 AI 分析/选择过滤等会话态,分享卡片只需要静态
 * GradeBook;独立 VM 让 ShareCardScreen 的 graphicsLayer 捕获路径更纯净。
 */
@HiltViewModel
class ShareCardViewModel @Inject constructor(
    bookSource: GradeBookSource,
) : ViewModel() {
    val book: StateFlow<GradeBook> = bookSource.books.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
    )
}
