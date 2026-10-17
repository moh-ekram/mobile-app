package com.example.ui.screens

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.data.sync.DiscoveredDriveFile
import com.example.ui.theme.*
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun QuestionBankDashboardView(
    questions: List<QuestionBankEntity>,
    driveQBFiles: List<DiscoveredDriveFile> = emptyList(),
    isRefreshingDriveQB: Boolean = false,
    downloadingCourseFileIds: Set<String> = emptySet(),
    onRefreshDriveQB: () -> Unit = {},
    onDownloadDriveQB: (DiscoveredDriveFile) -> Unit = {},
    onStartQuiz: (bankName: String, status: String, f1: Set<String>, f2: Set<String>, f3: Set<String>, count: Int, order: String, viewMode: String) -> Unit,
    onImportQBBytes: (bytes: ByteArray, fileName: String, clearExisting: Boolean) -> Unit,
    onDeleteQuestionsByBank: (bankName: String) -> Unit,
    onRenameBank: (oldBankName: String, newBankName: String) -> Unit,
    onAddSampleData: () -> Unit,
    onBackupQBProgress: () -> Unit = {},
    onRestoreQBProgress: (bytes: ByteArray) -> Unit = {}
) {
    val context = LocalContext.current
    val qbFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                var fileName = "Imported QB"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    onImportQBBytes(bytes, fileName, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val qbProgressFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    onRestoreQBProgress(bytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var configuringBank by remember { mutableStateOf<String?>(null) }
    var renamingBank by remember { mutableStateOf<String?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var deletingBank by remember { mutableStateOf<String?>(null) }
    var showImportCatalogModal by remember { mutableStateOf(false) }

    val banksMap = remember(questions) {
        if (questions.isEmpty()) {
            emptyMap()
        } else {
            questions.groupBy { it.bankName.ifBlank { "General QB" } }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("qb_dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question Bank",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
                IconButton(
                    onClick = { showImportCatalogModal = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag("qb_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Import",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Empty state if no banks
        if (banksMap.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "No Question Banks",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Browse the question bank catalog to install question sets, or load sample banks.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            color = SlateMuted,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showImportCatalogModal = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browse Catalog", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onAddSampleData,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText),
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Load Samples", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Render each bank card with minimal two-color palette
            items(banksMap.entries.toList(), key = { it.key }) { (bankName, bankQuestions) ->
                val totalCount = bankQuestions.size
                val knowCount = bankQuestions.count { it.status.equals("know", true) }
                val confusionCount = bankQuestions.count { it.status.equals("confusion", true) || it.status.equals("review", true) }
                val dontKnowCount = bankQuestions.count { it.status.equals("dont_know", true) }
                val unratedCount = bankQuestions.count { it.status.isBlank() || it.status.equals("unrated", true) }

                val knowPercent = if (totalCount > 0) (knowCount * 100 / totalCount) else 0
                val confusionPercent = if (totalCount > 0) (confusionCount * 100 / totalCount) else 0
                val dontKnowPercent = if (totalCount > 0) (dontKnowCount * 100 / totalCount) else 0
                val unratedPercent = if (totalCount > 0) (unratedCount * 100 / totalCount) else 0

                val knowProgress = if (totalCount > 0) knowCount.toFloat() / totalCount.toFloat() else 0f
                val confusionProgress = if (totalCount > 0) confusionCount.toFloat() / totalCount.toFloat() else 0f
                val dontKnowProgress = if (totalCount > 0) dontKnowCount.toFloat() / totalCount.toFloat() else 0f
                val unratedProgress = if (totalCount > 0) unratedCount.toFloat() / totalCount.toFloat() else 0f

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qb_card_${bankName.lowercase().replace(" ", "_")}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Bank Name, question count, and Setting/Filter Icon Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(IndigoLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = bankName,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$totalCount questions",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.5.sp,
                                        color = SlateMuted
                                    )
                                }
                            }

                            // Setting / Filter Button directly opens setting/filter menu
                            IconButton(
                                onClick = { configuringBank = bankName },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Settings & Filters",
                                    tint = SlateMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Statistics always visible with circular graph same as flashcard statistics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QBStatCircle(
                                title = "Know",
                                count = knowCount,
                                percentage = knowPercent,
                                progress = knowProgress,
                                strokeColor = EmeraldSuccess,
                                textColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onStartQuiz(bankName, "know", emptySet(), emptySet(), emptySet(), 0, "serial", "continuous")
                                }
                            )
                            QBStatCircle(
                                title = "Review",
                                count = confusionCount,
                                percentage = confusionPercent,
                                progress = confusionProgress,
                                strokeColor = AmberWarning,
                                textColor = AmberWarning,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onStartQuiz(bankName, "confusion", emptySet(), emptySet(), emptySet(), 0, "serial", "continuous")
                                }
                            )
                            QBStatCircle(
                                title = "Don't Know",
                                count = dontKnowCount,
                                percentage = dontKnowPercent,
                                progress = dontKnowProgress,
                                strokeColor = RoseError,
                                textColor = RoseError,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onStartQuiz(bankName, "dont_know", emptySet(), emptySet(), emptySet(), 0, "serial", "continuous")
                                }
                            )
                            QBStatCircle(
                                title = "Unrated",
                                count = unratedCount,
                                percentage = unratedPercent,
                                progress = unratedProgress,
                                strokeColor = IndigoPrimary,
                                textColor = SlateText,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onStartQuiz(bankName, "unrated", emptySet(), emptySet(), emptySet(), 0, "serial", "continuous")
                                }
                            )
                        }

                        // Practice Button: Longer and distinct modern vibrant color
                        Button(
                            onClick = {
                                onStartQuiz(bankName, "all", emptySet(), emptySet(), emptySet(), 0, "serial", "continuous")
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Practice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = PoppinsFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showImportCatalogModal) {
        QuestionBankCatalogModal(
            driveQBFiles = driveQBFiles,
            banksMap = banksMap,
            downloadingCourseFileIds = downloadingCourseFileIds,
            isRefreshingDriveQB = isRefreshingDriveQB,
            onRefreshDriveQB = onRefreshDriveQB,
            onDownloadDriveQB = onDownloadDriveQB,
            onPickDeviceFile = {
                qbFilePicker.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/comma-separated-values",
                    "text/tab-separated-values",
                    "application/csv",
                    "text/plain",
                    "application/json",
                    "*/*"
                ))
            },
            onDismiss = { showImportCatalogModal = false }
        )
    }

    // Rename Dialog
    if (renamingBank != null) {
        val target = renamingBank!!
        AlertDialog(
            onDismissRequest = { renamingBank = null },
            title = {
                Text(
                    text = "Rename Bank",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SlateText
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    placeholder = { Text("Enter bank name...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = SlateBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = renameInputText.trim()
                        if (cleaned.isNotBlank() && cleaned != target) {
                            onRenameBank(target, cleaned)
                        }
                        renamingBank = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingBank = null }) {
                    Text("Cancel", color = SlateMuted)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    // Delete Confirmation Dialog
    if (deletingBank != null) {
        val target = deletingBank!!
        AlertDialog(
            onDismissRequest = { deletingBank = null },
            title = {
                Text(
                    text = "Delete Bank",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SlateText
                )
            },
            text = {
                Text(
                    text = "Delete all questions in '$target'?",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.5.sp,
                    color = SlateMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteQuestionsByBank(target)
                        deletingBank = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBank = null }) {
                    Text("Cancel", color = SlateMuted)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    // Configure & Filter Modal Dialog for specific bank
    if (configuringBank != null) {
        val target = configuringBank!!
        val bankQuestions = remember(questions, target) {
            questions.filter { it.bankName.equals(target, ignoreCase = true) }
        }
        val qbPrefs = remember(context) { context.getSharedPreferences("qb_typography_settings", Context.MODE_PRIVATE) }
        var questionOrder by remember(target) { mutableStateOf(qbPrefs.getString("qb_question_order", "serial") ?: "serial") }
        var quizViewMode by remember(target) { mutableStateOf(qbPrefs.getString("qb_view_mode", "continuous") ?: "continuous") }
        var selectedStatus by remember(target) { mutableStateOf("all") }
        var selectedF1 by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
        var selectedF2 by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
        var selectedF3 by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
        var questionCount by remember(target, bankQuestions) { mutableIntStateOf(qbPrefs.getInt("qb_question_count", 0)) }
        var qSize by remember(target) { mutableFloatStateOf(qbPrefs.getFloat("qb_q_font_size", 16f)) }

        Dialog(onDismissRequest = { configuringBank = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = target,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SlateText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { configuringBank = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    QuestionBankConfigView(
                        questions = bankQuestions,
                        selectedStatus = selectedStatus,
                        onStatusChange = { selectedStatus = it },
                        questionOrder = questionOrder,
                        onOrderChange = { questionOrder = it },
                        quizViewMode = quizViewMode,
                        onViewModeChange = { quizViewMode = it },
                        selectedFilters1 = selectedF1,
                        selectedFilters2 = selectedF2,
                        selectedFilters3 = selectedF3,
                        questionCount = questionCount,
                        fontSize = qSize,
                        onFontSizeChange = { qSize = it },
                        onFilter1Toggle = { opt ->
                            selectedF1 = if (opt in selectedF1) selectedF1 - opt else selectedF1 + opt
                        },
                        onFilter1SelectAll = {
                            selectedF1 = bankQuestions.mapNotNull { it.filter1 }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        },
                        onFilter1Clear = { selectedF1 = emptySet() },
                        onFilter2Toggle = { opt ->
                            selectedF2 = if (opt in selectedF2) selectedF2 - opt else selectedF2 + opt
                        },
                        onFilter2SelectAll = {
                            selectedF2 = bankQuestions.mapNotNull { it.filter2 }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        },
                        onFilter2Clear = { selectedF2 = emptySet() },
                        onFilter3Toggle = { opt ->
                            selectedF3 = if (opt in selectedF3) selectedF3 - opt else selectedF3 + opt
                        },
                        onFilter3SelectAll = {
                            selectedF3 = bankQuestions.mapNotNull { it.filter3 }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        },
                        onFilter3Clear = { selectedF3 = emptySet() },
                        onCountChange = { questionCount = it },
                        onDeleteBank = {
                            val toDelete = target
                            configuringBank = null
                            deletingBank = toDelete
                        },
                        onStart = {
                            qbPrefs.edit()
                                .putString("qb_question_order", questionOrder)
                                .putString("qb_view_mode", quizViewMode)
                                .putInt("qb_question_count", questionCount)
                                .putFloat("qb_q_font_size", qSize)
                                .apply()
                            onStartQuiz(target, selectedStatus, selectedF1, selectedF2, selectedF3, questionCount, questionOrder, quizViewMode)
                            configuringBank = null
                        }
                    )
                }
            }
        }
    }
}


@Composable
internal fun QBStatCircle(
    title: String,
    count: Int,
    percentage: Int,
    progress: Float,
    strokeColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 4.2.dp.toPx()
                val radius = (size.minDimension - strokeWidthPx) / 2f
                val centerOffset = center

                // 1. Solid white filled center circle
                val innerRadius = radius - (strokeWidthPx / 2f) + 0.5f
                drawCircle(
                    color = Color.White,
                    radius = maxOf(0f, innerRadius),
                    center = centerOffset
                )

                // 2. Inactive background track ring
                drawCircle(
                    color = strokeColor.copy(alpha = 0.15f),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )

                // 3. Active circular percentage arc
                if (progress > 0f) {
                    val sweepAngle = (progress.coerceIn(0f, 1f)) * 360f
                    drawArc(
                        color = strokeColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }

            // In the center: count number and percentage
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = count.toString(),
                    fontFamily = PoppinsFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
                Text(
                    text = "$percentage%",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = strokeColor.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = title,
            fontFamily = PoppinsFontFamily,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = SlateText,
            maxLines = 1
        )
    }
}


@Composable
internal fun QBStatCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                fontFamily = PoppinsFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SlateText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMuted,
                maxLines = 1
            )
        }
    }
}


@Composable
internal fun SheetFilterCategoryButton(
    label: String,
    selectedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFilterActive = selectedCount > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isFilterActive) IndigoLight else Color(0xFFF8FAFC),
        border = BorderStroke(
            1.dp,
            if (isFilterActive) IndigoPrimary else SlateBorder
        ),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = if (isFilterActive) IndigoPrimary else SlateMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isFilterActive) "${label.take(7)} ($selectedCount)" else label.take(9),
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isFilterActive) IndigoPrimary else SlateText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DriveQBCatalogCard(
    item: DiscoveredDriveFile,
    cleanTitle: String,
    isAlreadyAdded: Boolean,
    isDownloading: Boolean,
    questionCount: Int? = null,
    onAddToList: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAlreadyAdded) Color(0xFFDCFCE7) else IndigoLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAlreadyAdded) Icons.Default.CheckCircle else Icons.Default.Quiz,
                        contentDescription = null,
                        tint = if (isAlreadyAdded) Color(0xFF15803D) else IndigoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = cleanTitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isAlreadyAdded && questionCount != null) {
                        Text(
                            text = "$questionCount questions",
                            fontSize = 11.sp,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = IndigoPrimary
                )
            } else if (!isAlreadyAdded) {
                Button(
                    onClick = onAddToList,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("add_qb_${item.fileId}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Added",
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBankCatalogModal(
    driveQBFiles: List<DiscoveredDriveFile>,
    banksMap: Map<String, List<QuestionBankEntity>>,
    downloadingCourseFileIds: Set<String>,
    isRefreshingDriveQB: Boolean,
    onRefreshDriveQB: () -> Unit,
    onDownloadDriveQB: (DiscoveredDriveFile) -> Unit,
    onPickDeviceFile: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Question Bank Catalog",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = SlateText
                        )
                        Text(
                            text = "Processed in backend. Click Add to install.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            color = SlateMuted
                        )
                    }
                    IconButton(
                        onClick = onRefreshDriveQB,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        if (isRefreshingDriveQB) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = IndigoPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = SlateBorder)

                // List of Catalog Files
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(driveQBFiles.distinctBy { it.fileId }, key = { "modal_qb_${it.fileId}" }) { item ->
                        val cleanTitle = item.title
                            .substringBeforeLast(".")
                            .replace("_", " ")
                            .replace("-", " ")
                            .trim()
                        val isDownloading = item.fileId in downloadingCourseFileIds
                        val isAlreadyAdded = banksMap.keys.any { installedBank ->
                            installedBank.equals(cleanTitle, ignoreCase = true) ||
                            installedBank.equals(item.title.substringBeforeLast("."), ignoreCase = true) ||
                            installedBank.contains(cleanTitle, ignoreCase = true)
                        }
                        val bankQuestionCount = if (isAlreadyAdded) {
                            banksMap.entries.firstOrNull {
                                it.key.equals(cleanTitle, ignoreCase = true) ||
                                it.key.contains(cleanTitle, ignoreCase = true)
                            }?.value?.size
                        } else null

                        DriveQBCatalogCard(
                            item = item,
                            cleanTitle = cleanTitle,
                            isAlreadyAdded = isAlreadyAdded,
                            isDownloading = isDownloading,
                            questionCount = bankQuestionCount,
                            onAddToList = { onDownloadDriveQB(item) }
                        )
                    }
                }

                HorizontalDivider(color = SlateBorder)

                // Custom Device Import option
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPickDeviceFile()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import custom file from device",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text(
                        text = "Close",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
internal fun QuestionBankConfigView(
    questions: List<QuestionBankEntity>,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    questionOrder: String,
    onOrderChange: (String) -> Unit,
    quizViewMode: String,
    onViewModeChange: (String) -> Unit,
    selectedFilters1: Set<String>,
    selectedFilters2: Set<String>,
    selectedFilters3: Set<String>,
    questionCount: Int,
    fontSize: Float = 16f,
    onFontSizeChange: (Float) -> Unit = {},
    onFilter1Toggle: (String) -> Unit,
    onFilter1SelectAll: () -> Unit = {},
    onFilter1Clear: () -> Unit,
    onFilter2Toggle: (String) -> Unit,
    onFilter2SelectAll: () -> Unit = {},
    onFilter2Clear: () -> Unit,
    onFilter3Toggle: (String) -> Unit,
    onFilter3SelectAll: () -> Unit = {},
    onFilter3Clear: () -> Unit,
    onCountChange: (Int) -> Unit,
    onDeleteBank: (() -> Unit)? = null,
    onStart: () -> Unit
) {
    val f1Label = questions.mapNotNull { it.filter1Label }.firstOrNull { it.isNotBlank() }
        ?: questions.firstOrNull()?.filter1Label?.ifBlank { "Course" } ?: "Course"
    val f2Label = questions.mapNotNull { it.filter2Label }.firstOrNull { it.isNotBlank() }
        ?: questions.firstOrNull()?.filter2Label?.ifBlank { "Q.type" } ?: "Q.type"
    val f3Label = questions.mapNotNull { it.filter3Label }.firstOrNull { it.isNotBlank() }
        ?: questions.firstOrNull()?.filter3Label?.ifBlank { "Session" } ?: "Session"

    val f1Values = remember(questions) {
        questions.mapNotNull { it.filter1 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val f2Values = remember(questions) {
        questions.mapNotNull { it.filter2 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val f3Values = remember(questions) {
        questions.mapNotNull { it.filter3 }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val matchedCount = remember(questions, selectedStatus, selectedFilters1, selectedFilters2, selectedFilters3) {
        questions.count { q ->
            val statusMatches = when (selectedStatus) {
                "all" -> true
                "know" -> (q.status ?: "").trim().lowercase() == "know"
                "confusion" -> (q.status ?: "").trim().lowercase() in listOf("confusion", "review")
                "dont_know" -> (q.status ?: "").trim().lowercase() == "dont_know"
                "unrated" -> (q.status ?: "").trim().isBlank() || (q.status ?: "").trim().lowercase() == "unrated"
                else -> true
            }
            statusMatches &&
            (selectedFilters1.isEmpty() || (q.filter1 != null && q.filter1 in selectedFilters1)) &&
            (selectedFilters2.isEmpty() || (q.filter2 != null && q.filter2 in selectedFilters2)) &&
            (selectedFilters3.isEmpty() || (q.filter3 != null && q.filter3 in selectedFilters3))
        }
    }

    var isGridFilterDialogOpen by remember { mutableStateOf(false) }
    var activeGridTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // 1. Question Order & View Mode (Side-by-side compact dropdown row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order Dropdown
            var orderMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    onClick = { orderMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Order:",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = if (questionOrder == "random") "Random" else "Serial",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateText
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = orderMenuExpanded,
                    onDismissRequest = { orderMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Serial Order", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                        onClick = {
                            onOrderChange("serial")
                            orderMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Random Order", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                        onClick = {
                            onOrderChange("random")
                            orderMenuExpanded = false
                        }
                    )
                }
            }

            // View Mode Dropdown
            var viewMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    onClick = { viewMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "View:",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = if (quizViewMode == "one_by_one") "1-by-1" else "Scroll",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateText
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = viewMenuExpanded,
                    onDismissRequest = { viewMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Scroll View", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                        onClick = {
                            onViewModeChange("continuous")
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("1-by-1 View", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                        onClick = {
                            onViewModeChange("one_by_one")
                            viewMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 2. Status Filter (5 iconized buttons)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Status",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMuted
            )

            val statusFilterIcons = listOf(
                Triple("all", Icons.Default.DoneAll, IndigoPrimary),
                Triple("know", Icons.Default.CheckCircle, EmeraldSuccess),
                Triple("confusion", Icons.Default.Psychology, AmberWarning),
                Triple("dont_know", Icons.Default.Cancel, RoseError),
                Triple("unrated", Icons.Default.HelpOutline, SlateText)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statusFilterIcons.forEach { (stKey, icon, brandColor) ->
                    val isSelected = selectedStatus == stKey

                    Surface(
                        onClick = { onStatusChange(stKey) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) brandColor else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) brandColor else Color(0xFFE2E8F0)
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
                                contentDescription = stKey,
                                modifier = Modifier.size(19.dp),
                                tint = if (isSelected) Color.White else brandColor
                            )
                        }
                    }
                }
            }
        }

        // 3. Sheet Filters
        if (f1Values.isNotEmpty() || f2Values.isNotEmpty() || f3Values.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sheet Filters",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMuted
                    )
                    if (selectedFilters1.isNotEmpty() || selectedFilters2.isNotEmpty() || selectedFilters3.isNotEmpty()) {
                        Text(
                            text = "Reset All",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary,
                            modifier = Modifier.clickable {
                                onFilter1Clear()
                                onFilter2Clear()
                                onFilter3Clear()
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SheetFilterCategoryButton(
                        label = f1Label,
                        selectedCount = selectedFilters1.size,
                        onClick = {
                            activeGridTab = 0
                            isGridFilterDialogOpen = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SheetFilterCategoryButton(
                        label = f2Label,
                        selectedCount = selectedFilters2.size,
                        onClick = {
                            activeGridTab = 1
                            isGridFilterDialogOpen = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SheetFilterCategoryButton(
                        label = f3Label,
                        selectedCount = selectedFilters3.size,
                        onClick = {
                            activeGridTab = 2
                            isGridFilterDialogOpen = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Questions Count Selector (5, 10, 15, +5, All)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Questions",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(5, 10, 15).forEach { opt ->
                    val isSelected = questionCount == opt
                    Surface(
                        onClick = { onCountChange(opt) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) IndigoPrimary else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else SlateBorder)
                    ) {
                        Text(
                            text = "$opt",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else SlateText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Show badge if count is custom and > 15
                if (questionCount > 15) {
                    Surface(
                        onClick = {},
                        shape = RoundedCornerShape(8.dp),
                        color = IndigoPrimary,
                        border = BorderStroke(1.dp, IndigoPrimary)
                    ) {
                        Text(
                            text = "$questionCount",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // +5 Button
                Surface(
                    onClick = {
                        val current = if (questionCount > 0) questionCount else 15
                        val next = if (matchedCount > 0) (current + 5).coerceAtMost(matchedCount) else current + 5
                        onCountChange(next)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = "+5",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }

                // All Button
                val isAllSelected = questionCount <= 0 || (matchedCount > 0 && questionCount >= matchedCount)
                Surface(
                    onClick = { onCountChange(0) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) IndigoPrimary else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (isAllSelected) IndigoPrimary else SlateBorder)
                ) {
                    Text(
                        text = if (matchedCount > 0) "All ($matchedCount)" else "All",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllSelected) Color.White else SlateText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 5. Start Practice Button with count info
        val effectiveCount = if (questionCount <= 0 || questionCount > matchedCount) matchedCount else questionCount
        Button(
            onClick = onStart,
            enabled = matchedCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("start_qb_test_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
        ) {
            Text(
                text = if (matchedCount > 0) "Start Practice ($effectiveCount of $matchedCount Qs)" else "No Questions Match",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // 6. Delete QB File Option (Positioned at bottom to prevent accidental clicks)
        if (onDeleteBank != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDeleteBank,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete QB File",
                        tint = RoseError.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Delete Question Bank",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = RoseError.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (isGridFilterDialogOpen) {
        SheetFilterGridSelectionDialog(
            initialTab = activeGridTab,
            f1Label = f1Label,
            f1Values = f1Values,
            selectedFilters1 = selectedFilters1,
            onFilter1Toggle = onFilter1Toggle,
            onFilter1SelectAll = onFilter1SelectAll,
            onFilter1Clear = onFilter1Clear,
            f2Label = f2Label,
            f2Values = f2Values,
            selectedFilters2 = selectedFilters2,
            onFilter2Toggle = onFilter2Toggle,
            onFilter2SelectAll = onFilter2SelectAll,
            onFilter2Clear = onFilter2Clear,
            f3Label = f3Label,
            f3Values = f3Values,
            selectedFilters3 = selectedFilters3,
            onFilter3Toggle = onFilter3Toggle,
            onFilter3SelectAll = onFilter3SelectAll,
            onFilter3Clear = onFilter3Clear,
            onDismiss = { isGridFilterDialogOpen = false }
        )
    }
}


@Composable
internal fun CompactFilterMenuButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    options: List<String>,
    selectedOptions: Set<String>,
    onToggleOption: (String) -> Unit,
    onClearOption: () -> Unit,
    onOpenGrid: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isFilterActive = selectedOptions.isNotEmpty()

    Box(modifier = modifier) {
        Surface(
            onClick = { if (options.isNotEmpty()) expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = if (isFilterActive) IndigoLight else Color(0xFFF8FAFC),
            border = BorderStroke(
                1.dp,
                if (isFilterActive) IndigoPrimary else SlateBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isFilterActive) IndigoPrimary else SlateMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isFilterActive) "${label.take(7)} (${selectedOptions.size})" else label.take(9),
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFilterActive) IndigoPrimary else SlateText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 180.dp, max = 280.dp)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPrimary
                )
                if (isFilterActive) {
                    Text(
                        text = "Clear",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseError,
                        modifier = Modifier.clickable {
                            onClearOption()
                            expanded = false
                        }
                    )
                }
            }
            HorizontalDivider(color = SlateBorder)
            if (onOpenGrid != null) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Select in Grid View",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onOpenGrid()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                )
                HorizontalDivider(color = SlateBorder)
            }
            options.forEach { opt ->
                val isChecked = opt in selectedOptions
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleOption(opt) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = IndigoPrimary,
                                    uncheckedColor = SlateBorder
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = opt,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = SlateText
                            )
                        }
                    },
                    onClick = { onToggleOption(opt) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}


@Composable
internal fun QuestionBankCountDropdown(
    questionCount: Int,
    maxAvailable: Int,
    onCountChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val baseOptions = listOf(5, 10, 15, 20, 25, 30)
    val countOptions = (baseOptions + listOf(maxAvailable).filter { it > 0 }).distinct().sorted()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Quiz, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(15.dp))
            Text(
                text = "Number of Questions",
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SlateText
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                color = if (expanded) IndigoLight.copy(alpha = 0.45f) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (expanded) IndigoPrimary else SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = if (questionCount >= maxAvailable && maxAvailable > 0) {
                        "All Available ($maxAvailable Questions)"
                    } else {
                        "$questionCount Questions"
                    }

                    Text(
                        text = countText,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoPrimary
                    )

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select question count",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color.White)
            ) {
                countOptions.forEach { cnt ->
                    val isChecked = (questionCount == cnt)
                    val label = if (cnt == maxAvailable && maxAvailable > 0 && maxAvailable !in baseOptions) {
                        "All Available Questions ($cnt)"
                    } else {
                        "$cnt Questions"
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isChecked) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = label,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChecked) IndigoPrimary else SlateText
                                )
                            }
                        },
                        onClick = {
                            onCountChange(cnt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


private data class SheetFilterTabInfo(
    val id: Int,
    val label: String,
    val options: List<String>,
    val selected: Set<String>,
    val onToggle: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onClear: () -> Unit
)

@Composable
internal fun SheetFilterGridSelectionDialog(
    initialTab: Int = 0,
    f1Label: String,
    f1Values: List<String>,
    selectedFilters1: Set<String>,
    onFilter1Toggle: (String) -> Unit,
    onFilter1SelectAll: () -> Unit = {},
    onFilter1Clear: () -> Unit = {},
    f2Label: String,
    f2Values: List<String>,
    selectedFilters2: Set<String>,
    onFilter2Toggle: (String) -> Unit,
    onFilter2SelectAll: () -> Unit = {},
    onFilter2Clear: () -> Unit = {},
    f3Label: String,
    f3Values: List<String>,
    selectedFilters3: Set<String>,
    onFilter3Toggle: (String) -> Unit,
    onFilter3SelectAll: () -> Unit = {},
    onFilter3Clear: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val tabs = remember(
        f1Label, f1Values, selectedFilters1,
        f2Label, f2Values, selectedFilters2,
        f3Label, f3Values, selectedFilters3
    ) {
        val list = mutableListOf<SheetFilterTabInfo>()
        if (f1Values.isNotEmpty()) {
            list.add(SheetFilterTabInfo(0, f1Label, f1Values, selectedFilters1, onFilter1Toggle, onFilter1SelectAll, onFilter1Clear))
        }
        if (f2Values.isNotEmpty()) {
            list.add(SheetFilterTabInfo(1, f2Label, f2Values, selectedFilters2, onFilter2Toggle, onFilter2SelectAll, onFilter2Clear))
        }
        if (f3Values.isNotEmpty()) {
            list.add(SheetFilterTabInfo(2, f3Label, f3Values, selectedFilters3, onFilter3Toggle, onFilter3SelectAll, onFilter3Clear))
        }
        list
    }

    if (tabs.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var selectedTabIndex by remember(initialTab) {
        val targetIdx = tabs.indexOfFirst { it.id == initialTab }
        mutableIntStateOf(if (targetIdx >= 0) targetIdx else 0)
    }
    val currentTab = tabs.getOrElse(selectedTabIndex) { tabs[0] }
    var searchQuery by remember(currentTab.id) { mutableStateOf("") }

    val filteredOptions = remember(currentTab.options, searchQuery) {
        if (searchQuery.isBlank()) currentTab.options
        else currentTab.options.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Sheet Filter Grid",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Category Tabs (if more than 1 category)
                if (tabs.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEachIndexed { idx, tab ->
                            val isSelected = (idx == selectedTabIndex)
                            Surface(
                                onClick = {
                                    selectedTabIndex = idx
                                    searchQuery = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IndigoPrimary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (tab.selected.isNotEmpty()) "${tab.label} (${tab.selected.size})" else tab.label,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SlateText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Search box if more than 4 items
                if (currentTab.options.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search ${currentTab.label}...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = SlateMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }

                // Summary and Quick Actions: Select All & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentTab.selected.size} of ${currentTab.options.size} selected",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentTab.selected.isNotEmpty()) IndigoPrimary else SlateMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Select All",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary,
                            modifier = Modifier.clickable { currentTab.onSelectAll() }
                        )
                        if (currentTab.selected.isNotEmpty()) {
                            Text(
                                text = "Clear",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseError,
                                modifier = Modifier.clickable { currentTab.onClear() }
                            )
                        }
                    }
                }

                // The 2-Column Grid of Option Chips
                if (filteredOptions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No options match \"$searchQuery\"" else "No options available",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 300.dp)
                    ) {
                        items(filteredOptions.size) { index ->
                            val opt = filteredOptions[index]
                            val isChecked = opt in currentTab.selected
                            Surface(
                                onClick = { currentTab.onToggle(opt) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) IndigoLight else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    if (isChecked) 1.5.dp else 1.dp,
                                    if (isChecked) IndigoPrimary else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = if (isChecked) IndigoPrimary else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = opt,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecked) IndigoPrimary else SlateText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Done Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "Apply & Done",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun QbTypographyFilterDialog(
    questionFontSize: Float,
    answerFontSize: Float,
    explanationFontSize: Float,
    questionFont: String,
    answerFont: String,
    explanationFont: String,
    questionOrder: String = "serial",
    onOrderChange: (String) -> Unit = {},
    quizViewMode: String = "continuous",
    onViewModeChange: (String) -> Unit = {},
    questionCount: Int = 0,
    onQuestionCountChange: (Int) -> Unit = {},
    totalAvailableQuestions: Int = 0,
    selectedStatuses: Set<String> = emptySet(),
    onStatusToggle: (String) -> Unit = {},
    onStatusClear: () -> Unit = {},
    f1Label: String = "Course",
    f1Values: List<String> = emptyList(),
    selectedFilters1: Set<String> = emptySet(),
    onFilter1Toggle: (String) -> Unit = {},
    onFilter1SelectAll: () -> Unit = {},
    onFilter1Clear: () -> Unit = {},
    f2Label: String = "Q.type",
    f2Values: List<String> = emptyList(),
    selectedFilters2: Set<String> = emptySet(),
    onFilter2Toggle: (String) -> Unit = {},
    onFilter2SelectAll: () -> Unit = {},
    onFilter2Clear: () -> Unit = {},
    f3Label: String = "Session",
    f3Values: List<String> = emptyList(),
    selectedFilters3: Set<String> = emptySet(),
    onFilter3Toggle: (String) -> Unit = {},
    onFilter3SelectAll: () -> Unit = {},
    onFilter3Clear: () -> Unit = {},
    onApplySettings: (qSize: Float, aSize: Float, eSize: Float, qFont: String, aFont: String, eFont: String) -> Unit,
    onDismiss: () -> Unit
) {
    var qSize by remember { mutableFloatStateOf(questionFontSize) }
    var aSize by remember { mutableFloatStateOf(answerFontSize) }
    var eSize by remember { mutableFloatStateOf(explanationFontSize) }
    var qFontState by remember { mutableStateOf(questionFont) }
    var aFontState by remember { mutableStateOf(answerFont) }
    var eFontState by remember { mutableStateOf(explanationFont) }
    var isGridFilterDialogOpen by remember { mutableStateOf(false) }
    var activeGridTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = {
            onApplySettings(qSize, aSize, eSize, qFontState, aFontState, eFontState)
            onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Minimal close button on top right, no redundant title text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onApplySettings(qSize, aSize, eSize, qFontState, aFontState, eFontState)
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1. Question Order & View Mode (Side-by-side compact dropdown row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Order Dropdown
                    var orderMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { orderMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Order:",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = SlateMuted
                                    )
                                    Text(
                                        text = if (questionOrder == "random") "Random" else "Serial",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SlateText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = SlateMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = orderMenuExpanded,
                            onDismissRequest = { orderMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Serial Order", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                                onClick = {
                                    onOrderChange("serial")
                                    orderMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Random Order", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                                onClick = {
                                    onOrderChange("random")
                                    orderMenuExpanded = false
                                }
                            )
                        }
                    }

                    // View Mode Dropdown
                    var viewMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { viewMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "View:",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = SlateMuted
                                    )
                                    Text(
                                        text = if (quizViewMode == "one_by_one") "1-by-1" else "Scroll",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SlateText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = SlateMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = viewMenuExpanded,
                            onDismissRequest = { viewMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Scroll View", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                                onClick = {
                                    onViewModeChange("continuous")
                                    viewMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("1-by-1 View", fontFamily = PoppinsFontFamily, fontSize = 12.sp) },
                                onClick = {
                                    onViewModeChange("one_by_one")
                                    viewMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Questions Count Selector (5, 10, 15, +5, All)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Questions",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(5, 10, 15).forEach { opt ->
                            val isSelected = questionCount == opt
                            Surface(
                                onClick = { onQuestionCountChange(opt) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IndigoPrimary else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else SlateBorder)
                            ) {
                                Text(
                                    text = "$opt",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else SlateText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (questionCount > 15) {
                            Surface(
                                onClick = {},
                                shape = RoundedCornerShape(8.dp),
                                color = IndigoPrimary,
                                border = BorderStroke(1.dp, IndigoPrimary)
                            ) {
                                Text(
                                    text = "$questionCount",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // +5 Button
                        Surface(
                            onClick = {
                                val current = if (questionCount > 0) questionCount else 15
                                val next = if (totalAvailableQuestions > 0) (current + 5).coerceAtMost(totalAvailableQuestions) else current + 5
                                onQuestionCountChange(next)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = "+5",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }

                        // All Button
                        val isAllSelected = questionCount <= 0 || (totalAvailableQuestions > 0 && questionCount >= totalAvailableQuestions)
                        Surface(
                            onClick = { onQuestionCountChange(0) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAllSelected) IndigoPrimary else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isAllSelected) IndigoPrimary else SlateBorder)
                        ) {
                            Text(
                                text = if (totalAvailableQuestions > 0) "All ($totalAvailableQuestions)" else "All",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSelected) Color.White else SlateText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 4. Status Category (Iconized buttons matching Flashcard/Widget control)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateMuted
                        )
                        if (selectedStatuses.isNotEmpty() && "all" !in selectedStatuses) {
                            Text(
                                text = "Clear",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary,
                                modifier = Modifier.clickable { onStatusClear() }
                            )
                        }
                    }

                    val statusFilterIcons = listOf(
                        Triple("all", Icons.Default.DoneAll, IndigoPrimary),
                        Triple("know", Icons.Default.CheckCircle, EmeraldSuccess),
                        Triple("confusion", Icons.Default.Psychology, AmberWarning),
                        Triple("dont_know", Icons.Default.Cancel, RoseError),
                        Triple("unrated", Icons.Default.HelpOutline, SlateText)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statusFilterIcons.forEach { (stKey, icon, brandColor) ->
                            val isSelected = if (stKey == "all") {
                                selectedStatuses.isEmpty() || "all" in selectedStatuses
                            } else {
                                stKey in selectedStatuses && "all" !in selectedStatuses
                            }

                            Surface(
                                onClick = {
                                    if (stKey == "all") {
                                        onStatusClear()
                                    } else {
                                        onStatusToggle(stKey)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) brandColor else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) brandColor else Color(0xFFE2E8F0)
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
                                        contentDescription = stKey,
                                        modifier = Modifier.size(19.dp),
                                        tint = if (isSelected) Color.White else brandColor
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Sheet Filters
                if (f1Values.isNotEmpty() || f2Values.isNotEmpty() || f3Values.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sheet Filters",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateMuted
                            )
                            if (selectedFilters1.isNotEmpty() || selectedFilters2.isNotEmpty() || selectedFilters3.isNotEmpty()) {
                                Text(
                                    text = "Reset All",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndigoPrimary,
                                    modifier = Modifier.clickable {
                                        onFilter1Clear()
                                        onFilter2Clear()
                                        onFilter3Clear()
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SheetFilterCategoryButton(
                                label = f1Label,
                                selectedCount = selectedFilters1.size,
                                onClick = {
                                    activeGridTab = 0
                                    isGridFilterDialogOpen = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SheetFilterCategoryButton(
                                label = f2Label,
                                selectedCount = selectedFilters2.size,
                                onClick = {
                                    activeGridTab = 1
                                    isGridFilterDialogOpen = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SheetFilterCategoryButton(
                                label = f3Label,
                                selectedCount = selectedFilters3.size,
                                onClick = {
                                    activeGridTab = 2
                                    isGridFilterDialogOpen = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action Buttons: Reset Defaults & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            qSize = 16f
                            aSize = 14f
                            eSize = 12f
                            qFontState = "auto"
                            aFontState = "auto"
                            eFontState = "auto"
                            onStatusClear()
                            onFilter1Clear()
                            onFilter2Clear()
                            onFilter3Clear()
                            onOrderChange("serial")
                        }
                    ) {
                        Text(
                            text = "Reset Defaults",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }

                    Button(
                        onClick = {
                            onApplySettings(qSize, aSize, eSize, qFontState, aFontState, eFontState)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Done",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }

    if (isGridFilterDialogOpen) {
        SheetFilterGridSelectionDialog(
            initialTab = activeGridTab,
            f1Label = f1Label,
            f1Values = f1Values,
            selectedFilters1 = selectedFilters1,
            onFilter1Toggle = onFilter1Toggle,
            onFilter1SelectAll = onFilter1SelectAll,
            onFilter1Clear = onFilter1Clear,
            f2Label = f2Label,
            f2Values = f2Values,
            selectedFilters2 = selectedFilters2,
            onFilter2Toggle = onFilter2Toggle,
            onFilter2SelectAll = onFilter2SelectAll,
            onFilter2Clear = onFilter2Clear,
            f3Label = f3Label,
            f3Values = f3Values,
            selectedFilters3 = selectedFilters3,
            onFilter3Toggle = onFilter3Toggle,
            onFilter3SelectAll = onFilter3SelectAll,
            onFilter3Clear = onFilter3Clear,
            onDismiss = { isGridFilterDialogOpen = false }
        )
    }
}

