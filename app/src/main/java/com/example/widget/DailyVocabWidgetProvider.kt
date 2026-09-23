package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
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
        const val KEY_WIDGET_COURSE_IDS = "widget_course_ids"
        const val KEY_WIDGET_TAG_FILTER = "widget_tag_filter"
        const val KEY_WIDGET_SIZE = "widget_size"
        const val KEY_WIDGET_FONT_SIZE = "widget_font_size"
        const val KEY_ROTATE_10S = "widget_rotate_10s"
        const val KEY_ROTATE_ON_HOME_RETURN = "widget_rotate_on_home_return"
        const val KEY_LAST_SHOWN_WORD_ID = "last_widget_word_id"

        fun getSelectedCourseIds(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_WIDGET_COURSE_IDS, null)
            return when {
                raw != null -> {
                    if (raw.isBlank() || raw == "all") emptySet()
                    else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                else -> {
                    val legacy = prefs.getString(KEY_WIDGET_COURSE_ID, "all") ?: "all"
                    if (legacy == "all" || legacy.isBlank()) emptySet() else setOf(legacy)
                }
            }
        }

        fun saveSelectedCourseIds(context: Context, courseIds: Set<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val joined = if (courseIds.isEmpty()) "all" else courseIds.joinToString(",")
            val single = if (courseIds.size == 1) courseIds.first() else if (courseIds.isEmpty()) "all" else courseIds.first()
            prefs.edit()
                .putString(KEY_WIDGET_COURSE_IDS, joined)
                .putString(KEY_WIDGET_COURSE_ID, single)
                .apply()
        }

        fun getSelectedTagFilters(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_WIDGET_TAG_FILTER, "all") ?: "all"
            if (raw == "all" || raw.isBlank()) return emptySet()
            return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }

        fun saveSelectedTagFilters(context: Context, tags: Set<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = if (tags.isEmpty()) "all" else tags.joinToString(",")
            prefs.edit().putString(KEY_WIDGET_TAG_FILTER, value).apply()
        }

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
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, DailyVocabWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget) ?: return
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                android.util.Log.e("DailyVocabWidget", "Error updating all widgets", e)
            }
        }

        data class ResponsiveWidgetConfig(
            val titleSp: Float,
            val meaningSp: Float,
            val exampleSp: Float,
            val courseSp: Float,
            val badgeSp: Float,
            val nextSp: Float,
            val hPadDp: Int,
            val vPadDp: Int,
            val headerBottomPadDp: Int,
            val meaningTopPadDp: Int,
            val exampleTopPadDp: Int,
            val maxTitleLines: Int,
            val maxMeaningLines: Int,
            val maxExampleLines: Int,
            val showExample: Boolean
        )

        private fun calculateResponsiveConfig(
            widthDp: Int,
            heightDp: Int,
            prefWidgetSize: String
        ): ResponsiveWidgetConfig {
            var effW = if (widthDp > 0) widthDp else 240
            var effH = if (heightDp > 0) heightDp else 130

            if (prefWidgetSize == "large") {
                effW = maxOf(effW, 300)
                effH = maxOf(effH, 200)
            } else if (prefWidgetSize == "compact") {
                effW = minOf(effW, 180)
                effH = minOf(effH, 90)
            }

            return when {
                // Jumbo / Full-Screen / Large Tablet / 4x4+ (effH >= 240 && effW >= 240)
                effH >= 240 && effW >= 240 -> ResponsiveWidgetConfig(
                    titleSp = 36f,
                    meaningSp = 20f,
                    exampleSp = 16.5f,
                    courseSp = 14f,
                    badgeSp = 12f,
                    nextSp = 14f,
                    hPadDp = 18,
                    vPadDp = 16,
                    headerBottomPadDp = 8,
                    meaningTopPadDp = 8,
                    exampleTopPadDp = 6,
                    maxTitleLines = 2,
                    maxMeaningLines = 6,
                    maxExampleLines = 3,
                    showExample = true
                )
                // Extra Large / 4x3 / 3x3 / Tall (effH >= 170 || (effH >= 140 && effW >= 280))
                effH >= 170 || (effH >= 140 && effW >= 280) -> ResponsiveWidgetConfig(
                    titleSp = 28f,
                    meaningSp = 17f,
                    exampleSp = 14.5f,
                    courseSp = 12.5f,
                    badgeSp = 11f,
                    nextSp = 12.5f,
                    hPadDp = 15,
                    vPadDp = 12,
                    headerBottomPadDp = 6,
                    meaningTopPadDp = 6,
                    exampleTopPadDp = 5,
                    maxTitleLines = 2,
                    maxMeaningLines = 4,
                    maxExampleLines = 3,
                    showExample = true
                )
                // Wide / Medium (e.g. 4x2 or 3x2) (effW >= 220 && effH >= 100)
                effW >= 220 && effH >= 100 -> ResponsiveWidgetConfig(
                    titleSp = 23f,
                    meaningSp = 15f,
                    exampleSp = 12.5f,
                    courseSp = 11f,
                    badgeSp = 10f,
                    nextSp = 11.5f,
                    hPadDp = 13,
                    vPadDp = 9,
                    headerBottomPadDp = 4,
                    meaningTopPadDp = 4,
                    exampleTopPadDp = 3,
                    maxTitleLines = 2,
                    maxMeaningLines = 3,
                    maxExampleLines = 2,
                    showExample = true
                )
                // Standard 2x2 / 3x1 (effH >= 85)
                effH >= 85 -> ResponsiveWidgetConfig(
                    titleSp = 20f,
                    meaningSp = 13.5f,
                    exampleSp = 11.5f,
                    courseSp = 10f,
                    badgeSp = 9f,
                    nextSp = 10.5f,
                    hPadDp = 11,
                    vPadDp = 8,
                    headerBottomPadDp = 3,
                    meaningTopPadDp = 3,
                    exampleTopPadDp = 2,
                    maxTitleLines = 2,
                    maxMeaningLines = 3,
                    maxExampleLines = 1,
                    showExample = true
                )
                // Very Compact 2x1
                else -> ResponsiveWidgetConfig(
                    titleSp = 17f,
                    meaningSp = 12f,
                    exampleSp = 10.5f,
                    courseSp = 9.5f,
                    badgeSp = 8.5f,
                    nextSp = 9.5f,
                    hPadDp = 9,
                    vPadDp = 6,
                    headerBottomPadDp = 2,
                    meaningTopPadDp = 2,
                    exampleTopPadDp = 2,
                    maxTitleLines = 1,
                    maxMeaningLines = 2,
                    maxExampleLines = 1,
                    showExample = false
                )
            }
        }

        private fun applyResponsiveStyling(
            context: Context,
            views: RemoteViews,
            config: ResponsiveWidgetConfig,
            prefFontSize: String
        ) {
            val fontFactor = when (prefFontSize) {
                "small" -> 0.85f
                "large" -> 1.25f
                "extra_large", "huge" -> 1.5f
                else -> 1.0f
            }

            val finalTitleSp = config.titleSp * fontFactor
            val finalMeaningSp = config.meaningSp * fontFactor
            val finalExampleSp = config.exampleSp * fontFactor
            val finalCourseSp = config.courseSp * (0.6f + fontFactor * 0.4f)
            val finalBadgeSp = config.badgeSp * (0.6f + fontFactor * 0.4f)
            val finalNextSp = config.nextSp * (0.6f + fontFactor * 0.4f)

            val density = context.resources.displayMetrics.density
            val hPadPx = (config.hPadDp * density).toInt()
            val vPadPx = (config.vPadDp * density).toInt()
            views.setViewPadding(R.id.widget_root, hPadPx, vPadPx, hPadPx, vPadPx)

            val headerBottomPx = (config.headerBottomPadDp * density).toInt()
            views.setViewPadding(R.id.widget_header, 0, 0, 0, headerBottomPx)

            val meaningTopPx = (config.meaningTopPadDp * density).toInt()
            views.setViewPadding(R.id.widget_word_meaning, 0, meaningTopPx, 0, 0)

            val exampleTopPx = (config.exampleTopPadDp * density).toInt()
            views.setViewPadding(R.id.widget_word_example, 0, exampleTopPx, 0, 0)

            views.setInt(R.id.widget_word_title, "setMaxLines", config.maxTitleLines)
            views.setInt(R.id.widget_word_meaning, "setMaxLines", config.maxMeaningLines)
            views.setInt(R.id.widget_word_example, "setMaxLines", config.maxExampleLines)

            views.setTextViewTextSize(R.id.widget_word_title, TypedValue.COMPLEX_UNIT_SP, finalTitleSp)
            views.setTextViewTextSize(R.id.widget_word_meaning, TypedValue.COMPLEX_UNIT_SP, finalMeaningSp)
            views.setTextViewTextSize(R.id.widget_word_example, TypedValue.COMPLEX_UNIT_SP, finalExampleSp)
            views.setTextViewTextSize(R.id.widget_course_title, TypedValue.COMPLEX_UNIT_SP, finalCourseSp)
            views.setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, finalBadgeSp)
            views.setTextViewTextSize(R.id.widget_next_button, TypedValue.COMPLEX_UNIT_SP, finalNextSp)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prefFontSize = prefs.getString(KEY_WIDGET_FONT_SIZE, "medium") ?: "medium"
                val prefWidgetSize = prefs.getString(KEY_WIDGET_SIZE, "standard") ?: "standard"

                // Read current widget physical dimensions from launcher options
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val optMinW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val optMaxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                val optMinH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val optMaxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)

                val rawWidthDp = if (isLandscape) {
                    if (optMaxW > 0) optMaxW else optMinW
                } else {
                    if (optMinW > 0) optMinW else optMaxW
                }

                val rawHeightDp = if (isLandscape) {
                    if (optMinH > 0) optMinH else optMaxH
                } else {
                    if (optMaxH > 0) optMaxH else optMinH
                }

                // If resized by user, launchers update min/max dimensions. Take maximum resolved bounds so font scales up appropriately.
                val resolvedW = maxOf(rawWidthDp, optMinW, optMaxW)
                val resolvedH = maxOf(rawHeightDp, optMinH, optMaxH)

                val config = calculateResponsiveConfig(resolvedW, resolvedH, prefWidgetSize)

                try {
                    val db = AppDatabase.getDatabase(context)
                    val selectedCourseIds = getSelectedCourseIds(context)
                    val selectedTags = getSelectedTagFilters(context)
                    val lastShownWordId = prefs.getString(KEY_LAST_SHOWN_WORD_ID, null)

                    val allCourses = db.courseDao().getAllCoursesList()
                    val courseMap = allCourses.associateBy { it.id }
                    val allWords = db.vocabularyDao().getAllWordsList()

                    // Robust Multi-Course Filtering: filter words belonging to any of the selected courses
                    val courseWords = if (selectedCourseIds.isNotEmpty()) {
                        val filtered = allWords.filter { it.courseId in selectedCourseIds }
                        if (filtered.isNotEmpty()) filtered else allWords
                    } else {
                        allWords
                    }

                    // Filter by multi-status tags (know, confusion, dont_know, unrated, flagged)
                    val filteredByTag = if (selectedTags.isNotEmpty()) {
                        courseWords.filter { word ->
                            selectedTags.any { tag ->
                                when (tag) {
                                    "flagged" -> word.isReported
                                    else -> word.status.equals(tag, ignoreCase = true)
                                }
                            }
                        }
                    } else {
                        courseWords
                    }

                    // Strict fallback hierarchy guarantees candidate list is NEVER empty
                    val finalCandidates = when {
                        filteredByTag.isNotEmpty() -> filteredByTag
                        courseWords.isNotEmpty() -> courseWords
                        allWords.isNotEmpty() -> allWords
                        else -> com.example.data.repository.SampleData.sampleWords
                    }

                    // Sequential cycle or non-repeating selection
                    val currentIndex = finalCandidates.indexOfFirst { it.id == lastShownWordId }
                    val word = if (finalCandidates.size > 1) {
                        if (currentIndex >= 0) {
                            finalCandidates[(currentIndex + 1) % finalCandidates.size]
                        } else {
                            finalCandidates.random()
                        }
                    } else {
                        finalCandidates.firstOrNull()
                    }

                    if (word != null) {
                        prefs.edit().putString(KEY_LAST_SHOWN_WORD_ID, word.id).apply()
                    }
                    val views = RemoteViews(context.packageName, R.layout.widget_daily_vocab)

                    val displayCourse = if (word != null) {
                        (courseMap[word.courseId]?.title ?: "VOCABULARY").uppercase()
                    } else {
                        "MEMORIZER"
                    }

                    val displayWord = word?.word ?: "Ephemeral"
                    val displayMeaning = word?.meaning ?: "Lasting for a very short time; fleeting or transient."

                    // Tag details & matching color for Place 1
                    val (tagLabel, tagColorInt) = when (word?.status) {
                        "know" -> Pair("KNOW", Color.parseColor("#059669")) // Emerald
                        "confusion" -> Pair("CONFUSION", Color.parseColor("#D97706")) // Amber
                        "dont_know" -> Pair("DON'T KNOW", Color.parseColor("#E11D48")) // Rose
                        else -> {
                            val grp = word?.group?.trim() ?: ""
                            val groupLabel = if (grp.isNotBlank()) {
                                if (grp.all { it.isDigit() }) "GROUP $grp" else grp.uppercase()
                            } else "UNRATED"
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

                    // Apply fully responsive typography, paddings, line counts & spacing
                    applyResponsiveStyling(context, views, config, prefFontSize)

                    // Apply Widget Size setting & hide example completely if absent to remove empty void
                    val hasExample = word != null && !word.example.isNullOrBlank() && prefWidgetSize != "compact" && config.showExample
                    if (hasExample) {
                        views.setViewVisibility(R.id.widget_word_example, View.VISIBLE)
                        views.setTextViewText(R.id.widget_word_example, "\"${word?.example?.trim()}\"")
                    } else {
                        views.setViewVisibility(R.id.widget_word_example, View.GONE)
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

                        applyResponsiveStyling(context, views, config, prefFontSize)

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

