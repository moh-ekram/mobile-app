package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
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
import org.json.JSONObject

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statsUpdateTrigger by remember { mutableIntStateOf(0) }
    var wordHighlightColor by remember { mutableStateOf("red") } // "red" (default), "blue", "green"

    // Top Section: null means list menu of categories; non-null opens dedicated section
    var selectedSection by remember { mutableStateOf<String?>(null) }

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

    // Game Mode Filtering & Count Config
    var gameStatusFilter by remember { mutableStateOf("all") } // "all", "correct", "incorrect", "not_studied"
    var gameQuestionCount by remember { mutableIntStateOf(10) }

    // Question Bank Config - Multi-Selection Grid Style
    var qbFilters1 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbFilters2 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbFilters3 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qbQuestionCount by remember { mutableIntStateOf(5) }

    fun getPlace1(w: VocabularyWordEntity): String {
        if (!w.customPlacesJson.isNullOrBlank()) {
            try {
                val json = JSONObject(w.customPlacesJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = k.lowercase().replace("_", "").replace(" ", "").replace("-", "")
                    if (kClean == "place1" || kClean.startsWith("place1") || kClean == "word") {
                        val v = json.optString(k, "").trim()
                        if (v.isNotBlank()) return v
                    }
                }
            } catch (_: Exception) {}
        }
        return w.word.trim()
    }

    fun getPlace2(w: VocabularyWordEntity): String {
        if (!w.customPlacesJson.isNullOrBlank()) {
            try {
                val json = JSONObject(w.customPlacesJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = k.lowercase().replace("_", "").replace(" ", "").replace("-", "")
                    if (kClean == "place2" || kClean.startsWith("place2") ||
                        kClean == "meaning" || kClean.contains("meaning") || kClean.contains("definition") || kClean.contains("translation")
                    ) {
                        val v = json.optString(k, "").trim()
                        if (v.isNotBlank()) return v
                    }
                }
            } catch (_: Exception) {}
        }
        return w.meaning.trim()
    }

    // Intercept back button: 
    // 1. Reading article -> back to article list
    // 2. In quiz questions -> back to section config
    // 3. In game section -> back to games menu
    // 4. In games menu -> back to home screen
    BackHandler {
        if (activeArticle != null) {
            onSelectArticle(null)
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
                val statusFiltered = if (courseQuizStatusFilters.isEmpty() || "all" in courseQuizStatusFilters) {
                    groupFiltered
                } else {
                    groupFiltered.filter { word ->
                        val s = if (word.lastQuizStatus.isNullOrBlank() || word.lastQuizStatus == "not_studied") "not_studied" else word.lastQuizStatus
                        s in courseQuizStatusFilters
                    }
                }
                val eligibleWords = (if (statusFiltered.isNotEmpty()) statusFiltered else groupFiltered)
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
                        explanation = (if (word.meaning.isNotBlank()) "Meaning: ${word.meaning}" else "") +
                                (if (!word.example.isNullOrBlank()) "\nExample: ${word.example}" else ""),
                        lastAttemptStatus = word.lastQuizStatus
                    )
                }
            }
            "odd_one_out", "analogy", "practice" -> {
                val base = games.filter { it.sheetType == section }
                val filtered = when (statusFilter) {
                    "correct" -> base.filter { it.lastAttemptStatus == "correct" }
                    "incorrect" -> base.filter { it.lastAttemptStatus == "incorrect" }
                    "not_studied" -> base.filter { it.lastAttemptStatus == null || it.lastAttemptStatus == "not_studied" }
                    else -> base
                }
                val count = maxCount ?: gameQuestionCount
                (if (count > 0 && count < filtered.size) filtered.shuffled().take(count) else filtered.shuffled())
                    .map { it.toQuizItem() }
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(
                horizontal = if (isReadingActiveArticle) 0.dp else 16.dp,
                vertical = if (isReadingActiveArticle) 0.dp else 8.dp
            )
    ) {
        if (selectedSection == null) {
            // LIST VIEW: User sees the list-type buttons for each game & practice section
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0. Quiz test (Course Vocabulary Quiz - Placed at Top)
                item {
                    val currentCourseWords = if (courseQuizCourseIds.isEmpty() || "all" in courseQuizCourseIds) {
                        if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                            words.filter { it.courseId == activeCourseId }
                        } else {
                            words
                        }
                    } else {
                        words.filter { it.courseId in courseQuizCourseIds }
                    }
                    val courseNotStudied = currentCourseWords.count { it.lastQuizStatus == null || it.lastQuizStatus == "not_studied" }
                    val primaryCourseId = if (courseQuizCourseIds.size == 1 && "all" !in courseQuizCourseIds) {
                        courseQuizCourseIds.first()
                    } else if (activeCourseId.isNotBlank() && activeCourseId != "all") {
                        activeCourseId
                    } else {
                        "all"
                    }
                    val (hubCorrect, hubTotal, _) = remember(primaryCourseId, words, statsUpdateTrigger) {
                        QuizTestStats.getStats(context, primaryCourseId, words)
                    }
                    val hubRatio = if (hubTotal > 0) (hubCorrect * 100 / hubTotal) else 0

                    GameCategoryCard(
                        title = "Quiz test",
                        description = null,
                        count = currentCourseWords.size,
                        icon = Icons.Default.Checklist,
                        badgeColor = Color(0xFF4F46E5),
                        correctCount = hubCorrect,
                        incorrectCount = (hubTotal - hubCorrect).coerceAtLeast(0),
                        notStudiedCount = courseNotStudied,
                        accuracyPercent = hubRatio,
                        isPooled = true,
                        onClick = {
                            selectedSection = "course_quiz"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            courseQuizStatusFilters = setOf("all")
                        }
                    )
                }

                // 1. Odd One Out
                item {
                    val oooItems = games.filter { it.sheetType == "odd_one_out" }
                    val oooCorrect = oooItems.count { it.lastAttemptStatus == "correct" }
                    val oooIncorrect = oooItems.count { it.lastAttemptStatus == "incorrect" }
                    val oooNotStudied = oooItems.count { it.lastAttemptStatus == null || it.lastAttemptStatus == "not_studied" }
                    val oooAttempted = oooCorrect + oooIncorrect
                    val oooRatio = if (oooAttempted > 0) (oooCorrect * 100 / oooAttempted) else 0

                    GameCategoryCard(
                        title = "Odd One Out",
                        description = null,
                        count = oooItems.size,
                        icon = Icons.Default.FilterAlt,
                        badgeColor = Color(0xFF8B5CF6),
                        correctCount = oooCorrect,
                        incorrectCount = oooIncorrect,
                        notStudiedCount = oooNotStudied,
                        accuracyPercent = oooRatio,
                        onClick = {
                            selectedSection = "odd_one_out"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            gameStatusFilter = "all"
                        }
                    )
                }

                // 2. Analogy Practice
                item {
                    val analogyItems = games.filter { it.sheetType == "analogy" }
                    val analogyCorrect = analogyItems.count { it.lastAttemptStatus == "correct" }
                    val analogyIncorrect = analogyItems.count { it.lastAttemptStatus == "incorrect" }
                    val analogyNotStudied = analogyItems.count { it.lastAttemptStatus == null || it.lastAttemptStatus == "not_studied" }
                    val analogyAttempted = analogyCorrect + analogyIncorrect
                    val analogyRatio = if (analogyAttempted > 0) (analogyCorrect * 100 / analogyAttempted) else 0

                    GameCategoryCard(
                        title = "Analogy Practice",
                        description = null,
                        count = analogyItems.size,
                        icon = Icons.Default.CompareArrows,
                        badgeColor = Color(0xFF0284C7),
                        correctCount = analogyCorrect,
                        incorrectCount = analogyIncorrect,
                        notStudiedCount = analogyNotStudied,
                        accuracyPercent = analogyRatio,
                        onClick = {
                            selectedSection = "analogy"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            gameStatusFilter = "all"
                        }
                    )
                }

                // 3. Practice Quiz
                item {
                    val practiceItems = games.filter { it.sheetType == "practice" }
                    val practiceCorrect = practiceItems.count { it.lastAttemptStatus == "correct" }
                    val practiceIncorrect = practiceItems.count { it.lastAttemptStatus == "incorrect" }
                    val practiceNotStudied = practiceItems.count { it.lastAttemptStatus == null || it.lastAttemptStatus == "not_studied" }
                    val practiceAttempted = practiceCorrect + practiceIncorrect
                    val practiceRatio = if (practiceAttempted > 0) (practiceCorrect * 100 / practiceAttempted) else 0

                    GameCategoryCard(
                        title = "Practice Quiz",
                        description = null,
                        count = practiceItems.size,
                        icon = Icons.Default.Quiz,
                        badgeColor = EmeraldSuccess,
                        correctCount = practiceCorrect,
                        incorrectCount = practiceIncorrect,
                        notStudiedCount = practiceNotStudied,
                        accuracyPercent = practiceRatio,
                        onClick = {
                            selectedSection = "practice"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            gameStatusFilter = "all"
                        }
                    )
                }

                // 4. Question Bank
                item {
                    GameCategoryCard(
                        title = "Question Bank (QB)",
                        description = null,
                        count = questions.size,
                        icon = Icons.Default.AccountBalance,
                        badgeColor = AmberWarning,
                        onClick = {
                            selectedSection = "question_bank"
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                        }
                    )
                }

                // 5. Read Article
                item {
                    GameCategoryCard(
                        title = "Read Article",
                        description = null,
                        count = articles.size,
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
            if (selectedSection != "read_article" || activeArticle == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            selectedSection = null
                            activeQuestions = emptyList()
                            isQuizCompleted = false
                            onSelectArticle(null)
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
                            "odd_one_out" -> "Odd One Out"
                            "analogy" -> "Analogy Practice"
                            "practice" -> "Practice Quiz"
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
            } else if (selectedSection in listOf("odd_one_out", "analogy", "practice") && activeQuestions.isEmpty() && !isQuizCompleted) {
                val secTitle = when (selectedSection) {
                    "odd_one_out" -> "Odd One Out"
                    "analogy" -> "Analogy Practice"
                    "practice" -> "Practice Quiz"
                    else -> "Practice"
                }
                GameModeConfigView(
                    section = selectedSection ?: "odd_one_out",
                    title = secTitle,
                    games = games,
                    selectedStatus = gameStatusFilter,
                    onStatusChange = { gameStatusFilter = it },
                    questionCount = gameQuestionCount,
                    onCountChange = { gameQuestionCount = it },
                    onStart = { startQuizForCategory(selectedSection ?: "odd_one_out", gameStatusFilter, gameQuestionCount) }
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
private fun GameModeConfigView(
    section: String,
    title: String,
    games: List<GamePracticeEntity>,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    questionCount: Int,
    onCountChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    val sectionItems = games.filter { it.sheetType == section }
    val correctItems = sectionItems.filter { it.lastAttemptStatus == "correct" }
    val incorrectItems = sectionItems.filter { it.lastAttemptStatus == "incorrect" }
    val notStudiedItems = sectionItems.filter { it.lastAttemptStatus == null || it.lastAttemptStatus == "not_studied" }
    val attemptedCount = correctItems.size + incorrectItems.size
    val accuracyPercent = if (attemptedCount > 0) (correctItems.size * 100 / attemptedCount) else 0

    val filteredCount = when (selectedStatus) {
        "correct" -> correctItems.size
        "incorrect" -> incorrectItems.size
        "not_studied" -> notStudiedItems.size
        else -> sectionItems.size
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
                        text = title,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    // Sub-heading and description removed as requested

                    // Accuracy & Ratio Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = if (attemptedCount > 0) Color(0xFF15803D) else SlateMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Accuracy Rate",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText
                                    )
                                }

                                Text(
                                    text = if (attemptedCount > 0) "$accuracyPercent%" else "N/A",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (accuracyPercent >= 70) Color(0xFF15803D) else if (attemptedCount > 0) RoseError else SlateMuted
                                )
                            }

                            Text(
                                text = if (attemptedCount > 0)
                                    "${correctItems.size} of $attemptedCount attempted questions answered correctly"
                                else
                                    "No questions attempted yet for this mode",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )

                            // Status breakdown chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "✓ ${correctItems.size} Correct",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFE4E6))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "✗ ${incorrectItems.size} Incorrect",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseError
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "○ ${notStudiedItems.size} Not Studied",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }
                    }

                    // Status Filter Section
                    Text(
                        text = "Filter by History",
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
                            selected = selectedStatus == "all",
                            onClick = { onStatusChange("all") },
                            label = { Text("All (${sectionItems.size})") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = selectedStatus == "correct",
                            onClick = { onStatusChange("correct") },
                            label = { Text("Previous Correct (${correctItems.size})") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF15803D),
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = selectedStatus == "incorrect",
                            onClick = { onStatusChange("incorrect") },
                            label = { Text("Previous Incorrect (${incorrectItems.size})") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoseError,
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = selectedStatus == "not_studied",
                            onClick = { onStatusChange("not_studied") },
                            label = { Text("Not Studied (${notStudiedItems.size})") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
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
                                label = { Text("$cnt Qs") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Match summary
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
                                text = "$filteredCount questions available for this filter",
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
                        enabled = filteredCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_game_practice_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                text = if (filteredCount > 0)
                                    "Start Practice (${minOf(questionCount, filteredCount)} Questions)"
                                else
                                    "No Questions Match Filter",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                        text = "Question Bank Test",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 17.sp,
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
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    correctCount: Int? = null,
    incorrectCount: Int? = null,
    notStudiedCount: Int? = null,
    accuracyPercent: Int? = null,
    isPooled: Boolean = false,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$count",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
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

                if (accuracyPercent != null && count > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasAttempts = ((correctCount ?: 0) + (incorrectCount ?: 0)) > 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasAttempts) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (hasAttempts) (if (isPooled) "$accuracyPercent% Pooled" else "$accuracyPercent% Accuracy") else "Not Started",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasAttempts) Color(0xFF15803D) else SlateMuted
                            )
                        }

                        if ((correctCount ?: 0) > 0) {
                            Text(
                                text = "✓ $correctCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                        if ((incorrectCount ?: 0) > 0) {
                            Text(
                                text = "✗ $incorrectCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseError
                            )
                        }
                        if ((notStudiedCount ?: 0) > 0) {
                            Text(
                                text = "○ $notStudiedCount new",
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                        }
                    }
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
        val base = if (selectedStatuses.isEmpty() || "all" in selectedStatuses) {
            groupFilteredWords
        } else {
            groupFilteredWords.filter { word ->
                val s = if (word.lastQuizStatus.isNullOrBlank() || word.lastQuizStatus == "not_studied") "not_studied" else word.lastQuizStatus
                s in selectedStatuses
            }
        }
        base.filter { getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Quiz Test Header Card (Strictly without sub heading or description)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quiz test",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SlateText
                        )

                        // Pooled Accuracy Ratio Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndigoLight)
                                .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (pooledTotal > 0) "$pooledAccuracy% Pooled Accuracy" else "Not Started",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }

                    // Pooled Accuracy Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ $pooledCorrect Correct",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFEE2E2))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕ ${(pooledTotal - pooledCorrect).coerceAtLeast(0)} Incorrect",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📝 $attempts Attempts",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
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
                            Column {
                                Text(
                                    text = "Filter Options",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                                Text(
                                    text = if (isFilterGridOpen) "Tap to close filter grid" else "Tap to open filter grid (Quiz, Group, Status)",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isFilterGridOpen) IndigoPrimary.copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isFilterGridOpen) "Grid Open" else "Grid Hidden",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFilterGridOpen) IndigoPrimary else SlateMuted
                                )
                            }
                            Icon(
                                imageVector = if (isFilterGridOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isFilterGridOpen) "Collapse Filter Grid" else "Expand Filter Grid",
                                tint = SlateMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                            "All Status"
                        } else {
                            selectedStatuses.joinToString(", ") {
                                when (it) {
                                    "not_studied" -> "Not Studied"
                                    "incorrect" -> "Incorrect"
                                    "correct" -> "Correct"
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
                                    text = "Filter Grid (Multiple Select)",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                            }
                            Text(
                                text = "Multi-Select Enabled",
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
                                Triple("all", "All", IndigoPrimary to IndigoLight),
                                Triple("correct", "Correct", EmeraldSuccess to EmeraldLight),
                                Triple("incorrect", "Incorrect", RoseError to RoseLight),
                                Triple("not_studied", "Not Studied", Color(0xFF64748B) to Color(0xFFF1F5F9))
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
