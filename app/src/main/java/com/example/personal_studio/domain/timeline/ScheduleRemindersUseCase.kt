package com.example.personal_studio.domain.timeline

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.personal_studio.core.workers.ReminderAlarmReceiver
import com.example.personal_studio.domain.model.ReminderSlot
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType

/**
 * Schedules per-item reminders via [AlarmManager.setExactAndAllowWhileIdle].
 *
 * Why not WorkManager any more? Doze + Chinese OEM aggressive background-kill
 * batched notifications until the user re-opened the app. exact-while-idle
 * alarms bypass Doze so reminders fire on time even if the process was
 * frozen. See spec 2026-05-01-p4-timeline-design §3.5 / §8 ("厂商 ROM 阻断
 * WorkManager" risk).
 *
 * `suspend` is kept on `invoke` for source compatibility with existing
 * callers (AddTaskViewModel, AddCourseViewModel, TaskDetailViewModel, etc.);
 * scheduling itself is now synchronous.
 */
class ScheduleRemindersUseCase(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(item: TimelineItem) {
        val slots = slotsFor(item.type)
        val now = nowProvider()
        for (slot in slots) {
            val fireAt = if (slot.isOverdue) item.startAt else item.startAt - slot.minBefore * 60_000L
            if (fireAt <= now) continue
            // FLAG_UPDATE_CURRENT never returns null (only FLAG_NO_CREATE does).
            val pendingIntent = buildPendingIntent(
                context, item.id, slot,
                PendingIntent.FLAG_UPDATE_CURRENT,
            )!!
            try {
                if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                    // No exact-alarm permission yet; fall back to inexact-but-Doze-tolerant.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                // System revoked the permission between check and schedule; degrade gracefully.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            }
        }
    }

    companion object {
        fun slotsFor(type: TimelineType): List<ReminderSlot> = when (type) {
            TimelineType.COURSE -> listOf(ReminderSlot(10, false))
            TimelineType.TASK -> listOf(
                ReminderSlot(1440, false),
                ReminderSlot(120, false),
                ReminderSlot(30, false),
                ReminderSlot(0, true),
            )
            TimelineType.CUSTOM -> listOf(ReminderSlot(30, false))
            TimelineType.EXAM -> listOf(
                ReminderSlot(1440, false),
                ReminderSlot(120, false),
                ReminderSlot(30, false),
            )
        }

        /**
         * Build the PendingIntent for a (item, slot) pair.
         *
         * The Intent's `data` URI is critical — without unique data,
         * `PendingIntent.getBroadcast` returns the SAME PendingIntent for
         * different alarms (component + action + data are matched; extras are
         * NOT). Encoding (itemId, minBefore, isOverdue) into the URI gives
         * us per-slot uniqueness even before [ReminderSlot.requestCode]
         * differentiates by hash.
         *
         * `FLAG_IMMUTABLE` is required on Android 12+. Pass [flags] as
         * `FLAG_UPDATE_CURRENT` (schedule) or `FLAG_NO_CREATE` (cancel-lookup).
         */
        fun buildPendingIntent(
            context: Context,
            itemId: Long,
            slot: ReminderSlot,
            flags: Int,
        ): PendingIntent? {
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_REMINDER
                data = Uri.parse(
                    "personalstudio://reminder/$itemId/${slot.minBefore}/${slot.isOverdue}",
                )
                putExtra(ReminderAlarmReceiver.KEY_ITEM_ID, itemId)
                putExtra(ReminderAlarmReceiver.KEY_MIN_BEFORE, slot.minBefore)
                putExtra(ReminderAlarmReceiver.KEY_IS_OVERDUE, slot.isOverdue)
            }
            return PendingIntent.getBroadcast(
                context,
                slot.requestCode(itemId),
                intent,
                flags or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
