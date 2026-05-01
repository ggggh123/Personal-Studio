package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.TimelineNotifier
import com.example.personal_studio.data.local.datastore.NotifPreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: TimelineRepository,
    private val notifier: TimelineNotifier,
    private val notifPrefs: NotifPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        if (itemId < 0) return Result.success()
        val minBefore = inputData.getInt(KEY_MIN_BEFORE, 0)
        val isOverdue = inputData.getBoolean(KEY_IS_OVERDUE, false)

        val item = repo.findById(itemId) ?: return Result.success()
        if (item.isDone) return Result.success()

        val switches = notifPrefs.switches.first()
        val enabled = when (item.type) {
            TimelineType.COURSE -> switches.course
            TimelineType.TASK -> switches.task
            TimelineType.CUSTOM -> switches.custom
        }
        if (!enabled) return Result.success()

        if (isOverdue) notifier.postOverdue(applicationContext, item)
        else notifier.postReminder(applicationContext, item, minBefore)
        return Result.success()
    }

    companion object {
        const val KEY_ITEM_ID = "itemId"
        const val KEY_MIN_BEFORE = "minBefore"
        const val KEY_IS_OVERDUE = "isOverdue"
    }
}
