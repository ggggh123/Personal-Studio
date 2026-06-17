package com.example.personal_studio

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.personal_studio.core.util.CrashLogger
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.scanner.OpenCvInitializer
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalStudioApp : Application(), Configuration.Provider {

    @Inject lateinit var prefs: UserPreferencesRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var gradesPollScheduler: GradesPollScheduler
    @Inject lateinit var ddlPollScheduler: DdlPollScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        OpenCvInitializer.ensureInitialized()
        CrashLogger.install(this)

        // One-shot: move legacy openrouter_api_key → new api_key if present.
        // Safe to call every launch; no-op once migrated.
        appScope.launch {
            runCatching { prefs.migrateLegacyKeysIfNeeded() }
        }

        // 力停(如 MIUI 从最近任务划掉)会清掉 WorkManager 周期任务,且仅在「开机」或「手动 toggle」
        // 时才重排。这里每次进程启动都从 prefs 重排一次(enabled→enqueue / disabled→cancel),
        // 保证力停后用户一打开 App 就立即恢复后台轮询。ExistingPeriodicWorkPolicy.UPDATE 幂等。
        appScope.launch {
            runCatching {
                gradesPollScheduler.rescheduleFromPrefs()
                ddlPollScheduler.rescheduleFromPrefs()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
