package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArticleEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.parser.ArticleParser
import com.example.data.parser.ParsedArticleItem
import com.example.ui.theme.*
import java.util.Locale
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articles: List<ArticleEntity>,
    activeArticle: ArticleEntity?,
    words: List<VocabularyWordEntity>,
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
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Article", tint = IndigoPrimary)
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
            onSelectArticle = onSelectArticle,
            onSaveArticle = onSaveArticle,
            onSaveArticlesBatch = onSaveArticlesBatch,
            onDeleteArticle = onDeleteArticle,
            onRateWord = onRateWord,
            showAddDialogFromParent = showAddDialog,
            onDismissAddDialog = { showAddDialog = false },
            modifier = if (activeArticle == null) Modifier.padding(innerPadding) else Modifier.statusBarsPadding()
        )
    }
}

@Composable
fun ArticleReaderView(
    articles: List<ArticleEntity>,
    activeArticle: ArticleEntity?,
    words: List<VocabularyWordEntity>,
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String, author: String, id: String?) -> Unit,
    onSaveArticlesBatch: (List<Triple<String, String, String>>) -> Unit = { list ->
        list.forEach { (t, c, a) -> onSaveArticle(t, c, a, null) }
    },
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
    showAddDialogFromParent: Boolean = false,
    onDismissAddDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedWordForDetails by remember { mutableStateOf<VocabularyWordEntity?>(null) }

    val isAdding = showAddDialog || showAddDialogFromParent

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

    // Place1 (Word) and Place2 (Meaning) lookup maps
    val place1Map = remember(words) {
        val map = mutableMapOf<String, VocabularyWordEntity>()
        words.forEach { w ->
            if (w.word.isNotBlank()) {
                map[w.word.trim().lowercase(Locale.ROOT)] = w
            }
        }
        map
    }

    val place2Map = remember(words) {
        val map = mutableMapOf<String, VocabularyWordEntity>()
        words.forEach { w ->
            if (w.meaning.isNotBlank()) {
                // Map the full meaning and component phrases
                val trimmedMeaning = w.meaning.trim().lowercase(Locale.ROOT)
                map[trimmedMeaning] = w
                val parts = trimmedMeaning.split("[,;/]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved Articles (${articles.size})",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Article", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    items(articles, key = { it.id }) { art ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectArticle(art) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = art.title,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(onClick = {
                                            onSelectArticle(art)
                                            showEditDialog = true
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            onDeleteArticle(art.id)
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = RoseError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (art.author.isNotBlank() && art.author != "Anonymous Author") {
                                    Text(
                                        text = "By ${art.author} • ${art.wordCount} words",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                } else {
                                    Text(
                                        text = "${art.wordCount} words",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Text(
                                    text = art.content.take(160) + if (art.content.length > 160) "..." else "",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = SlateMuted,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // FULL SCREEN MODE: Ultra-slim navigation & action bar
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
                    Text(
                        text = "By ${currentArticle.author} • ${currentArticle.wordCount} words",
                        fontSize = 9.5.sp,
                        color = IndigoPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
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

                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add New Article",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
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
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        // Highlighted Interactive Text taking full width of phone screen
                        val annotatedText = buildPlaceHighlightedAnnotatedString(
                            content = currentArticle.content,
                            place1Map = place1Map,
                            place2Map = place2Map
                        )

                        ClickableText(
                            text = annotatedText,
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 26.sp,
                                color = SlateText,
                                fontFamily = PoppinsFontFamily
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
}

/**
 * Builds an AnnotatedString that highlights words matching Place 1 (Word) or Place 2 (Meaning).
 */
private fun buildPlaceHighlightedAnnotatedString(
    content: String,
    place1Map: Map<String, VocabularyWordEntity>,
    place2Map: Map<String, VocabularyWordEntity>
): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""[\w\u0980-\u09FF]+|[^\w\s\u0980-\u09FF]+|\s+""")
        val matches = regex.findAll(content)

        for (m in matches) {
            val token = m.value
            val cleanToken = token.trim().lowercase(Locale.ROOT)
            val isPlace1 = place1Map.containsKey(cleanToken)
            val isPlace2 = place2Map.containsKey(cleanToken)

            if (isPlace1) {
                // Place 1 (Word) Highlight - Indigo
                val start = length
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = Color(0xFF4338CA),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFFE0E7FF),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(token)
                }
                pop()
            } else if (isPlace2) {
                // Place 2 (Meaning) Highlight - Emerald
                val start = length
                pushStringAnnotation(tag = "VOCAB_MATCH", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = Color(0xFF047857),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFFD1FAE5),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(token)
                }
                pop()
            } else {
                append(token)
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
