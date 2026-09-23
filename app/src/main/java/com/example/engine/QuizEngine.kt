package com.example.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.AnswerComparison
import com.example.data.model.QuestionReviewItem
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary
import com.example.data.model.QuizSchema
import org.json.JSONArray
import org.json.JSONObject

enum class AnswerState {
    UNANSWERED,
    CORRECT,
    INCORRECT,
    ANSWER_NOT_SET
}

data class QuestionAnswerState(
    val isAnswered: Boolean = false,
    val selectedOptionIndex: Int? = null,
    val userTextAnswer: String? = null,
    val answerState: AnswerState = AnswerState.UNANSWERED,
    val isLocked: Boolean = false
)

data class ActiveQuestion(
    val id: String,
    val type: QuestionType,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val fillBlankAnswer: String,
    val acceptedAnswers: List<String>,
    val points: Int,
    val explanation: String?
) {
    val hasConfiguredAnswer: Boolean
        get() = if (type == QuestionType.FILL_BLANK) {
            fillBlankAnswer.isNotBlank() || acceptedAnswers.any { it.isNotBlank() }
        } else {
            options.isNotEmpty() && correctAnswerIndex in options.indices
        }
}

data class AnswerFeedback(
    val isCorrect: Boolean,
    val selectedOptionIndex: Int = -1,
    val correctOptionIndex: Int = -1,
    val streak: Int,
    val pointsEarned: Int,
    val explanation: String?,
    val userTextAnswer: String? = null,
    val correctTextAnswer: String? = null,
    val isAnswerNotSet: Boolean = false
)

class QuizEngine(
    val quizId: String,
    val quizSchema: QuizSchema,
    val mode: QuizMode
) {
    val questions: List<ActiveQuestion>
    val totalQuestions: Int

    // Reactive Compose state for question answer states (single source of truth for UI)
    val questionStates = mutableStateMapOf<Int, QuestionAnswerState>()

    var currentQuestionIndex: Int by mutableIntStateOf(0)
        private set

    // Backwards-compatible raw maps for serialization & review
    val selectedAnswers = mutableMapOf<Int, Int>()
    val userTextAnswers = mutableMapOf<Int, String>()
    val lockedState = mutableMapOf<Int, Boolean>()

    var score: Int by mutableIntStateOf(0)
        private set

    var streak: Int by mutableIntStateOf(0)
        private set

    var timeRemainingSeconds: Int by mutableIntStateOf(quizSchema.timeLimit)
        private set

    var isExamSubmitted: Boolean by mutableStateOf(false)
        private set

    val totalPossibleScore: Int

    init {
        // Prepare questions with stable shuffle indexing
        val rawQuestions = if (quizSchema.shuffleQuestions) {
            quizSchema.questions.shuffled()
        } else {
            quizSchema.questions
        }

        questions = rawQuestions.map { q ->
            if (q.type == QuestionType.FILL_BLANK) {
                val cleanedAccepted = q.acceptedAnswers.filter { it.isNotBlank() }
                val primaryAnswer = q.fillBlankAnswer.trim().ifEmpty { cleanedAccepted.firstOrNull() ?: "" }
                val allAccepted = mutableListOf<String>()
                if (primaryAnswer.isNotEmpty()) {
                    allAccepted.add(primaryAnswer)
                }
                for (item in cleanedAccepted) {
                    if (item !in allAccepted) {
                        allAccepted.add(item)
                    }
                }

                ActiveQuestion(
                    id = q.id,
                    type = QuestionType.FILL_BLANK,
                    questionText = q.question,
                    options = emptyList(),
                    correctAnswerIndex = -1,
                    fillBlankAnswer = primaryAnswer,
                    acceptedAnswers = allAccepted,
                    points = q.points,
                    explanation = q.explanation
                )
            } else {
                val indexedOptions = q.options.mapIndexed { index, text -> index to text }
                val finalIndexedOptions = if (quizSchema.shuffleOptions) {
                    indexedOptions.shuffled()
                } else {
                    indexedOptions
                }
                val finalOptions = finalIndexedOptions.map { it.second }
                val finalCorrectIndex = finalIndexedOptions.indexOfFirst { it.first == q.answer }

                ActiveQuestion(
                    id = q.id,
                    type = QuestionType.MCQ,
                    questionText = q.question,
                    options = finalOptions,
                    correctAnswerIndex = if (finalCorrectIndex >= 0) finalCorrectIndex else 0,
                    fillBlankAnswer = "",
                    acceptedAnswers = emptyList(),
                    points = q.points,
                    explanation = q.explanation
                )
            }
        }

        totalQuestions = questions.size
        totalPossibleScore = questions.sumOf { it.points }

        // Initialize question states for every question
        for (i in 0 until totalQuestions) {
            questionStates[i] = QuestionAnswerState()
        }
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
            val answersObj = JSONObject(unfinished.answersStateJson)
            val lockedObj = JSONObject(unfinished.lockedStateJson)

            currentQuestionIndex = unfinished.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
            timeRemainingSeconds = unfinished.timeRemainingSeconds
            score = unfinished.score
            streak = unfinished.streak

            // Restore selected and typed answers
            val keys = answersObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val qIdx = k.toIntOrNull()
                if (qIdx != null && qIdx in questions.indices) {
                    val q = questions[qIdx]
                    if (q.type == QuestionType.FILL_BLANK) {
                        userTextAnswers[qIdx] = answersObj.optString(k, "")
                    } else {
                        val sel = answersObj.optInt(k, -1)
                        if (sel >= 0) {
                            selectedAnswers[qIdx] = sel
                        }
                    }
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

            // Populate reactive questionStates from restored data
            for (i in 0 until totalQuestions) {
                val q = questions.getOrNull(i)
                val isLocked = lockedState[i] == true

                if (q != null && q.type == QuestionType.FILL_BLANK) {
                    val userText = userTextAnswers[i]
                    val isAnswered = !userText.isNullOrBlank()
                    val isCorrect = isAnswered && AnswerComparison.isAnswerCorrect(userText!!, q.acceptedAnswers)

                    val answerState = when {
                        !isAnswered -> AnswerState.UNANSWERED
                        mode == QuizMode.EXAM -> AnswerState.UNANSWERED
                        isCorrect -> AnswerState.CORRECT
                        else -> AnswerState.INCORRECT
                    }

                    questionStates[i] = QuestionAnswerState(
                        isAnswered = isAnswered,
                        selectedOptionIndex = null,
                        userTextAnswer = userText,
                        answerState = answerState,
                        isLocked = isLocked
                    )
                } else {
                    val userSelected = selectedAnswers[i]
                    val isAnswered = userSelected != null
                    val isCorrect = (userSelected != null && q != null && userSelected == q.correctAnswerIndex)

                    val answerState = when {
                        userSelected == null -> AnswerState.UNANSWERED
                        mode == QuizMode.EXAM -> AnswerState.UNANSWERED
                        isCorrect -> AnswerState.CORRECT
                        else -> AnswerState.INCORRECT
                    }

                    questionStates[i] = QuestionAnswerState(
                        isAnswered = isAnswered,
                        selectedOptionIndex = userSelected,
                        userTextAnswer = null,
                        answerState = answerState,
                        isLocked = isLocked
                    )
                }
            }
        } catch (e: Exception) {
            // Graceful fallback to default engine
        }
    }

    val currentQuestion: ActiveQuestion?
        get() = questions.getOrNull(currentQuestionIndex)

    val isCurrentQuestionAnswered: Boolean
        get() = questionStates[currentQuestionIndex]?.isAnswered == true

    val isCurrentQuestionLocked: Boolean
        get() = questionStates[currentQuestionIndex]?.isLocked == true

    fun getQuestionState(questionIndex: Int): QuestionAnswerState {
        return questionStates[questionIndex] ?: QuestionAnswerState()
    }

    /**
     * Handles option selection for MCQ questions.
     * In Practice Mode: locks question immediately, validates live, updates score & streak.
     * In Exam Mode: stores selection without validation or locking, allows changing answer.
     */
    @Synchronized
    fun selectOption(optionIndex: Int): AnswerFeedback? {
        val q = currentQuestion ?: return null
        val qIndex = currentQuestionIndex
        if (q.type != QuestionType.MCQ) return null

        if (mode == QuizMode.PRACTICE) {
            val currentState = questionStates[qIndex]
            if (currentState?.isLocked == true || lockedState[qIndex] == true) {
                return null // Already answered and locked, ignore tap
            }

            val isCorrect = (optionIndex == q.correctAnswerIndex)
            val answerState = if (isCorrect) AnswerState.CORRECT else AnswerState.INCORRECT
            val pointsEarned = if (isCorrect) q.points else 0

            // 1. Immediately store answer and lock state
            selectedAnswers[qIndex] = optionIndex
            lockedState[qIndex] = true

            // 2. Update reactive state (triggers instant UI recomposition)
            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = optionIndex,
                userTextAnswer = null,
                answerState = answerState,
                isLocked = true
            )

            // 3. Update score and streak exactly once
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
            // Exam Mode: editable, no live feedback, no locking until submission
            if (isExamSubmitted) return null

            selectedAnswers[qIndex] = optionIndex
            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = optionIndex,
                userTextAnswer = null,
                answerState = AnswerState.UNANSWERED,
                isLocked = false
            )
            return null
        }
    }

    /**
     * Handles text answer submission in Practice Mode for Fill-in-the-blank questions.
     * Locks the question immediately, evaluates answer, awards points if correct.
     */
    @Synchronized
    fun submitTextAnswer(answerText: String): AnswerFeedback? {
        val q = currentQuestion ?: return null
        val qIndex = currentQuestionIndex
        if (q.type != QuestionType.FILL_BLANK) return null

        val currentState = questionStates[qIndex]
        if (currentState?.isLocked == true || lockedState[qIndex] == true) {
            return null // Already answered and locked
        }

        val trimmed = answerText.trim()
        userTextAnswers[qIndex] = trimmed
        lockedState[qIndex] = true

        if (!q.hasConfiguredAnswer) {
            // Empty answer in fill_blank: show "Answer not available" instead of judging it
            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = null,
                userTextAnswer = trimmed,
                answerState = AnswerState.ANSWER_NOT_SET,
                isLocked = true
            )
            return AnswerFeedback(
                isCorrect = false,
                selectedOptionIndex = -1,
                correctOptionIndex = -1,
                streak = streak,
                pointsEarned = 0,
                explanation = q.explanation,
                userTextAnswer = trimmed,
                correctTextAnswer = "Answer not available",
                isAnswerNotSet = true
            )
        }

        val isCorrect = AnswerComparison.isAnswerCorrect(trimmed, q.acceptedAnswers)
        val answerState = if (isCorrect) AnswerState.CORRECT else AnswerState.INCORRECT
        val pointsEarned = if (isCorrect) q.points else 0

        questionStates[qIndex] = QuestionAnswerState(
            isAnswered = true,
            selectedOptionIndex = null,
            userTextAnswer = trimmed,
            answerState = answerState,
            isLocked = true
        )

        if (isCorrect) {
            score += pointsEarned
            streak += 1
        } else {
            streak = 0
        }

        return AnswerFeedback(
            isCorrect = isCorrect,
            selectedOptionIndex = -1,
            correctOptionIndex = -1,
            streak = streak,
            pointsEarned = pointsEarned,
            explanation = q.explanation,
            userTextAnswer = trimmed,
            correctTextAnswer = q.fillBlankAnswer,
            isAnswerNotSet = false
        )
    }

    /**
     * Handles updating the typed text answer in Exam Mode for Fill-in-the-blank questions.
     * Allows free editing and navigation without revealing feedback.
     */
    fun updateExamTextAnswer(answerText: String) {
        if (isExamSubmitted) return
        val q = currentQuestion ?: return
        val qIndex = currentQuestionIndex
        if (q.type != QuestionType.FILL_BLANK) return

        userTextAnswers[qIndex] = answerText
        val isAnswered = answerText.trim().isNotEmpty()

        questionStates[qIndex] = QuestionAnswerState(
            isAnswered = isAnswered,
            selectedOptionIndex = null,
            userTextAnswer = answerText,
            answerState = AnswerState.UNANSWERED,
            isLocked = false
        )
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
            val isCorrect: Boolean
            val isAnswered: Boolean
            val userAnswerText: String?
            val correctAnswerText: String
            val userSelected = selectedAnswers[index]

            val isAnswerNotSet = (q.type == QuestionType.FILL_BLANK && !q.hasConfiguredAnswer)

            if (q.type == QuestionType.FILL_BLANK) {
                val userText = userTextAnswers[index]?.trim()
                val isAnsweredByUser = !userText.isNullOrEmpty()

                if (isAnswerNotSet) {
                    // Empty answer in fill_blank: treat as unanswered during scoring
                    isAnswered = isAnsweredByUser
                    isCorrect = false
                    userAnswerText = if (isAnsweredByUser) userText else null
                    correctAnswerText = "Answer not available"

                    questionStates[index] = QuestionAnswerState(
                        isAnswered = isAnsweredByUser,
                        selectedOptionIndex = null,
                        userTextAnswer = userText,
                        answerState = AnswerState.ANSWER_NOT_SET,
                        isLocked = true
                    )
                } else {
                    isAnswered = isAnsweredByUser
                    isCorrect = isAnswered && AnswerComparison.isAnswerCorrect(userText!!, q.acceptedAnswers)
                    userAnswerText = if (isAnswered) userText else null
                    correctAnswerText = q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }

                    // Reveal question state
                    questionStates[index] = QuestionAnswerState(
                        isAnswered = isAnswered,
                        selectedOptionIndex = null,
                        userTextAnswer = userText,
                        answerState = when {
                            !isAnswered -> AnswerState.UNANSWERED
                            isCorrect -> AnswerState.CORRECT
                            else -> AnswerState.INCORRECT
                        },
                        isLocked = true
                    )
                }
            } else {
                isAnswered = userSelected != null
                isCorrect = isAnswered && userSelected == q.correctAnswerIndex
                userAnswerText = userSelected?.let { q.options.getOrNull(it) }
                correctAnswerText = q.options.getOrElse(q.correctAnswerIndex) { "" }

                // Reveal question state
                questionStates[index] = QuestionAnswerState(
                    isAnswered = isAnswered,
                    selectedOptionIndex = userSelected,
                    userTextAnswer = null,
                    answerState = when {
                        !isAnswered -> AnswerState.UNANSWERED
                        isCorrect -> AnswerState.CORRECT
                        else -> AnswerState.INCORRECT
                    },
                    isLocked = true
                )
            }

            val pointsEarned = if (isCorrect) q.points else 0
            calculatedScore += pointsEarned

            if (isAnswerNotSet || !isAnswered) {
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
                userAnswerIndex = if (q.type == QuestionType.MCQ) userSelected else null,
                correctAnswerIndex = if (q.type == QuestionType.MCQ) q.correctAnswerIndex else -1,
                userAnswerText = userAnswerText,
                correctAnswerText = correctAnswerText,
                isCorrect = isCorrect,
                pointsEarned = pointsEarned,
                maxPoints = q.points,
                explanation = q.explanation,
                questionType = q.type,
                acceptedAnswers = q.acceptedAnswers,
                isAnswerNotSet = isAnswerNotSet
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
        questions.forEachIndexed { i, q ->
            if (q.type == QuestionType.FILL_BLANK) {
                val text = userTextAnswers[i]
                if (!text.isNullOrBlank()) {
                    answersObj.put(i.toString(), text)
                }
            } else {
                val sel = selectedAnswers[i]
                if (sel != null) {
                    answersObj.put(i.toString(), sel)
                }
            }
        }

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
