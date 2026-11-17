package com.example.personal_studio.data.scanner

import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCvInitializer {
    @Volatile private var initialized = false

    /**
     * Idempotent and thread-safe: loads the native library synchronously the first time.
     * The app warms it on a background thread at start; the scanner calls it again before
     * use, which waits for (or does) that load instead of racing it.
     */
    @Synchronized
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
