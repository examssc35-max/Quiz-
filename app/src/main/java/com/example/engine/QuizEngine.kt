package com.example.engine

import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.QuestionReviewItem
import com.example.data.model.QuestionSchema
import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary
import com.example.data.model.QuizSchema
import org.json.JSONArray
import org.json.JSONObject

data class ActiveQuestion(
    val id: String,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val points: Int,
    val explanation: String?
)

data class AnswerFeedback(
    val isCorrect: Boolean,
    val selectedOptionIndex: Int,
    val correctOptionIndex: Int,
    val streak: Int,
    val pointsEarned: Int,
    val explanation: String?
)

class QuizEngine(
    val quizId: String,
    val quizSchema: QuizSchema,
    val mode: QuizMode
) {
    val questions: List<ActiveQuestion>
    val totalQuestions: Int

    var currentQuestionIndex: Int = 0
        private set

    // questionIndex -> selectedOptionIndex
    val selectedAnswers = mutableMapOf<Int, Int>()

    // questionIndex -> locked state (in Practice Mode)
    val lockedState = mutableMapOf<Int, Boolean>()

    var score: Int = 0
        private set

    var streak: Int = 0
        private set

    var timeRemainingSeconds: Int = quizSchema.timeLimit
        private set

    var isExamSubmitted: Boolean = false
        private set

    val totalPossibleScore: Int

    init {
        // Prepare questions with rock-solid shuffling logic
        val rawQuestions = if (quizSchema.shuffleQuestions) {
            quizSchema.questions.shuffled()
        } else {
            quizSchema.questions
        }

        questions = rawQuestions.map { q ->
            val correctOptionText = q.options[q.answer]
            val finalOptions = if (quizSchema.shuffleOptions) {
                q.options.shuffled()
            } else {
                q.options
            }
            val finalCorrectIndex = finalOptions.indexOf(correctOptionText)

            ActiveQuestion(
                id = q.id,
                questionText = q.question,
                options = finalOptions,
                correctAnswerIndex = finalCorrectIndex,
                points = q.points,
                explanation = q.explanation
            )
        }

        totalQuestions = questions.size
        totalPossibleScore = questions.sumOf { it.points }
    }

    // Constructor to resume an unfinished quiz
    constructor(
        quizId: String,
        quizSchema: QuizSchema,
        unfinished: UnfinishedQuizEntity
    ) : this(
        quizId = quizId,
        quizSchema = quizSchema,
        mode = if (unfinished.mode == QuizMode.EXAM.name) QuizMode.EXAM else QuizMode.PRACTICE
    ) {
        // Reconstruct exact state
        try {
            val qOrderArr = JSONArray(unfinished.questionOrderJson)
            val optOrderObj = JSONObject(unfinished.optionOrderJson)
            val answersObj = JSONObject(unfinished.answersStateJson)
            val lockedObj = JSONObject(unfinished.lockedStateJson)

            currentQuestionIndex = unfinished.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
            timeRemainingSeconds = unfinished.timeRemainingSeconds
            score = unfinished.score
            streak = unfinished.streak

            // Restore selected answers
            val keys = answersObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val qIdx = k.toIntOrNull()
                if (qIdx != null) {
                    selectedAnswers[qIdx] = answersObj.getInt(k)
                }
            }

            // Restore locked states
            val lockKeys = lockedObj.keys()
            while (lockKeys.hasNext()) {
                val k = lockKeys.next()
                val qIdx = k.toIntOrNull()
                if (qIdx != null) {
                    lockedState[qIdx] = lockedObj.getBoolean(k)
                }
            }
        } catch (e: Exception) {
            // Graceful fallback to default engine
        }
    }

    val currentQuestion: ActiveQuestion?
        get() = questions.getOrNull(currentQuestionIndex)

    val isCurrentQuestionAnswered: Boolean
        get() = selectedAnswers.containsKey(currentQuestionIndex)

    val isCurrentQuestionLocked: Boolean
        get() = lockedState[currentQuestionIndex] == true

    /**
     * Handles option selection.
     * In Practice Mode: locks question immediately, validates live, updates score & streak.
     * In Exam Mode: stores selection without validation or locking, allows changing answer.
     */
    fun selectOption(optionIndex: Int): AnswerFeedback? {
        val q = currentQuestion ?: return null

        if (mode == QuizMode.PRACTICE) {
            if (isCurrentQuestionLocked) return null // Already answered & locked

            selectedAnswers[currentQuestionIndex] = optionIndex
            lockedState[currentQuestionIndex] = true

            val isCorrect = (optionIndex == q.correctAnswerIndex)
            val pointsEarned = if (isCorrect) q.points else 0

            if (isCorrect) {
                score += pointsEarned
                streak += 1
            } else {
                streak = 0
            }

            return AnswerFeedback(
                isCorrect = isCorrect,
                selectedOptionIndex = optionIndex,
                correctOptionIndex = q.correctAnswerIndex,
                streak = streak,
                pointsEarned = pointsEarned,
                explanation = q.explanation
            )
        } else {
            // Exam Mode: editable, no live feedback
            selectedAnswers[currentQuestionIndex] = optionIndex
            return null
        }
    }

    fun nextQuestion(): Boolean {
        if (currentQuestionIndex < totalQuestions - 1) {
            currentQuestionIndex++
            return true
        }
        return false
    }

    fun previousQuestion(): Boolean {
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--
            return true
        }
        return false
    }

    fun jumpToQuestion(index: Int): Boolean {
        if (index in 0 until totalQuestions) {
            currentQuestionIndex = index
            return true
        }
        return false
    }

    fun updateTimer(secondsRemaining: Int) {
        timeRemainingSeconds = secondsRemaining
    }

    fun submitExam(): QuizResultSummary {
        isExamSubmitted = true

        var calculatedScore = 0
        var correctCount = 0
        var wrongCount = 0
        var unansweredCount = 0

        val reviewItems = questions.mapIndexed { index, q ->
            val userSelected = selectedAnswers[index]
            val isAnswered = userSelected != null
            val isCorrect = isAnswered && userSelected == q.correctAnswerIndex

            val pointsEarned = if (isCorrect) q.points else 0
            calculatedScore += pointsEarned

            if (!isAnswered) {
                unansweredCount++
            } else if (isCorrect) {
                correctCount++
            } else {
                wrongCount++
            }

            QuestionReviewItem(
                questionNumber = index + 1,
                questionText = q.questionText,
                options = q.options,
                userAnswerIndex = userSelected,
                correctAnswerIndex = q.correctAnswerIndex,
                userAnswerText = userSelected?.let { q.options.getOrNull(it) },
                correctAnswerText = q.options[q.correctAnswerIndex],
                isCorrect = isCorrect,
                pointsEarned = pointsEarned,
                maxPoints = q.points,
                explanation = q.explanation
            )
        }

        if (mode == QuizMode.EXAM) {
            score = calculatedScore
        }

        val accuracy = if (totalQuestions > 0) {
            (correctCount.toFloat() / totalQuestions) * 100f
        } else 0f

        val timeTaken = if (quizSchema.timeLimit > 0) {
            (quizSchema.timeLimit - timeRemainingSeconds).coerceAtLeast(0)
        } else 0

        return QuizResultSummary(
            quizId = quizId,
            quizTitle = quizSchema.title,
            mode = mode,
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            wrongCount = wrongCount,
            unansweredCount = unansweredCount,
            score = score,
            maxScore = totalPossibleScore,
            accuracy = accuracy,
            timeTakenSeconds = timeTaken,
            reviewItems = reviewItems
        )
    }

    fun toUnfinishedEntity(): UnfinishedQuizEntity {
        val qOrderArr = JSONArray()
        questions.forEach { qOrderArr.put(it.id) }

        val optOrderObj = JSONObject()
        questions.forEach { q ->
            val arr = JSONArray()
            q.options.forEach { arr.put(it) }
            optOrderObj.put(q.id, arr)
        }

        val answersObj = JSONObject()
        selectedAnswers.forEach { (k, v) -> answersObj.put(k.toString(), v) }

        val lockedObj = JSONObject()
        lockedState.forEach { (k, v) -> lockedObj.put(k.toString(), v) }

        return UnfinishedQuizEntity(
            quizId = quizId,
            quizTitle = quizSchema.title,
            category = quizSchema.category,
            mode = mode.name,
            currentQuestionIndex = currentQuestionIndex,
            totalQuestions = totalQuestions,
            timeRemainingSeconds = timeRemainingSeconds,
            score = score,
            streak = streak,
            questionOrderJson = qOrderArr.toString(),
            optionOrderJson = optOrderObj.toString(),
            answersStateJson = answersObj.toString(),
            lockedStateJson = lockedObj.toString(),
            savedAt = System.currentTimeMillis()
        )
    }
}
