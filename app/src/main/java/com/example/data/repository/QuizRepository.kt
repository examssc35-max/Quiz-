package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizEntity
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.QuestionReviewItem
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizResultSummary
import com.example.data.model.QuizSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class QuizRepository(private val database: AppDatabase) {

    val allQuizzes: Flow<List<QuizEntity>> = database.quizDao().getAllQuizzes()
    val favoriteQuizzes: Flow<List<QuizEntity>> = database.quizDao().getFavoriteQuizzes()
    val recentQuizzes: Flow<List<QuizEntity>> = database.quizDao().getRecentQuizzes()
    val latestUnfinishedQuiz: Flow<UnfinishedQuizEntity?> = database.unfinishedQuizDao().getLatestUnfinishedQuiz()
    val allAttempts: Flow<List<QuizAttemptEntity>> = database.quizAttemptDao().getAllAttempts()
    val recentAttempts: Flow<List<QuizAttemptEntity>> = database.quizAttemptDao().getRecentAttempts()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        AppDatabase.populateInitialQuizzes(database)
    }

    suspend fun getQuizById(id: String): QuizEntity? = withContext(Dispatchers.IO) {
        database.quizDao().getQuizById(id)
    }

    suspend fun insertOrUpdateQuiz(quizSchema: QuizSchema, existingId: String? = null): String = withContext(Dispatchers.IO) {
        val id = existingId ?: UUID.randomUUID().toString()
        val totalPoints = quizSchema.questions.sumOf { it.points }
        val jsonString = QuizJsonParser.toJsonString(quizSchema)
        
        val existing = existingId?.let { database.quizDao().getQuizById(it) }
        val entity = QuizEntity(
            id = id,
            title = quizSchema.title,
            description = quizSchema.description,
            category = quizSchema.category,
            difficulty = quizSchema.difficulty,
            questionCount = quizSchema.questions.size,
            timeLimit = quizSchema.timeLimit,
            shuffleQuestions = quizSchema.shuffleQuestions,
            shuffleOptions = quizSchema.shuffleOptions,
            jsonContent = jsonString,
            isFavorite = existing?.isFavorite ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastPlayedAt = existing?.lastPlayedAt,
            totalAttempts = existing?.totalAttempts ?: 0,
            bestScore = existing?.bestScore,
            maxPossibleScore = totalPoints
        )
        database.quizDao().insertQuiz(entity)
        id
    }

    suspend fun duplicateQuiz(quizId: String): String = withContext(Dispatchers.IO) {
        val original = database.quizDao().getQuizById(quizId) ?: throw IllegalArgumentException("Quiz not found")
        val parsed = QuizJsonParser.validateAndParse(original.jsonContent).getOrThrow()
        val duplicatedSchema = parsed.copy(
            title = "${parsed.title} (Copy)",
            questions = parsed.questions.map { it.copy(id = UUID.randomUUID().toString().take(8)) }
        )
        insertOrUpdateQuiz(duplicatedSchema)
    }

    suspend fun deleteQuiz(quizId: String) = withContext(Dispatchers.IO) {
        database.quizDao().deleteQuizById(quizId)
        database.unfinishedQuizDao().deleteUnfinishedQuiz(quizId)
    }

    suspend fun toggleFavorite(quizId: String, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        database.quizDao().updateFavorite(quizId, !currentFavorite)
    }

    suspend fun recordAttempt(summary: QuizResultSummary) = withContext(Dispatchers.IO) {
        // Serialize review items to JSON
        val reviewArray = JSONArray()
        summary.reviewItems.forEach { item ->
            val obj = JSONObject()
            obj.put("questionNumber", item.questionNumber)
            obj.put("questionText", item.questionText)
            val optArr = JSONArray()
            item.options.forEach { optArr.put(it) }
            obj.put("options", optArr)
            obj.put("userAnswerIndex", item.userAnswerIndex ?: -1)
            obj.put("correctAnswerIndex", item.correctAnswerIndex)
            obj.put("userAnswerText", item.userAnswerText ?: "")
            obj.put("correctAnswerText", item.correctAnswerText)
            obj.put("isCorrect", item.isCorrect)
            obj.put("pointsEarned", item.pointsEarned)
            obj.put("maxPoints", item.maxPoints)
            obj.put("explanation", item.explanation ?: "")
            reviewArray.put(obj)
        }

        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            quizId = summary.quizId,
            quizTitle = summary.quizTitle,
            mode = summary.mode.name,
            totalQuestions = summary.totalQuestions,
            correctCount = summary.correctCount,
            wrongCount = summary.wrongCount,
            unansweredCount = summary.unansweredCount,
            score = summary.score,
            maxScore = summary.maxScore,
            accuracy = summary.accuracy,
            timeTakenSeconds = summary.timeTakenSeconds,
            timestamp = System.currentTimeMillis(),
            reviewDataJson = reviewArray.toString()
        )
        database.quizAttemptDao().insertAttempt(attempt)
        database.quizDao().recordQuizCompletion(summary.quizId, System.currentTimeMillis(), summary.score)
        database.unfinishedQuizDao().deleteUnfinishedQuiz(summary.quizId)
    }

    fun parseReviewItems(reviewJson: String): List<QuestionReviewItem> {
        return try {
            val arr = JSONArray(reviewJson)
            val list = mutableListOf<QuestionReviewItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val optArr = obj.getJSONArray("options")
                val options = mutableListOf<String>()
                for (j in 0 until optArr.length()) {
                    options.add(optArr.getString(j))
                }
                val uIdx = obj.getInt("userAnswerIndex")
                val explanation = obj.optString("explanation", "").ifEmpty { null }
                list.add(
                    QuestionReviewItem(
                        questionNumber = obj.getInt("questionNumber"),
                        questionText = obj.getString("questionText"),
                        options = options,
                        userAnswerIndex = if (uIdx >= 0) uIdx else null,
                        correctAnswerIndex = obj.getInt("correctAnswerIndex"),
                        userAnswerText = obj.optString("userAnswerText", "").ifEmpty { null },
                        correctAnswerText = obj.getString("correctAnswerText"),
                        isCorrect = obj.getBoolean("isCorrect"),
                        pointsEarned = obj.getInt("pointsEarned"),
                        maxPoints = obj.getInt("maxPoints"),
                        explanation = explanation
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveUnfinishedQuiz(unfinished: UnfinishedQuizEntity) = withContext(Dispatchers.IO) {
        database.unfinishedQuizDao().saveUnfinishedQuiz(unfinished)
    }

    suspend fun getUnfinishedQuiz(quizId: String): UnfinishedQuizEntity? = withContext(Dispatchers.IO) {
        database.unfinishedQuizDao().getUnfinishedQuiz(quizId)
    }

    suspend fun deleteUnfinishedQuiz(quizId: String) = withContext(Dispatchers.IO) {
        database.unfinishedQuizDao().deleteUnfinishedQuiz(quizId)
    }

    suspend fun resetStatistics() = withContext(Dispatchers.IO) {
        database.quizAttemptDao().deleteAllAttempts()
    }

    suspend fun clearAllQuizzes() = withContext(Dispatchers.IO) {
        database.quizDao().deleteAllQuizzes()
        database.unfinishedQuizDao().deleteAllUnfinishedQuizzes()
        database.quizAttemptDao().deleteAllAttempts()
        ensureSeeded()
    }

    suspend fun clearUnfinishedProgress() = withContext(Dispatchers.IO) {
        database.unfinishedQuizDao().deleteAllUnfinishedQuizzes()
    }

    companion object {
        @Volatile
        private var INSTANCE: QuizRepository? = null

        fun getInstance(context: Context): QuizRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val repo = QuizRepository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
