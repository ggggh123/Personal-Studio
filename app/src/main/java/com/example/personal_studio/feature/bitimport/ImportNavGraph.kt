package com.example.personal_studio.feature.bitimport

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.feature.bitimport.ui.ImportPreviewScreen
import com.example.personal_studio.feature.bitimport.ui.ImportProgressScreen
import com.example.personal_studio.feature.bitimport.ui.components.BannerAction
import com.example.personal_studio.feature.bitimport.ui.components.ErrorBanner
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Tg

/**
 * 从教务导入课表: signs in with the saved account, fetches the current term, shows what
 * would be imported and replaced, and writes only after 确认导入. Not signed in → login.
 */
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
    // An error wins over the step: it replaces the screen with a way out (retry / re-login / close).
    val err = ui.error
    if (err != null) {
        ImportErrorScreen(error = err, onRetry = vm::onRetry, onClose = onClose, onNeedLogin = onNeedLogin)
    } else when (ui.currentScreen) {
        // Credentials is the moment before the first step arrives; the term picker is never
        // entered (imports always use the current term), so both just show the wait.
        ImportScreen.Credentials, ImportScreen.TermPicker -> Starting()
        ImportScreen.Progress -> ImportProgressScreen(vm = vm)
        ImportScreen.Preview -> ImportPreviewScreen(vm = vm)
    }
}

@Composable
private fun Starting() {
    val c = Tg.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正在连接学校", style = MaterialTheme.typography.headlineMedium, color = c.text)
            BlinkingCursor()
        }
    }
}

@Composable
private fun ImportErrorScreen(
    error: ImportError,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    val context = LocalContext.current
    WizardScaffold(
        stepNumber = 3,
        totalSteps = 3,
        title = "导入课表",
        caption = "没有完成",
        onBack = onClose,
        primaryLabel = "退出",
        onPrimary = onClose,
    ) {
        ErrorBanner(
            error = error,
            onAction = { action ->
                when (action) {
                    BannerAction.Retry -> onRetry()
                    BannerAction.Reauthenticate -> onNeedLogin()
                    BannerAction.OpenIssueTracker -> runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ggggh123/Personal-Studio/issues")))
                    }
                }
            },
            onDismiss = onClose,
        )
    }
}
