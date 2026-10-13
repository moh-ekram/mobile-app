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
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal fun isWordMatchingFlashcardStatus(word: VocabularyWordEntity, selectedStatuses: Set<String>): Boolean {
    val normStatus = when (word.status.lowercase().trim()) {
        "know", "learned" -> "know"
        "confusion", "confused" -> "confusion"
        "dont_know", "needs review", "dontknow" -> "dont_know"
        else -> "unrated"
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
            "unrated", "unread" -> normStatus == "unrated"
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
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier
) {
    val highlightColor = Color(0xFFDC2626) // Fixed red

    val annotated = remember(question, highlightColor, fontFamily) {
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
                    val effectiveWordFont = fontFamily ?: (if (isBengali) KalpurushFontFamily else PoppinsFontFamily)
                    pushStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = effectiveWordFont,
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

    val effectiveFont = fontFamily ?: (if (isBengaliText(question)) KalpurushFontFamily else PoppinsFontFamily)
    Text(
        text = annotated,
        fontFamily = effectiveFont,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = SlateText,
        lineHeight = (fontSize.value * 1.35f).sp,
        modifier = modifier
    )
}

internal fun resolveQbFontFamily(fontKey: String, sampleText: String): FontFamily {
    return when (fontKey.lowercase()) {
        "poppins" -> PoppinsFontFamily
        "kalpurush" -> KalpurushFontFamily
        "aikya" -> AikyaFontFamily
        "serif" -> FontFamily.Serif
        "sans", "sans-serif" -> FontFamily.SansSerif
        "mono", "monospace" -> FontFamily.Monospace
        else -> if (isBengaliText(sampleText)) KalpurushFontFamily else PoppinsFontFamily
    }
}

@Composable
internal fun GameCategoryCard(
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


internal data class QuizQuestionItem(
    val id: String = "",
    val sheetType: String = "",
    val question: String,
    val opt1: String,
    val opt2: String,
    val opt3: String,
    val opt4: String,
    val opt5: String = "",
    val answer: String,
    val explanation: String?,
    val stem: String? = null,
    val stemId: String? = null,
    val lastAttemptStatus: String? = "not_studied",
    val bankName: String = "",
    val wordStatus: String = "unrated"
)

internal fun isOptionTheAnswer(opt: String, optIdx: Int, answer: String): Boolean {
    val cleanOpt = opt.replace(Regex("""^\s*#\s*"""), "").replace(Regex("""\s*#\s*$"""), "").trim()
    val cleanAns = answer.replace(Regex("""^\s*#\s*"""), "").replace(Regex("""\s*#\s*$"""), "").trim()
    if (cleanOpt.equals(cleanAns, ignoreCase = true)) return true

    val letter = ('A' + optIdx).toString()
    if (cleanAns.equals(letter, ignoreCase = true)) return true
    if (cleanAns.equals("($letter)", ignoreCase = true)) return true
    if (cleanAns.equals("${optIdx + 1}", ignoreCase = true)) return true
    if (cleanAns.equals("Opt${optIdx + 1}", ignoreCase = true)) return true
    if (cleanAns.equals("Option ${optIdx + 1}", ignoreCase = true)) return true
    if (cleanAns.equals("Option${optIdx + 1}", ignoreCase = true)) return true

    if (cleanAns.startsWith("$letter.", ignoreCase = true) || cleanAns.startsWith("$letter)", ignoreCase = true) || cleanAns.startsWith("($letter)", ignoreCase = true)) {
        val sub = cleanAns.replace(Regex("""^(\([A-Za-z]\)|[A-Za-z][\.\)])\s*"""), "").trim()
        if (sub.equals(cleanOpt, ignoreCase = true) || sub.isBlank()) return true
    }
    return false
}

internal fun GamePracticeEntity.toQuizItem(): QuizQuestionItem {
    val resolved = com.example.data.parser.QuestionAnswerResolver.resolve(
        rawOpt1 = opt1,
        rawOpt2 = opt2,
        rawOpt3 = opt3,
        rawOpt4 = opt4,
        rawOpt5 = opt5,
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
        opt5 = resolved.cleanOpt5,
        answer = resolved.correctAnswer,
        explanation = explanation,
        lastAttemptStatus = lastAttemptStatus
    )
}

internal fun QuestionBankEntity.toQuizItem(): QuizQuestionItem {
    val resolved = com.example.data.parser.QuestionAnswerResolver.resolve(
        rawOpt1 = opt1,
        rawOpt2 = opt2,
        rawOpt3 = opt3,
        rawOpt4 = opt4,
        rawOpt5 = opt5,
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
        opt5 = resolved.cleanOpt5,
        answer = resolved.correctAnswer,
        explanation = explanation,
        stem = stem,
        stemId = stemId,
        lastAttemptStatus = "not_studied",
        bankName = bankName,
        wordStatus = status
    )
}

