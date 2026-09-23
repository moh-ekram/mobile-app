package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.foundation.BorderStroke
import com.example.data.model.ArticleEntity
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import com.example.ui.theme.*
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.VolumeUp
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import org.json.JSONArray
import org.json.JSONObject

internal fun isWordMatchingFlashcardStatus(word: VocabularyWordEntity, selectedStatuses: Set<String>): Boolean {
    val normStatus = when (word.status.lowercase().trim()) {
        "know", "learned" -> "know"
        "confusion", "confused" -> "confusion"
        "dont_know", "needs review", "dontknow" -> "dont_know"
        else -> "unrated"
    }
    // Requirement: Only words marked as Confused, Know, Don't Know should be included in the games
    if (normStatus !in setOf("know", "confusion", "dont_know")) {
        return false
    }
    if (selectedStatuses.isEmpty() || "all" in selectedStatuses) {
        return true
    }
    return selectedStatuses.any { selected ->
        when (selected) {
            "all" -> true
            "know" -> normStatus == "know"
            "confusion", "confused" -> normStatus == "confusion"
            "dont_know" -> normStatus == "dont_know"
            else -> false
        }
    }
}

object QuizTestStats {
    private const val PREFS_NAME = "quiz_test_stats_prefs"
    private const val KEY_TOTAL_ANSWERED = "quiz_test_total_answered"
    private const val KEY_TOTAL_CORRECT = "quiz_test_total_correct"
    private const val KEY_ATTEMPTS = "quiz_test_attempts"

    fun getStats(context: Context, courseId: String, words: List<VocabularyWordEntity>): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyAnswered = if (courseId == "all") KEY_TOTAL_ANSWERED else "${KEY_TOTAL_ANSWERED}_$courseId"
        val keyCorrect = if (courseId == "all") KEY_TOTAL_CORRECT else "${KEY_TOTAL_CORRECT}_$courseId"
        val keyAttempts = if (courseId == "all") KEY_ATTEMPTS else "${KEY_ATTEMPTS}_$courseId"

        var total = prefs.getInt(keyAnswered, 0)
        var correct = prefs.getInt(keyCorrect, 0)
        var attempts = prefs.getInt(keyAttempts, 0)

        // Seed with existing word stats if no attempts recorded in prefs yet
        if (total == 0) {
            val relevantWords = if (courseId == "all") words else words.filter { it.courseId == courseId }
            val wCorrect = relevantWords.sumOf { it.quizCorrectCount }
            val wIncorrect = relevantWords.sumOf { it.quizIncorrectCount }
            if (wCorrect + wIncorrect > 0) {
                correct = wCorrect
                total = wCorrect + wIncorrect
                attempts = if (total > 0) 1 else 0
            }
        }

        return Triple(correct, total, attempts)
    }

    fun recordAttempt(context: Context, correctCount: Int, attemptedCount: Int, courseId: String) {
        if (attemptedCount <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyAnswered = if (courseId == "all") KEY_TOTAL_ANSWERED else "${KEY_TOTAL_ANSWERED}_$courseId"
        val keyCorrect = if (courseId == "all") KEY_TOTAL_CORRECT else "${KEY_TOTAL_CORRECT}_$courseId"
        val keyAttempts = if (courseId == "all") KEY_ATTEMPTS else "${KEY_ATTEMPTS}_$courseId"

        val prevAnswered = prefs.getInt(keyAnswered, 0)
        val prevCorrect = prefs.getInt(keyCorrect, 0)
        val prevAttempts = prefs.getInt(keyAttempts, 0)

        val newAnswered = prevAnswered + attemptedCount
        val newCorrect = prevCorrect + correctCount
        val newAttempts = prevAttempts + 1

        val editor = prefs.edit()
        editor.putInt(keyAnswered, newAnswered)
        editor.putInt(keyCorrect, newCorrect)
        editor.putInt(keyAttempts, newAttempts)

        if (courseId != "all") {
            val globalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0) + attemptedCount
            val globalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0) + correctCount
            val globalAttempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1
            editor.putInt(KEY_TOTAL_ANSWERED, globalAnswered)
            editor.putInt(KEY_TOTAL_CORRECT, globalCorrect)
            editor.putInt(KEY_ATTEMPTS, globalAttempts)
        }
        editor.apply()
    }
}

object ColumnMatchStats {
    private const val PREFS_NAME = "column_match_stats_prefs"
    private const val KEY_TOTAL_MATCHES = "cm_total_matches"
    private const val KEY_TOTAL_MISTAKES = "cm_total_mistakes"
    private const val KEY_ROUNDS = "cm_rounds"

    fun getStats(context: Context, courseId: String = "all"): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyMatches = if (courseId == "all") KEY_TOTAL_MATCHES else "${KEY_TOTAL_MATCHES}_$courseId"
        val keyMistakes = if (courseId == "all") KEY_TOTAL_MISTAKES else "${KEY_TOTAL_MISTAKES}_$courseId"
        val keyRounds = if (courseId == "all") KEY_ROUNDS else "${KEY_ROUNDS}_$courseId"

        var matches = prefs.getInt(keyMatches, 0)
        var mistakes = prefs.getInt(keyMistakes, 0)
        var rounds = prefs.getInt(keyRounds, 0)

        if (matches == 0 && mistakes == 0 && rounds == 0 && courseId != "all") {
            matches = prefs.getInt(KEY_TOTAL_MATCHES, 0)
            mistakes = prefs.getInt(KEY_TOTAL_MISTAKES, 0)
            rounds = prefs.getInt(KEY_ROUNDS, 0)
        }
        return Triple(matches, mistakes, rounds)
    }

    fun recordRound(context: Context, matches: Int, mistakes: Int, courseId: String = "all") {
        if (matches <= 0 && mistakes <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val globalMatches = prefs.getInt(KEY_TOTAL_MATCHES, 0) + matches
        val globalMistakes = prefs.getInt(KEY_TOTAL_MISTAKES, 0) + mistakes
        val globalRounds = prefs.getInt(KEY_ROUNDS, 0) + 1
        editor.putInt(KEY_TOTAL_MATCHES, globalMatches)
        editor.putInt(KEY_TOTAL_MISTAKES, globalMistakes)
        editor.putInt(KEY_ROUNDS, globalRounds)

        if (courseId != "all") {
            val keyMatches = "${KEY_TOTAL_MATCHES}_$courseId"
            val keyMistakes = "${KEY_TOTAL_MISTAKES}_$courseId"
            val keyRounds = "${KEY_ROUNDS}_$courseId"
            editor.putInt(keyMatches, prefs.getInt(keyMatches, 0) + matches)
            editor.putInt(keyMistakes, prefs.getInt(keyMistakes, 0) + mistakes)
            editor.putInt(keyRounds, prefs.getInt(keyRounds, 0) + 1)
        }
        editor.apply()
    }
}

object ArcherAimStats {
    private const val PREFS_NAME = "archer_aim_stats_prefs"
    private const val KEY_TOTAL_HITS = "archer_total_hits"
    private const val KEY_TOTAL_MISSES = "archer_total_misses"
    private const val KEY_ROUNDS = "archer_rounds"

    fun getStats(context: Context, courseId: String = "all"): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyHits = if (courseId == "all") KEY_TOTAL_HITS else "${KEY_TOTAL_HITS}_$courseId"
        val keyMisses = if (courseId == "all") KEY_TOTAL_MISSES else "${KEY_TOTAL_MISSES}_$courseId"
        val keyRounds = if (courseId == "all") KEY_ROUNDS else "${KEY_ROUNDS}_$courseId"

        var hits = prefs.getInt(keyHits, 0)
        var misses = prefs.getInt(keyMisses, 0)
        var rounds = prefs.getInt(keyRounds, 0)

        if (hits == 0 && misses == 0 && rounds == 0 && courseId != "all") {
            hits = prefs.getInt(KEY_TOTAL_HITS, 0)
            misses = prefs.getInt(KEY_TOTAL_MISSES, 0)
            rounds = prefs.getInt(KEY_ROUNDS, 0)
        }
        return Triple(hits, misses, rounds)
    }

    fun recordRound(context: Context, hits: Int, misses: Int, courseId: String = "all") {
        if (hits <= 0 && misses <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val globalHits = prefs.getInt(KEY_TOTAL_HITS, 0) + hits
        val globalMisses = prefs.getInt(KEY_TOTAL_MISSES, 0) + misses
        val globalRounds = prefs.getInt(KEY_ROUNDS, 0) + 1
        editor.putInt(KEY_TOTAL_HITS, globalHits)
        editor.putInt(KEY_TOTAL_MISSES, globalMisses)
        editor.putInt(KEY_ROUNDS, globalRounds)

        if (courseId != "all") {
            val keyHits = "${KEY_TOTAL_HITS}_$courseId"
            val keyMisses = "${KEY_TOTAL_MISSES}_$courseId"
            val keyRounds = "${KEY_ROUNDS}_$courseId"
            editor.putInt(keyHits, prefs.getInt(keyHits, 0) + hits)
            editor.putInt(keyMisses, prefs.getInt(keyMisses, 0) + misses)
            editor.putInt(keyRounds, prefs.getInt(keyRounds, 0) + 1)
        }
        editor.apply()
    }
}

fun segmentGraphemeClusters(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val boundary = java.text.BreakIterator.getCharacterInstance(java.util.Locale("bn"))
    boundary.setText(text)
    val result = mutableListOf<String>()
    var start = boundary.first()
    var end = boundary.next()
    while (end != java.text.BreakIterator.DONE) {
        val cluster = text.substring(start, end)
        result.add(cluster)
        start = end
        end = boundary.next()
    }
    return result
}

@Composable
fun GameStatisticsCard(
    title: String = "Statistics",
    correctCount: Int,
    incorrectCount: Int,
    totalCount: Int = correctCount + incorrectCount,
    modifier: Modifier = Modifier
) {
    val accuracy = if (totalCount > 0) (correctCount * 100 / totalCount) else 0
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title & Optional Accuracy Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(IndigoLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                }

                if (totalCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndigoLight)
                            .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "$accuracy% Accuracy",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                }
            }

            // Graphical Rate (Circle Percentage) & Ratio (Segmented Bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Circular Percentage Progress Gauge
                Box(
                    modifier = Modifier.size(74.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        // Background circular track
                        drawCircle(
                            color = Color(0xFFE2E8F0),
                            radius = radius,
                            style = Stroke(width = strokeWidth)
                        )
                        // Foreground circular progress arc
                        val sweepAngle = (accuracy.coerceIn(0, 100) / 100f) * 360f
                        if (sweepAngle > 0f) {
                            drawArc(
                                color = if (accuracy >= 70) Color(0xFF10B981) else if (accuracy >= 40) Color(0xFF6366F1) else Color(0xFFF59E0B),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$accuracy%",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Text(
                            text = "Rate",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateMuted
                        )
                    }
                }

                // 2. Graphic Ratio Bar & Ratio Information
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accuracy Ratio",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateText
                        )
                        Text(
                            text = if (totalCount > 0) "$correctCount : $incorrectCount" else "0 : 0",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }

                    // Graphical Segmented Ratio Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        if (totalCount > 0) {
                            if (correctCount > 0 && incorrectCount > 0) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(correctCount.toFloat())
                                            .fillMaxHeight()
                                            .background(Color(0xFF22C55E))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(incorrectCount.toFloat())
                                            .fillMaxHeight()
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            } else if (correctCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF22C55E))
                                )
                            } else if (incorrectCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }
                    }

                    // Ratio Breakdown Percentages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✓ $correctCount (${if (totalCount > 0) accuracy else 0}%)",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "✕ $incorrectCount (${if (totalCount > 0) (100 - accuracy) else 0}%)",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorizedQuestionText(
    question: String,
    colorChoice: String = "red",
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    modifier: Modifier = Modifier
) {
    val highlightColor = Color(0xFFDC2626) // Fixed red

    val annotated = remember(question, highlightColor) {
        buildAnnotatedString {
            val regex = Regex("\\[([^\\]]+)\\]")
            var lastIndex = 0
            val matches = regex.findAll(question).toList()

            if (matches.isEmpty()) {
                append(question)
            } else {
                for (match in matches) {
                    val range = match.range
                    if (range.first > lastIndex) {
                        append(question.substring(lastIndex, range.first))
                    }
                    val wordInside = match.groupValues[1]
                    val isBengali = isBengaliText(wordInside)
                    pushStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = if (isBengali) KalpurushFontFamily else PoppinsFontFamily,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
                    )
                    append(wordInside)
                    pop()
                    lastIndex = range.last + 1
                }
                if (lastIndex < question.length) {
                    append(question.substring(lastIndex))
                }
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = PoppinsFontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = SlateText,
        lineHeight = 22.sp,
        modifier = modifier
    )
}

@Composable
fun GamePracticeScreen(
    games: List<GamePracticeEntity>,
    questions: List<QuestionBankEntity>,
    onCompleteQuiz: (score: Int, total: Int) -> Unit,
    onRecordGameAnswer: (questionId: String, isCorrect: Boolean) -> Unit = { _, _ -> },
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "all",
    onRecordWordQuizAnswer: (wordId: String, isCorrect: Boolean) -> Unit = { _, _ -> },
    articles: List<ArticleEntity> = emptyList(),
    activeArticle: ArticleEntity? = null,
    words: List<VocabularyWordEntity> = emptyList(),
    onSelectArticle: (ArticleEntity?) -> Unit = {},
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit = { _, _, _, _ -> },
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit = {},
    onRateWord: (wordId: String, status: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onSectionChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statsUpdateTrigger by remember { mutableIntStateOf(0) }
    var wordHighlightColor by remember { mutableStateOf("red") } // "red" (default), "blue", "green"

    // Top Section: null means list menu of categories; non-null opens dedicated section
    var selectedSection by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSection) {
        onSectionChange(selectedSection)
    }

    // Active Quiz State (Vertical Single-Page Quiz List)
    var activeQuestions by remember { mutableStateOf<List<QuizQuestionItem>>(emptyList()) }
    var userAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var currentScore by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    // Quiz test (Course Vocabulary Quiz) Config - Multi-Selection Grid Style
    var courseQuizCourseIds by remember(activeCourseId, courses) {
        mutableStateOf(
            if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                setOf(activeCourseId)
            } else if (courses.isNotEmpty()) {
                setOf(courses.first().id)
            } else {
                setOf("all")
            }
        )
    }
    var courseQuizStatusFilters by remember { mutableStateOf(setOf("all")) }
    var courseQuizGroupFilters by remember { mutableStateOf(setOf("all")) }
    var courseQuizCount by remember { mutableIntStateOf(0) } // 0 means All / Infinite / No limit

    // Column Match Config & State
    var columnMatchCourseIds by remember(activeCourseId, courses) {
        mutableStateOf(
            if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                setOf(activeCourseId)
            } else if (courses.isNotEmpty()) {
                setOf(courses.first().id)
            } else {
                setOf("all")
            }
        )
    }
    var columnMatchStatusFilters by remember { mutableStateOf(setOf("all")) }
    var columnMatchPairCount by remember { mutableIntStateOf(6) }
    var activeColumnMatch by remember { mutableStateOf<ColumnMatchGameState?>(null) }

    // Archer Aim Config & State
    var archerCourseIds by remember(activeCourseId, courses) {
        mutableStateOf(
            if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                setOf(activeCourseId)
            } else if (courses.isNotEmpty()) {
                setOf(courses.first().id)
            } else {
                setOf("all")
            }
        )
    }
    var archerStatusFilters by remember { mutableStateOf(setOf("all")) }
    var archerWordCount by remember { mutableIntStateOf(10) }
    var archerGameMode by remember { mutableStateOf(ArcherGameMode.LETTER_AIM) }
    var activeArcherGame by remember { mutableStateOf<ArcherGameState?>(null) }

    // Game Mode Filtering & Count Config
    var gameStatusFilter by remember { mutableStateOf("all") } // "all", "correct", "incorrect", "not_studied"
    var gameQuestionCount by remember { mutableIntStateOf(10) }

    // Question Bank Config - Multi-Selection Grid Style
    var qbFilters1 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbFilters2 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbFilters3 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbQuestionCount by remember { mutableIntStateOf(5) }

    fun getPlaceValueAndLabel(w: VocabularyWordEntity, placeNumber: Int): Pair<String, String> {
        // First check customPlacesJson
        if (!w.customPlacesJson.isNullOrBlank()) {
            try {
                val json = JSONObject(w.customPlacesJson)
                val entries = mutableListOf<Pair<String, String>>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "").trim()
                    if (v.isNotBlank()) {
                        entries.add(k to v)
                    }
                }
                // 1. Explicit key match for "place$placeNumber" or "place $placeNumber"
                for ((k, v) in entries) {
                    val kClean = k.lowercase().replace("_", "").replace(" ", "").replace("-", "")
                    if (kClean == "place$placeNumber" || kClean.startsWith("place$placeNumber")) {
                        return k to v
                    }
                }
                // 2. Semantic key match based on standard place semantic conventions
                for ((k, v) in entries) {
                    val kLower = k.lowercase()
                    when (placeNumber) {
                        2 -> if (kLower.contains("meaning") || kLower.contains("definition") || kLower.contains("translation")) return k to v
                        3 -> if (kLower.contains("example") || kLower.contains("sentence") || kLower.contains("synonym")) return k to v
                        4 -> if (kLower.contains("synonym") || kLower.contains("example") || kLower.contains("sentence")) return k to v
                        5 -> if (kLower.contains("form") || kLower.contains("derivative") || kLower.contains("extra") || kLower.contains("mnemonic") || kLower.contains("trick")) return k to v
                    }
                }
                // 3. Positional fallback from parsed custom columns (1-indexed)
                if (placeNumber - 1 in entries.indices) {
                    val entry = entries[placeNumber - 1]
                    return entry.first to entry.second
                }
            } catch (_: Exception) {}
        }

        // Entity fields fallback
        return when (placeNumber) {
            1 -> "Word" to w.word.trim()
            2 -> "Meaning" to w.meaning.trim()
            3 -> {
                val value = (w.synonyms ?: w.example ?: w.extraMeaning ?: "").trim()
                val lbl = if (!w.synonyms.isNullOrBlank()) "Synonyms" else if (!w.example.isNullOrBlank()) "Example" else "Place 3"
                lbl to value
            }
            4 -> {
                val value = (w.example ?: w.synonyms ?: "").trim()
                val lbl = if (!w.example.isNullOrBlank()) "Example Sentence" else if (!w.synonyms.isNullOrBlank()) "Synonyms" else "Place 4"
                lbl to value
            }
            5 -> {
                val value = (w.extraWord ?: w.mnemonic ?: "").trim()
                val lbl = if (!w.extraWord.isNullOrBlank()) "Forms / Derivative" else if (!w.mnemonic.isNullOrBlank()) "Mnemonic" else "Place 5"
                lbl to value
            }
            else -> "Place $placeNumber" to ""
        }
    }

    fun getPlace(w: VocabularyWordEntity, placeNumber: Int): String {
        return getPlaceValueAndLabel(w, placeNumber).second
    }

    fun getPlace1(w: VocabularyWordEntity): String = getPlace(w, 1)
    fun getPlace2(w: VocabularyWordEntity): String = getPlace(w, 2)

    fun getPlaceHeaderLabel(placeNumber: Int, targetCourseIds: Set<String> = emptySet()): String {
        // 1. Check CourseEntity columnHeadersJson
        val effectiveCourseIds = if (targetCourseIds.isEmpty() || "all" in targetCourseIds) {
            courses.map { it.id }.toSet()
        } else {
            targetCourseIds
        }

        for (cId in effectiveCourseIds) {
            val course = courses.find { it.id == cId }
            val headersJson = course?.columnHeadersJson
            if (!headersJson.isNullOrBlank()) {
                try {
                    val array = JSONArray(headersJson)
                    for (i in 0 until array.length()) {
                        val rawH = array.getString(i).trim()
                        val lowerH = rawH.lowercase()
                        if (lowerH.startsWith("place$placeNumber") || lowerH.startsWith("place $placeNumber")) {
                            val clean = if (rawH.contains(":")) rawH.substringAfter(":").trim() else rawH.replace(Regex("""(?i)^place\s*\d+\s*[-_:]?\s*"""), "").trim()
                            if (clean.isNotBlank()) return "Place$placeNumber: $clean"
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Check sample word
        val sampleWord = words.firstOrNull {
            (targetCourseIds.isEmpty() || "all" in targetCourseIds || it.courseId in targetCourseIds) &&
            getPlaceValueAndLabel(it, placeNumber).second.isNotBlank()
        } ?: words.firstOrNull { getPlaceValueAndLabel(it, placeNumber).second.isNotBlank() }

        val rawLabel = if (sampleWord != null) {
            getPlaceValueAndLabel(sampleWord, placeNumber).first
        } else {
            if (placeNumber == 1) "Word" else "Meaning"
        }

        val cleanLabel = rawLabel.replace(Regex("""(?i)^place\s*\d+\s*[-_:]?\s*"""), "").trim()
        val finalLabel = if (cleanLabel.isNotBlank()) cleanLabel else (if (placeNumber == 1) "Word" else "Meaning")
        return "Place$placeNumber: $finalLabel"
    }

    fun getExplanationForWord(w: VocabularyWordEntity): String {
        val (p4Label, p4Val) = getPlaceValueAndLabel(w, 4)
        val (p5Label, p5Val) = getPlaceValueAndLabel(w, 5)

        val hasP4 = p4Val.isNotBlank()
        val hasP5 = p5Val.isNotBlank()

        if (hasP4 || hasP5) {
            val parts = mutableListOf<String>()
            if (hasP4) {
                val prefix = if (p4Label.isNotBlank() && !p4Val.startsWith(p4Label, ignoreCase = true)) "[$p4Label]\n" else ""
                parts.add("$prefix$p4Val")
            }
            if (hasP5) {
                val prefix = if (p5Label.isNotBlank() && !p5Val.startsWith(p5Label, ignoreCase = true)) "[$p5Label]\n" else ""
                parts.add("$prefix$p5Val")
            }
            return parts.joinToString("\n\n")
        }

        // Fallback: If neither place4 nor place5 has data, provide place2 & place3!
        val (p2Label, p2Val) = getPlaceValueAndLabel(w, 2)
        val (p3Label, p3Val) = getPlaceValueAndLabel(w, 3)
        val fallbackParts = mutableListOf<String>()
        if (p2Val.isNotBlank()) {
            val prefix = if (p2Label.isNotBlank() && !p2Val.startsWith(p2Label, ignoreCase = true)) "[$p2Label]\n" else ""
            fallbackParts.add("$prefix$p2Val")
        }
        if (p3Val.isNotBlank()) {
            val prefix = if (p3Label.isNotBlank() && !p3Val.startsWith(p3Label, ignoreCase = true)) "[$p3Label]\n" else ""
            fallbackParts.add("$prefix$p3Val")
        }
        return fallbackParts.joinToString("\n\n")
    }

    fun startColumnMatchGame() {
        val targetCourseIds = if (columnMatchCourseIds.isNotEmpty() && "all" !in columnMatchCourseIds) {
            columnMatchCourseIds
        } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
            setOf(activeCourseId)
        } else if (courses.isNotEmpty()) {
            setOf(courses.first().id)
        } else {
            words.map { it.courseId }.filter { it.isNotBlank() }.distinct().take(1).toSet()
        }

        val courseWords = if (targetCourseIds.isNotEmpty()) {
            words.filter { it.courseId in targetCourseIds }
        } else {
            words
        }

        val statusFiltered = courseWords.filter { word ->
            isWordMatchingFlashcardStatus(word, columnMatchStatusFilters)
        }

        val eligibleWords = statusFiltered.filter {
            getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank()
        }

        if (eligibleWords.isEmpty()) return

        val count = if (columnMatchPairCount > 0) columnMatchPairCount.coerceAtMost(eligibleWords.size) else eligibleWords.size
        val selectedWords = eligibleWords.shuffled().take(count)

        val leftList = selectedWords.map {
            ColumnMatchItem(
                wordId = it.id,
                text = getPlace1(it).removePrefix("[").removeSuffix("]").trim()
            )
        }.shuffled()

        val rightList = selectedWords.map {
            ColumnMatchItem(
                wordId = it.id,
                text = getPlace2(it).trim()
            )
        }.shuffled()

        activeColumnMatch = ColumnMatchGameState(
            leftItems = leftList,
            rightItems = rightList,
            totalPairs = selectedWords.size
        )
    }

    fun startArcherAimGame() {
        val targetCourseIds = if (archerCourseIds.isNotEmpty() && "all" !in archerCourseIds) {
            archerCourseIds
        } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
            setOf(activeCourseId)
        } else if (courses.isNotEmpty()) {
            setOf(courses.first().id)
        } else {
            words.map { it.courseId }.filter { it.isNotBlank() }.distinct().take(1).toSet()
        }

        val courseWords = if (targetCourseIds.isNotEmpty()) {
            words.filter { it.courseId in targetCourseIds }
        } else {
            words
        }

        val statusFiltered = courseWords.filter { word ->
            isWordMatchingFlashcardStatus(word, archerStatusFilters)
        }

        val eligibleWords = statusFiltered.filter {
            getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank()
        }

        if (eligibleWords.isEmpty()) return

        val count = if (archerWordCount > 0) archerWordCount.coerceAtMost(eligibleWords.size) else eligibleWords.size
        val selectedWords = eligibleWords.shuffled().take(count)

        val place1Label = getPlaceHeaderLabel(1, archerCourseIds)
        val place2Label = getPlaceHeaderLabel(2, archerCourseIds)

        val targets = if (archerGameMode == ArcherGameMode.MEANING_MATCH) {
            // Mode 2: Meaning Match (3 Options from Place 2 words)
            val allMeanings = eligibleWords.map { getPlace2(it).trim() }.filter { it.isNotBlank() }.distinct()
            selectedWords.mapNotNull { word ->
                val p1 = getPlace1(word).removePrefix("[").removeSuffix("]").trim()
                val p2 = getPlace2(word).trim()
                if (p1.isBlank() || p2.isBlank()) return@mapNotNull null

                val distractors = allMeanings.filter { it != p2 }.shuffled().take(2)
                val finalDistractors = if (distractors.size < 2) {
                    val fallback = listOf("Alternative Meaning", "Secondary Definition").filter { it != p2 }
                    (distractors + fallback).take(2)
                } else distractors

                val options = (listOf(p2) + finalDistractors).shuffled()
                val correctIndex = options.indexOf(p2)

                ArcherWordTarget(
                    wordEntity = word,
                    place1Text = p1,
                    place2Text = p2,
                    targetSegment = p2,
                    targetChar = p2.firstOrNull() ?: ' ',
                    options = options,
                    correctOptionIndex = correctIndex,
                    place1Label = place1Label,
                    place2Label = place2Label
                )
            }
        } else {
            // Mode 1: Letter / Syllable Aim with intact Bangla 'কার' signs
            selectedWords.mapNotNull { word ->
                val p1 = getPlace1(word).removePrefix("[").removeSuffix("]").trim()
                val p2 = getPlace2(word).trim()
                val segments = segmentGraphemeClusters(p1)
                val candidateSegments = segments.filter { seg ->
                    seg.isNotBlank() && seg.any { c -> c.isLetter() || c in '\u0980'..'\u09FF' }
                }.distinct()
                val targetSegment = if (candidateSegments.isNotEmpty()) {
                    candidateSegments.random()
                } else {
                    segments.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
                }

                ArcherWordTarget(
                    wordEntity = word,
                    place1Text = p1,
                    place2Text = p2,
                    targetSegment = targetSegment,
                    targetChar = targetSegment.firstOrNull() ?: ' ',
                    segments = segments,
                    place1Label = place1Label,
                    place2Label = place2Label
                )
            }
        }

        if (targets.isEmpty()) return

        activeArcherGame = ArcherGameState(
            targets = targets,
            mode = archerGameMode,
            currentIndex = 0,
            hits = 0,
            misses = 0,
            streak = 0,
            isRevealed = false,
            isFinished = false
        )
    }

    // Intercept back button: 
    // 1. Reading article -> back to article list
    // 2. Active archer aim -> back to archer aim config
    // 3. Active column match -> back to column match config
    // 4. In quiz questions -> back to section config
    // 5. In game section -> back to games menu
    // 6. In games menu -> back to home screen
    BackHandler {
        if (activeArticle != null) {
            onSelectArticle(null)
        } else if (activeArcherGame != null) {
            activeArcherGame = null
        } else if (activeColumnMatch != null) {
            activeColumnMatch = null
        } else if (activeQuestions.isNotEmpty()) {
            activeQuestions = emptyList()
            isQuizCompleted = false
        } else if (selectedSection != null) {
            selectedSection = null
        } else {
            onBack()
        }
    }

    fun startQuizForCategory(section: String, statusFilter: String = "all", maxCount: Int? = null) {
        val list = when (section) {
            "course_quiz" -> {
                val targetCourseIds = if (courseQuizCourseIds.isNotEmpty() && "all" !in courseQuizCourseIds) {
                    courseQuizCourseIds
                } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                    setOf(activeCourseId)
                } else if (courses.isNotEmpty()) {
                    setOf(courses.first().id)
                } else {
                    words.map { it.courseId }.filter { it.isNotBlank() }.distinct().take(1).toSet()
                }

                val courseWords = if (targetCourseIds.isNotEmpty()) {
                    words.filter { it.courseId in targetCourseIds }
                } else {
                    words
                }

                val groupFiltered = if (courseQuizGroupFilters.isEmpty() || "all" in courseQuizGroupFilters) {
                    courseWords
                } else {
                    courseWords.filter { it.group in courseQuizGroupFilters }
                }
                val statusFiltered = groupFiltered.filter { word ->
                    isWordMatchingFlashcardStatus(word, courseQuizStatusFilters)
                }
                val eligibleWords = statusFiltered
                    .filter { getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank() }

                val count = if (courseQuizCount > 0) courseQuizCount else eligibleWords.size
                val selectedWords = if (count > 0 && count < eligibleWords.size) {
                    eligibleWords.shuffled().take(count)
                } else {
                    eligibleWords.shuffled() // infinite / all words in course
                }

                selectedWords.map { word ->
                    // Question text strictly uses place1 word
                    val p1 = getPlace1(word).removePrefix("[").removeSuffix("]").trim()
                    // Answer is strictly place2 word
                    val p2 = getPlace2(word).trim()

                    // Options strictly come ONLY from the selected course of this word, ONLY place2 words!
                    val coursePlace2List = words
                        .filter { it.courseId == word.courseId }
                        .map { getPlace2(it).trim() }
                        .filter { it.isNotBlank() && it != p2 }
                        .distinct()

                    val distractors = coursePlace2List.shuffled().take(3)
                    val allOpts = (distractors + p2).shuffled()
                    val opt1 = allOpts.getOrElse(0) { p2 }
                    val opt2 = allOpts.getOrElse(1) { "" }
                    val opt3 = allOpts.getOrElse(2) { "" }
                    val opt4 = allOpts.getOrElse(3) { "" }

                    QuizQuestionItem(
                        id = word.id,
                        sheetType = "course_quiz",
                        question = "The [$p1] is suitable for?",
                        opt1 = opt1,
                        opt2 = opt2,
                        opt3 = opt3,
                        opt4 = opt4,
                        answer = p2,
                        explanation = getExplanationForWord(word),
                        lastAttemptStatus = word.lastQuizStatus
                    )
                }
            }
            "question_bank" -> {
                val filtered = questions.filter { q ->
                    (qbFilters1.isEmpty() || (q.filter1 != null && q.filter1 in qbFilters1)) &&
                    (qbFilters2.isEmpty() || (q.filter2 != null && q.filter2 in qbFilters2)) &&
                    (qbFilters3.isEmpty() || (q.filter3 != null && q.filter3 in qbFilters3))
                }.shuffled().take(qbQuestionCount)
                filtered.map { it.toQuizItem() }
            }
            else -> emptyList()
        }
        activeQuestions = list
        userAnswers = emptyMap()
        currentScore = 0
        isQuizCompleted = false
    }

    val isReadingActiveArticle = selectedSection == "read_article" && activeArticle != null
    val isPlayingArcherGame = selectedSection == "archer_aim" && activeArcherGame != null
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(
                horizontal = if (isReadingActiveArticle || isPlayingArcherGame) 0.dp else 16.dp,
                vertical = if (isReadingActiveArticle || isPlayingArcherGame) 0.dp else 8.dp
            )
    ) {
        if (selectedSection == null) {
            // LIST VIEW: User sees the list-type buttons for each game & practice section
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0. Quiz test
                item {
                    GameCategoryCard(
                        title = "Quiz test",
                        icon = Icons.Default.Checklist,
                        badgeColor = Color(0xFF4F46E5),
                        onClick = {
                            selectedSection = "course_quiz"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            courseQuizStatusFilters = setOf("all")
                        }
                    )
                }

                // 1. Column Match Game
                item {
                    GameCategoryCard(
                        title = "Column Match",
                        icon = Icons.Default.SwapHoriz,
                        badgeColor = Color(0xFF0D9488),
                        onClick = {
                            selectedSection = "column_match"
                            activeColumnMatch = null
                        }
                    )
                }

                // 2. Archer Aim Game
                item {
                    GameCategoryCard(
                        title = "Archer Aim",
                        icon = Icons.Default.Adjust,
                        badgeColor = Color(0xFFD97706),
                        onClick = {
                            selectedSection = "archer_aim"
                            activeArcherGame = null
                        }
                    )
                }

                // 3. Question Bank
                item {
                    GameCategoryCard(
                        title = "Question Bank (QB)",
                        icon = Icons.Default.AccountBalance,
                        badgeColor = AmberWarning,
                        onClick = {
                            selectedSection = "question_bank"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                        }
                    )
                }

                // 4. Read Article
                item {
                    GameCategoryCard(
                        title = "Read Article",
                        icon = Icons.Default.MenuBook,
                        badgeColor = RoseError,
                        onClick = {
                            selectedSection = "read_article"
                            onSelectArticle(null)
                        }
                    )
                }
            }
        } else {
            // DEDICATED SECTION VIEW
            if ((selectedSection != "read_article" || activeArticle == null) &&
                !(selectedSection == "archer_aim" && activeArcherGame != null)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (activeArcherGame != null) {
                                activeArcherGame = null
                            } else if (activeColumnMatch != null) {
                                activeColumnMatch = null
                            } else {
                                selectedSection = null
                                activeQuestions = emptyList()
                                isQuizCompleted = false
                                onSelectArticle(null)
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Games Hub",
                            tint = SlateText
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (selectedSection) {
                            "course_quiz" -> "Quiz test"
                            "column_match" -> "Column Match"
                            "archer_aim" -> "Archer Aim"
                            "question_bank" -> "Question Bank (QB)"
                            "read_article" -> "Read Article"
                            else -> "Practice"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                }
            }

            // Read Article View (Integrated inside Games)
            if (selectedSection == "read_article") {
                ArticleReaderView(
                    articles = articles,
                    activeArticle = activeArticle,
                    words = words,
                    courses = courses,
                    onSelectArticle = onSelectArticle,
                    onSaveArticle = onSaveArticle,
                    onSaveArticlesBatch = onSaveArticlesBatch,
                    onDeleteArticle = onDeleteArticle,
                    onRateWord = onRateWord,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (selectedSection == "column_match") {
                val primaryCourseId = if (columnMatchCourseIds.size == 1 && "all" !in columnMatchCourseIds) {
                    columnMatchCourseIds.first()
                } else "all"
                val cmStats = remember(primaryCourseId, statsUpdateTrigger) {
                    ColumnMatchStats.getStats(context, primaryCourseId)
                }
                if (activeColumnMatch == null) {
                    ColumnMatchConfigView(
                        courses = courses,
                        words = words,
                        selectedCourseIds = columnMatchCourseIds,
                        onCoursesChange = { columnMatchCourseIds = it },
                        selectedStatuses = columnMatchStatusFilters,
                        onStatusesChange = { columnMatchStatusFilters = it },
                        pairCount = columnMatchPairCount,
                        onPairCountChange = { columnMatchPairCount = it },
                        stats = cmStats,
                        getPlace1 = ::getPlace1,
                        getPlace2 = ::getPlace2,
                        onStart = { startColumnMatchGame() }
                    )
                } else {
                    ColumnMatchGameView(
                        gameState = activeColumnMatch!!,
                        leftHeaderLabel = getPlaceHeaderLabel(1, columnMatchCourseIds),
                        rightHeaderLabel = getPlaceHeaderLabel(2, columnMatchCourseIds),
                        onMatchSuccess = { wordId ->
                            onRecordWordQuizAnswer(wordId, true)
                        },
                        onRoundFinished = { statsUpdateTrigger++ },
                        onPlayAgain = { startColumnMatchGame() },
                        onBackToConfig = { activeColumnMatch = null }
                    )
                }
            } else if (selectedSection == "archer_aim") {
                val primaryCourseId = if (archerCourseIds.size == 1 && "all" !in archerCourseIds) {
                    archerCourseIds.first()
                } else "all"
                val archerStats = remember(primaryCourseId, statsUpdateTrigger) {
                    ArcherAimStats.getStats(context, primaryCourseId)
                }
                if (activeArcherGame == null) {
                    ArcherAimConfigView(
                        courses = courses,
                        words = words,
                        selectedCourseIds = archerCourseIds,
                        onCoursesChange = { archerCourseIds = it },
                        selectedStatuses = archerStatusFilters,
                        onStatusesChange = { archerStatusFilters = it },
                        wordCount = archerWordCount,
                        onWordCountChange = { archerWordCount = it },
                        gameMode = archerGameMode,
                        onGameModeChange = { archerGameMode = it },
                        stats = archerStats,
                        getPlace1 = ::getPlace1,
                        getPlace2 = ::getPlace2,
                        onStart = { startArcherAimGame() }
                    )
                } else {
                    ArcherAimGameView(
                        gameState = activeArcherGame!!,
                        onHit = { wordId ->
                            onRecordWordQuizAnswer(wordId, true)
                        },
                        onMiss = { wordId ->
                            onRecordWordQuizAnswer(wordId, false)
                        },
                        onRoundFinished = { statsUpdateTrigger++ },
                        onPlayAgain = { startArcherAimGame() },
                        onBackToConfig = { activeArcherGame = null }
                    )
                }
            } else if (selectedSection == "course_quiz" && activeQuestions.isEmpty() && !isQuizCompleted) {
                val primaryCourseId = if (courseQuizCourseIds.size == 1 && "all" !in courseQuizCourseIds) {
                    courseQuizCourseIds.first()
                } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                    activeCourseId
                } else {
                    "all"
                }
                val quizStats = remember(primaryCourseId, words, statsUpdateTrigger) {
                    QuizTestStats.getStats(context, primaryCourseId, words)
                }
                CourseQuizConfigView(
                    courses = courses,
                    words = words,
                    selectedCourseIds = courseQuizCourseIds,
                    onCoursesChange = { courseQuizCourseIds = it },
                    selectedStatuses = courseQuizStatusFilters,
                    onStatusesChange = { courseQuizStatusFilters = it },
                    selectedGroups = courseQuizGroupFilters,
                    onGroupsChange = { courseQuizGroupFilters = it },
                    questionCount = courseQuizCount,
                    onCountChange = { courseQuizCount = it },
                    pooledStats = quizStats,
                    getPlace1 = ::getPlace1,
                    getPlace2 = ::getPlace2,
                    onStart = { startQuizForCategory("course_quiz") }
                )
            } else if (selectedSection == "question_bank" && activeQuestions.isEmpty() && !isQuizCompleted) {
                QuestionBankConfigView(
                    questions = questions,
                    selectedFilters1 = qbFilters1,
                    selectedFilters2 = qbFilters2,
                    selectedFilters3 = qbFilters3,
                    questionCount = qbQuestionCount,
                    onFilter1Toggle = { v ->
                        qbFilters1 = if (v in qbFilters1) qbFilters1 - v else qbFilters1 + v
                    },
                    onFilter1Clear = { qbFilters1 = emptySet() },
                    onFilter2Toggle = { v ->
                        qbFilters2 = if (v in qbFilters2) qbFilters2 - v else qbFilters2 + v
                    },
                    onFilter2Clear = { qbFilters2 = emptySet() },
                    onFilter3Toggle = { v ->
                        qbFilters3 = if (v in qbFilters3) qbFilters3 - v else qbFilters3 + v
                    },
                    onFilter3Clear = { qbFilters3 = emptySet() },
                    onCountChange = { qbQuestionCount = it },
                    onStart = { startQuizForCategory("question_bank") }
                )
            } else if (isQuizCompleted) {
                val primaryCourseId = if (courseQuizCourseIds.size == 1 && "all" !in courseQuizCourseIds) {
                    courseQuizCourseIds.first()
                } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                    activeCourseId
                } else {
                    "all"
                }
                val quizStats = remember(primaryCourseId, words, statsUpdateTrigger) {
                    QuizTestStats.getStats(context, primaryCourseId, words)
                }
                QuizSummaryView(
                    score = currentScore,
                    total = activeQuestions.size,
                    pooledScore = quizStats.first,
                    pooledTotal = quizStats.second,
                    isQuizTest = (selectedSection == "course_quiz"),
                    onRetake = { startQuizForCategory(selectedSection ?: "course_quiz", gameStatusFilter, gameQuestionCount) }
                )
            } else if (activeQuestions.isNotEmpty()) {
                // Vertical scrolling list of all quiz questions on a single page
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("quiz_vertical_list"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
                ) {
                    // Header progress and score
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${userAnswers.size} of ${activeQuestions.size} Answered",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                    Text(
                                        text = "Score: $currentScore",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = {
                                        if (activeQuestions.isNotEmpty()) {
                                            userAnswers.size.toFloat() / activeQuestions.size
                                        } else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = IndigoPrimary,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }

                    // Questions
                    itemsIndexed(activeQuestions) { qIdx, currentQ ->
                        val selectedOpt = userAnswers[qIdx]
                        val isAnswered = selectedOpt != null
                        val options = remember(currentQ) {
                            listOf(currentQ.opt1, currentQ.opt2, currentQ.opt3, currentQ.opt4)
                                .filter { it.isNotBlank() }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_question_card_$qIdx")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                // Question Badge and Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(IndigoLight)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "QUESTION ${qIdx + 1}",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IndigoPrimary
                                        )
                                    }

                                    if (isAnswered) {
                                        val hasCorrect = options.indices.any { idx ->
                                            options[idx] == selectedOpt && isOptionTheAnswer(options[idx], idx, currentQ.answer)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (hasCorrect) EmeraldLight else RoseLight)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (hasCorrect) "CORRECT" else "INCORRECT",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasCorrect) EmeraldSuccess else RoseError
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Question Title with Colorized [Word]
                                ColorizedQuestionText(
                                    question = currentQ.question,
                                    colorChoice = wordHighlightColor,
                                    fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Options
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    options.forEachIndexed { optIdx, opt ->
                                        val isThisOptionSelected = selectedOpt == opt
                                        val isThisOptionAnswer = isOptionTheAnswer(opt, optIdx, currentQ.answer)

                                        val (btnBg, btnBorder, btnText) = when {
                                            !isAnswered -> Triple(Color(0xFFF8FAFC), SlateBorder, SlateText)
                                            isThisOptionAnswer -> Triple(EmeraldLight, EmeraldSuccess, EmeraldSuccess)
                                            isThisOptionSelected && !isThisOptionAnswer -> Triple(RoseLight, RoseError, RoseError)
                                            else -> Triple(Color(0xFFF8FAFC), SlateBorder, SlateMuted)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(btnBg)
                                                .border(1.5.dp, btnBorder, RoundedCornerShape(14.dp))
                                                .clickable(enabled = !isAnswered) {
                                                    val isCorrect = isOptionTheAnswer(opt, optIdx, currentQ.answer)
                                                    userAnswers = userAnswers + (qIdx to opt)
                                                    if (isCorrect) {
                                                        currentScore += 1
                                                    }
                                                    if (currentQ.id.isNotBlank()) {
                                                        if (currentQ.sheetType == "course_quiz" || words.any { it.id == currentQ.id }) {
                                                            onRecordWordQuizAnswer(currentQ.id, isCorrect)
                                                        }
                                                        if (currentQ.sheetType != "course_quiz") {
                                                            onRecordGameAnswer(currentQ.id, isCorrect)
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 13.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${('A' + optIdx)}.  $opt",
                                                    fontFamily = selectFontForText(opt),
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isThisOptionSelected || (isAnswered && isThisOptionAnswer)) FontWeight.Bold else FontWeight.Medium,
                                                    color = btnText,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isAnswered && isThisOptionAnswer) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Correct",
                                                        tint = EmeraldSuccess,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else if (isAnswered && isThisOptionSelected && !isThisOptionAnswer) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Incorrect",
                                                        tint = RoseError,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Explanation Card
                                if (isAnswered && !currentQ.explanation.isNullOrBlank()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = IndigoLight),
                                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC7D2FE)))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "EXPLANATION",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndigoPrimary,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = currentQ.explanation ?: "",
                                                fontFamily = selectFontForText(currentQ.explanation ?: ""),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = SlateText,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action (Finish or Results)
                    item {
                        Button(
                            onClick = {
                                isQuizCompleted = true
                                if (selectedSection == "course_quiz") {
                                    val primaryCourseId = if (courseQuizCourseIds.size == 1 && "all" !in courseQuizCourseIds) {
                                        courseQuizCourseIds.first()
                                    } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                                        activeCourseId
                                    } else {
                                        "all"
                                    }
                                    QuizTestStats.recordAttempt(context, currentScore, activeQuestions.size, primaryCourseId)
                                    statsUpdateTrigger++
                                }
                                onCompleteQuiz(currentScore, activeQuestions.size)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("finish_quiz_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text(
                                text = if (userAnswers.size == activeQuestions.size) "Finish & View Results" else "Submit (${userAnswers.size}/${activeQuestions.size}) & View Results",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                Text(
                    text = "No questions available for this category.\nAdd items in the Admin panel or load sample data.",
                    textAlign = TextAlign.Center,
                    color = SlateMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBankConfigView(
    questions: List<QuestionBankEntity>,
    selectedFilters1: Set<String>,
    selectedFilters2: Set<String>,
    selectedFilters3: Set<String>,
    questionCount: Int,
    onFilter1Toggle: (String) -> Unit,
    onFilter1Clear: () -> Unit,
    onFilter2Toggle: (String) -> Unit,
    onFilter2Clear: () -> Unit,
    onFilter3Toggle: (String) -> Unit,
    onFilter3Clear: () -> Unit,
    onCountChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    val f1Label = questions.firstOrNull()?.filter1Label ?: "Category"
    val f2Label = questions.firstOrNull()?.filter2Label ?: "Difficulty"
    val f3Label = questions.firstOrNull()?.filter3Label ?: "Source"

    val f1Values = questions.mapNotNull { it.filter1 }.distinct()
    val f2Values = questions.mapNotNull { it.filter2 }.distinct()
    val f3Values = questions.mapNotNull { it.filter3 }.distinct()

    val matchedCount = questions.count { q ->
        (selectedFilters1.isEmpty() || (q.filter1 != null && q.filter1 in selectedFilters1)) &&
        (selectedFilters2.isEmpty() || (q.filter2 != null && q.filter2 in selectedFilters2)) &&
        (selectedFilters3.isEmpty() || (q.filter3 != null && q.filter3 in selectedFilters3))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Filter & Category Selection",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    // Sub-heading and description removed as requested

                    // Filter 1 - Grid Style
                    if (f1Values.isNotEmpty()) {
                        Text(
                            text = f1Label,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilters1.isEmpty(),
                                onClick = onFilter1Clear,
                                label = { Text("All") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            f1Values.forEach { v ->
                                val isSelected = v in selectedFilters1
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFilter1Toggle(v) },
                                    label = { Text(v) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Filter 2 - Grid Style
                    if (f2Values.isNotEmpty()) {
                        Text(
                            text = f2Label,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilters2.isEmpty(),
                                onClick = onFilter2Clear,
                                label = { Text("All") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            f2Values.forEach { v ->
                                val isSelected = v in selectedFilters2
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFilter2Toggle(v) },
                                    label = { Text(v) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Filter 3 - Grid Style
                    if (f3Values.isNotEmpty()) {
                        Text(
                            text = f3Label,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilters3.isEmpty(),
                                onClick = onFilter3Clear,
                                label = { Text("All") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            f3Values.forEach { v ->
                                val isSelected = v in selectedFilters3
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFilter3Toggle(v) },
                                    label = { Text(v) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Question Count Selector
                    Text(
                        text = "Number of Questions",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { cnt ->
                            FilterChip(
                                selected = questionCount == cnt,
                                onClick = { onCountChange(cnt) },
                                label = { Text("$cnt Questions") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Match Count Notification Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = IndigoLight.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "$matchedCount questions match your selection",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onStart,
                        enabled = matchedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_qb_test_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                text = if (matchedCount > 0)
                                    "Start Practice Test (${minOf(questionCount, matchedCount)} Questions)"
                                else
                                    "No Questions Match Filters",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizSummaryView(
    score: Int,
    total: Int,
    pooledScore: Int? = null,
    pooledTotal: Int? = null,
    isQuizTest: Boolean = false,
    onRetake: () -> Unit
) {
    val percent = if (total > 0) (score * 100 / total) else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(42.dp)
                )
            }

            Text(
                text = "Practice Complete!",
                fontFamily = PoppinsFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SlateText
            )

            Text(
                text = "Round Score: $score / $total ($percent%)",
                fontFamily = PoppinsFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (percent >= 70) EmeraldSuccess else RoseError
            )

            // Recalculated Pooled Correctness Ratio Card
            if (isQuizTest && pooledTotal != null && pooledTotal > 0) {
                val pooledPercent = (pooledScore ?: 0) * 100 / pooledTotal
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoLight),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Recalculated Pooled Correctness",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                        Text(
                            text = "$pooledPercent% Mean Ratio",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SlateText
                        )
                        Text(
                            text = "Based on $pooledScore correct of $pooledTotal total questions",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }
                }
            }

            Text(
                text = if (percent >= 70) "Great score, keep it up!" else "Keep practicing to improve scores",
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                color = SlateMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRetake,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Try Another Round", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GameCategoryCard(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_category_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = SlateMuted
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private data class ColumnMatchItem(
    val wordId: String,
    val text: String
)

private data class ColumnMatchGameState(
    val leftItems: List<ColumnMatchItem>,
    val rightItems: List<ColumnMatchItem>,
    val totalPairs: Int
)

enum class ArcherGameMode {
    LETTER_AIM,
    MEANING_MATCH
}

data class ArcherWordTarget(
    val wordEntity: VocabularyWordEntity,
    val place1Text: String,
    val place2Text: String,
    val targetSegment: String,
    val targetChar: Char = targetSegment.firstOrNull() ?: ' ',
    val segments: List<String> = emptyList(),
    val place1Label: String = "Word",
    val place2Label: String = "Meaning",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0
)

data class ArcherGameState(
    val targets: List<ArcherWordTarget>,
    val mode: ArcherGameMode = ArcherGameMode.LETTER_AIM,
    val currentIndex: Int = 0,
    val hits: Int = 0,
    val misses: Int = 0,
    val streak: Int = 0,
    val isRevealed: Boolean = false,
    val isFinished: Boolean = false
)

@Composable
private fun ColumnMatchConfigView(
    courses: List<CourseEntity>,
    words: List<VocabularyWordEntity>,
    selectedCourseIds: Set<String>,
    onCoursesChange: (Set<String>) -> Unit,
    selectedStatuses: Set<String>,
    onStatusesChange: (Set<String>) -> Unit,
    pairCount: Int,
    onPairCountChange: (Int) -> Unit,
    stats: Triple<Int, Int, Int>,
    getPlace1: (VocabularyWordEntity) -> String,
    getPlace2: (VocabularyWordEntity) -> String,
    onStart: () -> Unit
) {
    var isFilterGridOpen by remember { mutableStateOf(false) }

    val currentCourseWords = remember(words, selectedCourseIds) {
        if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) words
        else words.filter { it.courseId in selectedCourseIds }
    }
    val statusFilteredWords = remember(currentCourseWords, selectedStatuses) {
        currentCourseWords.filter { word ->
            isWordMatchingFlashcardStatus(word, selectedStatuses)
        }
    }
    val eligibleWords = remember(statusFilteredWords) {
        statusFilteredWords.filter {
            getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Statistics Header Card (same as Quiz test)
        item {
            val (matches, mistakes, _) = stats
            GameStatisticsCard(
                title = "Statistics",
                correctCount = matches,
                incorrectCount = mistakes,
                totalCount = matches + mistakes
            )
        }

        // 2. Filter Options Toggle Card (Same filter option as Quiz test to select courses)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFilterGridOpen) IndigoLight.copy(alpha = 0.6f) else Color.White
                ),
                border = BorderStroke(1.5.dp, if (isFilterGridOpen) IndigoPrimary else SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFilterGridOpen = !isFilterGridOpen }
                    .testTag("column_match_filter_toggle_button")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilterGridOpen) IndigoPrimary else IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (isFilterGridOpen) Color.White else IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Filter Options",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }

                        Icon(
                            imageVector = if (isFilterGridOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFilterGridOpen) "Collapse Filter Grid" else "Expand Filter Grid",
                            tint = SlateMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Active Filters Summary Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val courseSummary = if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) {
                            "All Courses"
                        } else if (selectedCourseIds.size == 1) {
                            courses.find { it.id == selectedCourseIds.first() }?.title ?: "1 Course"
                        } else {
                            "${selectedCourseIds.size} Courses"
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(12.dp))
                            Text(
                                text = courseSummary,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateText,
                                maxLines = 1
                            )
                        }

                        val statusSummary = if (selectedStatuses.isEmpty() || "all" in selectedStatuses) {
                            "All Tagged"
                        } else {
                            selectedStatuses.joinToString(", ") {
                                when (it) {
                                    "know" -> "Know"
                                    "confusion", "confused" -> "Confused"
                                    "dont_know" -> "Don't Know"
                                    else -> it
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(12.dp))
                            Text(
                                text = statusSummary,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateText,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${eligibleWords.size} words eligible",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateMuted
                            )
                        }
                    }
                }
            }
        }

        // 3. Filter Grid Expansion (Identical to Quiz test!)
        if (isFilterGridOpen) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Select Courses",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                            }
                            Text(
                                text = "Multi-Select",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Quiz / Course",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllCoursesSelected = selectedCourseIds.isEmpty() || "all" in selectedCourseIds
                            val courseItems = listOf("all" to "All") + courses.map { it.id to it.title }

                            courseItems.chunked(2).forEach { rowCourses ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCourses.forEach { (cId, cTitle) ->
                                        val isSelected = if (cId == "all") {
                                            isAllCoursesSelected
                                        } else {
                                            !isAllCoursesSelected && cId in selectedCourseIds
                                        }
                                        Surface(
                                            onClick = {
                                                if (cId == "all") {
                                                    onCoursesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllCoursesSelected) {
                                                        mutableSetOf(cId)
                                                    } else {
                                                        val s = selectedCourseIds.toMutableSet()
                                                        if (s.contains(cId)) s.remove(cId) else s.add(cId)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onCoursesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) IndigoLight else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) IndigoPrimary else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = cTitle,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) IndigoPrimary else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(2 - rowCourses.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 2. STATUS FILTER (Minimal equal-size 2x2 grid)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Status",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllStatusesSelected = selectedStatuses.isEmpty() || "all" in selectedStatuses
                            val statusList = listOf(
                                Triple("all", "All Tagged", IndigoPrimary to IndigoLight),
                                Triple("know", "Know", EmeraldSuccess to EmeraldLight),
                                Triple("confusion", "Confused", AmberWarning to AmberLight),
                                Triple("dont_know", "Don't Know", RoseError to RoseLight)
                            )

                            statusList.chunked(2).forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPair.forEach { (stKey, label, colorPair) ->
                                        val (brandColor, bgTint) = colorPair
                                        val isSelected = if (stKey == "all") {
                                            isAllStatusesSelected
                                        } else {
                                            !isAllStatusesSelected && stKey in selectedStatuses
                                        }
                                        Surface(
                                            onClick = {
                                                if (stKey == "all") {
                                                    onStatusesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllStatusesSelected) {
                                                        mutableSetOf(stKey)
                                                    } else {
                                                        val s = selectedStatuses.toMutableSet()
                                                        if (s.contains(stKey)) s.remove(stKey) else s.add(stKey)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onStatusesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) bgTint else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) brandColor else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = label,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) brandColor else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(2 - rowPair.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { isFilterGridOpen = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary),
                            border = BorderStroke(1.dp, IndigoPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Done",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Pairs per Round selector
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Matching Pairs per Round",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onPairCountChange(0) },
                            shape = CircleShape,
                            color = if (pairCount == 0) IndigoPrimary else Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, if (pairCount == 0) IndigoPrimary else SlateBorder),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AllInclusive,
                                    contentDescription = "All",
                                    tint = if (pairCount == 0) Color.White else SlateMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        listOf(4, 6, 8, 10, 15).forEach { cnt ->
                            val isSel = pairCount == cnt
                            Surface(
                                onClick = { onPairCountChange(cnt) },
                                shape = CircleShape,
                                color = if (isSel) IndigoPrimary else Color(0xFFF8FAFC),
                                border = BorderStroke(1.5.dp, if (isSel) IndigoPrimary else SlateBorder),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = cnt.toString(),
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else SlateText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Start button
        item {
            val totalEligible = eligibleWords.size
            Button(
                onClick = onStart,
                enabled = totalEligible > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        text = if (totalEligible > 0) {
                            val countText = if (pairCount > 0 && pairCount < totalEligible) "$pairCount of $totalEligible" else "$totalEligible"
                            "Start Match ($countText Pairs)"
                        } else {
                            "No Matching Words for this Selection"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnMatchGameView(
    gameState: ColumnMatchGameState,
    leftHeaderLabel: String,
    rightHeaderLabel: String,
    onMatchSuccess: (String) -> Unit,
    onRoundFinished: () -> Unit = {},
    onPlayAgain: () -> Unit,
    onBackToConfig: () -> Unit
) {
    val context = LocalContext.current
    var selectedLeftId by remember(gameState) { mutableStateOf<String?>(null) }
    var selectedRightId by remember(gameState) { mutableStateOf<String?>(null) }
    var matchedIds by remember(gameState) { mutableStateOf<Set<String>>(emptySet()) }
    var mismatchLeftId by remember(gameState) { mutableStateOf<String?>(null) }
    var mismatchRightId by remember(gameState) { mutableStateOf<String?>(null) }
    var mistakes by remember(gameState) { mutableIntStateOf(0) }
    var moves by remember(gameState) { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val isCompleted = matchedIds.size >= gameState.totalPairs && gameState.totalPairs > 0

    LaunchedEffect(isCompleted) {
        if (isCompleted && gameState.totalPairs > 0) {
            ColumnMatchStats.recordRound(context, matchedIds.size, mistakes)
            onRoundFinished()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Progress & Score Header Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFCCFBF1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Match the Pairs",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Matched",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${matchedIds.size}/${gameState.totalPairs}",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (mistakes > 0) Color(0xFFFFE4E6) else Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Mistakes",
                                    tint = if (mistakes > 0) Color(0xFFE11D48) else SlateMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$mistakes",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mistakes > 0) Color(0xFFBE123C) else SlateMuted
                                )
                            }
                        }
                    }

                    val progress = if (gameState.totalPairs > 0) matchedIds.size.toFloat() / gameState.totalPairs else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF0D9488),
                        trackColor = Color(0xFFF1F5F9)
                    )
                }
            }
        }

        // 2. Victory completion card
        if (isCompleted) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.5.dp, Color(0xFF22C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "All Pairs Matched!",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )

                        val accuracy = if (moves > 0) ((gameState.totalPairs.toFloat() / moves) * 100).toInt().coerceIn(0, 100) else 100
                        Text(
                            text = "Completed in $moves attempts • $mistakes mistakes • $accuracy% accuracy",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = SlateMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBackToConfig,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text("Settings", fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = SlateText)
                            }

                            Button(
                                onClick = onPlayAgain,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Again", fontFamily = PoppinsFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Side-by-side columns
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Column 1 (Place 1)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = leftHeaderLabel,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    gameState.leftItems.forEach { item ->
                        val isMatched = item.wordId in matchedIds
                        val isSelected = selectedLeftId == item.wordId
                        val isMismatch = mismatchLeftId == item.wordId

                        MatchItemCard(
                            text = item.text,
                            isMatched = isMatched,
                            isSelected = isSelected,
                            isMismatch = isMismatch,
                            onClick = {
                                if (!isMatched && mismatchLeftId == null && mismatchRightId == null) {
                                    if (selectedLeftId == item.wordId) {
                                        selectedLeftId = null
                                    } else {
                                        selectedLeftId = item.wordId
                                        val right = selectedRightId
                                        if (right != null) {
                                            moves++
                                            if (right == item.wordId) {
                                                matchedIds = matchedIds + item.wordId
                                                selectedLeftId = null
                                                selectedRightId = null
                                                onMatchSuccess(item.wordId)
                                            } else {
                                                mistakes++
                                                mismatchLeftId = item.wordId
                                                mismatchRightId = right
                                                coroutineScope.launch {
                                                    delay(650)
                                                    mismatchLeftId = null
                                                    mismatchRightId = null
                                                    selectedLeftId = null
                                                    selectedRightId = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Column 2 (Place 2)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rightHeaderLabel,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    gameState.rightItems.forEach { item ->
                        val isMatched = item.wordId in matchedIds
                        val isSelected = selectedRightId == item.wordId
                        val isMismatch = mismatchRightId == item.wordId

                        MatchItemCard(
                            text = item.text,
                            isMatched = isMatched,
                            isSelected = isSelected,
                            isMismatch = isMismatch,
                            onClick = {
                                if (!isMatched && mismatchLeftId == null && mismatchRightId == null) {
                                    if (selectedRightId == item.wordId) {
                                        selectedRightId = null
                                    } else {
                                        selectedRightId = item.wordId
                                        val left = selectedLeftId
                                        if (left != null) {
                                            moves++
                                            if (left == item.wordId) {
                                                matchedIds = matchedIds + item.wordId
                                                selectedLeftId = null
                                                selectedRightId = null
                                                onMatchSuccess(item.wordId)
                                            } else {
                                                mistakes++
                                                mismatchLeftId = left
                                                mismatchRightId = item.wordId
                                                coroutineScope.launch {
                                                    delay(650)
                                                    mismatchLeftId = null
                                                    mismatchRightId = null
                                                    selectedLeftId = null
                                                    selectedRightId = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchItemCard(
    text: String,
    isMatched: Boolean,
    isSelected: Boolean,
    isMismatch: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isMatched -> Color(0xFFDCFCE7)
        isMismatch -> Color(0xFFFFE4E6)
        isSelected -> Color(0xFFEEF2FF)
        else -> Color.White
    }

    val borderColor = when {
        isMatched -> Color(0xFF22C55E)
        isMismatch -> Color(0xFFE11D48)
        isSelected -> IndigoPrimary
        else -> SlateBorder
    }

    val textColor = when {
        isMatched -> Color(0xFF15803D)
        isMismatch -> Color(0xFFBE123C)
        isSelected -> IndigoPrimary
        else -> SlateText
    }

    val isBengali = remember(text) { isBengaliText(text) }

    Surface(
        onClick = onClick,
        enabled = !isMatched,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected || isMismatch || isMatched) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                fontFamily = if (isBengali) KalpurushFontFamily else PoppinsFontFamily,
                fontSize = if (isBengali) 13.sp else 12.sp,
                fontWeight = if (isSelected || isMatched) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isMatched) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Matched",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(16.dp)
                )
            } else if (isMismatch) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Mismatch",
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ArcherAimConfigView(
    courses: List<CourseEntity>,
    words: List<VocabularyWordEntity>,
    selectedCourseIds: Set<String>,
    onCoursesChange: (Set<String>) -> Unit,
    selectedStatuses: Set<String>,
    onStatusesChange: (Set<String>) -> Unit,
    wordCount: Int,
    onWordCountChange: (Int) -> Unit,
    gameMode: ArcherGameMode = ArcherGameMode.LETTER_AIM,
    onGameModeChange: (ArcherGameMode) -> Unit = {},
    stats: Triple<Int, Int, Int>,
    getPlace1: (VocabularyWordEntity) -> String,
    getPlace2: (VocabularyWordEntity) -> String,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    var isFilterGridOpen by remember { mutableStateOf(false) }

    // Calculate eligible words based on course selection & status selection
    val effectiveCourseIds = if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) {
        courses.map { it.id }.toSet()
    } else {
        selectedCourseIds
    }

    val courseWords = if (effectiveCourseIds.isNotEmpty()) {
        words.filter { it.courseId in effectiveCourseIds }
    } else {
        words
    }

    val statusFilteredWords = remember(courseWords, selectedStatuses) {
        courseWords.filter { word ->
            isWordMatchingFlashcardStatus(word, selectedStatuses)
        }
    }

    val eligibleWords = statusFilteredWords.filter {
        getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Statistics Header Card (same as Quiz test)
        item {
            val (hits, misses, _) = stats
            GameStatisticsCard(
                title = "Statistics",
                correctCount = hits,
                incorrectCount = misses,
                totalCount = hits + misses
            )
        }

        // 1b. Mode Selection Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Game Mode",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isMode1 = (gameMode == ArcherGameMode.LETTER_AIM)
                        Surface(
                            onClick = { onGameModeChange(ArcherGameMode.LETTER_AIM) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMode1) IndigoPrimary else Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, if (isMode1) IndigoPrimary else SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🎯 Letter / কার",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMode1) Color.White else SlateText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hit letter with intact কার",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 10.sp,
                                    color = if (isMode1) Color.White.copy(alpha = 0.85f) else SlateMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        val isMode2 = (gameMode == ArcherGameMode.MEANING_MATCH)
                        Surface(
                            onClick = { onGameModeChange(ArcherGameMode.MEANING_MATCH) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMode2) IndigoPrimary else Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, if (isMode2) IndigoPrimary else SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🏹 Meaning Match",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMode2) Color.White else SlateText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hit 1 of 3 target options",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 10.sp,
                                    color = if (isMode2) Color.White.copy(alpha = 0.85f) else SlateMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Filter Selector Card (Collapsible, similar to Quiz test)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Filter Options",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }

                        IconButton(
                            onClick = { isFilterGridOpen = !isFilterGridOpen },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFilterGridOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isFilterGridOpen) "Collapse" else "Expand",
                                tint = SlateMuted
                            )
                        }
                    }

                    // Summary chips when collapsed
                    if (!isFilterGridOpen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val courseLabel = if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) {
                                "All Courses"
                            } else {
                                "${selectedCourseIds.size} Selected"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = IndigoLight,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = courseLabel,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                }
                            }

                            val statusLabel = if (selectedStatuses.isEmpty() || "all" in selectedStatuses) {
                                "All Tagged"
                            } else {
                                selectedStatuses.joinToString(", ") {
                                    when (it) {
                                        "know" -> "Know"
                                        "confusion", "confused" -> "Confused"
                                        "dont_know" -> "Don't Know"
                                        else -> it
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = statusLabel,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = "${eligibleWords.size} words",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }
                    }

                    // Expanded filter view
                    if (isFilterGridOpen) {
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 1. COURSE SELECTION (Equal-size 2-column grid)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Courses",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllCoursesSelected = selectedCourseIds.isEmpty() || "all" in selectedCourseIds
                            val allOption = CourseEntity(id = "all", title = "All Courses", description = "")
                            val displayCourses = listOf(allOption) + courses

                            displayCourses.chunked(2).forEach { rowCourses ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCourses.forEach { c ->
                                        val isSelected = if (c.id == "all") {
                                            isAllCoursesSelected
                                        } else {
                                            !isAllCoursesSelected && c.id in selectedCourseIds
                                        }
                                        Surface(
                                            onClick = {
                                                if (c.id == "all") {
                                                    onCoursesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllCoursesSelected) {
                                                        mutableSetOf(c.id)
                                                    } else {
                                                        val s = selectedCourseIds.toMutableSet()
                                                        if (s.contains(c.id)) s.remove(c.id) else s.add(c.id)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onCoursesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) IndigoLight else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) IndigoPrimary else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = c.title,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) IndigoPrimary else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(2 - rowCourses.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 2. STATUS FILTER (Equal-size 2x2 grid)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Status",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllStatusesSelected = selectedStatuses.isEmpty() || "all" in selectedStatuses
                            val statusList = listOf(
                                Triple("all", "All Tagged", IndigoPrimary to IndigoLight),
                                Triple("know", "Know", EmeraldSuccess to EmeraldLight),
                                Triple("confusion", "Confused", AmberWarning to AmberLight),
                                Triple("dont_know", "Don't Know", RoseError to RoseLight)
                            )

                            statusList.chunked(2).forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPair.forEach { (stKey, label, colorPair) ->
                                        val (brandColor, bgTint) = colorPair
                                        val isSelected = if (stKey == "all") {
                                            isAllStatusesSelected
                                        } else {
                                            !isAllStatusesSelected && stKey in selectedStatuses
                                        }
                                        Surface(
                                            onClick = {
                                                if (stKey == "all") {
                                                    onStatusesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllStatusesSelected) {
                                                        mutableSetOf(stKey)
                                                    } else {
                                                        val s = selectedStatuses.toMutableSet()
                                                        if (s.contains(stKey)) s.remove(stKey) else s.add(stKey)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onStatusesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) bgTint else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) brandColor else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = label,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) brandColor else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(2 - rowPair.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { isFilterGridOpen = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary),
                            border = BorderStroke(1.dp, IndigoPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply Filters (${eligibleWords.size} eligible)",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Word Count Selection
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Number of Target Words",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 20, 0).forEach { cnt ->
                            val isSel = (wordCount == cnt)
                            val label = if (cnt == 0) "All" else cnt.toString()
                            Surface(
                                onClick = { onWordCountChange(cnt) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) IndigoPrimary else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSel) IndigoPrimary else SlateBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else SlateText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Start Game Button
        item {
            val totalEligible = eligibleWords.size
            Button(
                onClick = onStart,
                enabled = totalEligible > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Adjust, contentDescription = null)
                    Text(
                        text = if (totalEligible > 0) {
                            val countText = if (wordCount > 0 && wordCount < totalEligible) "$wordCount of $totalEligible" else "$totalEligible"
                            "Start Archer Aim ($countText Words)"
                        } else {
                            "No Matching Words for this Selection"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class QuizQuestionItem(
    val id: String = "",
    val sheetType: String = "",
    val question: String,
    val opt1: String,
    val opt2: String,
    val opt3: String,
    val opt4: String,
    val answer: String,
    val explanation: String?,
    val lastAttemptStatus: String? = "not_studied"
)

private fun isOptionTheAnswer(opt: String, optIdx: Int, answer: String): Boolean {
    val cleanOpt = opt.replace(Regex("""^\s*#\s*"""), "").replace(Regex("""\s*#\s*$"""), "").trim()
    val cleanAns = answer.replace(Regex("""^\s*#\s*"""), "").replace(Regex("""\s*#\s*$"""), "").trim()
    if (cleanOpt.equals(cleanAns, ignoreCase = true)) return true

    val letter = ('A' + optIdx).toString()
    if (cleanAns.equals(letter, ignoreCase = true)) return true
    if (cleanAns.equals("Opt${optIdx + 1}", ignoreCase = true)) return true
    if (cleanAns.equals("Option ${optIdx + 1}", ignoreCase = true)) return true
    if (cleanAns.equals("Option${optIdx + 1}", ignoreCase = true)) return true

    if (cleanAns.startsWith("$letter.", ignoreCase = true) || cleanAns.startsWith("$letter)", ignoreCase = true)) {
        val sub = cleanAns.substring(2).trim()
        if (sub.equals(cleanOpt, ignoreCase = true)) return true
    }
    return false
}

private fun GamePracticeEntity.toQuizItem(): QuizQuestionItem {
    val resolved = com.example.data.parser.QuestionAnswerResolver.resolve(
        rawOpt1 = opt1,
        rawOpt2 = opt2,
        rawOpt3 = opt3,
        rawOpt4 = opt4,
        rawAnswer = answer
    )
    return QuizQuestionItem(
        id = id,
        sheetType = sheetType,
        question = question,
        opt1 = resolved.cleanOpt1,
        opt2 = resolved.cleanOpt2,
        opt3 = resolved.cleanOpt3,
        opt4 = resolved.cleanOpt4,
        answer = resolved.correctAnswer,
        explanation = explanation,
        lastAttemptStatus = lastAttemptStatus
    )
}

private fun QuestionBankEntity.toQuizItem(): QuizQuestionItem {
    val resolved = com.example.data.parser.QuestionAnswerResolver.resolve(
        rawOpt1 = opt1,
        rawOpt2 = opt2,
        rawOpt3 = opt3,
        rawOpt4 = opt4,
        rawAnswer = answer
    )
    return QuizQuestionItem(
        id = id,
        sheetType = "qb",
        question = question,
        opt1 = resolved.cleanOpt1,
        opt2 = resolved.cleanOpt2,
        opt3 = resolved.cleanOpt3,
        opt4 = resolved.cleanOpt4,
        answer = resolved.correctAnswer,
        explanation = explanation,
        lastAttemptStatus = "not_studied"
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseQuizConfigView(
    courses: List<CourseEntity>,
    words: List<VocabularyWordEntity>,
    selectedCourseIds: Set<String>,
    onCoursesChange: (Set<String>) -> Unit,
    selectedStatuses: Set<String>,
    onStatusesChange: (Set<String>) -> Unit,
    selectedGroups: Set<String>,
    onGroupsChange: (Set<String>) -> Unit,
    questionCount: Int,
    onCountChange: (Int) -> Unit,
    pooledStats: Triple<Int, Int, Int>,
    getPlace1: (VocabularyWordEntity) -> String,
    getPlace2: (VocabularyWordEntity) -> String,
    onStart: () -> Unit
) {
    var isFilterGridOpen by remember { mutableStateOf(false) }

    val currentCourseWords = remember(words, selectedCourseIds) {
        if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) words
        else words.filter { it.courseId in selectedCourseIds }
    }

    val availableGroups = remember(currentCourseWords) {
        currentCourseWords.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val groupFilteredWords = remember(currentCourseWords, selectedGroups) {
        if (selectedGroups.isEmpty() || "all" in selectedGroups) currentCourseWords
        else currentCourseWords.filter { it.group in selectedGroups }
    }

    val correctCount = remember(groupFilteredWords) {
        groupFilteredWords.count { it.lastQuizStatus == "correct" }
    }
    val incorrectCount = remember(groupFilteredWords) {
        groupFilteredWords.count { it.lastQuizStatus == "incorrect" }
    }
    val notStudiedCount = remember(groupFilteredWords) {
        groupFilteredWords.count { it.lastQuizStatus == null || it.lastQuizStatus == "not_studied" || it.lastQuizStatus.isBlank() }
    }

    val (pooledCorrect, pooledTotal, attempts) = pooledStats
    val pooledAccuracy = if (pooledTotal > 0) (pooledCorrect * 100 / pooledTotal) else 0

    val matchingWords = remember(groupFilteredWords, selectedStatuses) {
        val base = groupFilteredWords.filter { word ->
            isWordMatchingFlashcardStatus(word, selectedStatuses)
        }
        base.filter { getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Statistics Header Card with Graphical Rate and Ratio Visualizers
        item {
            val pooledIncorrect = (pooledTotal - pooledCorrect).coerceAtLeast(0)
            GameStatisticsCard(
                title = "Statistics",
                correctCount = pooledCorrect,
                incorrectCount = pooledIncorrect,
                totalCount = pooledTotal
            )
        }

        // 2. Filter Options Toggle Button (Filters are hidden always under this button until clicked)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFilterGridOpen) IndigoLight.copy(alpha = 0.6f) else Color.White
                ),
                border = BorderStroke(1.5.dp, if (isFilterGridOpen) IndigoPrimary else SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFilterGridOpen = !isFilterGridOpen }
                    .testTag("quiz_test_filter_toggle_button")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilterGridOpen) IndigoPrimary else IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (isFilterGridOpen) Color.White else IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Filter Options",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }

                        Icon(
                            imageVector = if (isFilterGridOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFilterGridOpen) "Collapse Filter Grid" else "Expand Filter Grid",
                            tint = SlateMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Active Filters Summary Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val courseSummary = if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) {
                            "All Courses"
                        } else if (selectedCourseIds.size == 1) {
                            courses.find { it.id == selectedCourseIds.first() }?.title ?: "1 Course"
                        } else {
                            "${selectedCourseIds.size} Courses"
                        }
                        val groupSummary = if (selectedGroups.isEmpty() || "all" in selectedGroups) "All Groups" else "${selectedGroups.size} Groups"
                        val statusSummary = if (selectedStatuses.isEmpty() || "all" in selectedStatuses) {
                            "All Tagged"
                        } else {
                            selectedStatuses.joinToString(", ") {
                                when (it) {
                                    "know" -> "Know"
                                    "confusion", "confused" -> "Confused"
                                    "dont_know" -> "Don't Know"
                                    else -> it
                                }
                            }
                        }

                        listOf(
                            Icons.Default.School to courseSummary,
                            Icons.Default.Group to groupSummary,
                            Icons.Default.History to statusSummary
                        ).forEach { (icon, text) ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(12.dp))
                                Text(
                                    text = text,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Filter Grid (Shown ONLY when filter button is clicked / isFilterGridOpen == true)
        if (isFilterGridOpen) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("quiz_test_filter_grid")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Filter Options",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                            }
                            Text(
                                text = "Multi-Select",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 1. SELECT QUIZ / COURSE (Equal Size Grid for all, minimum text, no dropdown)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Quiz / Course",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllCoursesSelected = selectedCourseIds.isEmpty() || "all" in selectedCourseIds
                            val courseItems = listOf("all" to "All") + courses.map { it.id to it.title }

                            courseItems.chunked(2).forEach { rowCourses ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCourses.forEach { (cId, cTitle) ->
                                        val isSelected = if (cId == "all") {
                                            isAllCoursesSelected
                                        } else {
                                            !isAllCoursesSelected && cId in selectedCourseIds
                                        }
                                        Surface(
                                            onClick = {
                                                if (cId == "all") {
                                                    onCoursesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllCoursesSelected) {
                                                        mutableSetOf(cId)
                                                    } else {
                                                        val s = selectedCourseIds.toMutableSet()
                                                        if (s.contains(cId)) s.remove(cId) else s.add(cId)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onCoursesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) IndigoLight else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) IndigoPrimary else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = cTitle,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) IndigoPrimary else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(2 - rowCourses.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 2. GROUPS (Equal Size Grid for all, minimum text, ONLY group name, no 'Group' prefix, no dropdown)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Group",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllGroupsSelected = selectedGroups.isEmpty() || "all" in selectedGroups
                            val groupItems = listOf("all") + availableGroups

                            groupItems.chunked(4).forEach { rowGrps ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowGrps.forEach { grp ->
                                        val isChecked = if (grp == "all") {
                                            isAllGroupsSelected
                                        } else {
                                            !isAllGroupsSelected && grp in selectedGroups
                                        }
                                        Surface(
                                            onClick = {
                                                if (grp == "all") {
                                                    onGroupsChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllGroupsSelected) {
                                                        mutableSetOf(grp)
                                                    } else {
                                                        val s = selectedGroups.toMutableSet()
                                                        if (s.contains(grp)) s.remove(grp) else s.add(grp)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onGroupsChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isChecked) IndigoPrimary else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isChecked) 1.5.dp else 1.dp,
                                                if (isChecked) IndigoPrimary else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (grp == "all") "All" else grp,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isChecked) Color.White else SlateText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(4 - rowGrps.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // 3. STATUS FILTER (Flashcard Status Tag Design, minimal equal-size 2x2 grid, no dropdown)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Status",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            val isAllStatusesSelected = selectedStatuses.isEmpty() || "all" in selectedStatuses
                            val statusList = listOf(
                                Triple("all", "All Tagged", IndigoPrimary to IndigoLight),
                                Triple("know", "Know", EmeraldSuccess to EmeraldLight),
                                Triple("confusion", "Confused", AmberWarning to AmberLight),
                                Triple("dont_know", "Don't Know", RoseError to RoseLight)
                            )

                            statusList.chunked(2).forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPair.forEach { (stKey, label, colorPair) ->
                                        val (brandColor, bgTint) = colorPair
                                        val isSelected = if (stKey == "all") {
                                            isAllStatusesSelected
                                        } else {
                                            !isAllStatusesSelected && stKey in selectedStatuses
                                        }
                                        Surface(
                                            onClick = {
                                                if (stKey == "all") {
                                                    onStatusesChange(setOf("all"))
                                                } else {
                                                    val newSet = if (isAllStatusesSelected) {
                                                        mutableSetOf(stKey)
                                                    } else {
                                                        val s = selectedStatuses.toMutableSet()
                                                        if (s.contains(stKey)) s.remove(stKey) else s.add(stKey)
                                                        if (s.isEmpty()) mutableSetOf("all") else s
                                                    }
                                                    onStatusesChange(newSet)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) bgTint else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) brandColor else SlateBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(brandColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = label,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) brandColor else SlateText
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = brandColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Done Button
                        OutlinedButton(
                            onClick = { isFilterGridOpen = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary),
                            border = BorderStroke(1.dp, IndigoPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Done",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }


        // 4. Question Limit & [Word] Color Settings Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Minimal Question Range / Limit (Icon or Number)
                    Text(
                        text = "Question Limit",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All / Infinite Icon Button
                        Surface(
                            onClick = { onCountChange(0) },
                            shape = CircleShape,
                            color = if (questionCount == 0) IndigoPrimary else Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, if (questionCount == 0) IndigoPrimary else SlateBorder),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AllInclusive,
                                    contentDescription = "All",
                                    tint = if (questionCount == 0) Color.White else SlateMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Minimal Numbers
                        listOf(5, 10, 20, 50, 100).forEach { cnt ->
                            val isSel = questionCount == cnt
                            Surface(
                                onClick = { onCountChange(cnt) },
                                shape = CircleShape,
                                color = if (isSel) IndigoPrimary else Color(0xFFF8FAFC),
                                border = BorderStroke(1.5.dp, if (isSel) IndigoPrimary else SlateBorder),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = cnt.toString(),
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else SlateText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Start Quiz Button
        item {
            val totalEligible = matchingWords.size
            Button(
                onClick = onStart,
                enabled = totalEligible > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        text = if (totalEligible > 0) {
                            val countText = if (questionCount > 0 && questionCount < totalEligible) "$questionCount of $totalEligible" else "$totalEligible"
                            "Start Quiz ($countText Questions)"
                        } else {
                            "No Matching Words for this Selection"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
