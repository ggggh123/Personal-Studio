package com.example.personal_studio.feature.bitimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitimport.ui.ImportPreviewScreen
import com.example.personal_studio.feature.bitimport.ui.ImportProgressScreen
import com.example.personal_studio.feature.bitimport.ui.ImportTermPickerScreen
import com.example.personal_studio.ui.components.TerminalSplash

@Composable
fun ImportEntryRoute(
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (!vm.isLoggedIn()) onNeedLogin() else vm.startWithSavedCreds()
    }
    LaunchedEffect(ui.done) {
        if (ui.done) onClose()
    }
    when (ui.currentScreen) {
        ImportScreen.Credentials -> TerminalSplash()
        ImportScreen.TermPicker  -> ImportTermPickerScreen(vm = vm)
        ImportScreen.Progress    -> ImportProgressScreen(vm = vm)
        ImportScreen.Preview     -> ImportPreviewScreen(vm = vm)
    }
}
