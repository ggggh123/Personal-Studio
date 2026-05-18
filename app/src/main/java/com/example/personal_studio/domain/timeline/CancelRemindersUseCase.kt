package com.example.personal_studio.domain.timeline

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.example.personal_studio.domain.model.TimelineType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cancels every reminder alarm registered for [itemId].
 *
 * We don't know the item's TimelineType at cancellation time (the row may
 * already be deleted), so we fan out across every type and try to look up
 * each slot via FLAG_NO_CREATE — which returns null when no matching
 * PendingIntent exists, letting us short-circuit cleanly. The per-slot
 * `pendingIntent.cancel()` after `alarmManager.cancel(pendingIntent)` flushes
 * the cached intent so a subsequent FLAG_NO_CREATE lookup truthfully reports
 * "no alarm".
 */
@Singleton
class CancelRemindersUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) {
    suspend operator fun invoke(itemId: Long) {
        for (type in TimelineType.values()) {
            for (slot in ScheduleRemindersUseCase.slotsFor(type)) {
                val pendingIntent = ScheduleRemindersUseCase.buildPendingIntent(
                    context, itemId, slot, PendingIntent.FLAG_NO_CREATE,
                ) ?: continue
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
