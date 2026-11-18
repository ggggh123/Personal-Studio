package com.example.personal_studio.feature.auth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.feature.auth.BitLoginEvent
import com.example.personal_studio.feature.auth.BitLoginStage
import com.example.personal_studio.feature.auth.BitLoginViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** What the login button says while each stage runs. */
internal fun loginStageText(stage: BitLoginStage?): String = when (stage) {
    BitLoginStage.CheckingNetwork -> "正在检测网络"
    BitLoginStage.Authenticating -> "正在登录"
    BitLoginStage.OpeningBrowser -> "正在打开学校验证"
    BitLoginStage.BrowserVerification -> "等待完成学校验证"
    BitLoginStage.VerifyingBrowser -> "正在确认验证结果"
    BitLoginStage.SmsVerification -> "等待短信验证"
    BitLoginStage.CaptchaVerification -> "等待输入验证码"
    null -> "登录"
}

/** Why the last attempt failed, in a sentence; null on success. */
internal fun loginErrorText(err: LoginOutcome): String? = when (err) {
    LoginOutcome.WrongCredentials -> "学号或密码不对，请检查后重试。"
    LoginOutcome.AccountLocked -> "账户暂时被锁定，请稍后再试，或先在学校网页上处理。"
    LoginOutcome.CaptchaRequired -> "学校要求先完成网页或短信验证。"
    is LoginOutcome.NetworkFail -> "暂时连不上学校，请检查网络后重试。"
    is LoginOutcome.Unexpected -> "登录没有成功：${err.cause}"
    LoginOutcome.Success -> null
}

/**
 * 登录学校账户: the first screen on a fresh install (with 暂不登录) and the guard in front
 * of school features (without it). Native sign-in first; an SMS second step or picture captcha
 * is answered in a sheet here, and the school's own page handles anything else it asks for.
 */
@Composable
fun BitLoginScreen(
    skipVisible: Boolean,
    onSucceeded: (firstSyncPending: Boolean) -> Unit,
    onSkipped: () -> Unit,
    vm: BitLoginViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    KeyboardHandledByInsets()
    BackHandler(enabled = st.loading && !st.waitingForPerson) { vm.onCancelLogin() }
    var slowResponse by remember { mutableStateOf(false) }
    LaunchedEffect(st.stage) {
        slowResponse = false
        if (st.loading && !st.waitingForPerson) {
            delay(10_000)
            slowResponse = true
        }
    }
    st.webLoginUrl?.let { url ->
        BitWebVerificationDialog(url = url, username = st.username, onFinished = vm::onWebLoginFinished)
    }
    st.sms?.let { prompt ->
        SmsVerifySheet(
            prompt, onSubmit = vm::onSmsSubmit, onResend = vm::onSmsResend,
            onUseWebPage = vm::onPromptUseWebPage, onCancel = vm::onCancelLogin,
        )
    }
    st.captcha?.let { prompt ->
        CaptchaSheet(prompt, onSubmit = vm::onCaptchaSubmit, onUseWebPage = vm::onPromptUseWebPage, onCancel = vm::onCancelLogin)
    }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                is BitLoginEvent.Succeeded -> onSucceeded(ev.firstSyncPending)
                BitLoginEvent.Skipped -> onSkipped()
            }
        }
    }
    val canSubmit = !st.loading && st.username.isNotBlank() && st.password.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        HorizonMark(Modifier.enterUp(true, 0))
        Text("课间", style = MaterialTheme.typography.displayMedium, color = c.text, modifier = Modifier.padding(top = 20.dp).enterUp(true, 60))
        Text(
            "登录北京理工大学统一身份认证，课表、作业、考试和成绩会自动同步到这里。",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            modifier = Modifier.padding(top = 8.dp).enterUp(true, 100),
        )

        Column(Modifier.padding(top = 32.dp).enterUp(true, 160), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TgTextField(
                st.username, vm::onUsernameChange,
                label = "学号", placeholder = "例如 1120230000", enabled = !st.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            )
            TgTextField(
                st.password, vm::onPasswordChange,
                label = "密码", enabled = !st.loading,
                visualTransformation = if (st.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canSubmit) vm.onLogin() }),
                trailing = {
                    IconButton(onClick = vm::onShowPasswordToggle) {
                        Icon(
                            if (st.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (st.showPassword) "隐藏密码" else "显示密码",
                            tint = c.textSecondary,
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = st.error?.let(::loginErrorText) != null,
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                val err = st.error
                Column(Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(err?.let(::loginErrorText).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    if (err == LoginOutcome.CaptchaRequired) {
                        TgPillButton("完成学校验证", onClick = vm::onOpenWebLogin, style = PillStyle.ACCENT, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp).enterUp(true, 200),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TgPillButton(
                loginStageText(st.stage) + if (st.loading) "…" else "",
                // Stays solid while a stage runs (it shows the stage), but a second tap does nothing.
                onClick = { if (canSubmit) vm.onLogin() },
                enabled = canSubmit || st.loading,
                style = PillStyle.ACCENT,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(st.loading && !st.waitingForPerson, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (slowResponse) {
                    Text("学校响应有点慢，可以继续等，也可以取消后重试。", style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary, modifier = Modifier.padding(top = 12.dp))
                }
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button, onClick = vm::onCancelLogin)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlinkingCursor()
                    Text("取消这次登录", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }

        TextAction(
            "改用学校网页登录",
            enabled = canSubmit,
            onClick = vm::onOpenWebLogin,
            modifier = Modifier.padding(top = 12.dp).enterUp(true, 240),
        )
        Text(
            "密码只保存在这台手机上，用于自动同步；校内校外会自动选择访问方式。",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp).enterUp(true, 240),
        )

        Spacer(Modifier.height(40.dp))
        if (skipVisible) {
            TextAction(
                "暂不登录，先看看",
                enabled = !st.loading,
                onClick = vm::onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                secondary = true,
            )
        }
    }
}

@Composable
private fun TextAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val c = Tg.colors
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            !enabled -> c.textSecondary.copy(alpha = 0.5f)
            secondary -> c.textSecondary
            else -> c.accent
        },
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

/** The app's mark: a sun half risen over the horizon, in the accent of the hour. */
@Composable
private fun HorizonMark(modifier: Modifier = Modifier) {
    val c = Tg.colors
    Canvas(modifier.size(width = 64.dp, height = 40.dp)) {
        val w = size.width
        val horizon = size.height * 0.72f
        val r = size.height * 0.42f
        drawArc(
            color = c.accent, startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(w / 2 - r, horizon - r), size = Size(r * 2, r * 2),
        )
        drawLine(c.text, Offset(0f, horizon), Offset(w, horizon), strokeWidth = 2.dp.toPx())
        drawArc(
            color = c.textSecondary, startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(w / 2 - r * 1.5f, horizon - r * 1.5f), size = Size(r * 3, r * 3),
            style = Stroke(width = 1.dp.toPx()), alpha = 0.5f,
        )
    }
}
