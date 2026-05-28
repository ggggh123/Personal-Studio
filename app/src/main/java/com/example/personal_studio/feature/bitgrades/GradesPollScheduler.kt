package com.example.personal_studio.feature.bitgrades

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.core.workers.GradePollWorker
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager 包装。enqueue/cancel + 从 prefs 重排(供 Boot 用)。
 *  `buildPeriodicRequest` 拆为伴生函数以便 JVM 单测无须起 WorkManager。 */
@Singleton
class GradesPollScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GradesSyncPrefs,
) {
    fun enqueue(intervalHours: Int) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicRequest(intervalHours),
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 从 prefs 快照判断:enabled → enqueue 当前间隔;disabled → cancel。
     *  Boot 完成时调用一次即可。 */
    suspend fun rescheduleFromPrefs() {
        val s = prefs.snapshot()
        if (s.enabled) enqueue(s.intervalHours) else cancel()
    }

    companion object {
        const val WORK_NAME = "grades-poll"

        fun buildPeriodicRequest(intervalHours: Int): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return PeriodicWorkRequestBuilder<GradePollWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                .build()
        }
    }
}
