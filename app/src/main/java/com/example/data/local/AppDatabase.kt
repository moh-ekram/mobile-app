package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*

@Database(
    entities = [
        VocabularyWordEntity::class,
        GamePracticeEntity::class,
        QuestionBankEntity::class,
        UserProgressEntity::class,
        CourseEntity::class,
        ArticleEntity::class,
        DeletedArticleTitleEntity::class,
        Flashcard::class,
        ArchivedWordProgressEntity::class,
        BookEntity::class,
        BookPageEntity::class,
        BookTocEntity::class,
        BookBookmarkEntity::class,
        BookHighlightEntity::class,
        BookNoteEntity::class,
        ArticleBookGroupEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun gamePracticeDao(): GamePracticeDao
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun courseDao(): CourseDao
    abstract fun articleDao(): ArticleDao
    abstract fun articleBookGroupDao(): ArticleBookGroupDao
    abstract fun deletedArticleDao(): DeletedArticleDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun archivedWordProgressDao(): ArchivedWordProgressDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val alterStatements = listOf(
                    "ALTER TABLE `saved_articles` ADD COLUMN `sourceUrl` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `saved_articles` ADD COLUMN `websiteName` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `saved_articles` ADD COLUMN `publishedDate` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `saved_articles` ADD COLUMN `faviconUrl` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `saved_articles` ADD COLUMN `coverImageUrl` TEXT NOT NULL DEFAULT ''"
                )
                for (stmt in alterStatements) {
                    try {
                        db.execSQL(stmt)
                    } catch (_: Exception) {
                    }
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `article_book_groups` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `articleIdsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `books` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceRef` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `totalPages` INTEGER NOT NULL,
                        `lastReadPage` INTEGER NOT NULL,
                        `lastReaderMode` TEXT NOT NULL,
                        `indexStatus` TEXT NOT NULL,
                        `indexError` TEXT,
                        `importedAt` INTEGER NOT NULL,
                        `lastOpenedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_pages` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `chapterTitle` TEXT NOT NULL,
                        `textContent` TEXT NOT NULL,
                        `wordCount` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_pages_bookId` ON `book_pages` (`bookId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_pages_bookId_pageNumber` ON `book_pages` (`bookId`, `pageNumber`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_toc_entries` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `level` INTEGER NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_toc_entries_bookId` ON `book_toc_entries` (`bookId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_bookmarks` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `snippet` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_bookmarks_bookId` ON `book_bookmarks` (`bookId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_highlights` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `selectedText` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `startOffset` INTEGER NOT NULL,
                        `endOffset` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_highlights_bookId` ON `book_highlights` (`bookId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_notes` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `highlightId` TEXT,
                        `selectedText` TEXT NOT NULL,
                        `noteText` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_notes_bookId` ON `book_notes` (`bookId`)")

                BookFtsHelper.ensureFtsTable(db)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memorizer_database"
                )
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            BookFtsHelper.ensureFtsTable(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON")
                            BookFtsHelper.ensureFtsTable(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
