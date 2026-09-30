package com.example.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.AnswerEvaluationRequest
import com.example.ai.SmartNormalizer
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
    ANSWER_NOT_SET,
    AI_UNAVAILABLE
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
    val explanation: String?,
    val isAiVerified: Boolean = false,
    val wasAnswerCorrected: Boolean = false,
    val originalAnswerDisplay: String? = null,
    val verifiedAnswerDisplay: String? = null,
    val correctionReason: String? = null,
    val verificationConfidence: Double = 0.0,
    val needsReview: Boolean = false
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
                val cleanedAccepted = q.effectiveAcceptedAnswers.filter { it.isNotBlank() }
                val primaryAnswer = q.effectiveFillBlankAnswer.trim().ifEmpty { cleanedAccepted.firstOrNull() ?: "" }
                val allAccepted = mutableListOf<String>()
                if (primaryAnswer.isNotEmpty()) {
                    allAccepted.add(primaryAnswer)
                }
                for (item in cleanedAccepted) {
                    if (item !in allAccepted) {
                        allAccepted.add(item)
                    }
                }

                val origDisplay = q.fillBlankAnswer.ifBlank { q.acceptedAnswers.firstOrNull().orEmpty() }
                val verDisplay = q.effectiveFillBlankAnswer

                ActiveQuestion(
                    id = q.id,
                    type = QuestionType.FILL_BLANK,
                    questionText = q.question,
                    options = emptyList(),
                    correctAnswerIndex = -1,
                    fillBlankAnswer = primaryAnswer,
                    acceptedAnswers = allAccepted,
                    points = q.points,
                    explanation = q.effectiveExplanation,
                    isAiVerified = q.isVerified,
                    wasAnswerCorrected = q.wasAnswerCorrected,
                    originalAnswerDisplay = origDisplay,
                    verifiedAnswerDisplay = verDisplay,
                    correctionReason = q.verificationReason,
                    verificationConfidence = q.verificationConfidence,
                    needsReview = q.needsReview
                )
            } else {
                val optionsSource = q.effectiveOptions
                val targetAnswer = q.effectiveAnswerIndex
                val indexedOptions = optionsSource.mapIndexed { index, text -> index to text }
                val finalIndexedOptions = if (quizSchema.shuffleOptions) {
                    indexedOptions.shuffled()
                } else {
                    indexedOptions
                }
                val finalOptions = finalIndexedOptions.map { it.second }
                val finalCorrectIndex = finalIndexedOptions.indexOfFirst { it.first == targetAnswer }

                val origOptText = q.options.getOrNull(q.answer) ?: "Option ${q.answer + 1}"
                val verOptText = optionsSource.getOrNull(targetAnswer) ?: "Option ${targetAnswer + 1}"

                ActiveQuestion(
                    id = q.id,
                    type = QuestionType.MCQ,
                    questionText = q.question,
                    options = finalOptions,
                    correctAnswerIndex = if (finalCorrectIndex != -1) finalCorrectIndex else -1,
                    fillBlankAnswer = "",
                    acceptedAnswers = emptyList(),
                    points = q.points,
                    explanation = q.effectiveExplanation,
                    isAiVerified = q.isVerified,
                    wasAnswerCorrected = q.wasAnswerCorrected,
                    originalAnswerDisplay = "${(q.answer + 'A'.code).toChar()}. $origOptText",
                    verifiedAnswerDisplay = "${(targetAnswer + 'A'.code).toChar()}. $verOptText",
                    correctionReason = q.verificationReason,
                    verificationConfidence = q.verificationConfidence,
                    needsReview = q.needsReview
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
            com.example.ai.AIManager.recordNonAiEvaluation()

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

        val (isCorrect, matched) = SmartNormalizer.checkExactOrNormalizedMatch(trimmed, q.acceptedAnswers)
        val answerState = if (isCorrect) AnswerState.CORRECT else AnswerState.INCORRECT
        val pointsEarned = if (isCorrect) q.points else 0

        val banglaExpl = if (isCorrect) {
            val matchedWord = matched ?: trimmed
            "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matchedWord” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
        } else {
            val correctListStr = if (q.acceptedAnswers.size > 1) {
                q.acceptedAnswers.joinToString(", ")
            } else {
                q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" }
            }
            "তোমার উত্তর “$trimmed” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: $correctListStr।"
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
     * Practice Mode Fill-in-the-Blank submission using the hardened 6-step evaluation pipeline:
     * STEP 1: Local exact / normalized comparison (HIGHEST PRIORITY - bypasses AI call)
     * STEP 2: Local semantic-safe rules (number/unit/digit equivalence)
     * STEP 3: AI contextual evaluation with complete sentence context
     * STEP 4: Simple, student-friendly Bangla explanation
     * STEP 5: Strict response validation & safety override (AI never overrides exact match)
     * STEP 6: Final result & UI state synchronization
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

        // =========================================================================
        // STEP 1 & STEP 2: Local exact / normalized comparison (HIGHEST PRIORITY)
        // AI must NOT be called when a deterministic exact match already proves the answer is correct.
        // =========================================================================
        val (isExactMatch, matchedAnswer) = SmartNormalizer.checkExactOrNormalizedMatch(trimmed, q.acceptedAnswers)
        if (isExactMatch && matchedAnswer != null) {
            com.example.ai.AIManager.recordNonAiEvaluation()
            lockedState[qIndex] = true
            val pointsEarned = q.points
            score += pointsEarned
            streak += 1

            val isIdentical = trimmed.equals(matchedAnswer, ignoreCase = false)
            val evalType = if (isIdentical) "exact_match" else "normalized_match"
            val banglaExpl = "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matchedAnswer” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
            val localEval = AiEvaluationResult(
                isCorrect = true,
                confidence = 1.0,
                evaluationType = evalType,
                matchedAnswer = matchedAnswer,
                verifiedAnswer = matchedAnswer,
                needsReview = false,
                explanation_bn = banglaExpl,
                reason = "Deterministic exact/normalized match against accepted answer",
                banglaExplanation = banglaExpl,
                decision = "correct",
                jsonAnswerCorrect = true
            )

            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = null,
                userTextAnswer = trimmed,
                answerState = AnswerState.CORRECT,
                isLocked = true,
                isAiEvaluating = false,
                aiEvaluation = localEval,
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
                isAiEvaluated = false,
                isAlternativeAccepted = false,
                aiEvaluation = localEval
            )
        }

        // =========================================================================
        // STEP 3: AI Contextual Evaluation
        // Send full context: question, completeSentence, blankPosition, category, etc.
        // =========================================================================
        questionStates[qIndex] = QuestionAnswerState(
            isAnswered = true,
            selectedOptionIndex = null,
            userTextAnswer = trimmed,
            answerState = AnswerState.EVALUATING,
            isLocked = true,
            isAiEvaluating = true
        )

        val request = AnswerEvaluationRequest(
            questionText = q.questionText,
            acceptedAnswers = q.acceptedAnswers,
            userAnswer = trimmed,
            questionType = QuestionType.FILL_BLANK,
            storedAnswer = q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull().orEmpty() },
            explanation = q.explanation,
            subject = quizSchema.category,
            category = quizSchema.category,
            sourceContext = quizSchema.title + if (quizSchema.description.isNotBlank()) " - ${quizSchema.description}" else "",
            completeSentence = q.questionText,
            blankPosition = com.example.ai.AIPromptBuilder.detectBlankPosition(q.questionText),
            surroundingText = com.example.ai.AIPromptBuilder.extractSurroundingText(q.questionText),
            quizTitle = quizSchema.title
        )

        val evaluatorToUse = customEvaluator ?: aiEvaluator
        val aiResult = try {
            evaluatorToUse.evaluateAnswerWithContext(request)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }

        lockedState[qIndex] = true

        val evaluation = if (aiResult.isSuccess) {
            aiResult.getOrThrow()
        } else {
            // STEP 4/5 Fallback: Graceful local fallback when AI is unavailable / network error
            SmartNormalizer.createLocalEvaluation(
                questionText = q.questionText,
                acceptedAnswers = q.acceptedAnswers,
                userAnswer = trimmed,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
        }

        // STEP 5: Strict response validation & Safety override
        val (safetyMatch, safetyMatchedWord) = SmartNormalizer.checkExactOrNormalizedMatch(trimmed, q.acceptedAnswers)
        val finalIsCorrect = evaluation.isCorrect || safetyMatch
        val finalMatched = if (safetyMatch) safetyMatchedWord else evaluation.matchedAnswer

        if (finalIsCorrect) {
            val pointsEarned = q.points
            score += pointsEarned
            streak += 1

            val isExact = AnswerComparison.isAnswerCorrect(trimmed, q.acceptedAnswers)
            val banglaExpl = evaluation.effectiveExplanationBn.ifBlank {
                "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “${finalMatched ?: trimmed}” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
            }

            val finalEval = evaluation.copy(
                isCorrect = true,
                matchedAnswer = finalMatched ?: evaluation.matchedAnswer,
                explanation_bn = banglaExpl,
                banglaExplanation = banglaExpl
            )

            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = null,
                userTextAnswer = trimmed,
                answerState = AnswerState.CORRECT,
                isLocked = true,
                isAiEvaluating = false,
                aiEvaluation = finalEval,
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
                aiEvaluation = finalEval
            )
        } else {
            val isAiUnavailable = evaluation.evaluationType == "ai_unavailable" || (!aiResult.isSuccess && evaluation.decision == "uncertain")
            if (isAiUnavailable) {
                // Requirement 18: AI Failure must NEVER mean wrong!
                val acceptedListStr = if (q.acceptedAnswers.isNotEmpty()) q.acceptedAnswers.joinToString(", ") else q.fillBlankAnswer
                val unavailableExpl = "AI সার্ভারের সাথে সংযোগ করা সম্ভব হয়নি। তোমার উত্তরটি ভুল হিসেবে চিহ্নিত করা হয়নি (Needs Review)। সম্ভাব্য সঠিক উত্তর: $acceptedListStr।"
                val finalEval = evaluation.copy(
                    isCorrect = false,
                    needsReview = true,
                    evaluationType = "ai_unavailable",
                    explanation_bn = unavailableExpl,
                    banglaExplanation = unavailableExpl
                )
                questionStates[qIndex] = QuestionAnswerState(
                    isAnswered = true,
                    selectedOptionIndex = null,
                    userTextAnswer = trimmed,
                    answerState = AnswerState.AI_UNAVAILABLE,
                    isLocked = true,
                    isAiEvaluating = false,
                    aiEvaluation = finalEval,
                    banglaExplanation = unavailableExpl
                )

                return AnswerFeedback(
                    isCorrect = false,
                    selectedOptionIndex = -1,
                    correctOptionIndex = -1,
                    streak = streak, // Streak is preserved!
                    pointsEarned = 0,
                    explanation = q.explanation,
                    userTextAnswer = trimmed,
                    correctTextAnswer = q.fillBlankAnswer,
                    isAnswerNotSet = false,
                    banglaExplanation = unavailableExpl,
                    isAiEvaluated = false,
                    isAlternativeAccepted = false,
                    aiEvaluation = finalEval
                )
            }

            streak = 0
            val correctListStr = if (q.acceptedAnswers.size > 1) {
                q.acceptedAnswers.joinToString(", ")
            } else {
                q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull().orEmpty() }
            }
            val banglaExpl = evaluation.effectiveExplanationBn.ifBlank {
                "তোমার উত্তর “$trimmed” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: “$correctListStr”。"
            }

            val finalEval = evaluation.copy(
                isCorrect = false,
                explanation_bn = banglaExpl,
                banglaExplanation = banglaExpl
            )

            questionStates[qIndex] = QuestionAnswerState(
                isAnswered = true,
                selectedOptionIndex = null,
                userTextAnswer = trimmed,
                answerState = AnswerState.INCORRECT,
                isLocked = true,
                isAiEvaluating = false,
                aiEvaluation = finalEval,
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
                aiEvaluation = finalEval
            )
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

                    // STEP 1 & 2: Local exact / normalized comparison (Highest Priority)
                    val (isExactMatch, matchedAnswer) = SmartNormalizer.checkExactOrNormalizedMatch(userText, q.acceptedAnswers)
                    if (isExactMatch && matchedAnswer != null) {
                        isCorrect = true
                        val evalType = if (userText.equals(matchedAnswer, ignoreCase = false)) "exact_match" else "normalized_match"
                        banglaExpl = "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matchedAnswer” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
                        aiEvalResult = AiEvaluationResult(
                            isCorrect = true,
                            confidence = 1.0,
                            evaluationType = evalType,
                            matchedAnswer = matchedAnswer,
                            verifiedAnswer = matchedAnswer,
                            needsReview = false,
                            explanation_bn = banglaExpl,
                            reason = "Deterministic exact/normalized match against accepted answer",
                            banglaExplanation = banglaExpl,
                            decision = "correct",
                            jsonAnswerCorrect = true
                        )
                    } else {
                        // STEP 3: Contextual AI Evaluation
                        val request = AnswerEvaluationRequest(
                            questionText = q.questionText,
                            acceptedAnswers = q.acceptedAnswers,
                            userAnswer = userText,
                            questionType = QuestionType.FILL_BLANK,
                            storedAnswer = q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull().orEmpty() },
                            explanation = q.explanation,
                            subject = quizSchema.category,
                            category = quizSchema.category,
                            sourceContext = quizSchema.title + if (quizSchema.description.isNotBlank()) " - ${quizSchema.description}" else "",
                            completeSentence = q.questionText,
                            blankPosition = com.example.ai.AIPromptBuilder.detectBlankPosition(q.questionText),
                            surroundingText = com.example.ai.AIPromptBuilder.extractSurroundingText(q.questionText),
                            quizTitle = quizSchema.title
                        )

                        val aiResult = try {
                            evaluatorToUse.evaluateAnswerWithContext(request)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Result.failure(e)
                        }

                        val eval = if (aiResult.isSuccess) {
                            aiResult.getOrThrow()
                        } else {
                            SmartNormalizer.createLocalEvaluation(
                                questionText = q.questionText,
                                acceptedAnswers = q.acceptedAnswers,
                                userAnswer = userText,
                                offlineNote = true,
                                evaluationType = "ai_unavailable"
                            )
                        }

                        val (safetyMatch, safetyMatchedWord) = SmartNormalizer.checkExactOrNormalizedMatch(userText, q.acceptedAnswers)
                        isCorrect = eval.isCorrect || safetyMatch
                        val chosenMatched = if (safetyMatch) safetyMatchedWord else eval.matchedAnswer

                        banglaExpl = eval.effectiveExplanationBn.ifBlank {
                            if (isCorrect) {
                                "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “${chosenMatched ?: userText}” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
                            } else {
                                val correctListStr = if (q.acceptedAnswers.size > 1) {
                                    q.acceptedAnswers.joinToString(", ")
                                } else {
                                    q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull().orEmpty() }
                                }
                                "তোমার উত্তর “$userText” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: “$correctListStr”。"
                            }
                        }

                        aiEvalResult = eval.copy(
                            isCorrect = isCorrect,
                            matchedAnswer = chosenMatched ?: eval.matchedAnswer,
                            explanation_bn = banglaExpl,
                            banglaExplanation = banglaExpl
                        )
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
                    banglaExplanation = questionStates[index]?.banglaExplanation,
                    questionId = q.id,
                    isAiVerified = q.isAiVerified,
                    wasAnswerCorrected = q.wasAnswerCorrected,
                    originalAnswerDisplay = q.originalAnswerDisplay,
                    verifiedAnswerDisplay = q.verifiedAnswerDisplay,
                    correctionReason = q.correctionReason,
                    verificationConfidence = q.verificationConfidence,
                    needsReview = q.needsReview
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
