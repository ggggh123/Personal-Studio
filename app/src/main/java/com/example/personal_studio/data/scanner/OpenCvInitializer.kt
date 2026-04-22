package com.example.personal_studio.data.scanner

import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCvInitializer {
    private var initialized = false

    /** Idempotent. Safe to call on main thread at app start — OpenCVLoader.initDebug() just
     *  loads the native lib synchronously and returns whether it succeeded. */
    fun ensureInitialized() {
        if (initialized) return
        val ok = OpenCVLoader.initDebug()
        if (ok) {
            Log.i("OpenCvInit", "OpenCV loaded: ${org.opencv.core.Core.VERSION}")
            initialized = true
        } else {
            Log.e("OpenCvInit", "OpenCV failed to load — scanner features will be broken")
        }
    }
}
