package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, importedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, importedAt DESC")
    suspend fun getAllBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookById(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceRef = :sourceRef AND sourceRef != '' LIMIT 1")
    suspend fun getBookBySourceRef(sourceRef: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET title = :title, author = :author, subject = :subject, description = :description WHERE id = :bookId")
    suspend fun updateBookMetadata(bookId: String, title: String, author: String, subject: String, description: String)

    @Query("UPDATE books SET lastReadPage = :page, lastReaderMode = :mode, lastOpenedAt = :timestamp WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: String, page: Int, mode: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET indexStatus = :status, indexError = :error, totalPages = :totalPages WHERE id = :bookId")
    suspend fun updateIndexStatus(bookId: String, status: String, error: String?, totalPages: Int)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    // Pages
    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageNumber ASC")
    fun getPagesForBook(bookId: String): Flow<List<BookPageEntity>>

    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageNumber ASC")
    suspend fun getPagesListForBook(bookId: String): List<BookPageEntity>

    @Query("SELECT * FROM book_pages WHERE bookId = :bookId AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getPage(bookId: String, pageNumber: Int): BookPageEntity?

    @Query("SELECT * FROM book_pages")
    suspend fun getAllPagesList(): List<BookPageEntity>

    @Query("SELECT COUNT(*) FROM book_pages")
    suspend fun getTotalPageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<BookPageEntity>)

    @Query("DELETE FROM book_pages WHERE bookId = :bookId")
    suspend fun deletePagesForBook(bookId: String)

    // TOC
    @Query("SELECT * FROM book_toc_entries WHERE bookId = :bookId ORDER BY sortOrder ASC, pageNumber ASC")
    fun getTocForBook(bookId: String): Flow<List<BookTocEntity>>

    @Query("SELECT * FROM book_toc_entries WHERE bookId = :bookId ORDER BY sortOrder ASC, pageNumber ASC")
    suspend fun getTocListForBook(bookId: String): List<BookTocEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTocEntries(entries: List<BookTocEntity>)

    @Query("DELETE FROM book_toc_entries WHERE bookId = :bookId")
    suspend fun deleteTocForBook(bookId: String)

    // Bookmarks
    @Query("SELECT * FROM book_bookmarks WHERE bookId = :bookId ORDER BY pageNumber ASC, createdAt DESC")
    fun getBookmarksForBook(bookId: String): Flow<List<BookBookmarkEntity>>

    @Query("SELECT * FROM book_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookBookmarkEntity>>

    @Query("SELECT * FROM book_bookmarks WHERE bookId = :bookId AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getBookmarkForPage(bookId: String, pageNumber: Int): BookBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookBookmarkEntity)

    @Query("DELETE FROM book_bookmarks WHERE bookId = :bookId AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkForPage(bookId: String, pageNumber: Int)

    @Query("DELETE FROM book_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    // Highlights
    @Query("SELECT * FROM book_highlights WHERE bookId = :bookId ORDER BY pageNumber ASC, createdAt DESC")
    fun getHighlightsForBook(bookId: String): Flow<List<BookHighlightEntity>>

    @Query("SELECT * FROM book_highlights ORDER BY createdAt DESC")
    fun getAllHighlights(): Flow<List<BookHighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: BookHighlightEntity)

    @Query("DELETE FROM book_highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: String)

    // Notes
    @Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY pageNumber ASC, updatedAt DESC")
    fun getNotesForBook(bookId: String): Flow<List<BookNoteEntity>>

    @Query("SELECT * FROM book_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<BookNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: BookNoteEntity)

    @Query("DELETE FROM book_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)
}
