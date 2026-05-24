package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.feature.bitgrades.GradesSyncViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesSyncRoute(onClose: () -> Unit, onDone: () -> Unit, vm: GradesSyncViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(st.done) { if (st.done) onDone() }

    WizardScaffold(
        stepNumber = 1, totalSteps = 1, title = "query-grades",
        onBack = onClose,
        primaryLabel = if (st.syncing) "查询中..." else "查询成绩",
        onPrimary = { if (!st.syncing) vm.onSync() },
        primaryEnabled = st.username.isNotBlank() && st.password.isNotBlank() && !st.syncing,
    ) {
        st.error?.let { GradeErrorBanner(it, onDismiss = vm::onDismissError, onRetry = vm::onRetry) }

        OutlinedTextField(st.username, vm::onUsernameChange, label = { Text("学号") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(st.password, vm::onPasswordChange, label = { Text("密码") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (st.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = vm::onShowPasswordToggle) { Text(if (st.showPassword) "隐藏" else "显示") } })
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(st.rememberPwd, vm::onRememberToggle)
            Text("记住密码（Keystore 加密）", color = Foam)
        }
        Row {
            FilterChip(st.networkMode == NetworkMode.LOCAL, { vm.onNetworkModeChange(NetworkMode.LOCAL) }, { Text("校内") })
            Spacer(Modifier.width(8.dp))
            FilterChip(st.networkMode == NetworkMode.WEBVPN, { vm.onNetworkModeChange(NetworkMode.WEBVPN) }, { Text("校外") })
        }
        if (st.progressSteps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            st.progressSteps.forEach { Text("> $it", color = Phosphor) }
        }
    }
}

@Composable
private fun GradeErrorBanner(err: GradesSyncError, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val (color, text) = when (err) {
        GradesSyncError.WrongCredentials -> Carmine to "密码错误，请重新输入。"
        GradesSyncError.AccountLocked -> Carmine to "账号已锁定，请稍后或改密码后重试。"
        GradesSyncError.CaptchaRequired -> Amber to "需要验证码：请到网页端手动登录一次再重试。"
        is GradesSyncError.NetworkFail -> Carmine to "网络异常，请检查网络后重试。"
        is GradesSyncError.ParseFail -> Carmine to "数据解析失败：${err.message}（可能接口改版）。"
        GradesSyncError.EmptyGrades -> Amber to "教务系统暂无成绩数据。"
        is GradesSyncError.Unexpected -> Carmine to (err.cause.message ?: "未知错误。")
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(text, color = color)
        Row {
            TextButton(onRetry) { Text("[重试]", color = color) }
            Spacer(Modifier.weight(1f))
            TextButton(onDismiss) { Text("[关闭]", color = color) }
        }
    }
}
