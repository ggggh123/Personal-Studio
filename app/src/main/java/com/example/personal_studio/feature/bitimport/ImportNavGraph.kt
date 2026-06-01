package com.example.personal_studio.feature.bitimport

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.feature.bitimport.ui.ImportPreviewScreen
import com.example.personal_studio.feature.bitimport.ui.ImportProgressScreen
import com.example.personal_studio.feature.bitimport.ui.ImportTermPickerScreen
import com.example.personal_studio.feature.bitimport.ui.components.BannerAction
import com.example.personal_studio.feature.bitimport.ui.components.ErrorBanner
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
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
    // error 优先:失败时显示 ErrorBanner 错误屏(可重试/退出),而不是回 Credentials —
    // 后者对已登录用户渲染成 TerminalSplash,会卡死在裸 logo。
    val err = ui.error
    if (err != null) {
        ImportErrorScreen(error = err, onRetry = vm::onRetry, onClose = onClose)
    } else when (ui.currentScreen) {
        ImportScreen.Credentials -> TerminalSplash()
        ImportScreen.TermPicker  -> ImportTermPickerScreen(vm = vm)
        ImportScreen.Progress    -> ImportProgressScreen(vm = vm)
        ImportScreen.Preview     -> ImportPreviewScreen(vm = vm)
    }
}

/** 导入出错屏:显示 ErrorBanner(失败原因 + 重试/打开浏览器/反馈)并提供退出。
 *  取代此前「失败/重试 → 回 Credentials → 渲染成 splash 卡死」的死路。 */
@Composable
private fun ImportErrorScreen(
    error: ImportError,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    WizardScaffold(
        stepNumber = 4,
        totalSteps = 4,
        title = "import error",
        onBack = onClose,
        primaryLabel = "退出",
        onPrimary = onClose,
    ) {
        ErrorBanner(
            error = error,
            onAction = { action ->
                when (action) {
                    BannerAction.Retry -> onRetry()
                    BannerAction.OpenBrowserLoginPage -> context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://sso.bit.edu.cn/cas/login")),
                    )
                    BannerAction.OpenIssueTracker -> context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ggggh123/Personal-Studio/issues")),
                    )
                }
            },
            onDismiss = onClose,
        )
    }
}
