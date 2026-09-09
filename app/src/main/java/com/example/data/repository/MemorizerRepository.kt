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

class MemorizerRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    val backupManager = BackupManager(context, database)
    val supabaseService = SupabaseSyncService(context)

    val allWords: Flow<List<VocabularyWordEntity>> = database.vocabularyDao().getAllWords()
    val distinctGroups: Flow<List<Int>> = database.vocabularyDao().getDistinctGroups()
    val allCourses: Flow<List<CourseEntity>> = database.courseDao().getAllCourses()
    val allArticles: Flow<List<ArticleEntity>> = database.articleDao().getAllArticles()
    val allGames: Flow<List<GamePracticeEntity>> = database.gamePracticeDao().getAllItems()
    val allQuestions: Flow<List<QuestionBankEntity>> = database.questionBankDao().getAllQuestions()

    fun getWordsForCourse(courseId: String): Flow<List<VocabularyWordEntity>> =
        if (courseId == "all") database.vocabularyDao().getAllWords()
        else database.vocabularyDao().getWordsByCourse(courseId)

    fun getDistinctGroupsForCourse(courseId: String): Flow<List<Int>> =
        if (courseId == "all") database.vocabularyDao().getDistinctGroups()
        else database.vocabularyDao().getDistinctGroupsByCourse(courseId)

    fun getProgress(userId: String): Flow<UserProgressEntity?> =
        database.userProgressDao().getProgress(userId)

    init {
        // Default starts empty as requested by user. Data is imported or downloaded from Drive.
    }

    suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        val defaultCourse = CourseEntity(
            id = "course_default",
            title = "Barron's 333 High-Frequency GRE",
            description = "Essential GRE vocabulary words with mnemonics, meanings, and derivatives."
        )
        database.courseDao().insertCourse(defaultCourse)

        val wordsWithCourse = SampleData.sampleWords.map { it.copy(courseId = "course_default") }
        database.vocabularyDao().insertWords(wordsWithCourse)
        database.gamePracticeDao().insertItems(SampleData.sampleGames)
        database.questionBankDao().insertQuestions(SampleData.sampleQuestionBank)

        // Seed sample reading article
        val sampleArticle = ArticleEntity(
            id = "article_seed_1",
            title = "The Art of Eloquent Rhetoric",
            content = "In ancient debates, scholars strove to be eloquent rather than capricious. An esoteric paradox would often confound the novice, while pragmatic leaders sought lucid arguments to mitigate public fear. Through meticulous scrutiny, one could discern genuine wisdom from superficial rhetoric.",
            courseId = "course_default",
            wordCount = 42
        )
        database.articleDao().insertArticle(sampleArticle)

        // Seed initial progress for default user
        val initialProgress = UserProgressEntity(
            userId = "1235",
            totalWords = wordsWithCourse.size,
            knowCount = 0,
            confusionCount = 0,
            dontKnowCount = 0,
            unratedCount = wordsWithCourse.size,
            streakDays = 1,
            quizCompleted = 0,
            quizTotalScore = 0
        )
        database.userProgressDao().insertOrUpdate(initialProgress)

        // Create initial backup files on device
        backupManager.saveBackupFiles("1235")
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

    suspend fun addQuestionBankItem(item: QuestionBankEntity) = withContext(Dispatchers.IO) {
        database.questionBankDao().insertQuestion(item)
    }

    suspend fun deleteQuestionBankItem(id: String) = withContext(Dispatchers.IO) {
        database.questionBankDao().deleteQuestionById(id)
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
                database.vocabularyDao().insertWords(words)
                refreshProgressAndSync(userId)
                Result.success(words.size)
            } else {
                Result.failure(Exception("No valid vocabulary rows found in course file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        seedInitialData()
    }
}
