package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.llm.CuratedModels
import com.example.personal_studio.core.llm.activeEndpoint
import com.example.personal_studio.core.util.SemesterTimeMapper
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.feature.profile.maskAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** What the settings rows show as their current value. */
data class SettingsHomeState(
    val semesterStart: LocalDate? = null,
    val periodCount: Int = 0,
    val courseCount: Int = 0,
    val modelName: String = "",
    val customModel: Boolean = false,
)

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    private val semester: SemesterPreferences,
    timetable: TimetablePreferences,
    timeline: TimelineRepository,
    userPrefs: UserPreferencesRepository,
    credPrefs: ImportCredentialPrefs,
    private val logout: LogoutUseCase,
) : ViewModel() {

    /** The signed-in school account, masked ("1120 **** 21"); null when signed out. */
    val account: StateFlow<String?> = credPrefs.observeAll()
        .map { it?.username?.let(::maskAccount) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), credPrefs.observeAll().value?.username?.let(::maskAccount))

    val state: StateFlow<SettingsHomeState> = combine(
        semester.startDate,
        timetable.periods,
        timeline.observeCourseSeriesList(),
        userPrefs.activeEndpoint(),
    ) { start, periods, series, endpoint ->
        SettingsHomeState(
            semesterStart = start,
            periodCount = periods.size,
            courseCount = series.size,
            modelName = (if (endpoint.custom) null else CuratedModels.displayFor(endpoint.model))
                ?: endpoint.model.ifBlank { "未填写模型" },
            customModel = endpoint.custom,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsHomeState())

    /** Signs out of the school account; [clearSchoolData] also deletes the downloaded school data. */
    fun logout(clearSchoolData: Boolean) = viewModelScope.launch { logout.invoke(clearSchoolData) }

    /** Stores the Monday of the picked date's week, as course weeks count from a Monday. */
    fun setSemesterStart(picked: LocalDate) {
        viewModelScope.launch { semester.setStartDate(SemesterTimeMapper.normalizeSemesterStart(picked)) }
    }
}
