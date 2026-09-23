package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(NotificationHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(NotificationHelper.KEY_ENABLED, true)
        if (!isEnabled) return

        val showWord = prefs.getBoolean(NotificationHelper.KEY_SHOW_WORD, true)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var title = "Daily Memorizer 📚"
                var message = "Review your flashcards and articles to keep your streak alive! 🔥"

                if (showWord) {
                    val db = AppDatabase.getDatabase(context)
                    val words = db.vocabularyDao().getAllWordsList()
                    if (words.isNotEmpty()) {
                        val selectedCourses = com.example.widget.DailyVocabWidgetProvider.getSelectedCourseIds(context)
                        val selectedTags = com.example.widget.DailyVocabWidgetProvider.getSelectedTagFilters(context)

                        val courseFiltered = if (selectedCourses.isNotEmpty()) {
                            val cf = words.filter { it.courseId in selectedCourses }
                            if (cf.isNotEmpty()) cf else words
                        } else {
                            words
                        }

                        val tagFiltered = if (selectedTags.isNotEmpty()) {
                            courseFiltered.filter { w ->
                                selectedTags.any { tag ->
                                    if (tag == "flagged") w.isReported
                                    else w.status.equals(tag, ignoreCase = true)
                                }
                            }
                        } else {
                            courseFiltered
                        }

                        val candidates = if (tagFiltered.isNotEmpty()) tagFiltered else courseFiltered
                        val candidate = candidates.randomOrNull() ?: words.random()
                        title = "💡 Word of the Hour: ${candidate.word}"
                        val cleanMeaning = candidate.meaning.replace("\n", " ").trim()
                        val shortMeaning = if (cleanMeaning.length > 90) cleanMeaning.take(87) + "..." else cleanMeaning
                        val exampleText = candidate.example
                        message = if (!exampleText.isNullOrBlank()) {
                            "📖 $shortMeaning\n\"${exampleText.take(65)}\""
                        } else {
                            "📖 $shortMeaning"
                        }
                    }
                }

                NotificationHelper.showNotification(context, title, message)
            } catch (_: Exception) {
                NotificationHelper.showNotification(
                    context,
                    "Daily Memorizer 📚",
                    "Keep your vocabulary streak alive today! 🔥"
                )
            } finally {
                NotificationHelper.rescheduleNext(context)
                pendingResult.finish()
            }
        }
    }
}

object NotificationHelper {
    const val PREFS_NAME = "memorizer_notification_prefs"
    const val KEY_ENABLED = "notifications_enabled"
    const val KEY_HOUR = "notification_hour"
    const val KEY_MINUTE = "notification_minute"
    const val KEY_FREQUENCY = "notification_frequency" // "1h", "2h", "3h", "4h", "6h", "daily"
    const val KEY_SHOW_WORD = "show_word_meaning" // boolean
    const val KEY_STREAK_ALERT = "streak_alert_enabled"
    const val KEY_SOUND_ENABLED = "sound_enabled"

    private const val CHANNEL_ID = "daily_study_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val ALARM_REQUEST_CODE = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "Daily Study Reminders"
                val descriptionText = "Reminders to practice vocabulary, review flashcards, and read articles"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    enableLights(true)
                    lightColor = 0xFF4F46E5.toInt()
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to create notification channel", e)
            }
        }
    }

    fun showNotification(
        context: Context,
        title: String = "Time for Daily Memorizer! 📚",
        message: String = "Review your flashcards and articles today to keep your streak alive! 🔥"
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted")
                    return
                }
            }
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error posting notification", e)
        }
    }

    fun scheduleReminder(
        context: Context,
        enabled: Boolean,
        frequency: String,
        hour: Int,
        minute: Int,
        showWordMeaning: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_FREQUENCY, frequency)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_SHOW_WORD, showWordMeaning)
            .apply()

        if (!enabled) {
            cancelDailyReminder(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val triggerTimeMs = calculateNextTriggerTime(frequency, hour, minute)

        try {
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
        } catch (_: Exception) {
            // Fallback for security restrictions
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    private fun calculateNextTriggerTime(frequency: String, hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        return when (frequency) {
            "1h" -> now + 60 * 60 * 1000L
            "2h" -> now + 2 * 60 * 60 * 1000L
            "3h" -> now + 3 * 60 * 60 * 1000L
            "4h" -> now + 4 * 60 * 60 * 1000L
            "6h" -> now + 6 * 60 * 60 * 1000L
            else -> { // "daily"
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= now) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                calendar.timeInMillis
            }
        }
    }

    fun rescheduleNext(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(KEY_ENABLED, true)
            if (!isEnabled) return
            val frequency = prefs.getString(KEY_FREQUENCY, "2h") ?: "2h"
            val hour = prefs.getInt(KEY_HOUR, 20)
            val minute = prefs.getInt(KEY_MINUTE, 0)
            val showWord = prefs.getBoolean(KEY_SHOW_WORD, true)
            scheduleReminder(context, true, frequency, hour, minute, showWord)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to rescheduleNext", e)
        }
    }

    fun cancelDailyReminder(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    fun triggerInstantSampleNotification(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val showWord = prefs.getBoolean(KEY_SHOW_WORD, true)
            var title = "Daily Memorizer 📚"
            var message = "Review your flashcards and articles to keep your streak alive! 🔥"

            if (showWord) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val words = db.vocabularyDao().getAllWordsList()
                    if (words.isNotEmpty()) {
                        val selectedCourses = com.example.widget.DailyVocabWidgetProvider.getSelectedCourseIds(context)
                        val selectedTags = com.example.widget.DailyVocabWidgetProvider.getSelectedTagFilters(context)

                        val courseFiltered = if (selectedCourses.isNotEmpty()) {
                            val cf = words.filter { it.courseId in selectedCourses }
                            if (cf.isNotEmpty()) cf else words
                        } else {
                            words
                        }

                        val tagFiltered = if (selectedTags.isNotEmpty()) {
                            courseFiltered.filter { w ->
                                selectedTags.any { tag ->
                                    if (tag == "flagged") w.isReported
                                    else w.status.equals(tag, ignoreCase = true)
                                }
                            }
                        } else {
                            courseFiltered
                        }

                        val candidates = if (tagFiltered.isNotEmpty()) tagFiltered else courseFiltered
                        val candidate = candidates.randomOrNull() ?: words.random()
                        title = "💡 Word of the Hour: ${candidate.word}"
                        val cleanMeaning = candidate.meaning.replace("\n", " ").trim()
                        val shortMeaning = if (cleanMeaning.length > 90) cleanMeaning.take(87) + "..." else cleanMeaning
                        val exampleText = candidate.example
                        message = if (!exampleText.isNullOrBlank()) {
                            "📖 $shortMeaning\n\"${exampleText.take(65)}\""
                        } else {
                            "📖 $shortMeaning"
                        }
                    } else {
                        title = "💡 Word of the Hour: Abate"
                        message = "📖 তীব্রতা হ্রাস পাওয়া; To lessen in intensity or subside\n\"The storm began to abate.\""
                    }
                } catch (_: Exception) {
                    title = "💡 Word of the Hour: Abate"
                    message = "📖 তীব্রতা হ্রাস পাওয়া; To lessen in intensity or subside"
                }
            }
            showNotification(context, title, message)
        }
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        scheduleReminder(context, true, "daily", hour, minute, true)
    }

    fun getNotificationTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_HOUR, 20)
        val minute = prefs.getInt(KEY_MINUTE, 0)
        return Pair(hour, minute)
    }

    fun isNotificationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun getNotificationSettings(context: Context): NotificationSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotificationSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            frequency = prefs.getString(KEY_FREQUENCY, "2h") ?: "2h",
            hour = prefs.getInt(KEY_HOUR, 20),
            minute = prefs.getInt(KEY_MINUTE, 0),
            showWordMeaning = prefs.getBoolean(KEY_SHOW_WORD, true)
        )
    }
}

data class NotificationSettings(
    val enabled: Boolean,
    val frequency: String,
    val hour: Int,
    val minute: Int,
    val showWordMeaning: Boolean
)

