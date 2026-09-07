package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.ui.theme.*

@Composable
fun GamePracticeScreen(
    games: List<GamePracticeEntity>,
    questions: List<QuestionBankEntity>,
    onCompleteQuiz: (score: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Top Tabs: "odd_one_out", "analogy", "practice", "question_bank"
    var selectedTab by remember { mutableStateOf("odd_one_out") }

    // Active Quiz State
    var activeQuestions by remember { mutableStateOf<List<QuizQuestionItem>>(emptyList()) }
    var currentQuestionIdx by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var currentScore by remember { mutableStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    // Question Bank Config
    var qbFilter1 by remember { mutableStateOf<String?>(null) }
    var qbFilter2 by remember { mutableStateOf<String?>(null) }
    var qbFilter3 by remember { mutableStateOf<String?>(null) }
    var qbQuestionCount by remember { mutableIntStateOf(5) }

    fun startQuizForCategory(tab: String) {
        val list = when (tab) {
            "odd_one_out" -> games.filter { it.sheetType == "odd_one_out" }.map { it.toQuizItem() }
            "analogy" -> games.filter { it.sheetType == "analogy" }.map { it.toQuizItem() }
            "practice" -> games.filter { it.sheetType == "practice" }.map { it.toQuizItem() }
            "question_bank" -> {
                val filtered = questions.filter { q ->
                    (qbFilter1 == null || q.filter1 == qbFilter1) &&
                    (qbFilter2 == null || q.filter2 == qbFilter2) &&
                    (qbFilter3 == null || q.filter3 == qbFilter3)
                }.shuffled().take(qbQuestionCount)
                filtered.map { it.toQuizItem() }
            }
            else -> emptyList()
        }
        activeQuestions = list
        currentQuestionIdx = 0
        selectedOption = null
        isSubmitted = false
        currentScore = 0
        isQuizCompleted = false
    }

    // Auto-start or refresh when tab switches
    LaunchedEffect(selectedTab, games, questions) {
        if (selectedTab != "question_bank") {
            startQuizForCategory(selectedTab)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "odd_one_out" to "Odd One Out",
                "analogy" to "Analogy",
                "practice" to "Practice Quiz",
                "question_bank" to "Question Bank (QB)"
            )
            tabs.forEach { (tabId, label) ->
                FilterChip(
                    selected = selectedTab == tabId,
                    onClick = {
                        selectedTab = tabId
                        if (tabId != "question_bank") {
                            startQuizForCategory(tabId)
                        } else {
                            activeQuestions = emptyList() // Show filter setup first
                            isQuizCompleted = false
                        }
                    },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Question Bank Filter Config View
        if (selectedTab == "question_bank" && activeQuestions.isEmpty() && !isQuizCompleted) {
            QuestionBankConfigView(
                questions = questions,
                selectedFilter1 = qbFilter1,
                selectedFilter2 = qbFilter2,
                selectedFilter3 = qbFilter3,
                questionCount = qbQuestionCount,
                onFilter1Change = { qbFilter1 = it },
                onFilter2Change = { qbFilter2 = it },
                onFilter3Change = { qbFilter3 = it },
                onCountChange = { qbQuestionCount = it },
                onStart = { startQuizForCategory("question_bank") }
            )
            return@Column
        }

        // Active Quiz or Result View
        if (isQuizCompleted) {
            QuizSummaryView(
                score = currentScore,
                total = activeQuestions.size,
                onRetake = { startQuizForCategory(selectedTab) }
            )
        } else if (activeQuestions.isNotEmpty()) {
            val currentQ = activeQuestions[currentQuestionIdx]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("game_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    item {
                        // Header progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${currentQuestionIdx + 1} of ${activeQuestions.size}",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = "Score: $currentScore",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { (currentQuestionIdx + 1).toFloat() / activeQuestions.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = IndigoPrimary,
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Question Title
                        Text(
                            text = currentQ.question,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Options
                        val options = listOf(currentQ.opt1, currentQ.opt2, currentQ.opt3, currentQ.opt4)
                            .filter { it.isNotBlank() }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            options.forEachIndexed { idx, opt ->
                                val isSelected = selectedOption == opt
                                val isCorrect = opt.equals(currentQ.answer, ignoreCase = true)

                                val (btnBg, btnBorder, btnText) = when {
                                    !isSubmitted && isSelected -> Triple(IndigoLight, IndigoPrimary, IndigoPrimary)
                                    isSubmitted && isCorrect -> Triple(EmeraldLight, EmeraldSuccess, EmeraldSuccess)
                                    isSubmitted && isSelected && !isCorrect -> Triple(RoseLight, RoseError, RoseError)
                                    else -> Triple(Color(0xFFF8FAFC), SlateBorder, SlateText)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(btnBg)
                                        .border(1.5.dp, btnBorder, RoundedCornerShape(14.dp))
                                        .clickable(enabled = !isSubmitted) {
                                            selectedOption = opt
                                            isSubmitted = true
                                            if (isCorrect) {
                                                currentScore += 1
                                            }
                                        }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${('A' + idx)}.  $opt",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected || (isSubmitted && isCorrect)) FontWeight.Bold else FontWeight.Medium,
                                            color = btnText,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isSubmitted && isCorrect) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Correct",
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else if (isSubmitted && isSelected && !isCorrect) {
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

                        // Explanation
                        AnimatedVisibility(visible = isSubmitted && !currentQ.explanation.isNullOrBlank()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = IndigoLight),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC7D2FE)))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "EXPLANATION",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentQ.explanation ?: "",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = SlateText,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Button
                    item {
                        if (isSubmitted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (currentQuestionIdx < activeQuestions.size - 1) {
                                        currentQuestionIdx += 1
                                        selectedOption = null
                                        isSubmitted = false
                                    } else {
                                        isQuizCompleted = true
                                        onCompleteQuiz(currentScore, activeQuestions.size)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                            ) {
                                Text(
                                    text = if (currentQuestionIdx < activeQuestions.size - 1) "Next Question" else "Finish & View Results",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
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
}

@Composable
private fun QuestionBankConfigView(
    questions: List<QuestionBankEntity>,
    selectedFilter1: String?,
    selectedFilter2: String?,
    selectedFilter3: String?,
    questionCount: Int,
    onFilter1Change: (String?) -> Unit,
    onFilter2Change: (String?) -> Unit,
    onFilter3Change: (String?) -> Unit,
    onCountChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    val f1Label = questions.firstOrNull()?.filter1Label ?: "Category"
    val f2Label = questions.firstOrNull()?.filter2Label ?: "Difficulty"
    val f3Label = questions.firstOrNull()?.filter3Label ?: "Source"

    val f1Values = questions.mapNotNull { it.filter1 }.distinct()
    val f2Values = questions.mapNotNull { it.filter2 }.distinct()
    val f3Values = questions.mapNotNull { it.filter3 }.distinct()

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
                        text = "Customize Question Bank Test",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    Text(
                        text = "Configure filters and set the number of questions before starting.",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = SlateMuted
                    )

                    // Filter 1
                    if (f1Values.isNotEmpty()) {
                        Text(text = f1Label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter1 == null,
                                onClick = { onFilter1Change(null) },
                                label = { Text("All") },
                                shape = CircleShape
                            )
                            f1Values.forEach { v ->
                                FilterChip(
                                    selected = selectedFilter1 == v,
                                    onClick = { onFilter1Change(v) },
                                    label = { Text(v) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }

                    // Filter 2
                    if (f2Values.isNotEmpty()) {
                        Text(text = f2Label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter2 == null,
                                onClick = { onFilter2Change(null) },
                                label = { Text("All") },
                                shape = CircleShape
                            )
                            f2Values.forEach { v ->
                                FilterChip(
                                    selected = selectedFilter2 == v,
                                    onClick = { onFilter2Change(v) },
                                    label = { Text(v) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }

                    // Filter 3
                    if (f3Values.isNotEmpty()) {
                        Text(text = f3Label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter3 == null,
                                onClick = { onFilter3Change(null) },
                                label = { Text("All") },
                                shape = CircleShape
                            )
                            f3Values.forEach { v ->
                                FilterChip(
                                    selected = selectedFilter3 == v,
                                    onClick = { onFilter3Change(v) },
                                    label = { Text(v) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }

                    // Question Count Selector
                    Text(
                        text = "Number of Questions",
                        fontSize = 12.sp,
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onStart,
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
                            Text("Start Practice Test", fontWeight = FontWeight.Bold)
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "Practice Complete!",
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SlateText
            )

            Text(
                text = "Your score: $score / $total ($percent%)",
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (percent >= 70) EmeraldSuccess else RoseError
            )

            Text(
                text = if (percent >= 70) "Awesome job! You are memorizing well." else "Keep practicing to reinforce your memory.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = SlateMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

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

private data class QuizQuestionItem(
    val question: String,
    val opt1: String,
    val opt2: String,
    val opt3: String,
    val opt4: String,
    val answer: String,
    val explanation: String?
)

private fun GamePracticeEntity.toQuizItem() = QuizQuestionItem(
    question = question,
    opt1 = opt1,
    opt2 = opt2,
    opt3 = opt3,
    opt4 = opt4,
    answer = answer,
    explanation = explanation
)

private fun QuestionBankEntity.toQuizItem() = QuizQuestionItem(
    question = question,
    opt1 = opt1,
    opt2 = opt2,
    opt3 = opt3,
    opt4 = opt4,
    answer = answer,
    explanation = explanation
)
