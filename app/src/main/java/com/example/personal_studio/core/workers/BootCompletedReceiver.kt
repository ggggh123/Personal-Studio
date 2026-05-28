package com.example.personal_studio.core.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var gradesPollScheduler: GradesPollScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 1) Timeline 提醒重排(既有)
        val req = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RescheduleRemindersWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req,
        )

        // 2) 成绩轮询从 prefs 重排(goAsync 让出 receiver 线程)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                gradesPollScheduler.rescheduleFromPrefs()
            } finally {
                pending.finish()
            }
        }
    }
}
