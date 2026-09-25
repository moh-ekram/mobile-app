package com.example.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Handles automatic scheduled backups strictly at 02:00 AM.
 * At no other time does automatic background backup occur.
 */
class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "AutoBackupReceiver triggered at ${Date()}")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val backupManager = BackupManager(context, db)
                val userId = "1235"

                // 1. Generate and save local backup files (JSON & CSV)
                val backupResult = backupManager.saveBackupFiles(userId)
                if (backupResult.isSuccess) {
                    Log.d(TAG, "02:00 AM local backup files saved successfully")
                } else {
                    Log.w(TAG, "02:00 AM local backup failed: ${backupResult.exceptionOrNull()?.message}")
                }

                // 2. If user has a linked Google Drive / SAF folder, backup to linked folder too
                val customTreeUri = backupManager.getCustomTreeUri()
                if (!customTreeUri.isNullOrBlank()) {
                    val driveResult = backupManager.backupDirectlyToLinkedFolder(userId)
                    if (driveResult.isSuccess) {
                        Log.d(TAG, "02:00 AM Google Drive linked folder backup overwritten successfully")
                    } else {
                        Log.w(TAG, "02:00 AM Drive backup failed: ${driveResult.exceptionOrNull()?.message}")
                    }
                }

                // Record successful 2:00 AM auto-backup execution timestamp
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong(KEY_LAST_AUTO_BACKUP_TIME, System.currentTimeMillis())
                    .apply()

            } catch (e: Exception) {
                Log.e(TAG, "Error executing 02:00 AM auto-backup", e)
            } finally {
                // Strictly reschedule for the next day's 02:00 AM
                AutoBackupScheduler.scheduleDaily2AMBackup(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "AutoBackupReceiver"
        const val PREFS_NAME = "memorizer_auto_backup_prefs"
        const val KEY_LAST_AUTO_BACKUP_TIME = "last_auto_backup_2am_timestamp"
    }
}

/**
 * Scheduler to guarantee automatic backup runs only at 02:00 AM.
 */
object AutoBackupScheduler {

    private const val ALARM_REQUEST_CODE = 9202

    /**
     * Schedules an alarm to fire strictly at 02:00:00 AM.
     */
    fun scheduleDaily2AMBackup(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AutoBackupReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val now = Calendar.getInstance()
            val targetCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If it is already past 02:00 AM today, schedule for 02:00 AM tomorrow
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val triggerTimeMs = targetCalendar.timeInMillis
            val formattedNextTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimeMs))
            Log.d("AutoBackupScheduler", "Next 02:00 AM auto-backup scheduled for: $formattedNextTime")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("AutoBackupScheduler", "Failed to schedule 02:00 AM auto-backup", e)
        }
    }

    /**
     * Gets the last recorded 02:00 AM auto-backup timestamp.
     */
    fun getLastAutoBackupTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(AutoBackupReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(AutoBackupReceiver.KEY_LAST_AUTO_BACKUP_TIME, 0L)
    }

    /**
     * Human-readable string for the next scheduled auto-backup.
     */
    fun getNextAutoBackupDescription(): String {
        return "Daily at 02:00 AM (শুধুমাত্র রাত ২:০০ টায়)"
    }
}
