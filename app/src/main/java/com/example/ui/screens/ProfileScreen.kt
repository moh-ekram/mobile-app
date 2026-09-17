package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CourseEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.UserSession
import com.example.data.model.VocabularyWordEntity
import com.example.notification.NotificationHelper
import com.example.ui.theme.*
import com.example.widget.DailyVocabWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    user: UserSession?,
    progress: UserProgressEntity?,
    backupDirectoryPath: String,
    customBackupTreeUri: String? = null,
    courses: List<CourseEntity> = emptyList(),
    words: List<VocabularyWordEntity> = emptyList(),
    activeCourseId: String = "",
    isDarkTheme: Boolean = false,
    isFlipAnimationEnabled: Boolean = true,
    isFocusMode: Boolean = false,
    isHapticEnabled: Boolean = true,
    onToggleDarkTheme: () -> Unit = {},
    onToggleFlipAnimation: (Boolean) -> Unit = {},
    onToggleFocusMode: (Boolean) -> Unit = {},
    onToggleHaptic: (Boolean) -> Unit = {},
    onSetCustomBackupTreeUri: (Uri) -> Unit = {},
    onManualBackup: () -> Unit,
    onBackupToDriveDirect: () -> Unit = {},
    onRestoreFromDriveDirect: () -> Unit = {},
    onRefreshWidget: () -> Unit = {},
    onExportToUri: (Uri) -> Unit = {},
    onRestoreFromUri: (Uri) -> Unit = {},
    onCloudSync: () -> Unit,
    onRestoreBackup: (String, Boolean) -> Unit,
    onUpdateProfile: (displayName: String, avatarUri: String?, targetExam: String, dailyGoal: Int, bio: String) -> Unit = { _, _, _, _, _ -> },
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf("") }

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
        if (uri != null) {
            onExportToUri(uri)
        }
    }

    val driveRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onRestoreFromUri(uri)
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val lastBackupStr = if (progress != null && progress.lastBackupTimestamp > 0) {
        dateFormat.format(Date(progress.lastBackupTimestamp))
    } else {
        "Auto-saved recently"
    }

    val widgetPrefs = remember { context.getSharedPreferences(DailyVocabWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedWidgetCourseId by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_COURSE_ID, "all") ?: "all")
    }
    var selectedWidgetTagFilter by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_TAG_FILTER, "all") ?: "all")
    }
    var selectedWidgetSize by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_SIZE, "standard") ?: "standard")
    }
    var selectedWidgetFontSize by remember {
        mutableStateOf(widgetPrefs.getString(DailyVocabWidgetProvider.KEY_WIDGET_FONT_SIZE, "medium") ?: "medium")
    }
    var rotateEvery10Seconds by remember {
        mutableStateOf(widgetPrefs.getBoolean(DailyVocabWidgetProvider.KEY_ROTATE_10S, true))
    }
    var rotateOnHomeReturn by remember {
        mutableStateOf(widgetPrefs.getBoolean(DailyVocabWidgetProvider.KEY_ROTATE_ON_HOME_RETURN, true))
    }

    val backupPrefs = remember { context.getSharedPreferences("backup_guide_prefs", Context.MODE_PRIVATE) }
    var showDriveAccountCoachMark by remember {
        mutableStateOf(backupPrefs.getBoolean("show_drive_account_coach", true))
    }
    var showSyncCoachMark by remember {
        mutableStateOf(backupPrefs.getBoolean("show_sync_coach", true))
    }

    // Notification Control State
    var notificationsEnabled by remember { mutableStateOf(NotificationHelper.isNotificationEnabled(context)) }
    val initialTime = remember { NotificationHelper.getNotificationTime(context) }
    var reminderHour by remember { mutableIntStateOf(initialTime.first) }
    var reminderMinute by remember { mutableIntStateOf(initialTime.second) }
    var streakAlertEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences(NotificationHelper.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NotificationHelper.KEY_STREAK_ALERT, true)
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            notificationsEnabled = true
            NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(IndigoLight)
                            .border(2.dp, IndigoPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user?.avatarUri.isNullOrBlank()) {
                            AsyncImage(
                                model = user?.avatarUri,
                                contentDescription = "Profile Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = user?.displayName ?: "User #1235",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateText
                    )

                    Text(
                        text = user?.email ?: "user1235@memorizer.app",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = SlateMuted
                    )

                    if (!user?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${user?.bio}\"",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EmeraldLight)
                                .border(1.dp, EmeraldBorder, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (user?.isGoogleUser == true) "Google Authenticated" else "ID: ${user?.userId ?: "1235"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(IndigoLight)
                                .border(1.dp, Color(0xFFC7D2FE), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = user?.targetExam ?: "GRE / IELTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = CircleShape,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Profile Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Appearance & Theme Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(palette.cardBorder)
                ),
                modifier = Modifier.testTag("theme_appearance_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF312E81) else Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFFA5B4FC) else Color(0xFFD97706),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = if (isDarkTheme) "Dark / Night Theme" else "Light / Day Theme",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleDarkTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IndigoPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
        }

        // Flashcard Core Display & Animation Settings Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(palette.cardBorder)
                ),
                modifier = Modifier.testTag("flashcard_settings_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (palette.isDark) Color(0xFF312E81) else IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Flashcard Display Settings",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }

                    HorizontalDivider(color = palette.cardBorder)

                    // Flip Animation Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flip Animation",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Switch(
                            checked = isFlipAnimationEnabled,
                            onCheckedChange = onToggleFlipAnimation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    HorizontalDivider(color = palette.cardBorder)

                    // Focus Mode Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Mode",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Switch(
                            checked = isFocusMode,
                            onCheckedChange = onToggleFocusMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    HorizontalDivider(color = palette.cardBorder)

                    // Haptic Feedback Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Haptic Feedback",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Subtle vibration on card swipes, flips & ratings",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = palette.textMuted
                            )
                        }
                        Switch(
                            checked = isHapticEnabled,
                            onCheckedChange = onToggleHaptic,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }
        }

        // 1. Unified Minimal Backup & Restore Card (Google Drive & Local Storage in one container)
        item {
            var selectedBackupTab by remember { mutableIntStateOf(0) } // 0: Google Drive, 1: Local Device
            val isDriveLinked = customBackupTreeUri != null && customBackupTreeUri.contains("com.google.android.apps.docs.storage", ignoreCase = true)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (palette.isDark) palette.surface else Color.White),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(palette.cardBorder)
                ),
                modifier = Modifier.testTag("unified_backup_restore_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with minimal tabs
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
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color(0xFF1E3A8A) else Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Backup & Restore",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Cloud & Local Storage",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }

                        // Compact Segmented Switcher
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (palette.isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedBackupTab == 0) (if (palette.isDark) Color(0xFF1E293B) else Color.White) else Color.Transparent)
                                    .clickable { selectedBackupTab = 0 }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Drive",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedBackupTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedBackupTab == 0) Color(0xFF0284C7) else palette.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedBackupTab == 1) (if (palette.isDark) Color(0xFF1E293B) else Color.White) else Color.Transparent)
                                    .clickable { selectedBackupTab = 1 }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Device",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedBackupTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedBackupTab == 1) IndigoPrimary else palette.textSecondary
                                )
                            }
                        }
                    }

                    if (selectedBackupTab == 0) {
                        // Google Drive View
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                                .border(1.dp, if (palette.isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (isDriveLinked) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = if (isDriveLinked) EmeraldSuccess else Color(0xFF0284C7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isDriveLinked) "Drive folder linked" else "No Drive folder set",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.textPrimary
                                )
                            }
                            Text(
                                text = if (isDriveLinked) "Change" else "Link Folder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.clickable {
                                    try {
                                        folderPickerLauncher.launch(Uri.parse("content://com.google.android.apps.docs.storage/document/root"))
                                    } catch (_: Exception) {
                                        folderPickerLauncher.launch(null)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (customBackupTreeUri != null) {
                                        onBackupToDriveDirect()
                                    } else {
                                        try {
                                            folderPickerLauncher.launch(Uri.parse("content://com.google.android.apps.docs.storage/document/root"))
                                        } catch (_: Exception) {
                                            folderPickerLauncher.launch(null)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup Drive", fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (customBackupTreeUri != null) {
                                        onRestoreFromDriveDirect()
                                    } else {
                                        driveRestoreLauncher.launch(arrayOf("application/json", "text/*"))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                                border = BorderStroke(1.dp, EmeraldSuccess),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Drive", fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Local Device View
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                                .border(1.dp, if (palette.isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Last: $lastBackupStr",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = if (customBackupTreeUri != null) "Folder: ${Uri.decode(customBackupTreeUri).takeLast(25)}" else "Default Storage",
                                    fontSize = 10.sp,
                                    color = palette.textSecondary,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "Change",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary,
                                modifier = Modifier.clickable { folderPickerLauncher.launch(null) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onManualBackup,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup Device", fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                                border = BorderStroke(1.dp, EmeraldSuccess),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore File", fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Notifications & Study Reminders Control Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (palette.isDark) palette.surface else Color.White),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(palette.cardBorder)
                ),
                modifier = Modifier.testTag("notification_settings_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color(0xFF312E81) else IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Daily Reminders",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = if (notificationsEnabled) "Active at ${String.format("%02d:%02d", reminderHour, reminderMinute)}" else "Disabled",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = if (notificationsEnabled) EmeraldSuccess else palette.textSecondary
                                )
                            }
                        }

                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    notificationsEnabled = true
                                    NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
                                } else {
                                    notificationsEnabled = false
                                    NotificationHelper.cancelDailyReminder(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary
                            )
                        )
                    }

                    if (notificationsEnabled) {
                        // Time Presets
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Reminder Time",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textSecondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(Pair(8, 0), Pair(13, 0), Pair(20, 0), Pair(22, 0))
                                val labels = listOf("08:00 AM", "01:00 PM", "08:00 PM", "10:00 PM")
                                presets.forEachIndexed { i, (h, m) ->
                                    val isSelected = (reminderHour == h && reminderMinute == m)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) IndigoPrimary else (if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)))
                                            .clickable {
                                                reminderHour = h
                                                reminderMinute = m
                                                NotificationHelper.scheduleDailyReminder(context, h, m)
                                            }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = labels[i],
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else palette.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Streak Alert Safeguard
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Streak Safeguard Alert",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    color = palette.textPrimary
                                )
                            }
                            Switch(
                                checked = streakAlertEnabled,
                                onCheckedChange = {
                                    streakAlertEnabled = it
                                    context.getSharedPreferences(NotificationHelper.PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().putBoolean(NotificationHelper.KEY_STREAK_ALERT, it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFF97316)
                                )
                            )
                        }

                        // Test Notification Button
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                NotificationHelper.showNotification(
                                    context = context,
                                    title = "Daily Study Reminder 🎯",
                                    message = "Keep your streak going! Practice your words and read articles today."
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 7.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Test Notification", fontFamily = PoppinsFontFamily, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Homescreen Widget Control Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (palette.isDark) palette.surface else Color.White),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(palette.cardBorder)
                ),
                modifier = Modifier.testTag("homescreen_widget_control_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (palette.isDark) Color(0xFF312E81) else IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Homescreen Widget",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }

                    // Dynamic Populated Options: Only show courses and categories that have words
                    val courseOptions = remember(courses, words) {
                        val list = mutableListOf<Pair<String, String>>()
                        if (words.isNotEmpty()) {
                            list.add("all" to "All Courses (${words.size})")
                        }
                        courses.forEach { c ->
                            val count = words.count { it.courseId == c.id }
                            if (count > 0) {
                                list.add(c.id to "${c.title} ($count)")
                            }
                        }
                        if (list.isEmpty()) {
                            listOf("all" to "All Courses")
                        } else {
                            list
                        }
                    }

                    // Words in the currently selected course
                    val wordsInSelectedCourse = remember(words, selectedWidgetCourseId) {
                        if (selectedWidgetCourseId == "all") words else words.filter { it.courseId == selectedWidgetCourseId }
                    }

                    // Robust Tag/Category Options: Only categories with count > 0 are displayed
                    val tagOptions = remember(wordsInSelectedCourse) {
                        val list = mutableListOf<Pair<String, String>>()
                        if (wordsInSelectedCourse.isNotEmpty()) {
                            list.add("all" to "All Statuses (${wordsInSelectedCourse.size})")
                        }
                        val knowCount = wordsInSelectedCourse.count { it.status.equals("know", ignoreCase = true) }
                        if (knowCount > 0) list.add("know" to "Know ($knowCount)")

                        val confusionCount = wordsInSelectedCourse.count { it.status.equals("confusion", ignoreCase = true) }
                        if (confusionCount > 0) list.add("confusion" to "Confusion ($confusionCount)")

                        val dontKnowCount = wordsInSelectedCourse.count { it.status.equals("dont_know", ignoreCase = true) }
                        if (dontKnowCount > 0) list.add("dont_know" to "Don't Know ($dontKnowCount)")

                        val unratedCount = wordsInSelectedCourse.count { it.status.equals("unrated", ignoreCase = true) }
                        if (unratedCount > 0) list.add("unrated" to "Unrated ($unratedCount)")

                        if (list.isEmpty()) {
                            listOf("all" to "All Statuses")
                        } else {
                            list
                        }
                    }

                    // Robust Auto-healing: Ensure user cannot remain on an empty/unpopulated filter
                    LaunchedEffect(courseOptions) {
                        if (courseOptions.none { it.first == selectedWidgetCourseId }) {
                            val fallback = courseOptions.firstOrNull()?.first ?: "all"
                            selectedWidgetCourseId = fallback
                            widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_COURSE_ID, fallback).apply()
                            DailyVocabWidgetProvider.updateAllWidgets(context)
                        }
                    }

                    LaunchedEffect(tagOptions) {
                        if (tagOptions.none { it.first == selectedWidgetTagFilter }) {
                            val fallback = tagOptions.firstOrNull()?.first ?: "all"
                            selectedWidgetTagFilter = fallback
                            widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_TAG_FILTER, fallback).apply()
                            DailyVocabWidgetProvider.updateAllWidgets(context)
                        }
                    }

                    // Dropdown Controls Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WidgetDropdownSelector(
                                label = "Course",
                                selectedValue = selectedWidgetCourseId,
                                options = courseOptions,
                                palette = palette,
                                onSelect = {
                                    selectedWidgetCourseId = it
                                    widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_COURSE_ID, it).apply()
                                    DailyVocabWidgetProvider.updateAllWidgets(context)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            WidgetDropdownSelector(
                                label = "Status",
                                selectedValue = selectedWidgetTagFilter,
                                options = tagOptions,
                                palette = palette,
                                onSelect = {
                                    selectedWidgetTagFilter = it
                                    widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_TAG_FILTER, it).apply()
                                    DailyVocabWidgetProvider.updateAllWidgets(context)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sizeOptions = remember {
                                listOf(
                                    "compact" to "Compact",
                                    "standard" to "Standard",
                                    "large" to "Large"
                                )
                            }
                            WidgetDropdownSelector(
                                label = "Widget Size",
                                selectedValue = selectedWidgetSize,
                                options = sizeOptions,
                                palette = palette,
                                onSelect = {
                                    selectedWidgetSize = it
                                    widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_SIZE, it).apply()
                                    DailyVocabWidgetProvider.updateAllWidgets(context)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            val fontSizeOptions = remember {
                                listOf(
                                    "small" to "Small",
                                    "medium" to "Medium (Default)",
                                    "large" to "Large",
                                    "extra_large" to "Extra Large"
                                )
                            }
                            WidgetDropdownSelector(
                                label = "Font Size",
                                selectedValue = selectedWidgetFontSize,
                                options = fontSizeOptions,
                                palette = palette,
                                onSelect = {
                                    selectedWidgetFontSize = it
                                    widgetPrefs.edit().putString(DailyVocabWidgetProvider.KEY_WIDGET_FONT_SIZE, it).apply()
                                    DailyVocabWidgetProvider.updateAllWidgets(context)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Frequent Word Change Switches (Minimal, no subheadings or descriptions)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 10-Second Auto Rotation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rotate Every 10 Seconds",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary
                            )
                            Switch(
                                checked = rotateEvery10Seconds,
                                onCheckedChange = { isChecked ->
                                    rotateEvery10Seconds = isChecked
                                    widgetPrefs.edit().putBoolean(DailyVocabWidgetProvider.KEY_ROTATE_10S, isChecked).apply()
                                    if (isChecked) {
                                        DailyVocabWidgetProvider.startFrequentTicker(context)
                                    } else {
                                        DailyVocabWidgetProvider.stopFrequentTicker()
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = palette.border)

                        // Every Home Return
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rotate on Home Return",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary
                            )
                            Switch(
                                checked = rotateOnHomeReturn,
                                onCheckedChange = { isChecked ->
                                    rotateOnHomeReturn = isChecked
                                    widgetPrefs.edit().putBoolean(DailyVocabWidgetProvider.KEY_ROTATE_ON_HOME_RETURN, isChecked).apply()
                                }
                            )
                        }
                    }

                    // Simulated Widget Layout matching selected options
                    val previewCourseName = if (selectedWidgetCourseId == "all") {
                        (courses.firstOrNull()?.title ?: "GRE VOCABULARY").uppercase()
                    } else {
                        (courses.find { it.id == selectedWidgetCourseId }?.title ?: "VOCABULARY").uppercase()
                    }

                    // Candidate words from populated pool
                    val previewCandidateWords = remember(wordsInSelectedCourse, selectedWidgetTagFilter) {
                        val filtered = if (selectedWidgetTagFilter == "all") {
                            wordsInSelectedCourse
                        } else {
                            wordsInSelectedCourse.filter { it.status.equals(selectedWidgetTagFilter, ignoreCase = true) }
                        }
                        if (filtered.isNotEmpty()) filtered else wordsInSelectedCourse.ifEmpty { words }
                    }
                    val previewWord = previewCandidateWords.firstOrNull()

                    val (previewTagText, previewTagColor, previewTagBg) = when (previewWord?.status ?: selectedWidgetTagFilter) {
                        "know" -> Triple("KNOW", EmeraldSuccess, EmeraldLight)
                        "confusion" -> Triple("CONFUSION", AmberWarning, AmberLight)
                        "dont_know" -> Triple("DON'T KNOW", RoseError, RoseLight)
                        else -> {
                            val grp = previewWord?.group?.trim() ?: ""
                            val label = if (grp.isNotBlank()) {
                                if (grp.all { it.isDigit() }) "GROUP $grp" else grp.uppercase()
                            } else "UNRATED"
                            Triple(label, IndigoPrimary, IndigoLight)
                        }
                    }

                    val (wordSizeSp, meaningSizeSp) = when (selectedWidgetFontSize) {
                        "small" -> 15.sp to 10.5.sp
                        "large" -> 20.sp to 13.sp
                        else -> 17.sp to 11.5.sp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (palette.isDark) Color(0xFF0F172A) else Color.White)
                            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Top Bar: Small course name & tag badge & next
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = previewCourseName,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    maxLines = 1
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(previewTagBg)
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = previewTagText,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = previewTagColor
                                        )
                                    }

                                    Text(
                                        text = "Next ➔",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                }
                            }

                            Text(
                                text = previewWord?.word ?: "Ephemeral",
                                fontFamily = PoppinsFontFamily,
                                fontSize = wordSizeSp,
                                fontWeight = FontWeight.Bold,
                                color = previewTagColor
                            )

                            Text(
                                text = previewWord?.meaning ?: "Lasting for a very short time; fleeting or transient.",
                                fontFamily = PoppinsFontFamily,
                                fontSize = meaningSizeSp,
                                lineHeight = 15.sp,
                                color = palette.textMuted,
                                maxLines = 3
                            )

                            if (selectedWidgetSize != "compact" && !previewWord?.example.isNullOrBlank()) {
                                Text(
                                    text = "\"${previewWord?.example?.trim()}\"",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = (meaningSizeSp.value - 1).sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = palette.textMuted,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onRefreshWidget()
                                DailyVocabWidgetProvider.updateAllWidgets(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh Widget", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                                    val myProvider = ComponentName(context, DailyVocabWidgetProvider::class.java)
                                    if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                                        appWidgetManager.requestPinAppWidget(myProvider, null, null)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddHome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pin to Home", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }
        }



        // Sign Out Button
        item {
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Out of Session", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(user?.displayName ?: "") }
        var targetExamInput by remember { mutableStateOf(user?.targetExam ?: "GRE / IELTS") }
        var dailyGoalInput by remember { mutableStateOf((user?.dailyWordGoal ?: 20).toString()) }
        var bioInput by remember { mutableStateOf(user?.bio ?: "") }
        var avatarUriState by remember { mutableStateOf(user?.avatarUri) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                avatarUriState = uri.toString()
            }
        }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    text = "Edit Profile Details",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = SlateText
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar selector
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(IndigoLight)
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
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
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

                    Text(
                        text = "Tap to choose photo (Photo Picker)",
                        fontSize = 10.sp,
                        color = SlateMuted
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = targetExamInput,
                        onValueChange = { targetExamInput = it },
                        label = { Text("Target Exam (e.g. GRE, IELTS, BCS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dailyGoalInput,
                        onValueChange = { dailyGoalInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Daily Word Goal") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Bio / Study Note") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val goal = dailyGoalInput.toIntOrNull() ?: 20
                        onUpdateProfile(nameInput.trim(), avatarUriState, targetExamInput.trim(), goal, bioInput.trim())
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Dialog
    if (showRestoreDialog) {
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        restoreText = stream.bufferedReader().readText()
                    }
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Text(
                    text = "Restore Progress",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = SlateText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select backup file to restore",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = SlateMuted
                    )

                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("text/plain", "application/json", "text/csv", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoLight, contentColor = IndigoPrimary)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Backup File (.json / .csv)", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = restoreText,
                        onValueChange = { restoreText = it },
                        label = { Text("Backup JSON or CSV") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isJson = restoreText.trim().startsWith("[") || restoreText.trim().startsWith("{")
                        onRestoreBackup(restoreText, isJson)
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WidgetDropdownSelector(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    palette: AppPalette,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.find { it.first == selectedValue }?.second ?: selectedValue

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textMuted
                    )
                    Text(
                        text = displayLabel,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = palette.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (palette.isDark) Color(0xFF1E293B) else Color.White)
        ) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            fontWeight = if (key == selectedValue) FontWeight.Bold else FontWeight.Normal,
                            color = if (key == selectedValue) IndigoPrimary else palette.textPrimary
                        )
                    },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    trailingIcon = if (key == selectedValue) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

