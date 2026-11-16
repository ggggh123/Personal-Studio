package com.example.personal_studio.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.GradesPollSettingsViewModel

/** 出分提醒 settings, opened from the "成绩自动查询已停止" notification. */
@Composable
fun GradesPollSettingsScreen(onBack: () -> Unit, vm: GradesPollSettingsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    PollSettingsPage(
        title = "出分提醒", what = "检查一次，有新成绩时通知",
        credsSaved = st.credsSaved, enabled = st.enabled, intervalHours = st.intervalHours, lastResult = st.lastResult,
        onToggle = { vm.onEnableToggle(it) }, onInterval = { vm.onIntervalSelect(it) }, onBack = onBack,
    )
}
