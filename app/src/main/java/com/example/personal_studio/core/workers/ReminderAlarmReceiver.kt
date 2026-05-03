package com.example.personal_studio.core.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.personal_studio.core.notification.TimelineNotifier
import com.example.personal_studio.data.local.datastore.NotifPreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the AlarmManager.setExactAndAllowWhileIdle alarms that replaced
 * the old WorkManager-based ReminderWorker (Doze + Chinese OEM aggressive
 * background-kill made the WM path batch up notifications until the user
 * re-opened the app — see spec 2026-05-01-p4-timeline-design §3.5 / §8).
 *
 * Hilt populates the @Inject fields *before* onReceive runs, so we can use
 * them inside the goAsync()-protected coroutine without races.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: TimelineRepository
    @Inject lateinit var notifier: TimelineNotifier
    @Inject lateinit var notifPrefs: NotifPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(KEY_ITEM_ID, -1L)
        val minBefore = intent.getIntExtra(KEY_MIN_BEFORE, 0)
        val isOverdue = intent.getBooleanExtra(KEY_IS_OVERDUE, false)
        if (itemId < 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = repo.findById(itemId) ?: return@launch
                if (item.isDone) return@launch
                val switches = notifPrefs.switches.first()
                val enabled = when (item.type) {
                    TimelineType.COURSE -> switches.course
                    TimelineType.TASK -> switches.task
                    TimelineType.CUSTOM -> switches.custom
                }
                if (!enabled) return@launch
                if (isOverdue) notifier.postOverdue(context, item)
                else notifier.postReminder(context, item, minBefore)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val KEY_ITEM_ID = "itemId"
        const val KEY_MIN_BEFORE = "minBefore"
        const val KEY_IS_OVERDUE = "isOverdue"
        const val ACTION_REMINDER = "com.example.personal_studio.action.REMINDER"
    }
}
