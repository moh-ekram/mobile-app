package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.search.BookSearchEngine
import com.example.ui.components.MinimalBottomSheet
import com.example.ui.components.MinimalChip
import com.example.ui.components.MinimalPrimaryButton
import com.example.ui.components.MinimalSecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    LaunchedEffect(currentPage) {
        onUpdateReadingProgress(book.id, currentPage)
    }

    BackHandler {
        onBack()
    }

    var barsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(currentPage) {
        barsVisible = true
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showContentsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showJumpPageSheet by remember { mutableStateOf(false) }
    var annotatePrefillText by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf<BookNoteEntity?>(null) }

    val currentPageEntity = remember(pages, currentPage) {
        pages.find { it.pageNumber == currentPage }
    }
    val pageText = currentPageEntity?.textContent.orEmpty()
    val chapterTitle = currentPageEntity?.chapterTitle.orEmpty()
    val isCurrentBookmarked = remember(bookmarks, currentPage) {
        bookmarks.any { it.pageNumber == currentPage }
    }
    val currentPageHighlights = remember(highlights, currentPage) {
        highlights.filter { it.pageNumber == currentPage }
    }
    val currentPageNotes = remember(notes, currentPage) {
        notes.filter { it.pageNumber == currentPage }
    }

    val readerTheme = remember(ebookPreferences.themeKey) {
        ReaderThemes.resolve(ebookPreferences.themeKey)
    }

    val scrollBarsConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -12f) {
                    barsVisible = false
                } else if (available.y > 12f) {
                    barsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerTheme.background)
            .nestedScroll(scrollBarsConnection)
    ) {
        val isTargetPageForHighlight = activeHighlightPage == null || activeHighlightPage == currentPage
        EbookPageContent(
            pageNumber = currentPage,
            chapterTitle = chapterTitle,
            pageText = pageText,
            ebookPrefs = ebookPreferences,
            readerTheme = readerTheme,
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

        // Single-row minimal top bar (hides on scroll down, returns on scroll up)
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = readerTheme.chromeBg,
                shadowElevation = AppElevation.None,
                tonalElevation = AppElevation.None,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(AppSpacing.TouchMin)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = readerTheme.chromeText,
                                modifier = Modifier.size(AppIconSize.Toolbar)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = AppSpacing.Xs),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = book.title,
                                fontFamily = selectFontForText(book.title),
                                fontSize = AppTypeSize.Title,
                                fontWeight = FontWeight.SemiBold,
                                color = readerTheme.chromeText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapterTitle.isNotBlank()) {
                                Text(
                                    text = chapterTitle,
                                    fontFamily = selectFontForText(chapterTitle),
                                    fontSize = AppTypeSize.Micro,
                                    color = readerTheme.chromeMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSearchSheet = true },
                            modifier = Modifier
                                .size(AppSpacing.TouchMin)
                                .testTag("reader_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search Book",
                                tint = readerTheme.chromeText,
                                modifier = Modifier.size(AppIconSize.Toolbar)
                            )
                        }

                        IconButton(
                            onClick = {
                                val snippet = pageText.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: ""
                                val title = chapterTitle.takeIf { it.isNotBlank() } ?: "Page $currentPage"
                                onToggleBookmark(book.id, currentPage, title, snippet)
                            },
                            modifier = Modifier
                                .size(AppSpacing.TouchMin)
                                .testTag("reader_bookmark_btn")
                        ) {
                            Icon(
                                imageVector = if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Toggle Bookmark",
                                tint = if (isCurrentBookmarked) readerTheme.accent else readerTheme.chromeText,
                                modifier = Modifier.size(AppIconSize.Toolbar)
                            )
                        }

                        IconButton(
                            onClick = { showReaderSettingsSheet = true },
                            modifier = Modifier
                                .size(AppSpacing.TouchMin)
                                .testTag("reader_typography_btn")
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Title,
                                fontWeight = FontWeight.SemiBold,
                                color = readerTheme.chromeText
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier
                                    .size(AppSpacing.TouchMin)
                                    .testTag("reader_more_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More Reading Options",
                                    tint = readerTheme.chromeText,
                                    modifier = Modifier.size(AppIconSize.Toolbar)
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                containerColor = readerTheme.chromeBg
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Contents & Notes",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = AppTypeSize.Body,
                                            color = readerTheme.chromeText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Toc,
                                            contentDescription = null,
                                            tint = readerTheme.chromeText,
                                            modifier = Modifier.size(AppIconSize.Inline)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showContentsSheet = true
                                    },
                                    modifier = Modifier.testTag("reader_contents_btn")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Add Highlight or Note",
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = AppTypeSize.Body,
                                            color = readerTheme.chromeText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.EditNote,
                                            contentDescription = null,
                                            tint = readerTheme.chromeText,
                                            modifier = Modifier.size(AppIconSize.Inline)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        annotatePrefillText = activeHighlightQuery ?: pageText.trim().take(180)
                                    },
                                    modifier = Modifier.testTag("reader_annotate_btn")
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = AppBorder.Hairline,
                        color = readerTheme.chromeBorder
                    )
                }
            }
        }

        // Lightweight bottom bar (hairline top divider, no shadow, compact arrows/slider, plain page text)
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = readerTheme.chromeBg,
                shadowElevation = AppElevation.None,
                tonalElevation = AppElevation.None,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = AppBorder.Hairline,
                        color = readerTheme.chromeBorder
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = AppSpacing.Sm, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(AppSpacing.TouchMin)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 1) readerTheme.chromeText else readerTheme.chromeMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        }

                        if (totalPages > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { currentPage = it.roundToInt().coerceIn(1, totalPages) },
                                valueRange = 1f..totalPages.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = AppSpacing.Sm),
                                colors = SliderDefaults.colors(
                                    thumbColor = readerTheme.accent,
                                    activeTrackColor = readerTheme.accent,
                                    inactiveTrackColor = readerTheme.chromeBorder
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(AppShapes.Small)
                                .clickable { showJumpPageSheet = true }
                                .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$currentPage/$totalPages",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Caption,
                                fontWeight = FontWeight.Medium,
                                color = readerTheme.chromeMuted
                            )
                        }

                        IconButton(
                            onClick = { if (currentPage < totalPages) currentPage++ },
                            enabled = currentPage < totalPages,
                            modifier = Modifier.size(AppSpacing.TouchMin)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "Next Page",
                                tint = if (currentPage < totalPages) readerTheme.chromeText else readerTheme.chromeMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReaderSettingsSheet) {
        EbookSettingsSheet(
            prefs = ebookPreferences,
            readerTheme = readerTheme,
            onChange = onUpdateEbookPreferences,
            onDismiss = { showReaderSettingsSheet = false }
        )
    }

    if (showContentsSheet) {
        BookContentsSheet(
            tocEntries = tocEntries,
            bookmarks = bookmarks,
            highlights = highlights,
            notes = notes,
            readerTheme = readerTheme,
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

    if (showSearchSheet) {
        InBookSearchSheet(
            bookTitle = book.title,
            readerTheme = readerTheme,
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

    if (showJumpPageSheet) {
        JumpToPageSheet(
            currentPage = currentPage,
            totalPages = totalPages,
            readerTheme = readerTheme,
            onJump = { page ->
                currentPage = page.coerceIn(1, totalPages)
                showJumpPageSheet = false
            },
            onDismiss = { showJumpPageSheet = false }
        )
    }

    if (annotatePrefillText != null || editingNote != null) {
        AnnotateNoteSheet(
            currentPage = currentPage,
            editingNote = editingNote,
            prefillText = annotatePrefillText.orEmpty(),
            readerTheme = readerTheme,
            onSave = { snippet, noteText, saveHighlight, colorHex ->
                if (editingNote != null) {
                    if (noteText.isNotBlank()) {
                        onSaveNote(book.id, currentPage, noteText, snippet, editingNote?.id)
                    }
                } else {
                    if (saveHighlight && snippet.isNotBlank()) {
                        onAddHighlight(book.id, currentPage, snippet, colorHex)
                    }
                    if (noteText.isNotBlank()) {
                        onSaveNote(book.id, currentPage, noteText, snippet, null)
                    }
                }
                annotatePrefillText = null
                editingNote = null
            },
            onDismiss = {
                annotatePrefillText = null
                editingNote = null
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
    readerTheme: ReaderThemePalette,
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
    val isBengali = remember(pageText) { isBengaliText(pageText) }
    val effectiveLineSpacing = if (isBengali) {
        maxOf(ebookPrefs.lineSpacingMultiplier, 1.85f)
    } else {
        ebookPrefs.lineSpacingMultiplier
    }
    val paragraphGap = ((ebookPrefs.fontSizeSp * 0.65f).roundToInt().coerceAtLeast(12)).dp

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerTheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = AppSpacing.MaxReaderWidth)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = ebookPrefs.horizontalMarginDp.dp,
                end = ebookPrefs.horizontalMarginDp.dp,
                top = 68.dp,
                bottom = 76.dp
            ),
            verticalArrangement = Arrangement.spacedBy(paragraphGap)
        ) {
            if (showHighlightBanner) {
                item(key = "highlight_banner") {
                    Surface(
                        shape = AppShapes.Small,
                        color = readerTheme.accent.copy(alpha = 0.08f),
                        border = BorderStroke(AppBorder.Hairline, readerTheme.accent.copy(alpha = 0.28f)),
                        shadowElevation = AppElevation.None,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FindInPage,
                                    contentDescription = null,
                                    tint = readerTheme.accent,
                                    modifier = Modifier.size(AppIconSize.Inline)
                                )
                                Text(
                                    text = "Matched line highlighted",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = AppTypeSize.Caption,
                                    fontWeight = FontWeight.Medium,
                                    color = readerTheme.accent
                                )
                            }
                            IconButton(
                                onClick = onClearSearchHighlight,
                                modifier = Modifier.size(AppSpacing.TouchMin)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear highlight",
                                    tint = readerTheme.accent,
                                    modifier = Modifier.size(AppIconSize.Inline)
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
                        color = readerTheme.accent,
                        modifier = Modifier.padding(bottom = AppSpacing.Xs)
                    )
                }
            }

            itemsIndexed(paragraphs) { idx, para ->
                val isMatchedParagraph = idx == matchedParaIndex
                val bestLineForThisPara = if (isMatchedParagraph) matchedBestLine else ""
                val annotated = remember(para, userHighlights, bestLineForThisPara, readerTheme) {
                    buildEbookParagraphText(
                        text = para,
                        userHighlights = userHighlights,
                        bestLine = bestLineForThisPara,
                        accentColor = readerTheme.accent,
                        inkColor = readerTheme.ink
                    )
                }

                SelectionContainer {
                    if (isMatchedParagraph) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(AppShapes.Medium)
                                .background(readerTheme.accent.copy(alpha = 0.06f))
                                .clickable { onParagraphClick(para) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(readerTheme.accent.copy(alpha = 0.85f))
                            )
                            Text(
                                text = annotated,
                                fontFamily = resolvedFont,
                                fontSize = ebookPrefs.fontSizeSp.sp,
                                lineHeight = (ebookPrefs.fontSizeSp * effectiveLineSpacing).sp,
                                color = readerTheme.ink,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm)
                            )
                        }
                    } else {
                        Text(
                            text = annotated,
                            fontFamily = resolvedFont,
                            fontSize = ebookPrefs.fontSizeSp.sp,
                            lineHeight = (ebookPrefs.fontSizeSp * effectiveLineSpacing).sp,
                            color = readerTheme.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.Small)
                                .clickable { onParagraphClick(para) }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            if (pageNotes.isNotEmpty()) {
                item(key = "notes_divider") {
                    HorizontalDivider(
                        thickness = AppBorder.Hairline,
                        color = readerTheme.chromeBorder
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.Sm))
                    Text(
                        text = "Page Notes (${pageNotes.size})",
                        fontFamily = PoppinsFontFamily,
                        fontSize = AppTypeSize.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = readerTheme.accent
                    )
                }
                items(pageNotes, key = { it.id }) { note ->
                    Surface(
                        shape = AppShapes.Medium,
                        color = readerTheme.subtleSurface,
                        border = BorderStroke(AppBorder.Hairline, readerTheme.chromeBorder),
                        shadowElevation = AppElevation.None,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)
                        ) {
                            if (note.selectedText.isNotBlank()) {
                                Text(
                                    text = "\"${note.selectedText}\"",
                                    fontFamily = selectFontForText(note.selectedText),
                                    fontSize = AppTypeSize.Micro,
                                    color = readerTheme.chromeMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = note.noteText,
                                fontFamily = selectFontForText(note.noteText),
                                fontSize = AppTypeSize.Body,
                                fontWeight = FontWeight.Medium,
                                color = readerTheme.ink
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onEditNote(note) }) {
                                    Text("Edit", fontSize = AppTypeSize.Caption, color = readerTheme.accent)
                                }
                                TextButton(onClick = { onDeleteNote(note.id) }) {
                                    Text("Delete", fontSize = AppTypeSize.Caption, color = RoseError)
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
private fun EbookSettingsSheet(
    prefs: EbookReaderPreferences,
    readerTheme: ReaderThemePalette,
    onChange: (EbookReaderPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    MinimalBottomSheet(
        title = "Reading Settings",
        onDismiss = onDismiss,
        containerColor = readerTheme.chromeBg,
        titleColor = readerTheme.chromeText,
        mutedColor = readerTheme.chromeMuted,
        borderColor = readerTheme.chromeBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Font Size",
                        fontFamily = PoppinsFontFamily,
                        fontSize = AppTypeSize.Body,
                        fontWeight = FontWeight.Medium,
                        color = readerTheme.chromeText
                    )
                    Text(
                        text = "${prefs.fontSizeSp.toInt()} sp",
                        fontFamily = PoppinsFontFamily,
                        fontSize = AppTypeSize.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = readerTheme.accent
                    )
                }
                Slider(
                    value = prefs.fontSizeSp,
                    onValueChange = { onChange(prefs.copy(fontSizeSp = it.roundToInt().toFloat())) },
                    valueRange = 12f..28f,
                    colors = SliderDefaults.colors(
                        thumbColor = readerTheme.accent,
                        activeTrackColor = readerTheme.accent,
                        inactiveTrackColor = readerTheme.chromeBorder
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Text(
                    text = "Font Style",
                    fontFamily = PoppinsFontFamily,
                    fontSize = AppTypeSize.Body,
                    fontWeight = FontWeight.Medium,
                    color = readerTheme.chromeText
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
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
                        MinimalChip(
                            label = label,
                            selected = prefs.fontFamilyKey == key,
                            onClick = { onChange(prefs.copy(fontFamilyKey = key)) },
                            accentColor = readerTheme.accent,
                            surfaceColor = readerTheme.subtleSurface,
                            textColor = readerTheme.chromeText,
                            borderColor = readerTheme.chromeBorder
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Text(
                    text = "Line Spacing",
                    fontFamily = PoppinsFontFamily,
                    fontSize = AppTypeSize.Body,
                    fontWeight = FontWeight.Medium,
                    color = readerTheme.chromeText
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    listOf(
                        1.25f to "Tight",
                        1.55f to "Normal",
                        1.8f to "Relaxed",
                        2.05f to "Wide"
                    ).forEach { (valF, label) ->
                        MinimalChip(
                            label = label,
                            selected = kotlin.math.abs(prefs.lineSpacingMultiplier - valF) < 0.05f,
                            onClick = { onChange(prefs.copy(lineSpacingMultiplier = valF)) },
                            accentColor = readerTheme.accent,
                            surfaceColor = readerTheme.subtleSurface,
                            textColor = readerTheme.chromeText,
                            borderColor = readerTheme.chromeBorder
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Text(
                    text = "Page Margins",
                    fontFamily = PoppinsFontFamily,
                    fontSize = AppTypeSize.Body,
                    fontWeight = FontWeight.Medium,
                    color = readerTheme.chromeText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    listOf(
                        12f to "Narrow",
                        20f to "Medium",
                        32f to "Wide"
                    ).forEach { (valM, label) ->
                        MinimalChip(
                            label = label,
                            selected = kotlin.math.abs(prefs.horizontalMarginDp - valM) < 1f,
                            onClick = { onChange(prefs.copy(horizontalMarginDp = valM)) },
                            accentColor = readerTheme.accent,
                            surfaceColor = readerTheme.subtleSurface,
                            textColor = readerTheme.chromeText,
                            borderColor = readerTheme.chromeBorder
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Text(
                    text = "Theme",
                    fontFamily = PoppinsFontFamily,
                    fontSize = AppTypeSize.Body,
                    fontWeight = FontWeight.Medium,
                    color = readerTheme.chromeText
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    ReaderThemes.All.forEach { themeOption ->
                        MinimalChip(
                            label = themeOption.label,
                            selected = prefs.themeKey == themeOption.key,
                            onClick = { onChange(prefs.copy(themeKey = themeOption.key)) },
                            accentColor = readerTheme.accent,
                            surfaceColor = readerTheme.subtleSurface,
                            textColor = readerTheme.chromeText,
                            borderColor = readerTheme.chromeBorder,
                            leadingDotColor = themeOption.background
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookContentsSheet(
    tocEntries: List<BookTocEntity>,
    bookmarks: List<BookBookmarkEntity>,
    highlights: List<BookHighlightEntity>,
    notes: List<BookNoteEntity>,
    readerTheme: ReaderThemePalette,
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

    MinimalBottomSheet(
        title = "Contents & Notes",
        onDismiss = onDismiss,
        containerColor = readerTheme.chromeBg,
        titleColor = readerTheme.chromeText,
        mutedColor = readerTheme.chromeMuted,
        borderColor = readerTheme.chromeBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            tabs.forEachIndexed { idx, label ->
                MinimalChip(
                    label = label,
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    accentColor = readerTheme.accent,
                    surfaceColor = readerTheme.subtleSurface,
                    textColor = readerTheme.chromeText,
                    borderColor = readerTheme.chromeBorder
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            when (selectedTab) {
                0 -> {
                    if (tocEntries.isEmpty()) {
                        item {
                            Text(
                                text = "No Table of Contents entries extracted.",
                                color = readerTheme.chromeMuted,
                                fontSize = AppTypeSize.Body,
                                modifier = Modifier.padding(vertical = AppSpacing.Lg)
                            )
                        }
                    } else {
                        items(tocEntries, key = { it.id }) { entry ->
                            Surface(
                                onClick = { onJumpToPage(entry.pageNumber, null) },
                                shape = AppShapes.Medium,
                                color = readerTheme.subtleSurface,
                                shadowElevation = AppElevation.None,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppSpacing.Md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.title,
                                        fontFamily = selectFontForText(entry.title),
                                        fontSize = AppTypeSize.Body,
                                        fontWeight = if (entry.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                                        color = readerTheme.chromeText,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = ((entry.level - 1) * 12).dp)
                                    )
                                    Text(
                                        text = "p.${entry.pageNumber}",
                                        fontSize = AppTypeSize.Micro,
                                        fontWeight = FontWeight.SemiBold,
                                        color = readerTheme.accent
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (bookmarks.isEmpty()) {
                        item {
                            Text(
                                text = "No bookmarks saved yet.",
                                color = readerTheme.chromeMuted,
                                fontSize = AppTypeSize.Body,
                                modifier = Modifier.padding(vertical = AppSpacing.Lg)
                            )
                        }
                    } else {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                onClick = { onJumpToPage(bm.pageNumber, null) },
                                shape = AppShapes.Medium,
                                color = readerTheme.subtleSurface,
                                shadowElevation = AppElevation.None,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppSpacing.Md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page ${bm.pageNumber} • ${bm.title}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = AppTypeSize.Body,
                                            color = readerTheme.chromeText
                                        )
                                        if (bm.snippet.isNotBlank()) {
                                            Text(
                                                text = bm.snippet,
                                                fontSize = AppTypeSize.Caption,
                                                color = readerTheme.chromeMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteBookmark(bm.id) },
                                        modifier = Modifier.size(AppSpacing.TouchMin)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove Bookmark",
                                            tint = RoseError,
                                            modifier = Modifier.size(AppIconSize.Inline)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (highlights.isEmpty()) {
                        item {
                            Text(
                                text = "No highlights saved yet. Tap any paragraph to highlight.",
                                color = readerTheme.chromeMuted,
                                fontSize = AppTypeSize.Body,
                                modifier = Modifier.padding(vertical = AppSpacing.Lg)
                            )
                        }
                    } else {
                        items(highlights, key = { it.id }) { hl ->
                            Surface(
                                onClick = { onJumpToPage(hl.pageNumber, hl.selectedText) },
                                shape = AppShapes.Medium,
                                color = readerTheme.subtleSurface,
                                shadowElevation = AppElevation.None,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppSpacing.Md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(AppShapes.Pill)
                                            .background(ReaderThemes.parseHighlightColor(hl.colorHex))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page ${hl.pageNumber}",
                                            fontSize = AppTypeSize.Micro,
                                            fontWeight = FontWeight.SemiBold,
                                            color = readerTheme.accent
                                        )
                                        Text(
                                            text = hl.selectedText,
                                            fontSize = AppTypeSize.Caption,
                                            color = readerTheme.chromeText,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteHighlight(hl.id) },
                                        modifier = Modifier.size(AppSpacing.TouchMin)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete Highlight",
                                            tint = RoseError,
                                            modifier = Modifier.size(AppIconSize.Inline)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (notes.isEmpty()) {
                        item {
                            Text(
                                text = "No notes saved yet.",
                                color = readerTheme.chromeMuted,
                                fontSize = AppTypeSize.Body,
                                modifier = Modifier.padding(vertical = AppSpacing.Lg)
                            )
                        }
                    } else {
                        items(notes, key = { it.id }) { note ->
                            Surface(
                                onClick = { onJumpToPage(note.pageNumber, note.selectedText.ifBlank { null }) },
                                shape = AppShapes.Medium,
                                color = readerTheme.subtleSurface,
                                shadowElevation = AppElevation.None,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppSpacing.Md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Page ${note.pageNumber}",
                                            fontSize = AppTypeSize.Micro,
                                            fontWeight = FontWeight.SemiBold,
                                            color = readerTheme.accent
                                        )
                                        Text(
                                            text = note.noteText,
                                            fontSize = AppTypeSize.Body,
                                            fontWeight = FontWeight.Medium,
                                            color = readerTheme.chromeText
                                        )
                                        if (note.selectedText.isNotBlank()) {
                                            Text(
                                                text = "\"${note.selectedText}\"",
                                                fontSize = AppTypeSize.Micro,
                                                color = readerTheme.chromeMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.size(AppSpacing.TouchMin)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete Note",
                                            tint = RoseError,
                                            modifier = Modifier.size(AppIconSize.Inline)
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

@Composable
private fun InBookSearchSheet(
    bookTitle: String,
    readerTheme: ReaderThemePalette,
    onSearch: suspend (String) -> List<BookPassageMatch>,
    onSelectResult: (BookPassageMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<BookPassageMatch>>(emptyList()) }

    MinimalBottomSheet(
        title = "Search Inside Book",
        subtitle = bookTitle,
        onDismiss = onDismiss,
        containerColor = readerTheme.chromeBg,
        titleColor = readerTheme.chromeText,
        mutedColor = readerTheme.chromeMuted,
        borderColor = readerTheme.chromeBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Keywords or phrase...", fontSize = AppTypeSize.Caption) },
                singleLine = true,
                shape = AppShapes.Medium,
                colors = readerTextFieldColors(readerTheme),
                modifier = Modifier
                    .weight(1f)
                    .testTag("inbook_search_input")
            )
            MinimalPrimaryButton(
                text = "Find",
                onClick = {
                    scope.launch {
                        isSearching = true
                        results = onSearch(query)
                        isSearching = false
                    }
                },
                enabled = query.trim().length >= 2 && !isSearching,
                accentColor = IndigoPrimary,
                modifier = Modifier.testTag("inbook_search_go_btn")
            )
        }

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = readerTheme.accent)
            }
        } else if (results.isEmpty() && query.trim().length >= 2) {
            Text(
                text = "Tap Find to search, or try different keywords.",
                fontSize = AppTypeSize.Caption,
                color = readerTheme.chromeMuted,
                modifier = Modifier.padding(vertical = AppSpacing.Md)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                items(results) { match ->
                    Surface(
                        onClick = { onSelectResult(match) },
                        shape = AppShapes.Medium,
                        color = readerTheme.subtleSurface,
                        border = BorderStroke(AppBorder.Hairline, readerTheme.chromeBorder),
                        shadowElevation = AppElevation.None,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = AppShapes.Small,
                                    color = readerTheme.accent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Page ${match.pageNumber}",
                                        fontSize = AppTypeSize.Micro,
                                        fontWeight = FontWeight.SemiBold,
                                        color = readerTheme.accent,
                                        modifier = Modifier.padding(horizontal = AppSpacing.Sm, vertical = 2.dp)
                                    )
                                }
                                if (match.chapterTitle.isNotBlank()) {
                                    Text(
                                        text = match.chapterTitle,
                                        fontSize = AppTypeSize.Micro,
                                        color = readerTheme.chromeMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    .clip(AppShapes.Small)
                                    .background(readerTheme.accent.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .fillMaxHeight()
                                        .background(readerTheme.accent.copy(alpha = 0.7f))
                                )
                                Text(
                                    text = buildEbookParagraphText(
                                        text = match.passageSnippet,
                                        userHighlights = emptyList(),
                                        bestLine = previewBestLine,
                                        accentColor = readerTheme.accent,
                                        inkColor = readerTheme.chromeText
                                    ),
                                    fontFamily = selectFontForText(match.passageSnippet),
                                    fontSize = AppTypeSize.Caption,
                                    color = readerTheme.chromeText.copy(alpha = 0.85f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpToPageSheet(
    currentPage: Int,
    totalPages: Int,
    readerTheme: ReaderThemePalette,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }

    MinimalBottomSheet(
        title = "Jump to Page",
        subtitle = "Page 1 - $totalPages",
        onDismiss = onDismiss,
        containerColor = readerTheme.chromeBg,
        titleColor = readerTheme.chromeText,
        mutedColor = readerTheme.chromeMuted,
        borderColor = readerTheme.chromeBorder
    ) {
        OutlinedTextField(
            value = pageInput,
            onValueChange = { v -> if (v.all { it.isDigit() }) pageInput = v },
            label = { Text("Page Number", fontSize = AppTypeSize.Caption) },
            singleLine = true,
            shape = AppShapes.Medium,
            colors = readerTextFieldColors(readerTheme),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            MinimalSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                textColor = readerTheme.chromeMuted,
                borderColor = readerTheme.chromeBorder,
                modifier = Modifier.weight(1f)
            )
            MinimalPrimaryButton(
                text = "Go",
                onClick = {
                    val p = pageInput.toIntOrNull()
                    if (p != null) onJump(p) else onDismiss()
                },
                accentColor = IndigoPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnnotateNoteSheet(
    currentPage: Int,
    editingNote: BookNoteEntity?,
    prefillText: String,
    readerTheme: ReaderThemePalette,
    onSave: (snippet: String, noteText: String, saveHighlight: Boolean, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialSnippet = editingNote?.selectedText ?: prefillText
    var snippetInput by remember { mutableStateOf(initialSnippet) }
    var noteInput by remember { mutableStateOf(editingNote?.noteText.orEmpty()) }
    var selectedColorHex by remember { mutableStateOf(ReaderThemes.HighlightSwatches.first().hex) }
    var saveAsHighlight by remember { mutableStateOf(editingNote == null) }

    MinimalBottomSheet(
        title = if (editingNote != null) "Edit Note" else "Highlight & Note",
        subtitle = "Page $currentPage",
        onDismiss = onDismiss,
        containerColor = readerTheme.chromeBg,
        titleColor = readerTheme.chromeText,
        mutedColor = readerTheme.chromeMuted,
        borderColor = readerTheme.chromeBorder
    ) {
        OutlinedTextField(
            value = snippetInput,
            onValueChange = { snippetInput = it },
            label = { Text("Passage / Selected Text", fontSize = AppTypeSize.Caption) },
            maxLines = 4,
            shape = AppShapes.Medium,
            colors = readerTextFieldColors(readerTheme),
            modifier = Modifier.fillMaxWidth()
        )

        if (editingNote == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                Checkbox(
                    checked = saveAsHighlight,
                    onCheckedChange = { saveAsHighlight = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = readerTheme.accent,
                        checkmarkColor = readerTheme.chromeBg,
                        uncheckedColor = readerTheme.chromeMuted
                    )
                )
                Text(
                    text = "Highlight",
                    fontSize = AppTypeSize.Body,
                    fontWeight = FontWeight.Medium,
                    color = readerTheme.chromeText
                )
                Spacer(modifier = Modifier.weight(1f))
                ReaderThemes.HighlightSwatches.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(AppShapes.Pill)
                            .background(swatch.color)
                            .border(
                                width = if (selectedColorHex == swatch.hex) 2.dp else AppBorder.Hairline,
                                color = if (selectedColorHex == swatch.hex) readerTheme.accent else readerTheme.chromeBorder,
                                shape = AppShapes.Pill
                            )
                            .clickable {
                                selectedColorHex = swatch.hex
                                saveAsHighlight = true
                            }
                    )
                }
            }
        }

        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = { Text("Your Note (optional if highlighting)", fontSize = AppTypeSize.Caption) },
            maxLines = 4,
            shape = AppShapes.Medium,
            colors = readerTextFieldColors(readerTheme),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            MinimalSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                textColor = readerTheme.chromeMuted,
                borderColor = readerTheme.chromeBorder,
                modifier = Modifier.weight(1f)
            )
            MinimalPrimaryButton(
                text = "Save",
                onClick = { onSave(snippetInput, noteInput, saveAsHighlight, selectedColorHex) },
                enabled = (saveAsHighlight && snippetInput.isNotBlank()) || noteInput.isNotBlank(),
                accentColor = IndigoPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun readerTextFieldColors(readerTheme: ReaderThemePalette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = readerTheme.chromeText,
    unfocusedTextColor = readerTheme.chromeText,
    cursorColor = readerTheme.accent,
    focusedBorderColor = readerTheme.accent,
    unfocusedBorderColor = readerTheme.chromeBorder,
    focusedLabelColor = readerTheme.accent,
    unfocusedLabelColor = readerTheme.chromeMuted,
    focusedPlaceholderColor = readerTheme.chromeMuted,
    unfocusedPlaceholderColor = readerTheme.chromeMuted
)

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

    var matchedIdx = -1
    if (cleanParaQuery.length >= 4) {
        val normParaQ = normalizeWhitespace(cleanParaQuery)
        matchedIdx = paragraphs.indexOfFirst { para ->
            para.contains(cleanParaQuery, ignoreCase = true) ||
                cleanParaQuery.contains(para, ignoreCase = true) ||
                normalizeWhitespace(para).contains(normParaQ)
        }
    }

    if (matchedIdx < 0 && cleanLineQuery.length >= 2) {
        val normLineQ = normalizeWhitespace(cleanLineQuery)
        matchedIdx = paragraphs.indexOfFirst { para ->
            para.contains(cleanLineQuery, ignoreCase = true) ||
                normalizeWhitespace(para).contains(normLineQ)
        }
    }

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

    for (hl in userHighlights) {
        val target = hl.selectedText.trim()
        if (target.length < 2) continue
        val idx = lower.indexOf(target.lowercase())
        if (idx >= 0) {
            val end = (idx + target.length).coerceAtMost(text.length)
            addStyle(
                SpanStyle(
                    background = ReaderThemes.parseHighlightColor(hl.colorHex).copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium
                ),
                idx,
                end
            )
        }
    }

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
