package com.example.personal_studio

import android.app.Application
import com.example.personal_studio.core.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PersonalStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
