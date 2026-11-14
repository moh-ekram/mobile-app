package com.example.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BookPageEntity

data class FtsCandidateRow(
    val bookId: String,
    val bookTitle: String,
    val pageNumber: Int,
    val textContent: String,
    val rawRank: Double
)

/**
 * Manages the SQLite FTS5 virtual table (`book_pages_fts`) with graceful FTS4 fallback
 * for older Android SQLite builds that omit the FTS5 compile flag.
 */
object BookFtsHelper {

    private const val TAG = "BookFtsHelper"

    enum class FtsMode { FTS5, FTS4, NONE }

    @Volatile
    var detectedMode: FtsMode = FtsMode.NONE
        private set

    fun ensureFtsTable(db: SupportSQLiteDatabase) {
        if (detectedMode != FtsMode.NONE) {
            try {
                // Verify table still exists
                db.query("SELECT count(*) FROM sqlite_master WHERE name='book_pages_fts'").use { cursor ->
                    if (cursor.moveToFirst() && cursor.getInt(0) > 0) return
                }
            } catch (_: Exception) {}
        }

        // 1. Try FTS5 with unicode61 tokenizer (ideal for both English and Bengali UTF-8)
        try {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS book_pages_fts USING fts5(
                    bookId UNINDEXED,
                    bookTitle UNINDEXED,
                    pageNumber UNINDEXED,
                    textContent,
                    tokenize='unicode61'
                )
                """.trimIndent()
            )
            detectedMode = FtsMode.FTS5
            return
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 unavailable on this SQLite build, falling back to FTS4: ${e.message}")
        }

        // 2. Fallback to FTS4 (supported on all Android API 24+ devices)
        try {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS book_pages_fts USING fts4(
                    bookId,
                    bookTitle,
                    pageNumber,
                    textContent
                )
                """.trimIndent()
            )
            detectedMode = FtsMode.FTS4
        } catch (e: Exception) {
            Log.e(TAG, "FTS4 also unavailable: ${e.message}")
            detectedMode = FtsMode.NONE
        }
    }

    fun replaceBookIndex(
        db: SupportSQLiteDatabase,
        bookId: String,
        bookTitle: String,
        pages: List<BookPageEntity>
    ) {
        ensureFtsTable(db)
        if (detectedMode == FtsMode.NONE) return
        try {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM book_pages_fts WHERE bookId = ?", arrayOf(bookId))
                for (page in pages) {
                    if (page.textContent.isNotBlank()) {
                        db.execSQL(
                            "INSERT INTO book_pages_fts(bookId, bookTitle, pageNumber, textContent) VALUES (?, ?, ?, ?)",
                            arrayOf(bookId, bookTitle, page.pageNumber, page.textContent)
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating FTS index for $bookId", e)
        }
    }

    fun removeBookFromIndex(db: SupportSQLiteDatabase, bookId: String) {
        ensureFtsTable(db)
        if (detectedMode == FtsMode.NONE) return
        try {
            db.execSQL("DELETE FROM book_pages_fts WHERE bookId = ?", arrayOf(bookId))
        } catch (e: Exception) {
            Log.w(TAG, "Failed removing book from FTS index: ${e.message}")
        }
    }

    fun updateBookTitleInIndex(db: SupportSQLiteDatabase, bookId: String, newTitle: String) {
        ensureFtsTable(db)
        if (detectedMode == FtsMode.NONE) return
        try {
            db.execSQL("UPDATE book_pages_fts SET bookTitle = ? WHERE bookId = ?", arrayOf(newTitle, bookId))
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating title in FTS index: ${e.message}")
        }
    }

    fun queryFtsCandidates(
        db: SupportSQLiteDatabase,
        matchQuery: String,
        bookIdFilter: String? = null,
        limit: Int = 60
    ): List<FtsCandidateRow> {
        ensureFtsTable(db)
        if (detectedMode == FtsMode.NONE || matchQuery.isBlank()) return emptyList()

        val results = mutableListOf<FtsCandidateRow>()
        val sql = when (detectedMode) {
            FtsMode.FTS5 -> if (bookIdFilter != null) {
                """
                SELECT bookId, bookTitle, pageNumber, textContent, bm25(book_pages_fts) AS rank
                FROM book_pages_fts
                WHERE book_pages_fts MATCH ? AND bookId = ?
                ORDER BY rank ASC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT bookId, bookTitle, pageNumber, textContent, bm25(book_pages_fts) AS rank
                FROM book_pages_fts
                WHERE book_pages_fts MATCH ?
                ORDER BY rank ASC
                LIMIT ?
                """.trimIndent()
            }
            FtsMode.FTS4 -> if (bookIdFilter != null) {
                """
                SELECT bookId, bookTitle, pageNumber, textContent, 0.0 AS rank
                FROM book_pages_fts
                WHERE textContent MATCH ? AND bookId = ?
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT bookId, bookTitle, pageNumber, textContent, 0.0 AS rank
                FROM book_pages_fts
                WHERE textContent MATCH ?
                LIMIT ?
                """.trimIndent()
            }
            FtsMode.NONE -> return emptyList()
        }

        val args: Array<Any> = if (bookIdFilter != null) {
            arrayOf(matchQuery, bookIdFilter, limit)
        } else {
            arrayOf(matchQuery, limit)
        }

        try {
            db.query(sql, args).use { cursor ->
                val idxBookId = cursor.getColumnIndex("bookId")
                val idxBookTitle = cursor.getColumnIndex("bookTitle")
                val idxPage = cursor.getColumnIndex("pageNumber")
                val idxText = cursor.getColumnIndex("textContent")
                val idxRank = cursor.getColumnIndex("rank")
                while (cursor.moveToNext()) {
                    val bId = if (idxBookId >= 0) cursor.getString(idxBookId) ?: "" else ""
                    val bTitle = if (idxBookTitle >= 0) cursor.getString(idxBookTitle) ?: "" else ""
                    val pNum = if (idxPage >= 0) cursor.getInt(idxPage) else 1
                    val content = if (idxText >= 0) cursor.getString(idxText) ?: "" else ""
                    val rawRank = if (idxRank >= 0) cursor.getDouble(idxRank) else 0.0
                    results.add(FtsCandidateRow(bId, bTitle, pNum, content, rawRank))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FTS query failed ($matchQuery): ${e.message}")
        }
        return results
    }
}
