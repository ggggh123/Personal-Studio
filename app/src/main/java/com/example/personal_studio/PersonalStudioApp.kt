package com.example.personal_studio

import android.app.Application
import com.example.personal_studio.core.util.CrashLogger
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.scanner.OpenCvInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalStudioApp : Application() {

    @Inject lateinit var prefs: UserPreferencesRepository

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
    }
}
