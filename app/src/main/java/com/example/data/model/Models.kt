package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

typealias WordStatus = String // "know", "confusion", "dont_know", "unrated"

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String, // e.g. "course_barrons_333", "course_gre_mastery"
    val title: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val columnHeadersJson: String? = null // JSON list of detected column headers
)

@Entity(tableName = "saved_articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val author: String = "Unknown Author",
    val courseId: String = "course_default",
    val createdAt: Long = System.currentTimeMillis(),
    val wordCount: Int = 0
)

@Entity(tableName = "deleted_article_titles")
data class DeletedArticleTitleEntity(
    @PrimaryKey val normalizedTitle: String,
    val originalTitle: String = "",
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vocabulary_words")
data class VocabularyWordEntity(
    @PrimaryKey val id: String,
    val word: String,
    val meaning: String,
    val group: String = "1",
    val synonyms: String? = null,
    val extraWord: String? = null,
    val extraMeaning: String? = null,
    val example: String? = null,
    val mnemonic: String? = null,
    val status: String = "unrated", // 'know' | 'confusion' | 'dont_know' | 'unrated'
    val customPlacesJson: String? = null,
    val courseId: String = "course_default",
    val timesReviewed: Int = 0,
    val lastReviewedAt: Long = System.currentTimeMillis(),
    val isReported: Boolean = false,
    val reportReason: String? = null,
    val lastQuizStatus: String? = "not_studied", // "correct", "incorrect", "not_studied"
    val quizCorrectCount: Int = 0,
    val quizIncorrectCount: Int = 0
)

@Entity(tableName = "game_practice_items")
data class GamePracticeEntity(
    @PrimaryKey val id: String,
    val sheetType: String, // "odd_one_out", "analogy", "practice"
    val question: String,
    val opt1: String,
    val opt2: String,
    val opt3: String,
    val opt4: String,
    val answer: String,
    val explanation: String? = null,
    val lastAttemptStatus: String? = "not_studied", // "correct", "incorrect", "not_studied"
    val correctCount: Int = 0,
    val incorrectCount: Int = 0
)

@Entity(tableName = "question_bank_items")
data class QuestionBankEntity(
    @PrimaryKey val id: String,
    val question: String,
    val opt1: String,
    val opt2: String,
    val opt3: String,
    val opt4: String,
    val answer: String,
    val explanation: String? = null,
    val filter1: String? = null,
    val filter2: String? = null,
    val filter3: String? = null,
    val filter1Label: String? = "Category",
    val filter2Label: String? = "Difficulty",
    val filter3Label: String? = "Source"
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val userId: String,
    val totalWords: Int = 0,
    val knowCount: Int = 0,
    val confusionCount: Int = 0,
    val dontKnowCount: Int = 0,
    val unratedCount: Int = 0,
    val streakDays: Int = 1,
    val quizCompleted: Int = 0,
    val quizTotalScore: Int = 0,
    val lastBackupTimestamp: Long = 0L,
    val lastCloudSyncTimestamp: Long = 0L
)

data class UserSession(
    val userId: String,
    val email: String? = null,
    val displayName: String,
    val isGuest: Boolean = false,
    val isGoogleUser: Boolean = false,
    val avatarUri: String? = null,
    val targetExam: String = "GRE / IELTS",
    val dailyWordGoal: Int = 20,
    val bio: String = "Aiming for GRE 330+ and IELTS 8.0"
)
