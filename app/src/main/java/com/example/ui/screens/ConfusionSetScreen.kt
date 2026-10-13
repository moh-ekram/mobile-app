package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.engine.ConfusionSetCacheManager
import com.example.data.engine.ConfusionSimilarityEngine
import com.example.data.engine.ConfusionSimilarityEngine.ComparisonField
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionConfig
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionGroup
import com.example.data.engine.ConfusionSimilarityEngine.MatchingMode
import com.example.data.engine.ConfusionSimilarityEngine.SimilarityAlgorithm
import com.example.data.engine.ConfusionSimilarityEngine.SortOption
import com.example.data.model.CourseEntity
import com.example.data.model.VocabularyWordEntity
import com.example.ui.theme.*
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfusionSetScreen(
    courses: List<CourseEntity>,
    words: List<VocabularyWordEntity>,
    initialCourseId: String = "all",
    getPlaceValue: (VocabularyWordEntity, Int) -> String,
    getPlaceLabel: (Int, String) -> String = { num, _ -> when (num) { 1 -> "Word"; 2 -> "Meaning"; else -> "Synonym" } },
    onRateWord: (wordId: String, status: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tts = remember(context) { TtsManager(context) }
    val hapticHelper = remember(context) { HapticHelper(context) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    // Restore last selected course and config from persistent local storage
    val initialSavedCourseId = remember {
        ConfusionSetCacheManager.getLastCourseId(context)
    }
    val initialSavedConfig = remember {
        ConfusionSetCacheManager.loadConfig(context)
    }

    // Selected Course state: only compare words belonging to this course
    val eligibleCourses = remember(courses, words) {
        val courseIdsWithWords = words.map { it.courseId }.toSet()
        courses.filter { it.id in courseIdsWithWords }.ifEmpty { courses }
    }

    var selectedCourseId by remember(eligibleCourses, initialCourseId) {
        val candidate = when {
            initialCourseId.isNotBlank() && initialCourseId != "all" && eligibleCourses.any { it.id == initialCourseId } -> initialCourseId
            initialSavedCourseId != null && eligibleCourses.any { it.id == initialSavedCourseId } -> initialSavedCourseId
            else -> eligibleCourses.firstOrNull()?.id ?: "course_default"
        }
        mutableStateOf(candidate)
    }

    var showCourseMenu by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showGlobalQuiz by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Configuration state initialized from persistent storage
    var config by remember { mutableStateOf(initialSavedConfig) }

    // Filter words strictly belonging to selected course
    val courseWords = remember(words, selectedCourseId) {
        words.filter { it.courseId == selectedCourseId }
    }

    // Load cached confusion groups immediately from local storage (Zero-reset, instant display)
    val initialCache = remember(selectedCourseId) {
        ConfusionSetCacheManager.loadConfusionGroups(
            context = context,
            courseId = selectedCourseId,
            currentWords = words.filter { it.courseId == selectedCourseId },
            config = config
        )
    }

    // State tracking groups, cache status, and rematching
    var isComputing by remember { mutableStateOf(false) }
    var confusionGroups by remember(selectedCourseId) {
        mutableStateOf<List<ConfusionGroup>>(initialCache?.groups ?: emptyList())
    }
    var isCachedMatch by remember(selectedCourseId) {
        mutableStateOf(initialCache?.isExactMatch == true)
    }
    var cacheTimestamp by remember(selectedCourseId) {
        mutableStateOf(initialCache?.timestamp ?: 0L)
    }
    var rematchTrigger by remember { mutableIntStateOf(0) }

    // Recompute or load confusion groups asynchronously when course, words, config, or user rematch changes
    LaunchedEffect(courseWords, config, rematchTrigger) {
        if (courseWords.isEmpty()) {
            confusionGroups = emptyList()
            isComputing = false
            isCachedMatch = false
            return@LaunchedEffect
        }

        // Try to load cached groups from local storage
        val cached = ConfusionSetCacheManager.loadConfusionGroups(
            context = context,
            courseId = selectedCourseId,
            currentWords = courseWords,
            config = config
        )

        // If rematch was not explicitly triggered and cached groups match exact words + settings:
        if (rematchTrigger == 0 && cached != null && cached.isExactMatch) {
            confusionGroups = cached.groups
            isCachedMatch = true
            cacheTimestamp = cached.timestamp
            isComputing = false
            return@LaunchedEffect
        }

        // Otherwise compute similarity in background and save to local storage
        isComputing = true
        withContext(Dispatchers.Default) {
            val groups = ConfusionSimilarityEngine.findConfusionGroups(
                words = courseWords,
                config = config,
                getPlaceValue = getPlaceValue
            )
            // Persist matched confusion sets to local storage
            ConfusionSetCacheManager.saveConfusionGroups(
                context = context,
                courseId = selectedCourseId,
                words = courseWords,
                config = config,
                groups = groups
            )
            withContext(Dispatchers.Main) {
                confusionGroups = groups
                isCachedMatch = true
                cacheTimestamp = System.currentTimeMillis()
                isComputing = false
            }
        }
    }

    // Search filtered groups
    val filteredGroups = remember(confusionGroups, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) confusionGroups
        else confusionGroups.filter { group ->
            group.words.any { item ->
                item.place1.lowercase(Locale.ROOT).contains(query) ||
                item.place2.lowercase(Locale.ROOT).contains(query) ||
                (item.place3?.lowercase(Locale.ROOT)?.contains(query) == true)
            }
        }
    }

    // Unique words count across matching groups
    val uniqueWordsCount = remember(filteredGroups) {
        filteredGroups.flatMap { it.words.map { w -> w.wordEntity.id } }.distinct().size
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 1. Compact Header
        Surface(
            color = Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("confusion_set_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Games",
                                tint = SlateText
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Confusion Set",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = IndigoLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${config.threshold.toInt()}%",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Advanced Settings button
                    IconButton(
                        onClick = { showAdvancedSettings = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (showAdvancedSettings) IndigoLight else Color(0xFFF1F5F9))
                            .testTag("confusion_set_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Advanced Settings",
                            tint = if (showAdvancedSettings) IndigoPrimary else SlateText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Course Selector Bar
                val selectedCourse = courses.find { it.id == selectedCourseId }
                Surface(
                    onClick = { showCourseMenu = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = selectedCourse?.title ?: "Select Course",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "(${courseWords.size} words)",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = SlateMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Course",
                            tint = SlateMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Course Selection Dropdown Menu
                DropdownMenu(
                    expanded = showCourseMenu,
                    onDismissRequest = { showCourseMenu = false }
                ) {
                    eligibleCourses.forEach { course ->
                        val count = words.count { it.courseId == course.id }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = course.title,
                                        fontWeight = if (course.id == selectedCourseId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (course.id == selectedCourseId) IndigoPrimary else SlateText
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "$count words",
                                        fontSize = 11.sp,
                                        color = SlateMuted
                                    )
                                }
                            },
                            onClick = {
                                selectedCourseId = course.id
                                ConfusionSetCacheManager.saveLastCourseId(context, course.id)
                                showCourseMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Comparison Field Selector (Using dynamic labels from course header/excel file)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ComparisonField.entries.forEach { field ->
                        val isSelected = config.field == field
                        val dynamicLabel = getPlaceLabel(field.placeNumber, selectedCourseId)

                        Surface(
                            onClick = {
                                val newConfig = config.copy(
                                    field = field,
                                    mode = if (field != ComparisonField.PLACE1 && config.mode == MatchingMode.SPELLING) {
                                        MatchingMode.MEANING
                                    } else if (field == ComparisonField.PLACE1 && config.mode == MatchingMode.MEANING) {
                                        MatchingMode.SPELLING
                                    } else config.mode
                                )
                                config = newConfig
                                ConfusionSetCacheManager.saveConfig(context, newConfig)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) IndigoPrimary else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dynamicLabel,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else SlateText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Instant Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter confusion sets by word...",
                            fontSize = 12.5.sp,
                            color = SlateMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = SlateMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = SlateBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }

        // 4. Clean Summary Bar with Local Storage Cache status & Rematch action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${filteredGroups.size} Groups",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
                Text(
                    text = "·",
                    color = SlateMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$uniqueWordsCount Words",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.5.sp,
                    color = SlateMuted
                )

                // Cache indicator badge: Shows results are saved in local storage
                if (isCachedMatch && confusionGroups.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Saved in local storage",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Saved",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccess
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Re-match / Refresh Button to allow manual recalculation anytime
                IconButton(
                    onClick = {
                        hapticHelper.subtleTick()
                        rematchTrigger++
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .testTag("confusion_rematch_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-match words",
                        tint = if (isComputing) IndigoPrimary else SlateMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Global Quiz Button: Quizzes all confusion sets together
                if (filteredGroups.isNotEmpty()) {
                    Surface(
                        onClick = { showGlobalQuiz = true },
                        shape = RoundedCornerShape(10.dp),
                        color = IndigoLight,
                        border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("global_confusion_quiz_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = "Quiz All Sets",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Quiz All (${filteredGroups.size})",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }
                }
            }
        }

        // 5. Main Content: List of Confusion Groups or Loading / Empty States
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                isComputing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = IndigoPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Analyzing vocabulary similarity...",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                    }
                }
                courseWords.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = SlateBorder,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No words found in this course",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateText
                        )
                        Text(
                            text = "Select another course or add vocabulary words.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            color = SlateMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                filteredGroups.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching confusion groups found" else "No confusion pairs found",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Try searching for a different word or clearing the query."
                            } else {
                                "No words meet the current ${config.threshold.toInt()}% threshold in '${getPlaceLabel(config.field.placeNumber, selectedCourseId)}'. Try lowering the threshold or changing the matching mode."
                            },
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            color = SlateMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (config.threshold > 65.0 && searchQuery.isBlank()) {
                            Button(
                                onClick = {
                                    val newConfig = config.copy(threshold = 65.0)
                                    config = newConfig
                                    ConfusionSetCacheManager.saveConfig(context, newConfig)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                            ) {
                                Text("Try 65% Similarity Threshold", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredGroups, key = { it.id }) { group ->
                            ConfusionGroupCard(
                                group = group,
                                config = config,
                                onSpeak = { text -> tts.speak(text) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Advanced Settings Dialog / Popover (Compact, side-by-side, toggle switches & icons)
    if (showAdvancedSettings) {
        AdvancedSettingsDialog(
            config = config,
            onConfigChange = { newConfig ->
                config = newConfig
                ConfusionSetCacheManager.saveConfig(context, newConfig)
            },
            onDismiss = { showAdvancedSettings = false }
        )
    }

    // Global Quiz Dialog encompassing all confusion groups in the course
    if (showGlobalQuiz && filteredGroups.isNotEmpty()) {
        GlobalConfusionQuizDialog(
            groups = filteredGroups,
            onDismiss = { showGlobalQuiz = false },
            onRateWord = onRateWord,
            onSpeak = { tts.speak(it) }
        )
    }
}

/**
 * Individual Confusion Set Card displaying clustered words.
 * Clean & minimal: No duplicate reason text, no per-card quiz button.
 */
@Composable
private fun ConfusionGroupCard(
    group: ConfusionGroup,
    config: ConfusionConfig,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Top Bar: Clean "90% Match" Badge and Word Count (No duplicate reason line, no redundant quiz button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val scoreColor = when {
                    group.maxSimilarity >= 85.0 -> EmeraldSuccess
                    group.maxSimilarity >= 75.0 -> IndigoPrimary
                    else -> AmberWarning
                }
                val scoreBg = when {
                    group.maxSimilarity >= 85.0 -> EmeraldLight
                    group.maxSimilarity >= 75.0 -> IndigoLight
                    else -> AmberLight
                }

                Surface(
                    color = scoreBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "%.0f%% Match".format(Locale.US, group.maxSimilarity),
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }

                Text(
                    text = "${group.words.size} Words",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Word Items inside the confusion group
            group.words.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                ConfusionWordRow(
                    item = item,
                    config = config,
                    onSpeak = { onSpeak(item.place1) }
                )
            }
        }
    }
}

/**
 * Individual Word Row inside a Confusion Group Card.
 * Uses word status color indicator directly without buttons or dropdowns.
 * Omits "Place 3:" prefix text to display extra/synonyms directly.
 */
@Composable
private fun ConfusionWordRow(
    item: ConfusionSimilarityEngine.ConfusionWordItem,
    config: ConfusionConfig,
    onSpeak: () -> Unit
) {
    val statusColor = when (item.wordEntity.status) {
        "know" -> EmeraldSuccess
        "dont_know" -> RoseError
        "confusion" -> AmberWarning
        else -> Color(0xFFCBD5E1) // unrated neutral slate
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Subtle vertical status color indicator on the left
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .width(3.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Word text with audio speaker icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.place1,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )

                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak word",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Optional phonetic representation tag when in phonetic mode
                        if (config.mode == MatchingMode.PRONUNCIATION && !item.phoneticPrimary.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFFEDE9FE),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "[${item.phoneticPrimary}]",
                                    fontSize = 10.sp,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6D28D9),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Minimal status color dot indicating word status (no bulky button)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Meaning / Place 2 display
                Text(
                    text = item.place2,
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 18.sp
                )

                // Place 3 display directly (without "Place 3:" label)
                if (!item.place3.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.place3,
                        fontSize = 11.5.sp,
                        color = SlateMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Advanced Settings Modal Dialog:
 * - Matching Mode: Compact 2x2 grid side-by-side with short labels ("Spelling", "Phonetic", "Combined", "Meaning")
 * - Algorithm: Single-row segmented switch toggle (Levenshtein | Jaro-Winkler)
 * - Sort Groups By: Compact side-by-side buttons with icons and short labels
 */
@Composable
private fun AdvancedSettingsDialog(
    config: ConfusionConfig,
    onConfigChange: (ConfusionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var tempConfig by remember(config) { mutableStateOf(config) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Advanced Settings",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Matching Mode (Compact 2x2 Grid with Short Labels)
                Text(
                    text = "Matching Mode",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(6.dp))

                val modeList = listOf(
                    Triple(MatchingMode.SPELLING, "Spelling", Icons.Default.Spellcheck),
                    Triple(MatchingMode.PRONUNCIATION, "Phonetic", Icons.Default.VolumeUp),
                    Triple(MatchingMode.COMBINED, "Combined", Icons.Default.Tune),
                    Triple(MatchingMode.MEANING, "Meaning", Icons.Default.Translate)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modeList.take(2).forEach { (mode, label, icon) ->
                            val isSel = tempConfig.mode == mode
                            Surface(
                                onClick = { tempConfig = tempConfig.copy(mode = mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) IndigoLight else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSel) IndigoPrimary else SlateBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSel) IndigoPrimary else SlateMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) IndigoPrimary else SlateText
                                    )
                                }
                            }
                        }
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modeList.drop(2).forEach { (mode, label, icon) ->
                            val isSel = tempConfig.mode == mode
                            Surface(
                                onClick = { tempConfig = tempConfig.copy(mode = mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) IndigoLight else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSel) IndigoPrimary else SlateBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSel) IndigoPrimary else SlateMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) IndigoPrimary else SlateText
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Similarity Threshold Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Threshold",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Text(
                        text = "${tempConfig.threshold.toInt()}%",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary
                    )
                }
                Slider(
                    value = tempConfig.threshold.toFloat(),
                    onValueChange = { tempConfig = tempConfig.copy(threshold = it.toDouble()) },
                    valueRange = 50f..95f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary,
                        inactiveTrackColor = SlateBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Algorithm: Single-line Segmented Switch
                Text(
                    text = "Algorithm",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                    ) {
                        SimilarityAlgorithm.entries.forEach { alg ->
                            val isSel = tempConfig.algorithm == alg
                            val shortName = if (alg == SimilarityAlgorithm.LEVENSHTEIN) "Levenshtein" else "Jaro-Winkler"
                            Surface(
                                onClick = { tempConfig = tempConfig.copy(algorithm = alg) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) Color.White else Color.Transparent,
                                shadowElevation = if (isSel) 1.dp else 0.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shortName,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) IndigoPrimary else SlateMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Combined Mode Weights (if applicable)
                if (tempConfig.mode == MatchingMode.COMBINED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Spelling / Phonetic (%.0f%% / %.0f%%)".format(
                            Locale.US, tempConfig.spellingWeight * 100, tempConfig.pronunciationWeight * 100
                        ),
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Slider(
                        value = tempConfig.spellingWeight.toFloat(),
                        onValueChange = {
                            val sw = it.toDouble()
                            tempConfig = tempConfig.copy(
                                spellingWeight = sw,
                                pronunciationWeight = 1.0 - sw
                            )
                        },
                        valueRange = 0.1f..0.9f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPrimary,
                            activeTrackColor = IndigoPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Sort Groups By (Side-by-side with icons)
                Text(
                    text = "Sort Groups By",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(6.dp))

                val sortOptions = listOf(
                    Triple(SortOption.SIMILARITY_DESC, "Score", Icons.Default.TrendingUp),
                    Triple(SortOption.SIZE_DESC, "Size", Icons.Default.Groups),
                    Triple(SortOption.ALPHABETICAL, "A–Z", Icons.Default.SortByAlpha)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sortOptions.forEach { (sort, label, icon) ->
                        val isSel = tempConfig.sortBy == sort
                        Surface(
                            onClick = { tempConfig = tempConfig.copy(sortBy = sort) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) IndigoLight else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isSel) IndigoPrimary else SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSel) IndigoPrimary else SlateMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) IndigoPrimary else SlateText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Reset to Defaults and Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { tempConfig = ConfusionConfig() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onConfigChange(tempConfig)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Apply Settings", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Data structure representing a question in the Global Confusion Quiz.
 */
private data class GlobalQuizQuestionItem(
    val targetWord: ConfusionSimilarityEngine.ConfusionWordItem,
    val options: List<ConfusionSimilarityEngine.ConfusionWordItem>,
    val groupIndex: Int
)

/**
 * Global Quiz Dialog: Tests user across all confusion sets in the selected course.
 */
@Composable
private fun GlobalConfusionQuizDialog(
    groups: List<ConfusionGroup>,
    onDismiss: () -> Unit,
    onRateWord: (String, String) -> Unit,
    onSpeak: (String) -> Unit
) {
    // Generate questions across all confusion groups
    val questions = remember(groups) {
        val qList = mutableListOf<GlobalQuizQuestionItem>()
        groups.forEachIndexed { gIdx, grp ->
            grp.words.forEach { word ->
                qList.add(
                    GlobalQuizQuestionItem(
                        targetWord = word,
                        options = grp.words.shuffled(),
                        groupIndex = gIdx + 1
                    )
                )
            }
        }
        // Shuffle questions and limit to up to 20 for an engaging session
        qList.shuffled().take(20)
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }

    val currentQ = questions.getOrNull(currentIndex)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confusion Set Quiz",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted
                        )
                    }
                }

                if (currentQ == null || currentIndex >= questions.size) {
                    // Quiz finished summary
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Quiz Completed!",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$correctCount of ${questions.size} Correct",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateText
                        )
                        val accuracy = if (questions.isNotEmpty()) (correctCount * 100 / questions.size) else 0
                        Text(
                            text = "Accuracy: $accuracy%",
                            fontSize = 12.5.sp,
                            color = SlateMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Done")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${currentIndex + 1} of ${questions.size}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary
                        )
                        Text(
                            text = "Set #${currentQ.groupIndex}",
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Question prompt: Show Meaning (Place 2) and ask which confused word it matches
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Which word matches this meaning?",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentQ.targetWord.place2,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Confused word options to choose from
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentQ.options.forEach { opt ->
                            val isCorrect = opt.wordEntity.id == currentQ.targetWord.wordEntity.id
                            val isChosen = selectedOption == opt.place1

                            val containerBg = when {
                                !isAnswered -> Color.White
                                isCorrect -> EmeraldLight
                                isChosen -> RoseLight
                                else -> Color.White
                            }
                            val borderCol = when {
                                !isAnswered -> SlateBorder
                                isCorrect -> EmeraldSuccess
                                isChosen -> RoseError
                                else -> SlateBorder
                            }

                            Surface(
                                onClick = {
                                    if (!isAnswered) {
                                        selectedOption = opt.place1
                                        isAnswered = true
                                        if (isCorrect) {
                                            correctCount++
                                            onRateWord(currentQ.targetWord.wordEntity.id, "know")
                                        } else {
                                            onRateWord(currentQ.targetWord.wordEntity.id, "confusion")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = containerBg,
                                border = BorderStroke(1.5.dp, borderCol),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = opt.place1,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateText
                                        )
                                        IconButton(
                                            onClick = { onSpeak(opt.place1) },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    if (isAnswered) {
                                        if (isCorrect) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Correct",
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (isChosen) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "Wrong",
                                                tint = RoseError,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isAnswered) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                selectedOption = null
                                isAnswered = false
                                currentIndex++
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentIndex + 1 < questions.size) "Next Word" else "See Results")
                        }
                    }
                }
            }
        }
    }
}
