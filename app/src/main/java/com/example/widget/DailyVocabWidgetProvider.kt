package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.*

class DailyVocabWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        startFrequentTicker(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startFrequentTicker(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopFrequentTicker()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT_WORD) {
            updateAllWidgets(context)
        }
        startFrequentTicker(context)
    }

    companion object {
        const val ACTION_NEXT_WORD = "com.example.widget.ACTION_NEXT_WORD"
        const val PREFS_NAME = "widget_prefs"
        const val KEY_WIDGET_COURSE_ID = "widget_course_id"
        const val KEY_WIDGET_TAG_FILTER = "widget_tag_filter"
        const val KEY_WIDGET_SIZE = "widget_size"
        const val KEY_WIDGET_FONT_SIZE = "widget_font_size"
        const val KEY_ROTATE_10S = "widget_rotate_10s"
        const val KEY_ROTATE_ON_HOME_RETURN = "widget_rotate_on_home_return"
        const val KEY_LAST_SHOWN_WORD_ID = "last_widget_word_id"

        private var tickerJob: Job? = null

        @Synchronized
        fun startFrequentTicker(context: Context) {
            if (tickerJob?.isActive == true) return
            val appContext = context.applicationContext
            tickerJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    delay(10_000L) // 10 seconds frequency
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val is10sEnabled = prefs.getBoolean(KEY_ROTATE_10S, true)
                    if (is10sEnabled) {
                        try {
                            updateAllWidgets(appContext)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        @Synchronized
        fun stopFrequentTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DailyVocabWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val prefCourseId = prefs.getString(KEY_WIDGET_COURSE_ID, "all") ?: "all"
                    val prefTagFilter = prefs.getString(KEY_WIDGET_TAG_FILTER, "all") ?: "all"
                    val lastShownWordId = prefs.getString(KEY_LAST_SHOWN_WORD_ID, null)

                    val allCourses = db.courseDao().getAllCoursesList()
                    val courseMap = allCourses.associateBy { it.id }

                    var candidateWords = if (prefCourseId != "all" && prefCourseId.isNotBlank()) {
                        val courseWords = db.vocabularyDao().getWordsListByCourse(prefCourseId)
                        if (courseWords.isNotEmpty()) courseWords else db.vocabularyDao().getAllWordsList()
                    } else {
                        db.vocabularyDao().getAllWordsList()
                    }

                    if (prefTagFilter != "all" && prefTagFilter.isNotBlank()) {
                        val filtered = candidateWords.filter { it.status.equals(prefTagFilter, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            candidateWords = filtered
                        }
                    }

                    // Avoid repeating the exact same word as previous cycle if possible
                    val freshCandidates = if (candidateWords.size > 1 && !lastShownWordId.isNullOrBlank()) {
                        val nonRepeating = candidateWords.filter { it.id != lastShownWordId }
                        if (nonRepeating.isNotEmpty()) nonRepeating else candidateWords
                    } else {
                        candidateWords
                    }

                    val word = if (freshCandidates.isNotEmpty()) freshCandidates.random() else null

                    if (word != null) {
                        prefs.edit().putString(KEY_LAST_SHOWN_WORD_ID, word.id).apply()
                    }
                    val views = RemoteViews(context.packageName, R.layout.widget_daily_vocab)

                    val displayCourse = if (word != null) {
                        (courseMap[word.courseId]?.title ?: "GENERAL VOCABULARY").uppercase()
                    } else {
                        "MEMORIZER"
                    }

                    val displayWord = word?.word ?: "Ephemeral"
                    val displayMeaning = word?.meaning ?: "Lasting for a very short time; fleeting or transient."
                    val displayExample = if (!word?.example.isNullOrBlank()) "\"${word?.example}\"" else "Tap to open Memorizer"

                    // Tag details & matching color for Place 1
                    val (tagLabel, tagColorInt) = when (word?.status) {
                        "know" -> Pair("KNOW", Color.parseColor("#059669")) // Emerald
                        "confusion" -> Pair("CONFUSION", Color.parseColor("#D97706")) // Amber
                        "dont_know" -> Pair("DON'T KNOW", Color.parseColor("#E11D48")) // Rose
                        else -> {
                            val groupLabel = if ((word?.group ?: 0) > 0) "GROUP ${word?.group}" else "UNRATED"
                            Pair(groupLabel, Color.parseColor("#4F46E5")) // Indigo
                        }
                    }

                    // Top: Small course name & tag badge
                    views.setTextViewText(R.id.widget_course_title, displayCourse)
                    views.setTextViewText(R.id.widget_badge, tagLabel)
                    views.setTextColor(R.id.widget_badge, tagColorInt)

                    // Place 1 (Word): text color matches tag color
                    views.setTextViewText(R.id.widget_word_title, displayWord)
                    views.setTextColor(R.id.widget_word_title, tagColorInt)

                    // Place 2 (Meaning)
                    views.setTextViewText(R.id.widget_word_meaning, displayMeaning)
                    views.setTextViewText(R.id.widget_word_example, displayExample)

                    // Apply Font Size setting
                    val prefFontSize = prefs.getString(KEY_WIDGET_FONT_SIZE, "medium") ?: "medium"
                    val (titleSize, meaningSize) = when (prefFontSize) {
                        "small" -> Pair(15f, 10.5f)
                        "large" -> Pair(22f, 14f)
                        else -> Pair(18f, 12f)
                    }
                    views.setTextViewTextSize(R.id.widget_word_title, android.util.TypedValue.COMPLEX_UNIT_SP, titleSize)
                    views.setTextViewTextSize(R.id.widget_word_meaning, android.util.TypedValue.COMPLEX_UNIT_SP, meaningSize)

                    // Apply Widget Size setting
                    val prefWidgetSize = prefs.getString(KEY_WIDGET_SIZE, "standard") ?: "standard"
                    if (prefWidgetSize == "compact") {
                        views.setViewVisibility(R.id.widget_word_example, android.view.View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_word_example, android.view.View.VISIBLE)
                    }

                    // Click intent for the whole widget -> open MainActivity
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

                    // Click intent for "Next" button -> cycles to another word
                    val nextIntent = Intent(context, DailyVocabWidgetProvider::class.java).apply {
                        action = ACTION_NEXT_WORD
                    }
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context,
                        1,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_next_button, nextPendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_daily_vocab)
                        views.setTextViewText(R.id.widget_course_title, "MEMORIZER")
                        views.setTextViewText(R.id.widget_word_title, "Ephemeral")
                        views.setTextColor(R.id.widget_word_title, Color.parseColor("#4F46E5"))
                        views.setTextViewText(R.id.widget_word_meaning, "Lasting for a very short time; fleeting or transient.")
                        views.setTextViewText(R.id.widget_badge, "DAILY VOCAB")
                        views.setTextColor(R.id.widget_badge, Color.parseColor("#4F46E5"))
                        views.setTextViewText(R.id.widget_word_example, "Tap to open Memorizer")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
