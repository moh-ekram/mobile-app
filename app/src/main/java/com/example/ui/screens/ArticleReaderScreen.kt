package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
                        IconButton(onClick = { showAddDialog = true }) {
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
            onDismissAddDialog = { showAddDialog = false },
            showSyncDialogFromParent = showSyncDialog,
            onDismissSyncDialog = { showSyncDialog = false },
            showReaderSettingsFromParent = showReaderSettingsDialog,
            onDismissReaderSettings = { showReaderSettingsDialog = false },
            modifier = if (activeArticle == null) Modifier.padding(innerPadding) else Modifier.statusBarsPadding()
        )
    }
}

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
    onDismissAddDialog: () -> Unit = {},
    showSyncDialogFromParent: Boolean = false,
    onDismissSyncDialog: () -> Unit = {},
    showReaderSettingsFromParent: Boolean = false,
    onDismissReaderSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
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
    var readerFontFamily by remember { mutableStateOf(readerPrefs.getString("reader_font_family", "default") ?: "default") }
    var readerHighlightBold by remember { mutableStateOf(readerPrefs.getString("reader_highlight_bold", "bold") ?: "bold") }
    var readerJustify by remember { mutableStateOf(readerPrefs.getBoolean("reader_justify", false)) }

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

    // Intercept back button when reading an article to return to articles list
    BackHandler(enabled = currentArticle != null) {
        onSelectArticle(null)
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

                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Article", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold)
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

                                Button(
                                    onClick = { showAddDialog = true },
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

                    items(articles, key = { it.id }) { art ->
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
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = art.title,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(onClick = {
                                            onSelectArticle(art)
                                            showEditDialog = true
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            onDeleteArticle(art.id)
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = RoseError,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                if (art.author.isNotBlank() && art.author != "Anonymous Author") {
                                    Text(
                                        text = "By ${art.author}",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SlateMuted
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }

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
        } else {
            // FULL SCREEN MODE: Ultra-slim navigation & action bar (No app icon/name, no bottom menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectArticle(null) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Articles List",
                        tint = SlateText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = currentArticle.title,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (currentArticle.author.isNotBlank() && currentArticle.author != "Anonymous Author") {
                        Text(
                            text = "By ${currentArticle.author}",
                            fontSize = 9.5.sp,
                            color = SlateMuted,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Course Filter Button (Opens Equal Space Course Grid)
                IconButton(
                    onClick = { showCourseFilterDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (selectedCourseIds.isNotEmpty()) {
                                Badge(containerColor = IndigoPrimary) {
                                    Text(selectedCourseIds.size.toString(), color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter Courses",
                            tint = if (selectedCourseIds.isNotEmpty()) IndigoPrimary else SlateText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        tts?.speak(currentArticle.content, TextToSpeech.QUEUE_FLUSH, null, "article_tts")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Listen to Article",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Article",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = { showLocalReaderSettings = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Reader Display Settings",
                        tint = SlateText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = {
                    onDeleteArticle(currentArticle.id)
                    onSelectArticle(null)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Article",
                        tint = RoseError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Article Content Area - TAKES FULL WIDTH OF THE PHONE SCREEN
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = readerPaddingDp.dp, vertical = 2.dp)
                    ) {
                        val selectedFont = when (readerFontFamily) {
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            else -> selectFontForText(currentArticle.content)
                        }
                        val isExtraBold = (readerHighlightBold == "extra_bold")

                        // Highlighted Interactive Text taking full width of phone screen with custom padding & font size
                        val annotatedText = buildPlaceHighlightedAnnotatedString(
                            content = currentArticle.content,
                            place1Map = place1Map,
                            place2Map = place2Map,
                            isExtraBold = isExtraBold
                        )

                        ClickableText(
                            text = annotatedText,
                            style = TextStyle(
                                fontSize = readerFontSize.sp,
                                lineHeight = (readerFontSize * readerLineSpacing).sp,
                                color = SlateText,
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
            }

        // Word Detail Bottom Card / Popup
        AnimatedVisibility(visible = selectedWordForDetails != null) {
            val word = selectedWordForDetails
            if (word != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = word.word,
                                    fontFamily = selectFontForText(word.word),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )

                                IconButton(
                                    onClick = { tts?.speak(word.word, TextToSpeech.QUEUE_FLUSH, null, "tts_article") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Speak",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "G${word.group}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedWordForDetails = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateLight)
                            }
                        }

                        // Meaning (Place 2)
                        Text(
                            text = word.meaning,
                            fontFamily = selectFontForText(word.meaning),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (!word.example.isNullOrBlank()) {
                            Text(
                                text = "“${word.example}”",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (!word.synonyms.isNullOrBlank()) {
                            Text(
                                text = "Synonyms: ${word.synonyms}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick rating directly from reading view
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onRateWord(word.id, "know")
                                    selectedWordForDetails = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("Know", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onRateWord(word.id, "confusion")
                                    selectedWordForDetails = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("Confused", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onRateWord(word.id, "dont_know")
                                    selectedWordForDetails = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("Don't Know", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    isExtraBold: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""[\w\u0980-\u09FF]+|[^\w\s\u0980-\u09FF]+|\s+""")
        val matches = regex.findAll(content)

        val targetWeight = if (isExtraBold) FontWeight.Black else FontWeight.ExtraBold

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
                        color = Color(0xFF1D4ED8),
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
                        color = Color(0xFF047857),
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
    onDismiss: () -> Unit,
    onSaveSingle: (title: String, content: String, author: String) -> Unit,
    onSaveBatch: (List<ParsedArticleItem>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Text / File, 1: Google Doc

    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var content by remember { mutableStateOf(initialContent) }

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
            Column {
                Text(
                    text = if (isEditing) "Edit Article" else "Add Articles",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = SlateText
                )
                if (!isEditing) {
                    Text(
                        text = "Option 1 Format: # Title, @ Author, --- separator",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = IndigoPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                    // Tab Selector: Upload/Text vs Google Doc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateLight)
                            .padding(3.dp)
                    ) {
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
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 0) IndigoPrimary else SlateMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Text / File",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) IndigoPrimary else SlateMuted
                                )
                            }
                        }

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
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) IndigoPrimary else SlateMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Doc",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) IndigoPrimary else SlateMuted
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

                    // Format hint card
                    if (!isEditing && liveParsedArticles.size <= 1) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Easy Format (Option 1):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                                Text(
                                    text = "# Article Title\n@ Author Name\nParagraph content...\n---\n# Next Article Title\n...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SlateMuted
                                )
                            }
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
                            label = { Text("Article Title (Optional if using # Title)") },
                            placeholder = { Text("Auto-detected if # Title is used") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author Name (Optional if using @ Author)") },
                            placeholder = { Text("Auto-detected if @ Author is used") },
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
                                else "Paste passage or multiple articles formatted with:\n# Title\n@ Author\nContent...\n---"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        maxLines = 14
                    )
                } else {
                    // TAB 1: Google Doc URL Import
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.3f))
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "How to write in Google Doc:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "# Article Title 1\n@ Author Name\nFirst article paragraph...\n\n---\n\n# Article Title 2\n@ Author Name\nSecond article paragraph...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SlateText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Delimiter: '---' separates multiple articles.\n• Make sure Doc sharing is set to 'Anyone with the link can view'.",
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                        }
                    }

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
            if (selectedTab == 1 && docFetchedArticles.isNotEmpty()) {
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
            } else {
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
