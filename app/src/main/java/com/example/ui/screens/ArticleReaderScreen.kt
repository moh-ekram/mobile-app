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
    onSync: (String) -> Unit = {},
    onSetSyncUrl: (String) -> Unit = {},
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit,
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
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
            onSync = onSync,
            onSetSyncUrl = onSetSyncUrl,
            onSelectArticle = onSelectArticle,
            onSaveArticle = onSaveArticle,
            onSaveArticlesBatch = onSaveArticlesBatch,
            onDeleteArticle = onDeleteArticle,
            onRateWord = onRateWord,
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
    onSync: (String) -> Unit = {},
    onSetSyncUrl: (String) -> Unit = {},
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit,
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
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
    var selectedCourseIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedWordForDetails by remember { mutableStateOf<VocabularyWordEntity?>(null) }

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
        selectedWordForDetails = null
    }

    BackHandler(enabled = currentArticle != null || selectedWordForDetails != null) {
        if (selectedWordForDetails != null) {
            selectedWordForDetails = null
        } else {
            onSelectArticle(null)
        }
    }

    // Filter vocabulary words based on course selection
    val activeWords = remember(words, selectedCourseIds) {
        if (selectedCourseIds.isEmpty()) {
            words
        } else {
            words.filter { selectedCourseIds.contains(it.courseId) }
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

    // Place1 (Word) and Place2 (Meaning) lookup maps based on activeWords
    val place1Map = remember(activeWords) {
        val map = mutableMapOf<String, VocabularyWordEntity>()
        activeWords.forEach { w ->
            if (w.word.isNotBlank()) {
                map[w.word.trim().lowercase(Locale.ROOT)] = w
            }
            val p1 = getPlace1(w)
            if (p1.isNotBlank()) {
                map[p1.trim().lowercase(Locale.ROOT)] = w
            }
        }
        map
    }

    val place2Map = remember(activeWords) {
        val map = mutableMapOf<String, VocabularyWordEntity>()
        activeWords.forEach { w ->
            if (w.meaning.isNotBlank()) {
                val trimmedMeaning = w.meaning.trim().lowercase(Locale.ROOT)
                map[trimmedMeaning] = w
                val parts = trimmedMeaning.split("[,;/]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
                parts.forEach { part ->
                    map[part] = w
                }
            }
            val p2 = getPlace2(w)
            if (p2.isNotBlank()) {
                val trimmedP2 = p2.trim().lowercase(Locale.ROOT)
                map[trimmedP2] = w
                val parts = trimmedP2.split("[,;/]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
                parts.forEach { part ->
                    map[part] = w
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

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        localAddDialogTab = 0
                                        showAddDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Article", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold)
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
                            Text(
                                text = "${articles.size} Articles",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateMuted
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (syncUrl.isNotBlank()) {
                                             onSync(syncUrl)
                                        } else {
                                             showSyncDialog = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp),
                                    enabled = !isSyncing
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = IndigoPrimary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = "Sync",
                                            modifier = Modifier.size(15.dp),
                                            tint = IndigoPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", fontSize = 12.sp, color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        localAddDialogTab = 1
                                        showAddDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = "Import URL",
                                        modifier = Modifier.size(15.dp),
                                        tint = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL", fontSize = 12.sp, color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        localAddDialogTab = 0
                                        showAddDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                // Minimal Settings Gear Icon right beside sync & add
                                IconButton(
                                    onClick = { showLocalReaderSettings = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Reader Display Settings",
                                        tint = SlateText,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Swipe right to edit  •  Swipe left to delete",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    items(articles, key = { it.id }) { art ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        onDeleteArticle(art.id)
                                        true
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
                                        fontFamily = PoppinsFontFamily,
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
                                        fontFamily = PoppinsFontFamily,
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
                        // Header: Minimal Back Button, Centered Chapter with Prev/Next, Right Reader Controls icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onSelectArticle(null) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to list",
                                    tint = subtleMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (currentIndex > 0) {
                                    IconButton(
                                        onClick = { onSelectArticle(articles[currentIndex - 1]) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Previous chapter",
                                            tint = subtleMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Chapter ${currentIndex + 1}",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = subtleMuted,
                                    letterSpacing = 1.2.sp
                                )

                                if (currentIndex < articles.size - 1) {
                                    IconButton(
                                        onClick = { onSelectArticle(articles[currentIndex + 1]) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next chapter",
                                            tint = subtleMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showReadingControlsSheet = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Reader Controls",
                                    tint = subtleMuted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Reading Content with Editorial Drop Cap & Interactive Words
                        val selectedFont = when (readerFontFamily) {
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            "sans" -> FontFamily.SansSerif
                            else -> FontFamily.Serif
                        }
                        val isExtraBold = (readerHighlightBold == "extra_bold")

                        val annotatedText = remember(currentArticle.content, place1Map, place2Map, isExtraBold, isReaderNightMode) {
                            buildPlaceHighlightedAnnotatedString(
                                content = currentArticle.content,
                                place1Map = place1Map,
                                place2Map = place2Map,
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
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                        ) {
                            item {
                                val trimmedText = currentArticle.content.trimStart()
                                val firstLetter = trimmedText.firstOrNull()

                                if (firstLetter != null && firstLetter.isLetter()) {
                                    // Classic Editorial Drop Cap layout
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = firstLetter.toString(),
                                            fontFamily = FontFamily.Serif,
                                            fontSize = (readerFontSize * 2.7f).sp,
                                            fontWeight = FontWeight.Normal,
                                            color = textColor,
                                            lineHeight = (readerFontSize * 2.2f).sp,
                                            modifier = Modifier.padding(end = 6.dp, top = 2.dp)
                                        )

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
                                                    val matchedWord = place1Map[key] ?: place2Map[key]
                                                    if (matchedWord != null) {
                                                        selectedWordForDetails = matchedWord
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else {
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
                                                val matchedWord = place1Map[key] ?: place2Map[key]
                                                if (matchedWord != null) {
                                                    selectedWordForDetails = matchedWord
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
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

                // Minimal Light & Soft Vocabulary Popup Modal (strictly scoped to the reader view)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedWordForDetails != null,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
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

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Header: Word (Place 1), Speak button, Group pill, Close button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        val displayWord = getPlace1(word).ifBlank { word.word }
                                        Text(
                                            text = displayWord,
                                            fontFamily = selectFontForText(displayWord),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        IconButton(
                                            onClick = { tts?.speak(displayWord, TextToSpeech.QUEUE_FLUSH, null, "tts_article") },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Pronounce word",
                                                tint = Color(0xFF6366F1),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "G${word.group}",
                                                fontSize = 11.sp,
                                                color = badgeText,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { selectedWordForDetails = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close popup",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Place 2: Meaning / Definition
                                val displayMeaning = getPlace2(word).ifBlank { word.meaning }
                                Text(
                                    text = displayMeaning,
                                    fontFamily = selectFontForText(displayMeaning),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = meaningTextColor,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                )

                                if (!word.example.isNullOrBlank()) {
                                    Text(
                                        text = "“${word.example}”",
                                        fontSize = 12.sp,
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
                                        fontSize = 11.sp,
                                        color = secondaryTextColor.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Functional Rating Action Buttons: Know, Confused, Don't Know
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Know Button
                                    val isKnow = latestStatus.equals("know", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "know")
                                            selectedWordForDetails = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isKnow) Color(0xFF10B981) else if (isReaderNightMode) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFECFDF5),
                                            contentColor = if (isKnow) Color.White else if (isReaderNightMode) Color(0xFF6EE7B7) else Color(0xFF047857)
                                        ),
                                        border = if (isKnow) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFF047857) else Color(0xFFA7F3D0)),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        if (isKnow) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text("Know", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // 2. Confused Button
                                    val isConfused = latestStatus.equals("confusion", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "confusion")
                                            selectedWordForDetails = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isConfused) Color(0xFFF59E0B) else if (isReaderNightMode) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB),
                                            contentColor = if (isConfused) Color.White else if (isReaderNightMode) Color(0xFFFCD34D) else Color(0xFFB45309)
                                        ),
                                        border = if (isConfused) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFFB45309) else Color(0xFFFDE68A)),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Text("Confused", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // 3. Don't Know Button
                                    val isDontKnow = latestStatus.equals("dont_know", ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onRateWord(word.id, "dont_know")
                                            selectedWordForDetails = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDontKnow) Color(0xFFF43F5E) else if (isReaderNightMode) Color(0xFF881337).copy(alpha = 0.4f) else Color(0xFFFFF1F2),
                                            contentColor = if (isDontKnow) Color.White else if (isReaderNightMode) Color(0xFFFDA4AF) else Color(0xFFBE123C)
                                        ),
                                        border = if (isDontKnow) null else BorderStroke(1.dp, if (isReaderNightMode) Color(0xFFBE123C) else Color(0xFFFECDD3)),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Text("Don't Know", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    // Equal Space Course Selection Grid Dialog
    if (showCourseFilterDialog) {
        AlertDialog(
            onDismissRequest = { showCourseFilterDialog = false },
            title = {
                Text(
                    text = "Filter Words by Course",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = SlateText
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select courses to highlight their vocabulary words in this article.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                selectedCourseIds = courses.map { it.id }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Select All", fontSize = 12.sp, color = IndigoPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                selectedCourseIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp, color = RoseError)
                        }
                    }

                    if (courses.isEmpty()) {
                        Text(
                            text = "No courses found. All vocabulary words are highlighted.",
                            fontSize = 13.sp,
                            color = SlateMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        // Equal space 2-column grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(courses, key = { it.id }) { course ->
                                val isSelected = selectedCourseIds.contains(course.id)
                                val count = words.count { it.courseId == course.id }
                                Surface(
                                    onClick = {
                                        selectedCourseIds = if (isSelected) {
                                            selectedCourseIds - course.id
                                        } else {
                                            selectedCourseIds + course.id
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) IndigoLight else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) IndigoPrimary else SlateBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(62.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = course.title,
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) IndigoPrimary else SlateText,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$count words",
                                                fontSize = 10.sp,
                                                color = SlateMuted
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
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
                    onClick = { showCourseFilterDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Article Sync Dialog
    if (isSyncingDialogVisible) {
        var inputUrl by remember(syncUrl) { mutableStateOf(syncUrl) }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        AlertDialog(
            onDismissRequest = {
                showSyncDialog = false
                onDismissSyncDialog()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = IndigoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Synchronize Articles",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SlateText
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sync articles from your Google Doc or source text. Matching titles will be updated, new articles will be added, and deleted articles will never be re-synced.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )

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
                }
            },
            confirmButton = {
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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSyncDialog = false
                    onDismissSyncDialog()
                }) {
                    Text("Cancel")
                }
            }
        )
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
}

/**
 * Builds an AnnotatedString that highlights words matching Place 1 (Word) or Place 2 (Meaning).
 * Normal text with color - no background highlight, no underline. Bolded for high visibility.
 * Automatically displays Bengali words in Kalpurush font.
 */
private fun buildPlaceHighlightedAnnotatedString(
    content: String,
    place1Map: Map<String, VocabularyWordEntity>,
    place2Map: Map<String, VocabularyWordEntity>,
    isExtraBold: Boolean = false,
    isNightMode: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""[\w\u0980-\u09FF]+|[^\w\s\u0980-\u09FF]+|\s+""")
        val matches = regex.findAll(content)

        val targetWeight = if (isExtraBold) FontWeight.Black else FontWeight.ExtraBold
        val place1Color = if (isNightMode) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
        val place2Color = if (isNightMode) Color(0xFF34D399) else Color(0xFF047857)

        for (m in matches) {
            val token = m.value
            val cleanToken = token.trim().lowercase(Locale.ROOT)
            val isPlace1 = place1Map.containsKey(cleanToken)
            val isPlace2 = place2Map.containsKey(cleanToken)

            if (isPlace1) {
                // Place 1 (Word) Highlight - Distinct Blue, Bold. No background, no underline!
                val isBengali = isBengaliText(token)
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = place1Color,
                        fontWeight = targetWeight,
                        fontFamily = if (isBengali) KalpurushFontFamily else PoppinsFontFamily,
                        fontSynthesis = FontSynthesis.All
                    )
                ) {
                    append(token)
                }
                pop()
            } else if (isPlace2) {
                // Place 2 (Meaning) Highlight - Distinct Green, Bold. No background, no underline!
                val isBengali = isBengaliText(token)
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = place2Color,
                        fontWeight = targetWeight,
                        fontFamily = if (isBengali) KalpurushFontFamily else PoppinsFontFamily,
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
                        fontFamily = if (isBengali) KalpurushFontFamily else PoppinsFontFamily,
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
    // 0: Text / File, 1: Web Link, 2: Google Doc
    var selectedTab by remember { mutableIntStateOf(if (isEditing) 0 else initialTab.coerceIn(0, 2)) }

    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var content by remember { mutableStateOf(initialContent) }

    // Web URL Import States
    var webArticleUrl by remember { mutableStateOf("") }
    var isExtractingWeb by remember { mutableStateOf(false) }
    var webErrorMessage by remember { mutableStateOf<String?>(null) }
    var extractedWebArticle by remember { mutableStateOf<ParsedArticleItem?>(null) }

    // Google Doc States
    var googleDocUrl by remember { mutableStateOf("") }
    var isFetchingDoc by remember { mutableStateOf(false) }
    var docErrorMessage by remember { mutableStateOf<String?>(null) }
    var docFetchedArticles by remember { mutableStateOf<List<ParsedArticleItem>>(emptyList()) }

    // Live parse detection for Tab 0
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Article" else "Add Article",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = SlateText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEditing) {
                    // 3-Tab Selector: Text/File, Web Link, Google Doc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateLight)
                            .padding(3.dp)
                    ) {
                        // Tab 0: Text / File
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = 0 },
                            color = if (selectedTab == 0) Color.White else Color.Transparent,
                            shadowElevation = if (selectedTab == 0) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedTab == 0) IndigoPrimary else SlateMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Text",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) IndigoPrimary else SlateMuted
                                )
                            }
                        }

                        // Tab 1: Web Link
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = 1 },
                            color = if (selectedTab == 1) Color.White else Color.Transparent,
                            shadowElevation = if (selectedTab == 1) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedTab == 1) IndigoPrimary else SlateMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Web Link",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) IndigoPrimary else SlateMuted
                                )
                            }
                        }

                        // Tab 2: Google Doc
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = 2 },
                            color = if (selectedTab == 2) Color.White else Color.Transparent,
                            shadowElevation = if (selectedTab == 2) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedTab == 2) IndigoPrimary else SlateMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Google Doc",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 2) IndigoPrimary else SlateMuted
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0 || isEditing) {
                    // TAB 0: Direct input or File Upload
                    if (!isEditing) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Text File (.txt)", fontSize = 13.sp)
                        }
                    }

                    // If multiple articles detected in content:
                    if (!isEditing && liveParsedArticles.size > 1) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = IndigoLight),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(IndigoPrimary.copy(alpha = 0.4f))
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${liveParsedArticles.size} Articles Detected!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                liveParsedArticles.take(4).forEachIndexed { idx, item ->
                                    Text(
                                        text = "${idx + 1}. ${item.title} (by ${item.author})",
                                        fontSize = 11.sp,
                                        color = SlateText,
                                        maxLines = 1
                                    )
                                }
                                if (liveParsedArticles.size > 4) {
                                    Text(
                                        text = "...and ${liveParsedArticles.size - 4} more",
                                        fontSize = 10.sp,
                                        color = SlateMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Article Title") },
                            placeholder = { Text("Enter article title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author Name") },
                            placeholder = { Text("Author (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Article Content *") },
                        placeholder = {
                            Text(
                                if (isEditing) "Article content..."
                                else "Paste article content or passage here..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        maxLines = 14
                    )
                } else if (selectedTab == 1) {
                    // TAB 1: Web URL Import
                    OutlinedTextField(
                        value = webArticleUrl,
                        onValueChange = {
                            webArticleUrl = it
                            webErrorMessage = null
                        },
                        label = { Text("Article Web Link") },
                        placeholder = { Text("https://example.com/article...") },
                        trailingIcon = {
                            IconButton(onClick = {
                                try {
                                    val clipService = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipService?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clipText.isNullOrBlank()) {
                                        webArticleUrl = clipText.trim()
                                        webErrorMessage = null
                                    }
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Link", tint = IndigoPrimary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (webArticleUrl.isNotBlank()) {
                                isExtractingWeb = true
                                webErrorMessage = null
                                coroutineScope.launch {
                                    val fetchResult = ArticleParser.fetchWebArticle(webArticleUrl)
                                    isExtractingWeb = false
                                    fetchResult.fold(
                                        onSuccess = { item ->
                                            extractedWebArticle = item
                                        },
                                        onFailure = { err ->
                                            webErrorMessage = err.message ?: "Failed to extract article."
                                        }
                                    )
                                }
                            }
                        },
                        enabled = webArticleUrl.isNotBlank() && !isExtractingWeb,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        if (isExtractingWeb) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting Article...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract Article", fontSize = 13.sp)
                        }
                    }

                    if (webErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RoseLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = webErrorMessage ?: "",
                                    color = RoseError,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (extractedWebArticle != null) {
                        val article = extractedWebArticle!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.4f))
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Article Extracted",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldSuccess
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            title = article.title
                                            author = article.author
                                            content = article.content
                                            selectedTab = 0
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Edit in Text", fontSize = 11.sp, color = IndigoPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = article.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateText,
                                    maxLines = 2
                                )
                                val wordCount = article.content.split("\\s+".toRegex()).count { it.isNotBlank() }
                                Text(
                                    text = "By ${article.author} • $wordCount words",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = article.content.take(200) + if (article.content.length > 200) "..." else "",
                                    fontSize = 11.sp,
                                    color = SlateMuted,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                } else {
                    // TAB 2: Google Doc URL Import
                    OutlinedTextField(
                        value = googleDocUrl,
                        onValueChange = {
                            googleDocUrl = it
                            docErrorMessage = null
                        },
                        label = { Text("Google Doc Link or ID") },
                        placeholder = { Text("https://docs.google.com/document/d/...") },
                        trailingIcon = {
                            IconButton(onClick = {
                                try {
                                    val clipService = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipService?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clipText.isNullOrBlank()) {
                                        googleDocUrl = clipText.trim()
                                        docErrorMessage = null
                                    }
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Link", tint = IndigoPrimary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (googleDocUrl.isNotBlank()) {
                                isFetchingDoc = true
                                docErrorMessage = null
                                coroutineScope.launch {
                                    val fetchResult = ArticleParser.fetchGoogleDocText(googleDocUrl)
                                    isFetchingDoc = false
                                    fetchResult.fold(
                                        onSuccess = { docText ->
                                            val parsed = ArticleParser.parseArticles(docText)
                                            if (parsed.isEmpty()) {
                                                docErrorMessage = "No articles could be parsed from the document."
                                            } else {
                                                docFetchedArticles = parsed
                                            }
                                        },
                                        onFailure = { err ->
                                            docErrorMessage = err.message ?: "Failed to fetch document."
                                        }
                                    )
                                }
                            }
                        },
                        enabled = googleDocUrl.isNotBlank() && !isFetchingDoc,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        if (isFetchingDoc) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching from Google Doc...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch from Google Doc", fontSize = 13.sp)
                        }
                    }

                    if (docErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RoseLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = docErrorMessage ?: "",
                                    color = RoseError,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (docFetchedArticles.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EmeraldLight),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.4f))
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${docFetchedArticles.size} Articles Ready to Import!",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                docFetchedArticles.forEachIndexed { idx, item ->
                                    val words = item.content.split("\\s+".toRegex()).count { it.isNotBlank() }
                                    Text(
                                        text = "${idx + 1}. ${item.title} — ${item.author} (${words} words)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SlateText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1 && extractedWebArticle != null) {
                Button(
                    onClick = {
                        val item = extractedWebArticle!!
                        onSaveSingle(item.title, item.content, item.author)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import & Read")
                }
            } else if (selectedTab == 2 && docFetchedArticles.isNotEmpty()) {
                Button(
                    onClick = {
                        onSaveBatch(docFetchedArticles)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Text("Import All (${docFetchedArticles.size}) & Read")
                }
            } else if (selectedTab == 0 && liveParsedArticles.size > 1) {
                Button(
                    onClick = {
                        onSaveBatch(liveParsedArticles)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Import All (${liveParsedArticles.size}) & Read")
                }
            } else if (selectedTab == 0 || isEditing) {
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
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text(if (isEditing) "Save Changes" else "Save & Read")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                Text(
                    text = "Reader Settings",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = SlateText
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(tempFontSize, tempPaddingDp, tempLineSpacing, tempFontFamily, tempHighlightBold, tempJustified)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontFamily = PoppinsFontFamily, color = SlateMuted)
            }
        }
    )
}
