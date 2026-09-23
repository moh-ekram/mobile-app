package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CourseEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.UserSession
import com.example.data.model.VocabularyWordEntity
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun HomeScreen(
    user: UserSession?,
    words: List<VocabularyWordEntity>,
    progress: UserProgressEntity?,
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "course_default",
    onSelectCourse: (String) -> Unit = {},
    onCreateCourseClick: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectStatus: (String) -> Unit = {},
    onSelectFlagged: () -> Unit = {},
    widgetWord: VocabularyWordEntity? = null,
    widgetCategory: String = "all",
    onSetWidgetCategory: (String) -> Unit = {},
    onCycleWidgetWord: () -> Unit = {},
    onRateWidgetWord: (String, String) -> Unit = { _, _ -> },
    onRefreshWidget: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Cycle word every time home screen appears
    LaunchedEffect(Unit) {
        onRefreshWidget()
    }

    // Filter data specifically for the currently selected active course
    val selectedCourse = courses.find { it.id == activeCourseId } ?: courses.firstOrNull()
    val activeWords = if (selectedCourse != null) {
        words.filter { it.courseId == selectedCourse.id }
    } else {
        words
    }

    val totalWords = activeWords.size
    val knowCount = activeWords.count { it.status == "know" }
    val confusionCount = activeWords.count { it.status == "confusion" }
    val dontKnowCount = activeWords.count { it.status == "dont_know" }
    val unratedCount = activeWords.count { it.status == "unrated" }
    val flaggedCount = activeWords.count { it.isReported }
    val masteryPercent = if (totalWords > 0) ((knowCount.toFloat() / totalWords) * 100).toInt() else 0

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(activeCourseId, totalWords) {
        startAnimation = false
        kotlinx.coroutines.delay(40)
        startAnimation = true
    }

    val animatedKnowCount by animateIntAsState(
        targetValue = if (startAnimation) knowCount else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "know_count_anim"
    )
    val animatedConfusionCount by animateIntAsState(
        targetValue = if (startAnimation) confusionCount else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "confusion_count_anim"
    )
    val animatedDontKnowCount by animateIntAsState(
        targetValue = if (startAnimation) dontKnowCount else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "dont_know_count_anim"
    )
    val animatedUnratedCount by animateIntAsState(
        targetValue = if (startAnimation) unratedCount else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "unrated_count_anim"
    )
    val animatedMasteryPercent by animateIntAsState(
        targetValue = if (startAnimation) masteryPercent else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "mastery_percent_anim"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnimation && totalWords > 0) knowCount.toFloat() / totalWords else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    val palette = LocalAppPalette.current

    var showCourseDialog by remember { mutableStateOf(false) }

    // Course Selection Dialog Popup
    if (showCourseDialog) {
        AlertDialog(
            onDismissRequest = { showCourseDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Course",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    IconButton(
                        onClick = { showCourseDialog = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateLight)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (courses.isEmpty()) {
                        Text(
                            text = "No courses available. Create a new course below.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(courses, key = { it.id }) { course ->
                                val isSelected = course.id == activeCourseId
                                val courseWords = words.filter { it.courseId == course.id }
                                val courseTotal = courseWords.size
                                val courseKnown = courseWords.count { it.status == "know" }
                                val coursePercent = if (courseTotal > 0) ((courseKnown.toFloat() / courseTotal) * 100).toInt() else 0

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectCourse(course.id)
                                            showCourseDialog = false
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) IndigoPrimary else Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = course.title,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) IndigoPrimary else SlateText
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(EmeraldLight)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "SELECTED",
                                                            fontFamily = PoppinsFontFamily,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = EmeraldSuccess
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$courseKnown / $courseTotal words ($coursePercent%)",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 11.sp,
                                                color = SlateMuted
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                onSelectCourse(course.id)
                                                showCourseDialog = false
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = IndigoPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            showCourseDialog = false
                            onCreateCourseClick()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, IndigoPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Course",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Welcome back,",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = user?.displayName ?: "Vocabulary Learner",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // Streak Pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${progress?.streakDays ?: 1} Day Streak",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Mastery Progress",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = EmeraldSuccess,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$animatedMasteryPercent% Mastered",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$animatedKnowCount of $totalWords words",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Course Selection Section with 'Select course' button
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCourseDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = selectedCourse?.title ?: "Select Course",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText,
                                maxLines = 1
                            )
                            Text(
                                text = "${activeWords.size} words • ${masteryPercent}% mastered",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    Button(
                        onClick = { showCourseDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Select course",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Stats Matrix for Selected Course
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedCourse?.title ?: "Course"} Statistics",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
                Text(
                    text = "$animatedKnowCount / $totalWords mastered",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = EmeraldSuccess
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Know",
                    count = animatedKnowCount,
                    bg = EmeraldLight,
                    border = EmeraldBorder,
                    textColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectStatus("know") }
                )
                StatCard(
                    title = "Confusion",
                    count = animatedConfusionCount,
                    bg = AmberLight,
                    border = AmberBorder,
                    textColor = AmberWarning,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectStatus("confusion") }
                )
                StatCard(
                    title = "Don't Know",
                    count = animatedDontKnowCount,
                    bg = RoseLight,
                    border = RoseBorder,
                    textColor = RoseError,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectStatus("dont_know") }
                )
                StatCard(
                    title = "Unrated",
                    count = animatedUnratedCount,
                    bg = Color(0xFFF1F5F9),
                    border = SlateBorder,
                    textColor = SlateText,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectStatus("unrated") }
                )
            }
        }

        // Quick Actions Section
        item {
            Text(
                text = "Quick Practice",
                fontFamily = PoppinsFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SlateText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            QuickPracticeTile(
                title = "Flashcard",
                icon = Icons.Default.Style,
                accent = IndigoPrimary,
                onClick = { onNavigate("flashcard") }
            )
        }

        item {
            QuickPracticeTile(
                title = "Practice",
                icon = Icons.Default.SportsEsports,
                accent = EmeraldSuccess,
                onClick = { onNavigate("games") }
            )
        }

        item {
            QuickPracticeTile(
                title = "Question Bank",
                icon = Icons.Default.Quiz,
                accent = AmberWarning,
                onClick = { onNavigate("games") }
            )
        }

        item {
            QuickPracticeTile(
                title = "Read Article",
                icon = Icons.Default.MenuBook,
                accent = Color(0xFF7C3AED),
                onClick = { onNavigate("article_reader") }
            )
        }

        // Word Groups Breakdown
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedCourse?.title ?: "Course"} Groups",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
                Text(
                    text = "${activeWords.size} words",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateMuted
                )
            }
        }

        val groups = activeWords.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct().sorted()
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { grp ->
                    val grpWords = activeWords.filter { it.group == grp }
                    val grpKnow = grpWords.count { it.status == "know" }
                    val grpPercent = if (grpWords.isNotEmpty()) (grpKnow * 100 / grpWords.size) else 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectGroup(grp)
                                onNavigate("flashcard")
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (palette.isDark) palette.surface else Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (grpPercent == 100) EmeraldSuccess.copy(alpha = 0.5f) else SlateBorder
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val displayGrpName = if (grp.isNotBlank() && grp.all { it.isDigit() }) "Group $grp" else grp
                                    Text(
                                        text = displayGrpName,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "(${grpWords.size} words)",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (grpPercent == 100) EmeraldSuccess else EmeraldLight)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$grpPercent% Mastered",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (grpPercent == 100) Color.White else EmeraldSuccess
                                    )
                                }
                            }

                            // Color fill progress bar
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { if (grpWords.isNotEmpty()) grpKnow.toFloat() / grpWords.size else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = EmeraldSuccess,
                                    trackColor = if (palette.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$grpKnow of ${grpWords.size} mastered",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textMuted
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Practice",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IndigoPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = IndigoPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: Int,
    bg: Color,
    border: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                fontFamily = PoppinsFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun QuickPracticeTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateText,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


