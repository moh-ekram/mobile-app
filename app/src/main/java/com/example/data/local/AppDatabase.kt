package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.*

@Database(
    entities = [
        VocabularyWordEntity::class,
        GamePracticeEntity::class,
        QuestionBankEntity::class,
        UserProgressEntity::class,
        CourseEntity::class,
        ArticleEntity::class,
        DeletedArticleTitleEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun gamePracticeDao(): GamePracticeDao
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun courseDao(): CourseDao
    abstract fun articleDao(): ArticleDao
    abstract fun deletedArticleDao(): DeletedArticleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memorizer_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

