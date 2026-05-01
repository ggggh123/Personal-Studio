package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RescheduleRemindersWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: TimelineRepository,
    private val cancel: CancelRemindersUseCase,
    private val schedule: ScheduleRemindersUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val until = now + 24L * 60L * 60L * 1000L
        val items = repo.getUpcomingItems(now, until)
        for (item in items) {
            cancel(item.id)
            schedule(item)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "reschedule_reminders"
    }
}
