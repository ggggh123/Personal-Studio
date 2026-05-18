package com.example.personal_studio.feature.bitimport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam

enum class BannerAction { OpenBrowserLoginPage, Retry, OpenIssueTracker }

@Composable
fun ErrorBanner(
    error: ImportError,
    onAction: (BannerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val content = when (error) {
        is ImportError.WrongCredentials -> ErrorContent(Carmine, "密码错误", "请重新输入。", null, null)
        is ImportError.AccountLocked    -> ErrorContent(Carmine, "账号已锁定", "请稍后或修改密码后重试。", null, null)
        is ImportError.CaptchaRequired  -> ErrorContent(Amber, "需要验证码",
            "教务系统此前多次失败，启用了验证码。访问网页端手动登录一次即可恢复。",
            "[打开浏览器]", BannerAction.OpenBrowserLoginPage)
        is ImportError.NetworkFail      -> ErrorContent(Carmine, "网络异常", "请检查网络后重试。", "[重试]", BannerAction.Retry)
        is ImportError.ParseFail        -> ErrorContent(Carmine, "数据解析失败",
            "教务系统返回了未预期的数据：${error.message}。可能是接口改版。",
            "[反馈]", BannerAction.OpenIssueTracker)
        is ImportError.NoCurrentTerm    -> ErrorContent(Amber, "未返回当前学期", "请手动选择学期。", null, null)
        is ImportError.EmptySchedule    -> ErrorContent(Amber, "课表为空", "该学期教务系统中无课程数据。", null, null)
        is ImportError.Unexpected       -> ErrorContent(Carmine, "未知错误",
            error.cause.message ?: "请重试或联系开发者", "[反馈]", BannerAction.OpenIssueTracker)
    }
    Column(
        Modifier.fillMaxWidth().border(1.dp, content.color, RoundedCornerShape(4.dp))
            .background(content.color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(content.headline, color = content.color, style = MaterialTheme.typography.titleSmall)
        Text(content.body, color = Foam, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth()) {
            if (content.actionLabel != null && content.action != null) {
                TextButton(onClick = { onAction(content.action) }) {
                    Text(content.actionLabel, color = content.color)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("[关闭]", color = content.color) }
        }
    }
}

private data class ErrorContent(
    val color: Color, val headline: String, val body: String,
    val actionLabel: String?, val action: BannerAction?,
)
