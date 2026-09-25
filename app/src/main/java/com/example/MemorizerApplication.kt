package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MemorizerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global Uncaught Exception Handler to catch and report errors gracefully
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MemorizerApplication", "Uncaught exception on thread: ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize core dependencies during Application lifecycle
        try {
            // Pre-warm Room database asynchronously on background thread
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    AppDatabase.getDatabase(this@MemorizerApplication)
                } catch (e: Exception) {
                    Log.e("MemorizerApplication", "Database pre-warm error", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MemorizerApplication", "Scope error during database pre-warm", e)
        }

        try {
            // Pre-create notification channel for daily vocabulary reminders
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Exception) {
            Log.e("MemorizerApplication", "Failed to create notification channel", e)
        }

        try {
            // Guarantee automatic backup runs strictly at 02:00 AM daily
            com.example.data.backup.AutoBackupScheduler.scheduleDaily2AMBackup(this)
        } catch (e: Exception) {
            Log.e("MemorizerApplication", "Failed to schedule 02:00 AM auto backup", e)
        }
    }
}
