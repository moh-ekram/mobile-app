package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.backup.AutoBackupScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            NotificationHelper.createNotificationChannel(context)
            if (NotificationHelper.isNotificationEnabled(context)) {
                NotificationHelper.rescheduleNext(context)
            }
            // Guarantee 02:00 AM auto-backup is scheduled after device boot
            AutoBackupScheduler.scheduleDaily2AMBackup(context)
        }
    }
}
