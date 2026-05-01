package com.example.personal_studio.domain.timeline

import androidx.work.WorkManager
import javax.inject.Inject

class CancelRemindersUseCase @Inject constructor(
    private val wm: WorkManager,
) {
    suspend operator fun invoke(itemId: Long) {
        // Cancel all 4 possible slot work names for this item.
        val all = listOf(
            // COURSE
            "reminder_${itemId}_10_false",
            // TASK
            "reminder_${itemId}_1440_false",
            "reminder_${itemId}_120_false",
            "reminder_${itemId}_30_false",
            "reminder_${itemId}_0_true",
            // CUSTOM
            // (CUSTOM 30_false already covered by TASK above)
        ).distinct()
        for (name in all) wm.cancelUniqueWork(name)
    }
}
