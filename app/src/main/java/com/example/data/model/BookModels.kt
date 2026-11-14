package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String = "",
    val subject: String = "",
    val description: String = "",
    val format: String = "PDF", // "PDF" or "DOCX"
    val fileName: String = "",
    val localFilePath: String = "",
    val sourceType: String = "device", // "device", "folder", "drive"
    val sourceRef: String = "",
    val fileSizeBytes: Long = 0L,
    val totalPages: Int = 0,
    val lastReadPage: Int = 1,
    val lastReaderMode: String = "ebook", // "ebook" or "pdf"
    val indexStatus: String = "ready", // "indexing", "ready", "error"
    val indexError: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "pageNumber"], unique = true)
    ]
)
data class BookPageEntity(
    @PrimaryKey val id: String, // "${bookId}_p${pageNumber}"
    val bookId: String,
    val pageNumber: Int,
    val chapterTitle: String = "",
    val textContent: String,
    val wordCount: Int = 0
)

@Entity(
    tableName = "book_toc_entries",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookTocEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val level: Int = 1,
    val pageNumber: Int,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "book_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookBookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageNumber: Int,
    val title: String = "",
    val snippet: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookHighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageNumber: Int,
    val selectedText: String,
    val colorHex: String = "#FDE047",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookNoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageNumber: Int,
    val highlightId: String? = null,
    val selectedText: String = "",
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BookReaderTarget(
    val bookId: String,
    val pageNumber: Int = 1,
    val highlightQuery: String? = null,
    val highlightParagraph: String? = null,
    val preferredMode: String? = null
)

data class BookPassageMatch(
    val bookId: String,
    val bookTitle: String,
    val bookFormat: String,
    val pageNumber: Int,
    val chapterTitle: String,
    val passageSnippet: String,
    val matchedTerms: List<String>,
    val relevanceScore: Double,
    val bestLine: String = ""
)

data class EbookReaderPreferences(
    val fontSizeSp: Float = 17f,
    val fontFamilyKey: String = "auto", // "auto", "poppins", "kalpurush", "aikya", "serif", "sans", "mono"
    val lineSpacingMultiplier: Float = 1.55f,
    val horizontalMarginDp: Float = 20f,
    val themeKey: String = "paper" // "paper", "sepia", "mist", "dark"
)
