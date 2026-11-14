package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.data.search.BookSearchEngine
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HIGHLIGHT_PALETTE = listOf(
    "#FDE047" to Color(0xFFFDE047), // Warm Yellow
    "#86EFAC" to Color(0xFF86EFAC), // Mint Green
    "#7DD3FC" to Color(0xFF7DD3FC), // Sky Blue
    "#FDA4AF" to Color(0xFFFDA4AF)  // Soft Rose
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridBookReaderScreen(
    book: BookEntity,
    pages: List<BookPageEntity>,
    tocEntries: List<BookTocEntity>,
    bookmarks: List<BookBookmarkEntity>,
    highlights: List<BookHighlightEntity>,
    notes: List<BookNoteEntity>,
    initialTarget: BookReaderTarget,
    ebookPreferences: EbookReaderPreferences,
    onUpdateEbookPreferences: (EbookReaderPreferences) -> Unit,
    onUpdateReadingProgress: (bookId: String, page: Int) -> Unit,
    onToggleBookmark: (bookId: String, page: Int, title: String, snippet: String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onAddHighlight: (bookId: String, page: Int, selectedText: String, colorHex: String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onSaveNote: (bookId: String, page: Int, noteText: String, selectedText: String, existingId: String?) -> Unit,
    onDeleteNote: (String) -> Unit,
    onSearchInBook: suspend (bookId: String, query: String) -> List<BookPassageMatch>,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val totalPages = maxOf(book.totalPages, pages.size, 1)

    var currentPage by remember(initialTarget.bookId) {
        mutableIntStateOf(initialTarget.pageNumber.coerceIn(1, totalPages))
    }
    var activeHighlightQuery by remember(initialTarget.bookId) {
        mutableStateOf(initialTarget.highlightQuery?.takeIf { it.isNotBlank() })
    }
    var activeHighlightParagraph by remember(initialTarget.bookId) {
        mutableStateOf(initialTarget.highlightParagraph?.takeIf { it.isNotBlank() })
    }
    var activeHighlightPage by remember(initialTarget.bookId) {
        mutableStateOf(
            if (!initialTarget.highlightQuery.isNullOrBlank() || !initialTarget.highlightParagraph.isNullOrBlank()) {
                initialTarget.pageNumber.coerceIn(1, totalPages)
            } else null
        )
    }

    // Re-clamp when target changes inside reader
    LaunchedEffect(
        initialTarget.pageNumber,
        initialTarget.highlightQuery,
        initialTarget.highlightParagraph
    ) {
        currentPage = initialTarget.pageNumber.coerceIn(1, totalPages)
        if (!initialTarget.highlightQuery.isNullOrBlank() || !initialTarget.highlightParagraph.isNullOrBlank()) {
            activeHighlightQuery = initialTarget.highlightQuery?.takeIf { it.isNotBlank() }
            activeHighlightParagraph = initialTarget.highlightParagraph?.takeIf { it.isNotBlank() }
            activeHighlightPage = currentPage
        }
    }

    // Persist reading position in Ebook mode
    LaunchedEffect(currentPage) {
        onUpdateReadingProgress(book.id, currentPage)
    }

    BackHandler {
        onBack()
    }

    var showContentsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var annotatePrefillText by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf<BookNoteEntity?>(null) }

    val currentPageEntity = remember(pages, currentPage) {
        pages.find { it.pageNumber == currentPage }
    }
    val pageText = currentPageEntity?.textContent.orEmpty()
    val isCurrentBookmarked = remember(bookmarks, currentPage) {
        bookmarks.any { it.pageNumber == currentPage }
    }
    val currentPageHighlights = remember(highlights, currentPage) {
        highlights.filter { it.pageNumber == currentPage }
    }
    val currentPageNotes = remember(notes, currentPage) {
        notes.filter { it.pageNumber == currentPage }
    }

    // Resolve Ebook theme palette
    val (bgColor, inkColor, subtleSurface, accentColor) = when (ebookPreferences.themeKey) {
        "sepia" -> listOf(Color(0xFFFBF4E4), Color(0xFF433422), Color(0xFFF3E7CF), Color(0xFF9A5B13))
        "mist" -> listOf(Color(0xFFF1F5F9), Color(0xFF0F172A), Color(0xFFE2E8F0), IndigoPrimary)
        "dark" -> listOf(Color(0xFF0F172A), Color(0xFFE2E8F0), Color(0xFF1E293B), Color(0xFFA5B4FC))
        else -> listOf(Color.White, SlateText, Color(0xFFF8FAFC), IndigoPrimary)
    }
    val isDarkTheme = ebookPreferences.themeKey == "dark"
    val chromeBg = if (isDarkTheme) Color(0xFF151C2C) else Color.White
    val chromeText = if (isDarkTheme) Color(0xFFF1F5F9) else SlateText
    val chromeMuted = if (isDarkTheme) Color(0xFF94A3B8) else SlateMuted

    Scaffold(
        topBar = {
            Surface(
                color = chromeBg,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = chromeText
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.title,
                                fontFamily = selectFontForText(book.title),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = chromeText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("Page $currentPage of $totalPages")
                                    if (!currentPageEntity?.chapterTitle.isNullOrBlank()) {
                                        append(" • ${currentPageEntity?.chapterTitle}")
                                    }
                                },
                                fontSize = 11.sp,
                                color = chromeMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Secondary Tool Row (Search, Bookmark, TOC/Notes, Reader Settings)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { showContentsSheet = true },
                                modifier = Modifier.size(48.dp).testTag("reader_contents_btn")
                            ) {
                                Icon(Icons.Default.Toc, contentDescription = "TOC, Bookmarks & Notes", tint = chromeText)
                            }
                            IconButton(
                                onClick = { showSearchSheet = true },
                                modifier = Modifier.size(48.dp).testTag("reader_search_btn")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search Book", tint = chromeText)
                            }
                            IconButton(
                                onClick = {
                                    val snippet = pageText.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: ""
                                    val title = currentPageEntity?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Page $currentPage"
                                    onToggleBookmark(book.id, currentPage, title, snippet)
                                },
                                modifier = Modifier.size(48.dp).testTag("reader_bookmark_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Toggle Bookmark",
                                    tint = if (isCurrentBookmarked) IndigoPrimary else chromeText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    annotatePrefillText = activeHighlightQuery
                                        ?: pageText.trim().take(180)
                                },
                                modifier = Modifier.size(48.dp).testTag("reader_annotate_btn")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Add Highlight or Note", tint = chromeText)
                            }

                            IconButton(
                                onClick = { showReaderSettingsSheet = true },
                                modifier = Modifier.size(48.dp).testTag("reader_typography_btn")
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = "Ebook Appearance", tint = IndigoPrimary)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = chromeBg,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 1) chromeText else chromeMuted.copy(alpha = 0.4f)
                            )
                        }

                        if (totalPages > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { currentPage = it.roundToInt().coerceIn(1, totalPages) },
                                valueRange = 1f..totalPages.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = IndigoPrimary,
                                    activeTrackColor = IndigoPrimary
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Surface(
                            onClick = { showJumpPageDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = IndigoLight,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "$currentPage/$totalPages",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        IconButton(
                            onClick = { if (currentPage < totalPages) currentPage++ },
                            enabled = currentPage < totalPages,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Page",
                                tint = if (currentPage < totalPages) chromeText else chromeMuted.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isTargetPageForHighlight = activeHighlightPage == null || activeHighlightPage == currentPage
            EbookPageContent(
                pageNumber = currentPage,
                chapterTitle = currentPageEntity?.chapterTitle.orEmpty(),
                pageText = pageText,
                ebookPrefs = ebookPreferences,
                bgColor = bgColor,
                inkColor = inkColor,
                subtleSurface = subtleSurface,
                accentColor = accentColor,
                userHighlights = currentPageHighlights,
                pageNotes = currentPageNotes,
                searchHighlightQuery = if (isTargetPageForHighlight) activeHighlightQuery else null,
                highlightParagraph = if (isTargetPageForHighlight) activeHighlightParagraph else null,
                onClearSearchHighlight = {
                    activeHighlightQuery = null
                    activeHighlightParagraph = null
                    activeHighlightPage = null
                },
                onParagraphClick = { para -> annotatePrefillText = para },
                onDeleteNote = onDeleteNote,
                onEditNote = { editingNote = it }
            )
        }
    }

    // Ebook Reader Appearance Settings Sheet
    if (showReaderSettingsSheet) {
        EbookSettingsSheet(
            prefs = ebookPreferences,
            onChange = onUpdateEbookPreferences,
            onDismiss = { showReaderSettingsSheet = false }
        )
    }

    // Contents Sheet: TOC, Bookmarks, Highlights, Notes
    if (showContentsSheet) {
        BookContentsSheet(
            tocEntries = tocEntries,
            bookmarks = bookmarks,
            highlights = highlights,
            notes = notes,
            onJumpToPage = { page, highlightSnippet ->
                currentPage = page.coerceIn(1, totalPages)
                if (!highlightSnippet.isNullOrBlank()) {
                    activeHighlightQuery = highlightSnippet
                    activeHighlightParagraph = highlightSnippet
                    activeHighlightPage = currentPage
                }
                showContentsSheet = false
            },
            onDeleteBookmark = onDeleteBookmark,
            onDeleteHighlight = onDeleteHighlight,
            onDeleteNote = onDeleteNote,
            onDismiss = { showContentsSheet = false }
        )
    }

    // In-Book Search Dialog
    if (showSearchSheet) {
        InBookSearchSheet(
            bookTitle = book.title,
            onSearch = { query -> onSearchInBook(book.id, query) },
            onSelectResult = { match ->
                currentPage = match.pageNumber.coerceIn(1, totalPages)
                activeHighlightQuery = match.bestLine.ifBlank {
                    match.matchedTerms.firstOrNull() ?: match.passageSnippet.take(80)
                }
                activeHighlightParagraph = match.passageSnippet
                activeHighlightPage = currentPage
                showSearchSheet = false
            },
            onDismiss = { showSearchSheet = false }
        )
    }

    // Jump to Page Dialog
    if (showJumpPageDialog) {
        var pageInput by remember { mutableStateOf(currentPage.toString()) }
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Jump to Page", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { v -> if (v.all { it.isDigit() }) pageInput = v },
                    label = { Text("Page (1 - $totalPages)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val p = pageInput.toIntOrNull()
                    if (p != null) {
                        currentPage = p.coerceIn(1, totalPages)
                    }
                    showJumpPageDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Highlight or Note Dialog
    if (annotatePrefillText != null || editingNote != null) {
        val initialSnippet = editingNote?.selectedText ?: annotatePrefillText.orEmpty()
        var snippetInput by remember { mutableStateOf(initialSnippet) }
        var noteInput by remember { mutableStateOf(editingNote?.noteText.orEmpty()) }
        var selectedColorHex by remember { mutableStateOf(HIGHLIGHT_PALETTE.first().first) }
        var saveAsHighlight by remember { mutableStateOf(editingNote == null) }

        AlertDialog(
            onDismissRequest = {
                annotatePrefillText = null
                editingNote = null
            },
            title = {
                Text(
                    text = if (editingNote != null) "Edit Note (Page $currentPage)" else "Highlight & Note (Page $currentPage)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = snippetInput,
                        onValueChange = { snippetInput = it },
                        label = { Text("Passage / Selected Text") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (editingNote == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = saveAsHighlight, onCheckedChange = { saveAsHighlight = it })
                            Text("Save Highlight", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.weight(1f))
                            HIGHLIGHT_PALETTE.forEach { (hex, col) ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (selectedColorHex == hex) 2.5.dp else 1.dp,
                                            color = if (selectedColorHex == hex) IndigoPrimary else SlateBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedColorHex = hex
                                            saveAsHighlight = true
                                        }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Your Note (optional if highlighting)") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingNote != null) {
                            if (noteInput.isNotBlank()) {
                                onSaveNote(book.id, currentPage, noteInput, snippetInput, editingNote?.id)
                            }
                        } else {
                            if (saveAsHighlight && snippetInput.isNotBlank()) {
                                onAddHighlight(book.id, currentPage, snippetInput, selectedColorHex)
                            }
                            if (noteInput.isNotBlank()) {
                                onSaveNote(book.id, currentPage, noteInput, snippetInput, null)
                            }
                        }
                        annotatePrefillText = null
                        editingNote = null
                    },
                    enabled = (saveAsHighlight && snippetInput.isNotBlank()) || noteInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    annotatePrefillText = null
                    editingNote = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EbookPageContent(
    pageNumber: Int,
    chapterTitle: String,
    pageText: String,
    ebookPrefs: EbookReaderPreferences,
    bgColor: Color,
    inkColor: Color,
    subtleSurface: Color,
    accentColor: Color,
    userHighlights: List<BookHighlightEntity>,
    pageNotes: List<BookNoteEntity>,
    searchHighlightQuery: String?,
    highlightParagraph: String?,
    onClearSearchHighlight: () -> Unit,
    onParagraphClick: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onEditNote: (BookNoteEntity) -> Unit
) {
    val resolvedFont = resolveEbookFontFamily(ebookPrefs.fontFamilyKey, pageText)
    val paragraphs = remember(pageText, pageNumber) {
        val split = pageText.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
        if (split.isNotEmpty()) split else listOf(pageText.ifBlank { "No extractable text on Page $pageNumber." })
    }

    val (matchedParaIndex, matchedBestLine) = remember(paragraphs, highlightParagraph, searchHighlightQuery) {
        findMatchedParagraphAndLine(
            paragraphs = paragraphs,
            highlightParagraph = highlightParagraph,
            searchQuery = searchHighlightQuery
        )
    }

    val showHighlightBanner = !searchHighlightQuery.isNullOrBlank() || !highlightParagraph.isNullOrBlank()
    val listState = rememberLazyListState()

    LaunchedEffect(pageNumber, matchedParaIndex, showHighlightBanner) {
        if (matchedParaIndex >= 0) {
            val headerCount = (if (showHighlightBanner) 1 else 0) + (if (chapterTitle.isNotBlank()) 1 else 0)
            listState.animateScrollToItem((headerCount + matchedParaIndex).coerceAtLeast(0))
        } else {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentPadding = PaddingValues(
            horizontal = ebookPrefs.horizontalMarginDp.dp,
            vertical = 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHighlightBanner) {
            item(key = "highlight_banner") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FindInPage,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Matched line highlighted • Page $pageNumber",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                        IconButton(onClick = onClearSearchHighlight, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear highlight",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (chapterTitle.isNotBlank()) {
            item(key = "chapter_title") {
                Text(
                    text = chapterTitle,
                    fontFamily = selectFontForText(chapterTitle),
                    fontSize = (ebookPrefs.fontSizeSp + 2f).sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        itemsIndexed(paragraphs) { idx, para ->
            val isMatchedParagraph = idx == matchedParaIndex
            val bestLineForThisPara = if (isMatchedParagraph) matchedBestLine else ""
            val annotated = remember(para, userHighlights, bestLineForThisPara, accentColor, inkColor) {
                buildEbookParagraphText(
                    text = para,
                    userHighlights = userHighlights,
                    bestLine = bestLineForThisPara,
                    accentColor = accentColor,
                    inkColor = inkColor
                )
            }

            SelectionContainer {
                if (isMatchedParagraph) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.055f))
                            .clickable { onParagraphClick(para) }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(accentColor.copy(alpha = 0.82f))
                        )
                        Text(
                            text = annotated,
                            fontFamily = resolvedFont,
                            fontSize = ebookPrefs.fontSizeSp.sp,
                            lineHeight = (ebookPrefs.fontSizeSp * ebookPrefs.lineSpacingMultiplier).sp,
                            color = inkColor,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    Text(
                        text = annotated,
                        fontFamily = resolvedFont,
                        fontSize = ebookPrefs.fontSizeSp.sp,
                        lineHeight = (ebookPrefs.fontSizeSp * ebookPrefs.lineSpacingMultiplier).sp,
                        color = inkColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onParagraphClick(para) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        if (pageNotes.isNotEmpty()) {
            item(key = "notes_divider") {
                HorizontalDivider(color = inkColor.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Page Notes (${pageNotes.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            items(pageNotes, key = { it.id }) { note ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = subtleSurface,
                    border = BorderStroke(1.dp, inkColor.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (note.selectedText.isNotBlank()) {
                            Text(
                                text = "\"${note.selectedText}\"",
                                fontSize = 11.5.sp,
                                color = inkColor.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = note.noteText,
                            fontFamily = selectFontForText(note.noteText),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = inkColor
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onEditNote(note) }) { Text("Edit", fontSize = 11.sp) }
                            TextButton(onClick = { onDeleteNote(note.id) }) {
                                Text("Delete", fontSize = 11.sp, color = RoseError)
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EbookSettingsSheet(
    prefs: EbookReaderPreferences,
    onChange: (EbookReaderPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Ebook Reader Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SlateText)

            // Font Size
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Font Size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${prefs.fontSizeSp.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                }
                Slider(
                    value = prefs.fontSizeSp,
                    onValueChange = { onChange(prefs.copy(fontSizeSp = it.roundToInt().toFloat())) },
                    valueRange = 12f..28f
                )
            }

            // Font Style
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Font Style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "auto" to "Auto",
                        "kalpurush" to "Kalpurush",
                        "aikya" to "Aikya",
                        "poppins" to "Poppins",
                        "serif" to "Serif",
                        "sans" to "Sans",
                        "mono" to "Mono"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = prefs.fontFamilyKey == key,
                            onClick = { onChange(prefs.copy(fontFamilyKey = key)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Line Spacing
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Line Spacing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.25f to "Tight", 1.55f to "Normal", 1.8f to "Relaxed", 2.05f to "Wide").forEach { (valF, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(prefs.lineSpacingMultiplier - valF) < 0.05f,
                            onClick = { onChange(prefs.copy(lineSpacingMultiplier = valF)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Margins
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Page Margins", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12f to "Narrow", 20f to "Medium", 32f to "Wide").forEach { (valM, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(prefs.horizontalMarginDp - valM) < 1f,
                            onClick = { onChange(prefs.copy(horizontalMarginDp = valM)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Background Themes
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Background Theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("paper", "Paper", Color.White to SlateText),
                        Triple("sepia", "Sepia", Color(0xFFFBF4E4) to Color(0xFF433422)),
                        Triple("mist", "Mist", Color(0xFFF1F5F9) to SlateText),
                        Triple("dark", "Night", Color(0xFF0F172A) to Color.White)
                    ).forEach { (key, label, colorsPair) ->
                        val selected = prefs.themeKey == key
                        Surface(
                            onClick = { onChange(prefs.copy(themeKey = key)) },
                            shape = RoundedCornerShape(12.dp),
                            color = colorsPair.first,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) IndigoPrimary else SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorsPair.second)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookContentsSheet(
    tocEntries: List<BookTocEntity>,
    bookmarks: List<BookBookmarkEntity>,
    highlights: List<BookHighlightEntity>,
    notes: List<BookNoteEntity>,
    onJumpToPage: (page: Int, highlightSnippet: String?) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "TOC (${tocEntries.size})",
        "Bookmarks (${bookmarks.size})",
        "Highlights (${highlights.size})",
        "Notes (${notes.size})"
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (tocEntries.isEmpty()) {
                            item { Text("No Table of Contents entries extracted.", color = SlateMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(tocEntries, key = { it.id }) { entry ->
                                Surface(
                                    onClick = { onJumpToPage(entry.pageNumber, null) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontFamily = selectFontForText(entry.title),
                                            fontSize = 13.sp,
                                            fontWeight = if (entry.level == 1) FontWeight.Bold else FontWeight.Medium,
                                            color = SlateText,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = ((entry.level - 1) * 12).dp)
                                        )
                                        Text("Page ${entry.pageNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (bookmarks.isEmpty()) {
                            item { Text("No bookmarks saved yet.", color = SlateMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(bookmarks, key = { it.id }) { bm ->
                                Surface(
                                    onClick = { onJumpToPage(bm.pageNumber, null) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page ${bm.pageNumber} • ${bm.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (bm.snippet.isNotBlank()) {
                                                Text(bm.snippet, fontSize = 11.5.sp, color = SlateMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Bookmark", tint = RoseError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        if (highlights.isEmpty()) {
                            item { Text("No highlights saved yet. Tap any paragraph to highlight.", color = SlateMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(highlights, key = { it.id }) { hl ->
                                Surface(
                                    onClick = { onJumpToPage(hl.pageNumber, hl.selectedText) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(parseHexColor(hl.colorHex))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page ${hl.pageNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                            Text(hl.selectedText, fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { onDeleteHighlight(hl.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Highlight", tint = RoseError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (notes.isEmpty()) {
                            item { Text("No notes saved yet.", color = SlateMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(notes, key = { it.id }) { note ->
                                Surface(
                                    onClick = { onJumpToPage(note.pageNumber, note.selectedText.ifBlank { null }) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("Page ${note.pageNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                            Text(note.noteText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            if (note.selectedText.isNotBlank()) {
                                                Text("\"${note.selectedText}\"", fontSize = 11.sp, color = SlateMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        IconButton(onClick = { onDeleteNote(note.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Note", tint = RoseError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InBookSearchSheet(
    bookTitle: String,
    onSearch: suspend (String) -> List<BookPassageMatch>,
    onSelectResult: (BookPassageMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<BookPassageMatch>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Inside Book",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search keywords or phrase in $bookTitle...", fontSize = 12.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("inbook_search_input")
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSearching = true
                                results = onSearch(query)
                                isSearching = false
                            }
                        },
                        enabled = query.trim().length >= 2 && !isSearching,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp).testTag("inbook_search_go_btn")
                    ) {
                        Text("Find")
                    }
                }

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IndigoPrimary)
                    }
                } else if (results.isEmpty() && query.trim().length >= 2) {
                    Text(
                        text = "Tap Find to search, or try different keywords.",
                        fontSize = 12.5.sp,
                        color = SlateMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { match ->
                            Surface(
                                onClick = { onSelectResult(match) },
                                shape = RoundedCornerShape(12.dp),
                                color = SlateBg,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = IndigoLight) {
                                            Text(
                                                text = "Page ${match.pageNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndigoPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (match.chapterTitle.isNotBlank()) {
                                            Text(match.chapterTitle, fontSize = 11.sp, color = SlateMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    val previewBestLine = remember(match.passageSnippet, match.bestLine, query) {
                                        match.bestLine.ifBlank {
                                            BookSearchEngine.findBestMatchingLine(match.passageSnippet, query)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IndigoPrimary.copy(alpha = 0.04f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .fillMaxHeight()
                                                .background(IndigoPrimary.copy(alpha = 0.7f))
                                        )
                                        Text(
                                            text = buildEbookParagraphText(
                                                text = match.passageSnippet,
                                                userHighlights = emptyList(),
                                                bestLine = previewBestLine,
                                                accentColor = IndigoPrimary,
                                                inkColor = SlateText
                                            ),
                                            fontFamily = selectFontForText(match.passageSnippet),
                                            fontSize = 12.5.sp,
                                            color = SlateText.copy(alpha = 0.82f),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveEbookFontFamily(key: String, sampleText: String): FontFamily {
    return when (key) {
        "poppins" -> PoppinsFontFamily
        "kalpurush" -> KalpurushFontFamily
        "aikya" -> AikyaFontFamily
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> selectArticleFontForText(sampleText)
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFFDE047)
    }
}

private fun normalizeWhitespace(text: String): String {
    return text.lowercase().replace(Regex("\\s+"), " ").trim()
}

private fun findMatchedParagraphAndLine(
    paragraphs: List<String>,
    highlightParagraph: String?,
    searchQuery: String?
): Pair<Int, String> {
    val cleanParaQuery = highlightParagraph
        ?.removePrefix("...")
        ?.removeSuffix("...")
        ?.trim()
        .orEmpty()
    val cleanLineQuery = searchQuery?.trim().orEmpty()
    if (cleanParaQuery.isBlank() && cleanLineQuery.isBlank()) return -1 to ""

    // 1. Match paragraph directly using highlightParagraph
    var matchedIdx = -1
    if (cleanParaQuery.length >= 4) {
        val normParaQ = normalizeWhitespace(cleanParaQuery)
        matchedIdx = paragraphs.indexOfFirst { para ->
            para.contains(cleanParaQuery, ignoreCase = true) ||
                cleanParaQuery.contains(para, ignoreCase = true) ||
                normalizeWhitespace(para).contains(normParaQ)
        }
    }

    // 2. Match paragraph directly using searchQuery (bestLine / free-text query)
    if (matchedIdx < 0 && cleanLineQuery.length >= 2) {
        val normLineQ = normalizeWhitespace(cleanLineQuery)
        matchedIdx = paragraphs.indexOfFirst { para ->
            para.contains(cleanLineQuery, ignoreCase = true) ||
                normalizeWhitespace(para).contains(normLineQ)
        }
    }

    // 3. Fallback token overlap for multi-word in-book search queries
    if (matchedIdx < 0 && cleanLineQuery.length >= 2) {
        val tokens = BookSearchEngine.tokenize(cleanLineQuery).filter { it.length >= 2 }.distinct()
        if (tokens.isNotEmpty()) {
            var bestScore = 0
            paragraphs.forEachIndexed { idx, para ->
                val paraTokens = BookSearchEngine.tokenize(para).toHashSet()
                val hits = tokens.count { it in paraTokens }
                if (hits > bestScore) {
                    bestScore = hits
                    matchedIdx = idx
                }
            }
        }
    }

    if (matchedIdx < 0) return -1 to ""

    val targetPara = paragraphs[matchedIdx]
    val bestLine = when {
        cleanLineQuery.length >= 2 && targetPara.contains(cleanLineQuery, ignoreCase = true) -> cleanLineQuery
        cleanLineQuery.length >= 2 -> BookSearchEngine.findBestMatchingLine(targetPara, cleanLineQuery)
        cleanParaQuery.length >= 2 -> BookSearchEngine.findBestMatchingLine(targetPara, cleanParaQuery)
        else -> ""
    }

    return matchedIdx to bestLine
}

private fun buildEbookParagraphText(
    text: String,
    userHighlights: List<BookHighlightEntity>,
    bestLine: String,
    accentColor: Color,
    inkColor: Color
) = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()

    // 1. Persisted user highlights
    for (hl in userHighlights) {
        val target = hl.selectedText.trim()
        if (target.length < 2) continue
        val idx = lower.indexOf(target.lowercase())
        if (idx >= 0) {
            val end = (idx + target.length).coerceAtMost(text.length)
            addStyle(
                SpanStyle(
                    background = parseHexColor(hl.colorHex).copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium
                ),
                idx,
                end
            )
        }
    }

    // 2. Single highest-matching line highlight (no per-word yellow clutter)
    val lineClean = bestLine.trim()
    if (lineClean.length >= 2) {
        val fullIdx = lower.indexOf(lineClean.lowercase())
        if (fullIdx >= 0) {
            val end = (fullIdx + lineClean.length).coerceAtMost(text.length)
            addStyle(
                SpanStyle(
                    background = accentColor.copy(alpha = 0.16f),
                    color = inkColor,
                    fontWeight = FontWeight.SemiBold
                ),
                fullIdx,
                end
            )
        }
    }
}
