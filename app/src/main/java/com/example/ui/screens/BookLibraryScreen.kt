package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.data.sync.DiscoveredDriveFile
import com.example.data.sync.GoogleDriveSyncService
import com.example.ui.components.MinimalBottomSheet
import com.example.ui.components.MinimalPrimaryButton
import com.example.ui.components.MinimalSecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookLibraryScreen(
    books: List<BookEntity>,
    bookmarks: List<BookBookmarkEntity>,
    highlights: List<BookHighlightEntity>,
    notes: List<BookNoteEntity>,
    driveBooks: List<DiscoveredDriveFile>,
    isRefreshingDriveBooks: Boolean,
    downloadingBookFileIds: Set<String>,
    isImportingBooks: Boolean,
    bookImportStatusText: String?,
    observeBookToc: (String) -> Flow<List<BookTocEntity>>,
    onImportBookUris: (List<Uri>) -> Unit,
    onImportFolderUri: (Uri) -> Unit,
    onRefreshDriveBooks: (String) -> Unit,
    onDownloadDriveBook: (DiscoveredDriveFile) -> Unit,
    onDownloadAllDriveBooks: () -> Unit,
    onOpenReader: (bookId: String, pageNumber: Int?) -> Unit,
    onUpdateBookMetadata: (bookId: String, title: String, author: String, subject: String, description: String) -> Unit,
    onReindexBook: (String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val accent = appAccentColor(palette.isDark)

    var selectedBookForProfile by remember { mutableStateOf<BookEntity?>(null) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showDriveBooksSheet by remember { mutableStateOf(false) }

    val liveProfileBook = remember(books, selectedBookForProfile) {
        selectedBookForProfile?.let { sel -> books.find { it.id == sel.id } }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            onImportBookUris(uris)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onImportFolderUri(treeUri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Minimal Top Bar: Back + Title + Single "+" Import Menu
        Surface(
            color = palette.surface,
            shadowElevation = AppElevation.None,
            tonalElevation = AppElevation.None,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(AppSpacing.TouchMin)
                                .testTag("books_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(AppIconSize.Toolbar)
                            )
                        }
                        Text(
                            text = "Books",
                            fontFamily = PoppinsFontFamily,
                            fontSize = AppTypeSize.Headline,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showImportMenu = true },
                            modifier = Modifier
                                .size(AppSpacing.TouchMin)
                                .testTag("books_import_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Import Books",
                                tint = accent,
                                modifier = Modifier.size(AppIconSize.Toolbar)
                            )
                        }

                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                            containerColor = palette.surface
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Import File",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = AppTypeSize.Body,
                                        color = palette.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(AppIconSize.Inline)
                                    )
                                },
                                onClick = {
                                    showImportMenu = false
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("books_import_file_button")
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Import Folder",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = AppTypeSize.Body,
                                        color = palette.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CreateNewFolder,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(AppIconSize.Inline)
                                    )
                                },
                                onClick = {
                                    showImportMenu = false
                                    folderPickerLauncher.launch(null)
                                },
                                modifier = Modifier.testTag("books_import_folder_button")
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Import from Drive",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = AppTypeSize.Body,
                                        color = accent
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(AppIconSize.Inline)
                                    )
                                },
                                onClick = {
                                    showImportMenu = false
                                    showDriveBooksSheet = true
                                    if (driveBooks.isEmpty()) {
                                        onRefreshDriveBooks(GoogleDriveSyncService.DEFAULT_BOOKS_DRIVE_FOLDER_ID)
                                    }
                                },
                                modifier = Modifier.testTag("books_drive_catalog_button")
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = palette.border.copy(alpha = 0.65f),
                    thickness = AppBorder.Hairline
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("book_library_list"),
            contentPadding = PaddingValues(top = AppSpacing.Md, bottom = 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Book Library",
                        fontFamily = PoppinsFontFamily,
                        fontSize = AppTypeSize.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textMuted
                    )
                    if (books.isNotEmpty()) {
                        val bookLabel = if (books.size == 1) "1 book" else "${books.size} books"
                        Text(
                            text = "$bookLabel • ${books.sumOf { it.totalPages }} pages",
                            fontFamily = PoppinsFontFamily,
                            fontSize = AppTypeSize.Micro,
                            color = palette.textMuted
                        )
                    }
                }
            }

            if (isImportingBooks) {
                item {
                    Surface(
                        shape = AppShapes.Medium,
                        color = accent.copy(alpha = 0.10f),
                        shadowElevation = AppElevation.None,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AppIconSize.Small),
                                strokeWidth = 2.dp,
                                color = accent
                            )
                            Text(
                                text = bookImportStatusText ?: "Indexing book offline...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Caption,
                                fontWeight = FontWeight.Medium,
                                color = accent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (books.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = AppSpacing.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(AppIconSize.Hero)
                        )
                        Text(
                            text = "No Offline Books Yet",
                            fontFamily = PoppinsFontFamily,
                            fontSize = AppTypeSize.Title,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Tap + in the top-right corner to add from File, Folder, or Drive.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = AppTypeSize.Caption,
                            color = palette.textMuted
                        )
                    }
                }
            } else {
                items(books, key = { it.id }) { book ->
                    BookListRowItem(
                        book = book,
                        onRowClick = { selectedBookForProfile = book },
                        onQuickRead = { onOpenReader(book.id, book.lastReadPage) }
                    )
                    HorizontalDivider(
                        thickness = AppBorder.Hairline,
                        color = palette.border.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = AppSpacing.Lg)
                    )
                }
            }
        }
    }

    if (liveProfileBook != null) {
        val bookToc by observeBookToc(liveProfileBook.id).collectAsState(initial = emptyList())
        val bookBookmarks = remember(bookmarks, liveProfileBook.id) { bookmarks.filter { it.bookId == liveProfileBook.id } }
        val bookHighlights = remember(highlights, liveProfileBook.id) { highlights.filter { it.bookId == liveProfileBook.id } }
        val bookNotes = remember(notes, liveProfileBook.id) { notes.filter { it.bookId == liveProfileBook.id } }

        BookProfileSheet(
            book = liveProfileBook,
            toc = bookToc,
            bookmarkCount = bookBookmarks.size,
            highlightCount = bookHighlights.size,
            noteCount = bookNotes.size,
            onDismiss = { selectedBookForProfile = null },
            onOpenReader = { page ->
                selectedBookForProfile = null
                onOpenReader(liveProfileBook.id, page)
            },
            onUpdateMetadata = { t, a, s, d ->
                onUpdateBookMetadata(liveProfileBook.id, t, a, s, d)
            },
            onReindex = { onReindexBook(liveProfileBook.id) },
            onDelete = {
                selectedBookForProfile = null
                onDeleteBook(liveProfileBook)
            }
        )
    }

    if (showDriveBooksSheet) {
        DriveBooksCatalogSheet(
            books = books,
            driveBooks = driveBooks,
            isRefreshing = isRefreshingDriveBooks,
            downloadingIds = downloadingBookFileIds,
            onRefresh = { url -> onRefreshDriveBooks(url) },
            onDownloadSingle = { onDownloadDriveBook(it) },
            onDownloadAll = { onDownloadAllDriveBooks() },
            onDismiss = { showDriveBooksSheet = false }
        )
    }
}

@Composable
private fun BookListRowItem(
    book: BookEntity,
    onRowClick: () -> Unit,
    onQuickRead: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = appAccentColor(palette.isDark)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(start = AppSpacing.Lg, end = AppSpacing.Sm, top = AppSpacing.Md, bottom = AppSpacing.Md)
            .testTag("book_card_${book.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = book.title,
                fontFamily = selectFontForText(book.title),
                fontSize = AppTypeSize.Title,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitleText = when (book.indexStatus) {
                "indexing" -> "Indexing text..."
                "error" -> "Indexing failed"
                else -> buildList {
                    if (book.author.isNotBlank()) add(book.author)
                    if (book.totalPages > 0) add("${book.totalPages} pages")
                }.joinToString(" • ").ifBlank { book.fileName }
            }

            Text(
                text = subtitleText,
                fontFamily = PoppinsFontFamily,
                fontSize = AppTypeSize.Caption,
                color = if (book.indexStatus == "error") RoseError else palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TextButton(
            onClick = onQuickRead,
            modifier = Modifier
                .defaultMinSize(minWidth = AppSpacing.TouchMin, minHeight = AppSpacing.TouchMin)
                .testTag("book_read_btn_${book.id}")
        ) {
            Text(
                text = "Read",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = AppTypeSize.Body,
                color = accent
            )
        }
    }
}

@Composable
private fun BookProfileSheet(
    book: BookEntity,
    toc: List<BookTocEntity>,
    bookmarkCount: Int,
    highlightCount: Int,
    noteCount: Int,
    onDismiss: () -> Unit,
    onOpenReader: (page: Int) -> Unit,
    onUpdateMetadata: (title: String, author: String, subject: String, description: String) -> Unit,
    onReindex: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = appAccentColor(palette.isDark)
    var showProfileMenu by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    var showTocList by remember { mutableStateOf(toc.isNotEmpty()) }

    val metaSubtitle = listOf(book.author, book.subject).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { null }

    MinimalBottomSheet(
        title = book.title,
        subtitle = metaSubtitle,
        onDismiss = onDismiss,
        headerActions = {
            Box {
                IconButton(
                    onClick = { showProfileMenu = true },
                    modifier = Modifier.size(AppSpacing.TouchMin)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Book Options",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(AppIconSize.Toolbar)
                    )
                }

                DropdownMenu(
                    expanded = showProfileMenu,
                    onDismissRequest = { showProfileMenu = false },
                    containerColor = palette.surface
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Edit",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Body,
                                color = palette.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = palette.textPrimary,
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        },
                        onClick = {
                            showProfileMenu = false
                            showEditSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Re-index",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Body,
                                color = palette.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = palette.textPrimary,
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        },
                        onClick = {
                            showProfileMenu = false
                            onReindex()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                fontFamily = PoppinsFontFamily,
                                fontSize = AppTypeSize.Body,
                                color = RoseError
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = RoseError,
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        },
                        onClick = {
                            showProfileMenu = false
                            showDeleteSheet = true
                        }
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                Surface(shape = AppShapes.Small, color = accent.copy(alpha = 0.12f)) {
                    Text(
                        text = book.format,
                        fontSize = AppTypeSize.Micro,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = AppSpacing.Sm, vertical = 2.dp)
                    )
                }
                Surface(shape = AppShapes.Small, color = EmeraldSuccess.copy(alpha = 0.12f)) {
                    Text(
                        text = book.sourceType.replaceFirstChar { it.uppercase() },
                        fontSize = AppTypeSize.Micro,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = AppSpacing.Sm, vertical = 2.dp)
                    )
                }
                val linkIds = remember(book.fileName, book.title) {
                    com.example.data.search.BookSearchEngine.extractBookLinkIds(book)
                }
                linkIds.forEach { tag ->
                    Surface(shape = AppShapes.Small, color = accent.copy(alpha = 0.12f)) {
                        Text(
                            text = "id-$tag",
                            fontSize = AppTypeSize.Micro,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = AppSpacing.Sm, vertical = 2.dp)
                        )
                    }
                }
            }

            if (book.description.isNotBlank()) {
                Text(
                    text = book.description,
                    fontSize = AppTypeSize.Caption,
                    color = palette.textMuted
                )
            }

            Surface(
                shape = AppShapes.Medium,
                color = palette.background,
                border = BorderStroke(AppBorder.Hairline, palette.border),
                shadowElevation = AppElevation.None,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.Md),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileMetric("Pages", "${book.totalPages}")
                    ProfileMetric("Size", formatFileSize(book.fileSizeBytes))
                    ProfileMetric("Bookmarks", "$bookmarkCount")
                    ProfileMetric("Highlights", "$highlightCount")
                    ProfileMetric("Notes", "$noteCount")
                }
            }

            // Single primary action button: Read
            MinimalPrimaryButton(
                text = "Read Book",
                onClick = { onOpenReader(book.lastReadPage) },
                accentColor = IndigoPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_open_ebook_btn"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(AppIconSize.Inline)
                    )
                }
            )

            if (toc.isNotEmpty()) {
                Surface(
                    shape = AppShapes.Medium,
                    color = palette.background,
                    border = BorderStroke(AppBorder.Hairline, palette.border),
                    shadowElevation = AppElevation.None,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTocList = !showTocList },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Table of Contents (${toc.size})",
                                fontSize = AppTypeSize.Body,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Icon(
                                imageVector = if (showTocList) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Toggle TOC",
                                tint = palette.textMuted,
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        }
                        AnimatedVisibility(visible = showTocList) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                                toc.take(15).forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.Small)
                                            .clickable { onOpenReader(entry.pageNumber) }
                                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Sm),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontFamily = selectFontForText(entry.title),
                                            fontSize = AppTypeSize.Caption,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = ((entry.level - 1) * 12).dp)
                                        )
                                        Text(
                                            text = "p.${entry.pageNumber}",
                                            fontSize = AppTypeSize.Micro,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent
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

    if (showEditSheet) {
        EditBookProfileSheet(
            book = book,
            onSave = { t, a, s, d ->
                onUpdateMetadata(t, a, s, d)
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false }
        )
    }

    if (showDeleteSheet) {
        ConfirmDeleteBookSheet(
            bookTitle = book.title,
            onConfirm = {
                showDeleteSheet = false
                onDelete()
            },
            onDismiss = { showDeleteSheet = false }
        )
    }
}

@Composable
private fun EditBookProfileSheet(
    book: BookEntity,
    onSave: (title: String, author: String, subject: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var editTitle by remember { mutableStateOf(book.title) }
    var editAuthor by remember { mutableStateOf(book.author) }
    var editSubject by remember { mutableStateOf(book.subject) }
    var editDesc by remember { mutableStateOf(book.description) }

    MinimalBottomSheet(
        title = "Edit Book Profile",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = editTitle,
            onValueChange = { editTitle = it },
            label = { Text("Book Title", fontSize = AppTypeSize.Caption) },
            singleLine = true,
            shape = AppShapes.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editAuthor,
            onValueChange = { editAuthor = it },
            label = { Text("Author", fontSize = AppTypeSize.Caption) },
            singleLine = true,
            shape = AppShapes.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editSubject,
            onValueChange = { editSubject = it },
            label = { Text("Subject / Category", fontSize = AppTypeSize.Caption) },
            singleLine = true,
            shape = AppShapes.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editDesc,
            onValueChange = { editDesc = it },
            label = { Text("Description / Notes", fontSize = AppTypeSize.Caption) },
            maxLines = 3,
            shape = AppShapes.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            MinimalSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            MinimalPrimaryButton(
                text = "Save",
                onClick = { onSave(editTitle, editAuthor, editSubject, editDesc) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConfirmDeleteBookSheet(
    bookTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    MinimalBottomSheet(
        title = "Remove Offline Book?",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Delete '$bookTitle' and its offline file, bookmarks, highlights, and notes from this device?",
            fontSize = AppTypeSize.Body,
            color = palette.textMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
        ) {
            MinimalSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            MinimalPrimaryButton(
                text = "Delete",
                onClick = onConfirm,
                accentColor = RoseError,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    val palette = LocalAppPalette.current
    val accent = appAccentColor(palette.isDark)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = AppTypeSize.Body,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Text(
            text = label,
            fontSize = AppTypeSize.Micro,
            color = palette.textMuted
        )
    }
}

@Composable
private fun DriveBooksCatalogSheet(
    books: List<BookEntity>,
    driveBooks: List<DiscoveredDriveFile>,
    isRefreshing: Boolean,
    downloadingIds: Set<String>,
    onRefresh: (String) -> Unit,
    onDownloadSingle: (DiscoveredDriveFile) -> Unit,
    onDownloadAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = appAccentColor(palette.isDark)
    var folderUrlInput by remember { mutableStateOf(GoogleDriveSyncService.DEFAULT_BOOKS_DRIVE_FOLDER_URL) }

    MinimalBottomSheet(
        title = "Google Drive Library",
        subtitle = "Download DOCX eBooks for offline study",
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = folderUrlInput,
                onValueChange = { folderUrlInput = it },
                label = { Text("Drive Folder URL", fontSize = AppTypeSize.Micro) },
                singleLine = true,
                shape = AppShapes.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onRefresh(folderUrlInput) },
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(AppSpacing.TouchMin)
                    .clip(AppShapes.Medium)
                    .background(accent.copy(alpha = 0.12f))
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppIconSize.Inline),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Scan Folder",
                        tint = accent,
                        modifier = Modifier.size(AppIconSize.Toolbar)
                    )
                }
            }
        }

        if (driveBooks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${driveBooks.size} book(s) found",
                    fontSize = AppTypeSize.Caption,
                    fontWeight = FontWeight.Medium,
                    color = palette.textMuted
                )
                TextButton(onClick = onDownloadAll) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(AppIconSize.Small)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.Xs))
                    Text(
                        text = "Download All",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = AppTypeSize.Caption,
                        color = accent
                    )
                }
            }
        }

        if (isRefreshing && driveBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    CircularProgressIndicator(color = accent)
                    Text(
                        text = "Scanning Google Drive folder...",
                        fontSize = AppTypeSize.Caption,
                        color = palette.textMuted
                    )
                }
            }
        } else if (driveBooks.isEmpty()) {
            Surface(
                shape = AppShapes.Medium,
                color = palette.background,
                shadowElevation = AppElevation.None,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.Xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                ) {
                    Text(
                        text = "No DOCX eBooks discovered yet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = AppTypeSize.Body,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Verify folder sharing is set to 'Anyone with the link' and tap Refresh.",
                        fontSize = AppTypeSize.Caption,
                        color = palette.textMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
            ) {
                items(driveBooks, key = { it.fileId }) { item ->
                    val isDownloading = item.fileId in downloadingIds
                    val isSavedOffline = books.any { it.sourceRef == "drive:${item.fileId}" }
                    Surface(
                        shape = AppShapes.Medium,
                        color = palette.background,
                        border = BorderStroke(AppBorder.Hairline, palette.border),
                        shadowElevation = AppElevation.None,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.Md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontFamily = selectFontForText(item.title),
                                    fontSize = AppTypeSize.Body,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSavedOffline) "Saved offline" else "Tap to download offline",
                                    fontSize = AppTypeSize.Micro,
                                    color = if (isSavedOffline) EmeraldSuccess else palette.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.Sm))
                            IconButton(
                                onClick = { onDownloadSingle(item) },
                                enabled = !isDownloading,
                                modifier = Modifier.size(AppSpacing.TouchMin)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(AppIconSize.Inline),
                                        strokeWidth = 2.dp,
                                        color = accent
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isSavedOffline) Icons.Outlined.DownloadDone else Icons.Outlined.CloudDownload,
                                        contentDescription = "Download ${item.title}",
                                        tint = if (isSavedOffline) EmeraldSuccess else accent,
                                        modifier = Modifier.size(AppIconSize.Toolbar)
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
