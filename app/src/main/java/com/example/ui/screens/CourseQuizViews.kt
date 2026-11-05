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
    onResetAnswer: () -> Unit = {},
    wordHighlightColor: String = "red",
    qbQuestionFontSize: Float = 16f,
    qbQuestionFont: String = "auto",
    qbAnswerFontSize: Float = 14f,
    qbAnswerFont: String = "auto",
    qbExplanationFontSize: Float = 12f,
    qbExplanationFont: String = "auto",
    selectedSection: String? = null,
    onOpenStem: (stemText: String, stemId: String?) -> Unit = { _, _ -> }
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
            // Top Row: Question ID badge (Clickable to reset answer when answered) + Stem chip
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
                            .background(if (isAnswered) Color(0xFFE0E7FF) else IndigoLight)
                            .clickable(enabled = isAnswered) {
                                onResetAnswer()
                            }
                            .padding(horizontal = 9.dp, vertical = 3.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            if (isAnswered) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Answer",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
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

            // Question Title with Colorized [Word] / Status Color & Customizable Font + Font Size
            ColorizedQuestionText(
                question = currentQ.question,
                colorChoice = wordHighlightColor,
                fontSize = qbQuestionFontSize.sp,
                fontFamily = resolveQbFontFamily(qbQuestionFont, currentQ.question),
                status = currentQ.wordStatus,
                isQuestionBank = (currentQ.sheetType == "qb" || selectedSection == "question_bank")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
    pooledStats: Triple<Int, Int, Int> = Triple(0, 0, 0),
    questionOrder: String = "serial",
    onOrderChange: (String) -> Unit = {},
    quizViewMode: String = "continuous",
    onViewModeChange: (String) -> Unit = {},
    getPlace1: (VocabularyWordEntity) -> String,
    getPlace2: (VocabularyWordEntity) -> String,
    onStart: () -> Unit
) {
    var isCourseExpanded by remember { mutableStateOf(true) }
    var isGroupExpanded by remember { mutableStateOf(false) }

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

    val matchingWords = remember(groupFilteredWords, selectedStatuses) {
        val base = groupFilteredWords.filter { word ->
            isWordMatchingFlashcardStatus(word, selectedStatuses)
        }
        base.filter { getPlace1(it).isNotBlank() && getPlace2(it).isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
    ) {
        // Filter Options Card - Always Open per user requirement
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_test_filter_grid")
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
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(18.dp)
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

                        // Eligible count badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndigoLight,
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                        ) {
                            Text(
                                text = "${matchingWords.size} Words",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 1. SELECT QUIZ / COURSE (Collapsible / Foldable per user request)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isCourseExpanded = !isCourseExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Quiz / Course",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                                Text(
                                    text = if (selectedCourseIds.isEmpty() || "all" in selectedCourseIds) "(All)" else "(${selectedCourseIds.size})",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                            Icon(
                                imageVector = if (isCourseExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCourseExpanded) "Collapse Courses" else "Expand Courses",
                                tint = SlateMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = isCourseExpanded) {
                            val isAllCoursesSelected = selectedCourseIds.isEmpty() || "all" in selectedCourseIds
                            val courseItems = listOf("all" to "All") + courses.map { it.id to it.title }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 2. GROUPS (Collapsible / Foldable per user request)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isGroupExpanded = !isGroupExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Group",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                                Text(
                                    text = if (selectedGroups.isEmpty() || "all" in selectedGroups) "(All)" else "(${selectedGroups.size})",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                            Icon(
                                imageVector = if (isGroupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isGroupExpanded) "Collapse Groups" else "Expand Groups",
                                tint = SlateMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = isGroupExpanded) {
                            val isAllGroupsSelected = selectedGroups.isEmpty() || "all" in selectedGroups
                            val groupItems = listOf("all") + availableGroups

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 3. STATUS FILTER (Small Icon Buttons matching Flashcard / other pages per user request)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            val isAllStatusesSelected = selectedStatuses.isEmpty() || "all" in selectedStatuses
                            Text(
                                text = if (isAllStatusesSelected) "All Words" else "${selectedStatuses.size} selected",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }

                        val isAllStatusesSelected = selectedStatuses.isEmpty() || "all" in selectedStatuses
                        val statusList = listOf(
                            Triple("all", Icons.Default.DoneAll, IndigoPrimary to IndigoLight),
                            Triple("unrated", Icons.Default.HelpOutline, SlateMuted to Color(0xFFF1F5F9)),
                            Triple("know", Icons.Default.CheckCircle, EmeraldSuccess to EmeraldLight),
                            Triple("confusion", Icons.Default.Psychology, AmberWarning to AmberLight),
                            Triple("dont_know", Icons.Default.Cancel, RoseError to RoseLight)
                        )

                        // 5 small icon buttons in a single row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusList.forEach { (stKey, icon, colorPair) ->
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
                                        .height(44.dp)
                                        .testTag("quiz_test_status_$stKey")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = stKey,
                                            tint = if (isSelected) brandColor else SlateMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // 4. ORDER & VIEW MODE (Serial/Random and Scroll/1-by-1/Reels per user request)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Order selection
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Order",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                    .padding(3.dp)
                            ) {
                                listOf("serial" to "Serial", "random" to "Random").forEach { (ordKey, ordLabel) ->
                                    val isSel = questionOrder == ordKey
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) IndigoPrimary else Color.Transparent)
                                            .clickable { onOrderChange(ordKey) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ordLabel,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) Color.White else SlateText
                                        )
                                    }
                                }
                            }
                        }

                        // View Mode selection
                        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "View Style",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                    .padding(3.dp)
                            ) {
                                listOf(
                                    "continuous" to "Scroll",
                                    "one_by_one" to "1-by-1",
                                    "reels" to "Reels"
                                ).forEach { (vmKey, vmLabel) ->
                                    val isSel = quizViewMode == vmKey
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) IndigoPrimary else Color.Transparent)
                                            .clickable { onViewModeChange(vmKey) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = vmLabel,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) Color.White else SlateText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Question Limit Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Question Limit",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                        // Numbers
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


