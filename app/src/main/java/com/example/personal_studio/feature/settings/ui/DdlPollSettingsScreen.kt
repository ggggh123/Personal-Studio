package com.example.personal_studio.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.DdlPollSettingsViewModel

/** 作业自动同步 settings, opened from the "作业自动查询已停止" notification. */
@Composable
fun DdlPollSettingsScreen(onBack: () -> Unit, vm: DdlPollSettingsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    PollSettingsPage(
        title = "作业自动同步", what = "同步一次乐学和新乐学的作业，有新作业时通知",
        credsSaved = st.credsSaved, enabled = st.enabled, intervalHours = st.intervalHours, lastResult = st.lastResult,
        onToggle = { vm.onEnableToggle(it) }, onInterval = { vm.onIntervalSelect(it) }, onBack = onBack,
    )
}
