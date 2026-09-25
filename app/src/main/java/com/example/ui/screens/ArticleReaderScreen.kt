package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ArticleEntity
import com.example.data.model.CourseEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.parser.ArticleParser
import com.example.data.parser.ParsedArticleItem
import com.example.ui.theme.*
import java.util.Locale
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontSynthesis
import org.json.JSONObject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articles: List<ArticleEntity>,
    activeArticle: ArticleEntity?,
    words: List<VocabularyWordEntity>,
    courses: List<CourseEntity> = emptyList(),
    isSyncing: Boolean = false,
    syncUrl: String = "",
    pendingSharedTextOrUrl: String? = null,
    onClearPendingShared: () -> Unit = {},
    onSync: (String) -> Unit = {},
    onSetSyncUrl: (String) -> Unit = {},
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit,
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
    onAddSampleData: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var initialEditorTab by remember { mutableIntStateOf(0) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showReaderSettingsDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (activeArticle != null) {
            onSelectArticle(null)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            if (activeArticle == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Articles",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SlateText)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSyncDialog = true },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = IndigoPrimary
                                )
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Articles", tint = IndigoPrimary)
                            }
                        }
                        IconButton(onClick = {
                            initialEditorTab = 1
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Import from Web URL", tint = IndigoPrimary)
                        }
                        IconButton(onClick = {
                            initialEditorTab = 0
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Article", tint = IndigoPrimary)
                        }
                        IconButton(onClick = { showReaderSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Reader Settings", tint = SlateText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        }
    ) { innerPadding ->
        ArticleReaderView(
            articles = articles,
            activeArticle = activeArticle,
            words = words,
            courses = courses,
            isSyncing = isSyncing,
            syncUrl = syncUrl,
            pendingSharedTextOrUrl = pendingSharedTextOrUrl,
            onClearPendingShared = onClearPendingShared,
            onSync = onSync,
            onSetSyncUrl = onSetSyncUrl,
            onSelectArticle = onSelectArticle,
            onSaveArticle = onSaveArticle,
            onSaveArticlesBatch = onSaveArticlesBatch,
            onDeleteArticle = onDeleteArticle,
            onRateWord = onRateWord,
            onAddSampleData = onAddSampleData,
            showAddDialogFromParent = showAddDialog,
            initialAddDialogTabFromParent = initialEditorTab,
            onDismissAddDialog = { showAddDialog = false },
            showSyncDialogFromParent = showSyncDialog,
            onDismissSyncDialog = { showSyncDialog = false },
            showReaderSettingsFromParent = showReaderSettingsDialog,
            onDismissReaderSettings = { showReaderSettingsDialog = false },
            modifier = if (activeArticle == null) Modifier.padding(innerPadding) else Modifier.statusBarsPadding()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderView(
    articles: List<ArticleEntity>,
    activeArticle: ArticleEntity?,
    words: List<VocabularyWordEntity>,
    courses: List<CourseEntity> = emptyList(),
    isSyncing: Boolean = false,
    syncUrl: String = "",
    pendingSharedTextOrUrl: String? = null,
    onClearPendingShared: () -> Unit = {},
    onSync: (String) -> Unit = {},
    onSetSyncUrl: (String) -> Unit = {},
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit,
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
    onAddSampleData: () -> Unit = {},
    showAddDialogFromParent: Boolean = false,
    initialAddDialogTabFromParent: Int = 0,
    onDismissAddDialog: () -> Unit = {},
    showSyncDialogFromParent: Boolean = false,
    onDismissSyncDialog: () -> Unit = {},
    showReaderSettingsFromParent: Boolean = false,
    onDismissReaderSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var localAddDialogTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showLocalReaderSettings by remember { mutableStateOf(false) }
    var showCourseFilterDialog by remember { mutableStateOf(false) }
    var articlePendingDelete by remember { mutableStateOf<ArticleEntity?>(null) }
    val readerScope = rememberCoroutineScope()
    var isExtractingSharedArticle by remember { mutableStateOf(false) }
    var sharedArticleError by remember { mutableStateOf<String?>(null) }

    // Course selection persistence
    val coursePrefs = remember { context.getSharedPreferences("reader_course_prefs", Context.MODE_PRIVATE) }
    var selectedCourseIds by remember {
        mutableStateOf<Set<String>>(coursePrefs.getStringSet("selected_courses", emptySet()) ?: emptySet())
    }

    // Blocked words persistence
    val blockedWordsPrefs = remember { context.getSharedPreferences("reader_blocked_words_prefs", Context.MODE_PRIVATE) }
    var blockedWordsSet by remember {
        mutableStateOf<Set<String>>(blockedWordsPrefs.getStringSet("blocked_words", emptySet()) ?: emptySet())
    }
    var wordToBlockConfirmation by remember { mutableStateOf<VocabularyWordEntity?>(null) }

    // Matched words for popup (supports sequential multi-course matches)
    var matchedWordsForPopup by remember { mutableStateOf<List<VocabularyWordEntity>>(emptyList()) }
    var popupWordIndex by remember { mutableIntStateOf(0) }
    val selectedWordForDetails = matchedWordsForPopup.getOrNull(popupWordIndex)

    val isAdding = showAddDialog || showAddDialogFromParent
    val isSyncingDialogVisible = showSyncDialog || showSyncDialogFromParent
    val isReaderSettingsVisible = showLocalReaderSettings || showReaderSettingsFromParent

    // Reader Display Settings State (Persisted in SharedPreferences)
    val readerPrefs = remember { context.getSharedPreferences("reader_display_prefs", Context.MODE_PRIVATE) }
    var readerFontSize by remember { mutableFloatStateOf(readerPrefs.getFloat("reader_font_size", 16f)) }
    var readerPaddingDp by remember { mutableIntStateOf(readerPrefs.getInt("reader_padding_dp", 8)) }
    var readerLineSpacing by remember { mutableFloatStateOf(readerPrefs.getFloat("reader_line_spacing", 1.6f)) }
    var readerFontFamily by remember { mutableStateOf(readerPrefs.getString("reader_font_family", "serif") ?: "serif") }
    var readerHighlightBold by remember { mutableStateOf(readerPrefs.getString("reader_highlight_bold", "bold") ?: "bold") }
    var readerJustify by remember { mutableStateOf(readerPrefs.getBoolean("reader_justify", false)) }
    var isReaderNightMode by remember { mutableStateOf(readerPrefs.getBoolean("reader_night_mode", false)) }
    var isContinuousScroll by remember { mutableStateOf(readerPrefs.getBoolean("reader_continuous_scroll", true)) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showReadingControlsSheet by remember { mutableStateOf(false) }
    var showChaptersDropdown by remember { mutableStateOf(false) }

    // TTS engine for audio pronunciation
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized
            }
        }
        engine.language = Locale.US
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    val currentArticle = activeArticle

    // Intercept back button when reading an article or dismiss popup if open
    LaunchedEffect(currentArticle?.id) {
        matchedWordsForPopup = emptyList()
        popupWordIndex = 0
    }

    BackHandler(enabled = currentArticle != null || matchedWordsForPopup.isNotEmpty()) {
        if (matchedWordsForPopup.isNotEmpty()) {
            matchedWordsForPopup = emptyList()
            popupWordIndex = 0
        } else {
            onSelectArticle(null)
        }
    }

    fun getPlace1(w: VocabularyWordEntity): String {
        if (!w.customPlacesJson.isNullOrBlank()) {
            try {
                val json = JSONObject(w.customPlacesJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = k.lowercase().replace("_", "").replace(" ", "").replace("-", "")
                    if (kClean == "place1" || kClean.startsWith("place1") || kClean == "word") {
                        val v = json.optString(k, "").trim()
                        if (v.isNotBlank()) return v
                    }
                }
            } catch (_: Exception) {}
        }
        return w.word.trim()
    }

    fun getPlace2(w: VocabularyWordEntity): String {
        if (!w.customPlacesJson.isNullOrBlank()) {
            try {
                val json = JSONObject(w.customPlacesJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = k.lowercase().replace("_", "").replace(" ", "").replace("-", "")
                    if (kClean == "place2" || kClean.startsWith("place2") ||
                        kClean == "meaning" || kClean.contains("meaning") || kClean.contains("definition") || kClean.contains("translation")
                    ) {
                        val v = json.optString(k, "").trim()
                        if (v.isNotBlank()) return v
                    }
                }
            } catch (_: Exception) {}
        }
        return w.meaning.trim()
    }

    // Filter vocabulary words based on selected courses AND blocked words
    val activeWords = remember(words, selectedCourseIds, blockedWordsSet) {
        val courseFiltered = if (selectedCourseIds.isEmpty()) {
            words
        } else {
            words.filter { selectedCourseIds.contains(it.courseId) }
        }
        courseFiltered.filter { w ->
            val w1 = w.word.trim().lowercase(Locale.ROOT)
            val p1 = getPlace1(w).trim().lowercase(Locale.ROOT)
            !blockedWordsSet.contains(w1) && !blockedWordsSet.contains(p1)
        }
    }

    // Multi-map for Place1 (Word) - stores all matching words across selected courses
    val place1MultiMap = remember(activeWords) {
        val map = mutableMapOf<String, MutableList<VocabularyWordEntity>>()
        activeWords.forEach { w ->
            val wKey = w.word.trim().lowercase(Locale.ROOT)
            if (wKey.isNotBlank()) {
                val list = map.getOrPut(wKey) { mutableListOf() }
                if (list.none { it.id == w.id }) list.add(w)
            }
            val p1 = getPlace1(w)
            val p1Key = p1.trim().lowercase(Locale.ROOT)
            if (p1Key.isNotBlank()) {
                val list = map.getOrPut(p1Key) { mutableListOf() }
                if (list.none { it.id == w.id }) list.add(w)
            }
        }
        map
    }

    // Multi-map for Place2 (Meaning) - stores all matching words across selected courses
    val place2MultiMap = remember(activeWords) {
        val map = mutableMapOf<String, MutableList<VocabularyWordEntity>>()
        activeWords.forEach { w ->
            if (w.meaning.isNotBlank()) {
                val trimmedMeaning = w.meaning.trim().lowercase(Locale.ROOT)
                val list = map.getOrPut(trimmedMeaning) { mutableListOf() }
                if (list.none { it.id == w.id }) list.add(w)

                val parts = trimmedMeaning.split("[,;/]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
                parts.forEach { part ->
                    val pList = map.getOrPut(part) { mutableListOf() }
                    if (pList.none { it.id == w.id }) pList.add(w)
                }
            }
            val p2 = getPlace2(w)
            if (p2.isNotBlank()) {
                val trimmedP2 = p2.trim().lowercase(Locale.ROOT)
                val list = map.getOrPut(trimmedP2) { mutableListOf() }
                if (list.none { it.id == w.id }) list.add(w)

                val parts = trimmedP2.split("[,;/]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
                parts.forEach { part ->
                    val pList = map.getOrPut(part) { mutableListOf() }
                    if (pList.none { it.id == w.id }) pList.add(w)
                }
            }
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
    ) {
        if (currentArticle == null) {
            // Android Share link card (System 4)
            if (pendingSharedTextOrUrl != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoLight),
                    border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shared via Android",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = IndigoPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingSharedTextOrUrl.take(160),
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = SlateText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sharedArticleError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sharedArticleError ?: "",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp,
                                color = RoseError
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    sharedArticleError = null
                                    onClearPendingShared()
                                },
                                enabled = !isExtractingSharedArticle
                            ) {
                                Text("Dismiss", fontFamily = PoppinsFontFamily, color = SlateMuted, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val text = pendingSharedTextOrUrl.trim()
                                    val urlRegex = Regex("""https?://\S+""")
                                    val foundUrl = urlRegex.find(text)?.value
                                    isExtractingSharedArticle = true
                                    sharedArticleError = null
                                    readerScope.launch {
                                        if (foundUrl != null) {
                                            val fetchResult = ArticleParser.fetchWebArticle(foundUrl)
                                            isExtractingSharedArticle = false
                                            fetchResult.fold(
                                                onSuccess = { item ->
                                                    onSaveArticle(item.title, item.content, item.author, null)
                                                    onClearPendingShared()
                                                },
                                                onFailure = { err ->
                                                    sharedArticleError = err.message ?: "Failed to import shared link"
                                                }
                                            )
                                        } else {
                                            isExtractingSharedArticle = false
                                            val parsed = ArticleParser.parseArticles(text)
                                            if (parsed.size > 1) {
                                                onSaveArticlesBatch(parsed.map { Triple(it.title, it.content, it.author) })
                                            } else if (parsed.size == 1) {
                                                val single = parsed.first()
                                                onSaveArticle(single.title, single.content, single.author, null)
                                            } else {
                                                onSaveArticle("Shared Article", text, "Shared via Android", null)
                                            }
                                            onClearPendingShared()
                                        }
                                    }
                                },
                                enabled = !isExtractingSharedArticle,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                            ) {
                                if (isExtractingSharedArticle) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importing...", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import & Read", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // LIST VIEW: Minimal articles list (No secondary header, only Articles header at top)
            if (articles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                        )
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
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = "No Articles Available",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )

                            Text(
                                text = "Add or upload an article to start reading",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                color = SlateMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onAddSampleData,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Sample Content", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            localAddDialogTab = 0
                                            showAddDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = IndigoPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Article", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            localAddDialogTab = 1
                                            showAddDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = IndigoPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import URL", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Article count & Course Selector chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = IndigoPrimary
                                        )
                                        Text(
                                            text = "${articles.size}",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateText
                                        )
                                    }
                                }

                                // Course filter chip
                                Surface(
                                    color = if (selectedCourseIds.isNotEmpty()) IndigoLight else Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedCourseIds.isNotEmpty()) IndigoPrimary.copy(alpha = 0.5f) else SlateBorder
                                    ),
                                    modifier = Modifier.clickable { showCourseFilterDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.School,
                                            contentDescription = "Select Courses",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (selectedCourseIds.isNotEmpty()) IndigoPrimary else SlateMuted
                                        )
                                        val courseLabel = when {
                                            courses.isEmpty() -> "Courses"
                                            selectedCourseIds.isEmpty() || selectedCourseIds.size == courses.size -> "All Courses"
                                            selectedCourseIds.size == 1 -> courses.firstOrNull { it.id == selectedCourseIds.first() }?.title?.take(10) ?: "1 Course"
                                            else -> "${selectedCourseIds.size} Courses"
                                        }
                                        Text(
                                            text = courseLabel,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selectedCourseIds.isNotEmpty()) IndigoPrimary else SlateText,
                                            maxLines = 1
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (selectedCourseIds.isNotEmpty()) IndigoPrimary else SlateMuted
                                        )
                                    }
                                }
                            }

                            // Right: Minimal, Iconized Toolbar (Sync, URL, Add, Settings)
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (syncUrl.isNotBlank()) onSync(syncUrl) else showSyncDialog = true
                                        },
                                        enabled = !isSyncing,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = IndigoPrimary
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = "Sync Articles",
                                                modifier = Modifier.size(16.dp),
                                                tint = IndigoPrimary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            localAddDialogTab = 1
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Language,
                                            contentDescription = "Import from Web URL",
                                            modifier = Modifier.size(16.dp),
                                            tint = IndigoPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            localAddDialogTab = 0
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add Article",
                                            modifier = Modifier.size(18.dp),
                                            tint = IndigoPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = { showLocalReaderSettings = true },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = "Reader Display Settings",
                                            modifier = Modifier.size(16.dp),
                                            tint = SlateText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(articles, key = { it.id }) { art ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        articlePendingDelete = art
                                        false
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onSelectArticle(art)
                                        showEditDialog = true
                                        false
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val isDelete = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val isEdit = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd || dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                val bgColor = when {
                                    isDelete -> RoseError
                                    isEdit -> IndigoPrimary
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    if (isDelete) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Delete", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    } else if (isEdit) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectArticle(art) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = art.title,
                                        fontFamily = selectArticleFontForText(art.title),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (art.author.isNotBlank() && art.author != "Anonymous Author") {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "By ${art.author}",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = art.content.take(130).replace("\n", " ") + if (art.content.length > 130) "..." else "",
                                        fontFamily = selectArticleFontForText(art.content),
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = SlateText.copy(alpha = 0.8f),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                // Delete Confirmation Dialog
                if (articlePendingDelete != null) {
                    val artToDelete = articlePendingDelete!!
                    Dialog(
                        onDismissRequest = { articlePendingDelete = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 24.dp)
                                .widthIn(max = 380.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 16.dp,
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFE4E6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = RoseError,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Delete Article?",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = SlateText,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Are you sure you want to delete \"${artToDelete.title}\"? This action cannot be undone.",
                                    fontFamily = selectArticleFontForText(artToDelete.title),
                                    fontSize = 13.sp,
                                    color = SlateMuted,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { articlePendingDelete = null },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            onDeleteArticle(artToDelete.id)
                                            articlePendingDelete = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Delete", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Minimal, clean e-reader view matching design reference (No edit/delete icons)
            val currentIndex = articles.indexOfFirst { it.id == currentArticle.id }.coerceAtLeast(0)
            val pageBg = if (isReaderNightMode) Color(0xFF0F172A) else Color(0xFFF1F5F9)
            val cardBg = if (isReaderNightMode) Color(0xFF1E293B) else Color(0xFFFCFAF7)
            val textColor = if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
            val subtleMuted = if (isReaderNightMode) Color(0xFF64748B) else Color(0xFF94A3B8)
            val cardBorderColor = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFCBD5E1)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.5.dp, cardBorderColor),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 10.dp, bottom = 12.dp, start = 14.dp, end = 14.dp)
                    ) {
                        // Header: Minimal, Iconized Back Button, Compact Chapter Pill, Quick Actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onSelectArticle(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = subtleMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Compact Chapter Pill (Prev, 1/X, Next)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { showChaptersDropdown = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (articles.size > 1 && currentIndex > 0) {
                                        IconButton(
                                            onClick = { onSelectArticle(articles[currentIndex - 1]) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Previous article",
                                                tint = if (isReaderNightMode) Color(0xFFCBD5E1) else SlateText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Text(
                                        text = "${currentIndex + 1} / ${articles.size}",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isReaderNightMode) Color(0xFFE2E8F0) else SlateText,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    if (articles.size > 1 && currentIndex < articles.size - 1) {
                                        IconButton(
                                            onClick = { onSelectArticle(articles[currentIndex + 1]) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Next article",
                                                tint = if (isReaderNightMode) Color(0xFFCBD5E1) else SlateText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                }
                            }

                            // Right: Minimal Quick Action Icons (TTS Audio, Theme Toggle, Reader Settings)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // TTS Audio toggle
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) {
                                            tts?.stop()
                                            isSpeaking = false
                                        } else {
                                            tts?.speak(currentArticle.content, TextToSpeech.QUEUE_FLUSH, null, "article_tts")
                                            isSpeaking = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Headphones,
                                        contentDescription = "Listen to Article",
                                        tint = if (isSpeaking) IndigoPrimary else subtleMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Dark / Light Mode Toggle
                                IconButton(
                                    onClick = {
                                        isReaderNightMode = !isReaderNightMode
                                        readerPrefs.edit().putBoolean("reader_night_mode", isReaderNightMode).apply()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isReaderNightMode) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                                        contentDescription = "Toggle Theme",
                                        tint = if (isReaderNightMode) Color(0xFFFDE047) else subtleMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Reading Controls Sheet
                                IconButton(
                                    onClick = { showReadingControlsSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Reader Settings",
                                        tint = subtleMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Reading Content with Interactive Words
                        val isBengaliArticle = isBengaliText(currentArticle.content)
                        val selectedFont = if (isBengaliArticle) AikyaFontFamily else when (readerFontFamily) {
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            "sans" -> FontFamily.SansSerif
                            else -> FontFamily.Serif
                        }
                        val isExtraBold = (readerHighlightBold == "extra_bold")

                        val annotatedText = remember(currentArticle.content, place1MultiMap, place2MultiMap, isExtraBold, isReaderNightMode) {
                            buildPlaceHighlightedAnnotatedString(
                                content = currentArticle.content,
                                place1MultiMap = place1MultiMap,
                                place2MultiMap = place2MultiMap,
                                isExtraBold = isExtraBold,
                                isNightMode = isReaderNightMode
                            )
                        }

                        // Content LazyColumn
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = readerPaddingDp.dp.coerceAtLeast(6.dp)),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
                        ) {
                            // Article Title and Metadata Header
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    val isTitleBengali = isBengaliText(currentArticle.title)
                                    Text(
                                        text = currentArticle.title,
                                        fontFamily = if (isTitleBengali) AikyaFontFamily else PoppinsFontFamily,
                                        fontSize = (readerFontSize * 1.45f).coerceAtLeast(22f).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        lineHeight = ((readerFontSize * 1.45f).coerceAtLeast(22f) * 1.25f).sp
                                    )

                                    // Minimal Iconized Metadata Row (Reading time, words, author)
                                    val wordCount = currentArticle.content.split(Regex("""\s+""")).count { it.isNotBlank() }
                                    val estReadingMinutes = (wordCount / 180).coerceAtLeast(1)
                                    val displayAuthor = currentArticle.author.trim()
                                    val hasValidAuthor = displayAuthor.isNotBlank() && !displayAuthor.equals("Anonymous Author", ignoreCase = true)

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = subtleMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "${estReadingMinutes}m",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 11.5.sp,
                                                color = subtleMuted
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.TextFields,
                                                contentDescription = null,
                                                tint = subtleMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "$wordCount w",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 11.5.sp,
                                                color = subtleMuted
                                            )
                                        }

                                        if (hasValidAuthor) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = subtleMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = displayAuthor,
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 11.5.sp,
                                                    color = subtleMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        thickness = 1.dp,
                                        color = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            // Clean Article Body (No broken drop-cap Row side-by-side that broke wrapping!)
                            item {
                                ClickableText(
                                    text = annotatedText,
                                    style = TextStyle(
                                        fontSize = readerFontSize.sp,
                                        lineHeight = (readerFontSize * readerLineSpacing).sp,
                                        color = textColor,
                                        fontFamily = selectedFont,
                                        textAlign = if (readerJustify) TextAlign.Justify else TextAlign.Start
                                    ),
                                    onClick = { offset ->
                                        annotatedText.getStringAnnotations(
                                            tag = "VOCAB_MATCH",
                                            start = offset,
                                            end = offset
                                        ).firstOrNull()?.let { annotation ->
                                            val key = annotation.item.lowercase(Locale.ROOT)
                                            val matchedList = place1MultiMap[key] ?: place2MultiMap[key] ?: emptyList()
                                            if (matchedList.isNotEmpty()) {
                                                matchedWordsForPopup = matchedList
                                                popupWordIndex = 0
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Footer: Subtle Centered Page Indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showReadingControlsSheet = true }
                                .padding(top = 4.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Page ${currentIndex + 1} of ${articles.size}",
                                fontFamily = FontFamily.Serif,
                                fontSize = 12.sp,
                                color = subtleMuted,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Touch outside to dismiss popup modal (touches on main screen close popup)
                if (selectedWordForDetails != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                matchedWordsForPopup = emptyList()
                                popupWordIndex = 0
                            }
                    )
                }

                // Minimal Light & Soft Vocabulary Popup Modal (strictly scoped to the reader view)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedWordForDetails != null,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    val word = selectedWordForDetails
                    if (word != null) {
                        val cardBgColor = if (isReaderNightMode) Color(0xFF1E293B) else Color(0xFFFFFFFF)
                        val cardBorder = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                        val primaryTextColor = if (isReaderNightMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                        val meaningTextColor = if (isReaderNightMode) Color(0xFF34D399) else Color(0xFF0D9488)
                        val secondaryTextColor = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        val badgeBg = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9)
                        val badgeText = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

                        // Latest status from active words list
                        val latestStatus = words.firstOrNull { it.id == word.id }?.status ?: word.status
                        val currentCourse = courses.firstOrNull { it.id == word.courseId }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 440.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* Consume clicks inside card so outside dismiss is not triggered */ },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Header: Word, Pronounce, Group badge, Match Navigator, Block & Close
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Word + Audio + Group
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        val displayWord = getPlace1(word).ifBlank { word.word }
                                        Text(
                                            text = displayWord,
                                            fontFamily = selectArticleFontForText(displayWord),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        IconButton(
                                            onClick = { tts?.speak(displayWord, TextToSpeech.QUEUE_FLUSH, null, "tts_article") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Pronounce word",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "G${word.group}",
                                                fontSize = 10.sp,
                                                color = badgeText,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Right: Multi-match switcher + Block + Close
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (matchedWordsForPopup.size > 1) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9))
                                                    .padding(horizontal = 2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { if (popupWordIndex > 0) popupWordIndex-- },
                                                    enabled = popupWordIndex > 0,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Prev match",
                                                        tint = if (popupWordIndex > 0) IndigoPrimary else secondaryTextColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "${popupWordIndex + 1}/${matchedWordsForPopup.size}",
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndigoPrimary
                                                )
                                                IconButton(
                                                    onClick = { if (popupWordIndex < matchedWordsForPopup.size - 1) popupWordIndex++ },
                                                    enabled = popupWordIndex < matchedWordsForPopup.size - 1,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Next match",
                                                        tint = if (popupWordIndex < matchedWordsForPopup.size - 1) IndigoPrimary else secondaryTextColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { wordToBlockConfirmation = word },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Block,
                                                contentDescription = "Block word",
                                                tint = RoseError.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                matchedWordsForPopup = emptyList()
                                                popupWordIndex = 0
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = secondaryTextColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Optional Course badge sub-line
                                if (currentCourse != null) {
                                    Text(
                                        text = currentCourse.title,
                                        fontSize = 10.5.sp,
                                        color = IndigoPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 1.dp, bottom = 2.dp)
                                    )
                                }

                                // Place 2: Meaning / Definition
                                val displayMeaning = getPlace2(word).ifBlank { word.meaning }
                                Text(
                                    text = displayMeaning,
                                    fontFamily = selectArticleFontForText(displayMeaning),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = meaningTextColor,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                if (!word.example.isNullOrBlank()) {
                                    Text(
                                        text = "“${word.example}”",
                                        fontFamily = selectArticleFontForText(word.example),
                                        fontSize = 11.5.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = secondaryTextColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }

                                if (!word.synonyms.isNullOrBlank()) {
                                    Text(
                                        text = "Synonyms: ${word.synonyms}",
                                        fontSize = 10.5.sp,
                                        color = secondaryTextColor.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Minimal, Iconized Rating Buttons: Know, Review, Learn
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 1. Know
                                    val isKnow = latestStatus.equals("know", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "know")
                                            if (popupWordIndex < matchedWordsForPopup.size - 1) {
                                                popupWordIndex++
                                            } else {
                                                matchedWordsForPopup = emptyList()
                                                popupWordIndex = 0
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isKnow) Color(0xFF10B981) else if (isReaderNightMode) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFECFDF5),
                                            contentColor = if (isKnow) Color.White else if (isReaderNightMode) Color(0xFF6EE7B7) else Color(0xFF047857)
                                        ),
                                        border = if (isKnow) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFF047857) else Color(0xFFA7F3D0)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Know", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // 2. Review
                                    val isConfused = latestStatus.equals("confusion", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "confusion")
                                            if (popupWordIndex < matchedWordsForPopup.size - 1) {
                                                popupWordIndex++
                                            } else {
                                                matchedWordsForPopup = emptyList()
                                                popupWordIndex = 0
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isConfused) Color(0xFFF59E0B) else if (isReaderNightMode) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB),
                                            contentColor = if (isConfused) Color.White else if (isReaderNightMode) Color(0xFFFCD34D) else Color(0xFFB45309)
                                        ),
                                        border = if (isConfused) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFFB45309) else Color(0xFFFDE68A)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Review", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // 3. Learn
                                    val isDontKnow = latestStatus.equals("dont_know", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "dont_know")
                                            if (popupWordIndex < matchedWordsForPopup.size - 1) {
                                                popupWordIndex++
                                            } else {
                                                matchedWordsForPopup = emptyList()
                                                popupWordIndex = 0
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDontKnow) Color(0xFFF43F5E) else if (isReaderNightMode) Color(0xFF881337).copy(alpha = 0.4f) else Color(0xFFFFF1F2),
                                            contentColor = if (isDontKnow) Color.White else if (isReaderNightMode) Color(0xFFFDA4AF) else Color(0xFFBE123C)
                                        ),
                                        border = if (isDontKnow) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFFBE123C) else Color(0xFFFECDD3)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Learn", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Upload Article Dialog (Editable Title and Author name by default, supports Option 1 format & Google Docs)
    if (isAdding) {
        ArticleEditorDialog(
            initialTitle = "Daily Reading Passage",
            initialAuthor = "Anonymous Author",
            initialContent = "",
            isEditing = false,
            initialTab = if (showAddDialogFromParent) initialAddDialogTabFromParent else localAddDialogTab,
            onDismiss = {
                showAddDialog = false
                onDismissAddDialog()
            },
            onSaveSingle = { title, content, author ->
                onSaveArticle(title, content, author, null)
                showAddDialog = false
                onDismissAddDialog()
            },
            onSaveBatch = { parsedArticles ->
                if (parsedArticles.size == 1) {
                    val single = parsedArticles.first()
                    onSaveArticle(single.title, single.content, single.author, null)
                } else {
                    onSaveArticlesBatch(parsedArticles.map { Triple(it.title, it.content, it.author) })
                }
                showAddDialog = false
                onDismissAddDialog()
            }
        )
    }

    // Edit Current Article Dialog (Title & Author & Content editable)
    if (showEditDialog && currentArticle != null) {
        ArticleEditorDialog(
            initialTitle = currentArticle.title,
            initialAuthor = currentArticle.author,
            initialContent = currentArticle.content,
            isEditing = true,
            onDismiss = { showEditDialog = false },
            onSaveSingle = { title, content, author ->
                onSaveArticle(title, content, author, currentArticle.id)
                showEditDialog = false
            },
            onSaveBatch = { parsedArticles ->
                if (parsedArticles.isNotEmpty()) {
                    val single = parsedArticles.first()
                    onSaveArticle(single.title, single.content, single.author, currentArticle.id)
                }
                showEditDialog = false
            }
        )
    }

    // Block Word Confirmation Dialog
    if (wordToBlockConfirmation != null) {
        val wordToBlock = wordToBlockConfirmation!!
        val wordLabel = getPlace1(wordToBlock).ifBlank { wordToBlock.word }
        Dialog(
            onDismissRequest = { wordToBlockConfirmation = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp)
                    .widthIn(max = 380.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFE4E6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = RoseError,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Block Word?",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SlateText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Are you sure you want to block \"$wordLabel\"? This word will no longer be highlighted or matched in articles.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = SlateMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { wordToBlockConfirmation = null },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val wRaw = wordToBlock.word.trim().lowercase(Locale.ROOT)
                                val p1Raw = getPlace1(wordToBlock).trim().lowercase(Locale.ROOT)
                                val updatedBlocked = blockedWordsSet.toMutableSet().apply {
                                    if (wRaw.isNotBlank()) add(wRaw)
                                    if (p1Raw.isNotBlank()) add(p1Raw)
                                }
                                blockedWordsSet = updatedBlocked
                                blockedWordsPrefs.edit().putStringSet("blocked_words", updatedBlocked).apply()
                                matchedWordsForPopup = emptyList()
                                popupWordIndex = 0
                                wordToBlockConfirmation = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Block", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Article Sync Dialog
    if (isSyncingDialogVisible) {
        var inputUrl by remember(syncUrl) { mutableStateOf(syncUrl) }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        Dialog(
            onDismissRequest = {
                showSyncDialog = false
                onDismissSyncDialog()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(IndigoLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(17.dp))
                            }
                            Text(
                                text = "Synchronize Articles",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = SlateText
                            )
                        }

                        IconButton(
                            onClick = {
                                showSyncDialog = false
                                onDismissSyncDialog()
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(SlateLight)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMuted, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Sync articles from your Google Doc or source text. Matching titles will be updated, new articles will be added, and deleted articles will never be re-synced.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Google Doc Link or Raw Text") },
                        placeholder = { Text("https://docs.google.com/document/d/...") },
                        trailingIcon = {
                            IconButton(onClick = {
                                try {
                                    val clip = clipboardManager?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasteText.isNotBlank()) {
                                            inputUrl = pasteText
                                        }
                                    }
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Link", tint = IndigoPrimary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSyncDialog = false
                                onDismissSyncDialog()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("Cancel", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (inputUrl.isNotBlank()) {
                                    onSetSyncUrl(inputUrl.trim())
                                    onSync(inputUrl.trim())
                                    showSyncDialog = false
                                    onDismissSyncDialog()
                                }
                            },
                            enabled = inputUrl.isNotBlank() && !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Reader Controls Bottom Sheet (Matches the minimalist aesthetic in user reference)
    if (showReadingControlsSheet && currentArticle != null) {
        val currentIndex = articles.indexOfFirst { it.id == currentArticle.id }.coerceAtLeast(0)
        ModalBottomSheet(
            onDismissRequest = { showReadingControlsSheet = false },
            containerColor = if (isReaderNightMode) Color(0xFF1E293B) else Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (isReaderNightMode) Color(0xFF475569) else Color(0xFFCBD5E1))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Top Circular/Pill Action Buttons (Headphones, Theme, Bookmark, Font)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Headphones (Audio Reading / TTS)
                    IconButton(
                        onClick = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                tts?.speak(currentArticle.content, TextToSpeech.QUEUE_FLUSH, null, "article_tts")
                                isSpeaking = true
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isSpeaking) IndigoPrimary else (if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Audio Reading",
                            tint = if (isSpeaking) Color.White else (if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF475569)),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Night / Paper Mode
                    IconButton(
                        onClick = {
                            isReaderNightMode = !isReaderNightMode
                            readerPrefs.edit().putBoolean("reader_night_mode", isReaderNightMode).apply()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isReaderNightMode) Color(0xFF312E81) else Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            if (isReaderNightMode) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                            contentDescription = "Toggle Theme",
                            tint = if (isReaderNightMode) Color(0xFFFDE047) else Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Bookmark
                    IconButton(
                        onClick = { isBookmarked = !isBookmarked },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isBookmarked) Color(0xFFFEF3C7) else (if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Color(0xFFD97706) else (if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF475569)),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Font Style (Serif vs Sans)
                    IconButton(
                        onClick = {
                            readerFontFamily = if (readerFontFamily == "serif") "sans" else "serif"
                            readerPrefs.edit().putString("reader_font_family", readerFontFamily).apply()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (readerFontFamily == "serif") IndigoLight else (if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                    ) {
                        Icon(
                            Icons.Default.FontDownload,
                            contentDescription = "Toggle Font",
                            tint = if (readerFontFamily == "serif") IndigoPrimary else (if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF475569)),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 2. Chapters Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Chapters",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF0F172A)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF8FAFC))
                            .border(1.dp, if (isReaderNightMode) Color(0xFF475569) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .clickable { showChaptersDropdown = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentIndex + 1}   ${currentArticle.title}",
                                fontFamily = FontFamily.Serif,
                                fontSize = 13.5.sp,
                                color = if (isReaderNightMode) Color.White else Color(0xFF1E293B),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }

                        DropdownMenu(
                            expanded = showChaptersDropdown,
                            onDismissRequest = { showChaptersDropdown = false },
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            articles.forEachIndexed { idx, art ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${idx + 1}. ${art.title}",
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 13.sp,
                                            fontWeight = if (idx == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                            color = if (idx == currentIndex) IndigoPrimary else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        onSelectArticle(art)
                                        showChaptersDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Text Size & Scrolling Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Text Size
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Text Size",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val sizes = listOf(14f, 17f, 21f)
                            sizes.forEach { sz ->
                                val isSelected = kotlin.math.abs(readerFontSize - sz) < 1.5f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) (if (isReaderNightMode) Color(0xFF1E293B) else Color.White) else Color.Transparent)
                                        .clickable {
                                            readerFontSize = sz
                                            readerPrefs.edit().putFloat("reader_font_size", sz).apply()
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        fontFamily = FontFamily.Serif,
                                        fontSize = (sz * 0.9f).sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) IndigoPrimary else (if (isReaderNightMode) Color(0xFFCBD5E1) else Color(0xFF475569))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Right Column: Scrolling
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Scrolling",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isReaderNightMode) Color(0xFF334155) else Color(0xFFF1F5F9))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isContinuousScroll) "Continuous" else "Paged",
                                fontSize = 11.5.sp,
                                color = if (isReaderNightMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                            )
                            Switch(
                                checked = isContinuousScroll,
                                onCheckedChange = {
                                    isContinuousScroll = it
                                    readerPrefs.edit().putBoolean("reader_continuous_scroll", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IndigoPrimary
                                )
                            )
                        }
                    }
                }

                // 4. Fine-tuning Slider with Sun and Moon icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )

                    Slider(
                        value = readerFontSize,
                        onValueChange = {
                            readerFontSize = it
                            readerPrefs.edit().putFloat("reader_font_size", it).apply()
                        },
                        valueRange = 13f..26f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPrimary,
                            activeTrackColor = IndigoPrimary,
                            inactiveTrackColor = if (isReaderNightMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )
                    )

                    Icon(
                        Icons.Default.NightlightRound,
                        contentDescription = null,
                        tint = if (isReaderNightMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Reader Display Settings Dialog (English, Minimalist layout, font size, padding, alignment)
    if (isReaderSettingsVisible) {
        ReaderSettingsDialog(
            fontSize = readerFontSize,
            paddingDp = readerPaddingDp,
            lineSpacing = readerLineSpacing,
            fontFamily = readerFontFamily,
            highlightBold = readerHighlightBold,
            isJustified = readerJustify,
            onSave = { newSize, newPadding, newSpacing, newFont, newBold, newJust ->
                readerFontSize = newSize
                readerPaddingDp = newPadding
                readerLineSpacing = newSpacing
                readerFontFamily = newFont
                readerHighlightBold = newBold
                readerJustify = newJust
                readerPrefs.edit()
                    .putFloat("reader_font_size", newSize)
                    .putInt("reader_padding_dp", newPadding)
                    .putFloat("reader_line_spacing", newSpacing)
                    .putString("reader_font_family", newFont)
                    .putString("reader_highlight_bold", newBold)
                    .putBoolean("reader_justify", newJust)
                    .apply()
            },
            onDismiss = {
                showLocalReaderSettings = false
                onDismissReaderSettings()
            }
        )
    }

    // Course Selection Filter Dialog for Word Matching
    if (showCourseFilterDialog) {
        CourseFilterDialog(
            courses = courses,
            selectedCourseIds = selectedCourseIds,
            words = words,
            onSelectCourses = { newSelection ->
                selectedCourseIds = newSelection
                coursePrefs.edit().putStringSet("selected_courses", newSelection).apply()
            },
            onAddSampleData = onAddSampleData,
            onDismiss = { showCourseFilterDialog = false }
        )
    }
}

/**
 * Builds an AnnotatedString that highlights words matching Place 1 (Word) or Place 2 (Meaning).
 * Normal text with color - no background highlight, no underline. Bolded for high visibility.
 * Prevents duplicate highlighting of the same word in the same article.
 * Uses Aikya font for Bengali text.
 */
private fun buildPlaceHighlightedAnnotatedString(
    content: String,
    place1MultiMap: Map<String, List<VocabularyWordEntity>>,
    place2MultiMap: Map<String, List<VocabularyWordEntity>>,
    isExtraBold: Boolean = false,
    isNightMode: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""[\w\u0980-\u09FF]+|[^\w\s\u0980-\u09FF]+|\s+""")
        val matches = regex.findAll(content)

        val targetWeight = if (isExtraBold) FontWeight.Black else FontWeight.ExtraBold
        val place1Color = if (isNightMode) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
        val place2Color = if (isNightMode) Color(0xFF34D399) else Color(0xFF047857)

        // Set to ensure a word is only highlighted ONCE per article
        val seenKeys = mutableSetOf<String>()

        for (m in matches) {
            val token = m.value
            val cleanToken = token.trim().lowercase(Locale.ROOT)
            val isPlace1 = place1MultiMap.containsKey(cleanToken)
            val isPlace2 = place2MultiMap.containsKey(cleanToken)
            val isAlreadySeen = seenKeys.contains(cleanToken)

            if (isPlace1 && !isAlreadySeen) {
                seenKeys.add(cleanToken)
                val isBengali = isBengaliText(token)
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = place1Color,
                        fontWeight = targetWeight,
                        fontFamily = if (isBengali) AikyaFontFamily else PoppinsFontFamily,
                        fontSynthesis = FontSynthesis.All
                    )
                ) {
                    append(token)
                }
                pop()
            } else if (isPlace2 && !isAlreadySeen) {
                seenKeys.add(cleanToken)
                val isBengali = isBengaliText(token)
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = place2Color,
                        fontWeight = targetWeight,
                        fontFamily = if (isBengali) AikyaFontFamily else PoppinsFontFamily,
                        fontSynthesis = FontSynthesis.All
                    )
                ) {
                    append(token)
                }
                pop()
            } else {
                val isBengali = isBengaliText(token)
                withStyle(
                    SpanStyle(
                        fontFamily = if (isBengali) AikyaFontFamily else PoppinsFontFamily,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append(token)
                }
            }
        }
    }
}

@Composable
private fun ArticleEditorDialog(
    initialTitle: String,
    initialAuthor: String,
    initialContent: String,
    isEditing: Boolean,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSaveSingle: (title: String, content: String, author: String) -> Unit,
    onSaveBatch: (List<ParsedArticleItem>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 0: Single, 1: Bulk URLs
    var selectedTab by remember { mutableIntStateOf(if (isEditing) 0 else initialTab.coerceIn(0, 1)) }

    // Single / Text states
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var content by remember { mutableStateOf(initialContent) }

    // Bulk URLs states
    var bulkUrlsInput by remember { mutableStateOf("") }
    var isBatchImportingBulk by remember { mutableStateOf(false) }
    var bulkImportProgress by remember { mutableStateOf(0 to 0) }
    var bulkErrorMessage by remember { mutableStateOf<String?>(null) }

    val detectedBulkUrls = remember(bulkUrlsInput) {
        val regex = Regex("""https?://[^\s,;"'<>]+""")
        regex.findAll(bulkUrlsInput)
            .map { it.value.trim().trimEnd('.', ',', ';') }
            .distinct()
            .toList()
    }

    // Single Tab: Web URL state
    var singleWebUrl by remember { mutableStateOf("") }
    var isExtractingSingleWeb by remember { mutableStateOf(false) }
    var singleWebErrorMessage by remember { mutableStateOf<String?>(null) }

    // Live parse detection for manual text
    val liveParsedArticles = remember(content, title, author) {
        if (content.isBlank()) emptyList()
        else ArticleParser.parseArticles(content, title, author)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "Imported Article"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx).substringBeforeLast(".")
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val fileText = stream.bufferedReader().readText()
                    val parsed = ArticleParser.parseArticles(fileText, fileName, "Anonymous Author")
                    if (parsed.size == 1 && !fileText.contains("#") && !fileText.contains("---")) {
                        title = parsed.first().title
                        author = parsed.first().author
                        content = parsed.first().content
                    } else {
                        content = fileText
                        if (parsed.size == 1) {
                            title = parsed.first().title
                            author = parsed.first().author
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Article" else "Import Article",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SlateText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SlateLight)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!isEditing) {
                    // Minimal Segmented Pill Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to ("Single" to Icons.Default.Description),
                            1 to ("Bulk" to Icons.Default.FormatListBulleted)
                        ).forEach { (tabIndex, pair) ->
                            val (label, icon) = pair
                            val isSelected = selectedTab == tabIndex
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTab = tabIndex },
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) IndigoPrimary else SlateMuted
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) IndigoPrimary else SlateMuted
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                    1 -> {
                        OutlinedTextField(
                            value = bulkUrlsInput,
                            onValueChange = {
                                bulkUrlsInput = it
                                bulkErrorMessage = null
                            },
                            label = { Text("URLs") },
                            placeholder = { Text("Paste links, one per line...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            maxLines = 8
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (detectedBulkUrls.isNotEmpty()) IndigoLight else SlateLight
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (detectedBulkUrls.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (detectedBulkUrls.isNotEmpty()) IndigoPrimary else SlateMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${detectedBulkUrls.size} URLs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (detectedBulkUrls.isNotEmpty()) IndigoPrimary else SlateMuted
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val clipService = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipText = clipService?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clipText.isNullOrBlank()) {
                                                bulkUrlsInput = if (bulkUrlsInput.isBlank()) clipText.trim()
                                                else bulkUrlsInput.trim() + "\n" + clipText.trim()
                                                bulkErrorMessage = null
                                            }
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp), tint = IndigoPrimary)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Paste", fontSize = 11.sp, color = IndigoPrimary)
                                }

                                if (bulkUrlsInput.isNotBlank()) {
                                    TextButton(
                                        onClick = { bulkUrlsInput = "" },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = SlateMuted)
                                    }
                                }
                            }
                        }

                        if (bulkErrorMessage != null) {
                            Text(text = bulkErrorMessage ?: "", color = RoseError, fontSize = 11.sp)
                        }

                        if (isBatchImportingBulk) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Importing ${bulkImportProgress.first} of ${bulkImportProgress.second}...",
                                    fontSize = 11.sp,
                                    color = IndigoPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (bulkImportProgress.second > 0)
                                            bulkImportProgress.first.toFloat() / bulkImportProgress.second.toFloat()
                                        else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = IndigoPrimary
                                )
                            }
                        }
                    }

                    0 -> {
                        // Single Article
                        if (!isEditing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = singleWebUrl,
                                    onValueChange = {
                                        singleWebUrl = it
                                        singleWebErrorMessage = null
                                    },
                                    label = { Text("Link") },
                                    placeholder = { Text("Paste article link") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateMuted)
                                    },
                                    trailingIcon = {
                                        if (isExtractingSingleWeb) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = IndigoPrimary)
                                        } else if (singleWebUrl.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    isExtractingSingleWeb = true
                                                    singleWebErrorMessage = null
                                                    coroutineScope.launch {
                                                        val fetchResult = ArticleParser.fetchWebArticle(singleWebUrl.trim())
                                                        isExtractingSingleWeb = false
                                                        fetchResult.fold(
                                                            onSuccess = { item ->
                                                                title = item.title
                                                                author = item.author
                                                                content = item.content
                                                                singleWebUrl = ""
                                                            },
                                                            onFailure = { err ->
                                                                singleWebErrorMessage = err.message ?: "Failed to extract link"
                                                            }
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Fetch", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                try {
                                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    val txt = clip?.primaryClip?.getItemAt(0)?.text?.toString()
                                                    if (!txt.isNullOrBlank()) {
                                                        singleWebUrl = txt.trim()
                                                        singleWebErrorMessage = null
                                                    }
                                                } catch (_: Exception) {}
                                            }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                )

                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = "File", modifier = Modifier.size(17.dp), tint = IndigoPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("File", fontSize = 11.5.sp, color = IndigoPrimary)
                                }
                            }

                            if (singleWebErrorMessage != null) {
                                Text(singleWebErrorMessage ?: "", color = RoseError, fontSize = 11.sp)
                            }
                        }

                        if (!isEditing && liveParsedArticles.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = IndigoLight
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${liveParsedArticles.size} articles found in text", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IndigoPrimary)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Title") },
                                    placeholder = { Text("Title") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )

                                OutlinedTextField(
                                    value = author,
                                    onValueChange = { author = it },
                                    label = { Text("Author") },
                                    placeholder = { Text("Author") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Content *") },
                            placeholder = { Text("Paste or type text...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10
                        )
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

                // Footer Row with Cancel and Primary action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    when (selectedTab) {
                        1 -> {
                            Button(
                                onClick = {
                                    if (detectedBulkUrls.isNotEmpty()) {
                                        isBatchImportingBulk = true
                                        bulkErrorMessage = null
                                        bulkImportProgress = 0 to detectedBulkUrls.size
                                        coroutineScope.launch {
                                            val fetched = ArticleParser.fetchBulkWebArticles(detectedBulkUrls) { cur, tot, _ ->
                                                bulkImportProgress = cur to tot
                                            }
                                            isBatchImportingBulk = false
                                            if (fetched.isNotEmpty()) {
                                                onSaveBatch(fetched)
                                            } else {
                                                bulkErrorMessage = "Could not extract articles from the provided links."
                                            }
                                        }
                                    }
                                },
                                enabled = detectedBulkUrls.isNotEmpty() && !isBatchImportingBulk,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text(
                                    if (isBatchImportingBulk) "Importing..."
                                    else "Import (${detectedBulkUrls.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    if (content.isNotBlank()) {
                                        val parsed = ArticleParser.parseArticles(content, title, author)
                                        if (parsed.size > 1) {
                                            onSaveBatch(parsed)
                                        } else if (parsed.size == 1) {
                                            val single = parsed.first()
                                            onSaveSingle(single.title, single.content, single.author)
                                        } else {
                                            onSaveSingle(
                                                title.trim().ifEmpty { "New Article" },
                                                content.trim(),
                                                author.trim().ifEmpty { "Anonymous Author" }
                                            )
                                        }
                                    }
                                },
                                enabled = content.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text(
                                    if (isEditing) "Save" else "Save & Read",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun ReaderSettingsDialog(
    fontSize: Float,
    paddingDp: Int,
    lineSpacing: Float,
    fontFamily: String,
    highlightBold: String,
    isJustified: Boolean,
    onSave: (fontSize: Float, paddingDp: Int, lineSpacing: Float, fontFamily: String, highlightBold: String, isJustified: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFontSize by remember { mutableFloatStateOf(fontSize) }
    var tempPaddingDp by remember { mutableIntStateOf(paddingDp) }
    var tempLineSpacing by remember { mutableFloatStateOf(lineSpacing) }
    var tempFontFamily by remember { mutableStateOf(fontFamily) }
    var tempHighlightBold by remember { mutableStateOf(highlightBold) }
    var tempJustified by remember { mutableStateOf(isJustified) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with close button
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
                            Icon(Icons.Default.Tune, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(17.dp))
                        }
                        Text(
                            text = "Reader Settings",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = SlateText
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SlateLight)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMuted, modifier = Modifier.size(15.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                // Font Size Control
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateText
                        )
                        Text(
                            text = "${tempFontSize.toInt()} sp",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                    Slider(
                        value = tempFontSize,
                        onValueChange = { tempFontSize = it },
                        valueRange = 13f..26f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPrimary,
                            activeTrackColor = IndigoPrimary
                        )
                    )
                }

                // Screen Margin / Padding
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Screen Padding",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Compact", 4, "4 dp"),
                            Triple("Normal", 10, "10 dp"),
                            Triple("Spacious", 18, "18 dp")
                        ).forEach { (label, padVal, _) ->
                            val isSelected = tempPaddingDp == padVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) IndigoLight else Color(0xFFF1F5F9))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) IndigoPrimary else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempPaddingDp = padVal }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else SlateText
                                )
                            }
                        }
                    }
                }

                // Line Spacing
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Line Spacing",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("Tight", 1.35f),
                            Pair("Normal", 1.6f),
                            Pair("Relaxed", 1.9f)
                        ).forEach { (label, mult) ->
                            val isSelected = Math.abs(tempLineSpacing - mult) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) IndigoLight else Color(0xFFF1F5F9))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) IndigoPrimary else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempLineSpacing = mult }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else SlateText
                                )
                            }
                        }
                    }
                }

                // Text Alignment
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Alignment",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isLeftSelected = !tempJustified
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLeftSelected) IndigoLight else Color(0xFFF1F5F9))
                                .border(
                                    width = if (isLeftSelected) 1.5.dp else 1.dp,
                                    color = if (isLeftSelected) IndigoPrimary else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { tempJustified = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Left Align",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = if (isLeftSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLeftSelected) IndigoPrimary else SlateText
                            )
                        }

                        val isJustifySelected = tempJustified
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isJustifySelected) IndigoLight else Color(0xFFF1F5F9))
                                .border(
                                    width = if (isJustifySelected) 1.5.dp else 1.dp,
                                    color = if (isJustifySelected) IndigoPrimary else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { tempJustified = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Justify",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = if (isJustifySelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isJustifySelected) IndigoPrimary else SlateText
                            )
                        }
                    }
                }

                // Word Highlight Boldness Emphasis
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Highlight Boldness",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("Bold", "bold"),
                            Pair("Extra Bold", "extra_bold")
                        ).forEach { (label, mode) ->
                            val isSelected = tempHighlightBold == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) IndigoLight else Color(0xFFF1F5F9))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) IndigoPrimary else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempHighlightBold = mode }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) IndigoPrimary else SlateText
                                )
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Close", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            onSave(tempFontSize, tempPaddingDp, tempLineSpacing, tempFontFamily, tempHighlightBold, tempJustified)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("Apply", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseFilterDialog(
    courses: List<CourseEntity>,
    selectedCourseIds: Set<String>,
    words: List<VocabularyWordEntity>,
    onSelectCourses: (Set<String>) -> Unit,
    onAddSampleData: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var tempSelected by remember { mutableStateOf(selectedCourseIds) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = "Match Courses",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = SlateText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (tempSelected.size == courses.size) {
                                    tempSelected = emptySet()
                                } else {
                                    tempSelected = courses.map { it.id.toString() }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (tempSelected.size == courses.size) "Deselect All" else "Select All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(SlateLight)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = SlateMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Words from selected courses will be matched and highlighted in articles.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = SlateMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {

                if (courses.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No courses available",
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                        Button(
                            onClick = {
                                onAddSampleData()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Sample Courses", fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(courses, key = { it.id }) { course ->
                            val courseIdStr = course.id.toString()
                            val isSelected = tempSelected.contains(courseIdStr)
                            val courseWordCount = words.count { it.courseId == course.id }

                            Surface(
                                onClick = {
                                    tempSelected = if (isSelected) {
                                        tempSelected - courseIdStr
                                    } else {
                                        tempSelected + courseIdStr
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) IndigoLight else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) IndigoPrimary else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = course.title,
                                            fontFamily = selectArticleFontForText(course.title),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) IndigoPrimary else SlateText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$courseWordCount words",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            color = SlateMuted
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            tempSelected = if (checked) {
                                                tempSelected + courseIdStr
                                            } else {
                                                tempSelected - courseIdStr
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = IndigoPrimary,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMuted),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Cancel", fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            onSelectCourses(tempSelected)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("Apply", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

