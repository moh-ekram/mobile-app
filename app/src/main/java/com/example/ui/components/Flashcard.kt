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
    isFlipAnimationEnabled: Boolean = true,
    isFocusMode: Boolean = false,
    onRate: (String) -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    var isFlipped by remember(word.id) { mutableStateOf(false) }
    var hasFlippedCurrentCard by remember(word.id) { mutableStateOf(false) }

    // 3D rotation animation (if flip animation is enabled)
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = if (isFlipAnimationEnabled) tween(durationMillis = 460, easing = FastOutSlowInEasing) else tween(durationMillis = 0),
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
            .padding(horizontal = if (isFocusMode) 4.dp else 8.dp)
            .testTag("flashcard_container"),
        contentAlignment = Alignment.Center
    ) {
        // 3D Perspective Card Container
        val cardHeightMod = if (isFocusMode) {
            Modifier.fillMaxWidth().heightIn(min = 520.dp, max = 640.dp)
        } else {
            Modifier.fillMaxWidth().heightIn(min = 470.dp, max = 540.dp)
        }

        Box(
            modifier = cardHeightMod
                .then(
                    if (isFlipAnimationEnabled) {
                        Modifier.graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 16f * density
                        }
                    } else Modifier
                )
                .shadow(elevation = if (palette.isDark) 4.dp else 12.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0x264F46E5))
                .clip(RoundedCornerShape(28.dp))
                .background(palette.cardBackground)
                .border(1.dp, palette.cardBorder, RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    hasFlippedCurrentCard = true
                    isFlipped = !isFlipped
                }
                .padding(if (isFocusMode) 22.dp else 20.dp)
        ) {
            val showFront = if (isFlipAnimationEnabled) rotation <= 90f else !isFlipped
            if (showFront) {
                // ================= FRONT FACE =================
                FrontFaceContent(
                    word = word,
                    status = status,
                    isFlipped = isFlipped,
                    hasFlipped = hasFlippedCurrentCard,
                    bounceY = bounceY,
                    isFocusMode = isFocusMode,
                    onSearch = { openGoogleSearch(word.word) },
                    onSpeak = { onSpeak(word.word) },
                    onRate = onRate,
                    onNext = onNext
                )
            } else {
                // ================= BACK FACE =================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isFlipAnimationEnabled) Modifier.graphicsLayer { rotationY = 180f } else Modifier
                        )
                ) {
                    BackFaceContent(
                        word = word,
                        status = status,
                        isFocusMode = isFocusMode,
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
    isFocusMode: Boolean = false,
    onSearch: () -> Unit,
    onSpeak: () -> Unit,
    onRate: (String) -> Unit,
    onNext: () -> Unit
) {
    val palette = LocalAppPalette.current
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
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val wordColor = when (status) {
                "know" -> EmeraldSuccess
                "dont_know" -> RoseError
                "confusion" -> AmberWarning
                else -> palette.textPrimary
            }

            val wordLen = word.word.length
            val dynamicFontSize = when {
                wordLen <= 6 -> if (isFocusMode) 38.sp else 34.sp
                wordLen <= 10 -> if (isFocusMode) 32.sp else 28.sp
                wordLen <= 14 -> if (isFocusMode) 26.sp else 23.sp
                wordLen <= 18 -> if (isFocusMode) 22.sp else 20.sp
                wordLen <= 24 -> if (isFocusMode) 19.sp else 17.sp
                else -> if (isFocusMode) 16.sp else 15.sp
            }
            val dynamicLineHeight = (dynamicFontSize.value * 1.22f).sp

            Text(
                text = word.word,
                fontFamily = selectFontForText(word.word),
                fontSize = dynamicFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = wordColor,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                lineHeight = dynamicLineHeight,
                maxLines = 4,
                softWrap = true,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
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
                        .background(if (palette.isDark) Color(0xFF312E81) else IndigoLight)
                        .border(1.dp, if (palette.isDark) Color(0xFF4338CA) else Color(0xFFC7D2FE), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Click to Flip",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary
                        )
                    }
                }
            }
        }

        // Response Rating Buttons Footer
        RatingFooter(
            status = status,
            isFocusMode = isFocusMode,
            onRate = onRate,
            onNext = onNext
        )
    }
}

@Composable
private fun BackFaceContent(
    word: VocabularyWordEntity,
    status: String,
    isFocusMode: Boolean = false,
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
                customPlacesList.forEachIndexed { index, (label, value) ->
                    val isBengali = isBengaliText(value)
                    val font = selectFontForText(value)
                    val labelLower = label.lowercase().trim()
                    // Place 2 detection (Meaning / Bengali Definition / Place 2)
                    val isPlace2 = labelLower.startsWith("place2") || labelLower.contains("place 2") ||
                            labelLower.contains("meaning") || labelLower.contains("definition") ||
                            labelLower.contains("translation") || index == 0
                    // Place 4 or Place 5 detection (Forms, Synonyms, Derivatives, Sentences, etc.)
                    val isPlace4or5 = labelLower.startsWith("place4") || labelLower.contains("place 4") ||
                            labelLower.startsWith("place5") || labelLower.contains("place 5") ||
                            labelLower.contains("synonym") || labelLower.contains("extra") ||
                            labelLower.contains("example") || labelLower.contains("form") ||
                            labelLower.contains("sentence")

                    val displayLabel = run {
                        var cleaned = label.trim()
                        if (cleaned.contains(":")) cleaned = cleaned.substringAfter(":").trim()
                        cleaned = cleaned.replace(Regex("""(?i)^place\s*\d+\s*[-_:]?\s*"""), "").trim()
                        cleaned = cleaned.replace(Regex("""(?i)\s*\(place\s*\d+\)"""), "").trim()
                        if (cleaned.isBlank()) {
                            when {
                                labelLower.contains("1") -> "Word"
                                labelLower.contains("2") -> "Meaning"
                                labelLower.contains("3") -> "Example"
                                labelLower.contains("4") -> "Synonyms"
                                labelLower.contains("5") -> "Forms"
                                else -> label.trim()
                            }
                        } else cleaned
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = displayLabel.uppercase(),
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPlace2) {
                                AnnotatedString(value)
                            } else {
                                // If place4/place5 contains place1's word, highlight it in red (RoseError)
                                formatLineWithRedWord(value, word.word)
                            },
                            fontFamily = font,
                            fontSize = if (isPlace2) 21.sp else if (isBengali) 16.sp else 14.sp,
                            fontWeight = if (isPlace2) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPlace2) EmeraldSuccess else SlateText,
                            textAlign = TextAlign.Center,
                            lineHeight = if (isPlace2) 26.sp else 20.sp
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
                            fontFamily = PoppinsFontFamily,
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
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatLineWithRedWord(word.example, word.word),
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
                            fontFamily = PoppinsFontFamily,
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
                            fontFamily = PoppinsFontFamily,
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
                            fontFamily = PoppinsFontFamily,
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
            isFocusMode = isFocusMode,
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
                fontFamily = PoppinsFontFamily,
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
    isFocusMode: Boolean = false,
    onRate: (String) -> Unit,
    onNext: () -> Unit
) {
    val palette = LocalAppPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFocusMode) 16.dp else 10.dp, bottom = if (isFocusMode) 8.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.cardBorder)
        )

        Spacer(modifier = Modifier.height(if (isFocusMode) 16.dp else 12.dp))

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
                inactiveBg = if (palette.isDark) Color(0xFF381219) else RoseLight,
                inactiveBorder = if (palette.isDark) Color(0xFF4C1D24) else RoseBorder,
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
                inactiveBg = if (palette.isDark) Color(0xFF362005) else AmberLight,
                inactiveBorder = if (palette.isDark) Color(0xFF4D2E07) else AmberBorder,
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
                activeBg = if (palette.isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                activeBorder = if (palette.isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                inactiveBg = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                inactiveBorder = if (palette.isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                tint = if (palette.isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = if (palette.isDark) Color(0xFF94A3B8) else Color(0xFF475569),
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
                inactiveBg = if (palette.isDark) Color(0xFF064E3B) else EmeraldLight,
                inactiveBorder = if (palette.isDark) Color(0xFF065F46) else EmeraldBorder,
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
    val palette = LocalAppPalette.current
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
            fontFamily = PoppinsFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) (if (palette.isDark) Color.White else SlateText) else palette.textMuted
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
                    color = RoseError, // exact don't know tag color
                    fontWeight = FontWeight.Bold
                )
            )
            append(valText.substring(matchIndex, endMatch))
            pop()

            cursor = endMatch
        }
    }
}
