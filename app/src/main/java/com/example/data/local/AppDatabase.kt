package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.QuizAttemptDao
import com.example.data.local.dao.QuizDao
import com.example.data.local.dao.UnfinishedQuizDao
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizEntity
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.QuizJsonParser
import com.example.data.samples.SampleQuizzes

@Database(
    entities = [QuizEntity::class, QuizAttemptEntity::class, UnfinishedQuizEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quizDao(): QuizDao
    abstract fun quizAttemptDao(): QuizAttemptDao
    abstract fun unfinishedQuizDao(): UnfinishedQuizDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quiz_explore.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun populateInitialQuizzes(database: AppDatabase) {
            val dao = database.quizDao()
            if (dao.getQuizCount() == 0) {
                val entities = SampleQuizzes.ALL_SAMPLES.mapIndexed { index, schema ->
                    val totalPoints = schema.questions.sumOf { it.points }
                    QuizEntity(
                        id = if (index == 0) "sample_bn_gk" else "sample_${schema.category.lowercase().replace(" ", "_")}",
                        title = schema.title,
                        description = schema.description,
                        category = schema.category,
                        difficulty = schema.difficulty,
                        questionCount = schema.questions.size,
                        timeLimit = schema.timeLimit,
                        shuffleQuestions = schema.shuffleQuestions,
                        shuffleOptions = schema.shuffleOptions,
                        jsonContent = QuizJsonParser.toJsonString(schema),
                        isFavorite = (index == 0 || index == 1),
                        createdAt = System.currentTimeMillis() - (index * 60000L),
                        maxPossibleScore = totalPoints
                    )
                }
                dao.insertAll(entities)
            }
        }
    }
}
