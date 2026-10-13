package com.example.ui.screens

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.data.sync.DiscoveredDriveFile
import com.example.ui.theme.*
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
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
    initialSection: String? = null,
    onUpdateQuestionStatus: (questionId: String, status: String) -> Unit = { _, _ -> },
    onDeleteQuestionsByBank: (bankName: String) -> Unit = {},
    onRenameBank: (oldBankName: String, newBankName: String) -> Unit = { _, _ -> },
    onImportQBBytes: (bytes: ByteArray, fileName: String, clearExisting: Boolean) -> Unit = { _, _, _ -> },
    driveCourses: List<DiscoveredDriveFile> = emptyList(),
    isRefreshingDriveCourses: Boolean = false,
    driveQBFiles: List<DiscoveredDriveFile> = emptyList(),
    isRefreshingDriveQB: Boolean = false,
    downloadingCourseFileIds: Set<String> = emptySet(),
    onRefreshDriveCourses: () -> Unit = {},
    onRefreshDriveQB: () -> Unit = {},
    onDownloadDriveQB: (DiscoveredDriveFile) -> Unit = {},
    onAddSampleData: () -> Unit = {},
    onBackupQBProgress: () -> Unit = {},
    onRestoreQBProgress: (bytes: ByteArray) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statsUpdateTrigger by remember { mutableIntStateOf(0) }
    var wordHighlightColor by remember { mutableStateOf("red") } // "red" (default), "blue", "green"

    // Top Section: null means list menu of categories; non-null opens dedicated section
    var selectedSection by remember { mutableStateOf<String?>(initialSection) }

    LaunchedEffect(selectedSection) {
        onSectionChange(selectedSection)
    }

    // Active Quiz State (Vertical Single-Page Quiz List or One-by-One)
    var activeQuestions by remember { mutableStateOf<List<QuizQuestionItem>>(emptyList()) }
    var userAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var currentScore by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }
    var quizViewMode by remember { mutableStateOf("continuous") } // "continuous" or "one_by_one"
    var oneByOneIndex by remember { mutableIntStateOf(0) }
    var activeStemBottomSheet by remember { mutableStateOf<Pair<String, String?>?>(null) } // Pair(stemText, stemId)

    // Question Bank Active Bank & Filter Selection
    var activeBankName by remember { mutableStateOf<String?>(null) }
    var qbStatusFilter by remember { mutableStateOf<Set<String>>(emptySet()) }

    // QB Typography & Appearance Settings (Font and Font Size for Question, Answer, and Explanation)
    val qbPrefs = remember(context) { context.getSharedPreferences("qb_typography_settings", Context.MODE_PRIVATE) }
    var qbQuestionFontSize by remember { mutableFloatStateOf(qbPrefs.getFloat("qb_q_font_size", 16f)) }
    var qbAnswerFontSize by remember { mutableFloatStateOf(qbPrefs.getFloat("qb_a_font_size", 14f)) }
    var qbExplanationFontSize by remember { mutableFloatStateOf(qbPrefs.getFloat("qb_e_font_size", 12f)) }

    var qbQuestionFont by remember { mutableStateOf(qbPrefs.getString("qb_q_font", "auto") ?: "auto") }
    var qbAnswerFont by remember { mutableStateOf(qbPrefs.getString("qb_a_font", "auto") ?: "auto") }
    var qbExplanationFont by remember { mutableStateOf(qbPrefs.getString("qb_e_font", "auto") ?: "auto") }
    var qbQuestionOrder by remember { mutableStateOf(qbPrefs.getString("qb_question_order", "serial") ?: "serial") } // "serial" or "random"

    var showQbTypographyDialog by remember { mutableStateOf(false) }

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

    fun getCleanPlaceLabel(placeNumber: Int, targetCourseId: String): String {
        val raw = getPlaceHeaderLabel(placeNumber, if (targetCourseId.isNotBlank() && targetCourseId != "all") setOf(targetCourseId) else emptySet())
        val clean = if (raw.contains(":")) raw.substringAfter(":").trim() else raw.replace(Regex("""(?i)^place\s*\d+\s*[-_:]?\s*"""), "").trim()
        return if (clean.isNotBlank()) clean else when (placeNumber) {
            1 -> "Word"
            2 -> "Meaning"
            3 -> "Synonym"
            else -> "Place $placeNumber"
        }
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
            if (initialSection != null && selectedSection == initialSection) {
                onBack()
            } else {
                selectedSection = null
            }
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
                    (activeBankName == null || q.bankName.equals(activeBankName, ignoreCase = true)) &&
                    (if (qbStatusFilter.isEmpty() || "all" in qbStatusFilter) true else {
                        val qSt = (q.status ?: "").trim().lowercase()
                        qbStatusFilter.any { filterSt ->
                            when (filterSt.lowercase()) {
                                "know" -> qSt == "know"
                                "confusion", "review" -> qSt == "confusion" || qSt == "review"
                                "dont_know" -> qSt == "dont_know"
                                "unrated" -> qSt.isBlank() || qSt == "unrated"
                                else -> qSt == filterSt.lowercase()
                            }
                        }
                    }) &&
                    (qbFilters1.isEmpty() || (q.filter1 != null && q.filter1 in qbFilters1)) &&
                    (qbFilters2.isEmpty() || (q.filter2 != null && q.filter2 in qbFilters2)) &&
                    (qbFilters3.isEmpty() || (q.filter3 != null && q.filter3 in qbFilters3))
                }
                val ordered = if (qbQuestionOrder == "random") filtered.shuffled() else filtered
                val selected = if (qbQuestionCount > 0) ordered.take(qbQuestionCount) else ordered
                selected.map { it.toQuizItem() }
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

                // 1. Question Bank
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

                // 2. Read Article
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

                // 3. Confusion Set
                item {
                    GameCategoryCard(
                        title = "Confusion Set",
                        icon = Icons.Default.CompareArrows,
                        badgeColor = Color(0xFF8B5CF6),
                        onClick = {
                            selectedSection = "confusion_set"
                        }
                    )
                }
            }
        } else {
            // DEDICATED SECTION VIEW
            if ((selectedSection != "read_article" || activeArticle == null) &&
                !(selectedSection == "question_bank" && activeQuestions.isEmpty() && !isQuizCompleted) &&
                selectedSection != "confusion_set"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (activeQuestions.isNotEmpty()) {
                                    activeQuestions = emptyList()
                                    isQuizCompleted = false
                                } else if (initialSection != null && selectedSection == initialSection) {
                                    onBack()
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
                                contentDescription = "Back",
                                tint = SlateText
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (selectedSection) {
                                "course_quiz" -> "Quiz test"
                                "question_bank" -> "Question Bank"
                                "read_article" -> "Read Article"
                                "confusion_set" -> "Confusion Set"
                                else -> "Practice"
                            },
                            fontFamily = PoppinsFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }

                    // In QB question sheet, filter button same as flashcard filter button
                    if (activeQuestions.isNotEmpty()) {
                        IconButton(
                            onClick = { showQbTypographyDialog = true },
                            modifier = Modifier.testTag("qb_topbar_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Question Sheet Filter & Settings",
                                tint = IndigoPrimary
                            )
                        }
                    }
                }
            }

            // Confusion Set View
            if (selectedSection == "confusion_set") {
                ConfusionSetScreen(
                    courses = courses,
                    words = words,
                    initialCourseId = activeCourseId,
                    getPlaceValue = { w, placeNum -> getPlace(w, placeNum) },
                    getPlaceLabel = { placeNum, cId -> getCleanPlaceLabel(placeNum, cId) },
                    onRateWord = onRateWord,
                    onBack = {
                        if (initialSection != null && selectedSection == initialSection) {
                            onBack()
                        } else {
                            selectedSection = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (selectedSection == "read_article") {
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
            } else if (selectedSection == "question_bank" && activeQuestions.isEmpty() && !isQuizCompleted) {
                QuestionBankDashboardView(
                    questions = questions,
                    driveQBFiles = if (driveQBFiles.isNotEmpty()) driveQBFiles else driveCourses,
                    isRefreshingDriveQB = isRefreshingDriveQB,
                    downloadingCourseFileIds = downloadingCourseFileIds,
                    onRefreshDriveQB = onRefreshDriveQB,
                    onDownloadDriveQB = onDownloadDriveQB,
                    onStartQuiz = { bName: String, status: String, f1: Set<String>, f2: Set<String>, f3: Set<String>, count: Int, order: String, viewMode: String ->
                        activeBankName = bName
                        qbStatusFilter = if (status.isBlank() || status == "all") emptySet() else setOf(status)
                        qbFilters1 = f1
                        qbFilters2 = f2
                        qbFilters3 = f3
                        qbQuestionCount = count
                        qbQuestionOrder = order
                        quizViewMode = viewMode
                        startQuizForCategory("question_bank")
                    },
                    onImportQBBytes = onImportQBBytes,
                    onDeleteQuestionsByBank = onDeleteQuestionsByBank,
                    onRenameBank = onRenameBank,
                    onAddSampleData = onAddSampleData,
                    onBackupQBProgress = onBackupQBProgress,
                    onRestoreQBProgress = onRestoreQBProgress
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
                    isQuestionBank = (selectedSection == "question_bank"),
                    onRetake = { startQuizForCategory(selectedSection ?: "course_quiz", gameStatusFilter, gameQuestionCount) },
                    onBackToBanks = {
                        activeQuestions = emptyList()
                        isQuizCompleted = false
                    },
                    onPracticeMissed = {
                        val missed = activeQuestions.filterIndexed { idx, q ->
                            val ans = userAnswers[idx]
                            ans == null || !isOptionTheAnswer(ans, 0, q.answer)
                        }
                        if (missed.isNotEmpty()) {
                            activeQuestions = missed
                            userAnswers = emptyMap()
                            currentScore = 0
                            isQuizCompleted = false
                        }
                    }
                )
            } else if (activeQuestions.isNotEmpty()) {
                val clampedOneByOneIdx = oneByOneIndex.coerceIn(0, (activeQuestions.size - 1).coerceAtLeast(0))
                val currentOneByOneQ = activeQuestions.getOrNull(clampedOneByOneIdx)

                Box(modifier = Modifier.fillMaxSize()) {
                    if (quizViewMode == "continuous") {
                        // Continuous Scrolling Mode: all questions in a vertical LazyColumn
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("quiz_vertical_list"),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                        ) {
                            // Header progress
                            item {
                                QuizProgressHeaderCard(
                                    answeredCount = userAnswers.size,
                                    totalCount = activeQuestions.size
                                )
                            }

                            // Continuous list of questions
                            itemsIndexed(activeQuestions) { qIdx, currentQ ->
                                QuizQuestionCardView(
                                    qIdx = qIdx,
                                    currentQ = currentQ,
                                    selectedOpt = userAnswers[qIdx],
                                    isAnswered = userAnswers[qIdx] != null,
                                    onOptionSelect = { opt, isCorrect ->
                                        userAnswers = userAnswers + (qIdx to opt)
                                        if (isCorrect) currentScore += 1
                                        if (currentQ.id.isNotBlank()) {
                                            if (currentQ.sheetType == "course_quiz" || words.any { it.id == currentQ.id }) {
                                                onRecordWordQuizAnswer(currentQ.id, isCorrect)
                                            }
                                            if (currentQ.sheetType == "qb" || selectedSection == "question_bank") {
                                                onUpdateQuestionStatus(currentQ.id, if (isCorrect) "know" else "dont_know")
                                            } else if (currentQ.sheetType != "course_quiz") {
                                                onRecordGameAnswer(currentQ.id, isCorrect)
                                            }
                                        }
                                    },
                                    onStatusUpdate = { status ->
                                        onUpdateQuestionStatus(currentQ.id, status)
                                    },
                                    wordHighlightColor = wordHighlightColor,
                                    qbQuestionFontSize = qbQuestionFontSize,
                                    qbQuestionFont = qbQuestionFont,
                                    qbAnswerFontSize = qbAnswerFontSize,
                                    qbAnswerFont = qbAnswerFont,
                                    qbExplanationFontSize = qbExplanationFontSize,
                                    qbExplanationFont = qbExplanationFont,
                                    selectedSection = selectedSection,
                                    onOpenStem = { stemText, stemId ->
                                        activeStemBottomSheet = Pair(stemText, stemId)
                                    }
                                )
                            }
                        }
                    } else {
                        // One-by-One Mode: single question displayed at a time
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header progress
                            QuizProgressHeaderCard(
                                answeredCount = userAnswers.size,
                                totalCount = activeQuestions.size
                            )

                            // Quick question jumper pills (1, 2, 3...)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                itemsIndexed(activeQuestions) { idx, q ->
                                    val isCur = idx == clampedOneByOneIdx
                                    val isAns = userAnswers.containsKey(idx)
                                    val selectedOpt = userAnswers[idx]
                                    val options = listOf(q.opt1, q.opt2, q.opt3, q.opt4, q.opt5).filter { it.isNotBlank() }
                                    val isCor = isAns && options.indices.any { oIdx ->
                                        options[oIdx] == selectedOpt && isOptionTheAnswer(options[oIdx], oIdx, q.answer)
                                    }

                                    val bg = when {
                                        isCur -> IndigoPrimary
                                        !isAns -> Color(0xFFF1F5F9)
                                        isCor -> EmeraldLight
                                        else -> RoseLight
                                    }
                                    val textColor = when {
                                        isCur -> Color.White
                                        !isAns -> SlateMuted
                                        isCor -> EmeraldSuccess
                                        else -> RoseError
                                    }

                                    Surface(
                                        onClick = { oneByOneIndex = idx },
                                        shape = RoundedCornerShape(8.dp),
                                        color = bg,
                                        border = if (isCur) BorderStroke(1.5.dp, IndigoPrimary) else null,
                                        modifier = Modifier.size(width = 38.dp, height = 34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }

                            // The Active Question Card
                            if (currentOneByOneQ != null) {
                                QuizQuestionCardView(
                                    qIdx = clampedOneByOneIdx,
                                    currentQ = currentOneByOneQ,
                                    selectedOpt = userAnswers[clampedOneByOneIdx],
                                    isAnswered = userAnswers[clampedOneByOneIdx] != null,
                                    onOptionSelect = { opt, isCorrect ->
                                        userAnswers = userAnswers + (clampedOneByOneIdx to opt)
                                        if (isCorrect) currentScore += 1
                                        if (currentOneByOneQ.id.isNotBlank()) {
                                            if (currentOneByOneQ.sheetType == "course_quiz" || words.any { it.id == currentOneByOneQ.id }) {
                                                onRecordWordQuizAnswer(currentOneByOneQ.id, isCorrect)
                                            }
                                            if (currentOneByOneQ.sheetType == "qb" || selectedSection == "question_bank") {
                                                onUpdateQuestionStatus(currentOneByOneQ.id, if (isCorrect) "know" else "dont_know")
                                            } else if (currentOneByOneQ.sheetType != "course_quiz") {
                                                onRecordGameAnswer(currentOneByOneQ.id, isCorrect)
                                            }
                                        }
                                    },
                                    onStatusUpdate = { status ->
                                        onUpdateQuestionStatus(currentOneByOneQ.id, status)
                                    },
                                    wordHighlightColor = wordHighlightColor,
                                    qbQuestionFontSize = qbQuestionFontSize,
                                    qbQuestionFont = qbQuestionFont,
                                    qbAnswerFontSize = qbAnswerFontSize,
                                    qbAnswerFont = qbAnswerFont,
                                    qbExplanationFontSize = qbExplanationFontSize,
                                    qbExplanationFont = qbExplanationFont,
                                    selectedSection = selectedSection,
                                    onOpenStem = { stemText, stemId ->
                                        activeStemBottomSheet = Pair(stemText, stemId)
                                    }
                                )
                            }

                            // Navigation Controls (Previous, Indicator, Next)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (clampedOneByOneIdx > 0) oneByOneIndex = clampedOneByOneIdx - 1 },
                                    enabled = clampedOneByOneIdx > 0,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (clampedOneByOneIdx > 0) IndigoPrimary else SlateBorder)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Previous", fontSize = 13.sp)
                                }

                                Text(
                                    text = "Question ${clampedOneByOneIdx + 1} of ${activeQuestions.size}",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateText
                                )

                                Button(
                                    onClick = {
                                        if (clampedOneByOneIdx < activeQuestions.size - 1) {
                                            oneByOneIndex = clampedOneByOneIdx + 1
                                        }
                                    },
                                    enabled = clampedOneByOneIdx < activeQuestions.size - 1,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                ) {
                                    Text("Next", fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Fixed Minimal Submit Button at bottom of Question Page (minimal and small in size per user request)
                    Surface(
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
                        shape = RoundedCornerShape(20.dp),
                        color = EmeraldSuccess,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .testTag("finish_quiz_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Submit",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Floating Stem Button (ONLY if current question in one-by-one mode has a stem!)
                    if (quizViewMode == "one_by_one" && currentOneByOneQ != null && !currentOneByOneQ.stem.isNullOrBlank()) {
                        Surface(
                            onClick = { activeStemBottomSheet = Pair(currentOneByOneQ.stem, currentOneByOneQ.stemId) },
                            shape = RoundedCornerShape(20.dp),
                            color = IndigoPrimary,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 14.dp, bottom = 12.dp)
                                .testTag("floating_stem_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Read Stem",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (!currentOneByOneQ.stemId.isNullOrBlank()) "Stem: ${currentOneByOneQ.stemId}" else "Stem",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
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

    if (showQbTypographyDialog) {
        val currentBankQuestions = remember(questions, activeBankName) {
            if (activeBankName.isNullOrBlank()) questions else questions.filter { it.bankName.equals(activeBankName, ignoreCase = true) }
        }
        val sheetF1Label = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter1Label }.firstOrNull { it.isNotBlank() } ?: "Course"
        }
        val sheetF2Label = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter2Label }.firstOrNull { it.isNotBlank() } ?: "Q.type"
        }
        val sheetF3Label = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter3Label }.firstOrNull { it.isNotBlank() } ?: "Session"
        }
        val sheetF1Values = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter1 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val sheetF2Values = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter2 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val sheetF3Values = remember(currentBankQuestions) {
            currentBankQuestions.mapNotNull { it.filter3 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }

        QbTypographyFilterDialog(
            questionFontSize = qbQuestionFontSize,
            answerFontSize = qbAnswerFontSize,
            explanationFontSize = qbExplanationFontSize,
            questionFont = qbQuestionFont,
            answerFont = qbAnswerFont,
            explanationFont = qbExplanationFont,
            questionOrder = qbQuestionOrder,
            onOrderChange = { newOrder ->
                qbQuestionOrder = newOrder
                qbPrefs.edit().putString("qb_question_order", newOrder).apply()
                if (activeQuestions.isNotEmpty()) {
                    activeQuestions = if (newOrder == "random") {
                        activeQuestions.shuffled()
                    } else {
                        activeQuestions.sortedBy { it.id }
                    }
                }
            },
            quizViewMode = quizViewMode,
            onViewModeChange = { newMode ->
                quizViewMode = newMode
                qbPrefs.edit().putString("qb_view_mode", newMode).apply()
            },
            questionCount = qbQuestionCount,
            onQuestionCountChange = { newCount ->
                qbQuestionCount = newCount
                qbPrefs.edit().putInt("qb_question_count", newCount).apply()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            totalAvailableQuestions = currentBankQuestions.size,
            selectedStatuses = qbStatusFilter,
            onStatusToggle = { st ->
                qbStatusFilter = if (st in qbStatusFilter) qbStatusFilter - st else qbStatusFilter + st
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onStatusClear = {
                qbStatusFilter = emptySet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            f1Label = sheetF1Label,
            f1Values = sheetF1Values,
            selectedFilters1 = qbFilters1,
            onFilter1Toggle = { opt ->
                qbFilters1 = if (opt in qbFilters1) qbFilters1 - opt else qbFilters1 + opt
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter1SelectAll = {
                qbFilters1 = sheetF1Values.toSet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter1Clear = {
                qbFilters1 = emptySet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            f2Label = sheetF2Label,
            f2Values = sheetF2Values,
            selectedFilters2 = qbFilters2,
            onFilter2Toggle = { opt ->
                qbFilters2 = if (opt in qbFilters2) qbFilters2 - opt else qbFilters2 + opt
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter2SelectAll = {
                qbFilters2 = sheetF2Values.toSet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter2Clear = {
                qbFilters2 = emptySet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            f3Label = sheetF3Label,
            f3Values = sheetF3Values,
            selectedFilters3 = qbFilters3,
            onFilter3Toggle = { opt ->
                qbFilters3 = if (opt in qbFilters3) qbFilters3 - opt else qbFilters3 + opt
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter3SelectAll = {
                qbFilters3 = sheetF3Values.toSet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onFilter3Clear = {
                qbFilters3 = emptySet()
                if (selectedSection == "question_bank") {
                    startQuizForCategory("question_bank")
                }
            },
            onApplySettings = { qSize, aSize, eSize, qFont, aFont, eFont ->
                qbQuestionFontSize = qSize
                qbAnswerFontSize = aSize
                qbExplanationFontSize = eSize
                qbQuestionFont = qFont
                qbAnswerFont = aFont
                qbExplanationFont = eFont
                qbPrefs.edit()
                    .putFloat("qb_q_font_size", qSize)
                    .putFloat("qb_a_font_size", aSize)
                    .putFloat("qb_e_font_size", eSize)
                    .putString("qb_q_font", qFont)
                    .putString("qb_a_font", aFont)
                    .putString("qb_e_font", eFont)
                    .apply()
            },
            onDismiss = { showQbTypographyDialog = false }
        )
    }

    if (activeStemBottomSheet != null) {
        val (stemText, stemId) = activeStemBottomSheet!!
        ModalBottomSheet(
            onDismissRequest = { activeStemBottomSheet = null },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = Color.White,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = SlateBorder)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Stem",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (!stemId.isNullOrBlank()) "Stem: ${stemId.uppercase()}" else "Question Stem / Scenario",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Text(
                                text = "Reference context for answering",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = SlateMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = { activeStemBottomSheet = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stemText,
                        fontFamily = resolveQbFontFamily(qbQuestionFont, stemText),
                        fontSize = qbQuestionFontSize.sp,
                        color = SlateText,
                        lineHeight = (qbQuestionFontSize * 1.45f).sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
}

