package com.example.personal_studio.core.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val req = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RescheduleRemindersWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
