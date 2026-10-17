package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.util.FlashcardDesignConfig
import com.example.ui.util.FlashcardDesignManager
import com.example.ui.util.HapticHelper
import kotlin.math.roundToInt

/**
 * Dedicated, minimal, and highly professional settings tab for Flashcard Design.
 * Allows users to customize card styles, background colors, font sizes for every place,
 * left-right swipe transitions, and flip animations.
 */
@Composable
fun FlashcardDesignTab(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val hapticHelper = remember { HapticHelper(context) }

    var config by remember { mutableStateOf(FlashcardDesignManager.loadConfig(context)) }
    var isPreviewFlipped by remember { mutableStateOf(false) }

    fun updateConfig(newConfig: FlashcardDesignConfig) {
        config = newConfig
        FlashcardDesignManager.saveConfig(context, newConfig)
    }

    val previewRotation by animateFloatAsState(
        targetValue = if (isPreviewFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "flashcard_preview_flip"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("flashcard_design_tab"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----------------------------------------------------
        // Header Row: Title & Minimal "Reset to Default" Action
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Flashcard Design",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Personalize colors, typography & motion",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.textMuted
                    )
                }

                // Clean Minimal Reset Button
                OutlinedButton(
                    onClick = {
                        hapticHelper.subtleTick()
                        val def = FlashcardDesignManager.resetToDefault(context)
                        config = def
                        isPreviewFlipped = false
                        Toast.makeText(context, "Reset to default flashcard style", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("reset_flashcard_design_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset to Default",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset Default",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoPrimary
                    )
                }
            }
        }

        // ----------------------------------------------------
        // 1. Live Interactive Card Preview (Centered, Sleek)
        // ----------------------------------------------------
        item {
            val resolvedBg = config.resolveCardBackgroundColor(palette.cardBackground)
            val previewFontFamily = when (config.fontFamilyType) {
                "serif" -> FontFamily.Serif
                "monospace" -> FontFamily.Monospace
                "sans" -> FontFamily.SansSerif
                else -> PoppinsFontFamily
            }
            val previewTextAlign = if (config.textAlignment == "start") TextAlign.Start else TextAlign.Center

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
                        .shadow(
                            elevation = config.cardElevationDp.dp,
                            shape = RoundedCornerShape(config.cornerRadiusDp.dp)
                        )
                        .background(
                            if (config.cardStyle == "gradient") {
                                Brush.verticalGradient(listOf(resolvedBg, resolvedBg.copy(alpha = 0.85f)))
                            } else {
                                Brush.linearGradient(listOf(resolvedBg, resolvedBg))
                            }
                        )
                        .border(
                            config.borderWidthDp.dp,
                            if (config.cardStyle == "minimal") IndigoPrimary.copy(alpha = 0.45f) else palette.cardBorder,
                            RoundedCornerShape(config.cornerRadiusDp.dp)
                        )
                        .clickable {
                            hapticHelper.cardFlip()
                            isPreviewFlipped = !isPreviewFlipped
                        }
                        .padding(16.dp)
                        .then(
                            when (config.flipAnimationStyle) {
                                "flip_3d_v" -> Modifier.graphicsLayer {
                                    rotationX = previewRotation
                                    cameraDistance = 14f * density
                                }
                                else -> Modifier.graphicsLayer {
                                    rotationY = previewRotation
                                    cameraDistance = 14f * density
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val showFront = previewRotation <= 90f
                    if (showFront) {
                        Column(
                            horizontalAlignment = if (config.textAlignment == "start") Alignment.Start else Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "PLACE 1 (WORD)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary.copy(alpha = 0.8f),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ephemeral",
                                fontFamily = previewFontFamily,
                                fontSize = (config.place1MaxFontSize * 0.74f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (palette.isDark && resolvedBg == Color(0xFF1E293B)) Color.White else IndigoPrimary,
                                textAlign = previewTextAlign
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap card to flip ↻",
                                fontSize = 9.5.sp,
                                color = palette.textMuted
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = if (config.textAlignment == "start") Alignment.Start else Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    when (config.flipAnimationStyle) {
                                        "flip_3d_v" -> Modifier.graphicsLayer { rotationX = 180f }
                                        else -> Modifier.graphicsLayer { rotationY = 180f }
                                    }
                                )
                        ) {
                            Text(
                                text = "PLACE 2 (MEANING)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lasting for a very short time",
                                fontFamily = previewFontFamily,
                                fontSize = (config.place2MaxFontSize * 0.74f).sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                textAlign = previewTextAlign
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Place 3: Fleeting, Transient",
                                fontFamily = previewFontFamily,
                                fontSize = (config.place3MaxFontSize * 0.82f).sp,
                                color = palette.textMuted,
                                textAlign = previewTextAlign
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 2. Card Style & Background Color
        // ----------------------------------------------------
        item {
            DesignSectionCard(
                title = "Style & Background Color",
                icon = Icons.Default.Palette,
                palette = palette
            ) {
                // Card Style Chips
                Text(
                    text = "Card Preset Style",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlashcardDesignManager.CARD_STYLES.forEach { (key, label) ->
                        val isSelected = config.cardStyle == key
                        Surface(
                            onClick = {
                                hapticHelper.subtleTick()
                                updateConfig(config.copy(cardStyle = key))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndigoLight else palette.cardBorder.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else palette.cardBorder),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else palette.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Color Swatches
                Text(
                    text = "Background Color",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlashcardDesignManager.COLOR_PRESETS.forEach { swatch ->
                        val isSelected = config.backgroundColorHex.equals(swatch.hex, ignoreCase = true)
                        val swatchColor = if (swatch.hex == "default") palette.cardBackground else {
                            try { Color(android.graphics.Color.parseColor(swatch.hex)) } catch (_: Exception) { palette.cardBackground }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    hapticHelper.subtleTick()
                                    updateConfig(config.copy(backgroundColorHex = swatch.hex))
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        if (isSelected) 2.5.dp else 1.dp,
                                        if (isSelected) IndigoPrimary else palette.cardBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (swatch.isDark) Color.White else IndigoPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = swatch.name,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) IndigoPrimary else palette.textMuted
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 3. Maximum Font Sizes (Word, Meaning, Extra)
        // ----------------------------------------------------
        item {
            DesignSectionCard(
                title = "Place Maximum Font Sizes",
                icon = Icons.Default.FormatSize,
                palette = palette
            ) {
                // Place 1 (Front Word)
                FontSizeSliderItem(
                    label = "Place 1 (Front Word)",
                    fontSizeSp = config.place1MaxFontSize,
                    range = 18f..42f,
                    palette = palette,
                    onValueChange = { updateConfig(config.copy(place1MaxFontSize = it)) }
                )

                // Place 2 (Meaning / Definition)
                FontSizeSliderItem(
                    label = "Place 2 (Primary Meaning)",
                    fontSizeSp = config.place2MaxFontSize,
                    range = 14f..32f,
                    palette = palette,
                    onValueChange = { updateConfig(config.copy(place2MaxFontSize = it)) }
                )

                // Place 3 (Notes / Extra)
                FontSizeSliderItem(
                    label = "Place 3 (Extra / Synonyms)",
                    fontSizeSp = config.place3MaxFontSize,
                    range = 12f..24f,
                    palette = palette,
                    onValueChange = { updateConfig(config.copy(place3MaxFontSize = it)) }
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Typography & Alignment in Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text Alignment",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("center" to "Center", "start" to "Left Align").forEach { (alignKey, label) ->
                            val isSelected = config.textAlignment == alignKey
                            Surface(
                                onClick = {
                                    hapticHelper.subtleTick()
                                    updateConfig(config.copy(textAlignment = alignKey))
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IndigoPrimary else palette.cardBorder.copy(alpha = 0.25f),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 4. Motion & Animations (Swipe & Flip)
        // ----------------------------------------------------
        item {
            DesignSectionCard(
                title = "Motion & Animations",
                icon = Icons.Default.Animation,
                palette = palette
            ) {
                // Left-Right Swipe Style
                Text(
                    text = "Left-Right Swipe Animation",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlashcardDesignManager.SWIPE_ANIMATIONS.forEach { (key, label) ->
                        val isSelected = config.swipeAnimationStyle == key
                        Surface(
                            onClick = {
                                hapticHelper.subtleTick()
                                updateConfig(config.copy(swipeAnimationStyle = key))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndigoLight else palette.cardBorder.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else palette.cardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else palette.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Card Flip Animation Style
                Text(
                    text = "Card Flip Animation",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlashcardDesignManager.FLIP_ANIMATIONS.forEach { (key, label) ->
                        val isSelected = config.flipAnimationStyle == key
                        Surface(
                            onClick = {
                                hapticHelper.subtleTick()
                                updateConfig(config.copy(flipAnimationStyle = key))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndigoLight else palette.cardBorder.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else palette.cardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else palette.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 5. Card Geometry & Elevation (Corner radius & Shadows)
        // ----------------------------------------------------
        item {
            DesignSectionCard(
                title = "Card Geometry & Elevation",
                icon = Icons.Default.RoundedCorner,
                palette = palette
            ) {
                // Corner Radius Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Corner Radius", fontSize = 12.sp, color = palette.textPrimary)
                        Text("${config.cornerRadiusDp} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                    }
                    Slider(
                        value = config.cornerRadiusDp.toFloat(),
                        onValueChange = { updateConfig(config.copy(cornerRadiusDp = it.roundToInt())) },
                        valueRange = 8f..36f,
                        steps = 13,
                        colors = SliderDefaults.colors(thumbColor = IndigoPrimary, activeTrackColor = IndigoPrimary)
                    )
                }

                // Elevation Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shadow / Elevation", fontSize = 12.sp, color = palette.textPrimary)
                        Text("${config.cardElevationDp} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                    }
                    Slider(
                        value = config.cardElevationDp.toFloat(),
                        onValueChange = { updateConfig(config.copy(cardElevationDp = it.roundToInt())) },
                        valueRange = 0f..10f,
                        steps = 10,
                        colors = SliderDefaults.colors(thumbColor = IndigoPrimary, activeTrackColor = IndigoPrimary)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Retained for backwards compatibility if referenced elsewhere.
 */
@Composable
fun FlashcardDesignSection(
    modifier: Modifier = Modifier
) {
    FlashcardDesignTab(modifier = modifier)
}

@Composable
private fun DesignSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: AppPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(IndigoLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            content()
        }
    }
}

@Composable
private fun FontSizeSliderItem(
    label: String,
    fontSizeSp: Int,
    range: ClosedFloatingPointRange<Float>,
    palette: AppPalette,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = palette.textPrimary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(IndigoLight)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$fontSizeSp sp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
            }
        }
        Slider(
            value = fontSizeSp.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(thumbColor = IndigoPrimary, activeTrackColor = IndigoPrimary)
        )
    }
}
