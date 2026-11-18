package com.example.personal_studio.feature.auth.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.auth.CaptchaPromptUi
import com.example.personal_studio.feature.auth.SmsPromptUi
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgBottomSheet
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.delay

/** What the SMS sheet says about the code it sent. */
internal fun smsSentText(prompt: SmsPromptUi): String {
    val phone = prompt.phoneTail?.let { "尾号 $it 的手机" } ?: "绑定的手机"
    return when {
        prompt.sentCount == 0 && prompt.sending -> "学校要求短信验证这次登录，正在向${phone}发送验证码…"
        prompt.sentCount == 0 -> "学校要求短信验证这次登录，验证码还没有发出。"
        prompt.stillValid -> "刚才发给${phone}的验证码仍然有效，请输入它。"
        else -> "验证码已发送至${phone}。"
    }
}

/**
 * 短信验证 inside the app, in place of the school's page: the code goes to the bound phone, is
 * checked by the school, and the sign-in continues. Dismissing cancels the sign-in; the school's
 * page stays one tap away.
 */
@Composable
internal fun SmsVerifySheet(
    prompt: SmsPromptUi,
    onSubmit: (code: String, trustDevice: Boolean) -> Unit,
    onResend: () -> Unit,
    onUseWebPage: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Tg.colors
    var code by rememberSaveable { mutableStateOf("") }
    var trust by rememberSaveable { mutableStateOf(true) }
    var left by remember { mutableIntStateOf(0) }
    LaunchedEffect(prompt.sentCount) {
        if (prompt.sentCount == 0) return@LaunchedEffect
        left = prompt.cooldownSeconds
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    val canSubmit = code.length >= 4 && !prompt.checking
    val submit = { if (canSubmit) onSubmit(code, trust) }

    TgBottomSheet(onDismiss = onCancel, header = "短信验证") {
        Text(
            smsSentText(prompt), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TgTextField(
            code, { v -> code = v.filter(Char::isDigit).take(8) },
            modifier = Modifier.padding(top = 16.dp),
            label = "验证码", placeholder = "短信里的数字",
            error = prompt.error, enabled = !prompt.checking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            val ready = !prompt.sending && left == 0 && !prompt.checking
            Text(
                when {
                    prompt.sending -> "正在发送…"
                    left > 0 -> "$left 秒后可重发"
                    else -> "重新发送"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) c.accent else c.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = ready, role = Role.Button, onClick = onResend)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("信任这台手机", style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(
                    "学校允许时，一段时间内在这台手机上登录不再要求短信",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            TgSwitch(trust, { trust = it }, enabled = !prompt.checking)
        }
        TgPillButton(
            if (prompt.checking) "正在验证…" else "验证并登录",
            onClick = submit, enabled = canSubmit || prompt.checking, style = PillStyle.ACCENT,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        WebPageLink(enabled = !prompt.checking, onClick = onUseWebPage)
    }
}

/** The school's picture captcha, asked before the password is checked. */
@Composable
internal fun CaptchaSheet(
    prompt: CaptchaPromptUi,
    onSubmit: (String) -> Unit,
    onUseWebPage: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Tg.colors
    var code by rememberSaveable { mutableStateOf("") }
    val bitmap = remember(prompt) {
        runCatching { BitmapFactory.decodeByteArray(prompt.image, 0, prompt.image.size)?.asImageBitmap() }.getOrNull()
    }
    TgBottomSheet(onDismiss = onCancel, header = "输入验证码") {
        Text(
            "学校要求先输入图片里的字符，再检查密码。",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
        )
        if (bitmap != null) {
            Image(
                bitmap, contentDescription = "验证码图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White),
            )
        } else {
            Text(
                "验证码图片无法显示，请改用学校网页。",
                style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(top = 16.dp),
            )
        }
        TgTextField(
            code, { code = it.trim().take(12) },
            modifier = Modifier.padding(top = 16.dp),
            label = "验证码", enabled = bitmap != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) onSubmit(code) }),
        )
        TgPillButton(
            "继续登录", onClick = { onSubmit(code) }, enabled = code.isNotBlank() && bitmap != null, style = PillStyle.ACCENT,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        WebPageLink(enabled = true, onClick = onUseWebPage)
    }
}

@Composable
private fun WebPageLink(enabled: Boolean, onClick: () -> Unit) {
    val c = Tg.colors
    Text(
        "改用学校网页验证",
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) c.textSecondary else c.textSecondary.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(vertical = 12.dp),
    )
}
