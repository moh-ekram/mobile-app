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

@Composable
fun ColorizedQuestionText(
    question: String,
    colorChoice: String = "red",
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontFamily: FontFamily? = null,
    status: String? = null,
    isQuestionBank: Boolean = false,
    modifier: Modifier = Modifier
) {
    val normStatus = status?.lowercase()?.trim() ?: "unrated"
    val statusColor = when (normStatus) {
        "know" -> EmeraldSuccess
        "confusion", "confused", "review" -> AmberWarning
        "dont_know" -> RoseError
        else -> SlateText
    }
    val highlightColor = if (isQuestionBank) {
        statusColor
    } else {
        when (normStatus) {
            "know" -> EmeraldSuccess
            "confusion", "confused", "review" -> AmberWarning
            "dont_know" -> RoseError
            "unrated" -> IndigoPrimary
            else -> IndigoPrimary
        }
    }
    val effectiveFont = fontFamily ?: (if (isBengaliText(question)) KalpurushFontFamily else PoppinsFontFamily)
    val baseTextColor = if (isQuestionBank) statusColor else SlateText

    if (LatexMathParser.hasLatex(question)) {
        LatexMathText(
            text = question,
            fontFamily = effectiveFont,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = baseTextColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            highlightColor = highlightColor,
            modifier = modifier
        )
    } else {
        val annotated = remember(question, highlightColor, effectiveFont, isQuestionBank, baseTextColor) {
            buildAnnotatedString {
                val regex = Regex("\\[([^\\]]+)\\]")
                var lastIndex = 0
                val matches = regex.findAll(question).toList()

                if (matches.isEmpty() || isQuestionBank) {
                    append(question)
                } else {
                    for (match in matches) {
                        val range = match.range
                        if (range.first > lastIndex) {
                            append(question.substring(lastIndex, range.first))
                        }
                        val wordInside = match.groupValues[1]
                        val isBengali = isBengaliText(wordInside)
                        val effectiveWordFont = if (isBengali) KalpurushFontFamily else PoppinsFontFamily
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

        Text(
            text = annotated,
            fontFamily = effectiveFont,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = baseTextColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            modifier = modifier
        )
    }
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


data class QuizQuestionItem(
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
    val cleanOpt = opt.trim()
    val cleanAns = answer.trim()
    if (cleanOpt.isNotEmpty() && cleanOpt.equals(cleanAns, ignoreCase = true)) return true

    val letter = ('A' + optIdx).toString()
    if (cleanAns.equals(letter, ignoreCase = true)) return true
    return false
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

