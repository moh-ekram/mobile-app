package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
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
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articles: List<ArticleEntity>,
    activeArticle: ArticleEntity?,
    words: List<VocabularyWordEntity>,
    onSelectArticle: (ArticleEntity?) -> Unit,
    onSaveArticle: (title: String, content: String) -> Unit,
    onDeleteArticle: (String) -> Unit,
    onRateWord: (wordId: String, status: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedWordForDetails by remember { mutableStateOf<VocabularyWordEntity?>(null) }

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

    // Default sample if no article is currently selected
    val currentArticle = activeArticle ?: articles.firstOrNull()

    // Build lookup maps for Place1 (word) and Place2 (meaning)
    val wordMap = remember(words) {
        val map = mutableMapOf<String, VocabularyWordEntity>()
        words.forEach { w ->
            if (w.word.isNotBlank()) {
                map[w.word.trim().lowercase(Locale.ROOT)] = w
            }
            if (w.meaning.isNotBlank()) {
                map[w.meaning.trim().lowercase(Locale.ROOT)] = w
            }
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Article Reader",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Text(
                            text = "Smart place1 & place2 highlighter with meanings",
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SlateText)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "New Article", tint = IndigoPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SlateBg)
                .padding(innerPadding)
        ) {
            // Saved Articles Horizontal Bar
            if (articles.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "SAVED ARTICLES (${articles.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateLight,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(articles, key = { it.id }) { art ->
                            val isSelected = art.id == currentArticle?.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectArticle(art) },
                                label = {
                                    Text(
                                        text = art.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Article Content Area
            if (currentArticle != null) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(SlateBorder)
                            )
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentArticle.title,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SlateText
                                        )
                                        Text(
                                            text = "${currentArticle.wordCount} words • Tap highlighted words for meaning",
                                            fontSize = 11.sp,
                                            color = SlateLight,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteArticle(currentArticle.id) }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RoseError)
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    color = SlateBorder
                                )

                                // Highlighted Interactive Text
                                val annotatedText = buildHighlightedAnnotatedString(
                                    content = currentArticle.content,
                                    wordMap = wordMap
                                )

                                ClickableText(
                                    text = annotatedText,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp,
                                        color = SlateText,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    onClick = { offset ->
                                        annotatedText.getStringAnnotations(
                                            tag = "VOCAB_WORD",
                                            start = offset,
                                            end = offset
                                        ).firstOrNull()?.let { annotation ->
                                            val matchedWord = wordMap[annotation.item.lowercase(Locale.ROOT)]
                                            if (matchedWord != null) {
                                                selectedWordForDetails = matchedWord
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No articles yet",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                        Text(
                            text = "Upload or paste text to highlight place1 & place2 words",
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Article")
                        }
                    }
                }
            }

            // Word Meaning Popup Sheet / Card
            selectedWordForDetails?.let { word ->
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
                            .padding(16.dp)
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
                                    onClick = {
                                        tts?.speak(word.word, TextToSpeech.QUEUE_FLUSH, null, null)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Speak",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(18.dp)
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
                            fontSize = 16.sp,
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
                                Text("Confusion", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

    // Add / Upload Article Dialog
    if (showAddDialog) {
        AddArticleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content ->
                onSaveArticle(title, content)
                showAddDialog = false
            }
        )
    }
}

/**
 * Builds an AnnotatedString that highlights words matching place1 or place2 with clickable annotations.
 */
private fun buildHighlightedAnnotatedString(
    content: String,
    wordMap: Map<String, VocabularyWordEntity>
): AnnotatedString {
    return buildAnnotatedString {
        // Regex to split by whitespace and punctuation while keeping tokens
        val regex = Regex("""[\w\u0980-\u09FF]+|[^\w\s\u0980-\u09FF]+|\s+""")
        val matches = regex.findAll(content)

        for (m in matches) {
            val token = m.value
            val cleanToken = token.trim().lowercase(Locale.ROOT)
            val vocabMatch = wordMap[cleanToken]

            if (vocabMatch != null) {
                // Highlighted word
                val start = length
                pushStringAnnotation(tag = "VOCAB_WORD", annotation = cleanToken)
                withStyle(
                    SpanStyle(
                        color = Color(0xFF4F46E5),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFFE0E7FF),
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
private fun AddArticleDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        val fileName = cursor.getString(nameIdx)
                        if (title.isBlank()) {
                            title = fileName.substringBeforeLast(".")
                        }
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    content = stream.bufferedReader().readText()
                }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Article for Reading", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SlateText)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    Text("Select Text File from Device (.txt)")
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Article Title *") },
                    placeholder = { Text("e.g. Science & Discovery") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Article Content *") },
                    placeholder = { Text("Paste article or passage here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 12
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onSave(title.trim().ifEmpty { "Reading Passage" }, content.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save & Read")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
