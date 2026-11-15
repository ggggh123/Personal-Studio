package com.example.personal_studio.feature.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.feature.auth.BitLoginEvent
import com.example.personal_studio.feature.auth.BitLoginStage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** 开机自检引导日志(仅首屏场景逐行淡入,营造终端启动仪式)。 */
private val BOOT_LINES = listOf(
    "> boot personal-studio",
    "> 安全模块就绪",
    "> 等待身份认证…",
)
private const val BOOT_LINE_DELAY_MS = 260L
private const val BOOT_TAIL_DELAY_MS = 320L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitLoginScreen(
    skipVisible: Boolean,
    onSucceeded: (firstSyncPending: Boolean) -> Unit,
    onSkipped: () -> Unit,
    vm: BitLoginViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = st.loading && st.webLoginUrl == null) { vm.onCancelLogin() }
    var slowResponse by remember { mutableStateOf(false) }
    LaunchedEffect(st.stage) {
        slowResponse = false
        if (st.loading && st.webLoginUrl == null) {
            delay(10_000)
            slowResponse = true
        }
    }
    st.webLoginUrl?.let { url ->
        BitWebVerificationDialog(
            url = url,
            username = st.username,
            onFinished = vm::onWebLoginFinished,
        )
    }

    // 守卫场景(skipVisible=false:用户点功能被拦截)直接进表单,不播开机动效。
    // 首屏场景才播一次启动序列。bootDone 用 remember,登录失败重组不会重播。
    var bootDone by remember { mutableStateOf(!skipVisible) }
    var visibleLines by remember { mutableStateOf(if (bootDone) BOOT_LINES.size else 0) }

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                is BitLoginEvent.Succeeded -> onSucceeded(ev.firstSyncPending)
                BitLoginEvent.Skipped -> onSkipped()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (bootDone) return@LaunchedEffect
        for (i in 1..BOOT_LINES.size) {
            delay(BOOT_LINE_DELAY_MS)
            visibleLines = i
        }
        delay(BOOT_TAIL_DELAY_MS)
        bootDone = true
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

        // 开机自检序列:逐行打出 + 末尾闪烁提示符;点击任意处跳过。
        if (!bootDone) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { bootDone = true },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BOOT_LINES.take(visibleLines).forEach { line ->
                    Text(line, color = Phosphor, style = MaterialTheme.typography.labelMedium)
                }
                if (visibleLines > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("> ", color = Phosphor, style = MaterialTheme.typography.labelMedium)
                        BlinkingCursor()
                    }
                }
            }
        }

        // 表单:开机序列完成后淡入(守卫场景 bootDone 初始即 true,直接显示)。
        AnimatedVisibility(visible = bootDone, enter = fadeIn(tween(450))) {
            Column {
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
                        ErrorBox(err, vm::onOpenWebLogin)
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
                        enabled = !st.loading,
                        label = { Text("学号") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = st.password, onValueChange = vm::onPasswordChange,
                        enabled = !st.loading,
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
                    // 网络全自动:校内优先探测 + 校内↔校外按需回退,无需手动选择。
                    Text(
                        "› 自动选择可用的学校访问方式",
                        color = FoamMute, style = MaterialTheme.typography.labelSmall,
                    )
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
                    Text(when (st.stage) {
                        BitLoginStage.CheckingNetwork -> "正在检测连接 "
                        BitLoginStage.Authenticating -> "正在登录学校账户 "
                        BitLoginStage.OpeningBrowser -> "正在准备学校验证 "
                        BitLoginStage.BrowserVerification -> "等待完成学校验证 "
                        BitLoginStage.VerifyingBrowser -> "正在确认验证结果 "
                        null -> "登录 →"
                    }, color = if (st.loading) FoamDim else Phosphor)
                    if (st.loading) BlinkingCursor()
                }

                if (st.loading && st.webLoginUrl == null) {
                    if (slowResponse) {
                        Text("学校响应较慢，可以继续等待或取消后重试。", color = FoamMute,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    TextButton(onClick = vm::onCancelLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("取消本次登录", color = FoamMute)
                    }
                }

                TextButton(
                    onClick = vm::onOpenWebLogin,
                    enabled = !st.loading && st.username.isNotBlank() && st.password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("学校网页验证 / 短信验证", color = Amber) }

                if (skipVisible) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = vm::onSkip, enabled = !st.loading, modifier = Modifier.fillMaxWidth()) {
                        Text("跳过", color = FoamDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBox(err: LoginOutcome, onOpenWeb: () -> Unit) {
    val msg = when (err) {
        LoginOutcome.WrongCredentials -> "学号或密码错误"
        LoginOutcome.AccountLocked -> "账号已锁定,请稍后或网页端处理"
        LoginOutcome.CaptchaRequired -> "学校要求网页或短信验证，请在下方完成验证后重试"
        is LoginOutcome.NetworkFail -> "暂时无法连接学校服务，请检查网络后重试"
        is LoginOutcome.Unexpected -> "登录失败：${err.cause}"
        LoginOutcome.Success -> return
    }
    Column(Modifier.fillMaxWidth().border(1.dp, Carmine.copy(alpha = 0.6f)).padding(8.dp)) {
        Text(msg, color = Carmine, style = MaterialTheme.typography.labelMedium)
        if (err == LoginOutcome.CaptchaRequired) {
            TextButton(onClick = onOpenWeb) { Text("完成学校网页验证", color = Amber) }
        }
    }
}
