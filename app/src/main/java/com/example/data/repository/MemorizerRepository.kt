package com.example.data.repository

import android.content.Context
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.model.ArticleEntity
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.parser.FileParsers
import com.example.data.supabase.SupabaseSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import com.example.data.sync.GoogleDriveSyncService
import java.io.ByteArrayInputStream
import java.util.UUID

data class CourseSyncDetail(
    val courseId: String,
    val courseTitle: String,
    val updatedCount: Int,
    val newCount: Int,
    val totalWords: Int
)

data class DriveSyncSummary(
    val totalCourses: Int,
    val totalUpdatedWords: Int,
    val totalAddedWords: Int,
    val courseDetails: List<CourseSyncDetail>,
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalCourseFileInput(
    val fileName: String,
    val bytes: ByteArray
)

class MemorizerRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    val backupManager = BackupManager(context, database)
    val supabaseService = SupabaseSyncService(context)

    val allWords: Flow<List<VocabularyWordEntity>> = database.vocabularyDao().getAllWords()
    val distinctGroups: Flow<List<String>> = database.vocabularyDao().getDistinctGroups()
    val allCourses: Flow<List<CourseEntity>> = database.courseDao().getAllCourses()
    val allArticles: Flow<List<ArticleEntity>> = database.articleDao().getAllArticles()
    val allGames: Flow<List<GamePracticeEntity>> = database.gamePracticeDao().getAllItems()
    val allQuestions: Flow<List<QuestionBankEntity>> = database.questionBankDao().getAllQuestions()

    fun getWordsForCourse(courseId: String): Flow<List<VocabularyWordEntity>> =
        if (courseId == "all") database.vocabularyDao().getAllWords()
        else database.vocabularyDao().getWordsByCourse(courseId)

    fun getDistinctGroupsForCourse(courseId: String): Flow<List<String>> =
        if (courseId == "all") database.vocabularyDao().getDistinctGroups()
        else database.vocabularyDao().getDistinctGroupsByCourse(courseId)

    fun getProgress(userId: String): Flow<UserProgressEntity?> =
        database.userProgressDao().getProgress(userId)

    init {
        // As requested by user: NO sample data anywhere. Clean up any leftover sample courses from previous runs.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database.courseDao().deleteCourseById("course_default")
                database.vocabularyDao().deleteWordsByCourse("course_default")
                val existing = database.courseDao().getAllCoursesList()
                existing.filter { it.title.contains("Barron", ignoreCase = true) }.forEach {
                    database.courseDao().deleteCourseById(it.id)
                    database.vocabularyDao().deleteWordsByCourse(it.id)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        // No-op: user explicitly requested NO sample data anywhere.
    }

    suspend fun createCourse(title: String, description: String? = null, headersJson: String? = null): CourseEntity = withContext(Dispatchers.IO) {
        val id = "course_" + System.currentTimeMillis()
        val course = CourseEntity(
            id = id,
            title = title,
            description = description,
            columnHeadersJson = headersJson
        )
        database.courseDao().insertCourse(course)
        course
    }

    suspend fun updateCourseTitle(courseId: String, newTitle: String) = withContext(Dispatchers.IO) {
        database.courseDao().updateCourseTitle(courseId, newTitle)
    }

    suspend fun deleteCourse(courseId: String, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.courseDao().deleteCourseById(courseId)
        database.vocabularyDao().deleteWordsByCourse(courseId)
        refreshProgressAndSync(userId)
    }

    suspend fun saveArticle(
        title: String,
        content: String,
        author: String = "Unknown Author",
        courseId: String = "course_default",
        articleId: String? = null
    ): ArticleEntity = withContext(Dispatchers.IO) {
        val id = articleId ?: ("art_" + System.currentTimeMillis())
        val count = content.split("\\s+".toRegex()).count { it.isNotBlank() }
        val article = ArticleEntity(
            id = id,
            title = title.ifBlank { "Untitled Article" },
            content = content,
            author = author.ifBlank { "Unknown Author" },
            courseId = courseId,
            wordCount = count
        )
        database.articleDao().insertArticle(article)
        article
    }

    suspend fun deleteArticle(articleId: String) = withContext(Dispatchers.IO) {
        database.articleDao().deleteArticleById(articleId)
    }

    suspend fun updateWordStatus(id: String, status: String, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().updateWordStatus(id, status)
        refreshProgressAndSync(userId)
    }

    suspend fun addWord(word: VocabularyWordEntity, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().insertWord(word)
        refreshProgressAndSync(userId)
    }

    suspend fun updateWord(word: VocabularyWordEntity, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().updateWord(word)
        refreshProgressAndSync(userId)
    }

    suspend fun reportWord(id: String, isReported: Boolean = true, reason: String? = null, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().reportWord(id, isReported, reason)
        refreshProgressAndSync(userId)
    }

    suspend fun deleteWord(id: String, userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().deleteWordById(id)
        refreshProgressAndSync(userId)
    }

    suspend fun addGameItem(item: GamePracticeEntity) = withContext(Dispatchers.IO) {
        database.gamePracticeDao().insertItem(item)
    }

    suspend fun deleteGameItem(id: String) = withContext(Dispatchers.IO) {
        database.gamePracticeDao().deleteItemById(id)
    }

    suspend fun deleteGamesBySection(section: String) = withContext(Dispatchers.IO) {
        database.gamePracticeDao().deleteItemsBySection(section)
    }

    suspend fun deleteGameItems(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            database.gamePracticeDao().deleteItemsByIds(ids)
        }
    }

    suspend fun clearAllGames() = withContext(Dispatchers.IO) {
        database.gamePracticeDao().clearAll()
    }

    suspend fun recordGameAnswer(questionId: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val status = if (isCorrect) "correct" else "incorrect"
        database.gamePracticeDao().recordAttempt(questionId, status, isCorrect)
    }

    suspend fun recordWordQuizAnswer(wordId: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val status = if (isCorrect) "correct" else "incorrect"
        database.vocabularyDao().recordQuizAttempt(wordId, status, isCorrect)
    }

    suspend fun addQuestionBankItem(item: QuestionBankEntity) = withContext(Dispatchers.IO) {
        database.questionBankDao().insertQuestion(item)
    }

    suspend fun deleteQuestionBankItem(id: String) = withContext(Dispatchers.IO) {
        database.questionBankDao().deleteQuestionById(id)
    }

    suspend fun deleteQuestionBankItems(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            database.questionBankDao().deleteQuestionsByIds(ids)
        }
    }

    suspend fun clearAllQuestionBank() = withContext(Dispatchers.IO) {
        database.questionBankDao().clearAll()
    }

    suspend fun recordQuizResult(score: Int, total: Int, userId: String = "1235") = withContext(Dispatchers.IO) {
        val current = database.userProgressDao().getProgressOnce(userId) ?: UserProgressEntity(userId)
        val updated = current.copy(
            quizCompleted = current.quizCompleted + 1,
            quizTotalScore = current.quizTotalScore + score
        )
        database.userProgressDao().insertOrUpdate(updated)
        backupManager.saveBackupFiles(userId)
        supabaseService.syncProgressRecord(updated)
    }

    suspend fun refreshProgressAndSync(userId: String = "1235") = withContext(Dispatchers.IO) {
        val words = database.vocabularyDao().getAllWordsList()
        val current = database.userProgressDao().getProgressOnce(userId) ?: UserProgressEntity(userId)
        val updated = current.copy(
            totalWords = words.size,
            knowCount = words.count { it.status == "know" },
            confusionCount = words.count { it.status == "confusion" },
            dontKnowCount = words.count { it.status == "dont_know" },
            unratedCount = words.count { it.status == "unrated" },
            lastBackupTimestamp = System.currentTimeMillis()
        )
        database.userProgressDao().insertOrUpdate(updated)

        // Automatic device file backup
        backupManager.saveBackupFiles(userId)

        // Supabase cloud sync
        supabaseService.syncProgressRecord(updated)
    }

    suspend fun importCourseFile(content: String, isJson: Boolean, courseId: String = "course_default", userId: String = "1235"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val words = if (isJson) {
                FileParsers.parseJsonCourse(content, courseId)
            } else {
                FileParsers.parseCourseCsv(content, courseId)
            }
            if (words.isNotEmpty()) {
                // If not JSON, extract headers and save to course
                if (!isJson) {
                    val headers = FileParsers.extractHeaders(content)
                    if (headers.isNotEmpty()) {
                        val course = database.courseDao().getCourseById(courseId)
                        if (course != null) {
                            val headersJson = org.json.JSONArray(headers).toString()
                            database.courseDao().insertCourse(course.copy(columnHeadersJson = headersJson))
                        }
                    }
                }
                database.vocabularyDao().safeUpsertWordsPreservingProgress(words)
                refreshProgressAndSync(userId)
                Result.success(words.size)
            } else {
                Result.failure(Exception("No valid vocabulary rows found in course file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads and synchronizes all courses found inside a Google Drive folder link,
     * Google Sheet link, or Google Drive file link.
     * ID-based safe upsert ensures all user learning status (Know, Confusion, Don't Know),
     * review counts, and quiz statistics are preserved for existing words.
     */
    suspend fun syncCoursesFromDrive(
        inputUrl: String,
        preserveProgress: Boolean = true,
        userId: String = "1235"
    ): Result<DriveSyncSummary> = withContext(Dispatchers.IO) {
        try {
            val downloadedFiles = GoogleDriveSyncService.fetchFilesFromInput(inputUrl)
            if (downloadedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No readable course files found in the provided Google Drive link."))
            }
            processDownloadedOrBatchFiles(
                files = downloadedFiles.map { LocalCourseFileInput(it.fileName, it.bytes) },
                preserveProgress = preserveProgress,
                userId = userId
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronizes multiple course files selected directly from the user's device.
     * Each file becomes a distinct course, preserving existing IDs and user learning progress.
     */
    suspend fun importMultipleCourseFiles(
        files: List<LocalCourseFileInput>,
        preserveProgress: Boolean = true,
        userId: String = "1235"
    ): Result<DriveSyncSummary> = withContext(Dispatchers.IO) {
        try {
            if (files.isEmpty()) {
                return@withContext Result.failure(Exception("No files selected."))
            }
            processDownloadedOrBatchFiles(files, preserveProgress, userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processDownloadedOrBatchFiles(
        files: List<LocalCourseFileInput>,
        preserveProgress: Boolean,
        userId: String
    ): Result<DriveSyncSummary> {
        val existingCourses = database.courseDao().getAllCoursesList().toMutableList()
        val details = mutableListOf<CourseSyncDetail>()
        var overallUpdated = 0
        var overallAdded = 0

        for (file in files) {
            val cleanTitle = file.fileName
                .substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
                .ifBlank { "Imported Course" }

            // Find existing course by title or create new
            var course = existingCourses.find { it.title.equals(cleanTitle, ignoreCase = true) }
            if (course == null) {
                val newCourseId = "c_" + UUID.randomUUID().toString().take(8)
                course = CourseEntity(id = newCourseId, title = cleanTitle)
                database.courseDao().insertCourse(course)
                existingCourses.add(course)
            }
            val courseId = course.id

            val words = mutableListOf<VocabularyWordEntity>()
            val isExcel = file.fileName.endsWith(".xlsx", ignoreCase = true) ||
                (file.bytes.size > 4 && file.bytes[0] == 0x50.toByte() && file.bytes[1] == 0x4B.toByte())

            if (isExcel) {
                val sheets = FileParsers.parseMultiSheetXlsx(ByteArrayInputStream(file.bytes))
                for (sheet in sheets) {
                    val sheetWords = FileParsers.parseCourseRows(sheet.rows, courseId, defaultGroupName = sheet.sheetName)
                    words.addAll(sheetWords)
                }
            } else if (file.fileName.endsWith(".json", ignoreCase = true)) {
                val jsonStr = String(file.bytes, Charsets.UTF_8)
                val jsonWords = FileParsers.parseJsonCourse(jsonStr, courseId)
                words.addAll(jsonWords)
            } else {
                // CSV / TSV
                val csvStr = String(file.bytes, Charsets.UTF_8)
                val csvWords = FileParsers.parseCourseCsv(csvStr, courseId)
                words.addAll(csvWords)
            }

            if (words.isNotEmpty()) {
                val (updated, inserted) = if (preserveProgress) {
                    database.vocabularyDao().safeUpsertWordsPreservingProgress(words)
                } else {
                    database.vocabularyDao().insertWords(words)
                    Pair(0, words.size)
                }
                overallUpdated += updated
                overallAdded += inserted
                details.add(
                    CourseSyncDetail(
                        courseId = courseId,
                        courseTitle = cleanTitle,
                        updatedCount = updated,
                        newCount = inserted,
                        totalWords = words.size
                    )
                )
            }
        }

        if (details.isEmpty()) {
            return Result.failure(Exception("None of the files contained valid vocabulary rows."))
        }

        refreshProgressAndSync(userId)

        return Result.success(
            DriveSyncSummary(
                totalCourses = details.size,
                totalUpdatedWords = overallUpdated,
                totalAddedWords = overallAdded,
                courseDetails = details
            )
        )
    }

    suspend fun importGameItems(items: List<GamePracticeEntity>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (items.isNotEmpty()) {
                database.gamePracticeDao().insertItems(items)
                Result.success(items.size)
            } else {
                Result.failure(Exception("No valid game questions found to import"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGameFile(content: String, defaultSheetType: String = "practice"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = FileParsers.parseGameCsv(content, defaultSheetType)
            if (items.isNotEmpty()) {
                database.gamePracticeDao().insertItems(items)
                Result.success(items.size)
            } else {
                Result.failure(Exception("No valid game questions found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importQuestionBankFile(content: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = FileParsers.parseQuestionBankCsv(content)
            if (items.isNotEmpty()) {
                database.questionBankDao().insertQuestions(items)
                Result.success(items.size)
            } else {
                Result.failure(Exception("No valid Question Bank questions found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetAllData(userId: String = "1235") = withContext(Dispatchers.IO) {
        database.vocabularyDao().clearAll()
        database.gamePracticeDao().clearAll()
        database.questionBankDao().clearAll()
        database.articleDao().clearAll()
        database.courseDao().clearAll()
        val emptyProgress = UserProgressEntity(
            userId = userId,
            totalWords = 0,
            knowCount = 0,
            confusionCount = 0,
            dontKnowCount = 0,
            unratedCount = 0,
            streakDays = 1,
            quizCompleted = 0,
            quizTotalScore = 0
        )
        database.userProgressDao().insertOrUpdate(emptyProgress)
    }
}
