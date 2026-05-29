package com.example.personal_studio.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.personal_studio.MainActivity
import com.example.personal_studio.R
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DdlNotifier @Inject constructor() {

    /** 发"N 个新作业"聚合通知;同 id 覆盖。 */
    fun notifyNewDdls(context: Context, newEvents: List<DdlEvent>) {
        if (newEvents.isEmpty()) return
        NotificationChannels.ensureCreated(context)
        val head = newEvents.take(5).joinToString("\n") {
            "• ${it.course?.let { c -> "[$c] " } ?: ""}${it.title}"
        }
        val more = if (newEvents.size > 5) "\n…还有 ${newEvents.size - 5} 个" else ""
        post(
            context = context,
            notificationId = NID_NEW,
            title = "${newEvents.size} 个新作业",
            shortText = newEvents.first().title + if (newEvents.size > 1) " 等" else "",
            bigText = head + more,
            deeplink = "personalstudio://assignments",
        )
    }

    fun notifyStop(context: Context, reason: DdlSyncError) {
        NotificationChannels.ensureCreated(context)
        val text = when (reason) {
            DdlSyncError.WrongCredentials -> "密码错误,凭据已清 — 请打开 App 重新登录"
            DdlSyncError.AccountLocked    -> "账号已锁定,请稍后或修改密码后再启用"
            DdlSyncError.CaptchaRequired  -> "乐学要求验证码,请到网页端手动登录一次后重启"
            DdlSyncError.NeedReview       -> "教务提示未完成评教,请先完成后再启用"
            is DdlSyncError.ParseFail     -> "乐学接口结构可能变化,请等 App 更新"
            else                          -> "未知错误,请打开 App 查看"
        }
        post(
            context = context,
            notificationId = NID_STOP,
            title = "作业自动查询已停止",
            shortText = text,
            bigText = text,
            deeplink = "personalstudio://settings/ddl-poll",
        )
    }

    private fun post(
        context: Context, notificationId: Int,
        title: String, shortText: String, bigText: String, deeplink: String,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deeplink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.DDL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, n) }
    }

    companion object {
        private const val NID_NEW = 5_000_011
        private const val NID_STOP = 5_000_012
    }
}
