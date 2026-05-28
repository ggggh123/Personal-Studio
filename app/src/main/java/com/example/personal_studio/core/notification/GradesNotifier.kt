package com.example.personal_studio.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.personal_studio.MainActivity
import com.example.personal_studio.R
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradesNotifier @Inject constructor() {

    /** 发"N 门新成绩"聚合通知;同 id 覆盖,不堆叠多条。 */
    fun notifyNewGrades(context: Context, newEntries: List<GradeEntryEntity>) {
        if (newEntries.isEmpty()) return
        NotificationChannels.ensureCreated(context)
        val title = "${newEntries.size} 门新成绩"
        val head = newEntries.take(5).joinToString("\n") { "• ${it.courseName} ${it.score}" }
        val more = if (newEntries.size > 5) "\n…还有 ${newEntries.size - 5} 门" else ""
        post(
            context = context,
            notificationId = NID_NEW_GRADES,
            title = title,
            shortText = "${newEntries.first().courseName} ${newEntries.first().score}" +
                if (newEntries.size > 1) " 等" else "",
            bigText = head + more,
            deeplink = "personalstudio://grades",
        )
    }

    /** 后台自动查询被停下的提示;同 id 覆盖。 */
    fun notifyStop(context: Context, reason: GradesSyncError) {
        NotificationChannels.ensureCreated(context)
        val text = when (reason) {
            GradesSyncError.WrongCredentials -> "密码错误,凭据已清 — 请打开 App 重新登录"
            GradesSyncError.AccountLocked    -> "账号已锁定,请稍后或修改密码后再启用"
            GradesSyncError.CaptchaRequired  -> "教务系统要求验证码,请到网页端手动登录一次后重启"
            GradesSyncError.NeedReview       -> "教务提示未完成评教,请先评教后再启用"
            is GradesSyncError.ParseFail     -> "教务接口结构可能变化,请等 App 更新"
            else                             -> "未知错误,请打开 App 查看"
        }
        post(
            context = context,
            notificationId = NID_STOP,
            title = "成绩自动查询已停止",
            shortText = text,
            bigText = text,
            deeplink = "personalstudio://settings/grades-poll",
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
        val n = NotificationCompat.Builder(context, NotificationChannels.GRADES_ID)
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
        private const val NID_NEW_GRADES = 5_000_001
        private const val NID_STOP = 5_000_002
    }
}
