package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.*
import java.io.ByteArrayInputStream

@Composable
fun AdminPanelScreen(
    words: List<VocabularyWordEntity>,
    games: List<GamePracticeEntity>,
    questions: List<QuestionBankEntity>,
    courses: List<CourseEntity> = emptyList(),
    activeCourseId: String = "",
    onCreateCourse: (String, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onSelectCourse: (String) -> Unit = {},
    onDeleteCourse: (String) -> Unit = {},
    onAddWord: (VocabularyWordEntity) -> Unit,
    onDeleteWord: (String) -> Unit,
    onDeleteGame: (String) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onImportCourse: (String, Boolean, String, String?) -> Unit = { _, _, _, _ -> },
    onImportGame: (String, String) -> Unit,
    onImportGameItems: (List<GamePracticeEntity>) -> Unit = {},
    onImportQB: (String) -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf("courses") } // "courses", "words", "games", "qb"
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var showAddWordDialog by remember { mutableStateOf(false) }
    var showUploadCourseDialog by remember { mutableStateOf(false) }
    var showUploadGameDialog by remember { mutableStateOf(false) }
    var showUploadQBDialog by remember { mutableStateOf(false) }
    var targetCourseIdForUpload by remember(activeCourseId) { mutableStateOf(activeCourseId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Section Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSection == "courses",
                onClick = { selectedSection = "courses" },
                label = { Text("Courses (${courses.size})", fontSize = 11.sp) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoPrimary,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedSection == "words",
                onClick = { selectedSection = "words" },
                label = { Text("Words (${words.size})", fontSize = 11.sp) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoPrimary,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedSection == "games",
                onClick = { selectedSection = "games" },
                label = { Text("Games (${games.size})", fontSize = 11.sp) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoPrimary,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedSection == "qb",
                onClick = { selectedSection = "qb" },
                label = { Text("QB (${questions.size})", fontSize = 11.sp) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoPrimary,
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Download Course Data Banner (Google Drive Link)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldLight),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldBorder))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Download Course Data",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Google Drive", fontFamily = PoppinsFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                        }
                    }
                    Text(
                        text = "Download course spreadsheets and files",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://drive.google.com/drive/folders/1OBqSlB21FD_-0tpRZE8H6R5VFzDkeX2n")
                        )
                        context.startActivity(intent)
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content by Selected Section
        when (selectedSection) {
            "courses" -> {
                CoursesAdminView(
                    courses = courses,
                    activeCourseId = activeCourseId,
                    words = words,
                    onCreateCourseClick = { showCreateCourseDialog = true },
                    onSelectCourse = onSelectCourse,
                    onDeleteCourse = onDeleteCourse,
                    onUploadToCourse = { cId ->
                        targetCourseIdForUpload = cId
                        showUploadCourseDialog = true
                    }
                )
            }
            "words" -> {
                WordsAdminView(
                    words = words,
                    activeCourseTitle = courses.firstOrNull { it.id == activeCourseId }?.title ?: if (activeCourseId.isNotBlank()) "Active Course" else "No Course Selected",
                    onAddWordClick = { showAddWordDialog = true },
                    onUploadClick = {
                        targetCourseIdForUpload = activeCourseId
                        showUploadCourseDialog = true
                    },
                    onDeleteWord = onDeleteWord
                )
            }
            "games" -> {
                GamesAdminView(
                    games = games,
                    onUploadClick = { showUploadGameDialog = true },
                    onDeleteGame = onDeleteGame
                )
            }
            "qb" -> {
                QbAdminView(
                    questions = questions,
                    onUploadClick = { showUploadQBDialog = true },
                    onDeleteQuestion = onDeleteQuestion
                )
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
            defaultContent = "Id,Question,Opt1,Opt2,Opt3,Opt4,Ans,Explanation,Filter1:Category,Filter2:Difficulty,Filter3:Source\n\"qb_101\",\"Identify the appropriate synonym for 'Ephemeral':\",\"Transient#\",\"Eternal\",\"Persistent\",\"Enduring\",\"Transient\",\"Ephemeral means fleeting or transient.\",\"Vocabulary Mastery\",\"Medium\",\"Barron's 333\"",
            onDismiss = { showUploadQBDialog = false },
            onImport = { content, _, _ ->
                onImportQB(content)
                showUploadQBDialog = false
            }
        )
    }
}

@Composable
private fun CoursesAdminView(
    courses: List<CourseEntity>,
    activeCourseId: String,
    words: List<VocabularyWordEntity>,
    onCreateCourseClick: () -> Unit,
    onSelectCourse: (String) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onUploadToCourse: (String) -> Unit
) {
    var coursePendingDelete by remember { mutableStateOf<CourseEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onCreateCourseClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create New Course", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                    val courseWordCount = words.count { it.courseId == course.id }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFFF0FDF4) else Color.White
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isActive) EmeraldSuccess else SlateBorder
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = course.title,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) EmeraldSuccess else SlateText
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

                                IconButton(
                                    onClick = { coursePendingDelete = course },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Course", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
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
                                        onClick = { onUploadToCourse(course.id) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload Excel/CSV", fontSize = 10.sp)
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
    activeCourseTitle: String = "",
    onAddWordClick: () -> Unit,
    onUploadClick: () -> Unit,
    onDeleteWord: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (activeCourseTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Course: ",
                    fontSize = 11.sp,
                    color = SlateLight
                )
                Text(
                    text = activeCourseTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPrimary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddWordClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Word", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onUploadClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload Excel/CSV", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (words.isEmpty()) {
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
                    Icon(Icons.Default.LayersClear, contentDescription = null, tint = SlateLight, modifier = Modifier.size(36.dp))
                    Text("No words found in this course", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SlateText)
                    Text("Upload an Excel (.xlsx) or CSV file with vocab items.", fontSize = 12.sp, color = SlateMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words, key = { it.id }) { word ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
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
                                }
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

                            IconButton(onClick = { onDeleteWord(word.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Word", tint = RoseError)
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
    onDeleteGame: (String) -> Unit
) {
    var showInstructions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Expandable Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFCBD5E1)))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
                        Icon(Icons.Default.Info, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Game Data Format & Input Instructions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }
                    Icon(
                        imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = SlateMuted
                    )
                }

                AnimatedVisibility(visible = showInstructions) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. File Formats: Multi-sheet Excel (.xlsx) or standard CSV (.csv).",
                            fontSize = 11.sp,
                            color = SlateText
                        )
                        Text(
                            text = "2. Sheet Names: 'practice', 'quiz', 'odd_one_out', 'analogy'. Each sheet is automatically categorized by its name.",
                            fontSize = 11.sp,
                            color = SlateText
                        )
                        Text(
                            text = "3. Required Columns: Question, Opt1, Opt2, Opt3, Opt4, Ans, Explanation.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary
                        )
                        Text(
                            text = "4. Correct Answer Indicator: In the 'Ans' column, enter the answer text, option index (1-4), or letter (A-D). Alternatively, append '#' to the correct option text (e.g. 'Abate#') if leaving 'Ans' empty.",
                            fontSize = 11.sp,
                            color = SlateText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Upload Games File with Validation (Excel / CSV)", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (games.isEmpty()) {
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
                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = SlateLight, modifier = Modifier.size(36.dp))
                    Text("No Game Questions Loaded", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SlateText)
                    Text("Upload an Excel file with sheets like 'quiz' or 'practice' to start playing.", fontSize = 12.sp, color = SlateMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.sheetType.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                                Text(
                                    text = item.question,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText
                                )
                                Text(
                                    text = "Answer: ${item.answer}",
                                    fontSize = 12.sp,
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(onClick = { onDeleteGame(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Game", tint = RoseError)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QbAdminView(
    questions: List<QuestionBankEntity>,
    onUploadClick: () -> Unit,
    onDeleteQuestion: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Upload Question Bank File (Excel / CSV)", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (questions.isEmpty()) {
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
                    Text("Question Bank is Empty", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SlateText)
                    Text("Upload an Excel or CSV file with QB questions.", fontSize = 12.sp, color = SlateMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(questions, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
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
                        val grpNum = group.toIntOrNull() ?: 1
                        onConfirm(
                            VocabularyWordEntity(
                                id = "word_${System.currentTimeMillis()}",
                                word = word.trim(),
                                meaning = meaning.trim(),
                                group = grpNum,
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
