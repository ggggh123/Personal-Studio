package com.example.personal_studio.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat

object NotificationChannels {
    const val REMINDERS_ID = "timeline_reminders"
    const val OVERDUE_ID = "timeline_overdue"

    fun ensureCreated(context: Context) {
        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS_ID, "提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课程 / DDL / 自定义事件的提前提醒"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(OVERDUE_ID, "已过期", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "DDL 已过期但未标记完成"
            }
        )
    }
}
