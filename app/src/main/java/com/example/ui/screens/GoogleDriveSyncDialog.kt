package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.DriveSyncSummary
import com.example.ui.theme.*

/**
 * Modern minimalist card for Google Drive Course Sync.
 */
@Composable
fun GoogleDriveSyncCard(
    isSyncing: Boolean,
    onOpenSyncDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenSyncDialog() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDBEAFE)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF2563EB),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = "Cloud Sync",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Google Drive Course Sync",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF)
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFDBEAFE))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Safe Merge",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                    Text(
                        text = if (isSyncing) "Syncing courses from Google Drive..." else "Sync multiple course files from Drive folder link",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = SlateMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2563EB))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSyncing) "Syncing" else "Sync",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Dialog to configure and start Google Drive Course Folder sync.
 */
@Composable
fun GoogleDriveSyncDialog(
    initialUrl: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: (url: String, preserveProgress: Boolean) -> Unit,
    onPickBatchFiles: () -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var preserveProgress by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Dialog(onDismissRequest = { if (!isSyncing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Google Drive Sync",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Text(
                                text = "Course Folder & Spreadsheets",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    if (!isSyncing) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateLight)
                        }
                    }
                }

                Divider(color = SlateBorder)

                // Input Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Google Drive Folder or Sheet URL",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "https://drive.google.com/drive/folders/...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = SlateLight
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontFamily = PoppinsFontFamily, fontSize = 13.sp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlText.isNotBlank()) {
                                    IconButton(
                                        onClick = { urlText = "" },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!clipText.isNullOrBlank()) {
                                            urlText = clipText.trim()
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3,
                        enabled = !isSyncing
                    )
                    Text(
                        text = "Supports public Google Drive folders, Google Sheets, or direct files. Every file inside the folder becomes its own course.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.5.sp,
                        color = SlateMuted
                    )
                }

                // Safe Merge Option Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (preserveProgress) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (preserveProgress) Color(0xFFBBF7D0) else SlateBorder)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSyncing) { preserveProgress = !preserveProgress }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = preserveProgress,
                            onCheckedChange = { preserveProgress = it },
                            enabled = !isSyncing,
                            colors = CheckboxDefaults.colors(
                                checkedColor = EmeraldSuccess,
                                uncheckedColor = SlateLight
                            ),
                            modifier = Modifier.size(20.dp).offset(y = (-2).dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Preserve Learning Progress & Status (ID-Based)",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (preserveProgress) Color(0xFF166534) else SlateText
                            )
                            Text(
                                text = "Words with matching IDs will update their definitions and examples, while keeping your existing status ('Know', 'Confusion', 'Don't Know') and quiz history intact. New words will be seamlessly added.",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.5.sp,
                                color = SlateMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Batch Files from Device Alternative
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF8FAFC))
                        .clickable(enabled = !isSyncing) {
                            onDismiss()
                            onPickBatchFiles()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Or Select Multiple Files from Device",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = IndigoPrimary
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SlateLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSyncing
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                    }

                    Button(
                        onClick = { onSync(urlText, preserveProgress) },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = !isSyncing && urlText.isNotBlank()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Syncing...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Now",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Summary dialog shown after Google Drive sync or batch file import.
 */
@Composable
fun DriveSyncSummaryDialog(
    summary: DriveSyncSummary,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Course Sync Completed",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Text(
                            text = "Learning progress preserved successfully",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            color = SlateMuted
                        )
                    }
                }

                Divider(color = SlateBorder)

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${summary.totalCourses}",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Text(
                                text = "Courses",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${summary.totalUpdatedWords}",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            Text(
                                text = "Updated (Kept)",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0FDF4))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${summary.totalAddedWords}",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                            Text(
                                text = "New Words",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 10.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                Text(
                    text = "Courses Processed:",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )

                // Course breakdown list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(summary.courseDetails) { detail ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = detail.courseTitle,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SlateText
                                    )
                                    Text(
                                        text = "${detail.totalWords} total items",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.5.sp,
                                        color = SlateMuted
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (detail.updatedCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFFDBEAFE))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "${detail.updatedCount} updated",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D4ED8)
                                            )
                                        }
                                    }
                                    if (detail.newCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFFDCFCE7))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "+${detail.newCount} new",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldSuccess
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Text(
                        text = "Done",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
