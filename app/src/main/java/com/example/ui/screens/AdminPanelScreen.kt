package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.parser.FileParsers
import com.example.data.parser.GameValidationSummary
import com.example.data.parser.ParsedSheet
import com.example.data.repository.DriveSyncSummary
import com.example.data.repository.LocalCourseFileInput
import com.example.ui.theme.*
import java.io.ByteArrayInputStream

@Composable
fun AdminPanelScreen(
    words: List<VocabularyWordEntity>,
    games: List<GamePracticeEntity>,
    questions: List<QuestionBankEntity>,
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "",
    selectedCourseIds: Set<String> = emptySet(),
    onToggleCourseSelection: (String) -> Unit = {},
    onSelectAllCourses: () -> Unit = {},
    onDeselectAllCourses: () -> Unit = {},
    isSyncingDrive: Boolean = false,
    driveSyncUrl: String = "",
    driveSyncSummary: DriveSyncSummary? = null,
    onSyncFromDrive: (String, Boolean) -> Unit = { _, _ -> },
    onBatchImportFiles: (List<LocalCourseFileInput>, Boolean) -> Unit = { _, _ -> },
    onClearDriveSummary: () -> Unit = {},
    onCreateCourse: (String, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onSelectCourse: (String) -> Unit = {},
    onDeleteCourse: (String) -> Unit = {},
    onAddWord: (VocabularyWordEntity) -> Unit,
    onUpdateWord: (VocabularyWordEntity) -> Unit = {},
    onUpdateCourse: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteWord: (String) -> Unit,
    onDeleteGame: (String) -> Unit,
    onDeleteGamesBySection: (String) -> Unit = {},
    onClearAllGames: () -> Unit = {},
    onDeleteQuestion: (String) -> Unit,
    onClearAllQB: () -> Unit = {},
    onImportCourse: (String, Boolean, String, String?) -> Unit = { _, _, _, _ -> },
    onImportGame: (String, String) -> Unit,
    onImportGameItems: (List<GamePracticeEntity>) -> Unit = {},
    onImportQB: (String) -> Unit,
    onResetData: () -> Unit = {},
    onSubPageChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubPage by remember { mutableStateOf<String?>(null) } // null = Main Dashboard, "courses", "words", "games", "qb"

    LaunchedEffect(selectedSubPage) {
        onSubPageChange(selectedSubPage)
    }
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var showAddWordDialog by remember { mutableStateOf(false) }
    var showUploadCourseDialog by remember { mutableStateOf(false) }
    var showUploadGameDialog by remember { mutableStateOf(false) }
    var showUploadQBDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var targetCourseIdForUpload by remember(activeCourseId) { mutableStateOf(activeCourseId) }
    var wordBeingEdited by remember { mutableStateOf<VocabularyWordEntity?>(null) }
    var courseBeingEdited by remember { mutableStateOf<CourseEntity?>(null) }

    // Multi-file picker for batch course files from device
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

    // Intercept back button if in a sub-page
    BackHandler(enabled = selectedSubPage != null) {
        selectedSubPage = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (selectedSubPage == null) {
            // First page of Control Panel: Download button & Main categories in list order with modern minimal tabs
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Two minimal side-by-side buttons for Drive Course Sync & Download Datasets (Zero description to save space)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDriveSyncDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (isSyncingDrive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Syncing...",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            } else {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Drive Course Sync",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://drive.google.com/drive/folders/1OBqSlB21FD_-0tpRZE8H6R5VFzDkeX2n")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldSuccess),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = EmeraldSuccess
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download Dataset",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccess,
                                maxLines = 1
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "MAIN CATEGORIES",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateLight,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // 1. Courses Tab
                item {
                    val activeCourseTitle = courses.find { it.id == activeCourseId }?.title ?: "Default"
                    val selCount = if (selectedCourseIds.isEmpty()) courses.size else selectedCourseIds.size
                    AdminCategoryListCard(
                        title = "Courses",
                        subtitle = "${courses.size} courses • $selCount selected for study & backup",
                        badge = "$selCount/${courses.size}",
                        icon = Icons.Default.School,
                        iconBg = IndigoLight,
                        iconTint = IndigoPrimary,
                        onClick = { selectedSubPage = "courses" }
                    )
                }

                // 2. Words Tab
                item {
                    AdminCategoryListCard(
                        title = "Words",
                        subtitle = "${words.size} vocabulary items • Search, filter & edit",
                        badge = "${words.size}",
                        icon = Icons.Default.Translate,
                        iconBg = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF2563EB),
                        onClick = { selectedSubPage = "words" }
                    )
                }

                // 3. Games Tab
                item {
                    AdminCategoryListCard(
                        title = "Games",
                        subtitle = "${games.size} questions • Odd One Out, Analogy, Practice Quiz",
                        badge = "${games.size}",
                        icon = Icons.Default.SportsEsports,
                        iconBg = Color(0xFFFEF3C7),
                        iconTint = Color(0xFFD97706),
                        onClick = { selectedSubPage = "games" }
                    )
                }

                // 4. Question Bank (QB) Tab
                item {
                    AdminCategoryListCard(
                        title = "Question Bank (QB)",
                        subtitle = "${questions.size} questions • Multi-filter question bank",
                        badge = "${questions.size}",
                        icon = Icons.Default.Quiz,
                        iconBg = Color(0xFFF3E8FF),
                        iconTint = Color(0xFF9333EA),
                        onClick = { selectedSubPage = "qb" }
                    )
                }
            }
        } else {
            // Sub-page Top Navigation Bar (Slim, minimal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedSubPage = null },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Control Panel",
                        tint = SlateText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = when (selectedSubPage) {
                            "courses" -> "Courses (${courses.size})"
                            "words" -> "Words (${words.size})"
                            "games" -> "Games (${games.size})"
                            "qb" -> "Question Bank (${questions.size})"
                            else -> "Control"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                }
            }

            // Sub-page Content
            when (selectedSubPage) {
                "courses" -> {
                    CoursesAdminView(
                        courses = courses,
                        activeCourseId = activeCourseId,
                        selectedCourseIds = selectedCourseIds,
                        onToggleCourseSelection = onToggleCourseSelection,
                        onSelectAllCourses = onSelectAllCourses,
                        onDeselectAllCourses = onDeselectAllCourses,
                        words = words,
                        onOpenDriveSync = { showDriveSyncDialog = true },
                        onBatchUpload = { batchFilePicker.launch("*/*") },
                        onCreateCourseClick = { showCreateCourseDialog = true },
                        onSelectCourse = onSelectCourse,
                        onDeleteCourse = onDeleteCourse,
                        onEditCourse = { course -> courseBeingEdited = course },
                        onManageWords = { selectedSubPage = "words" },
                        onUploadToCourse = { cId ->
                            targetCourseIdForUpload = cId
                            showUploadCourseDialog = true
                        }
                    )
                }
                "words" -> {
                    WordsAdminView(
                        words = words,
                        courses = courses,
                        activeCourseId = activeCourseId,
                        onSelectCourse = onSelectCourse,
                        onAddWordClick = { showAddWordDialog = true },
                        onUploadClick = {
                            targetCourseIdForUpload = activeCourseId
                            showUploadCourseDialog = true
                        },
                        onEditWord = { word -> wordBeingEdited = word },
                        onDeleteWord = onDeleteWord,
                        onClearReport = { wordId ->
                            val word = words.find { it.id == wordId }
                            if (word != null) {
                                onUpdateWord(word.copy(isReported = false, reportReason = null))
                            }
                        }
                    )
                }
                "games" -> {
                    GamesAdminView(
                        games = games,
                        onUploadClick = { showUploadGameDialog = true },
                        onDeleteGame = onDeleteGame,
                        onDeleteGamesBySection = onDeleteGamesBySection,
                        onClearAllGames = onClearAllGames
                    )
                }
                "qb" -> {
                    QbAdminView(
                        questions = questions,
                        onUploadClick = { showUploadQBDialog = true },
                        onDeleteQuestion = onDeleteQuestion,
                        onClearAllQB = onClearAllQB
                    )
                }
            }
        }
    }

    // Create Course Dialog
    if (showCreateCourseDialog) {
        CreateCourseDialog(
            onDismiss = { showCreateCourseDialog = false },
            onConfirm = { title, desc, fileContent, isJson ->
                onCreateCourse(title, desc, fileContent, isJson)
                showCreateCourseDialog = false
            }
        )
    }

    // Add Word Dialog
    if (showAddWordDialog) {
        AddWordDialog(
            onDismiss = { showAddWordDialog = false },
            onConfirm = { newWord ->
                onAddWord(newWord.copy(courseId = activeCourseId))
                showAddWordDialog = false
            }
        )
    }

    // Upload Course Dialog (Excel / CSV / JSON) with Preview
    if (showUploadCourseDialog) {
        val courseName = courses.firstOrNull { it.id == targetCourseIdForUpload }?.title ?: "Selected Course"
        UploadFileDialog(
            title = "Upload Course Vocab ($courseName)",
            subtitle = "Target Course: $courseName\nColumns format: id, group, Place1: Word, Place2: Meaning, Place3: Example, Place4: Synonyms, Place5: Extra, Place6: Mnemonic\nSupports Excel (.xlsx), CSV, and JSON files from device.",
            defaultContent = "id,group,Place1: Word,Place2: Meaning,Place3: Example,Place4: Synonyms,Place5: Extra,Place6: Mnemonic\n\"custom_1\",1,\"Eloquent\",\"Fluent and persuasive in speech\",\"An eloquent speaker captivates any audience.\",\"Articulate, expressive\",\"Eloquence (noun)\",\"E-loquent: speak with great flow.\"",
            initialTitle = courseName,
            onDismiss = { showUploadCourseDialog = false },
            onImport = { content, isJson, updatedTitle ->
                onImportCourse(content, isJson, targetCourseIdForUpload, updatedTitle)
                showUploadCourseDialog = false
            }
        )
    }

    // Upload Game Dialog with Multi-Sheet Validation & Preview
    if (showUploadGameDialog) {
        UploadGameWithValidationDialog(
            onDismiss = { showUploadGameDialog = false },
            onImportValidItems = { validItems ->
                onImportGameItems(validItems)
                showUploadGameDialog = false
            },
            onImportRawContent = { content, sheetType ->
                onImportGame(content, sheetType)
                showUploadGameDialog = false
            }
        )
    }

    // Upload Question Bank Dialog (Excel / CSV)
    if (showUploadQBDialog) {
        UploadFileDialog(
            title = "Upload Question Bank File (Excel / CSV)",
            subtitle = "Columns format: Id*, Question*, Opt1-4*, Ans*, Explanation, Filter1:label, Filter2:label, Filter3:label\nSupports Excel (.xlsx) and CSV files from device.",
            defaultContent = "Id,Question,Opt1,Opt2,Opt3,Opt4,Ans,Explanation,Filter1:Category,Filter2:Difficulty,Filter3:Source\n\"qb_101\",\"Identify the appropriate synonym for 'Ephemeral':\",\"Transient#\",\"Eternal\",\"Persistent\",\"Enduring\",\"Transient\",\"Ephemeral means fleeting or transient.\",\"Vocabulary Mastery\",\"Medium\",\"Exam Prep\"",
            onDismiss = { showUploadQBDialog = false },
            onImport = { content, _, _ ->
                onImportQB(content)
                showUploadQBDialog = false
            }
        )
    }

    // Edit Word Dialog (Manual Data Edit)
    if (wordBeingEdited != null) {
        EditWordDialog(
            word = wordBeingEdited!!,
            onDismiss = { wordBeingEdited = null },
            onConfirm = { updatedWord ->
                onUpdateWord(updatedWord)
                wordBeingEdited = null
            }
        )
    }

    // Edit Course Dialog (Manual Course Edit)
    if (courseBeingEdited != null) {
        EditCourseDialog(
            course = courseBeingEdited!!,
            onDismiss = { courseBeingEdited = null },
            onConfirm = { newTitle, newDescription ->
                onUpdateCourse(courseBeingEdited!!.id, newTitle, newDescription)
                courseBeingEdited = null
            }
        )
    }

    // Google Drive Sync Dialog
    if (showDriveSyncDialog) {
        GoogleDriveSyncDialog(
            initialUrl = driveSyncUrl,
            isSyncing = isSyncingDrive,
            onDismiss = { showDriveSyncDialog = false },
            onSync = { url, preserve ->
                onSyncFromDrive(url, preserve)
                showDriveSyncDialog = false
            },
            onPickBatchFiles = {
                batchFilePicker.launch("*/*")
            }
        )
    }

    // Google Drive Sync Summary Dialog
    if (driveSyncSummary != null) {
        DriveSyncSummaryDialog(
            summary = driveSyncSummary,
            onDismiss = onClearDriveSummary
        )
    }
}

@Composable
private fun AdminCategoryListCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 7.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = badge,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateMuted
                            )
                        }
                    }

                    Text(
                        text = subtitle,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = SlateMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CoursesAdminView(
    courses: List<CourseEntity>,
    activeCourseId: String,
    selectedCourseIds: Set<String> = emptySet(),
    onToggleCourseSelection: (String) -> Unit = {},
    onSelectAllCourses: () -> Unit = {},
    onDeselectAllCourses: () -> Unit = {},
    words: List<VocabularyWordEntity>,
    onOpenDriveSync: () -> Unit = {},
    onBatchUpload: () -> Unit = {},
    onCreateCourseClick: () -> Unit,
    onSelectCourse: (String) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onEditCourse: (CourseEntity) -> Unit = {},
    onManageWords: () -> Unit = {},
    onUploadToCourse: (String) -> Unit
) {
    var coursePendingDelete by remember { mutableStateOf<CourseEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Google Drive Course Sync Card
        GoogleDriveSyncCard(
            isSyncing = false,
            onOpenSyncDialog = onOpenDriveSync
        )

        // Actions Row: Batch Upload from device & Create New Course
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBatchUpload,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFCBD5E1))
                )
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateText)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Batch Files",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateText
                )
            }

            Button(
                onClick = onCreateCourseClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create Course",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Selection Control Bar: Controls which courses are used for Flashcards, Games, and Backup
        if (courses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(15.dp))
                            Text(
                                text = "Study & Backup Selection",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                        }
                        val selCount = if (selectedCourseIds.isEmpty()) courses.size else selectedCourseIds.size
                        Text(
                            text = "$selCount of ${courses.size} courses active in Flashcards, Games & Backup",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.5.sp,
                            color = SlateMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onSelectAllCourses,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                        }
                        TextButton(
                            onClick = onDeselectAllCourses,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Normal, color = SlateMuted)
                        }
                    }
                }
            }
        }

        if (courses.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = SlateLight,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No Courses Created Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SlateText
                    )
                    Text(
                        text = "Create a new course above or download pre-made course datasets from Google Drive.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(courses, key = { it.id }) { course ->
                    val isActive = course.id == activeCourseId
                    val isIncluded = selectedCourseIds.isEmpty() || selectedCourseIds.contains(course.id)
                    val courseWordCount = words.count { it.courseId == course.id }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFFF0FDF4) else if (isIncluded) Color.White else Color(0xFFF8FAFC)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isActive) EmeraldSuccess else if (isIncluded) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = course.title,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) EmeraldSuccess else if (isIncluded) SlateText else SlateMuted
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(EmeraldLight)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEditCourse(course) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Course", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { coursePendingDelete = course },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Course", tint = RoseError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Course Selection Checkbox row for Study & Backup
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isIncluded) EmeraldLight.copy(alpha = 0.45f) else Color(0xFFF1F5F9))
                                    .clickable { onToggleCourseSelection(course.id) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isIncluded,
                                    onCheckedChange = { onToggleCourseSelection(course.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldSuccess,
                                        uncheckedColor = SlateLight
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isIncluded) "Included in Study & Backup" else "Excluded from Study & Backup",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (isIncluded) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isIncluded) EmeraldSuccess else SlateMuted
                                )
                            }

                            if (!course.description.isNullOrBlank()) {
                                Text(
                                    text = course.description,
                                    fontSize = 11.sp,
                                    color = SlateMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$courseWordCount Vocabulary Words",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateLight
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            onSelectCourse(course.id)
                                            onManageWords()
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Words", fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onUploadToCourse(course.id) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload", fontSize = 10.sp)
                                    }

                                    if (!isActive) {
                                        Button(
                                            onClick = { onSelectCourse(course.id) },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Select", fontSize = 10.sp)
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

    // Confirmation dialog before deleting course
    if (coursePendingDelete != null) {
        val courseToDelete = coursePendingDelete!!
        AlertDialog(
            onDismissRequest = { coursePendingDelete = null },
            title = {
                Text("Delete Course?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${courseToDelete.title}'?\n\nAll vocabulary words and data associated with this course will be deleted.",
                    fontSize = 13.sp,
                    color = SlateMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCourse(courseToDelete.id)
                        coursePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Delete Course")
                }
            },
            dismissButton = {
                TextButton(onClick = { coursePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CreateCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, initialContent: String?, isJson: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var isJsonFile by remember { mutableStateOf(false) }
    var fileFeedback by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            var fileName = "Selected file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            selectedFileName = fileName
            val derivedTitle = fileName.substringBeforeLast(".")
            // Title is automatically filled from file name and editable
            title = derivedTitle

            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                    val rows = FileParsers.parseXlsx(stream)
                    val csv = FileParsers.rowsToCsv(rows)
                    fileContent = csv
                    isJsonFile = false
                    val count = maxOf(0, rows.size - 1)
                    fileFeedback = "Excel file loaded ($count items). Title auto-filled from file name."
                } else if (fileName.endsWith(".json", ignoreCase = true)) {
                    val text = stream.bufferedReader().readText()
                    fileContent = text
                    isJsonFile = true
                    fileFeedback = "JSON file loaded. Title auto-filled from file name."
                } else {
                    val text = stream.bufferedReader().readText()
                    fileContent = text
                    isJsonFile = false
                    val count = maxOf(0, text.lines().count { it.isNotBlank() } - 1)
                    fileFeedback = "CSV file loaded ($count items). Title auto-filled from file name."
                }
            }
        } catch (e: Exception) {
            fileFeedback = "Failed to load file: ${e.localizedMessage}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Course", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "text/csv",
                                "text/comma-separated-values",
                                "application/json",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select File (.xlsx / .csv / .json)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (fileFeedback != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldLight)
                    ) {
                        Text(
                            text = fileFeedback ?: "",
                            color = EmeraldSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title (Optional - auto-filled from file)") },
                    placeholder = { Text("Auto-filled from file name (editable)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Short info about this course") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifEmpty {
                        selectedFileName?.substringBeforeLast(".") ?: "New Course"
                    }
                    onConfirm(finalTitle, description.trim().ifEmpty { null }, fileContent, isJsonFile)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Create Course")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WordsAdminView(
    words: List<VocabularyWordEntity>,
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "",
    onSelectCourse: (String) -> Unit = {},
    onAddWordClick: () -> Unit,
    onUploadClick: () -> Unit,
    onEditWord: (VocabularyWordEntity) -> Unit = {},
    onDeleteWord: (String) -> Unit,
    onClearReport: (String) -> Unit = {}
) {
    var selectedCourseFilterId by remember(activeCourseId, courses) {
        mutableStateOf(if (activeCourseId.isNotBlank()) activeCourseId else courses.firstOrNull()?.id ?: "")
    }
    var showOnlyReported by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val activeCourseObj = courses.firstOrNull { it.id == selectedCourseFilterId }

    // Words filtered by chosen course (or all if none selected)
    val wordsInCourse = remember(words, selectedCourseFilterId) {
        if (selectedCourseFilterId.isBlank()) words
        else words.filter { it.courseId == selectedCourseFilterId }
    }

    val reportedCountInCourse = remember(wordsInCourse) {
        wordsInCourse.count { it.isReported }
    }

    val displayedWords = remember(wordsInCourse, showOnlyReported, searchQuery) {
        var list = if (showOnlyReported) wordsInCourse.filter { it.isReported } else wordsInCourse
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.word.lowercase().contains(q) ||
                it.meaning.lowercase().contains(q) ||
                it.group.lowercase().contains(q) ||
                (it.reportReason?.lowercase()?.contains(q) == true)
            }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Course Selector Chips Row (no bulky label or margins)
        if (courses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                courses.forEach { course ->
                    val isSelected = selectedCourseFilterId == course.id
                    val count = words.count { it.courseId == course.id }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCourseFilterId = course.id
                            onSelectCourse(course.id)
                        },
                        label = {
                            Text(
                                text = "${course.title} ($count)",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                FilterChip(
                    selected = selectedCourseFilterId.isBlank(),
                    onClick = { selectedCourseFilterId = "" },
                    label = {
                        Text(
                            text = "All (${words.size})",
                            fontSize = 10.sp,
                            fontWeight = if (selectedCourseFilterId.isBlank()) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Compact Search & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Bar (compact 36dp)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search words, meanings...", fontSize = 11.sp, color = SlateLight) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateLight, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateLight, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = SlateBorder
                )
            )

            // Add Word Button (compact)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoPrimary)
                    .clickable { onAddWordClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Word", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Upload Button (compact)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .clickable { onUploadClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Upload File", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
            }

            // Reported filter chip (compact)
            FilterChip(
                selected = showOnlyReported,
                onClick = { showOnlyReported = !showOnlyReported },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = if (showOnlyReported) Color.White else (if (reportedCountInCourse > 0) RoseError else SlateLight),
                        modifier = Modifier.size(13.dp)
                    )
                },
                label = {
                    Text(
                        text = "$reportedCountInCourse",
                        fontSize = 10.sp,
                        fontWeight = if (showOnlyReported) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RoseError,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.height(30.dp)
            )
        }

        // Single tiny status line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${displayedWords.size} words" + if (showOnlyReported) " (Reported only)" else "",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMuted
            )
        }

        if (displayedWords.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (showOnlyReported) Icons.Default.CheckCircle else Icons.Default.LayersClear,
                        contentDescription = null,
                        tint = if (showOnlyReported) EmeraldSuccess else SlateLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (showOnlyReported) "No reported words in this course!" else "No words found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SlateText
                    )
                    Text(
                        text = if (showOnlyReported) "All words in this course are clean." else "Upload an Excel (.xlsx) or CSV file with vocab items.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedWords, key = { it.id }) { word ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (word.isReported) RoseError.copy(alpha = 0.6f) else SlateBorder)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = word.word,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateText
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(IndigoLight)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Group ${word.group}",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndigoPrimary
                                            )
                                        }

                                        val statusColor = when (word.status) {
                                            "know" -> EmeraldSuccess
                                            "dont_know" -> RoseError
                                            "confusion" -> AmberWarning
                                            else -> SlateLight
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = word.status.replace("_", " ").uppercase(),
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = word.meaning,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (!word.example.isNullOrBlank()) {
                                        Text(
                                            text = word.example,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 12.sp,
                                            color = SlateMuted
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditWord(word) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Word", tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onDeleteWord(word.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Word", tint = RoseError, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            // Prominent Reported Banner with Clear button
                            if (word.isReported) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF1F2))
                                        .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = RoseError, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Reported: ${word.reportReason ?: "Issue reported"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RoseError
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onEditWord(word) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Edit", fontSize = 11.sp, color = IndigoPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { onClearReport(word.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Clear", fontSize = 11.sp, color = RoseError, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun GamesAdminView(
    games: List<GamePracticeEntity>,
    onUploadClick: () -> Unit,
    onDeleteGame: (String) -> Unit,
    onDeleteGamesBySection: (String) -> Unit = {},
    onClearAllGames: () -> Unit = {}
) {
    var showInstructions by remember { mutableStateOf(false) }
    var expandedSectionKey by remember { mutableStateOf<String?>(null) }
    var sectionPendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // key, title
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val gameSections = listOf(
        Triple("odd_one_out", "Odd One Out", "Find the word that does not belong to the set"),
        Triple("analogy", "Analogy Practice", "Solve word relationship and analogy pairs"),
        Triple("practice", "Practice Quiz", "Standard multiple-choice practice questions")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Expandable Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFCBD5E1)))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInstructions = !showInstructions },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Game Data Format & Input Instructions",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }
                    Icon(
                        imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showInstructions) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Formats: Multi-sheet Excel (.xlsx) or CSV (.csv)", fontSize = 11.sp, color = SlateText)
                        Text("• Sheet names: 'odd_one_out', 'analogy', 'practice' (or 'quiz')", fontSize = 11.sp, color = SlateText)
                        Text("• Columns: Question, Opt1, Opt2, Opt3, Opt4, Ans, Explanation", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row: Upload + Bulk Clear All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onUploadClick,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Games File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (games.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoseError)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RoseError)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Three Separate Sections for Games Shown in List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(gameSections, key = { it.first }) { (secKey, secTitle, secDesc) ->
                val sectionItems = games.filter {
                    it.sheetType.equals(secKey, ignoreCase = true) ||
                    (secKey == "practice" && (it.sheetType.equals("quiz", ignoreCase = true) || it.sheetType.isBlank()))
                }
                val isExpanded = expandedSectionKey == secKey

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (secKey) {
                                                "odd_one_out" -> Color(0xFFFEF3C7)
                                                "analogy" -> Color(0xFFEFF6FF)
                                                else -> Color(0xFFF3E8FF)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (secKey) {
                                            "odd_one_out" -> Icons.Default.Shuffle
                                            "analogy" -> Icons.Default.CompareArrows
                                            else -> Icons.Default.SportsEsports
                                        },
                                        contentDescription = null,
                                        tint = when (secKey) {
                                            "odd_one_out" -> Color(0xFFD97706)
                                            "analogy" -> Color(0xFF2563EB)
                                            else -> Color(0xFF9333EA)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = secTitle,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateText
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (sectionItems.isNotEmpty()) IndigoLight else Color(0xFFF1F5F9))
                                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "${sectionItems.size}",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sectionItems.isNotEmpty()) IndigoPrimary else SlateMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = secDesc,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls Row: View Questions toggle + Section Bulk Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    expandedSectionKey = if (isExpanded) null else secKey
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Hide Questions" else "View Questions (${sectionItems.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (sectionItems.isNotEmpty()) {
                                IconButton(
                                    onClick = { sectionPendingDelete = Pair(secKey, secTitle) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Bulk Delete $secTitle",
                                        tint = RoseError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Expanded Questions List
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (sectionItems.isEmpty()) {
                                Text(
                                    text = "No questions in this category. Upload an Excel or CSV file to add questions.",
                                    fontSize = 11.5.sp,
                                    color = SlateMuted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sectionItems.forEachIndexed { idx, item ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${idx + 1}. ${item.question}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SlateText
                                                    )
                                                    Text(
                                                        text = "Ans: ${item.answer}",
                                                        fontSize = 11.sp,
                                                        color = EmeraldSuccess,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteGame(item.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Delete",
                                                        tint = RoseError,
                                                        modifier = Modifier.size(15.dp)
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
        }
    }

    // Confirmation dialog for section bulk delete
    if (sectionPendingDelete != null) {
        val (secKey, secTitle) = sectionPendingDelete!!
        AlertDialog(
            onDismissRequest = { sectionPendingDelete = null },
            title = { Text("Delete All $secTitle?") },
            text = { Text("Are you sure you want to delete all questions in $secTitle? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGamesBySection(secKey)
                        sectionPendingDelete = null
                    }
                ) {
                    Text("Delete All", color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sectionPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for clear all games
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Game Data?") },
            text = { Text("Are you sure you want to delete all ${games.size} questions across all game categories? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllGames()
                        showClearAllConfirm = false
                    }
                ) {
                    Text("Clear All", color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QbAdminView(
    questions: List<QuestionBankEntity>,
    onUploadClick: () -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onClearAllQB: () -> Unit = {}
) {
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredQuestions = remember(questions, searchQuery) {
        if (searchQuery.isBlank()) questions
        else {
            val q = searchQuery.trim().lowercase()
            questions.filter {
                it.question.lowercase().contains(q) ||
                it.answer.lowercase().contains(q) ||
                (it.filter1?.lowercase()?.contains(q) == true) ||
                (it.filter2?.lowercase()?.contains(q) == true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onUploadClick,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload QB File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (questions.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoseError)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RoseError)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (questions.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search question bank...", fontSize = 11.sp, color = SlateLight) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateLight, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateLight, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = SlateBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredQuestions.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = SlateLight, modifier = Modifier.size(36.dp))
                    Text(
                        if (questions.isEmpty()) "Question Bank is Empty" else "No matching questions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SlateText
                    )
                    Text("Upload an Excel or CSV file with QB questions.", fontSize = 12.sp, color = SlateMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredQuestions, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.filter1 ?: "General"} • ${item.filter2 ?: "Medium"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                                Text(
                                    text = item.question,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                                Text(
                                    text = "Ans: ${item.answer}",
                                    fontSize = 12.sp,
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(onClick = { onDeleteQuestion(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Question", tint = RoseError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear Question Bank?") },
            text = { Text("Are you sure you want to delete all ${questions.size} questions from Question Bank? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllQB()
                        showClearAllConfirm = false
                    }
                ) {
                    Text("Clear All", color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UploadGameWithValidationDialog(
    onDismiss: () -> Unit,
    onImportValidItems: (List<GamePracticeEntity>) -> Unit,
    onImportRawContent: (String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var validationSummary by remember { mutableStateOf<GameValidationSummary?>(null) }
    var rawInputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMessage = null
        try {
            var fileName = "Selected file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            selectedFileName = fileName

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val parsedSheets = if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                    FileParsers.parseMultiSheetXlsx(ByteArrayInputStream(bytes))
                } else {
                    // CSV fallback
                    val text = String(bytes)
                    val lines = text.lines().filter { it.isNotBlank() }
                    val rows = lines.map { line ->
                        line.split(",").map { it.trim().trim('\"') }
                    }
                    listOf(ParsedSheet("practice", rows))
                }

                val summary = FileParsers.validateAndParseGameSheets(parsedSheets)
                validationSummary = summary
            }
        } catch (e: Exception) {
            errorMessage = "Failed to parse file: ${e.localizedMessage}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Upload Game Data (Excel / CSV)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
                Text("Multi-sheet validation and anomaly detection", fontSize = 11.sp, color = SlateMuted)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Excel (.xlsx) / CSV File", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RoseLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = RoseError,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // Validation Preview
                validationSummary?.let { summary ->
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "File: ${selectedFileName ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SlateText
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(EmeraldLight)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("${summary.validCount} Valid Items", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                                    }

                                    if (summary.anomalyCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(AmberLight)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("${summary.anomalyCount} Anomalies", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberWarning)
                                        }
                                    }
                                }

                                if (summary.sheetBreakdown.isNotEmpty()) {
                                    Text(
                                        text = "Sheets Detected: " + summary.sheetBreakdown.entries.joinToString(", ") { "${it.key} (${it.value})" },
                                        fontSize = 11.sp,
                                        color = IndigoPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (summary.anomalies.isNotEmpty()) {
                                    Text(
                                        text = "Issues Detected (will be skipped):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    summary.anomalies.take(5).forEach { issue ->
                                        Text(text = "• $issue", fontSize = 10.sp, color = SlateMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(text = "Or paste raw CSV text:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateText)
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        label = { Text("CSV Text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 6
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val summary = validationSummary
                    if (summary != null && summary.validGames.isNotEmpty()) {
                        onImportValidItems(summary.validGames)
                    } else if (rawInputText.isNotBlank()) {
                        onImportRawContent(rawInputText, "practice")
                    }
                },
                enabled = (validationSummary?.validGames?.isNotEmpty() == true) || rawInputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                val count = validationSummary?.validCount ?: 0
                Text(if (count > 0) "Import $count Valid Items" else "Import Content")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onConfirm: (VocabularyWordEntity) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("1") }
    var example by remember { mutableStateOf("") }
    var synonyms by remember { mutableStateOf("") }
    var extraWord by remember { mutableStateOf("") }
    var mnemonic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vocabulary Word", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text("Word *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = meaning,
                        onValueChange = { meaning = it },
                        label = { Text("Meaning * (Bengali or Definition)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text("Group (1, 2, 3...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = example,
                        onValueChange = { example = it },
                        label = { Text("Example Sentence") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = synonyms,
                        onValueChange = { synonyms = it },
                        label = { Text("Synonyms") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = extraWord,
                        onValueChange = { extraWord = it },
                        label = { Text("Derivative / Forms") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = mnemonic,
                        onValueChange = { mnemonic = it },
                        label = { Text("Mnemonic / Trick") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (word.isNotBlank()) {
                        val grpVal = group.trim().ifEmpty { "1" }
                        onConfirm(
                            VocabularyWordEntity(
                                id = "word_${System.currentTimeMillis()}",
                                word = word.trim(),
                                meaning = meaning.trim(),
                                group = grpVal,
                                example = example.trim().ifEmpty { null },
                                synonyms = synonyms.trim().ifEmpty { null },
                                extraWord = extraWord.trim().ifEmpty { null },
                                mnemonic = mnemonic.trim().ifEmpty { null }
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save Word")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UploadFileDialog(
    title: String,
    subtitle: String,
    defaultContent: String,
    initialTitle: String? = null,
    onDismiss: () -> Unit,
    onImport: (String, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    var fileContent by remember { mutableStateOf(defaultContent) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var customTitle by remember(initialTitle) { mutableStateOf(initialTitle ?: "") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        var fileName = "Selected file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            selectedFileName = fileName
            val derivedTitle = fileName.substringBeforeLast(".")
            // Title is automatically filled from file name and editable
            customTitle = derivedTitle

            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                    val rows = FileParsers.parseXlsx(stream)
                    if (rows.isNotEmpty()) {
                        val csv = FileParsers.rowsToCsv(rows)
                        fileContent = csv
                        val count = maxOf(0, rows.size - 1)
                        statusFeedback = "Excel (.xlsx) loaded: $count data rows detected"
                        isSuccessStatus = true
                    } else {
                        statusFeedback = "No data rows found in $fileName"
                        isSuccessStatus = false
                    }
                } else if (fileName.endsWith(".json", ignoreCase = true)) {
                    val text = stream.bufferedReader().readText()
                    fileContent = text
                    statusFeedback = "JSON file loaded ($fileName)"
                    isSuccessStatus = true
                } else {
                    // CSV or text
                    val text = stream.bufferedReader().readText()
                    fileContent = text
                    val count = maxOf(0, text.lines().count { it.isNotBlank() } - 1)
                    statusFeedback = "CSV file loaded: $count data rows detected"
                    isSuccessStatus = true
                }
            }
        } catch (e: Exception) {
            statusFeedback = "Failed to read file: ${e.localizedMessage}"
            isSuccessStatus = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmeraldLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Excel (.xlsx)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(IndigoLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("CSV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AmberLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberWarning)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = subtitle, fontSize = 11.sp, color = SlateMuted)

                // Pick Excel or CSV file button
                Button(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "text/csv",
                                "text/comma-separated-values",
                                "application/json",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Excel (.xlsx) / CSV File", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (selectedFileName != null || statusFeedback != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccessStatus) EmeraldLight else RoseLight
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isSuccessStatus) EmeraldBorder else RoseBorder
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSuccessStatus) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSuccessStatus) EmeraldSuccess else RoseError,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                if (selectedFileName != null) {
                                    Text(
                                        text = selectedFileName ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText
                                    )
                                }
                                if (statusFeedback != null) {
                                    Text(
                                        text = statusFeedback ?: "",
                                        fontSize = 11.sp,
                                        color = if (isSuccessStatus) EmeraldSuccess else RoseError
                                    )
                                }
                            }
                        }
                    }
                }

                // Title is not mandatory, auto-filled from file name, can be edited
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Title (Optional - auto-filled from file)") },
                    placeholder = { Text("Auto-filled from file name (editable)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Or paste / edit file content:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateText
                )

                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    label = { Text("CSV / JSON / Excel text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isJson = fileContent.trim().startsWith("[") || fileContent.trim().startsWith("{")
                    val finalTitle = customTitle.trim().ifEmpty { selectedFileName?.substringBeforeLast(".") }
                    onImport(fileContent, isJson, finalTitle)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Import Content")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditWordDialog(
    word: VocabularyWordEntity,
    onDismiss: () -> Unit,
    onConfirm: (VocabularyWordEntity) -> Unit
) {
    var wordText by remember { mutableStateOf(word.word) }
    var meaning by remember { mutableStateOf(word.meaning) }
    var group by remember { mutableStateOf(word.group) }
    var example by remember { mutableStateOf(word.example ?: "") }
    var synonyms by remember { mutableStateOf(word.synonyms ?: "") }
    var extraWord by remember { mutableStateOf(word.extraWord ?: "") }
    var mnemonic by remember { mutableStateOf(word.mnemonic ?: "") }
    var selectedStatus by remember { mutableStateOf(word.status) }
    var isReported by remember { mutableStateOf(word.isReported) }
    var reportReason by remember { mutableStateOf(word.reportReason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = IndigoPrimary)
                Text("Edit Word", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = wordText,
                        onValueChange = { wordText = it },
                        label = { Text("Word (Place 1) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = meaning,
                        onValueChange = { meaning = it },
                        label = { Text("Meaning (Place 2) *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text("Group") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val statuses = listOf(
                            "unrated" to "Unrated",
                            "dont_know" to "Don't Know",
                            "confusion" to "Confusion",
                            "know" to "Know"
                        )
                        statuses.forEach { (stKey, stLabel) ->
                            FilterChip(
                                selected = selectedStatus.equals(stKey, ignoreCase = true),
                                onClick = { selectedStatus = stKey },
                                label = { Text(stLabel, fontSize = 10.sp) },
                                shape = CircleShape
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = example,
                        onValueChange = { example = it },
                        label = { Text("Example (Place 3)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = synonyms,
                        onValueChange = { synonyms = it },
                        label = { Text("Synonyms (Place 4)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = extraWord,
                        onValueChange = { extraWord = it },
                        label = { Text("Derivative / Forms (Place 5)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = mnemonic,
                        onValueChange = { mnemonic = it },
                        label = { Text("Mnemonic (Place 6)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isReported) Color(0xFFFFF1F2) else SlateBg
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isReported) Color(0xFFFECDD3) else SlateBorder)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = if (isReported) RoseError else SlateLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isReported) "Word is Reported" else "Report Status",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isReported) RoseError else SlateText
                                    )
                                }
                                Switch(
                                    checked = isReported,
                                    onCheckedChange = { isReported = it }
                                )
                            }
                            if (isReported) {
                                OutlinedTextField(
                                    value = reportReason,
                                    onValueChange = { reportReason = it },
                                    label = { Text("Report Reason / Note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (wordText.isNotBlank()) {
                        onConfirm(
                            word.copy(
                                word = wordText.trim(),
                                meaning = meaning.trim(),
                                group = group.trim().ifEmpty { "1" },
                                status = selectedStatus,
                                example = example.trim().ifEmpty { null },
                                synonyms = synonyms.trim().ifEmpty { null },
                                extraWord = extraWord.trim().ifEmpty { null },
                                mnemonic = mnemonic.trim().ifEmpty { null },
                                isReported = isReported,
                                reportReason = if (isReported) reportReason.trim().ifEmpty { null } else null
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditCourseDialog(
    course: CourseEntity,
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String, newDescription: String?) -> Unit
) {
    var title by remember { mutableStateOf(course.title) }
    var description by remember { mutableStateOf(course.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = IndigoPrimary)
                Text("Edit Course", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Course Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), description.trim().ifEmpty { null })
                    }
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
