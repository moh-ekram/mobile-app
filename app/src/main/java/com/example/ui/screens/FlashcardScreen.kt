package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    selectedGroups: Set<Int> = emptySet(),
    selectedStatuses: Set<String> = emptySet(),
    sortOrder: String = "default",
    availableGroups: List<Int> = emptyList(),
    isFocusMode: Boolean = false,
    isFlipAnimationEnabled: Boolean = true,
    onToggleFocusMode: (Boolean) -> Unit = {},
    onToggleGroup: (Int) -> Unit = {},
    onClearGroups: () -> Unit = {},
    onToggleStatus: (String) -> Unit = {},
    onClearStatuses: () -> Unit = {},
    onSetSortOrder: (String) -> Unit = {},
    onReshuffle: () -> Unit = {},
    onResetAllFilters: () -> Unit = {},
    onRate: (String, String) -> Unit, // (wordId, status)
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val ttsManager = remember { TtsManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    var showFilterDialog by remember { mutableStateOf(false) }

    val hasActiveFilters = selectedGroups.isNotEmpty() || selectedStatuses.isNotEmpty() || sortOrder != "default"

    if (showFilterDialog) {
        FlashcardFilterDialog(
            selectedGroups = selectedGroups,
            selectedStatuses = selectedStatuses,
            sortOrder = sortOrder,
            availableGroups = availableGroups,
            onToggleGroup = onToggleGroup,
            onClearGroups = onClearGroups,
            onToggleStatus = onToggleStatus,
            onClearStatuses = onClearStatuses,
            onSetSortOrder = onSetSortOrder,
            onReshuffle = onReshuffle,
            onResetAllFilters = onResetAllFilters,
            onDismiss = { showFilterDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = if (isFocusMode) 8.dp else 16.dp, vertical = if (isFocusMode) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Filter Bar or Minimal Focus Mode Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isFocusMode) {
                // Focus Mode Active: All filter options are completely turned off/hidden!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onToggleFocusMode(false) },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = palette.surface,
                            contentColor = palette.textPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Focus Mode",
                            modifier = Modifier.size(16.dp),
                            tint = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Exit Focus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary
                        )
                    }
                }
            } else {
                // Normal Mode: Filter Row with Filter Categories Button + Active Chips + Focus + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Categories Button (Triggers Row & Grid Box Filter Dialog)
                    val activeCount = selectedGroups.size + selectedStatuses.size + (if (sortOrder != "default") 1 else 0)
                    Surface(
                        onClick = { showFilterDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasActiveFilters) (if (palette.isDark) Color(0xFF312E81) else IndigoLight) else palette.surface,
                        border = BorderStroke(
                            1.dp,
                            if (hasActiveFilters) (if (palette.isDark) Color(0xFF4338CA) else IndigoPrimary) else palette.cardBorder
                        ),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Open Filter Categories",
                                tint = if (hasActiveFilters) (if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary) else palette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (hasActiveFilters) "Filters ($activeCount)" else "Filter Categories",
                                fontSize = 12.sp,
                                fontWeight = if (hasActiveFilters) FontWeight.Bold else FontWeight.Medium,
                                color = if (hasActiveFilters) (if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary) else palette.textPrimary
                            )
                        }
                    }

                    // Active Selected Category Chips (Row of selected boxes - Always remembered and visible)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedStatuses.forEach { st ->
                            val (label, color) = when (st) {
                                "know" -> "Know" to EmeraldSuccess
                                "confusion" -> "Confusion" to AmberWarning
                                "dont_know" -> "Don't Know" to RoseError
                                else -> "Unrated" to SlateText
                            }
                            Surface(
                                onClick = { onToggleStatus(st) },
                                shape = CircleShape,
                                color = palette.surface,
                                border = BorderStroke(1.dp, color),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = color, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        selectedGroups.sorted().forEach { grp ->
                            Surface(
                                onClick = { onToggleGroup(grp) },
                                shape = CircleShape,
                                color = if (palette.isDark) Color(0xFF312E81) else IndigoLight,
                                border = BorderStroke(1.dp, IndigoPrimary),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("G$grp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = IndigoPrimary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        if (sortOrder != "default") {
                            val sortName = when (sortOrder) {
                                "a_z" -> "A-Z"
                                "z_a" -> "Z-A"
                                "random" -> "Random"
                                else -> sortOrder
                            }
                            Surface(
                                onClick = { onSetSortOrder("default") },
                                shape = CircleShape,
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(12.dp))
                                    Text(sortName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = palette.textMuted, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // Full Screen / Focus Mode Toggle Button
                    IconButton(
                        onClick = { onToggleFocusMode(true) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.surface)
                            .border(1.dp, palette.cardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen Focus Mode",
                            tint = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset button if filters applied
                    if (hasActiveFilters) {
                        IconButton(
                            onClick = onResetAllFilters,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset Filters",
                                tint = RoseError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Card Counter & Progress
            if (words.isNotEmpty()) {
                val safeIndex = currentIndex.coerceIn(0, words.size - 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card ${safeIndex + 1} of ${words.size}",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateMuted
                    )

                    LinearProgressIndicator(
                        progress = { (safeIndex + 1).toFloat() / words.size },
                        modifier = Modifier
                            .width(110.dp)
                            .height(5.dp)
                            .clip(CircleShape),
                        color = IndigoPrimary,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // Center: Flashcard with Smooth Animated Transitions
        if (words.isNotEmpty()) {
            val safeIndex = currentIndex.coerceIn(0, words.size - 1)
            val currentWord = words[safeIndex]

            AnimatedContent(
                targetState = currentWord,
                transitionSpec = {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.4f).toInt() },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(280))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.4f).toInt() },
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(220))
                    )
                },
                label = "flashcard_slide_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { targetWord ->
                Flashcard(
                    word = targetWord,
                    status = targetWord.status,
                    isFlipAnimationEnabled = isFlipAnimationEnabled,
                    isFocusMode = isFocusMode,
                    onRate = { newStatus -> onRate(targetWord.id, newStatus) },
                    onNext = onNext,
                    onSpeak = { text -> ttsManager.speak(text) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No Words in this Filter",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Clear filters to view cards",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = palette.textMuted
                        )
                        Button(
                            onClick = onResetAllFilters,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Reset All Filters", fontFamily = PoppinsFontFamily, color = Color.White)
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar (Hidden in Focus Mode for maximum card height and bottom tag buttons)
        if (!isFocusMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("prev_card_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = palette.surface,
                        contentColor = palette.textPrimary
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
                        else -> palette.textMuted
                    }

                    Text(
                        text = statusLabel,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                FilledTonalIconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("next_card_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = palette.surface,
                        contentColor = palette.textPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Card")
                }
            }
        }
    }
}

@Composable
private fun FlashcardFilterDialog(
    selectedGroups: Set<Int>,
    selectedStatuses: Set<String>,
    sortOrder: String,
    availableGroups: List<Int>,
    onToggleGroup: (Int) -> Unit,
    onClearGroups: () -> Unit,
    onToggleStatus: (String) -> Unit,
    onClearStatuses: () -> Unit,
    onSetSortOrder: (String) -> Unit,
    onReshuffle: () -> Unit,
    onResetAllFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Filter Categories",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = palette.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Status Category - Row & Grid Boxes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status Category",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        if (selectedStatuses.isNotEmpty()) {
                            Text(
                                text = "Clear Statuses",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = IndigoPrimary,
                                modifier = Modifier.clickable { onClearStatuses() }
                            )
                        }
                    }

                    // All Statuses Box
                    val isAllStatus = selectedStatuses.isEmpty()
                    Surface(
                        onClick = { onClearStatuses() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAllStatus) (if (palette.isDark) Color(0xFF312E81) else IndigoLight) else palette.surface,
                        border = BorderStroke(if (isAllStatus) 1.5.dp else 1.dp, if (isAllStatus) IndigoPrimary else palette.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "All Statuses (Show All)",
                                fontSize = 12.sp,
                                fontWeight = if (isAllStatus) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllStatus) IndigoPrimary else palette.textPrimary
                            )
                        }
                    }

                    // Status 2x2 Grid of Boxes
                    val statusList = listOf(
                        Triple("know", "Know", EmeraldSuccess to EmeraldLight),
                        Triple("confusion", "Confusion", AmberWarning to AmberLight),
                        Triple("dont_know", "Don't Know", RoseError to RoseLight),
                        Triple("unrated", "Unrated", SlateText to Color(0xFFF1F5F9))
                    )

                    statusList.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPair.forEach { (stKey, label, colorPair) ->
                                val (brandColor, bgTint) = colorPair
                                val isSelected = selectedStatuses.contains(stKey)
                                Surface(
                                    onClick = { onToggleStatus(stKey) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) bgTint else palette.surface,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) brandColor else palette.cardBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
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
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) brandColor else palette.textPrimary
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
                        }
                    }
                }

                HorizontalDivider(color = palette.cardBorder)

                // 2. Groups Category - Responsive Grid Boxes (4 per row)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Course Groups (Grid)",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        if (selectedGroups.isNotEmpty()) {
                            Text(
                                text = "Clear Groups",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = IndigoPrimary,
                                modifier = Modifier.clickable { onClearGroups() }
                            )
                        }
                    }

                    // All Groups Box
                    val isAllGroups = selectedGroups.isEmpty()
                    Surface(
                        onClick = { onClearGroups() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAllGroups) (if (palette.isDark) Color(0xFF312E81) else IndigoLight) else palette.surface,
                        border = BorderStroke(if (isAllGroups) 1.5.dp else 1.dp, if (isAllGroups) IndigoPrimary else palette.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "All Groups (Show All)",
                                fontSize = 12.sp,
                                fontWeight = if (isAllGroups) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllGroups) IndigoPrimary else palette.textPrimary
                            )
                        }
                    }

                    // Group Grid Boxes (4 per row)
                    if (availableGroups.isNotEmpty()) {
                        availableGroups.chunked(4).forEach { rowGrps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowGrps.forEach { grp ->
                                    val isChecked = selectedGroups.contains(grp)
                                    Surface(
                                        onClick = { onToggleGroup(grp) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isChecked) IndigoPrimary else palette.surface,
                                        border = BorderStroke(
                                            if (isChecked) 1.5.dp else 1.dp,
                                            if (isChecked) IndigoPrimary else palette.cardBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "G$grp",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isChecked) Color.White else palette.textPrimary
                                            )
                                            if (isChecked) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
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

                HorizontalDivider(color = palette.cardBorder)

                // 3. Card Sort Order - 2x2 Grid Boxes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sequence & Sorting",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )

                    val sortOptions = listOf(
                        Triple("default", "Default Order", Icons.Default.FormatListNumbered),
                        Triple("a_z", "A - Z", Icons.Default.ArrowUpward),
                        Triple("z_a", "Z - A", Icons.Default.ArrowDownward),
                        Triple("random", "Random Shuffle", Icons.Default.Shuffle)
                    )

                    sortOptions.chunked(2).forEach { rowSorts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSorts.forEach { (orderKey, label, icon) ->
                                val isSelected = sortOrder == orderKey
                                Surface(
                                    onClick = { onSetSortOrder(orderKey) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) (if (palette.isDark) Color(0xFF312E81) else IndigoLight) else palette.surface,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) IndigoPrimary else palette.cardBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) IndigoPrimary else palette.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) IndigoPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (sortOrder == "random") {
                        OutlinedButton(
                            onClick = { onReshuffle() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reshuffle Cards Now", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Apply & Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onResetAllFilters() }) {
                Text("Reset All", color = RoseError, fontWeight = FontWeight.Medium)
            }
        },
        containerColor = palette.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
