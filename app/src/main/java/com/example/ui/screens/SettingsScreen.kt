package com.example.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.UserSession
import com.example.data.model.VocabularyWordEntity
import com.example.data.repository.DriveSyncSummary
import com.example.data.repository.LocalCourseFileInput
import com.example.data.sync.DiscoveredDriveFile
import com.example.notification.NotificationHelper
import com.example.ui.theme.*
import com.example.widget.DailyVocabWidgetProvider
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: UserSession?,
    progress: UserProgressEntity?,
    backupDirectoryPath: String,
    customBackupTreeUri: String? = null,
    words: List<VocabularyWordEntity> = emptyList(),
    games: List<GamePracticeEntity> = emptyList(),
    questions: List<QuestionBankEntity> = emptyList(),
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "",
    selectedCourseIds: Set<String> = emptySet(),
    isDarkTheme: Boolean = false,
    isFlipAnimationEnabled: Boolean = true,
    isFocusMode: Boolean = false,
    isHapticEnabled: Boolean = true,
    isSyncingDrive: Boolean = false,
    driveSyncUrl: String = "",
    driveSyncSummary: DriveSyncSummary? = null,
    driveCourses: List<DiscoveredDriveFile> = emptyList(),
    isRefreshingDriveCourses: Boolean = false,
    downloadingCourseFileIds: Set<String> = emptySet(),
    initialTab: Int = 0,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onToggleFlipAnimation: (Boolean) -> Unit = {},
    onToggleFocusMode: (Boolean) -> Unit = {},
    onToggleHaptic: (Boolean) -> Unit = {},
    onSetCustomBackupTreeUri: (Uri) -> Unit = {},
    onManualBackup: () -> Unit = {},
    onBackupToDriveDirect: () -> Unit = {},
    onRestoreFromDriveDirect: () -> Unit = {},
    onRefreshWidget: () -> Unit = {},
    onExportToUri: (Uri) -> Unit = {},
    onRestoreFromUri: (Uri) -> Unit = {},
    onCloudSync: () -> Unit = {},
    onRefreshDriveCourses: () -> Unit = {},
    onDownloadDriveCourse: (DiscoveredDriveFile) -> Unit = {},
    onRestoreBackup: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateProfile: (displayName: String, avatarUri: String?, targetExam: String, dailyGoal: Int, bio: String) -> Unit = { _, _, _, _, _ -> },
    onLogout: () -> Unit = {},
    onToggleCourseSelection: (String) -> Unit = {},
    onSelectAllCourses: () -> Unit = {},
    onDeselectAllCourses: () -> Unit = {},
    onSyncFromDrive: (String, Boolean) -> Unit = { _, _ -> },
    onBatchImportFiles: (List<LocalCourseFileInput>, Boolean) -> Unit = { _, _ -> },
    onClearDriveSummary: () -> Unit = {},
    onCreateCourse: (String, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onSelectCourse: (String) -> Unit = {},
    onDeleteCourse: (String, Boolean) -> Unit = { _, _ -> },
    onAddWord: (VocabularyWordEntity) -> Unit = {},
    onUpdateWord: (VocabularyWordEntity) -> Unit = {},
    onUpdateCourse: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteWord: (String) -> Unit = {},
    onDeleteWords: (List<String>) -> Unit = {},
    onUpdateGroupForWords: (List<String>, String) -> Unit = { _, _ -> },
    onMoveWordsToCourse: (List<String>, String) -> Unit = { _, _ -> },
    onUpdateStatusForWords: (List<String>, String) -> Unit = { _, _ -> },
    onReportWord: (String, Boolean, String?) -> Unit = { _, _, _ -> },
    onDeleteGame: (String) -> Unit = {},
    onDeleteGamesBySection: (String) -> Unit = {},
    onClearAllGames: () -> Unit = {},
    onDeleteQuestion: (String) -> Unit = {},
    onClearAllQB: () -> Unit = {},
    onImportCourse: (String, Boolean, String, String?) -> Unit = { _, _, _, _ -> },
    onImportGame: (String, String) -> Unit = { _, _ -> },
    onImportGameItems: (List<GamePracticeEntity>) -> Unit = {},
    onImportQB: (String) -> Unit = {},
    qbSyncUrl: String = "",
    isSyncingQB: Boolean = false,
    onImportQBFromUrl: (String, Boolean) -> Unit = { _, _ -> },
    onImportQBBytes: (ByteArray, String, Boolean) -> Unit = { _, _, _ -> },
    onExportQBProgressToUri: (Uri) -> Unit = {},
    onRestoreQBProgress: (ByteArray) -> Unit = {},
    onResetData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog states
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var showAddWordDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showQbSyncDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf("") }
    var wordBeingEdited by remember { mutableStateOf<VocabularyWordEntity?>(null) }
    var courseBeingEdited by remember { mutableStateOf<CourseEntity?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // File picker for Question Bank (CSV/JSON/Excel)
    val qbFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "qb_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    onImportQBBytes(bytes, fileName, false)
                    Toast.makeText(context, "Processing Question Bank file...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Multi-file picker for batch courses
    val batchFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val fileInputs = mutableListOf<LocalCourseFileInput>()
            for (uri in uris) {
                try {
                    var fileName = "Course_${System.currentTimeMillis()}"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        fileInputs.add(LocalCourseFileInput(fileName, bytes))
                    }
                } catch (_: Exception) {}
            }
            if (fileInputs.isNotEmpty()) {
                onBatchImportFiles(fileInputs, true)
            }
        }
    }

    // SAF Document Launchers
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            onSetCustomBackupTreeUri(uri)
        }
    }

    val driveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) onExportToUri(uri)
    }

    val driveRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onRestoreFromUri(uri)
    }

    val qbProgressExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) onExportQBProgressToUri(uri)
    }

    val qbProgressRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    onRestoreQBProgress(bytes)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Settings tabs definition
    val tabs = listOf(
        SettingsTabItem("General", Icons.Default.Tune),
        SettingsTabItem("Courses", Icons.Default.School),
        SettingsTabItem("Widget & Reminders", Icons.Default.Widgets),
        SettingsTabItem("Backup", Icons.Default.CloudSync)
    )

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, tabs.size - 1)) { tabs.size }

    LaunchedEffect(initialTab) {
        if (initialTab in 0 until tabs.size && pagerState.currentPage != initialTab) {
            pagerState.animateScrollToPage(initialTab)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Sleek Minimal Iconized Tab Row with Swipe Sync
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = palette.surface,
            contentColor = IndigoPrimary,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = IndigoPrimary,
                    height = 3.dp
                )
            },
            divider = { HorizontalDivider(color = palette.border.copy(alpha = 0.5f)) }
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .testTag("settings_tab_${tab.title.lowercase()}"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = if (selected) IndigoPrimary else palette.textMuted
                            )
                            Text(
                                text = tab.title,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selected) (if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary) else palette.textMuted
                            )
                        }
                    }
                )
            }
        }

        // Swipeable Tab Content with HorizontalPager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> GeneralSettingsTab(
                    user = user,
                    progress = progress,
                    isDarkTheme = isDarkTheme,
                    isFlipAnimationEnabled = isFlipAnimationEnabled,
                    isFocusMode = isFocusMode,
                    isHapticEnabled = isHapticEnabled,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onToggleFlipAnimation = onToggleFlipAnimation,
                    onToggleFocusMode = onToggleFocusMode,
                    onToggleHaptic = onToggleHaptic,
                    onEditProfile = { showEditProfileDialog = true },
                    onUpdateProfile = onUpdateProfile,
                    onLogout = onLogout
                )
                1 -> CoursesSettingsTab(
                    courses = courses,
                    activeCourseId = activeCourseId,
                    selectedCourseIds = selectedCourseIds,
                    words = words,
                    questions = questions,
                    isSyncingDrive = isSyncingDrive,
                    driveSyncSummary = driveSyncSummary,
                    driveCourses = driveCourses,
                    isRefreshingDriveCourses = isRefreshingDriveCourses,
                    downloadingCourseFileIds = downloadingCourseFileIds,
                    onRefreshDriveCourses = onRefreshDriveCourses,
                    onDownloadDriveCourse = onDownloadDriveCourse,
                    onToggleCourseSelection = onToggleCourseSelection,
                    onSelectAllCourses = onSelectAllCourses,
                    onDeselectAllCourses = onDeselectAllCourses,
                    onSelectCourse = onSelectCourse,
                    onDeleteCourse = onDeleteCourse,
                    onCreateCourseClick = { showCreateCourseDialog = true },
                    onEditCourseClick = { courseBeingEdited = it },
                    onBatchImportClick = { batchFilePicker.launch("*/*") },
                    onDriveSyncClick = { showDriveSyncDialog = true },
                    onClearDriveSummary = onClearDriveSummary,
                    onAddWordClick = { showAddWordDialog = true },
                    onEditWordClick = { wordBeingEdited = it },
                    onDeleteWord = onDeleteWord,
                    onDeleteWords = onDeleteWords,
                    onUpdateGroupForWords = onUpdateGroupForWords,
                    onMoveWordsToCourse = onMoveWordsToCourse,
                    onUpdateStatusForWords = onUpdateStatusForWords,
                    onReportWord = onReportWord,
                    onDeleteQuestion = onDeleteQuestion,
                    onClearAllQB = onClearAllQB,
                    onImportQBClick = { showQbSyncDialog = true },
                    onImportQBFileClick = { qbFilePicker.launch("*/*") }
                )
                2 -> WidgetAndRemindersTab(
                    courses = courses,
                    words = words,
                    selectedCourseIds = selectedCourseIds,
                    onToggleCourseSelection = onToggleCourseSelection,
                    onSelectAllCourses = onSelectAllCourses,
                    onDeselectAllCourses = onDeselectAllCourses,
                    onRefreshWidget = onRefreshWidget
                )
                3 -> BackupSettingsTab(
                    progress = progress,
                    backupDirectoryPath = backupDirectoryPath,
                    customBackupTreeUri = customBackupTreeUri,
                    courses = courses,
                    words = words,
                    onSelectFolder = { folderPickerLauncher.launch(null) },
                    onManualBackup = onManualBackup,
                    onBackupToDriveDirect = onBackupToDriveDirect,
                    onRestoreFromDriveDirect = onRestoreFromDriveDirect,
                    onExportFile = { driveExportLauncher.launch("memorizer_backup_${System.currentTimeMillis()}.json") },
                    onRestoreFile = { driveRestoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    onCloudSync = onCloudSync,
                    onDirectPasteRestore = { showRestoreDialog = true },
                    onResetData = { showResetConfirmDialog = true },
                    onImportQBClick = { showQbSyncDialog = true },
                    onExportQBProgress = { qbProgressExportLauncher.launch("qb_progress_${System.currentTimeMillis()}.json") },
                    onRestoreQBProgress = { qbProgressRestoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                )
            }
        }
    }

    // Dialogs
    if (showEditProfileDialog && user != null) {
        EditProfileModal(
            currentName = user.displayName,
            currentAvatar = user.avatarUri,
            currentTargetExam = user.targetExam,
            currentGoal = user.dailyWordGoal,
            currentBio = user.bio,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, avatar, exam, goal, bio ->
                onUpdateProfile(name, avatar, exam, goal, bio)
                showEditProfileDialog = false
            }
        )
    }

    if (showCreateCourseDialog) {
        CompactCreateCourseModal(
            onDismiss = { showCreateCourseDialog = false },
            onCreate = { title, desc, fileContent, isJson ->
                onCreateCourse(title, desc, fileContent, isJson)
                showCreateCourseDialog = false
            }
        )
    }

    if (courseBeingEdited != null) {
        CompactEditCourseModal(
            course = courseBeingEdited!!,
            onDismiss = { courseBeingEdited = null },
            onSave = { id, title, desc ->
                onUpdateCourse(id, title, desc)
                courseBeingEdited = null
            }
        )
    }

    if (showAddWordDialog) {
        CompactWordModal(
            initialWord = null,
            activeCourseId = activeCourseId,
            courses = courses,
            onDismiss = { showAddWordDialog = false },
            onSave = { word ->
                onAddWord(word)
                showAddWordDialog = false
            }
        )
    }

    if (wordBeingEdited != null) {
        CompactWordModal(
            initialWord = wordBeingEdited,
            activeCourseId = activeCourseId,
            courses = courses,
            onDismiss = { wordBeingEdited = null },
            onSave = { word ->
                onUpdateWord(word)
                wordBeingEdited = null
            }
        )
    }

    if (showDriveSyncDialog) {
        CompactDriveSyncModal(
            initialUrl = driveSyncUrl,
            isSyncing = isSyncingDrive,
            onDismiss = { showDriveSyncDialog = false },
            onSync = { url, preserve ->
                onSyncFromDrive(url, preserve)
                showDriveSyncDialog = false
            }
        )
    }

    if (showQbSyncDialog) {
        CompactQbSyncModal(
            initialUrl = qbSyncUrl,
            isSyncing = isSyncingQB,
            onDismiss = { showQbSyncDialog = false },
            onSync = { url, clearExisting ->
                onImportQBFromUrl(url, clearExisting)
                showQbSyncDialog = false
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Paste JSON Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = restoreText,
                    onValueChange = { restoreText = it },
                    placeholder = { Text("Paste JSON string here...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreText.isNotBlank()) {
                            onRestoreBackup(restoreText, true)
                            showRestoreDialog = false
                            restoreText = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Reset to Sample Data", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE11D48)) },
            text = { Text("Reset all courses, words, and games back to default sample content? Custom progress will be replaced.", fontSize = 12.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "Data reset to sample", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ----------------------------------------------------
// TAB 0: General & Profile
// ----------------------------------------------------
@Composable
private fun GeneralSettingsTab(
    user: UserSession?,
    progress: UserProgressEntity?,
    isDarkTheme: Boolean,
    isFlipAnimationEnabled: Boolean,
    isFocusMode: Boolean,
    isHapticEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleFlipAnimation: (Boolean) -> Unit,
    onToggleFocusMode: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onUpdateProfile: (String, String?, String, Int, String) -> Unit,
    onLogout: () -> Unit
) {
    val palette = LocalAppPalette.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onUpdateProfile(
                user?.displayName ?: "User #1235",
                uri.toString(),
                user?.targetExam ?: "GRE / IELTS",
                user?.dailyWordGoal ?: 20,
                user?.bio ?: ""
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Centered Beautiful Minimal Profile Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("centered_profile_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Centered Profile Avatar with Photo Picker overlay
                    Box(
                        modifier = Modifier.size(94.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            shape = CircleShape,
                            color = if (palette.isDark) Color(0xFF1E293B) else IndigoLight,
                            border = BorderStroke(2.dp, IndigoPrimary)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!user?.avatarUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.avatarUri,
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = (user?.displayName?.take(1) ?: "U").uppercase(),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 32.sp,
                                        color = IndigoPrimary
                                    )
                                }
                            }
                        }

                        // Floating Camera Badge Button
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            shape = CircleShape,
                            color = IndigoPrimary,
                            border = BorderStroke(2.dp, palette.surface),
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Add or change profile picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Display Name
                    Text(
                        text = user?.displayName ?: "Learner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Centered Action Buttons: Add/Change Photo & Edit Profile
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (user?.avatarUri.isNullOrBlank()) Icons.Default.AddPhotoAlternate else Icons.Default.FlipCameraIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = IndigoPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (user?.avatarUri.isNullOrBlank()) "Add Photo" else "Change Photo",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }

                        Button(
                            onClick = onEditProfile,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Profile", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Quick Stats Badges
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    icon = Icons.Default.LocalFireDepartment,
                    title = "Streak",
                    value = "${progress?.streakDays ?: 1}d",
                    tint = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.CheckCircle,
                    title = "Mastered",
                    value = "${progress?.knowCount ?: 0}",
                    tint = EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.MilitaryTech,
                    title = "Score",
                    value = "${progress?.quizTotalScore ?: 0}",
                    tint = IndigoPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Appearance & Behavior Settings
        item {
            Text(
                text = "Preferences",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = palette.textPrimary,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingSwitchRow(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Theme",
                        checked = isDarkTheme,
                        onCheckedChange = onToggleDarkTheme
                    )
                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        icon = Icons.Default.FlipCameraAndroid,
                        title = "Card Flip Animation",
                        checked = isFlipAnimationEnabled,
                        onCheckedChange = onToggleFlipAnimation
                    )
                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Feedback",
                        checked = isHapticEnabled,
                        onCheckedChange = onToggleHaptic
                    )
                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        icon = Icons.Default.VisibilityOff,
                        title = "Focus Mode",
                        checked = isFocusMode,
                        onCheckedChange = onToggleFocusMode
                    )
                }
            }
        }

        // Logout
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE11D48)),
                border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// ----------------------------------------------------
// Swipeable Course Card (Right to Edit, Left to Delete)
// ----------------------------------------------------
@Composable
private fun SwipeableCourseCard(
    course: CourseEntity,
    isSelected: Boolean,
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
            border = BorderStroke(1.dp, palette.border),
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
                        modifier = Modifier.padding(top = 2.dp)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onManageWords,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = IndigoLight,
                            contentColor = IndigoPrimary
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Words", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveCourseCard(
    item: DiscoveredDriveFile,
    cleanTitle: String,
    extension: String,
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
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // File Type Avatar / Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isAlreadyAdded) EmeraldSuccess.copy(alpha = 0.12f)
                            else IndigoPrimary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAlreadyAdded) Icons.Default.School else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (isAlreadyAdded) EmeraldSuccess else IndigoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = cleanTitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.cardBackground,
                            border = BorderStroke(0.5.dp, palette.border)
                        ) {
                            Text(
                                text = extension,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (isAlreadyAdded && wordCount != null && wordCount > 0) {
                            Text(
                                text = "$wordCount words in course",
                                fontSize = 11.sp,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = item.title,
                                fontSize = 10.5.sp,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: Downloading state vs Added state vs Add to list
            if (isDownloading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IndigoPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = IndigoPrimary
                    )
                    Text(
                        text = "Adding...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndigoPrimary
                    )
                }
            } else if (isAlreadyAdded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldSuccess.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Added",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccess
                            )
                        }
                    }
                    IconButton(
                        onClick = onAddToList,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.cardBackground)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Re-download / update",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAddToList,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Add to list",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: Courses & Content Management
// ----------------------------------------------------
@Composable
private fun CoursesSettingsTab(
    courses: List<CourseEntity>,
    activeCourseId: String,
    selectedCourseIds: Set<String>,
    words: List<VocabularyWordEntity>,
    questions: List<QuestionBankEntity>,
    isSyncingDrive: Boolean,
    driveSyncSummary: DriveSyncSummary?,
    driveCourses: List<DiscoveredDriveFile> = emptyList(),
    isRefreshingDriveCourses: Boolean = false,
    downloadingCourseFileIds: Set<String> = emptySet(),
    onRefreshDriveCourses: () -> Unit = {},
    onDownloadDriveCourse: (DiscoveredDriveFile) -> Unit = {},
    onToggleCourseSelection: (String) -> Unit,
    onSelectAllCourses: () -> Unit,
    onDeselectAllCourses: () -> Unit,
    onSelectCourse: (String) -> Unit,
    onDeleteCourse: (String, Boolean) -> Unit,
    onCreateCourseClick: () -> Unit,
    onEditCourseClick: (CourseEntity) -> Unit,
    onBatchImportClick: () -> Unit,
    onDriveSyncClick: () -> Unit,
    onClearDriveSummary: () -> Unit,
    onAddWordClick: () -> Unit,
    onEditWordClick: (VocabularyWordEntity) -> Unit,
    onDeleteWord: (String) -> Unit,
    onDeleteWords: (List<String>) -> Unit,
    onUpdateGroupForWords: (List<String>, String) -> Unit,
    onMoveWordsToCourse: (List<String>, String) -> Unit,
    onUpdateStatusForWords: (List<String>, String) -> Unit,
    onReportWord: (String, Boolean, String?) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onClearAllQB: () -> Unit,
    onImportQBClick: () -> Unit,
    onImportQBFileClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    var contentSubView by remember { mutableStateOf("courses") } // "courses", "words", "qb"
    var wordSearchQuery by remember { mutableStateOf("") }
    var qbSearchQuery by remember { mutableStateOf("") }
    var showClearAllQBConfirm by remember { mutableStateOf(false) }
    var selectedCourseFilterForWords by remember(activeCourseId, courses) {
        mutableStateOf(if (activeCourseId.isNotBlank() && courses.any { it.id == activeCourseId }) activeCourseId else "all")
    }
    var selectedGroupFilterForWords by remember { mutableStateOf("all") }
    var selectedStatusFilters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedWordIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkEditDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    var courseToDelete by remember { mutableStateOf<CourseEntity?>(null) }
    var courseDropdownExpanded by remember { mutableStateOf(false) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    val wordsInCourse = remember(words, selectedCourseFilterForWords) {
        if (selectedCourseFilterForWords == "all") words
        else words.filter { it.courseId == selectedCourseFilterForWords }
    }

    val availableGroups = remember(wordsInCourse) {
        wordsInCourse.mapNotNull { it.group.takeIf { g -> g.isNotBlank() } }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    }

    LaunchedEffect(availableGroups, selectedGroupFilterForWords) {
        if (selectedGroupFilterForWords != "all" && selectedGroupFilterForWords !in availableGroups) {
            selectedGroupFilterForWords = "all"
        }
    }

    val filteredWords = remember(wordsInCourse, selectedGroupFilterForWords, wordSearchQuery, selectedStatusFilters) {
        wordsInCourse.filter { word ->
            val groupMatch = selectedGroupFilterForWords == "all" || word.group == selectedGroupFilterForWords
            val textMatch = wordSearchQuery.isBlank() ||
                word.word.contains(wordSearchQuery, ignoreCase = true) ||
                word.meaning.contains(wordSearchQuery, ignoreCase = true) ||
                word.group.contains(wordSearchQuery, ignoreCase = true) ||
                (word.synonyms?.contains(wordSearchQuery, ignoreCase = true) == true)
            val statusMatch = selectedStatusFilters.isEmpty() || selectedStatusFilters.any { filter ->
                when (filter) {
                    "flagged" -> word.isReported
                    else -> word.status.equals(filter, ignoreCase = true)
                }
            }
            groupMatch && textMatch && statusMatch
        }
    }

    if (showClearAllQBConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllQBConfirm = false },
            title = { Text("Clear All Questions?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Are you sure you want to delete all ${questions.size} questions from Question Bank? This cannot be undone.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllQBConfirm = false
                        onClearAllQB()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear All", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllQBConfirm = false }) { Text("Cancel", fontSize = 12.sp) }
            }
        )
    }

    // Course Delete Confirmation Dialog
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
                        "Delete \"${course.title}\"?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        "Keep your learning progress (ratings & review history) for future re-imports?",
                        fontSize = 12.sp,
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

    // Bulk Edit Dialog
    if (showBulkEditDialog && selectedWordIds.isNotEmpty()) {
        CompactBulkEditModal(
            selectedCount = selectedWordIds.size,
            courses = courses,
            availableGroups = availableGroups,
            onDismiss = { showBulkEditDialog = false },
            onApply = { newGroup, newCourseId, newStatus ->
                val idsList = selectedWordIds.toList()
                if (!newGroup.isNullOrBlank()) {
                    onUpdateGroupForWords(idsList, newGroup)
                }
                if (!newCourseId.isNullOrBlank()) {
                    onMoveWordsToCourse(idsList, newCourseId)
                }
                if (!newStatus.isNullOrBlank()) {
                    onUpdateStatusForWords(idsList, newStatus)
                }
                selectedWordIds = emptySet()
                showBulkEditDialog = false
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirm && selectedWordIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = {
                Text("Delete ${selectedWordIds.size} Words?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete these ${selectedWordIds.size} selected words from the course? This action cannot be undone.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsList = selectedWordIds.toList()
                        showBulkDeleteConfirm = false
                        selectedWordIds = emptySet()
                        onDeleteWords(idsList)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete (${selectedWordIds.size})", fontSize = 12.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Delete Group Confirmation Dialog
    if (showDeleteGroupConfirm && selectedGroupFilterForWords != "all") {
        val wordsInThisGroup = filteredWords.filter { it.group == selectedGroupFilterForWords }
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = false },
            title = {
                Text("Delete Group $selectedGroupFilterForWords?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete all ${wordsInThisGroup.size} words in Group $selectedGroupFilterForWords? This will permanently remove the entire group.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsList = wordsInThisGroup.map { it.id }
                        showDeleteGroupConfirm = false
                        selectedWordIds = selectedWordIds - idsList.toSet()
                        selectedGroupFilterForWords = "all"
                        onDeleteWords(idsList)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete All ${wordsInThisGroup.size} Words", fontSize = 12.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sub-View Pills (No bracketed numbers, Games section removed)
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubViewChip(
                    title = "Courses",
                    icon = Icons.Default.School,
                    selected = contentSubView == "courses",
                    onClick = { contentSubView = "courses" }
                )
                SubViewChip(
                    title = "Words",
                    icon = Icons.Default.Translate,
                    selected = contentSubView == "words",
                    onClick = { contentSubView = "words" }
                )
                SubViewChip(
                    title = "QB",
                    icon = Icons.Default.HelpOutline,
                    selected = contentSubView == "qb",
                    onClick = { contentSubView = "qb" }
                )
            }
        }

        // Sub-View: COURSES
        if (contentSubView == "courses") {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Course Library",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )

                    // Plus icon removed per user instruction
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onDriveSyncClick,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(palette.surface)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Drive Sync", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onBatchImportClick,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(palette.surface)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Batch Files", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Sync Summary Notification Banner
            if (driveSyncSummary != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (palette.isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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

            if (courses.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(28.dp))
                            Text("No Courses in Library", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = palette.textPrimary)
                            Text("Choose any course from the Google Drive list below and click 'Add to list' to download.", fontSize = 11.5.sp, color = palette.textSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Swipe right to edit • Swipe left to delete",
                        fontSize = 11.sp,
                        color = palette.textMuted,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // Courses List using Swipeable cards
                items(courses.distinctBy { it.id }, key = { "inst_course_${it.id}" }) { course ->
                    val courseWordCount = words.count { it.courseId == course.id }
                    val courseFlaggedCount = words.count { it.courseId == course.id && it.isReported }

                    SwipeableCourseCard(
                        course = course,
                        isSelected = false,
                        wordCount = courseWordCount,
                        flaggedCount = courseFlaggedCount,
                        palette = palette,
                        onClick = {
                            onSelectCourse(course.id)
                        },
                        onEdit = { onEditCourseClick(course) },
                        onDelete = { courseToDelete = course },
                        onManageWords = {
                            selectedCourseFilterForWords = course.id
                            selectedGroupFilterForWords = "all"
                            selectedWordIds = emptySet()
                            contentSubView = "words"
                        }
                    )
                }
            }

            // Google Drive Courses Catalog Header
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Google Drive Courses",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary
                            )
                        }
                        Text(
                            text = "${driveCourses.size} files in Drive • Click 'Add to list' to download",
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onRefreshDriveCourses,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(palette.surface)
                        ) {
                            if (isRefreshingDriveCourses) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IndigoPrimary)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Drive Courses", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                            }
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
                val ext = item.title.substringAfterLast(".", "XLSX").uppercase()

                DriveCourseCard(
                    item = item,
                    cleanTitle = cleanTitle,
                    extension = ext,
                    isAlreadyAdded = isAlreadyAdded,
                    isDownloading = isDownloading,
                    wordCount = wordCountForCourse,
                    palette = palette,
                    onAddToList = { onDownloadDriveCourse(item) }
                )
            }
        }

        // Sub-View: WORDS
        if (contentSubView == "words") {
            // Header: Title & Add Word Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Course Vocabulary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                        Text(
                            text = "${filteredWords.size} words listed" + if (selectedGroupFilterForWords != "all") " (Group $selectedGroupFilterForWords)" else "",
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                    }
                    Button(
                        onClick = onAddWordClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Word", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Dropdown Filter Row (Course Dropdown & Group Dropdown)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Course Filter Dropdown
                    val currentCourse = courses.firstOrNull { it.id == selectedCourseFilterForWords }
                    val courseTitle = if (selectedCourseFilterForWords == "all") "All Courses" else (currentCourse?.title ?: "Select Course")
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { courseDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = palette.surface,
                            border = BorderStroke(1.dp, if (selectedCourseFilterForWords != "all") IndigoPrimary else palette.border),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (selectedCourseFilterForWords != "all") IndigoPrimary else palette.textMuted
                                    )
                                    Text(
                                        text = courseTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selectedCourseFilterForWords != "all") FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedCourseFilterForWords != "all") IndigoPrimary else palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = courseDropdownExpanded,
                            onDismissRequest = { courseDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (selectedCourseFilterForWords == "all") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                        }
                                        Text("All Courses (${words.size})", fontSize = 12.sp, fontWeight = if (selectedCourseFilterForWords == "all") FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = {
                                    selectedCourseFilterForWords = "all"
                                    selectedWordIds = emptySet()
                                    courseDropdownExpanded = false
                                }
                            )
                            HorizontalDivider(color = palette.border)
                            courses.forEach { course ->
                                val count = words.count { it.courseId == course.id }
                                val isSelected = selectedCourseFilterForWords == course.id
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                            }
                                            Text("${course.title} ($count)", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    },
                                    onClick = {
                                        selectedCourseFilterForWords = course.id
                                        selectedWordIds = emptySet()
                                        courseDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. Group Filter Dropdown
                    val groupTitle = if (selectedGroupFilterForWords == "all") "All Groups" else "Group $selectedGroupFilterForWords"
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { groupDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = palette.surface,
                            border = BorderStroke(1.dp, if (selectedGroupFilterForWords != "all") IndigoPrimary else palette.border),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (selectedGroupFilterForWords != "all") IndigoPrimary else palette.textMuted
                                    )
                                    Text(
                                        text = groupTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selectedGroupFilterForWords != "all") FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedGroupFilterForWords != "all") IndigoPrimary else palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = groupDropdownExpanded,
                            onDismissRequest = { groupDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (selectedGroupFilterForWords == "all") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                        }
                                        Text("All Groups (${wordsInCourse.size})", fontSize = 12.sp, fontWeight = if (selectedGroupFilterForWords == "all") FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = {
                                    selectedGroupFilterForWords = "all"
                                    selectedWordIds = emptySet()
                                    groupDropdownExpanded = false
                                }
                            )
                            HorizontalDivider(color = palette.border)
                            availableGroups.forEach { grp ->
                                val count = wordsInCourse.count { it.group == grp }
                                val isSelected = selectedGroupFilterForWords == grp
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                            }
                                            Text("Group $grp ($count words)", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    },
                                    onClick = {
                                        selectedGroupFilterForWords = grp
                                        selectedWordIds = emptySet()
                                        groupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Group Filter Horizontal Chips
            if (availableGroups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedGroupFilterForWords == "all",
                            onClick = {
                                selectedGroupFilterForWords = "all"
                                selectedWordIds = emptySet()
                            },
                            label = { Text("All (${wordsInCourse.size})", fontSize = 10.5.sp) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                        availableGroups.forEach { grp ->
                            val count = wordsInCourse.count { it.group == grp }
                            val isSelected = selectedGroupFilterForWords == grp
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedGroupFilterForWords = if (isSelected) "all" else grp
                                    selectedWordIds = emptySet()
                                },
                                label = { Text("Group $grp ($count)", fontSize = 10.5.sp) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // Search query
            item {
                OutlinedTextField(
                    value = wordSearchQuery,
                    onValueChange = { wordSearchQuery = it },
                    placeholder = { Text("Search word, meaning, or group...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (wordSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { wordSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            // Word Status Filter with Icons Only
            item {
                val wordFilterOptions = listOf(
                    Triple("all", "All", Icons.Default.DoneAll),
                    Triple("know", "Know", Icons.Default.CheckCircle),
                    Triple("confusion", "Confusion", Icons.Default.Psychology),
                    Triple("dont_know", "Don't Know", Icons.Default.Cancel),
                    Triple("unrated", "Unrated", Icons.Default.HelpOutline),
                    Triple("flagged", "Flagged", Icons.Default.Flag)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wordFilterOptions.forEach { (key, label, icon) ->
                        val isSelected = if (key == "all") selectedStatusFilters.isEmpty() else key in selectedStatusFilters
                        val chipColor = when (key) {
                            "know" -> EmeraldSuccess
                            "confusion" -> AmberWarning
                            "dont_know" -> Color(0xFFE11D48)
                            "unrated" -> Color(0xFF64748B)
                            "flagged" -> Color(0xFFE11D48)
                            else -> IndigoPrimary
                        }
                        Surface(
                            onClick = {
                                selectedStatusFilters = if (key == "all") {
                                    emptySet()
                                } else {
                                    if (key in selectedStatusFilters) selectedStatusFilters - key else selectedStatusFilters + key
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) chipColor else palette.surface,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) chipColor else palette.border
                            ),
                            modifier = Modifier.size(width = 46.dp, height = 36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(19.dp),
                                    tint = if (isSelected) Color.White else chipColor
                                )
                            }
                        }
                    }
                }
            }

            // Bulk Action Toolbar Bar
            item {
                val allFilteredIds = remember(filteredWords) { filteredWords.map { it.id }.toSet() }
                val areAllSelected = filteredWords.isNotEmpty() && allFilteredIds.all { it in selectedWordIds }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedWordIds.isNotEmpty()) IndigoPrimary.copy(alpha = 0.08f) else palette.surface,
                    border = BorderStroke(1.dp, if (selectedWordIds.isNotEmpty()) IndigoPrimary.copy(alpha = 0.5f) else palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                selectedWordIds = if (areAllSelected) {
                                    selectedWordIds - allFilteredIds
                                } else {
                                    selectedWordIds + allFilteredIds
                                }
                            }
                        ) {
                            Checkbox(
                                checked = areAllSelected,
                                onCheckedChange = { checked ->
                                    selectedWordIds = if (checked) selectedWordIds + allFilteredIds else selectedWordIds - allFilteredIds
                                },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = IndigoPrimary,
                                    uncheckedColor = palette.textMuted
                                )
                            )
                            Text(
                                text = if (selectedWordIds.isEmpty()) "Select All (${filteredWords.size})" else "${selectedWordIds.size} Selected",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedWordIds.isNotEmpty()) IndigoPrimary else palette.textPrimary
                            )
                        }

                        if (selectedWordIds.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showBulkEditDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit (${selectedWordIds.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { showBulkDeleteConfirm = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete (${selectedWordIds.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                IconButton(
                                    onClick = { selectedWordIds = emptySet() },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = palette.textMuted, modifier = Modifier.size(15.dp))
                                }
                            }
                        } else if (selectedGroupFilterForWords != "all" && filteredWords.isNotEmpty()) {
                            TextButton(
                                onClick = { showDeleteGroupConfirm = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete Group $selectedGroupFilterForWords", color = Color(0xFFE11D48), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Word Cards List
            if (filteredWords.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LayersClear, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(36.dp))
                            Text("No words match this filter", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                            Text("Try changing the group filter or search query.", fontSize = 11.sp, color = palette.textMuted)
                        }
                    }
                }
            } else {
                items(filteredWords, key = { it.id }) { word ->
                    val isSelected = word.id in selectedWordIds
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) IndigoPrimary.copy(alpha = 0.06f)
                            else if (word.isReported) (if (palette.isDark) Color(0xFF451A1A) else Color(0xFFFFF1F2))
                            else palette.surface
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else (if (word.isReported) 1.5.dp else 1.dp),
                            if (isSelected) IndigoPrimary else (if (word.isReported) Color(0xFFF43F5E) else palette.border)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Selection Checkbox
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedWordIds = if (checked) selectedWordIds + word.id else selectedWordIds - word.id
                                },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = IndigoPrimary,
                                    uncheckedColor = palette.textMuted
                                )
                            )

                            // Word Information
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(word.word, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = palette.textPrimary)

                                    // Group Pill (clickable to filter by this group)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = IndigoLight,
                                        modifier = Modifier.clickable {
                                            selectedGroupFilterForWords = word.group
                                        }
                                    ) {
                                        Text(
                                            text = "G${word.group}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IndigoPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    // Status Badge
                                    val statusColor = when (word.status) {
                                        "know" -> EmeraldSuccess
                                        "confusion" -> AmberWarning
                                        "dont_know" -> Color(0xFFE11D48)
                                        else -> Color(0xFF64748B)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = statusColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = word.status.replace("_", " ").uppercase(),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    if (word.isReported) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFEE2E2),
                                            border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(Icons.Default.Flag, contentDescription = "Flagged", tint = Color(0xFFE11D48), modifier = Modifier.size(10.dp))
                                                Text("Flagged", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                                            }
                                        }
                                    }
                                }

                                Text(word.meaning, fontSize = 11.5.sp, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                if (!word.example.isNullOrBlank()) {
                                    Text(
                                        word.example,
                                        fontSize = 10.sp,
                                        color = palette.textMuted.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (word.isReported && !word.reportReason.isNullOrBlank()) {
                                    Text(
                                        "Reason: ${word.reportReason}",
                                        fontSize = 10.sp,
                                        color = Color(0xFFE11D48),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Actions
                            IconButton(
                                onClick = {
                                    onReportWord(word.id, !word.isReported, if (!word.isReported) "User flagged in list" else null)
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (word.isReported) Icons.Default.Flag else Icons.Default.OutlinedFlag,
                                    contentDescription = "Toggle Flag",
                                    tint = if (word.isReported) Color(0xFFE11D48) else palette.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            IconButton(onClick = { onEditWordClick(word) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = palette.textMuted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { onDeleteWord(word.id) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFE11D48), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Sub-View: QUESTION BANK (QB)
        if (contentSubView == "qb") {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Question Bank", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                        Text(
                            text = "${questions.size} questions available",
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Link Import Button
                        Button(
                            onClick = onImportQBClick,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Add Link", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Link", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // File Import Button
                        IconButton(
                            onClick = onImportQBFileClick,
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.surface)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import File", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                        }

                        if (questions.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearAllQBConfirm = true },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.surface)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFE11D48), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (questions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(36.dp))
                            Text(
                                "Question Bank is Empty",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                "Google Drive, Google Sheets, বা সরাসরি CSV/JSON লিঙ্ক এড করে কুইজ ও গেমসের প্রশ্ন ইম্পোর্ট করুন।",
                                fontSize = 12.sp,
                                color = palette.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onImportQBClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add QB Link", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onImportQBFileClick,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("From File", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Search box
                item {
                    OutlinedTextField(
                        value = qbSearchQuery,
                        onValueChange = { qbSearchQuery = it },
                        placeholder = { Text("Search question or answer...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (qbSearchQuery.isNotBlank()) {
                                IconButton(onClick = { qbSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }

                val filtered = if (qbSearchQuery.isBlank()) questions else questions.filter {
                    it.question.contains(qbSearchQuery, ignoreCase = true) ||
                            it.answer.contains(qbSearchQuery, ignoreCase = true) ||
                            (!it.explanation.isNullOrBlank() && it.explanation.contains(qbSearchQuery, ignoreCase = true)) ||
                            (!it.filter1.isNullOrBlank() && it.filter1.contains(qbSearchQuery, ignoreCase = true))
                }

                items(filtered.take(60), key = { it.id }) { q ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(q.question, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ans: ${q.answer}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EmeraldSuccess)
                                    if (!q.filter1.isNullOrBlank()) {
                                        Text("• ${q.filter1}", fontSize = 10.sp, color = palette.textMuted)
                                    }
                                }
                                if (!q.explanation.isNullOrBlank()) {
                                    Text(q.explanation, fontSize = 10.sp, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(onClick = { onDeleteQuestion(q.id) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFE11D48), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: Widget & Study Reminders (Unified)
// ----------------------------------------------------
@Composable
private fun WidgetAndRemindersTab(
    courses: List<CourseEntity>,
    words: List<VocabularyWordEntity>,
    selectedCourseIds: Set<String>,
    onToggleCourseSelection: (String) -> Unit,
    onSelectAllCourses: () -> Unit,
    onDeselectAllCourses: () -> Unit,
    onRefreshWidget: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val widgetPrefs = remember { context.getSharedPreferences(DailyVocabWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE) }

    // --- Notification Settings State ---
    val initialNotifSettings = remember { NotificationHelper.getNotificationSettings(context) }
    var isNotifEnabled by remember { mutableStateOf(initialNotifSettings.enabled) }
    var notifFrequency by remember { mutableStateOf(initialNotifSettings.frequency) }
    var showWordMeaning by remember { mutableStateOf(initialNotifSettings.showWordMeaning) }
    var notifHour by remember { mutableStateOf(initialNotifSettings.hour) }
    var notifMinute by remember { mutableStateOf(initialNotifSettings.minute) }

    // Permission launcher for Android 13+ (POST_NOTIFICATIONS)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isNotifEnabled = true
            NotificationHelper.scheduleReminder(
                context = context,
                enabled = true,
                frequency = notifFrequency,
                hour = notifHour,
                minute = notifMinute,
                showWordMeaning = showWordMeaning
            )
            Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            isNotifEnabled = false
            NotificationHelper.scheduleReminder(
                context = context,
                enabled = false,
                frequency = notifFrequency,
                hour = notifHour,
                minute = notifMinute,
                showWordMeaning = showWordMeaning
            )
            Toast.makeText(context, "Notification permission required for alerts", Toast.LENGTH_LONG).show()
        }
    }

    fun persistReminder(
        newEnabled: Boolean = isNotifEnabled,
        newFreq: String = notifFrequency,
        newShowWord: Boolean = showWordMeaning,
        newH: Int = notifHour,
        newM: Int = notifMinute
    ) {
        NotificationHelper.scheduleReminder(
            context = context,
            enabled = newEnabled,
            frequency = newFreq,
            hour = newH,
            minute = newM,
            showWordMeaning = newShowWord
        )
    }

    // --- Target Courses Selection State (Fully Synced with App & Widget) ---
    val isAllCourses = courses.isNotEmpty() && selectedCourseIds.size >= courses.size

    // --- Shared Multi-Select Status Filter State ---
    var selectedStatusFilters by remember {
        mutableStateOf(DailyVocabWidgetProvider.getSelectedTagFilters(context))
    }

    // --- Widget Appearance State ---
    var selectedFontSize by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_FONT_SIZE, "medium") ?: "medium")
    }
    var selectedWidgetSize by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_SIZE, "standard") ?: "standard")
    }
    var rotate10s by remember {
        mutableStateOf(widgetPrefs.getBoolean(DailyVocabWidgetProvider.KEY_ROTATE_10S, true))
    }

    fun persistAllSettings(
        newStatusFilters: Set<String> = selectedStatusFilters,
        newFontSize: String = selectedFontSize,
        newWidgetSize: String = selectedWidgetSize,
        newRotate10s: Boolean = rotate10s
    ) {
        DailyVocabWidgetProvider.saveSelectedTagFilters(context, newStatusFilters)
        widgetPrefs.edit()
            .putString(DailyVocabWidgetProvider.KEY_WIDGET_FONT_SIZE, newFontSize)
            .putString(DailyVocabWidgetProvider.KEY_WIDGET_SIZE, newWidgetSize)
            .putBoolean(DailyVocabWidgetProvider.KEY_ROTATE_10S, newRotate10s)
            .apply()
        DailyVocabWidgetProvider.updateAllWidgets(context)
        onRefreshWidget()
    }

    val eligibleWords = remember(words, selectedCourseIds, isAllCourses, selectedStatusFilters) {
        val courseFiltered = if (selectedCourseIds.isEmpty() || isAllCourses) {
            words
        } else {
            words.filter { it.courseId in selectedCourseIds }
        }
        if (selectedStatusFilters.isEmpty()) {
            courseFiltered
        } else {
            courseFiltered.filter { word ->
                selectedStatusFilters.any { filter ->
                    if (filter == "flagged") word.isReported
                    else word.status.equals(filter, ignoreCase = true)
                }
            }
        }
    }

    val frequencies = listOf(
        Pair("10m", "10 min"),
        Pair("15m", "15 min"),
        Pair("20m", "20 min"),
        Pair("30m", "30 min"),
        Pair("45m", "45 min"),
        Pair("1h", "1 hour"),
        Pair("2h", "2 hours"),
        Pair("3h", "3 hours"),
        Pair("4h", "4 hours"),
        Pair("6h", "6 hours"),
        Pair("8h", "8 hours"),
        Pair("12h", "12 hours"),
        Pair("daily", "Daily")
    )

    val wordFilterOptions = listOf(
        Triple("all", "All", Icons.Default.DoneAll),
        Triple("know", "Know", Icons.Default.CheckCircle),
        Triple("confusion", "Confusion", Icons.Default.Psychology),
        Triple("dont_know", "Don't Know", Icons.Default.Cancel),
        Triple("unrated", "Unrated", Icons.Default.HelpOutline),
        Triple("flagged", "Flagged", Icons.Default.Flag)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Study Reminders Notification Switch Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Study Reminders", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = palette.textPrimary)
                            Text(
                                if (isNotifEnabled) "Active • ${eligibleWords.size} words in pool" else "Disabled",
                                fontSize = 11.sp,
                                color = if (isNotifEnabled) EmeraldSuccess else palette.textMuted
                            )
                        }

                        Switch(
                            checked = isNotifEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                            isNotifEnabled = true
                                            persistReminder(newEnabled = true)
                                        } else {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        isNotifEnabled = true
                                        persistReminder(newEnabled = true)
                                    }
                                } else {
                                    isNotifEnabled = false
                                    persistReminder(newEnabled = false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary
                            )
                        )
                    }

                    if (isNotifEnabled) {
                        HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                        // Frequency chips
                        Text("Frequency", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            frequencies.forEach { (key, label) ->
                                val isSelected = notifFrequency == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        notifFrequency = key
                                        persistReminder(newFreq = key)
                                    },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = palette.background,
                                        labelColor = palette.textPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        if (notifFrequency == "daily") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Alert Time", fontSize = 12.sp, color = palette.textPrimary)
                                FilledTonalButton(
                                    onClick = {
                                        notifHour = if (notifHour >= 23) 0 else notifHour + 1
                                        persistReminder(newH = notifHour)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(String.format(Locale.US, "%02d:%02d", notifHour, notifMinute), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Show Word & Meaning", fontSize = 12.sp, color = palette.textPrimary)
                            Switch(
                                checked = showWordMeaning,
                                onCheckedChange = {
                                    showWordMeaning = it
                                    persistReminder(newShowWord = it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IndigoPrimary
                                )
                            )
                        }

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationHelper.createNotificationChannel(context)
                                    NotificationHelper.triggerInstantSampleNotification(context)
                                    Toast.makeText(context, "Sample notification sent! Check notification bar.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Send Test Notification Now", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Word Status Filter Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
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
                    Text(
                        text = "Word Status Filter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = palette.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        wordFilterOptions.forEach { (key, label, icon) ->
                            val isSelected = if (key == "all") selectedStatusFilters.isEmpty() else key in selectedStatusFilters
                            val chipColor = when (key) {
                                "know" -> EmeraldSuccess
                                "confusion" -> AmberWarning
                                "dont_know" -> Color(0xFFE11D48)
                                "unrated" -> Color(0xFF64748B)
                                "flagged" -> Color(0xFFE11D48)
                                else -> IndigoPrimary
                            }
                            Surface(
                                onClick = {
                                    val newFilters = if (key == "all") {
                                        emptySet()
                                    } else {
                                        if (key in selectedStatusFilters) selectedStatusFilters - key else selectedStatusFilters + key
                                    }
                                    selectedStatusFilters = newFilters
                                    persistAllSettings(newStatusFilters = newFilters)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) chipColor else if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) chipColor else palette.border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(19.dp),
                                        tint = if (isSelected) Color.White else chipColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Target Courses Dropdown Option with Equal Size Grid
        item {
            var isTargetCoursesExpanded by remember { mutableStateOf(true) }

            Card(
                shape = RoundedCornerShape(14.dp),
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
                    // Dropdown Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTargetCoursesExpanded = !isTargetCoursesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Target Courses",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = palette.textPrimary
                            )
                            val selectedCount = selectedCourseIds.size
                            val summaryText = when {
                                courses.isEmpty() -> "No courses available"
                                selectedCount == 0 -> "None selected (All courses included)"
                                selectedCount >= courses.size -> "All ${courses.size} courses selected"
                                else -> "$selectedCount of ${courses.size} courses selected"
                            }
                            Text(
                                text = summaryText,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = if (selectedCount > 0) EmeraldSuccess else palette.textMuted
                            )
                        }

                        IconButton(
                            onClick = { isTargetCoursesExpanded = !isTargetCoursesExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isTargetCoursesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTargetCoursesExpanded) "Collapse" else "Expand",
                                tint = palette.textMuted
                            )
                        }
                    }

                    Text(
                        text = "Widget and study notifications will function only for the selected courses.",
                        fontSize = 11.sp,
                        color = palette.textMuted,
                        lineHeight = 15.sp
                    )

                    // Expandable Dropdown Grid
                    AnimatedVisibility(
                        visible = isTargetCoursesExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = palette.border.copy(alpha = 0.6f))

                            // Multi-Select Action Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select multiple courses:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.textMuted
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            onSelectAllCourses()
                                            val allIds = courses.map { it.id }.toSet()
                                            DailyVocabWidgetProvider.saveSelectedCourseIds(context, allIds)
                                            DailyVocabWidgetProvider.updateAllWidgets(context)
                                            onRefreshWidget()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                                    }
                                    Text("•", fontSize = 10.sp, color = palette.textMuted)
                                    TextButton(
                                        onClick = {
                                            onDeselectAllCourses()
                                            DailyVocabWidgetProvider.saveSelectedCourseIds(context, emptySet())
                                            DailyVocabWidgetProvider.updateAllWidgets(context)
                                            onRefreshWidget()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted)
                                    }
                                }
                            }

                            // Equal Size Grid for Multi-Selecting Courses
                            if (courses.isEmpty()) {
                                Text(
                                    text = "No courses available in Library",
                                    fontSize = 11.sp,
                                    color = palette.textMuted,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                val coursePairs = courses.chunked(2)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    coursePairs.forEach { rowCourses ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowCourses.forEach { course ->
                                                val isSelected = course.id in selectedCourseIds
                                                val courseWordsCount = words.count { it.courseId == course.id }

                                                Surface(
                                                    onClick = {
                                                        onToggleCourseSelection(course.id)
                                                        val newSet = if (isSelected) selectedCourseIds - course.id else selectedCourseIds + course.id
                                                        DailyVocabWidgetProvider.saveSelectedCourseIds(context, newSet)
                                                        DailyVocabWidgetProvider.updateAllWidgets(context)
                                                        onRefreshWidget()
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) {
                                                        if (palette.isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)
                                                    } else {
                                                        if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
                                                    },
                                                    border = BorderStroke(
                                                        if (isSelected) 1.5.dp else 1.dp,
                                                        if (isSelected) EmeraldSuccess else palette.border
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(72.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Text(
                                                                text = course.title,
                                                                fontFamily = PoppinsFontFamily,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 12.sp,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = if (isSelected) {
                                                                    if (palette.isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
                                                                } else {
                                                                    palette.textPrimary
                                                                }
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "$courseWordsCount words",
                                                                fontSize = 10.sp,
                                                                color = if (isSelected) {
                                                                    if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                                                                } else {
                                                                    palette.textMuted
                                                                }
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) EmeraldSuccess else palette.cardBorder
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isSelected) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Selected",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Maintain equal column width if row has 1 item
                                            if (rowCourses.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
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

        // Widget Appearance & Rotation Settings Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Widget Appearance", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)

                    // Font Size: Only +/- icons on the side
                    val fontSizes = listOf("small", "medium", "large")
                    val currentIdx = fontSizes.indexOf(selectedFontSize).coerceAtLeast(0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", fontSize = 12.sp, color = palette.textPrimary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentIdx > 0) {
                                        val newKey = fontSizes[currentIdx - 1]
                                        selectedFontSize = newKey
                                        persistAllSettings(newFontSize = newKey)
                                    }
                                },
                                enabled = currentIdx > 0,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = palette.textPrimary)
                            }

                            Text(
                                text = when (selectedFontSize) {
                                    "small" -> "Small"
                                    "large" -> "Large"
                                    else -> "Medium"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary,
                                modifier = Modifier.widthIn(min = 52.dp),
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = {
                                    if (currentIdx < fontSizes.size - 1) {
                                        val newKey = fontSizes[currentIdx + 1]
                                        selectedFontSize = newKey
                                        persistAllSettings(newFontSize = newKey)
                                    }
                                },
                                enabled = currentIdx < fontSizes.size - 1,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = palette.textPrimary)
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    // Widget Size Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Widget Layout", fontSize = 12.sp, color = palette.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("compact" to "Compact", "standard" to "Standard", "large" to "Large").forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedWidgetSize == key,
                                    onClick = {
                                        selectedWidgetSize = key
                                        persistAllSettings(newWidgetSize = key)
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    SettingSwitchRow(
                        icon = Icons.Default.RotateRight,
                        title = "Rotate Every 10 Seconds",
                        checked = rotate10s,
                        onCheckedChange = {
                            rotate10s = it
                            persistAllSettings(newRotate10s = it)
                        }
                    )
                }
            }
        }

        // Refresh Widget Button
        item {
            Button(
                onClick = {
                    persistAllSettings()
                    DailyVocabWidgetProvider.updateAllWidgets(context)
                    onRefreshWidget()
                    Toast.makeText(context, "Homescreen widget refreshed!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Homescreen Widget Now", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: Backup & Cloud Sync
// ----------------------------------------------------
@Composable
private fun BackupSettingsTab(
    progress: UserProgressEntity?,
    backupDirectoryPath: String,
    customBackupTreeUri: String?,
    courses: List<CourseEntity> = emptyList(),
    words: List<VocabularyWordEntity> = emptyList(),
    onSelectFolder: () -> Unit,
    onManualBackup: () -> Unit,
    onBackupToDriveDirect: () -> Unit,
    onRestoreFromDriveDirect: () -> Unit,
    onExportFile: () -> Unit,
    onRestoreFile: () -> Unit,
    onCloudSync: () -> Unit,
    onDirectPasteRestore: () -> Unit,
    onResetData: () -> Unit,
    onImportQBClick: () -> Unit = {},
    onExportQBProgress: () -> Unit = {},
    onRestoreQBProgress: () -> Unit = {}
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Filter states for Selective Export
    var exportSelectedCourseIds by remember(courses) {
        mutableStateOf(courses.map { it.id }.toSet())
    }
    var exportSelectedStatusTags by remember {
        mutableStateOf<Set<String>>(emptySet()) // empty = all
    }
    var isCoursesExpanded by remember { mutableStateOf(false) }

    // Live filtered words calculation
    val filteredWords = remember(words, exportSelectedCourseIds, exportSelectedStatusTags) {
        val courseFiltered = if (exportSelectedCourseIds.isEmpty()) {
            words
        } else {
            words.filter { it.courseId in exportSelectedCourseIds }
        }
        if (exportSelectedStatusTags.isEmpty()) {
            courseFiltered
        } else {
            courseFiltered.filter { word ->
                exportSelectedStatusTags.any { tag ->
                    if (tag.equals("flagged", ignoreCase = true)) word.isReported
                    else word.status.equals(tag, ignoreCase = true)
                }
            }
        }
    }

    var pendingExportFormat by remember { mutableStateOf<String?>(null) }

    val filteredDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/*")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = if (pendingExportFormat == "csv") {
                        generateFilteredCsv(filteredWords, courses)
                    } else {
                        generateFilteredJson(filteredWords, courses, exportSelectedStatusTags)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Filtered data exported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val lastBackupStr = if (progress != null && progress.lastBackupTimestamp > 0) {
        dateFormat.format(Date(progress.lastBackupTimestamp))
    } else {
        "Auto-saved recently"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Backup: Daily at 2:00 AM", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = palette.textPrimary)
                        Text("Last: $lastBackupStr", fontSize = 10.5.sp, color = palette.textMuted)
                    }
                    IconButton(
                        onClick = {
                            onManualBackup()
                            Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(IndigoLight)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Google Drive & Linked Folder
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive Sync", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = palette.textPrimary)
                            Text(
                                text = if (!customBackupTreeUri.isNullOrBlank()) "Linked folder active" else "App storage (Default)",
                                fontSize = 10.5.sp,
                                color = palette.textMuted
                            )
                        }
                        FilledTonalButton(
                            onClick = onSelectFolder,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Change Folder", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onBackupToDriveDirect()
                                Toast.makeText(context, "Saving to Drive...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Backup Drive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onRestoreFromDriveDirect,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Restore Drive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Selective Data Export (Word Status Tag & Courses)
        // ----------------------------------------------------
        item {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card Title Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Filtered Data Export",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Export by word status tag & course",
                                    fontSize = 10.5.sp,
                                    color = palette.textMuted
                                )
                            }
                        }

                        // Badge showing matched word count
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (filteredWords.isNotEmpty()) EmeraldSuccess.copy(alpha = 0.12f) else palette.border.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, if (filteredWords.isNotEmpty()) EmeraldSuccess.copy(alpha = 0.4f) else palette.border)
                        ) {
                            Text(
                                text = "${filteredWords.size} words",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filteredWords.isNotEmpty()) EmeraldSuccess else palette.textMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    // 1. Word Status Tag Multi-Select Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Word Status Tags",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = palette.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndigoPrimary,
                                    modifier = Modifier
                                        .clickable { exportSelectedStatusTags = emptySet() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = palette.textMuted
                                )
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE11D48),
                                    modifier = Modifier
                                        .clickable { exportSelectedStatusTags = setOf("none") }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // 6 Status Icons Row with multiple-select support (Exact match to image!)
                        val statusOptions = listOf(
                            Triple("all", "All", Icons.Default.DoneAll),
                            Triple("know", "Know", Icons.Default.CheckCircle),
                            Triple("confusion", "Confusion", Icons.Default.Psychology),
                            Triple("dont_know", "Don't Know", Icons.Default.Cancel),
                            Triple("unrated", "Unrated", Icons.Default.HelpOutline),
                            Triple("flagged", "Flagged", Icons.Default.Flag)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusOptions.forEach { (key, label, icon) ->
                                val isSelected = if (key == "all") exportSelectedStatusTags.isEmpty() else key in exportSelectedStatusTags
                                val chipColor = when (key) {
                                    "know" -> EmeraldSuccess
                                    "confusion" -> AmberWarning
                                    "dont_know" -> Color(0xFFE11D48)
                                    "unrated" -> Color(0xFF64748B)
                                    "flagged" -> Color(0xFFE11D48)
                                    else -> IndigoPrimary
                                }
                                Surface(
                                    onClick = {
                                        exportSelectedStatusTags = if (key == "all") {
                                            emptySet()
                                        } else {
                                            if (exportSelectedStatusTags.isEmpty()) {
                                                setOf(key)
                                            } else if (key in exportSelectedStatusTags) {
                                                val next = exportSelectedStatusTags - key
                                                if (next.isEmpty()) emptySet() else next
                                            } else {
                                                (exportSelectedStatusTags - "none") + key
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) chipColor else if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) chipColor else palette.border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(19.dp),
                                            tint = if (isSelected) Color.White else chipColor
                                        )
                                    }
                                }
                            }
                        }

                        // Status Tag Count Summary Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val counts = listOf(
                                Triple("Know", words.count { it.status.equals("know", ignoreCase = true) }, EmeraldSuccess),
                                Triple("Confusion", words.count { it.status.equals("confusion", ignoreCase = true) }, AmberWarning),
                                Triple("Don't Know", words.count { it.status.equals("dont_know", ignoreCase = true) }, Color(0xFFE11D48)),
                                Triple("Unrated", words.count { it.status.equals("unrated", ignoreCase = true) }, Color(0xFF64748B)),
                                Triple("Flagged", words.count { it.isReported }, Color(0xFFE11D48))
                            )
                            counts.forEach { (lbl, cnt, clr) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = clr.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, clr.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = "$lbl: $cnt",
                                        fontSize = 10.sp,
                                        color = clr,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    // 2. Course List Multi-Select Section with Icons
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Courses (${exportSelectedCourseIds.size}/${courses.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = palette.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Select All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndigoPrimary,
                                    modifier = Modifier
                                        .clickable { exportSelectedCourseIds = courses.map { it.id }.toSet() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = palette.textMuted
                                )
                                Text(
                                    text = "Deselect",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE11D48),
                                    modifier = Modifier
                                        .clickable { exportSelectedCourseIds = emptySet() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Courses multi-select list/grid with icons
                        if (courses.isEmpty()) {
                            Text("No courses found", fontSize = 11.sp, color = palette.textMuted)
                        } else {
                            val displayedCourses = if (isCoursesExpanded || courses.size <= 4) courses else courses.take(4)

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                displayedCourses.forEach { course ->
                                    val isSelected = course.id in exportSelectedCourseIds
                                    val courseWordsCount = words.count { it.courseId == course.id }

                                    Surface(
                                        onClick = {
                                            exportSelectedCourseIds = if (isSelected) {
                                                exportSelectedCourseIds - course.id
                                            } else {
                                                exportSelectedCourseIds + course.id
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) IndigoLight.copy(alpha = 0.5f) else (if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) IndigoPrimary.copy(alpha = 0.6f) else palette.border
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // Course Icon
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) IndigoPrimary else palette.border.copy(alpha = 0.4f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.School,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (isSelected) Color.White else palette.textMuted
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = course.title,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        fontSize = 12.sp,
                                                        color = palette.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "$courseWordsCount words",
                                                        fontSize = 10.sp,
                                                        color = palette.textMuted
                                                    )
                                                }
                                            }

                                            // Multi-Select Check Icon
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) IndigoPrimary else palette.cardBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (courses.size > 4) {
                                    Text(
                                        text = if (isCoursesExpanded) "Show Less ▲" else "+ Show ${courses.size - 4} More Courses ▼",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IndigoPrimary,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .clickable { isCoursesExpanded = !isCoursesExpanded }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    // 3. Export Action Buttons (JSON, CSV, Share)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (filteredWords.isEmpty()) {
                                        Toast.makeText(context, "No words matched the selected filters", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pendingExportFormat = "json"
                                        filteredDocLauncher.launch("memorizer_filtered_${System.currentTimeMillis()}.json")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export JSON", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (filteredWords.isEmpty()) {
                                        Toast.makeText(context, "No words matched the selected filters", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pendingExportFormat = "csv"
                                        filteredDocLauncher.launch("memorizer_filtered_${System.currentTimeMillis()}.csv")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export CSV", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Share Action
                        OutlinedButton(
                            onClick = {
                                if (filteredWords.isEmpty()) {
                                    Toast.makeText(context, "No words matched to share", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        val content = generateFilteredCsv(filteredWords, courses)
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, content)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Filtered Words (CSV)"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = IndigoPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Filtered List Directly", fontSize = 11.sp, color = IndigoPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Question Bank Progress Backup Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Question Bank", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = palette.textPrimary)
                        TextButton(
                            onClick = onImportQBClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Sync Link", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportQBProgress,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Export QB", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onRestoreQBProgress,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore QB", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Full App Backup & Restore
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Full App Backup (JSON)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = palette.textPrimary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportFile,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Export JSON", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onRestoreFile,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore JSON", fontSize = 11.sp)
                        }
                    }

                    TextButton(
                        onClick = onDirectPasteRestore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Paste Raw JSON", fontSize = 11.sp)
                    }
                }
            }
        }

        // Reset
        item {
            TextButton(
                onClick = onResetData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Sample Data", fontSize = 11.sp, color = Color(0xFFE11D48))
            }
        }
    }
}

// ----------------------------------------------------
// Compact Reusable UI Components
// ----------------------------------------------------
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IndigoPrimary
            )
        )
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                Text(title, fontSize = 9.sp, color = palette.textMuted)
            }
        }
    }
}

@Composable
private fun SubViewChip(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) IndigoPrimary else palette.surface,
        border = BorderStroke(1.dp, if (selected) IndigoPrimary else palette.border),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else palette.textMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else palette.textPrimary
            )
        }
    }
}

data class SettingsTabItem(
    val title: String,
    val icon: ImageVector
)

// ----------------------------------------------------
// Compact Modals for Settings
// ----------------------------------------------------
@Composable
private fun EditProfileModal(
    currentName: String,
    currentAvatar: String?,
    currentTargetExam: String,
    currentGoal: Int,
    currentBio: String,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: String?, exam: String, goal: Int, bio: String) -> Unit
) {
    val palette = LocalAppPalette.current
    var name by remember { mutableStateOf(currentName) }
    var avatarUriState by remember { mutableStateOf(currentAvatar) }
    var targetExam by remember { mutableStateOf(currentTargetExam) }
    var dailyGoalStr by remember { mutableStateOf(currentGoal.toString()) }
    var bio by remember { mutableStateOf(currentBio) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUriState = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar preview and picker button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (palette.isDark) Color(0xFF1E293B) else IndigoLight)
                        .border(1.5.dp, IndigoPrimary, CircleShape)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUriState.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUriState,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Pick Photo",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                TextButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = IndigoPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (avatarUriState.isNullOrBlank()) "Choose Profile Photo" else "Change Profile Photo",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoPrimary
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetExam,
                    onValueChange = { targetExam = it },
                    label = { Text("Target Exam (e.g. GRE, IELTS)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dailyGoalStr,
                    onValueChange = { dailyGoalStr = it },
                    label = { Text("Daily Word Goal", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Short Bio", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim(), avatarUriState, targetExam.trim(), dailyGoalStr.toIntOrNull() ?: 20, bio.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CompactCreateCourseModal(
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
private fun CompactEditCourseModal(
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
private fun CompactWordModal(
    initialWord: VocabularyWordEntity?,
    activeCourseId: String,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: (VocabularyWordEntity) -> Unit
) {
    var word by remember { mutableStateOf(initialWord?.word ?: "") }
    var meaning by remember { mutableStateOf(initialWord?.meaning ?: "") }
    var group by remember { mutableStateOf(initialWord?.group ?: "1") }
    var synonyms by remember { mutableStateOf(initialWord?.synonyms ?: "") }
    var example by remember { mutableStateOf(initialWord?.example ?: "") }
    var selectedCourseId by remember { mutableStateOf(initialWord?.courseId ?: activeCourseId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialWord == null) "Add Word" else "Edit Word", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Word", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text("Meaning", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Group (e.g. 1, 2, 3...)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = synonyms,
                    onValueChange = { synonyms = it },
                    label = { Text("Synonyms (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text("Example Sentence (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (word.isNotBlank() && meaning.isNotBlank()) {
                        val wordEntity = initialWord?.copy(
                            word = word.trim(),
                            meaning = meaning.trim(),
                            group = group.trim().ifBlank { "1" },
                            synonyms = synonyms.ifBlank { null },
                            example = example.ifBlank { null },
                            courseId = selectedCourseId
                        ) ?: VocabularyWordEntity(
                            id = "custom_${System.currentTimeMillis()}",
                            word = word.trim(),
                            meaning = meaning.trim(),
                            group = group.trim().ifBlank { "1" },
                            synonyms = synonyms.ifBlank { null },
                            example = example.ifBlank { null },
                            courseId = selectedCourseId
                        )
                        onSave(wordEntity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text(if (initialWord == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CompactDriveSyncModal(
    initialUrl: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: (url: String, preserve: Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var preserve by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync from Google Drive / Sheets", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Google Drive / Sheet Share URL", fontSize = 11.sp) },
                    placeholder = { Text("https://docs.google.com/spreadsheets/...", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = preserve, onCheckedChange = { preserve = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preserve existing data", fontSize = 11.sp)
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

@Composable
private fun CompactQbSyncModal(
    initialUrl: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: (url: String, clearExisting: Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var clearExisting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val palette = LocalAppPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                Text("Sync Question Bank", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Drive / Sheets Public Link", fontSize = 11.sp) },
                    placeholder = { Text("Paste share link...", fontSize = 10.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndigoPrimary)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (url.isNotBlank()) {
                                IconButton(onClick = { url = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = clipboard?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasteText.isNotBlank()) {
                                            url = pasteText.trim()
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(14.dp), tint = IndigoPrimary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = clearExisting,
                        onCheckedChange = { clearExisting = it },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Replace existing questions", fontSize = 11.5.sp, color = palette.textPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) onSync(url.trim(), clearExisting)
                },
                enabled = url.isNotBlank() && !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importing...", fontSize = 11.5.sp)
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Now", fontSize = 11.5.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSyncing) {
                Text("Cancel", fontSize = 11.5.sp)
            }
        }
    )
}

@Composable
private fun CompactBulkEditModal(
    selectedCount: Int,
    courses: List<CourseEntity>,
    availableGroups: List<String>,
    onDismiss: () -> Unit,
    onApply: (newGroup: String?, newCourseId: String?, newStatus: String?) -> Unit
) {
    val palette = LocalAppPalette.current
    var changeGroupEnabled by remember { mutableStateOf(false) }
    var targetGroup by remember { mutableStateOf("") }

    var changeCourseEnabled by remember { mutableStateOf(false) }
    var targetCourseId by remember { mutableStateOf(courses.firstOrNull()?.id ?: "") }
    var courseDropdownOpen by remember { mutableStateOf(false) }

    var changeStatusEnabled by remember { mutableStateOf(false) }
    var targetStatus by remember { mutableStateOf("know") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                Text("Bulk Edit ($selectedCount items)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Change Group
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (changeGroupEnabled) IndigoPrimary else palette.border)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { changeGroupEnabled = !changeGroupEnabled }
                        ) {
                            Checkbox(
                                checked = changeGroupEnabled,
                                onCheckedChange = { changeGroupEnabled = it },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Group", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.textPrimary)
                        }

                        if (changeGroupEnabled) {
                            OutlinedTextField(
                                value = targetGroup,
                                onValueChange = { targetGroup = it },
                                placeholder = { Text("e.g. 1, 2, 3...", fontSize = 11.sp) },
                                label = { Text("Target Group Name / Number", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )

                            if (availableGroups.isNotEmpty()) {
                                Text("Existing Groups:", fontSize = 10.sp, color = palette.textMuted)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    availableGroups.forEach { grp ->
                                        Surface(
                                            onClick = { targetGroup = grp },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (targetGroup == grp) IndigoPrimary else palette.surface,
                                            border = BorderStroke(1.dp, if (targetGroup == grp) IndigoPrimary else palette.border)
                                        ) {
                                            Text(
                                                text = "Group $grp",
                                                fontSize = 10.sp,
                                                color = if (targetGroup == grp) Color.White else palette.textPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Move to Course
                if (courses.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (changeCourseEnabled) IndigoPrimary else palette.border)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { changeCourseEnabled = !changeCourseEnabled }
                            ) {
                                Checkbox(
                                    checked = changeCourseEnabled,
                                    onCheckedChange = { changeCourseEnabled = it },
                                    modifier = Modifier.size(20.dp),
                                    colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Move to Another Course", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.textPrimary)
                            }

                            if (changeCourseEnabled) {
                                val selectedCourseObj = courses.firstOrNull { it.id == targetCourseId }
                                Box {
                                    Surface(
                                        onClick = { courseDropdownOpen = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = palette.surface,
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = selectedCourseObj?.title ?: "Select Course",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = palette.textPrimary
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMuted)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = courseDropdownOpen,
                                        onDismissRequest = { courseDropdownOpen = false }
                                    ) {
                                        courses.forEach { course ->
                                            DropdownMenuItem(
                                                text = { Text(course.title, fontSize = 12.sp) },
                                                onClick = {
                                                    targetCourseId = course.id
                                                    courseDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Change Learning Status
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (changeStatusEnabled) IndigoPrimary else palette.border)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { changeStatusEnabled = !changeStatusEnabled }
                        ) {
                            Checkbox(
                                checked = changeStatusEnabled,
                                onCheckedChange = { changeStatusEnabled = it },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Learning Status", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.textPrimary)
                        }

                        if (changeStatusEnabled) {
                            val statuses = listOf(
                                Triple("know", "Know", EmeraldSuccess),
                                Triple("confusion", "Confusion", AmberWarning),
                                Triple("dont_know", "Don't Know", Color(0xFFE11D48)),
                                Triple("unrated", "Unrated", Color(0xFF64748B))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                statuses.forEach { (stKey, stLabel, stColor) ->
                                    val isSelected = targetStatus == stKey
                                    Surface(
                                        onClick = { targetStatus = stKey },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) stColor else palette.surface,
                                        border = BorderStroke(1.dp, if (isSelected) stColor else palette.border),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = stLabel,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else stColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalGroup = if (changeGroupEnabled && targetGroup.isNotBlank()) targetGroup.trim() else null
                    val finalCourse = if (changeCourseEnabled && targetCourseId.isNotBlank()) targetCourseId else null
                    val finalStatus = if (changeStatusEnabled && targetStatus.isNotBlank()) targetStatus else null
                    onApply(finalGroup, finalCourse, finalStatus)
                },
                enabled = changeGroupEnabled || changeCourseEnabled || changeStatusEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Apply to $selectedCount Words", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}

private fun generateFilteredJson(
    filteredWords: List<VocabularyWordEntity>,
    courses: List<CourseEntity>,
    statusTags: Set<String>
): String {
    val courseMap = courses.associateBy { it.id }
    val root = JSONObject()
    root.put("app", "Memorizer")
    root.put("type", "filtered_export")
    root.put("version", "2.0")
    root.put("timestamp", System.currentTimeMillis())
    root.put("totalWords", filteredWords.size)
    val tagsArray = JSONArray()
    if (statusTags.isEmpty()) {
        tagsArray.put("all")
    } else {
        statusTags.forEach { tagsArray.put(it) }
    }
    root.put("selectedStatuses", tagsArray)

    val coursesArray = JSONArray()
    courses.filter { c -> filteredWords.any { it.courseId == c.id } }.forEach { c ->
        val cObj = JSONObject()
        cObj.put("id", c.id)
        cObj.put("title", c.title)
        cObj.put("description", c.description ?: "")
        coursesArray.put(cObj)
    }
    root.put("courses", coursesArray)

    val wordsArray = JSONArray()
    filteredWords.forEach { w ->
        val wObj = JSONObject()
        wObj.put("id", w.id)
        wObj.put("courseId", w.courseId)
        wObj.put("courseTitle", courseMap[w.courseId]?.title ?: "General")
        wObj.put("word", w.word)
        wObj.put("meaning", w.meaning)
        wObj.put("group", w.group)
        wObj.put("synonyms", w.synonyms ?: "")
        wObj.put("extraWord", w.extraWord ?: "")
        wObj.put("extraMeaning", w.extraMeaning ?: "")
        wObj.put("example", w.example ?: "")
        wObj.put("mnemonic", w.mnemonic ?: "")
        wObj.put("status", w.status)
        wObj.put("isReported", w.isReported)
        wObj.put("reportReason", w.reportReason ?: "")
        wObj.put("quizCorrectCount", w.quizCorrectCount)
        wObj.put("quizIncorrectCount", w.quizIncorrectCount)
        wordsArray.put(wObj)
    }
    root.put("words", wordsArray)
    return root.toString(2)
}

private fun generateFilteredCsv(
    filteredWords: List<VocabularyWordEntity>,
    courses: List<CourseEntity>
): String {
    val courseMap = courses.associateBy { it.id }
    val sb = StringBuilder()
    sb.append("id,courseId,courseTitle,group,word,meaning,example,synonyms,extraWord,mnemonic,status,isReported,reportReason\n")
    filteredWords.forEach { w ->
        sb.append("\"${w.id}\",")
        sb.append("\"${w.courseId}\",")
        sb.append("\"${(courseMap[w.courseId]?.title ?: "General").replace("\"", "\"\"")}\",")
        sb.append("\"${w.group.replace("\"", "\"\"")}\",")
        sb.append("\"${w.word.replace("\"", "\"\"")}\",")
        sb.append("\"${w.meaning.replace("\"", "\"\"")}\",")
        sb.append("\"${(w.example ?: "").replace("\"", "\"\"")}\",")
        sb.append("\"${(w.synonyms ?: "").replace("\"", "\"\"")}\",")
        sb.append("\"${(w.extraWord ?: "").replace("\"", "\"\"")}\",")
        sb.append("\"${(w.mnemonic ?: "").replace("\"", "\"\"")}\",")
        sb.append("\"${w.status}\",")
        sb.append("\"${w.isReported}\",")
        sb.append("\"${(w.reportReason ?: "").replace("\"", "\"\"")}\"\n")
    }
    return sb.toString()
}
