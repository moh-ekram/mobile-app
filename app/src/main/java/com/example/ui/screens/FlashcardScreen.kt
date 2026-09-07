package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VocabularyWordEntity
import com.example.ui.components.Flashcard
import com.example.ui.theme.*
import com.example.ui.util.TtsManager

@Composable
fun FlashcardScreen(
    words: List<VocabularyWordEntity>,
    currentIndex: Int,
    selectedGroup: Int?,
    selectedStatus: String,
    availableGroups: List<Int>,
    onSelectGroup: (Int?) -> Unit,
    onSelectStatus: (String) -> Unit,
    onRate: (String, String) -> Unit, // (wordId, status)
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Group & Status Filter Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Group Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                    label = { Text("All Groups", fontSize = 12.sp) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoPrimary,
                        selectedLabelColor = Color.White
                    )
                )

                availableGroups.forEach { grp ->
                    FilterChip(
                        selected = selectedGroup == grp,
                        onClick = { onSelectGroup(grp) },
                        label = { Text("Group $grp", fontSize = 12.sp) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Status Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusList = listOf(
                    "all" to "All Status",
                    "unrated" to "Unrated",
                    "dont_know" to "Don't Know",
                    "confusion" to "Confusion",
                    "know" to "Know"
                )

                statusList.forEach { (stKey, stLabel) ->
                    FilterChip(
                        selected = selectedStatus == stKey,
                        onClick = { onSelectStatus(stKey) },
                        label = { Text(stLabel, fontSize = 11.sp) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (stKey) {
                                "know" -> EmeraldSuccess
                                "confusion" -> AmberWarning
                                "dont_know" -> RoseError
                                else -> IndigoSecondary
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Card Counter & Progress
            if (words.isNotEmpty()) {
                val safeIndex = currentIndex.coerceIn(0, words.size - 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card ${safeIndex + 1} of ${words.size}",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateMuted
                    )

                    LinearProgressIndicator(
                        progress = { (safeIndex + 1).toFloat() / words.size },
                        modifier = Modifier
                            .width(120.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = IndigoPrimary,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // Center: Flashcard or Empty State
        if (words.isNotEmpty()) {
            val safeIndex = currentIndex.coerceIn(0, words.size - 1)
            val currentWord = words[safeIndex]

            Flashcard(
                word = currentWord,
                status = currentWord.status,
                onRate = { newStatus -> onRate(currentWord.id, newStatus) },
                onNext = onNext,
                onSpeak = { text -> ttsManager.speak(text) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No Words in this Filter",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Text(
                            text = "Try clearing the group or status filter to see more flashcards.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = SlateMuted
                        )
                        Button(
                            onClick = {
                                onSelectGroup(null)
                                onSelectStatus("all")
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Show All Words", color = Color.White)
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar (Previous, Shuffle, Next)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("prev_card_button"),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White,
                    contentColor = SlateText
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Card")
            }

            // Quick Status Indicator
            if (words.isNotEmpty()) {
                val safeIndex = currentIndex.coerceIn(0, words.size - 1)
                val st = words[safeIndex].status
                val statusLabel = when (st) {
                    "know" -> "Learned"
                    "confusion" -> "Confused"
                    "dont_know" -> "Needs Review"
                    else -> "Not Rated"
                }
                val badgeColor = when (st) {
                    "know" -> EmeraldSuccess
                    "confusion" -> AmberWarning
                    "dont_know" -> RoseError
                    else -> SlateLight
                }

                Text(
                    text = statusLabel,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }

            FilledTonalIconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("next_card_button"),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White,
                    contentColor = SlateText
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Card")
            }
        }
    }
}
