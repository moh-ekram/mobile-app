package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdAt ASC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY createdAt ASC")
    suspend fun getAllCoursesList(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: String)
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY createdAt DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM saved_articles WHERE courseId = :courseId ORDER BY createdAt DESC")
    fun getArticlesByCourse(courseId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM saved_articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun deleteArticleById(id: String)
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary_words ORDER BY `group` ASC, id ASC")
    fun getAllWords(): Flow<List<VocabularyWordEntity>>

    @Query("SELECT * FROM vocabulary_words ORDER BY `group` ASC, id ASC")
    suspend fun getAllWordsList(): List<VocabularyWordEntity>

    @Query("SELECT * FROM vocabulary_words WHERE courseId = :courseId ORDER BY `group` ASC, id ASC")
    fun getWordsByCourse(courseId: String): Flow<List<VocabularyWordEntity>>

    @Query("SELECT * FROM vocabulary_words WHERE courseId = :courseId ORDER BY `group` ASC, id ASC")
    suspend fun getWordsListByCourse(courseId: String): List<VocabularyWordEntity>

    @Query("SELECT DISTINCT `group` FROM vocabulary_words WHERE courseId = :courseId ORDER BY `group` ASC")
    fun getDistinctGroupsByCourse(courseId: String): Flow<List<Int>>

    @Query("SELECT * FROM vocabulary_words WHERE `group` = :group ORDER BY id ASC")
    fun getWordsByGroup(group: Int): Flow<List<VocabularyWordEntity>>

    @Query("SELECT DISTINCT `group` FROM vocabulary_words ORDER BY `group` ASC")
    fun getDistinctGroups(): Flow<List<Int>>

    @Query("SELECT * FROM vocabulary_words WHERE id = :id LIMIT 1")
    suspend fun getWordById(id: String): VocabularyWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<VocabularyWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: VocabularyWordEntity)

    @Update
    suspend fun updateWord(word: VocabularyWordEntity)

    @Query("UPDATE vocabulary_words SET status = :status, timesReviewed = timesReviewed + 1, lastReviewedAt = :timestamp WHERE id = :id")
    suspend fun updateWordStatus(id: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteWord(word: VocabularyWordEntity)

    @Query("DELETE FROM vocabulary_words WHERE id = :id")
    suspend fun deleteWordById(id: String)

    @Query("DELETE FROM vocabulary_words WHERE courseId = :courseId")
    suspend fun deleteWordsByCourse(courseId: String)

    @Query("DELETE FROM vocabulary_words")
    suspend fun clearAll()
}

@Dao
interface GamePracticeDao {
    @Query("SELECT * FROM game_practice_items ORDER BY id ASC")
    fun getAllItems(): Flow<List<GamePracticeEntity>>

    @Query("SELECT * FROM game_practice_items ORDER BY id ASC")
    suspend fun getAllItemsList(): List<GamePracticeEntity>

    @Query("SELECT * FROM game_practice_items WHERE sheetType = :type ORDER BY id ASC")
    fun getItemsByType(type: String): Flow<List<GamePracticeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<GamePracticeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GamePracticeEntity)

    @Delete
    suspend fun deleteItem(item: GamePracticeEntity)

    @Query("DELETE FROM game_practice_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM game_practice_items")
    suspend fun clearAll()
}

@Dao
interface QuestionBankDao {
    @Query("SELECT * FROM question_bank_items ORDER BY id ASC")
    fun getAllQuestions(): Flow<List<QuestionBankEntity>>

    @Query("SELECT * FROM question_bank_items ORDER BY id ASC")
    suspend fun getAllQuestionsList(): List<QuestionBankEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionBankEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionBankEntity)

    @Delete
    suspend fun deleteQuestion(question: QuestionBankEntity)

    @Query("DELETE FROM question_bank_items WHERE id = :id")
    suspend fun deleteQuestionById(id: String)

    @Query("DELETE FROM question_bank_items")
    suspend fun clearAll()
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    fun getProgress(userId: String): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    suspend fun getProgressOnce(userId: String): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: UserProgressEntity)
}
