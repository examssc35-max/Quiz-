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
        val verifiedCount = quizSchema.questions.count { it.isVerified }
        val correctedCount = quizSchema.questions.count { it.wasAnswerCorrected }

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
            maxPossibleScore = totalPoints,
            isAiVerified = (verifiedCount > 0 && verifiedCount == quizSchema.questions.size) || (existing?.isAiVerified == true),
            lastVerifiedAt = if (verifiedCount > 0) (existing?.lastVerifiedAt ?: System.currentTimeMillis()) else existing?.lastVerifiedAt,
            verifiedQuestionCount = if (verifiedCount > 0) verifiedCount else existing?.verifiedQuestionCount ?: 0,
            correctedQuestionCount = if (correctedCount > 0) correctedCount else existing?.correctedQuestionCount ?: 0,
            verificationSummary = existing?.verificationSummary,
            auditLogJson = existing?.auditLogJson
        )
        database.quizDao().insertQuiz(entity)
        id
    }

    suspend fun saveAuditedQuiz(quizId: String, updatedSchema: QuizSchema, summary: com.example.ai.QuizAuditSummary) = withContext(Dispatchers.IO) {
        val existing = database.quizDao().getQuizById(quizId)
        val jsonString = QuizJsonParser.toJsonString(updatedSchema)
        val totalPoints = updatedSchema.questions.sumOf { it.points }

        val summaryJson = JSONObject().apply {
            put("quizId", summary.quizId)
            put("totalAudited", summary.totalQuestionsAudited)
            put("correctedCount", summary.correctedCount)
            put("needsReviewCount", summary.needsReviewCount)
            put("auditedAt", summary.auditedAt)
            val recordsArr = JSONArray()
            summary.auditRecords.forEach { r ->
                val rObj = JSONObject().apply {
                    put("questionId", r.questionId)
                    put("questionText", r.questionText)
                    put("originalAnswer", r.originalAnswer)
                    put("correctedAnswer", r.correctedAnswer)
                    put("changed", r.changed)
                    put("reason", r.reason)
                    put("confidence", r.confidence)
                    put("needsReview", r.needsReview)
                    put("timestamp", r.timestamp)
                }
                recordsArr.put(rObj)
            }
            put("records", recordsArr)
        }.toString()

        val entity = QuizEntity(
            id = quizId,
            title = updatedSchema.title,
            description = updatedSchema.description,
            category = updatedSchema.category,
            difficulty = updatedSchema.difficulty,
            questionCount = updatedSchema.questions.size,
            timeLimit = updatedSchema.timeLimit,
            shuffleQuestions = updatedSchema.shuffleQuestions,
            shuffleOptions = updatedSchema.shuffleOptions,
            jsonContent = jsonString,
            isFavorite = existing?.isFavorite ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastPlayedAt = existing?.lastPlayedAt,
            totalAttempts = existing?.totalAttempts ?: 0,
            bestScore = existing?.bestScore,
            maxPossibleScore = totalPoints,
            isAiVerified = true,
            lastVerifiedAt = summary.auditedAt,
            verifiedQuestionCount = summary.totalQuestionsAudited,
            correctedQuestionCount = summary.correctedCount,
            verificationSummary = "AI verified ${summary.totalQuestionsAudited} questions. ${summary.correctedCount} answers corrected.",
            auditLogJson = summaryJson
        )
        database.quizDao().insertQuiz(entity)
    }

    suspend fun revertQuizVerification(quizId: String): Boolean = withContext(Dispatchers.IO) {
        val quiz = database.quizDao().getQuizById(quizId) ?: return@withContext false
        val schema = QuizJsonParser.validateAndParse(quiz.jsonContent).getOrNull() ?: return@withContext false
        val restoredQuestions = schema.questions.map { q ->
            q.copy(
                verifiedAnswerIndex = null,
                verifiedFillBlankAnswer = null,
                verifiedAcceptedAnswers = null,
                verifiedExplanation = null,
                isVerified = false,
                verificationConfidence = 0.0,
                verificationReason = null,
                needsReview = false,
                lastVerifiedAt = null,
                correctedOptions = null
            )
        }
        val restoredSchema = schema.copy(questions = restoredQuestions)
        val jsonString = QuizJsonParser.toJsonString(restoredSchema)
        val entity = quiz.copy(
            jsonContent = jsonString,
            isAiVerified = false,
            lastVerifiedAt = null,
            verifiedQuestionCount = 0,
            correctedQuestionCount = 0,
            verificationSummary = null,
            auditLogJson = null
        )
        database.quizDao().insertQuiz(entity)
        true
    }

    suspend fun duplicateQuiz(quizId: String): String = withContext(Dispatchers.IO) {
        val original = database.quizDao().getQuizById(quizId) ?: throw IllegalArgumentException("Quiz not found")
        val parsed = QuizJsonParser.validateAndParse(original.jsonContent).getOrThrow()
        val duplicatedSchema = parsed.copy(
            title = "${parsed.title} (Copy)",
            questions = parsed.questions.map { it.copy(id = UUID.randomUUID().toString().take(8)) }
        )
        return@withContext insertOrUpdateQuiz(duplicatedSchema)
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

    suspend fun updateQuestionAnswer(
        quizId: String,
        questionId: String,
        newAnswer: String,
        newAcceptedAnswers: List<String> = emptyList(),
        newOptionIndex: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        val quiz = database.quizDao().getQuizById(quizId) ?: return@withContext false
        val schema = QuizJsonParser.validateAndParse(quiz.jsonContent).getOrNull() ?: return@withContext false
        val updatedQuestions = schema.questions.map { q ->
            if (q.id == questionId) {
                if (q.type == com.example.data.model.QuestionType.MCQ && newOptionIndex in q.options.indices) {
                    q.copy(answer = newOptionIndex)
                } else {
                    val allAccepted = mutableListOf<String>()
                    if (newAnswer.isNotBlank()) allAccepted.add(newAnswer)
                    for (acc in newAcceptedAnswers) {
                        if (acc.isNotBlank() && acc !in allAccepted) allAccepted.add(acc)
                    }
                    for (acc in q.acceptedAnswers) {
                        if (acc.isNotBlank() && acc !in allAccepted) allAccepted.add(acc)
                    }
                    q.copy(
                        fillBlankAnswer = newAnswer.ifBlank { q.fillBlankAnswer },
                        acceptedAnswers = allAccepted
                    )
                }
            } else {
                q
            }
        }
        val updatedSchema = schema.copy(questions = updatedQuestions)
        insertOrUpdateQuiz(updatedSchema, quizId)
        true
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
