package com.example.personal_studio.domain.timeline

import androidx.work.WorkManager
import com.example.personal_studio.domain.model.TimelineType
import javax.inject.Inject

class CancelRemindersUseCase @Inject constructor(
    private val wm: WorkManager,
) {
    suspend operator fun invoke(itemId: Long) {
        val names = TimelineType.values()
            .flatMap { ScheduleRemindersUseCase.slotsFor(it) }
            .map { it.workName(itemId) }
            .distinct()
        for (name in names) wm.cancelUniqueWork(name)
    }
}
