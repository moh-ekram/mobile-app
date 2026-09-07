package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MemorizerApp()
            }
        }
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
    val distinctGroups by viewModel.distinctGroups.collectAsState()
    val allGames by viewModel.allGames.collectAsState()
    val allQuestions by viewModel.allQuestions.collectAsState()
    val allArticles by viewModel.allArticles.collectAsState()
    val activeArticle by viewModel.activeArticle.collectAsState()
    val customBackupTreeUri by viewModel.customBackupTreeUri.collectAsState()
    val userProgress by viewModel.userProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

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

    if (currentUser == null) {
        LoginScreen(
            onLoginSuccess = { /* user state already updated */ },
            onCredentialsLogin = { id, pass -> viewModel.loginWithCredentials(id, pass) },
            onGoogleLogin = { viewModel.loginWithGoogle() }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "M",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Memorizer",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SlateText
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(CircleShape)
                                .background(IndigoLight)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentUser?.displayName ?: "User #1235",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = SlateText
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    val navItems = listOf(
                        NavigationItem("home", "Home", Icons.Default.Home),
                        NavigationItem("flashcard", "Flashcard", Icons.Default.Style),
                        NavigationItem("games", "Games", Icons.Default.SportsEsports),
                        NavigationItem("admin", "Admin", Icons.Default.AdminPanelSettings),
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
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = IndigoPrimary,
                                selectedTextColor = IndigoPrimary,
                                unselectedIconColor = SlateLight,
                                unselectedTextColor = SlateMuted,
                                indicatorColor = IndigoLight
                            ),
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = SlateBg
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
                            onSelectGroup = { grp -> viewModel.selectedGroup.value = grp }
                        )
                        "flashcard" -> FlashcardScreen(
                            words = filteredWords,
                            currentIndex = currentWordIdx,
                            selectedGroup = selectedGroup,
                            selectedStatus = selectedStatus,
                            availableGroups = distinctGroups,
                            onSelectGroup = { grp -> viewModel.selectedGroup.value = grp },
                            onSelectStatus = { st -> viewModel.selectedStatusFilter.value = st },
                            onRate = { id, st -> viewModel.rateWord(id, st) },
                            onNext = { viewModel.nextWord() },
                            onPrevious = { viewModel.previousWord() }
                        )
                        "games" -> GamePracticeScreen(
                            games = allGames,
                            questions = allQuestions,
                            onCompleteQuiz = { score, total ->
                                viewModel.recordQuizCompletion(score, total)
                            }
                        )
                        "article_reader" -> ArticleReaderScreen(
                            articles = allArticles,
                            activeArticle = activeArticle,
                            words = allWords,
                            onSelectArticle = { art -> viewModel.setActiveArticle(art) },
                            onSaveArticle = { title, content -> viewModel.saveArticle(title, content) },
                            onDeleteArticle = { id -> viewModel.deleteArticle(id) },
                            onRateWord = { id, st -> viewModel.rateWord(id, st) },
                            onBack = { viewModel.setRoute("home") }
                        )
                        "admin" -> AdminPanelScreen(
                            words = allWords,
                            games = allGames,
                            questions = allQuestions,
                            courses = allCourses,
                            activeCourseId = activeCourseId,
                            onCreateCourse = { title, desc -> viewModel.createCourse(title, desc) },
                            onSelectCourse = { cId -> viewModel.setActiveCourse(cId) },
                            onDeleteCourse = { cId -> viewModel.deleteCourse(cId) },
                            onAddWord = { word -> viewModel.addCustomWord(word) },
                            onDeleteWord = { id -> viewModel.deleteWord(id) },
                            onDeleteGame = { id -> viewModel.deleteGameItem(id) },
                            onDeleteQuestion = { id -> viewModel.deleteQuestionBankItem(id) },
                            onImportCourse = { content, isJson, cId -> viewModel.importCourseFile(content, isJson, cId) },
                            onImportGame = { content, type -> viewModel.importGameFile(content, type) },
                            onImportQB = { content -> viewModel.importQuestionBankFile(content) },
                            onResetData = { viewModel.resetToSample() }
                        )
                        "profile" -> ProfileScreen(
                            user = currentUser,
                            progress = userProgress,
                            backupDirectoryPath = viewModel.repository.backupManager.getBackupPathString(),
                            customBackupTreeUri = customBackupTreeUri,
                            onSetCustomBackupTreeUri = { uri -> viewModel.setCustomBackupTreeUri(uri) },
                            onManualBackup = { viewModel.triggerManualBackup() },
                            onCloudSync = { viewModel.triggerCloudSync() },
                            onRestoreBackup = { content, isJson -> viewModel.restoreBackupContent(content, isJson) },
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
