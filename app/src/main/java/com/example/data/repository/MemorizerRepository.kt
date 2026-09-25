package com.example.data.repository

import android.content.Context
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.model.ArticleEntity
import com.example.data.model.DeletedArticleTitleEntity
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.model.ArchivedWordProgressEntity
import com.example.data.parser.ArticleParser
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
    private val prefs = context.getSharedPreferences("memorizer_prefs", Context.MODE_PRIVATE)

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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Seed sample courses and articles if either is empty
                val existingCourses = database.courseDao().getAllCoursesList()
                val existingArticles = database.articleDao().getAllArticlesList()
                if (existingCourses.isEmpty() || existingArticles.isEmpty()) {
                    seedSampleData(force = false)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun seedSampleData(force: Boolean = false) = withContext(Dispatchers.IO) {
        val existingCourses = database.courseDao().getAllCoursesList()
        if (force || existingCourses.isEmpty()) {
            database.courseDao().insertCourses(SampleData.sampleCourses)
            database.vocabularyDao().insertWords(SampleData.sampleWords)
            val existingGames = database.gamePracticeDao().getAllItemsList()
            if (existingGames.isEmpty()) {
                database.gamePracticeDao().insertItems(SampleData.sampleGames)
            }
            val existingQ = database.questionBankDao().getAllQuestionsList()
            if (existingQ.isEmpty()) {
                database.questionBankDao().insertQuestions(SampleData.sampleQuestionBank)
            }
        }

        val existingArticles = database.articleDao().getAllArticlesList()
        if (force || existingArticles.isEmpty()) {
            // Remove any deletion locks for sample articles
            SampleData.sampleArticles.forEach { art ->
                database.deletedArticleDao().removeDeleted(art.title.trim().lowercase())
            }
            database.articleDao().insertArticles(SampleData.sampleArticles)
        }

        refreshProgressAndSync("1235")
    }

    suspend fun seedInitialData() = seedSampleData(force = false)

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

    suspend fun deleteCourse(courseId: String, keepProgress: Boolean = true, userId: String = "1235") = withContext(Dispatchers.IO) {
        if (keepProgress) {
            val words = database.vocabularyDao().getWordsListByCourse(courseId)
            if (words.isNotEmpty()) {
                val archived = words.map { w ->
                    ArchivedWordProgressEntity(
                        id = "${courseId}_${w.word.lowercase().trim()}",
                        originalId = w.id,
                        word = w.word,
                        normalizedWord = w.word.lowercase().trim(),
                        courseId = courseId,
                        status = w.status,
                        timesReviewed = w.timesReviewed,
                        lastReviewedAt = w.lastReviewedAt,
                        isReported = w.isReported,
                        reportReason = w.reportReason,
                        lastQuizStatus = w.lastQuizStatus,
                        quizCorrectCount = w.quizCorrectCount,
                        quizIncorrectCount = w.quizIncorrectCount
                    )
                }
                database.archivedWordProgressDao().insertAll(archived)
            }
        } else {
            database.archivedWordProgressDao().deleteByCourse(courseId)
        }
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

    suspend fun saveArticles(articles: List<ArticleEntity>) = withContext(Dispatchers.IO) {
        database.articleDao().insertArticles(articles)
    }

    fun getArticleSyncUrl(): String {
        return prefs.getString("article_sync_url", "") ?: ""
    }

    fun setArticleSyncUrl(url: String) {
        prefs.edit().putString("article_sync_url", url).apply()
    }

    suspend fun deleteArticle(articleId: String) = withContext(Dispatchers.IO) {
        val existing = database.articleDao().getArticleById(articleId)
        if (existing != null) {
            val norm = existing.title.trim().lowercase()
            database.deletedArticleDao().insertDeleted(
                DeletedArticleTitleEntity(normalizedTitle = norm, originalTitle = existing.title)
            )
        }
        database.articleDao().deleteArticleById(articleId)
    }

    data class ArticleSyncSummary(
        val updatedCount: Int,
        val addedCount: Int,
        val skippedDeletedCount: Int,
        val totalProcessed: Int
    )

    suspend fun syncArticlesFromSource(source: String): Result<ArticleSyncSummary> = withContext(Dispatchers.IO) {
        try {
            val trimmedSource = source.trim()
            val contentText = if (trimmedSource.startsWith("http://") || trimmedSource.startsWith("https://") || trimmedSource.contains("docs.google.com")) {
                setArticleSyncUrl(trimmedSource)
                val fetchRes = ArticleParser.fetchGoogleDocText(trimmedSource)
                fetchRes.getOrThrow()
            } else {
                trimmedSource
            }

            val parsedArticles = ArticleParser.parseArticles(contentText)
            if (parsedArticles.isEmpty()) {
                return@withContext Result.failure(Exception("No valid articles found in document."))
            }

            val deletedTitles = database.deletedArticleDao().getAllDeletedNormalizedTitles().toSet()
            val existingArticles = database.articleDao().getAllArticlesList()
            val existingMap = existingArticles.associateBy { it.title.trim().lowercase() }

            var updatedCount = 0
            var addedCount = 0
            var skippedDeletedCount = 0

            parsedArticles.forEachIndexed { idx, item ->
                val normTitle = item.title.trim().lowercase()
                if (deletedTitles.contains(normTitle)) {
                    // Deleted article - strictly ignore and never re-sync!
                    skippedDeletedCount++
                } else if (existingMap.containsKey(normTitle)) {
                    // Match found! Update content and author
                    val existing = existingMap[normTitle]!!
                    val count = item.content.split("\\s+".toRegex()).count { it.isNotBlank() }
                    val updated = existing.copy(
                        title = item.title,
                        content = item.content,
                        author = item.author,
                        wordCount = count
                    )
                    database.articleDao().insertArticle(updated)
                    updatedCount++
                } else {
                    // New article!
                    val count = item.content.split("\\s+".toRegex()).count { it.isNotBlank() }
                    val newEntity = ArticleEntity(
                        id = "art_${System.currentTimeMillis()}_$idx",
                        title = item.title,
                        content = item.content,
                        author = item.author,
                        courseId = "course_default",
                        wordCount = count
                    )
                    database.articleDao().insertArticle(newEntity)
                    addedCount++
                }
            }

            Result.success(
                ArticleSyncSummary(
                    updatedCount = updatedCount,
                    addedCount = addedCount,
                    skippedDeletedCount = skippedDeletedCount,
                    totalProcessed = parsedArticles.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    suspend fun recordWordQuizAnswer(wordId: String, isCorrect: Boolean, userId: String = "1235") = withContext(Dispatchers.IO) {
        val quizStatus = if (isCorrect) "correct" else "incorrect"
        database.vocabularyDao().recordQuizAttempt(wordId, quizStatus, isCorrect)

        // Correct option marks word as 'know', Incorrect option marks as 'dont_know'
        val ratingStatus = if (isCorrect) "know" else "dont_know"
        database.vocabularyDao().updateWordStatus(wordId, ratingStatus)

        // Sync and refresh stats for Flashcard and Homepage
        refreshProgressAndSync(userId)
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

    suspend fun importQuestionBankFile(content: String, clearExisting: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = FileParsers.parseQuestionBankAny(content)
            if (items.isNotEmpty()) {
                if (clearExisting) {
                    database.questionBankDao().getAllQuestionsList().forEach {
                        database.questionBankDao().deleteQuestion(it)
                    }
                }
                database.questionBankDao().insertQuestions(items)
                Result.success(items.size)
            } else {
                Result.failure(Exception("No valid Question Bank questions found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importQuestionBankFromBytes(bytes: ByteArray, fileName: String, clearExisting: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = FileParsers.parseQuestionBankFromBytes(bytes, fileName)
            if (items.isNotEmpty()) {
                if (clearExisting) {
                    database.questionBankDao().getAllQuestionsList().forEach {
                        database.questionBankDao().deleteQuestion(it)
                    }
                }
                database.questionBankDao().insertQuestions(items)
                Result.success(items.size)
            } else {
                Result.failure(Exception("No valid Question Bank questions found in $fileName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importQuestionBankFromUrl(
        inputUrl: String,
        clearExisting: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val downloadedFiles = GoogleDriveSyncService.fetchFilesFromInput(inputUrl)
            if (downloadedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No readable files found in the provided link."))
            }
            val allQuestions = mutableListOf<QuestionBankEntity>()
            for (file in downloadedFiles) {
                val questions = FileParsers.parseQuestionBankFromBytes(file.bytes, file.fileName)
                allQuestions.addAll(questions)
            }
            if (allQuestions.isNotEmpty()) {
                if (clearExisting) {
                    database.questionBankDao().getAllQuestionsList().forEach {
                        database.questionBankDao().deleteQuestion(it)
                    }
                }
                database.questionBankDao().insertQuestions(allQuestions)
                Result.success(allQuestions.size)
            } else {
                Result.failure(Exception("No valid Question Bank questions found in the link data."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val articleScraperService = com.example.data.service.ArticleFlashcardScraperService.getInstance()
    val articleSitemapService = com.example.data.service.ArticleSitemapService.getInstance()

    suspend fun scrapeArticleAndExtractBody(url: String): Result<String> {
        return articleScraperService.extractMainBodyTextFromUrl(url)
    }

    suspend fun scrapeAndGenerateFlashcards(
        urls: List<String>,
        saveToDb: Boolean = true
    ): Result<List<com.example.data.service.GeneratedFlashcard>> = withContext(Dispatchers.IO) {
        try {
            val words = database.vocabularyDao().getAllWordsList()
            val cards = articleScraperService.generateFlashcardsFromUrls(urls, words)
            if (saveToDb && cards.isNotEmpty()) {
                articleScraperService.saveToFlashcardDatabase(context, cards)
                articleScraperService.saveToVocabularyDatabase(context, cards)
            }
            Result.success(cards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWebsiteSitemap(urlOrDomain: String): Result<com.example.data.service.SitemapFetchResult> {
        return articleSitemapService.fetchSitemap(urlOrDomain)
    }

    suspend fun batchImportSitemapArticles(
        selectedArticles: List<com.example.data.service.SitemapArticleItem>,
        courseId: String = "course_default",
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): Result<List<ArticleEntity>> {
        return articleSitemapService.batchImportArticlesToReader(context, selectedArticles, courseId, onProgress)
    }

    suspend fun batchGenerateSitemapFlashcards(
        selectedArticles: List<com.example.data.service.SitemapArticleItem>,
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return articleSitemapService.batchGenerateFlashcardsFromArticles(context, selectedArticles, onProgress)
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
