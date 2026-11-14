package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.data.sync.DiscoveredDriveFile
import com.example.data.sync.GoogleDriveSyncService
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
    onOpenReader: (bookId: String, pageNumber: Int?, preferredMode: String?) -> Unit,
    onUpdateBookMetadata: (bookId: String, title: String, author: String, subject: String, description: String) -> Unit,
    onReindexBook: (String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current

    var selectedBookForProfile by remember { mutableStateOf<BookEntity?>(null) }
    var showDriveBooksDialog by remember { mutableStateOf(false) }

    // Keep profile sheet synced with live DB updates
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
        // Minimal Top Bar with Back + Title on left and all Import actions in top-right corner
        Surface(
            color = palette.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
                                .size(48.dp)
                                .testTag("books_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.textPrimary
                            )
                        }
                        Text(
                            text = "Books",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // All Import Buttons in Top-Right Corner
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TopImportIconAction(
                            icon = Icons.Default.UploadFile,
                            label = "File",
                            contentDescription = "Import Book File",
                            tint = IndigoPrimary,
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            },
                            modifier = Modifier.testTag("books_import_file_button")
                        )
                        TopImportIconAction(
                            icon = Icons.Default.CreateNewFolder,
                            label = "Folder",
                            contentDescription = "Import Folder",
                            tint = IndigoPrimary,
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.testTag("books_import_folder_button")
                        )
                        TopImportIconAction(
                            icon = Icons.Default.CloudDownload,
                            label = "Drive",
                            contentDescription = "Import from Drive",
                            tint = IndigoPrimary,
                            onClick = {
                                showDriveBooksDialog = true
                                if (driveBooks.isEmpty()) {
                                    onRefreshDriveBooks(GoogleDriveSyncService.DEFAULT_BOOKS_DRIVE_FOLDER_ID)
                                }
                            },
                            modifier = Modifier.testTag("books_drive_catalog_button")
                        )
                    }
                }
                HorizontalDivider(color = palette.border.copy(alpha = 0.55f), thickness = 0.75.dp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("book_library_list"),
            contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Minimal Offline Book Library section header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Book Library",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    if (books.isNotEmpty()) {
                        val bookLabel = if (books.size == 1) "1 book" else "${books.size} books"
                        Text(
                            text = "$bookLabel • ${books.sumOf { it.totalPages }} pages",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            color = palette.textMuted
                        )
                    }
                }
            }

            // Indexing / Import Progress Indicator
            if (isImportingBooks) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IndigoLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = IndigoPrimary
                            )
                            Text(
                                text = bookImportStatusText ?: "Indexing book offline...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = IndigoPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Book List or Minimal Empty State
            if (books.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(IndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "No Offline Books Yet",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Use File, Folder, or Drive in the top-right corner to add books.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = palette.textMuted
                        )
                    }
                }
            } else {
                items(books, key = { it.id }) { book ->
                    BookCardItem(
                        book = book,
                        onCardClick = { selectedBookForProfile = book },
                        onQuickRead = { onOpenReader(book.id, book.lastReadPage, "ebook") }
                    )
                }
            }
        }
    }

    // Book Profile Sheet
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
            onOpenReader = { page, mode ->
                selectedBookForProfile = null
                onOpenReader(liveProfileBook.id, page, mode)
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

    // Google Drive Books Catalog Modal
    if (showDriveBooksDialog) {
        DriveBooksCatalogDialog(
            books = books,
            driveBooks = driveBooks,
            isRefreshing = isRefreshingDriveBooks,
            downloadingIds = downloadingBookFileIds,
            onRefresh = { url -> onRefreshDriveBooks(url) },
            onDownloadSingle = { onDownloadDriveBook(it) },
            onDownloadAll = { onDownloadAllDriveBooks() },
            onDismiss = { showDriveBooksDialog = false }
        )
    }
}

@Composable
private fun TopImportIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            fontFamily = PoppinsFontFamily,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun BookCardItem(
    book: BookEntity,
    onCardClick: () -> Unit,
    onQuickRead: () -> Unit
) {
    val palette = LocalAppPalette.current
    val isPdf = book.format.equals("PDF", ignoreCase = true)
    val badgeBg = if (isPdf) RoseLight else IndigoLight
    val badgeColor = if (isPdf) RoseError else IndigoPrimary
    val progressFraction = if (book.totalPages > 0) {
        (book.lastReadPage.toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPct = (progressFraction * 100).toInt()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.75f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCardClick() }
            .testTag("book_card_${book.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.format.uppercase(),
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = book.title,
                        fontFamily = selectFontForText(book.title),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaText = buildList {
                        if (book.author.isNotBlank()) add(book.author)
                        if (book.totalPages > 0) add("p.${book.lastReadPage}/${book.totalPages} ($progressPct%)")
                    }.joinToString(" • ").ifBlank { book.fileName }
                    Text(
                        text = if (book.indexStatus == "indexing") "Indexing text..." else metaText,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = if (book.indexStatus == "error") RoseError else palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onQuickRead,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("book_read_btn_${book.id}")
                ) {
                    Text(
                        text = "Read",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = IndigoPrimary
                    )
                }
            }

            if (progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = IndigoPrimary.copy(alpha = 0.85f),
                    trackColor = palette.border.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookProfileSheet(
    book: BookEntity,
    toc: List<BookTocEntity>,
    bookmarkCount: Int,
    highlightCount: Int,
    noteCount: Int,
    onDismiss: () -> Unit,
    onOpenReader: (page: Int, mode: String) -> Unit,
    onUpdateMetadata: (title: String, author: String, subject: String, description: String) -> Unit,
    onReindex: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalAppPalette.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTocList by remember { mutableStateOf(toc.isNotEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (book.format == "PDF") RoseLight else IndigoLight
                        ) {
                            Text(
                                text = book.format,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (book.format == "PDF") RoseError else IndigoPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldLight
                        ) {
                            Text(
                                text = book.sourceType.replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        val linkIds = remember(book.fileName, book.title) {
                            com.example.data.search.BookSearchEngine.extractBookLinkIds(book)
                        }
                        linkIds.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IndigoLight
                            ) {
                                Text(
                                    text = "id-$tag",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = book.title,
                        fontFamily = selectFontForText(book.title),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    if (book.author.isNotBlank() || book.subject.isNotBlank()) {
                        Text(
                            text = listOf(book.author, book.subject).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 13.sp,
                            color = palette.textMuted
                        )
                    }
                    if (book.description.isNotBlank()) {
                        Text(
                            text = book.description,
                            fontSize = 12.5.sp,
                            color = palette.textMuted
                        )
                    }
                }

                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Book Profile", tint = IndigoPrimary)
                }
            }

            // Metadata Grid
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.background,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileMetric("Pages", "${book.totalPages}")
                    ProfileMetric("Size", formatFileSize(book.fileSizeBytes))
                    ProfileMetric("Bookmarks", "$bookmarkCount")
                    ProfileMetric("Highlights", "$highlightCount")
                    ProfileMetric("Notes", "$noteCount")
                }
            }

            // Ebook Reader Launch Button
            Button(
                onClick = { onOpenReader(book.lastReadPage, "ebook") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("profile_open_ebook_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Book", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            // TOC Preview
            if (toc.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTocList = !showTocList },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Table of Contents (${toc.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Icon(
                                imageVector = if (showTocList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle TOC",
                                tint = palette.textMuted
                            )
                        }
                        AnimatedVisibility(visible = showTocList) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                toc.take(15).forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpenReader(entry.pageNumber, "ebook") }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontFamily = selectFontForText(entry.title),
                                            fontSize = 12.5.sp,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = ((entry.level - 1) * 12).dp)
                                        )
                                        Text(
                                            text = "p.${entry.pageNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IndigoPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Maintenance actions (Re-index & Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReindex,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-index", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                    border = BorderStroke(1.dp, RoseBorder),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }

    if (showEditDialog) {
        var editTitle by remember { mutableStateOf(book.title) }
        var editAuthor by remember { mutableStateOf(book.author) }
        var editSubject by remember { mutableStateOf(book.subject) }
        var editDesc by remember { mutableStateOf(book.description) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Book Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Book Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = { editAuthor = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
                        label = { Text("Subject / Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description / Notes") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateMetadata(editTitle, editAuthor, editSubject, editDesc)
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Offline Book?") },
            text = { Text("Delete '${book.title}' and its offline file, bookmarks, highlights, and notes from this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    val palette = LocalAppPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
        Text(text = label, fontSize = 10.sp, color = palette.textMuted)
    }
}

@Composable
private fun DriveBooksCatalogDialog(
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
    var folderUrlInput by remember { mutableStateOf(GoogleDriveSyncService.DEFAULT_BOOKS_DRIVE_FOLDER_URL) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Book Library",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Download PDF & DOCX books for offline study",
                            fontSize = 11.5.sp,
                            color = palette.textMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = folderUrlInput,
                        onValueChange = { folderUrlInput = it },
                        label = { Text("Drive Folder URL", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRefresh(folderUrlInput) },
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndigoLight)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan Folder", tint = IndigoPrimary)
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textMuted
                        )
                        TextButton(onClick = onDownloadAll) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (isRefreshing && driveBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = IndigoPrimary)
                            Text("Scanning Google Drive folder...", fontSize = 12.sp, color = palette.textMuted)
                        }
                    }
                } else if (driveBooks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No PDF/DOCX files discovered yet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "Verify folder sharing is set to 'Anyone with the link' and tap Refresh, or use File / Folder import from your device.",
                                fontSize = 11.5.sp,
                                color = palette.textMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(driveBooks, key = { it.fileId }) { item ->
                            val isDownloading = item.fileId in downloadingIds
                            val isSavedOffline = books.any { it.sourceRef == "drive:${item.fileId}" }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.background,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontFamily = selectFontForText(item.title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isSavedOffline) "Saved offline" else "Tap to download offline",
                                            fontSize = 11.sp,
                                            color = if (isSavedOffline) EmeraldSuccess else palette.textMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onDownloadSingle(item) },
                                        enabled = !isDownloading,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = if (isSavedOffline) Icons.Default.DownloadDone else Icons.Default.CloudDownload,
                                                contentDescription = "Download ${item.title}",
                                                tint = if (isSavedOffline) EmeraldSuccess else IndigoPrimary
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
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
