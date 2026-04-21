package com.example.personal_studio.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "PersonalStudio"
    private const val DIR_NAME = "crash-logs"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "crash-$timestamp.txt")
                file.writeText("Thread: ${thread.name}\n\n$sw")
                Log.e(TAG, "Crash written to ${file.absolutePath}")
            } catch (ioe: Throwable) {
                Log.e(TAG, "Failed to persist crash log", ioe)
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}
