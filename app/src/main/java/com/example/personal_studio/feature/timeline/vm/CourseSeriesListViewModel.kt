package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.CourseSeriesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CourseSeriesListViewModel @Inject constructor(
    private val repo: TimelineRepository,
) : ViewModel() {
    val series: StateFlow<List<CourseSeriesSummary>> =
        repo.observeCourseSeriesList()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
