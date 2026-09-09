package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
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
import com.example.data.model.ArticleEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import com.example.ui.theme.*

@Composable
fun GamePracticeScreen(
    games: List<GamePracticeEntity>,
    questions: List<QuestionBankEntity>,
    onCompleteQuiz: (score: Int, total: Int) -> Unit,
    articles: List<ArticleEntity> = emptyList(),
    activeArticle: ArticleEntity? = null,
    words: List<VocabularyWordEntity> = emptyList(),
    onSelectArticle: (ArticleEntity?) -> Unit = {},
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit = { _, _, _, _ -> },
    onDeleteArticle: (String) -> Unit = {},
    onRateWord: (wordId: String, status: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Top Section: null means list menu of categories; non-null opens dedicated section
    var selectedSection by remember { mutableStateOf<String?>(null) }

    // Intercept back button when in a game/practice section to return to Games menu
    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

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

    fun startQuizForCategory(section: String) {
        val list = when (section) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (selectedSection == null) {
            // LIST VIEW: User sees the list-type buttons for each game & practice section
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "Games & Practice",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose a practice mode",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }
                }

                // 1. Odd One Out
                item {
                    GameCategoryCard(
                        title = "Odd One Out",
                        description = "Find the odd word out",
                        count = games.count { it.sheetType == "odd_one_out" },
                        icon = Icons.Default.FilterAlt,
                        badgeColor = Color(0xFF8B5CF6),
                        onClick = {
                            selectedSection = "odd_one_out"
                            startQuizForCategory("odd_one_out")
                        }
                    )
                }

                // 2. Analogy Practice
                item {
                    GameCategoryCard(
                        title = "Analogy Practice",
                        description = "Match word relationship pairs",
                        count = games.count { it.sheetType == "analogy" },
                        icon = Icons.Default.CompareArrows,
                        badgeColor = Color(0xFF0284C7),
                        onClick = {
                            selectedSection = "analogy"
                            startQuizForCategory("analogy")
                        }
                    )
                }

                // 3. Practice Quiz
                item {
                    GameCategoryCard(
                        title = "Practice Quiz",
                        description = "Test your vocabulary knowledge",
                        count = games.count { it.sheetType == "practice" },
                        icon = Icons.Default.Quiz,
                        badgeColor = EmeraldSuccess,
                        onClick = {
                            selectedSection = "practice"
                            startQuizForCategory("practice")
                        }
                    )
                }

                // 4. Question Bank
                item {
                    GameCategoryCard(
                        title = "Question Bank (QB)",
                        description = "Practice targeted exam questions",
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
                        description = "Read articles with highlights",
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

            // Read Article View (Integrated inside Games)
            if (selectedSection == "read_article") {
                ArticleReaderView(
                    articles = articles,
                    activeArticle = activeArticle,
                    words = words,
                    onSelectArticle = onSelectArticle,
                    onSaveArticle = onSaveArticle,
                    onDeleteArticle = onDeleteArticle,
                    onRateWord = onRateWord,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (selectedSection == "question_bank" && activeQuestions.isEmpty() && !isQuizCompleted) {
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
            } else if (isQuizCompleted) {
                QuizSummaryView(
                    score = currentScore,
                    total = activeQuestions.size,
                    onRetake = { startQuizForCategory(selectedSection ?: "odd_one_out") }
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
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = "Score: $currentScore",
                                fontFamily = PoppinsFontFamily,
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
                            fontFamily = PoppinsFontFamily,
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
                                            fontFamily = PoppinsFontFamily,
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
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentQ.explanation ?: "",
                                        fontFamily = PoppinsFontFamily,
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
                                    fontFamily = PoppinsFontFamily,
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
                        fontFamily = PoppinsFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    Text(
                        text = "Set filters before starting test",
                        fontFamily = PoppinsFontFamily,
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
                fontFamily = PoppinsFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SlateText
            )

            Text(
                text = "Your score: $score / $total ($percent%)",
                fontFamily = PoppinsFontFamily,
                fontSize = 17.sp,
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

@Composable
private fun GameCategoryCard(
    title: String,
    description: String,
    count: Int,
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = SlateMuted
                )
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
