package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MemorizerViewModel
import com.example.widget.DailyVocabWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                context?.let { ctx ->
                    val prefs = ctx.getSharedPreferences(DailyVocabWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
                    val onHomeReturn = prefs.getBoolean(DailyVocabWidgetProvider.KEY_ROTATE_ON_HOME_RETURN, true)
                    if (onHomeReturn) {
                        DailyVocabWidgetProvider.updateAllWidgets(ctx)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start frequent 10-second background ticker
        DailyVocabWidgetProvider.startFrequentTicker(this)

        // Register screen wake / unlock receiver for instant widget updates
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        setContent {
            val viewModel: MemorizerViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                MemorizerApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DailyVocabWidgetProvider.startFrequentTicker(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Called when user presses Home or switches apps to leave to home screen
        triggerHomeReturnUpdate()
    }

    override fun onStop() {
        super.onStop()
        // Ensure widget is freshly updated when app goes into background/home
        triggerHomeReturnUpdate()
    }

    private fun triggerHomeReturnUpdate() {
        val prefs = getSharedPreferences(DailyVocabWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val onHomeReturn = prefs.getBoolean(DailyVocabWidgetProvider.KEY_ROTATE_ON_HOME_RETURN, true)
        if (onHomeReturn) {
            DailyVocabWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizerApp(viewModel: MemorizerViewModel = viewModel()) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState()
    val activeCourseId by viewModel.activeCourseId.collectAsState()
    val allWords by viewModel.allWords.collectAsState()
    val filteredWords by viewModel.filteredWords.collectAsState()
    val currentWordIdx by viewModel.currentWordIndex.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val selectedStatus by viewModel.selectedStatusFilter.collectAsState()
    val selectedGroups by viewModel.selectedGroups.collectAsState()
    val selectedStatuses by viewModel.selectedStatuses.collectAsState()
    val cardSortOrder by viewModel.cardSortOrder.collectAsState()
    val distinctGroups by viewModel.distinctGroups.collectAsState()
    val allGames by viewModel.allGames.collectAsState()
    val allQuestions by viewModel.allQuestions.collectAsState()
    val allArticles by viewModel.allArticles.collectAsState()
    val activeArticle by viewModel.activeArticle.collectAsState()
    val isSyncingArticles by viewModel.isSyncingArticles.collectAsState()
    val articleSyncUrl by viewModel.articleSyncUrl.collectAsState()
    val customBackupTreeUri by viewModel.customBackupTreeUri.collectAsState()
    val userProgress by viewModel.userProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isFocusMode by viewModel.isFocusMode.collectAsState()
    val isFlipAnimationEnabled by viewModel.isFlipAnimationEnabled.collectAsState()
    val currentWidgetWord by viewModel.currentWidgetWord.collectAsState()
    val widgetCategory by viewModel.widgetCategory.collectAsState()
    val isSyncingDrive by viewModel.isSyncingDrive.collectAsState()
    val driveSyncUrl by viewModel.driveSyncUrl.collectAsState()
    val driveSyncSummary by viewModel.driveSyncSummary.collectAsState()
    val palette = LocalAppPalette.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearStatusMessage()
            }
        }
    }

    // System Back Button Handling
    val canNavigateBack = currentRoute != "home" || activeArticle != null || viewModel.hasBackStack()
    BackHandler(enabled = canNavigateBack) {
        if (activeArticle != null) {
            viewModel.selectArticle(null)
        } else if (currentRoute == "flashcard") {
            if (isFocusMode) {
                viewModel.setFocusMode(false)
            }
            viewModel.setRoute("home")
        } else {
            viewModel.navigateBack()
        }
    }

    if (currentUser == null) {
        LoginScreen(
            onLoginSuccess = { /* user state already updated */ },
            onCredentialsLogin = { id, pass -> viewModel.loginWithCredentials(id, pass) },
            onGoogleLogin = { viewModel.loginWithGoogle() }
        )
    } else {
        val isArticleReading = activeArticle != null
        val hideTopBar = (currentRoute == "flashcard" && isFocusMode) || isArticleReading
        val hideBottomBar = currentRoute == "flashcard" || isArticleReading

        Scaffold(
            topBar = {
                if (!hideTopBar) {
                    TopAppBar(
                        navigationIcon = {
                            if (currentRoute == "flashcard") {
                                IconButton(onClick = { viewModel.setRoute("home") }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Home",
                                        tint = palette.textPrimary
                                    )
                                }
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentRoute == "home") {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(IndigoPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "M",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "Memorizer",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = palette.textPrimary
                                    )
                                } else {
                                    val screenTitle = when (currentRoute) {
                                        "flashcard" -> "Flashcards"
                                        "games" -> "Games"
                                        "admin" -> "Control"
                                        "profile" -> "Profile"
                                        "article_reader" -> "Articles"
                                        else -> currentRoute.replaceFirstChar { it.uppercase() }
                                    }
                                    Text(
                                        text = screenTitle,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        },
                        actions = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color(0xFF312E81) else IndigoLight)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = currentUser?.displayName ?: "User #1235",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.surface,
                            titleContentColor = palette.textPrimary
                        )
                    )
                }
            },
            bottomBar = {
                if (!hideBottomBar) {
                    NavigationBar(
                        containerColor = palette.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("main_navigation_bar")
                    ) {
                        val navItems = listOf(
                            NavigationItem("home", "Home", Icons.Default.Home),
                            NavigationItem("flashcard", "Flashcard", Icons.Default.Style),
                            NavigationItem("games", "Games", Icons.Default.SportsEsports),
                            NavigationItem("admin", "Control", Icons.Default.AdminPanelSettings),
                            NavigationItem("profile", "Profile", Icons.Default.Person)
                        )

                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.setRoute(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary,
                                    selectedTextColor = if (palette.isDark) Color(0xFFA5B4FC) else IndigoPrimary,
                                    unselectedIconColor = palette.textMuted,
                                    unselectedTextColor = palette.textMuted,
                                    indicatorColor = if (palette.isDark) Color(0xFF312E81) else IndigoLight
                                ),
                                modifier = Modifier.testTag("nav_item_${item.route}")
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = palette.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Crossfade(targetState = currentRoute, label = "screen_transition") { route ->
                    when (route) {
                        "home" -> HomeScreen(
                            user = currentUser,
                            words = allWords,
                            progress = userProgress,
                            courses = allCourses,
                            activeCourseId = activeCourseId,
                            onSelectCourse = { cId -> viewModel.setActiveCourse(cId) },
                            onCreateCourseClick = { viewModel.setRoute("admin") },
                            onNavigate = { target -> viewModel.setRoute(target) },
                            onSelectGroup = { grp -> viewModel.selectGroup(grp) },
                            onSelectStatus = { status ->
                                viewModel.selectStatusFilter(status)
                                viewModel.setRoute("flashcard")
                            },
                            widgetWord = currentWidgetWord,
                            widgetCategory = widgetCategory,
                            onSetWidgetCategory = { cat -> viewModel.setWidgetCategory(cat) },
                            onCycleWidgetWord = { viewModel.cycleNextWidgetWord() },
                            onRateWidgetWord = { id, st -> viewModel.rateWord(id, st) },
                            onRefreshWidget = { viewModel.cycleNextWidgetWord() }
                        )
                        "flashcard" -> FlashcardScreen(
                            words = filteredWords,
                            currentIndex = currentWordIdx,
                            courseName = allCourses.find { it.id == activeCourseId }?.title ?: "Vocabulary",
                            selectedGroups = selectedGroups,
                            selectedStatuses = selectedStatuses,
                            sortOrder = cardSortOrder,
                            availableGroups = distinctGroups,
                            isFocusMode = isFocusMode,
                            isFlipAnimationEnabled = isFlipAnimationEnabled,
                            onToggleFocusMode = { enable -> viewModel.setFocusMode(enable) },
                            onToggleGroup = { grp -> viewModel.toggleGroup(grp) },
                            onClearGroups = { viewModel.clearGroups() },
                            onToggleStatus = { st -> viewModel.toggleStatus(st) },
                            onClearStatuses = { viewModel.clearStatuses() },
                            onSetSortOrder = { ord -> viewModel.setCardSortOrder(ord) },
                            onReshuffle = { viewModel.reshuffleCards() },
                            onResetAllFilters = { viewModel.resetAllCardFilters() },
                            onRate = { id, st -> viewModel.rateWord(id, st) },
                            onReportWord = { id, isReported, reason -> viewModel.reportWord(id, isReported, reason) },
                            onNext = { viewModel.nextWord() },
                            onPrevious = { viewModel.previousWord() },
                            onBack = {
                                if (isFocusMode) {
                                    viewModel.setFocusMode(false)
                                }
                                viewModel.setRoute("home")
                            }
                        )
                        "games" -> GamePracticeScreen(
                            games = allGames,
                            questions = allQuestions,
                            courses = allCourses,
                            activeCourseId = activeCourseId,
                            onCompleteQuiz = { score, total ->
                                viewModel.recordQuizCompletion(score, total)
                            },
                            onRecordGameAnswer = { qId, isCorrect ->
                                viewModel.recordGameAnswer(qId, isCorrect)
                            },
                            onRecordWordQuizAnswer = { wId, isCorrect ->
                                viewModel.recordWordQuizAnswer(wId, isCorrect)
                            },
                            articles = allArticles,
                            activeArticle = activeArticle,
                            words = allWords,
                            onSelectArticle = { art -> viewModel.setActiveArticle(art) },
                            onSaveArticle = { title, content, author, id ->
                                viewModel.saveArticle(title, content, author, id)
                            },
                            onSaveArticlesBatch = { batch ->
                                viewModel.saveArticlesBatch(batch)
                            },
                            onDeleteArticle = { id -> viewModel.deleteArticle(id) },
                            onRateWord = { id, st -> viewModel.rateWord(id, st) },
                            onBack = { viewModel.navigateBack() }
                        )
                        "article_reader" -> ArticleReaderScreen(
                            articles = allArticles,
                            activeArticle = activeArticle,
                            words = allWords,
                            courses = allCourses,
                            isSyncing = isSyncingArticles,
                            syncUrl = articleSyncUrl,
                            onSync = { source -> viewModel.syncArticles(source) },
                            onSetSyncUrl = { url -> viewModel.setArticleSyncUrl(url) },
                            onSelectArticle = { art -> viewModel.setActiveArticle(art) },
                            onSaveArticle = { title, content, author, id ->
                                viewModel.saveArticle(title, content, author, id)
                            },
                            onSaveArticlesBatch = { batch ->
                                viewModel.saveArticlesBatch(batch)
                            },
                            onDeleteArticle = { id -> viewModel.deleteArticle(id) },
                            onRateWord = { id, st -> viewModel.rateWord(id, st) },
                            onBack = { viewModel.navigateBack() }
                        )
                        "admin" -> AdminPanelScreen(
                            words = allWords,
                            games = allGames,
                            questions = allQuestions,
                            courses = allCourses,
                            activeCourseId = activeCourseId,
                            isSyncingDrive = isSyncingDrive,
                            driveSyncUrl = driveSyncUrl,
                            driveSyncSummary = driveSyncSummary,
                            onSyncFromDrive = { url, preserve -> viewModel.syncCoursesFromDrive(url, preserve) },
                            onBatchImportFiles = { files, preserve -> viewModel.importMultipleCourseFiles(files, preserve) },
                            onClearDriveSummary = { viewModel.clearDriveSyncSummary() },
                            onCreateCourse = { title, desc, fileContent, isJson -> viewModel.createCourse(title, desc, fileContent, isJson) },
                            onSelectCourse = { cId -> viewModel.setActiveCourse(cId) },
                            onDeleteCourse = { cId -> viewModel.deleteCourse(cId) },
                            onAddWord = { word -> viewModel.addCustomWord(word) },
                            onUpdateWord = { word -> viewModel.updateWord(word) },
                            onUpdateCourse = { cId, title, desc -> viewModel.updateCourse(cId, title, desc) },
                            onDeleteWord = { id -> viewModel.deleteWord(id) },
                            onDeleteGame = { id -> viewModel.deleteGameItem(id) },
                            onDeleteGamesBySection = { sec -> viewModel.deleteGamesBySection(sec) },
                            onClearAllGames = { viewModel.clearAllGames() },
                            onDeleteQuestion = { id -> viewModel.deleteQuestionBankItem(id) },
                            onClearAllQB = { viewModel.clearAllQuestionBank() },
                            onImportCourse = { content, isJson, cId, title -> viewModel.importCourseFile(content, isJson, cId, title) },
                            onImportGame = { content, type -> viewModel.importGameFile(content, type) },
                            onImportGameItems = { items -> viewModel.importGameItems(items) },
                            onImportQB = { content -> viewModel.importQuestionBankFile(content) },
                            onResetData = { viewModel.resetToSample() }
                        )
                        "profile" -> ProfileScreen(
                            user = currentUser,
                            progress = userProgress,
                            backupDirectoryPath = viewModel.repository.backupManager.getBackupPathString(),
                            customBackupTreeUri = customBackupTreeUri,
                            courses = allCourses,
                            words = allWords,
                            activeCourseId = activeCourseId,
                            isDarkTheme = isDarkTheme,
                            isFlipAnimationEnabled = isFlipAnimationEnabled,
                            isFocusMode = isFocusMode,
                            onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                            onToggleFlipAnimation = { enable -> viewModel.setFlipAnimationEnabled(enable) },
                            onToggleFocusMode = { enable -> viewModel.setFocusMode(enable) },
                            onSetCustomBackupTreeUri = { uri -> viewModel.setCustomBackupTreeUri(uri) },
                            onManualBackup = { viewModel.triggerManualBackup() },
                            onBackupToDriveDirect = { viewModel.backupDirectlyToLinkedFolder() },
                            onRestoreFromDriveDirect = { viewModel.restoreDirectlyFromLinkedFolder() },
                            onRefreshWidget = { viewModel.refreshWidget() },
                            onExportToUri = { uri -> viewModel.exportBackupToUri(uri) },
                            onRestoreFromUri = { uri -> viewModel.restoreFromUri(uri) },
                            onCloudSync = { viewModel.triggerCloudSync() },
                            onRestoreBackup = { content, isJson -> viewModel.restoreBackupContent(content, isJson) },
                            onUpdateProfile = { name, avatar, targetExam, goal, bio ->
                                viewModel.updateProfile(name, avatar, targetExam, goal, bio)
                            },
                            onLogout = { viewModel.logout() }
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Maintained for test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
