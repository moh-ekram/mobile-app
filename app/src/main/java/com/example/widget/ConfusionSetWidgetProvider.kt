package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.engine.ConfusionSetCacheManager
import com.example.data.engine.ConfusionSimilarityEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.*
import java.util.Locale

/**
 * 2nd Home Screen Widget: Randomly shuffles and displays Confusion Set groups every 10 seconds.
 */
class ConfusionSetWidgetProvider : AppWidgetProvider() {

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
        newOptions: android.os.Bundle
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
        when (intent.action) {
            ACTION_NEXT_CONFUSION_GROUP -> {
                updateAllWidgets(context, direction = 1)
            }
            ACTION_PREV_CONFUSION_GROUP -> {
                updateAllWidgets(context, direction = -1)
            }
            else -> {
                updateAllWidgets(context)
            }
        }
        startFrequentTicker(context)
    }

    companion object {
        const val ACTION_NEXT_CONFUSION_GROUP = "com.example.widget.ACTION_NEXT_CONFUSION_GROUP"
        const val ACTION_PREV_CONFUSION_GROUP = "com.example.widget.ACTION_PREV_CONFUSION_GROUP"
        const val PREFS_NAME = "confusion_widget_prefs"
        const val KEY_CURRENT_GROUP_INDEX = "current_group_index"
        const val KEY_AUTO_SHUFFLE_ENABLED = "auto_shuffle_enabled"
        const val KEY_SHUFFLE_INTERVAL_SECONDS = "shuffle_interval_seconds"
        const val KEY_MIN_SIMILARITY_THRESHOLD = "min_similarity_threshold"
        const val KEY_MAX_WORDS_PER_GROUP = "max_words_per_group"
        const val KEY_SHOW_MEANING = "show_meaning"
        const val KEY_COLOR_BY_STATUS = "color_by_status"

        private var tickerJob: Job? = null

        fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun isAutoShuffleEnabled(context: Context): Boolean =
            getPrefs(context).getBoolean(KEY_AUTO_SHUFFLE_ENABLED, true)

        fun setAutoShuffleEnabled(context: Context, enabled: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_AUTO_SHUFFLE_ENABLED, enabled).apply()
            if (enabled) {
                stopFrequentTicker()
                startFrequentTicker(context)
            } else {
                stopFrequentTicker()
            }
        }

        fun getShuffleIntervalSeconds(context: Context): Int =
            getPrefs(context).getInt(KEY_SHUFFLE_INTERVAL_SECONDS, 10)

        fun setShuffleIntervalSeconds(context: Context, seconds: Int) {
            getPrefs(context).edit().putInt(KEY_SHUFFLE_INTERVAL_SECONDS, seconds).apply()
            stopFrequentTicker()
            if (isAutoShuffleEnabled(context)) {
                startFrequentTicker(context)
            }
        }

        fun getMinSimilarityThreshold(context: Context): Int =
            getPrefs(context).getInt(KEY_MIN_SIMILARITY_THRESHOLD, 70)

        fun setMinSimilarityThreshold(context: Context, threshold: Int) {
            getPrefs(context).edit().putInt(KEY_MIN_SIMILARITY_THRESHOLD, threshold).apply()
        }

        fun getMaxWordsPerGroup(context: Context): Int =
            getPrefs(context).getInt(KEY_MAX_WORDS_PER_GROUP, 3)

        fun setMaxWordsPerGroup(context: Context, maxWords: Int) {
            getPrefs(context).edit().putInt(KEY_MAX_WORDS_PER_GROUP, maxWords).apply()
        }

        fun isShowMeaning(context: Context): Boolean =
            getPrefs(context).getBoolean(KEY_SHOW_MEANING, true)

        fun setShowMeaning(context: Context, show: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_SHOW_MEANING, show).apply()
        }

        fun isColorByStatus(context: Context): Boolean =
            getPrefs(context).getBoolean(KEY_COLOR_BY_STATUS, true)

        fun setColorByStatus(context: Context, color: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_COLOR_BY_STATUS, color).apply()
        }

        @Synchronized
        fun startFrequentTicker(context: Context) {
            if (!isAutoShuffleEnabled(context)) return
            if (tickerJob?.isActive == true) return
            val appContext = context.applicationContext
            val intervalMs = (getShuffleIntervalSeconds(context) * 1000L).coerceAtLeast(3000L)
            tickerJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    delay(intervalMs)
                    try {
                        updateAllWidgets(appContext, direction = 1)
                    } catch (_: Exception) {}
                }
            }
        }

        @Synchronized
        fun stopFrequentTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        fun updateAllWidgets(context: Context, direction: Int = 0) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, ConfusionSetWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget) ?: return
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId, direction)
                }
            } catch (e: Exception) {
                android.util.Log.e("ConfusionWidget", "Error updating all confusion widgets", e)
            }
        }

        private fun getWordStatusColorInt(rawStatus: String?): Int {
            val normalized = com.example.ui.theme.normalizeWordStatus(rawStatus)
            return when (normalized) {
                "know" -> Color.parseColor("#059669") // EmeraldSuccess
                "dont_know" -> Color.parseColor("#E11D48") // RoseError
                "confusion" -> Color.parseColor("#D97706") // AmberWarning
                else -> Color.parseColor("#4F46E5") // IndigoPrimary
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            direction: Int = 0
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = getPrefs(context)
                    val autoShuffle = isAutoShuffleEnabled(context)
                    val intervalSec = getShuffleIntervalSeconds(context)
                    val minThreshold = getMinSimilarityThreshold(context).toDouble()
                    val maxWords = getMaxWordsPerGroup(context)
                    val showMeaning = isShowMeaning(context)
                    val colorByStatus = isColorByStatus(context)

                    // 1. Fetch confusion groups from persistent local cache or compute fallback
                    var groups = ConfusionSetCacheManager.getCachedGroupsForWidget(context)
                    if (groups.isEmpty()) {
                        val db = AppDatabase.getDatabase(context)
                        val words = db.vocabularyDao().getAllWordsList()
                        if (words.size >= 2) {
                            val candidateWords = if (words.size > 50) words.take(50) else words
                            groups = ConfusionSimilarityEngine.findConfusionGroups(
                                words = candidateWords,
                                config = ConfusionSimilarityEngine.ConfusionConfig(threshold = minThreshold)
                            ) { w, num ->
                                when (num) {
                                    1 -> w.word
                                    2 -> w.meaning
                                    else -> w.synonyms ?: ""
                                }
                            }
                        }
                    }

                    if (groups.isEmpty()) {
                        // Sample fallback if database is empty
                        groups = listOf(
                            ConfusionSimilarityEngine.ConfusionGroup(
                                id = "sample_1",
                                words = listOf(
                                    ConfusionSimilarityEngine.ConfusionWordItem(
                                        wordEntity = com.example.data.model.VocabularyWordEntity(
                                            id = "s1",
                                            word = "Principal",
                                            meaning = "Most important; head of a school",
                                            courseId = "default",
                                            status = "know"
                                        ),
                                        place1 = "Principal",
                                        place2 = "Most important; head of a school",
                                        place3 = "Chief, Head",
                                        comparedRawValue = "Principal",
                                        comparedNormalized = "principal"
                                    ),
                                    ConfusionSimilarityEngine.ConfusionWordItem(
                                        wordEntity = com.example.data.model.VocabularyWordEntity(
                                            id = "s2",
                                            word = "Principle",
                                            meaning = "Fundamental truth, moral rule or law",
                                            courseId = "default",
                                            status = "confusion"
                                        ),
                                        place1 = "Principle",
                                        place2 = "Fundamental truth, moral rule or law",
                                        place3 = "Rule, Canon",
                                        comparedRawValue = "Principle",
                                        comparedNormalized = "principle"
                                    )
                                ),
                                avgSimilarity = 90.0,
                                maxSimilarity = 90.0,
                                representativeReason = "Similar spelling",
                                pairs = emptyList()
                            )
                        )
                    }

                    // 2. Select group: sequential if direction != 0, else random
                    val prevIndex = prefs.getInt(KEY_CURRENT_GROUP_INDEX, -1)
                    val currentIndex = if (groups.size > 1) {
                        if (direction != 0) {
                            (prevIndex + direction + groups.size) % groups.size
                        } else {
                            var next = kotlin.random.Random.nextInt(groups.size)
                            if (next == prevIndex) {
                                next = (next + 1) % groups.size
                            }
                            next
                        }
                    } else {
                        0
                    }
                    prefs.edit().putInt(KEY_CURRENT_GROUP_INDEX, currentIndex).apply()

                    val currentGroup = groups[currentIndex]
                    val views = RemoteViews(context.packageName, R.layout.widget_confusion_set)

                    // 3. Header: Match badge and Group count
                    val matchPct = String.format(Locale.US, " • %.0f%% Match", currentGroup.maxSimilarity)
                    views.setTextViewText(R.id.widget_c_match_score, matchPct)
                    views.setTextViewText(
                        R.id.widget_c_group_info,
                        "Group ${currentIndex + 1} of ${groups.size} (${currentGroup.words.size} Words)"
                    )

                    // Footer text
                    val footerText = if (autoShuffle) {
                        "Shuffles every ${intervalSec}s • Tap to open"
                    } else {
                        "Manual shuffle • Tap to open"
                    }
                    views.setTextViewText(R.id.widget_c_footer, footerText)

                    // 4. Populate Words in Group
                    val word1 = currentGroup.words.getOrNull(0)
                    if (word1 != null) {
                        views.setViewVisibility(R.id.widget_c_word1_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_c_word1_title, word1.place1)
                        val color1 = if (colorByStatus) getWordStatusColorInt(word1.wordEntity.status) else Color.parseColor("#4F46E5")
                        views.setTextColor(R.id.widget_c_word1_title, color1)
                        if (showMeaning) {
                            views.setViewVisibility(R.id.widget_c_word1_meaning, View.VISIBLE)
                            views.setTextViewText(R.id.widget_c_word1_meaning, word1.place2)
                        } else {
                            views.setViewVisibility(R.id.widget_c_word1_meaning, View.GONE)
                        }
                    }

                    val word2 = currentGroup.words.getOrNull(1)
                    if (word2 != null) {
                        views.setViewVisibility(R.id.widget_c_word2_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_c_word2_title, word2.place1)
                        val color2 = if (colorByStatus) getWordStatusColorInt(word2.wordEntity.status) else Color.parseColor("#4F46E5")
                        views.setTextColor(R.id.widget_c_word2_title, color2)
                        if (showMeaning) {
                            views.setViewVisibility(R.id.widget_c_word2_meaning, View.VISIBLE)
                            views.setTextViewText(R.id.widget_c_word2_meaning, word2.place2)
                        } else {
                            views.setViewVisibility(R.id.widget_c_word2_meaning, View.GONE)
                        }
                    }

                    val word3 = if (maxWords >= 3) currentGroup.words.getOrNull(2) else null
                    if (word3 != null) {
                        views.setViewVisibility(R.id.widget_c_word3_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_c_word3_title, word3.place1)
                        val color3 = if (colorByStatus) getWordStatusColorInt(word3.wordEntity.status) else Color.parseColor("#4F46E5")
                        views.setTextColor(R.id.widget_c_word3_title, color3)
                        if (showMeaning) {
                            views.setViewVisibility(R.id.widget_c_word3_meaning, View.VISIBLE)
                            views.setTextViewText(R.id.widget_c_word3_meaning, word3.place2)
                        } else {
                            views.setViewVisibility(R.id.widget_c_word3_meaning, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_c_word3_container, View.GONE)
                    }

                    // 5. Click intent -> opens MainActivity directly into Confusion Set screen!
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("target_route", "confusion_set")
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        101,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_c_root, openAppPendingIntent)

                    // 6. Navigation Buttons
                    val prevIntent = Intent(context, ConfusionSetWidgetProvider::class.java).apply {
                        action = ACTION_PREV_CONFUSION_GROUP
                    }
                    val prevPendingIntent = PendingIntent.getBroadcast(
                        context,
                        102,
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_c_prev_button, prevPendingIntent)

                    val nextIntent = Intent(context, ConfusionSetWidgetProvider::class.java).apply {
                        action = ACTION_NEXT_CONFUSION_GROUP
                    }
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context,
                        103,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_c_next_button, nextPendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    android.util.Log.e("ConfusionWidget", "Error updating widget ID $appWidgetId", e)
                    try {
                        val fallbackViews = RemoteViews(context.packageName, R.layout.widget_confusion_set)
                        fallbackViews.setTextViewText(R.id.widget_c_match_score, " • 90% Match")
                        fallbackViews.setTextViewText(R.id.widget_c_group_info, "Group 1 of 1 (2 Words)")
                        fallbackViews.setViewVisibility(R.id.widget_c_word1_container, View.VISIBLE)
                        fallbackViews.setTextViewText(R.id.widget_c_word1_title, "Principal")
                        fallbackViews.setTextColor(R.id.widget_c_word1_title, Color.parseColor("#059669"))
                        fallbackViews.setTextViewText(R.id.widget_c_word1_meaning, "Most important; head of a school")
                        fallbackViews.setViewVisibility(R.id.widget_c_word2_container, View.VISIBLE)
                        fallbackViews.setTextViewText(R.id.widget_c_word2_title, "Principle")
                        fallbackViews.setTextColor(R.id.widget_c_word2_title, Color.parseColor("#D97706"))
                        fallbackViews.setTextViewText(R.id.widget_c_word2_meaning, "Fundamental truth, moral rule or law")
                        fallbackViews.setViewVisibility(R.id.widget_c_word3_container, View.GONE)

                        val openAppIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("target_route", "confusion_set")
                        }
                        val openAppPendingIntent = PendingIntent.getActivity(
                            context,
                            101,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        fallbackViews.setOnClickPendingIntent(R.id.widget_c_root, openAppPendingIntent)
                        appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
