package com.example.personal_studio.feature.auth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.feature.auth.BitLoginEvent
import com.example.personal_studio.feature.auth.BitLoginViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Dim
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitLoginScreen(
    skipVisible: Boolean,
    onSucceeded: () -> Unit,
    onSkipped: () -> Unit,
    vm: BitLoginViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                BitLoginEvent.Succeeded -> onSucceeded()
                BitLoginEvent.Skipped -> onSkipped()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .scanLines()
            .vignette()
            .systemBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("PERSONAL // STUDIO", color = Phosphor, style = MaterialTheme.typography.displayMedium)
        Text("> 统一身份认证", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Phosphor.copy(alpha = 0.04f))
                .border(1.dp, Phosphor.copy(alpha = 0.45f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "── AUTH ──────────────",
                color = Phosphor.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
            )

            st.error?.let { err ->
                ErrorBox(err) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://login.bit.edu.cn/")),
                        )
                    }
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Phosphor,
                unfocusedBorderColor = Dim,
                cursorColor = Phosphor,
                focusedLabelColor = Phosphor,
                unfocusedLabelColor = FoamDim,
                focusedTextColor = Foam,
                unfocusedTextColor = Foam,
            )
            OutlinedTextField(
                value = st.username, onValueChange = vm::onUsernameChange,
                label = { Text("学号") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            OutlinedTextField(
                value = st.password, onValueChange = vm::onPasswordChange,
                label = { Text("密码") }, singleLine = true,
                visualTransformation = if (st.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = vm::onShowPasswordToggle) {
                        Icon(
                            if (st.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "toggle", tint = FoamMute,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = st.rememberPwd, onCheckedChange = vm::onRememberToggle,
                    colors = CheckboxDefaults.colors(checkedColor = Phosphor),
                )
                Text("记住密码（Keystore 加密）", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = st.networkMode == NetworkMode.LOCAL,
                    onClick = { vm.onNetworkModeChange(NetworkMode.LOCAL) }, label = { Text("校内") },
                )
                FilterChip(
                    selected = st.networkMode == NetworkMode.WEBVPN,
                    onClick = { vm.onNetworkModeChange(NetworkMode.WEBVPN) }, label = { Text("校外") },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (st.loading) FoamDim else Phosphor)
                .background(if (st.loading) Phosphor.copy(alpha = 0f) else Phosphor.copy(alpha = 0.08f))
                .clickable(
                    enabled = !st.loading,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { vm.onLogin() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (st.loading) "验证中 " else "登录 →", color = if (st.loading) FoamDim else Phosphor)
            if (st.loading) BlinkingCursor()
        }

        if (skipVisible) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = vm::onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("跳过", color = FoamDim)
            }
        }
    }
}

@Composable
private fun ErrorBox(err: LoginOutcome, onOpenWeb: () -> Unit) {
    val msg = when (err) {
        LoginOutcome.WrongCredentials -> "学号或密码错误"
        LoginOutcome.AccountLocked -> "账号已锁定,请稍后或网页端处理"
        LoginOutcome.CaptchaRequired -> "需要验证码,请在网页端登录一次后重试"
        is LoginOutcome.NetworkFail -> "连接失败,检查网络或切换校内/校外后重试"
        is LoginOutcome.Unexpected -> "登录失败,请重试"
        LoginOutcome.Success -> return
    }
    Column(Modifier.fillMaxWidth().border(1.dp, Carmine.copy(alpha = 0.6f)).padding(8.dp)) {
        Text(msg, color = Carmine, style = MaterialTheme.typography.labelMedium)
        if (err == LoginOutcome.CaptchaRequired) {
            TextButton(onClick = onOpenWeb) { Text("打开网页端", color = Amber) }
        }
    }
}
