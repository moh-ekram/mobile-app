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
import com.example.ui.theme.*
import com.example.ui.util.LatexMathParser
import com.example.ui.util.LatexMathText
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun QuizProgressHeaderCard(
    answeredCount: Int,
    totalCount: Int,
    quizViewMode: String = "continuous",
    onViewModeChange: (String) -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder),
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
                    text = "$answeredCount of $totalCount Answered",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPrimary
                )
                val percent = if (totalCount > 0) (answeredCount * 100 / totalCount) else 0
                Text(
                    text = "$percent%",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = {
                    if (totalCount > 0) answeredCount.toFloat() / totalCount else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = IndigoPrimary,
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
internal fun QuizQuestionCardView(
    qIdx: Int,
    currentQ: QuizQuestionItem,
    selectedOpt: String?,
    isAnswered: Boolean,
    onOptionSelect: (String, Boolean) -> Unit,
    onStatusUpdate: (String) -> Unit,
    wordHighlightColor: String,
    qbQuestionFontSize: Float,
    qbQuestionFont: String,
    qbAnswerFontSize: Float,
    qbAnswerFont: String,
    qbExplanationFontSize: Float,
    qbExplanationFont: String,
    selectedSection: String?,
    onOpenStem: (stemText: String, stemId: String?) -> Unit
) {
    val options = remember(currentQ) {
        listOf(currentQ.opt1, currentQ.opt2, currentQ.opt3, currentQ.opt4, currentQ.opt5)
            .filter { it.isNotBlank() }
    }

    val cardBorder = BorderStroke(1.dp, SlateBorder)
    val cardBg = Color.White

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_question_card_$qIdx")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Question ID badge + Stem chip (Answer eye button removed per user request)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(IndigoLight)
                            .padding(horizontal = 9.dp, vertical = 3.5.dp)
                    ) {
                        val displayId = when {
                            currentQ.id.isNotBlank() && !currentQ.id.startsWith("qb_") -> currentQ.id
                            currentQ.id.startsWith("qb_") && currentQ.id.contains("_") -> "ID: ${currentQ.id.substringAfterLast("_")}"
                            else -> "ID: ${qIdx + 1}"
                        }
                        Text(
                            text = displayId,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }

                    // Stem button: ONLY shown if this question has a stem!
                    if (!currentQ.stem.isNullOrBlank()) {
                        Surface(
                            onClick = { onOpenStem(currentQ.stem, currentQ.stemId) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                            modifier = Modifier.testTag("read_stem_button_${currentQ.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Read Stem",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (!currentQ.stemId.isNullOrBlank()) "Stem: ${currentQ.stemId}" else "Read Stem",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question Title with Colorized [Word] & Customizable Font + Font Size
            ColorizedQuestionText(
                question = currentQ.question,
                colorChoice = wordHighlightColor,
                fontSize = qbQuestionFontSize.sp,
                fontFamily = resolveQbFontFamily(qbQuestionFont, currentQ.question)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Options: Modern lighter tints, stroke removed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { optIdx, opt ->
                    val isThisOptionSelected = selectedOpt == opt
                    val isThisOptionAnswer = isOptionTheAnswer(opt, optIdx, currentQ.answer)

                    val (btnBg, btnText) = when {
                        !isAnswered -> Pair(Color(0xFFF1F5F9), SlateText)
                        isThisOptionAnswer -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D)) // Modern light emerald with crisp dark emerald text
                        isThisOptionSelected && !isThisOptionAnswer -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C)) // Modern light rose with crisp dark red text
                        else -> Pair(Color(0xFFF8FAFC), Color(0xFF94A3B8))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(btnBg)
                            .clickable(enabled = !isAnswered) {
                                val isCorrect = isOptionTheAnswer(opt, optIdx, currentQ.answer)
                                onOptionSelect(opt, isCorrect)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        LatexMathText(
                            text = "${('A' + optIdx)}.  $opt",
                            fontFamily = resolveQbFontFamily(qbAnswerFont, opt),
                            fontSize = qbAnswerFontSize.sp,
                            fontWeight = if (isThisOptionSelected || (isAnswered && isThisOptionAnswer)) FontWeight.Bold else FontWeight.Medium,
                            color = btnText,
                            lineHeight = (qbAnswerFontSize * 1.35f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Explanation Card with Customizable Font + Font Size & LaTeX Equation Support
            if (isAnswered && !currentQ.explanation.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoLight),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE))
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
                        LatexMathText(
                            text = currentQ.explanation ?: "",
                            fontFamily = resolveQbFontFamily(qbExplanationFont, currentQ.explanation ?: ""),
                            fontSize = qbExplanationFontSize.sp,
                            fontWeight = FontWeight.Normal,
                            color = SlateText,
                            lineHeight = (qbExplanationFontSize * 1.4f).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuizSummaryView(
    score: Int,
    total: Int,
    pooledScore: Int? = null,
    pooledTotal: Int? = null,
    isQuizTest: Boolean = false,
    isQuestionBank: Boolean = false,
    onRetake: () -> Unit,
    onBackToBanks: (() -> Unit)? = null,
    onPracticeMissed: (() -> Unit)? = null
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

            if (onPracticeMissed != null && score < total) {
                OutlinedButton(
                    onClick = onPracticeMissed,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                    border = BorderStroke(1.dp, RoseBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = RoseError)
                        Text("Practice Missed (${total - score})", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isQuestionBank && onBackToBanks != null) {
                OutlinedButton(
                    onClick = onBackToBanks,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary),
                    border = BorderStroke(1.dp, IndigoPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = IndigoPrimary)
                        Text("Back to Question Banks", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseQuizConfigView(
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
                            "All Words"
                        } else {
                            selectedStatuses.joinToString(", ") {
                                when (it) {
                                    "know" -> "Know"
                                    "confusion", "confused" -> "Confused"
                                    "dont_know" -> "Don't Know"
                                    "unrated", "unread" -> "Unrated"
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
                                Triple("all", "All Words", IndigoPrimary to IndigoLight),
                                Triple("unrated", "Unrated", SlateMuted to Color(0xFFF1F5F9)),
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
                                    repeat(2 - rowPair.size) {
                                        Spacer(modifier = Modifier.weight(1f))
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


