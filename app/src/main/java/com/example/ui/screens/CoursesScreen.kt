package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CourseEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.repository.DriveSyncSummary
import com.example.data.repository.LocalCourseFileInput
import com.example.data.sync.DiscoveredDriveFile
import com.example.ui.theme.AppPalette
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CoursesScreen(
    courses: List<CourseEntity>,
    activeCourseId: String?,
    words: List<VocabularyWordEntity>,
    isSyncingDrive: Boolean,
    driveSyncUrl: String,
    driveSyncSummary: DriveSyncSummary?,
    driveCourses: List<DiscoveredDriveFile>,
    isRefreshingDriveCourses: Boolean,
    downloadingCourseFileIds: Set<String>,
    onRefreshDriveCourses: () -> Unit,
    onDownloadDriveCourse: (DiscoveredDriveFile) -> Unit,
    onSelectCourse: (String) -> Unit,
    onDeleteCourse: (String, Boolean) -> Unit,
    onUpdateCourse: (String, String, String?) -> Unit,
    onCreateCourse: (String, String?, String?, Boolean) -> Unit,
    onSyncFromDrive: (String, Boolean) -> Unit,
    onBatchImportFiles: (List<LocalCourseFileInput>, Boolean) -> Unit,
    onClearDriveSummary: () -> Unit,
    onManageWords: (String) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val palette = LocalAppPalette.current
    val context = LocalContext.current

    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var courseBeingEdited by remember { mutableStateOf<CourseEntity?>(null) }
    var courseToDelete by remember { mutableStateOf<CourseEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("courses_screen_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Section: Clean title & installed courses count
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Course Library",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "${courses.size} installed courses",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    color = palette.textMuted
                )
            }
        }

        // Sync Summary Notification Banner
        if (driveSyncSummary != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (palette.isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Synced: ${driveSyncSummary.totalCourses} courses, ${driveSyncSummary.totalAddedWords + driveSyncSummary.totalUpdatedWords} words",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (palette.isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
                        )
                        IconButton(onClick = onClearDriveSummary, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // Section: Installed Courses
        if (courses.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "No Courses in Library",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            "Choose any course from the Google Drive catalog below and click 'Add to list' to download, or click '+ New' to create one.",
                            fontSize = 11.5.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Courses List using Swipeable cards
            items(courses.distinctBy { it.id }, key = { "course_${it.id}" }) { course ->
                val courseWordCount = words.count { it.courseId == course.id }
                val courseFlaggedCount = words.count { it.courseId == course.id && it.isReported }
                val isActive = course.id == activeCourseId

                CourseSwipeableCard(
                    course = course,
                    isActive = isActive,
                    wordCount = courseWordCount,
                    flaggedCount = courseFlaggedCount,
                    palette = palette,
                    onClick = {
                        onSelectCourse(course.id)
                    },
                    onEdit = { courseBeingEdited = course },
                    onDelete = { courseToDelete = course },
                    onManageWords = {
                        onManageWords(course.id)
                    }
                )
            }
        }

        // Section: Catalog Header
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catalog",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary
                )

                IconButton(
                    onClick = onRefreshDriveCourses,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.surface)
                        .testTag("refresh_drive_courses_button")
                ) {
                    if (isRefreshingDriveCourses) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = IndigoPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Catalog",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Drive Courses List
        items(driveCourses.distinctBy { it.fileId }, key = { "drive_${it.fileId}" }) { item ->
            val cleanTitle = item.title
                .substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
            val isDownloading = item.fileId in downloadingCourseFileIds
            val downloadedCourse = courses.find {
                it.title.equals(cleanTitle, ignoreCase = true) ||
                        it.title.equals(item.title.substringBeforeLast("."), ignoreCase = true) ||
                        it.title.equals(item.title, ignoreCase = true)
            }
            val isAlreadyAdded = downloadedCourse != null
            val wordCountForCourse = if (downloadedCourse != null) words.count { it.courseId == downloadedCourse.id } else null

            DriveCatalogCard(
                item = item,
                cleanTitle = cleanTitle,
                isAlreadyAdded = isAlreadyAdded,
                isDownloading = isDownloading,
                wordCount = wordCountForCourse,
                palette = palette,
                onAddToList = { onDownloadDriveCourse(item) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Dialog: Create Course
    if (showCreateCourseDialog) {
        CourseCreateModal(
            onDismiss = { showCreateCourseDialog = false },
            onCreate = { title, desc, fileContent, isJson ->
                onCreateCourse(title, desc, fileContent, isJson)
                showCreateCourseDialog = false
            }
        )
    }

    // Dialog: Edit Course
    if (courseBeingEdited != null) {
        CourseEditModal(
            course = courseBeingEdited!!,
            onDismiss = { courseBeingEdited = null },
            onSave = { id, title, desc ->
                onUpdateCourse(id, title, desc)
                courseBeingEdited = null
            }
        )
    }

    // Dialog: Delete Course Confirmation
    if (courseToDelete != null) {
        val course = courseToDelete!!
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = {
                Text("Delete Course", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete \"${course.title}\"?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "• Keep Progress: Course and words are removed from library, but your rating progress is saved in backup.\n• Delete All: Permanently removes course, words, and progress records.",
                        fontSize = 11.5.sp,
                        color = palette.textSecondary
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val id = course.id
                            courseToDelete = null
                            onDeleteCourse(id, true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Keep Progress", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val id = course.id
                            courseToDelete = null
                            onDeleteCourse(id, false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Delete All", fontSize = 11.5.sp, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Dialog: Drive Sync
    if (showDriveSyncDialog) {
        CourseDriveSyncModal(
            initialUrl = driveSyncUrl,
            isSyncing = isSyncingDrive,
            onDismiss = { showDriveSyncDialog = false },
            onSync = { url, preserve ->
                onSyncFromDrive(url, preserve)
                showDriveSyncDialog = false
            }
        )
    }
}

@Composable
private fun CourseSwipeableCard(
    course: CourseEntity,
    isActive: Boolean,
    wordCount: Int,
    flaggedCount: Int,
    palette: AppPalette,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageWords: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val maxDrag = 140f
    val triggerThreshold = 75f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Swipe Reveal Action Background
        val currentOffset = offsetX.value
        if (currentOffset != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (currentOffset > 0) IndigoPrimary.copy(alpha = 0.9f)
                        else Color(0xFFE11D48).copy(alpha = 0.9f)
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (currentOffset > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (currentOffset > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Delete", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Foreground Course Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = palette.surface
            ),
            border = BorderStroke(
                1.dp,
                if (isActive) IndigoPrimary else palette.border
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(course.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val currentVal = offsetX.value
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                if (currentVal > triggerThreshold) {
                                    onEdit()
                                } else if (currentVal < -triggerThreshold) {
                                    onDelete()
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Text(
                            text = "$wordCount words",
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                        if (flaggedCount > 0) {
                            Text(
                                text = "🚩 $flaggedCount flagged",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE11D48)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveCatalogCard(
    item: DiscoveredDriveFile,
    cleanTitle: String,
    isAlreadyAdded: Boolean,
    isDownloading: Boolean,
    wordCount: Int?,
    palette: AppPalette,
    onAddToList: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(
            1.dp,
            if (isAlreadyAdded) EmeraldSuccess.copy(alpha = 0.4f) else palette.border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (wordCount != null) {
                    Text(
                        text = "$wordCount words",
                        fontSize = 11.sp,
                        color = palette.textMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = IndigoPrimary
                )
            } else if (!isAlreadyAdded) {
                Button(
                    onClick = onAddToList,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Add to list", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CourseCreateModal(
    onDismiss: () -> Unit,
    onCreate: (title: String, desc: String?, fileContent: String?, isJson: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Course", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) onCreate(title.trim(), desc.ifBlank { null }, null, false)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CourseEditModal(
    course: CourseEntity,
    onDismiss: () -> Unit,
    onSave: (id: String, title: String, desc: String?) -> Unit
) {
    var title by remember { mutableStateOf(course.title) }
    var desc by remember { mutableStateOf(course.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Course", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) onSave(course.id, title.trim(), desc.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CourseDriveSyncModal(
    initialUrl: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: (url: String, preserve: Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var preserve by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync from Google Drive", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Google Drive Folder / Sheet URL", fontSize = 11.sp) },
                    placeholder = { Text("https://drive.google.com/drive/folders/...", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = preserve, onCheckedChange = { preserve = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preserve existing progress", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) onSync(url.trim(), preserve)
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text(if (isSyncing) "Syncing..." else "Sync Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
