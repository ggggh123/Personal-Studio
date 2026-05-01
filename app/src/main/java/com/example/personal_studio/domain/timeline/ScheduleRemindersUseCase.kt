package com.example.personal_studio.domain.timeline

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.core.workers.ReminderWorker
import com.example.personal_studio.domain.model.ReminderSlot
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScheduleRemindersUseCase @Inject constructor(
    private val wm: WorkManager,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(item: TimelineItem) {
        val slots = slotsFor(item.type)
        val now = nowProvider()
        for (slot in slots) {
            val fireAt = if (slot.isOverdue) item.startAt else item.startAt - slot.minBefore * 60_000L
            if (fireAt <= now) continue
            val data = Data.Builder()
                .putLong(ReminderWorker.KEY_ITEM_ID, item.id)
                .putInt(ReminderWorker.KEY_MIN_BEFORE, slot.minBefore)
                .putBoolean(ReminderWorker.KEY_IS_OVERDUE, slot.isOverdue)
                .build()
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(fireAt - now, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            wm.enqueueUniqueWork(slot.workName(item.id), ExistingWorkPolicy.REPLACE, req)
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
        }
    }
}
