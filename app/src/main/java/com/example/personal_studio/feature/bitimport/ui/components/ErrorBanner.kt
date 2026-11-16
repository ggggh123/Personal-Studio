package com.example.personal_studio.feature.bitimport.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.theme.Tg

enum class BannerAction { Reauthenticate, Retry, OpenIssueTracker }

/** Headline, explanation and the one action that helps, for an import that failed. */
internal data class ImportErrorText(val headline: String, val body: String, val actionLabel: String?, val action: BannerAction?)

internal fun importErrorText(error: ImportError): ImportErrorText = when (error) {
    is ImportError.WrongCredentials -> ImportErrorText("密码未被接受", "学校没有接受保存的密码，请重新登录学校账户。", "重新登录", BannerAction.Reauthenticate)
    is ImportError.AccountLocked -> ImportErrorText("账户已被锁定", "请稍后再试，或先在学校网页上修改密码。", null, null)
    is ImportError.CaptchaRequired -> ImportErrorText("学校要求验证", "需要先在应用内完成学校网页或短信验证，然后再导入。", "去验证", BannerAction.Reauthenticate)
    is ImportError.NetworkFail -> ImportErrorText("连不上学校", "检查网络后重试；校外会自动改用 WebVPN。", "重试", BannerAction.Retry)
    is ImportError.ParseFail -> ImportErrorText("没能读懂课表", "教务系统返回了意料之外的内容（${error.message}），可能是接口改版了。", "反馈问题", BannerAction.OpenIssueTracker)
    is ImportError.NoCurrentTerm -> ImportErrorText("学校没有返回当前学期", "可能正处于学期之间，稍后再试。", "重试", BannerAction.Retry)
    is ImportError.EmptySchedule -> ImportErrorText("这学期没有课", "教务系统里这个学期还没有排课。", "重试", BannerAction.Retry)
    is ImportError.Unexpected -> ImportErrorText("导入失败", error.cause.message ?: "出现了未知错误。", "反馈问题", BannerAction.OpenIssueTracker)
}

@Composable
fun ErrorBanner(
    error: ImportError,
    onAction: (BannerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    val t = importErrorText(error)
    Column(Modifier.fillMaxWidth()) {
        Text(t.headline, style = MaterialTheme.typography.headlineMedium, color = c.text)
        Text(t.body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        if (t.actionLabel != null && t.action != null) {
            TgPillButton(t.actionLabel, onClick = { onAction(t.action) }, style = PillStyle.ACCENT, modifier = Modifier.padding(top = 20.dp))
        } else {
            TgPillButton("关闭", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.padding(top = 20.dp))
        }
    }
}
