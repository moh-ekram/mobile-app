package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VocabularyWordEntity
import com.example.ui.theme.*

@Composable
fun Flashcard(
    word: VocabularyWordEntity,
    status: String = "unrated",
    onRate: (String) -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFlipped by remember(word.id) { mutableStateOf(false) }
    var hasFlippedCurrentCard by remember(word.id) { mutableStateOf(false) }

    // 3D rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "card_flip_rotation"
    )

    // Pulse animation for the "Click to Flip" indicator
    val infiniteTransition = rememberInfiniteTransition(label = "click_to_flip_pulse")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_y"
    )

    fun openGoogleSearch(text: String) {
        if (text.isBlank()) return
        try {
            val url = "https://www.google.com/search?q=" + Uri.encode("$text meaning in bengali")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("flashcard_container"),
        contentAlignment = Alignment.Center
    ) {
        // 3D Perspective Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 470.dp, max = 540.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 16f * density
                }
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0x264F46E5))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    hasFlippedCurrentCard = true
                    isFlipped = !isFlipped
                }
                .padding(20.dp)
        ) {
            if (rotation <= 90f) {
                // ================= FRONT FACE =================
                FrontFaceContent(
                    word = word,
                    status = status,
                    isFlipped = isFlipped,
                    hasFlipped = hasFlippedCurrentCard,
                    bounceY = bounceY,
                    onSearch = { openGoogleSearch(word.word) },
                    onSpeak = { onSpeak(word.word) },
                    onRate = onRate,
                    onNext = onNext
                )
            } else {
                // ================= BACK FACE =================
                // Inverted 180 degrees so text renders correctly on the back
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    BackFaceContent(
                        word = word,
                        status = status,
                        onSearch = { openGoogleSearch(word.word) },
                        onSpeak = { onSpeak(word.word) },
                        onRate = onRate,
                        onNext = onNext
                    )
                }
            }
        }
    }
}

@Composable
private fun FrontFaceContent(
    word: VocabularyWordEntity,
    status: String,
    isFlipped: Boolean,
    hasFlipped: Boolean,
    bounceY: Float,
    onSearch: () -> Unit,
    onSpeak: () -> Unit,
    onRate: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        TopBarSection(
            group = word.group,
            onSearch = onSearch,
            onSpeak = onSpeak
        )

        // Center Word Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WORD",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SlateLight,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val wordColor = when (status) {
                "know" -> EmeraldSuccess
                "dont_know" -> RoseError
                "confusion" -> AmberWarning
                else -> SlateText
            }

            Text(
                text = word.word,
                fontFamily = selectFontForText(word.word),
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = wordColor,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            AnimatedVisibility(
                visible = !isFlipped && !hasFlipped,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .offset(y = bounceY.dp)
                        .clip(CircleShape)
                        .background(IndigoLight)
                        .border(1.dp, Color(0xFFC7D2FE), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Click to Flip",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary
                        )
                    }
                }
            }
        }

        // Response Rating Buttons Footer
        RatingFooter(
            status = status,
            onRate = onRate,
            onNext = onNext
        )
    }
}

@Composable
private fun BackFaceContent(
    word: VocabularyWordEntity,
    status: String,
    onSearch: () -> Unit,
    onSpeak: () -> Unit,
    onRate: (String) -> Unit,
    onNext: () -> Unit
) {
    val customPlacesList: List<Pair<String, String>> = remember(word.customPlacesJson) {
        if (!word.customPlacesJson.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(word.customPlacesJson)
                val list = mutableListOf<Pair<String, String>>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "").trim()
                    if (v.isNotBlank()) {
                        list.add(k to v)
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        TopBarSection(
            group = word.group,
            onSearch = onSearch,
            onSpeak = onSpeak
        )

        // Center Details Section (Only renders columns that were present in the uploaded course file)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (customPlacesList.isNotEmpty()) {
                // Dynamically show ONLY columns present in the uploaded course excel file
                customPlacesList.forEach { (label, value) ->
                    val isBengali = isBengaliText(value)
                    val font = selectFontForText(value)
                    val isMeaning = label.contains("meaning", ignoreCase = true) || label.contains("অর্থ", ignoreCase = true) || label.contains("place2", ignoreCase = true)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = label.uppercase(),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (label.contains("synonym", ignoreCase = true) || label.contains("extra", ignoreCase = true) || label.contains("form", ignoreCase = true)) {
                                formatLineWithRedWord(value, word.word)
                            } else {
                                AnnotatedString(value)
                            },
                            fontFamily = font,
                            fontSize = if (isMeaning) 21.sp else if (isBengali) 16.sp else 14.sp,
                            fontWeight = if (isMeaning) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMeaning) EmeraldSuccess else SlateText,
                            textAlign = TextAlign.Center,
                            lineHeight = if (isMeaning) 26.sp else 20.sp
                        )
                    }
                }
            } else {
                // Default fallback when customPlacesJson is not present
                if (word.meaning.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "MEANING",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = word.meaning,
                            fontFamily = selectFontForText(word.meaning),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black,
                            color = EmeraldSuccess,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )
                    }
                }

                if (!word.example.isNullOrBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "EXAMPLE SENTENCE",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = word.example,
                            fontFamily = selectFontForText(word.example),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateText,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }

                if (!word.extraWord.isNullOrBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "DERIVATIVE / FORMS",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatLineWithRedWord(word.extraWord, word.word),
                            fontFamily = selectFontForText(word.extraWord),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }

                if (!word.synonyms.isNullOrBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "SYNONYMS",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatLineWithRedWord(word.synonyms, word.word),
                            fontFamily = selectFontForText(word.synonyms),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }

                if (!word.mnemonic.isNullOrBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "MNEMONIC / TRICK",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = word.mnemonic,
                            fontFamily = selectFontForText(word.mnemonic),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            color = IndigoPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Response Rating Buttons Footer
        RatingFooter(
            status = status,
            onRate = onRate,
            onNext = onNext
        )
    }
}

@Composable
private fun TopBarSection(
    group: Int?,
    onSearch: () -> Unit,
    onSpeak: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group Badge
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(IndigoLight)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (group != null && group > 0) "GROUP $group" else "VOCABULARY",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = IndigoPrimary,
                letterSpacing = 1.2.sp
            )
        }

        // Actions (Search & Speak)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Google Search Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, SlateBorder, CircleShape)
                    .clickable { onSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search on Google",
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Speak Word Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(IndigoLight)
                    .clickable { onSpeak() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speak word",
                    tint = IndigoPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingFooter(
    status: String,
    onRate: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFF1F5F9))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Don't Know Button
            RatingItem(
                title = "don't know",
                isActive = status == "dont_know",
                activeBg = RoseError,
                activeBorder = Color(0xFFBE123C),
                inactiveBg = RoseLight,
                inactiveBorder = RoseBorder,
                tint = if (status == "dont_know") Color.White else RoseError,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Don't know",
                        tint = if (status == "dont_know") Color.White else RoseError,
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = { onRate("dont_know") }
            )

            // Confusion Button
            RatingItem(
                title = "confusion",
                isActive = status == "confusion",
                activeBg = AmberWarning,
                activeBorder = Color(0xFFB45309),
                inactiveBg = AmberLight,
                inactiveBorder = AmberBorder,
                tint = if (status == "confusion") Color.White else AmberWarning,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Confusion",
                        tint = if (status == "confusion") Color.White else AmberWarning,
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = { onRate("confusion") }
            )

            // Skip Button
            RatingItem(
                title = "skip",
                isActive = false,
                activeBg = Color(0xFFE2E8F0),
                activeBorder = Color(0xFFCBD5E1),
                inactiveBg = Color(0xFFF1F5F9),
                inactiveBorder = Color(0xFFE2E8F0),
                tint = Color(0xFF475569),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = onNext
            )

            // Know Button
            RatingItem(
                title = "know",
                isActive = status == "know",
                activeBg = EmeraldSuccess,
                activeBorder = Color(0xFF047857),
                inactiveBg = EmeraldLight,
                inactiveBorder = EmeraldBorder,
                tint = if (status == "know") Color.White else EmeraldSuccess,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Know",
                        tint = if (status == "know") Color.White else EmeraldSuccess,
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = { onRate("know") }
            )
        }
    }
}

@Composable
private fun RatingItem(
    title: String,
    isActive: Boolean,
    activeBg: Color,
    activeBorder: Color,
    inactiveBg: Color,
    inactiveBorder: Color,
    tint: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isActive) activeBg else inactiveBg)
                .border(1.dp, if (isActive) activeBorder else inactiveBorder, RoundedCornerShape(16.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Text(
            text = title,
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) SlateText else SlateLight
        )
    }
}

/**
 * Highlights any occurrence of targetWord in valText in red font
 */
fun formatLineWithRedWord(valText: String, targetWord: String): AnnotatedString {
    if (valText.isBlank()) return AnnotatedString("")
    if (targetWord.isBlank()) return AnnotatedString(valText)

    return buildAnnotatedString {
        val lowerVal = valText.lowercase()
        val lowerTarget = targetWord.lowercase()

        var cursor = 0
        while (cursor < valText.length) {
            val matchIndex = lowerVal.indexOf(lowerTarget, cursor)
            if (matchIndex == -1) {
                append(valText.substring(cursor))
                break
            }

            if (matchIndex > cursor) {
                append(valText.substring(cursor, matchIndex))
            }

            val endMatch = matchIndex + targetWord.length
            pushStyle(
                SpanStyle(
                    color = Color(0xFFDC2626), // red-600
                    fontWeight = FontWeight.Bold
                )
            )
            append(valText.substring(matchIndex, endMatch))
            pop()

            cursor = endMatch
        }
    }
}
