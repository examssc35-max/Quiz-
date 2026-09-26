package com.example.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.AiEvaluationResult
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
    EVALUATING,
    CORRECT,
    INCORRECT,
    ANSWER_NOT_SET
}

data class QuestionAnswerState(
    val isAnswered: Boolean = false,
    val selectedOptionIndex: Int? = null,
    val userTextAnswer: String? = null,
    val answerState: AnswerState = AnswerState.UNANSWERED,
    val isLocked: Boolean = false,
    val isAiEvaluating: Boolean = false,
    val aiEvaluation: AiEvaluationResult? = null,
    val banglaExplanation: String? = null
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
    val isAnswerNotSet: Boolean = false,
    val banglaExplanation: String? = null,
    val isAiEvaluated: Boolean = false,
    val isAlternativeAccepted: Boolean = false,
    val aiEvaluation: AiEvaluationResult? = null
)

class QuizEngine(
    val quizId: String,
    val quizSchema: QuizSchema,
    val mode: QuizMode,
    val aiEvaluator: AiAnswerEvaluator = com.example.ai.AIManager.defaultManager
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
                    correctAnswerIndex = finalCorrectIndex,
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
        unfinished: UnfinishedQuizEntity,
        aiEvaluator: AiAnswerEvaluator = com.example.ai.AIManager.defaultManager
    ) : this(
        quizId = quizId,
        quizSchema = quizSchema,
        mode = if (unfinished.mode == QuizMode.EXAM.name) QuizMode.EXAM else QuizMode.PRACTICE,
        aiEvaluator = aiEvaluator
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

            if (!q.hasConfiguredAnswer || q.correctAnswerIndex !in q.options.indices) {
                // Invalid or missing correct answer in question data
                selectedAnswers[qIndex] = optionIndex
                lockedState[qIndex] = true
                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = optionIndex,
                    userTextAnswer = null,
                    answerState = AnswerState.ANSWER_NOT_SET,
                    isLocked = true
                )
                streak = 0
                return AnswerFeedback(
                    isCorrect = false,
                    selectedOptionIndex = optionIndex,
                    correctOptionIndex = -1,
                    streak = 0,
                    pointsEarned = 0,
                    explanation = q.explanation ?: "Answer not configured",
                    isAnswerNotSet = true
                )
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
     * Handles text answer submission in Practice Mode for Fill-in-the-blank questions (synchronous).
     * Evaluates exact answer matching, awards points if correct.
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

        val banglaExpl = if (isCorrect) {
            val matched = q.acceptedAnswers.firstOrNull {
                AnswerComparison.normalize(it) == AnswerComparison.normalize(trimmed)
            } ?: trimmed
            "তোমার উত্তরটি সঠিক। এখানে ‘$matched’ শব্দটি বাক্যের অর্থ ও grammar অনুযায়ী ঠিকভাবে বসে।"
        } else {
            val correctListStr = if (q.acceptedAnswers.size > 1) {
                q.acceptedAnswers.joinToString(", ")
            } else {
                q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }
            }
            "তোমার উত্তর ‘$trimmed’ সঠিক উত্তরের সাথে মেলেনি। সঠিক উত্তর: $correctListStr।"
        }

        questionStates[qIndex] = QuestionAnswerState(
            isAnswered = true,
            selectedOptionIndex = null,
            userTextAnswer = trimmed,
            answerState = answerState,
            isLocked = true,
            banglaExplanation = banglaExpl
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
            isAnswerNotSet = false,
            banglaExplanation = banglaExpl
        )
    }

    /**
     * Practice Mode Fill-in-the-Blank submission with real Gemini AI evaluation.
     *
     * 1. Locks the question and displays the animated evaluating indicator.
     * 2. Sends question text, blank position context, accepted answers, and user's answer to Gemini.
     * 3. Gemini evaluates sentence meaning, context, grammar, part of speech, tense, singular/plural,
     *    natural English usage, and genuinely valid alternatives.
     * 4. Returns structured result with clear, context-specific Bangla explanation.
     * 5. If network or API fails, falls back gracefully to accepted answers comparison without crashing.
     */
    suspend fun submitPracticeFillBlankAnswer(
        answerText: String,
        customEvaluator: AiAnswerEvaluator? = null
    ): AnswerFeedback? {
        val q = currentQuestion ?: return null
        val qIndex = currentQuestionIndex
        if (q.type != QuestionType.FILL_BLANK) return null

        val currentState = questionStates[qIndex]
        if (currentState?.isLocked == true || currentState?.isAiEvaluating == true || lockedState[qIndex] == true) {
            return null // Prevent duplicate requests and submissions
        }

        val trimmed = answerText.trim()
        userTextAnswers[qIndex] = trimmed

        if (!q.hasConfiguredAnswer) {
            lockedState[qIndex] = true
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

        // Instantly lock question & show AI evaluating state
        questionStates[qIndex] = QuestionAnswerState(
            isAnswered = true,
            selectedOptionIndex = null,
            userTextAnswer = trimmed,
            answerState = AnswerState.EVALUATING,
            isLocked = true,
            isAiEvaluating = true
        )

        // Evaluate with configured Gemini API
        val evaluatorToUse = customEvaluator ?: aiEvaluator
        val aiResult = try {
            evaluatorToUse.evaluateAnswer(
                questionText = q.questionText,
                acceptedAnswers = q.acceptedAnswers,
                userAnswer = trimmed
            )
        } catch (e: Exception) {
            Result.failure(e)
        }

        lockedState[qIndex] = true

        if (aiResult.isSuccess) {
            val evaluation = aiResult.getOrThrow()
            val isExact = AnswerComparison.isAnswerCorrect(trimmed, q.acceptedAnswers)

            if (evaluation.isCorrect) {
                val pointsEarned = q.points
                score += pointsEarned
                streak += 1

                val banglaExpl = evaluation.banglaExplanation.ifBlank {
                    "তোমার উত্তরটি সঠিক। ‘$trimmed’ শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।"
                }

                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = null,
                    userTextAnswer = trimmed,
                    answerState = AnswerState.CORRECT,
                    isLocked = true,
                    isAiEvaluating = false,
                    aiEvaluation = evaluation,
                    banglaExplanation = banglaExpl
                )

                return AnswerFeedback(
                    isCorrect = true,
                    selectedOptionIndex = -1,
                    correctOptionIndex = -1,
                    streak = streak,
                    pointsEarned = pointsEarned,
                    explanation = q.explanation,
                    userTextAnswer = trimmed,
                    correctTextAnswer = q.fillBlankAnswer,
                    isAnswerNotSet = false,
                    banglaExplanation = banglaExpl,
                    isAiEvaluated = true,
                    isAlternativeAccepted = !isExact,
                    aiEvaluation = evaluation
                )
            } else {
                streak = 0
                val banglaExpl = evaluation.banglaExplanation.ifBlank {
                    val correctListStr = if (q.acceptedAnswers.size > 1) {
                        q.acceptedAnswers.joinToString(", ")
                    } else {
                        q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }
                    }
                    "তোমার উত্তরটি এই বাক্যে উপযুক্ত নয়। এখানে সঠিক উত্তর: $correctListStr।"
                }

                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = null,
                    userTextAnswer = trimmed,
                    answerState = AnswerState.INCORRECT,
                    isLocked = true,
                    isAiEvaluating = false,
                    aiEvaluation = evaluation,
                    banglaExplanation = banglaExpl
                )

                return AnswerFeedback(
                    isCorrect = false,
                    selectedOptionIndex = -1,
                    correctOptionIndex = -1,
                    streak = streak,
                    pointsEarned = 0,
                    explanation = q.explanation,
                    userTextAnswer = trimmed,
                    correctTextAnswer = q.fillBlankAnswer,
                    isAnswerNotSet = false,
                    banglaExplanation = banglaExpl,
                    isAiEvaluated = true,
                    isAlternativeAccepted = false,
                    aiEvaluation = evaluation
                )
            }
        } else {
            // Graceful fallback when Gemini API is offline / unreachable / timed out
            val isExact = AnswerComparison.isAnswerCorrect(trimmed, q.acceptedAnswers)
            val correctListStr = if (q.acceptedAnswers.size > 1) {
                q.acceptedAnswers.joinToString(", ")
            } else {
                q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }
            }

            if (isExact) {
                val pointsEarned = q.points
                score += pointsEarned
                streak += 1
                val fallbackExpl = "তোমার উত্তরটি সঠিক। ‘$trimmed’ শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।"

                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = null,
                    userTextAnswer = trimmed,
                    answerState = AnswerState.CORRECT,
                    isLocked = true,
                    isAiEvaluating = false,
                    aiEvaluation = null,
                    banglaExplanation = fallbackExpl
                )

                return AnswerFeedback(
                    isCorrect = true,
                    selectedOptionIndex = -1,
                    correctOptionIndex = -1,
                    streak = streak,
                    pointsEarned = pointsEarned,
                    explanation = q.explanation,
                    userTextAnswer = trimmed,
                    correctTextAnswer = q.fillBlankAnswer,
                    isAnswerNotSet = false,
                    banglaExplanation = fallbackExpl,
                    isAiEvaluated = false,
                    isAlternativeAccepted = false,
                    aiEvaluation = null
                )
            } else {
                streak = 0
                val fallbackExpl = "তোমার উত্তর ‘$trimmed’ এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও grammar অনুযায়ী এখানে সঠিক উত্তর: $correctListStr।"

                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = null,
                    userTextAnswer = trimmed,
                    answerState = AnswerState.INCORRECT,
                    isLocked = true,
                    isAiEvaluating = false,
                    aiEvaluation = null,
                    banglaExplanation = fallbackExpl
                )

                return AnswerFeedback(
                    isCorrect = false,
                    selectedOptionIndex = -1,
                    correctOptionIndex = -1,
                    streak = streak,
                    pointsEarned = 0,
                    explanation = q.explanation,
                    userTextAnswer = trimmed,
                    correctTextAnswer = q.fillBlankAnswer,
                    isAnswerNotSet = false,
                    banglaExplanation = fallbackExpl,
                    isAiEvaluated = false,
                    isAlternativeAccepted = false,
                    aiEvaluation = null
                )
            }
        }
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

    suspend fun submitExam(
        customEvaluator: AiAnswerEvaluator? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): QuizResultSummary {
        isExamSubmitted = true

        var calculatedScore = 0
        var correctCount = 0
        var wrongCount = 0
        var unansweredCount = 0

        val evaluatorToUse = customEvaluator ?: aiEvaluator

        val reviewItems = mutableListOf<QuestionReviewItem>()
        for (index in questions.indices) {
            val q = questions[index]
            val isCorrect: Boolean
            val isAnswered: Boolean
            val userAnswerText: String?
            val correctAnswerText: String
            val userSelected = selectedAnswers[index]

            val isAnswerNotSet = if (q.type == QuestionType.FILL_BLANK) {
                !q.hasConfiguredAnswer
            } else {
                !q.hasConfiguredAnswer || q.correctAnswerIndex !in q.options.indices
            }
            var banglaExpl: String? = null
            var aiEvalResult: AiEvaluationResult? = null

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
                } else if (!isAnsweredByUser) {
                    isAnswered = false
                    isCorrect = false
                    userAnswerText = null
                    correctAnswerText = q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }
                    banglaExpl = "এই প্রশ্নের কোনো উত্তর দেওয়া হয়নি। গ্রহণযোগ্য সঠিক উত্তর: ${q.acceptedAnswers.joinToString(", ")}।"

                    questionStates[index] = QuestionAnswerState(
                        isAnswered = false,
                        selectedOptionIndex = null,
                        userTextAnswer = null,
                        answerState = AnswerState.UNANSWERED,
                        isLocked = true,
                        banglaExplanation = banglaExpl
                    )
                } else {
                    isAnswered = true
                    userAnswerText = userText
                    correctAnswerText = q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }

                    onProgress?.invoke(index + 1, totalQuestions)

                    // Evaluate answered Fill-in-the-Blank with Gemini
                    val aiResult = try {
                        evaluatorToUse.evaluateAnswer(
                            questionText = q.questionText,
                            acceptedAnswers = q.acceptedAnswers,
                            userAnswer = userText!!
                        )
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    if (aiResult.isSuccess) {
                        val eval = aiResult.getOrThrow()
                        aiEvalResult = eval
                        isCorrect = eval.isCorrect
                        banglaExpl = eval.banglaExplanation.ifBlank {
                            if (isCorrect) {
                                "তোমার উত্তরটি সঠিক। ‘$userText’ শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।"
                            } else {
                                "তোমার উত্তরটি এই বাক্যে উপযুক্ত নয়। সঠিক উত্তর: ${q.acceptedAnswers.joinToString(", ")}।"
                            }
                        }
                    } else {
                        // Fallback when AI is unavailable/offline
                        isCorrect = AnswerComparison.isAnswerCorrect(userText!!, q.acceptedAnswers)
                        banglaExpl = if (isCorrect) {
                            "তোমার উত্তরটি সঠিক। ‘$userText’ শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।"
                        } else {
                            "তোমার উত্তর ‘$userText’ এই বাক্যে উপযুক্ত নয়। সঠিক উত্তর: ${q.acceptedAnswers.joinToString(", ")}।"
                        }
                    }

                    questionStates[index] = QuestionAnswerState(
                        isAnswered = true,
                        selectedOptionIndex = null,
                        userTextAnswer = userText,
                        answerState = if (isCorrect) AnswerState.CORRECT else AnswerState.INCORRECT,
                        isLocked = true,
                        aiEvaluation = aiEvalResult,
                        banglaExplanation = banglaExpl
                    )
                }
            } else {
                if (isAnswerNotSet) {
                    isAnswered = userSelected != null
                    isCorrect = false
                    userAnswerText = userSelected?.let { q.options.getOrNull(it) }
                    correctAnswerText = "Answer not configured"

                    questionStates[index] = QuestionAnswerState(
                        isAnswered = isAnswered,
                        selectedOptionIndex = userSelected,
                        userTextAnswer = null,
                        answerState = AnswerState.ANSWER_NOT_SET,
                        isLocked = true
                    )
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

            reviewItems.add(
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
                    isAnswerNotSet = isAnswerNotSet,
                    banglaExplanation = questionStates[index]?.banglaExplanation
                )
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

    /**
     * Suspend variant of submitExam using exact answer matching
     * without making asynchronous network calls (useful for testing or offline mode).
     */
    suspend fun submitExamSync(): QuizResultSummary {
        return submitExam(customEvaluator = object : AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<AiEvaluationResult> {
                val correct = AnswerComparison.isAnswerCorrect(userAnswer, acceptedAnswers)
                return Result.success(
                    AiEvaluationResult(
                        isCorrect = correct,
                        confidence = 1.0,
                        reason = "Synchronous evaluation",
                        banglaExplanation = if (correct) "সঠিক উত্তর" else "ভুল উত্তর",
                        matchedAnswer = acceptedAnswers.firstOrNull() ?: ""
                    )
                )
            }
        })
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
