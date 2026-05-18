package com.example.personal_studio.feature.bitimport.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.ErrorBanner
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold

@Composable
fun ImportCredentialsScreen(
    vm: ImportViewModel = hiltViewModel(),
    onClose: () -> Unit,
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    WizardScaffold(
        stepNumber = 1, totalSteps = 4,
        title = "login.bit.edu.cn",
        onBack = onClose,
        primaryLabel = "登录 →",
        primaryEnabled = ui.username.isNotBlank() && ui.password.isNotBlank(),
        onPrimary = vm::onLogin,
    ) {
        if (ui.error != null) {
            ErrorBanner(
                error = ui.error!!,
                onAction = { /* TODO(p5-polish): wire browser-open + issue-link via LocalContext */ },
                onDismiss = vm::onDismissError,
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = ui.username, onValueChange = vm::onUsernameChange,
            label = { Text("学号") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ui.password, onValueChange = vm::onPasswordChange,
            label = { Text("密码") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (ui.showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = vm::onShowPasswordToggle) {
                    Icon(
                        if (ui.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "toggle password visibility",
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ui.rememberPwd, onCheckedChange = vm::onRememberToggle)
            Text("记住密码（用 Keystore 加密保存）")
        }
        Spacer(Modifier.height(16.dp))
        Text("网络模式", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.selectable(
                selected = ui.networkMode == NetworkMode.LOCAL,
                onClick = { vm.onNetworkModeChange(NetworkMode.LOCAL) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = ui.networkMode == NetworkMode.LOCAL, onClick = null)
            Text("校内（直连 login.bit.edu.cn）")
        }
        Row(
            Modifier.selectable(
                selected = ui.networkMode == NetworkMode.WEBVPN,
                onClick = { vm.onNetworkModeChange(NetworkMode.WEBVPN) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = ui.networkMode == NetworkMode.WEBVPN, onClick = null)
            Text("校外（WebVPN 转发）")
        }
    }
}
