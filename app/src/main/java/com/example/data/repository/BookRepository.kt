package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.AppDatabase
import com.example.data.local.BookFtsHelper
import com.example.data.model.*
import com.example.data.parser.BookParserService
import com.example.data.search.BookSearchEngine
import com.example.data.sync.DiscoveredDriveFile
import com.example.data.sync.GoogleDriveSyncService
import com.example.ui.screens.QuizQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val bookDao = database.bookDao()
    private val readerPrefs = context.getSharedPreferences("memorizer_ebook_reader_prefs", Context.MODE_PRIVATE)

    val booksDirectory: File by lazy {
        File(context.filesDir, "books").apply {
            if (!exists()) mkdirs()
        }
    }

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allBookmarks: Flow<List<BookBookmarkEntity>> = bookDao.getAllBookmarks()
    val allHighlights: Flow<List<BookHighlightEntity>> = bookDao.getAllHighlights()
    val allNotes: Flow<List<BookNoteEntity>> = bookDao.getAllNotes()

    fun observeBook(bookId: String): Flow<BookEntity?> = bookDao.observeBookById(bookId)
    fun getPagesForBook(bookId: String): Flow<List<BookPageEntity>> = bookDao.getPagesForBook(bookId)
    fun getTocForBook(bookId: String): Flow<List<BookTocEntity>> = bookDao.getTocForBook(bookId)
    fun getBookmarksForBook(bookId: String): Flow<List<BookBookmarkEntity>> = bookDao.getBookmarksForBook(bookId)
    fun getHighlightsForBook(bookId: String): Flow<List<BookHighlightEntity>> = bookDao.getHighlightsForBook(bookId)
    fun getNotesForBook(bookId: String): Flow<List<BookNoteEntity>> = bookDao.getNotesForBook(bookId)

    fun loadEbookPreferences(): EbookReaderPreferences {
        return EbookReaderPreferences(
            fontSizeSp = readerPrefs.getFloat("font_size_sp", 17f),
            fontFamilyKey = readerPrefs.getString("font_family", "auto") ?: "auto",
            lineSpacingMultiplier = readerPrefs.getFloat("line_spacing", 1.55f),
            horizontalMarginDp = readerPrefs.getFloat("margin_dp", 20f),
            themeKey = readerPrefs.getString("theme_key", "paper") ?: "paper"
        )
    }

    fun saveEbookPreferences(prefs: EbookReaderPreferences) {
        readerPrefs.edit()
            .putFloat("font_size_sp", prefs.fontSizeSp)
            .putString("font_family", prefs.fontFamilyKey)
            .putFloat("line_spacing", prefs.lineSpacingMultiplier)
            .putFloat("margin_dp", prefs.horizontalMarginDp)
            .putString("theme_key", prefs.themeKey)
            .apply()
    }

    suspend fun importBookFromUri(
        uri: Uri,
        sourceType: String = "device"
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveFileNameFromUri(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not open selected file."))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Selected file is empty."))
            }

            importBookFromBytes(
                fileName = fileName,
                bytes = bytes,
                sourceType = sourceType,
                sourceRef = uri.toString()
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBooksFromFolderUri(
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentName: String) -> Unit = { _, _, _ -> }
    ): Result<List<BookEntity>> = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to access selected folder."))

            val supportedFiles = mutableListOf<DocumentFile>()
            fun collectSupported(dir: DocumentFile, depth: Int) {
                if (depth > 3) return
                for (child in dir.listFiles()) {
                    if (child.isDirectory) {
                        collectSupported(child, depth + 1)
                    } else if (child.isFile) {
                        val name = child.name?.lowercase() ?: ""
                        val mime = child.type?.lowercase() ?: ""
                        if (name.endsWith(".pdf") || name.endsWith(".docx") ||
                            mime == "application/pdf" ||
                            mime.contains("wordprocessingml.document")
                        ) {
                            supportedFiles.add(child)
                        }
                    }
                }
            }
            collectSupported(rootDoc, 0)

            if (supportedFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No PDF or DOCX files found in the selected folder."))
            }

            val imported = mutableListOf<BookEntity>()
            supportedFiles.forEachIndexed { idx, doc ->
                val name = doc.name ?: "Book_${idx + 1}.pdf"
                onProgress(idx + 1, supportedFiles.size, name)
                val res = importBookFromUri(doc.uri, sourceType = "folder")
                res.getOrNull()?.let { imported.add(it) }
            }

            if (imported.isEmpty()) {
                Result.failure(IllegalStateException("Failed to import PDF/DOCX books from the selected folder."))
            } else {
                Result.success(imported)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndImportDriveBook(item: DiscoveredDriveFile): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            val existing = bookDao.getBookBySourceRef("drive:${item.fileId}")
            val downloaded = GoogleDriveSyncService.downloadSingleBookDriveFile(item)
            importBookFromBytes(
                fileName = downloaded.fileName,
                bytes = downloaded.bytes,
                sourceType = "drive",
                sourceRef = "drive:${item.fileId}",
                existingBookId = existing?.id
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBookFromBytes(
        fileName: String,
        bytes: ByteArray,
        sourceType: String = "device",
        sourceRef: String = "",
        existingBookId: String? = null
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            val format = BookParserService.detectFormat(fileName, bytes)
            val lowerName = fileName.lowercase()
            if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx") && format !in listOf("PDF", "DOCX")) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported format. Only PDF and DOCX books are supported."))
            }

            val bookId = existingBookId ?: ("book_" + UUID.randomUUID().toString().replace("-", "").take(12))
            val cleanBaseTitle = fileName.substringBeforeLast(".").replace("_", " ").trim().ifBlank { "Untitled Book" }
            val ext = format.lowercase()
            val targetFile = File(booksDirectory, "$bookId.$ext")
            targetFile.writeBytes(bytes)

            val now = System.currentTimeMillis()
            val placeholder = BookEntity(
                id = bookId,
                title = cleanBaseTitle,
                format = format,
                fileName = fileName,
                localFilePath = targetFile.absolutePath,
                sourceType = sourceType,
                sourceRef = sourceRef,
                fileSizeBytes = targetFile.length(),
                totalPages = 0,
                lastReadPage = 1,
                lastReaderMode = "ebook",
                indexStatus = "indexing",
                importedAt = now,
                lastOpenedAt = now
            )
            bookDao.insertBook(placeholder)

            return@withContext parseAndIndexInternal(placeholder, cleanBaseTitle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reindexBook(bookId: String): Result<BookEntity> = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId)
            ?: return@withContext Result.failure(IllegalStateException("Book not found."))
        bookDao.updateIndexStatus(bookId, "indexing", null, book.totalPages)
        parseAndIndexInternal(book, book.title)
    }

    private suspend fun parseAndIndexInternal(book: BookEntity, fallbackTitle: String): Result<BookEntity> {
        val file = File(book.localFilePath)
        if (!file.exists() || file.length() == 0L) {
            bookDao.updateIndexStatus(book.id, "error", "Offline file is missing.", 0)
            return Result.failure(IllegalStateException("Offline book file is missing."))
        }

        return try {
            val parsed = BookParserService.parseBookFile(
                context = context,
                bookId = book.id,
                localFile = file,
                fallbackTitle = fallbackTitle,
                formatHint = book.format
            )
            val finalTitle = if (parsed.title.isNotBlank() && parsed.title.length >= 2) {
                parsed.title
            } else {
                fallbackTitle
            }

            val updatedBook = book.copy(
                title = finalTitle,
                author = parsed.author.ifBlank { book.author },
                subject = parsed.subject.ifBlank { book.subject },
                format = parsed.format,
                totalPages = parsed.totalPages,
                lastReadPage = book.lastReadPage.coerceIn(1, parsed.totalPages.coerceAtLeast(1)),
                indexStatus = "ready",
                indexError = null
            )

            bookDao.insertBook(updatedBook)
            bookDao.deletePagesForBook(updatedBook.id)
            bookDao.insertPages(parsed.pages)
            bookDao.deleteTocForBook(updatedBook.id)
            if (parsed.tocEntries.isNotEmpty()) {
                bookDao.insertTocEntries(parsed.tocEntries)
            }

            BookFtsHelper.replaceBookIndex(
                db = database.openHelper.writableDatabase,
                bookId = updatedBook.id,
                bookTitle = updatedBook.title,
                pages = parsed.pages
            )

            Result.success(updatedBook)
        } catch (e: Exception) {
            bookDao.updateIndexStatus(book.id, "error", e.message ?: "Failed to parse book.", book.totalPages)
            Result.failure(e)
        }
    }

    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        subject: String,
        description: String
    ) = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Untitled Book" }
        bookDao.updateBookMetadata(bookId, cleanTitle, author.trim(), subject.trim(), description.trim())
        BookFtsHelper.updateBookTitleInIndex(database.openHelper.writableDatabase, bookId, cleanTitle)
    }

    suspend fun updateReadingProgress(bookId: String, page: Int, mode: String) = withContext(Dispatchers.IO) {
        bookDao.updateReadingProgress(bookId, page.coerceAtLeast(1), mode, System.currentTimeMillis())
    }

    suspend fun toggleBookmark(bookId: String, pageNumber: Int, title: String, snippet: String): Boolean =
        withContext(Dispatchers.IO) {
            val existing = bookDao.getBookmarkForPage(bookId, pageNumber)
            if (existing != null) {
                bookDao.deleteBookmarkForPage(bookId, pageNumber)
                false
            } else {
                bookDao.insertBookmark(
                    BookBookmarkEntity(
                        id = "${bookId}_bm_${pageNumber}_${System.currentTimeMillis()}",
                        bookId = bookId,
                        pageNumber = pageNumber,
                        title = title.ifBlank { "Page $pageNumber" },
                        snippet = snippet.take(140)
                    )
                )
                true
            }
        }

    suspend fun deleteBookmarkById(id: String) = withContext(Dispatchers.IO) {
        bookDao.deleteBookmarkById(id)
    }

    suspend fun addHighlight(
        bookId: String,
        pageNumber: Int,
        selectedText: String,
        colorHex: String = "#FDE047",
        startOffset: Int = 0,
        endOffset: Int = 0
    ): BookHighlightEntity = withContext(Dispatchers.IO) {
        val entity = BookHighlightEntity(
            id = "${bookId}_hl_${UUID.randomUUID().toString().take(8)}",
            bookId = bookId,
            pageNumber = pageNumber,
            selectedText = selectedText.trim(),
            colorHex = colorHex,
            startOffset = startOffset,
            endOffset = endOffset
        )
        bookDao.insertHighlight(entity)
        entity
    }

    suspend fun deleteHighlightById(id: String) = withContext(Dispatchers.IO) {
        bookDao.deleteHighlightById(id)
    }

    suspend fun saveNote(
        bookId: String,
        pageNumber: Int,
        noteText: String,
        selectedText: String = "",
        highlightId: String? = null,
        existingId: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = BookNoteEntity(
            id = existingId ?: "${bookId}_note_${UUID.randomUUID().toString().take(8)}",
            bookId = bookId,
            pageNumber = pageNumber,
            highlightId = highlightId,
            selectedText = selectedText.trim(),
            noteText = noteText.trim(),
            createdAt = now,
            updatedAt = now
        )
        bookDao.insertNote(note)
    }

    suspend fun deleteNoteById(id: String) = withContext(Dispatchers.IO) {
        bookDao.deleteNoteById(id)
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        BookFtsHelper.removeBookFromIndex(database.openHelper.writableDatabase, book.id)
        bookDao.deleteBookById(book.id)
        try {
            val file = File(book.localFilePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    suspend fun searchForMcqPassages(questionItem: QuizQuestionItem, maxResults: Int = 3): List<BookPassageMatch> {
        return BookSearchEngine.searchForMcqExplanation(database, questionItem, maxResults)
    }

    suspend fun searchInsideBook(bookId: String, query: String): List<BookPassageMatch> {
        return BookSearchEngine.searchInBook(database, bookId, query)
    }

    private fun resolveFileNameFromUri(uri: Uri): String {
        var name = ""
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) {
                    name = cursor.getString(idx) ?: ""
                }
            }
        } catch (_: Exception) {}
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast("/") ?: "ImportedBook.pdf"
        }
        return name
    }
}
