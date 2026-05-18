package com.example.personal_studio.domain.timeline

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.core.workers.RescheduleRemindersWorker
import javax.inject.Inject

class RescheduleAllUpcomingUseCase @Inject constructor(
    private val wm: WorkManager,
) {
    operator fun invoke() {
        val req = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        wm.enqueueUniqueWork(RescheduleRemindersWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
