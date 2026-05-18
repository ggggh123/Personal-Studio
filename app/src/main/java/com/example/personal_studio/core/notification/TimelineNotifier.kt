package com.example.personal_studio.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.personal_studio.MainActivity
import com.example.personal_studio.R
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineNotifier @Inject constructor() {

    fun postReminder(context: Context, item: TimelineItem, minBefore: Int) {
        NotificationChannels.ensureCreated(context)
        val text = when (item.type) {
            TimelineType.COURSE -> "$minBefore 分钟后${item.location?.let { "  ·  $it" } ?: ""}"
            TimelineType.TASK -> "DDL 还剩 ${humanDuration(minBefore)}"
            TimelineType.CUSTOM -> "$minBefore 分钟后${item.location?.let { "  ·  $it" } ?: ""}"
        }
        post(
            context = context,
            notificationId = (item.id * 100 + minBefore).toInt(),
            channelId = NotificationChannels.REMINDERS_ID,
            title = item.title,
            text = text,
            itemId = item.id,
        )
    }

    fun postOverdue(context: Context, item: TimelineItem) {
        NotificationChannels.ensureCreated(context)
        post(
            context = context,
            notificationId = (item.id * 100 + 999).toInt(),
            channelId = NotificationChannels.OVERDUE_ID,
            title = "已过期 · ${item.title}",
            text = "DDL 已过，请及时处理",
            itemId = item.id,
        )
    }

    private fun post(
        context: Context, notificationId: Int, channelId: String,
        title: String, text: String, itemId: Long,
    ) {
        val deeplink = Uri.parse("personalstudio://timeline/detail/$itemId")
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deeplink
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    private fun humanDuration(minutes: Int): String =
        when {
            minutes >= 1440 -> "${minutes / 1440} 天"
            minutes >= 60 -> "${minutes / 60} 小时"
            else -> "$minutes 分钟"
        }
}
