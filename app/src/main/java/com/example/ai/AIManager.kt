package com.example.ai

import android.content.Context
import android.util.Log
import com.example.ai.providers.AnthropicProvider
import com.example.ai.providers.CustomApiProvider
import com.example.ai.providers.GeminiProvider
import com.example.ai.providers.OpenAICompatibleProvider
import com.example.ai.providers.OpenAIProvider
import com.example.ai.providers.OpenRouterProvider
import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizResultSummary
import com.example.engine.AiAnswerEvaluator
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Central AI Manager that acts as an abstraction layer between QuizEngine and AI providers.
 * Manages provider dispatching, memory caching, error resilience, and conversational agent features.
 */
class AIManager(
    private val storage: AISettingsStorage? = null
) : AiAnswerEvaluator {

    companion object {
        private const val TAG = "AIManager"
        private const val PROMPT_VERSION = "v2_cloud_smart"

        // In-memory cache across questions and sessions
        private val evaluationCache = ConcurrentHashMap<String, AiEvaluationResult>()
        private val auditCache = ConcurrentHashMap<String, QuestionAuditResult>()

        // In-flight request deduplication map to prevent multiple identical requests
        private val inFlightEvaluations = ConcurrentHashMap<String, Deferred<Result<AiEvaluationResult>>>()

        // Observable AI Usage Stats (Requirement 26)
        private val _usageStats = MutableStateFlow(AiUsageStats())
        val usageStats: StateFlow<AiUsageStats> = _usageStats.asStateFlow()

        fun recordNonAiEvaluation(count: Int = 1) {
            _usageStats.update {
                it.copy(
                    questionsEvaluatedWithoutAi = it.questionsEvaluatedWithoutAi + count,
                    aiRequestsSaved = it.aiRequestsSaved + count
                )
            }
        }

        fun recordCachedEvaluation(count: Int = 1) {
            _usageStats.update {
                it.copy(
                    cachedEvaluations = it.cachedEvaluations + count,
                    aiRequestsSaved = it.aiRequestsSaved + count
                )
            }
        }

        fun recordAiRequest(count: Int = 1) {
            _usageStats.update {
                it.copy(
                    aiRequestsThisSession = it.aiRequestsThisSession + count
                )
            }
        }

        @Volatile
        private var INSTANCE: AIManager? = null

        fun getInstance(context: Context): AIManager {
            return INSTANCE ?: synchronized(this) {
                val storage = AISettingsStorage.getInstance(context)
                val manager = AIManager(storage)
                INSTANCE = manager
                manager
            }
        }

        val defaultManager: AIManager by lazy {
            AIManager(null)
        }
    }

    private val providers: Map<AIProviderType, AIProvider> = mapOf(
        AIProviderType.GEMINI to GeminiProvider(),
        AIProviderType.OPENAI to OpenAIProvider(),
        AIProviderType.ANTHROPIC to AnthropicProvider(),
        AIProviderType.OPENROUTER to OpenRouterProvider(),
        AIProviderType.OPENAI_COMPATIBLE to OpenAICompatibleProvider(),
        AIProviderType.HUGGING_FACE to OpenAICompatibleProvider(),
        AIProviderType.CUSTOM to CustomApiProvider()
    )

    fun getCurrentConfig(): AIConfig {
        return storage?.getConfig() ?: AIConfig()
    }

    fun saveConfig(config: AIConfig) {
        storage?.saveConfig(config)
        evaluationCache.clear()
        auditCache.clear()
    }

    fun resetConfig() {
        storage?.resetConfig()
        evaluationCache.clear()
        auditCache.clear()
    }

    override suspend fun evaluateAnswer(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val request = AnswerEvaluationRequest(
            questionText = questionText,
            acceptedAnswers = acceptedAnswers,
            userAnswer = userAnswer,
            questionType = QuestionType.FILL_BLANK
        )
        evaluateDetailed(request)
    }

    override suspend fun evaluateAnswerWithContext(
        request: AnswerEvaluationRequest
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        evaluateDetailed(request)
    }

    suspend fun evaluateDetailed(request: AnswerEvaluationRequest): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val trimmedAnswer = request.userAnswer.trim()
        val allAccepted = request.acceptedAnswers.filter { it.isNotBlank() }.ifEmpty {
            if (request.storedAnswer.isNotBlank()) listOf(request.storedAnswer) else emptyList()
        }

        // =========================================================================
        // STEP 1 & STEP 2: Local exact / normalized comparison & semantic-safe rules
        // Deterministic exact match has HIGHEST PRIORITY. AI must NOT be called.
        // =========================================================================
        val (isExactOrNorm, matched) = SmartNormalizer.checkExactOrNormalizedMatch(trimmedAnswer, allAccepted)
        if (isExactOrNorm && matched != null) {
            recordNonAiEvaluation()
            val evalType = if (trimmedAnswer.equals(matched, ignoreCase = false)) "exact_match" else "normalized_match"
            val expl = "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matched” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
            val exactResult = AiEvaluationResult(
                isCorrect = true,
                confidence = 1.0,
                evaluationType = evalType,
                matchedAnswer = matched,
                verifiedAnswer = matched,
                needsReview = false,
                explanation_bn = expl,
                reason = "Deterministic exact/normalized match against verified accepted answer",
                banglaExplanation = expl,
                decision = "correct",
                jsonAnswerCorrect = true
            )
            Log.d(TAG, "Step 1 exact/normalized match succeeded for: '$trimmedAnswer' -> '$matched'. Skipping AI call.")
            return@withContext Result.success(exactResult)
        }

        val config = getCurrentConfig()

        val cacheKey = generateCacheKey(
            questionText = request.questionText,
            acceptedAnswers = allAccepted,
            userAnswer = trimmedAnswer,
            providerType = config.providerType,
            model = config.effectiveModel,
            questionType = request.questionType,
            storedAnswer = request.storedAnswer,
            options = request.options
        )

        // =========================================================================
        // STEP 3: Smart AI Cache (Requirement 13)
        // =========================================================================
        evaluationCache[cacheKey]?.let { cached ->
            recordCachedEvaluation()
            Log.d(TAG, "Returning cached evaluation for: $trimmedAnswer")
            return@withContext Result.success(cached)
        }

        // 1. If AI is toggled OFF in settings, evaluate via smart local normalization
        if (!config.enabled) {
            recordNonAiEvaluation()
            Log.d(TAG, "AI is disabled by user settings. Using smart local evaluation.")
            val localResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = allAccepted,
                userAnswer = trimmedAnswer,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
            return@withContext Result.success(localResult)
        }

        // 2. If no API key is configured for the provider, fall back gracefully
        if (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM) {
            recordNonAiEvaluation()
            Log.w(TAG, "No API key configured for ${config.providerType.displayName}. Falling back to smart normalizer.")
            val localResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = allAccepted,
                userAnswer = trimmedAnswer,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
            return@withContext Result.success(localResult)
        }

        // =========================================================================
        // STEP 4: In-flight Request Deduplication (Requirement 20)
        // =========================================================================
        val inFlight = inFlightEvaluations[cacheKey]
        if (inFlight != null) {
            Log.d(TAG, "Reusing in-flight AI evaluation for: $trimmedAnswer")
            return@withContext inFlight.await()
        }

        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)

        // =========================================================================
        // STEP 5: Controlled Retries & Real Cloud AI Request (Requirements 18 & 19)
        // =========================================================================
        val deferred = async(Dispatchers.IO) {
            recordAiRequest()
            var attempts = 0
            val maxRetries = 2
            var lastError: Throwable? = null

            while (attempts <= maxRetries) {
                try {
                    val providerResult = provider.evaluateAnswer(request, config)
                    if (providerResult.isSuccess) {
                        val rawEval = providerResult.getOrThrow()
                        val validated = AIPromptBuilder.validateAndHardenEvaluation(
                            parsedResult = rawEval,
                            userAnswer = trimmedAnswer,
                            acceptedAnswers = allAccepted,
                            fallbackAnswer = request.storedAnswer
                        )
                        evaluationCache[cacheKey] = validated
                        return@async Result.success(validated)
                    } else {
                        val err = providerResult.exceptionOrNull()
                        lastError = err
                        val msg = err?.message.orEmpty()
                        if (msg.contains("400") || msg.contains("401") || msg.contains("403") || msg.contains("404")) {
                            break // Permanent error, no retry
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    lastError = e
                }
                attempts++
                if (attempts <= maxRetries) {
                    val delayMs = 500L * (1 shl (attempts - 1))
                    delay(delayMs)
                }
            }

            // Failure handling (Requirement 18: AI Failure must NEVER mean wrong)
            Log.w(TAG, "AI evaluation failed after $attempts attempt(s): ${lastError?.message}")
            val fallbackResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = allAccepted,
                userAnswer = trimmedAnswer,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
            Result.success(fallbackResult)
        }

        inFlightEvaluations[cacheKey] = deferred
        try {
            deferred.await()
        } finally {
            inFlightEvaluations.remove(cacheKey)
        }
    }

    suspend fun chatFollowUp(
        context: QuestionAiContext,
        history: List<ChatMessage>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getCurrentConfig()

        if (!config.enabled || (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM)) {
            val localAnswer = generateLocalChatFallback(context, userMessage)
            return@withContext Result.success(localAnswer)
        }

        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        try {
            recordAiRequest()
            val result = provider.chatFollowUp(context, history, userMessage, config)
            if (result.isSuccess) {
                result
            } else {
                Result.success(generateLocalChatFallback(context, userMessage))
            }
        } catch (e: Exception) {
            Result.success(generateLocalChatFallback(context, userMessage))
        }
    }

    suspend fun analyzeQuizResult(summary: QuizResultSummary): Result<AiResultAnalysis> = withContext(Dispatchers.IO) {
        val config = getCurrentConfig()

        if (!config.enabled || (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM)) {
            return@withContext Result.success(generateLocalResultAnalysis(summary))
        }

        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        try {
            recordAiRequest()
            val res = provider.analyzeQuizResult(summary, config)
            if (res.isSuccess) res else Result.success(generateLocalResultAnalysis(summary))
        } catch (e: Exception) {
            Result.success(generateLocalResultAnalysis(summary))
        }
    }

    suspend fun auditQuestion(
        question: QuestionSchema,
        contextHint: String = ""
    ): Result<QuestionAuditResult> = withContext(Dispatchers.IO) {
        val config = getCurrentConfig()

        val cacheKey = "${config.providerType.id}::${config.effectiveModel}::${question.type}::${question.question.trim()}::${question.options.joinToString("||")}::${question.answer}::${question.fillBlankAnswer}"
        auditCache[cacheKey]?.let { cached ->
            recordCachedEvaluation()
            Log.d(TAG, "Returning cached question audit for: ${question.id}")
            return@withContext Result.success(cached)
        }

        // Check locally first (Requirement 2 & 24)
        val (isSuspicious, issue) = AIPromptBuilder.isQuestionSuspiciousLocally(question)
        if (!isSuspicious) {
            recordNonAiEvaluation()
            val localClean = AIPromptBuilder.generateLocalDeterministicAudit(question)
            return@withContext Result.success(localClean)
        }

        if (!config.enabled || (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM)) {
            recordNonAiEvaluation()
            val localResult = generateLocalQuestionAudit(question)
            return@withContext Result.success(localResult)
        }

        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        try {
            recordAiRequest()
            val providerResult = provider.auditQuestion(question, config)
            if (providerResult.isSuccess) {
                val auditRes = providerResult.getOrThrow()
                auditCache[cacheKey] = auditRes
                Result.success(auditRes)
            } else {
                Log.w(TAG, "AI question audit returned failure: ${providerResult.exceptionOrNull()?.message}")
                val localFallback = generateLocalQuestionAudit(question)
                Result.success(localFallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during question audit: ${e.message}", e)
            val localFallback = generateLocalQuestionAudit(question)
            Result.success(localFallback)
        }
    }

    /**
     * Requirement 2, 12, 24: "Smart AI Verify"
     * 1. Inspects quiz deterministically first.
     * 2. Identifies suspicious/uncertain questions.
     * 3. Skips clean questions that require NO AI.
     * 4. Batches suspicious questions to Real Cloud AI.
     * 5. Applies high-confidence corrections (>= 0.90).
     * 6. Produces rich audit summary with counts.
     */
    suspend fun auditQuiz(
        quiz: com.example.data.model.QuizSchema,
        onProgress: (verifiedCount: Int, totalCount: Int, currentQuestionText: String) -> Unit = { _, _, _ -> }
    ): Result<Pair<com.example.data.model.QuizSchema, QuizAuditSummary>> = withContext(Dispatchers.IO) {
        val total = quiz.questions.size
        if (total == 0) {
            return@withContext Result.success(Pair(quiz, QuizAuditSummary(quiz.title.hashCode().toString(), 0, 0, 0)))
        }

        val config = getCurrentConfig()

        // 1. Deterministic Local Inspection: Separate clean questions from suspicious ones
        val cleanResults = mutableMapOf<String, QuestionAuditResult>()
        val suspiciousQuestions = mutableListOf<QuestionSchema>()

        quiz.questions.forEach { q ->
            val (isSuspicious, _) = AIPromptBuilder.isQuestionSuspiciousLocally(q)
            if (!isSuspicious) {
                cleanResults[q.id] = AIPromptBuilder.generateLocalDeterministicAudit(q)
            } else {
                suspiciousQuestions.add(q)
            }
        }

        recordNonAiEvaluation(cleanResults.size)
        var auditedSoFar = cleanResults.size
        onProgress(auditedSoFar, total, if (suspiciousQuestions.isEmpty()) "All questions verified locally!" else "Inspecting uncertain questions...")

        // 2. Batch Cloud AI Requests for Suspicious Questions
        val suspiciousResults = mutableMapOf<String, QuestionAuditResult>()

        if (suspiciousQuestions.isNotEmpty()) {
            if (config.enabled && (config.isKeyConfigured || config.providerType == AIProviderType.CUSTOM)) {
                val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
                val batchSize = 5
                val batches = suspiciousQuestions.chunked(batchSize)

                for (batch in batches) {
                    recordAiRequest()
                    val batchRes = try {
                        provider.auditQuestionsBatch(batch, config).getOrElse {
                            batch.map { q -> generateLocalQuestionAudit(q) }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        batch.map { q -> generateLocalQuestionAudit(q) }
                    }

                    batchRes.forEach { auditRes ->
                        suspiciousResults[auditRes.questionId] = auditRes
                    }
                    auditedSoFar += batch.size
                    onProgress(auditedSoFar.coerceAtMost(total), total, batch.firstOrNull()?.question.orEmpty())
                }
            } else {
                suspiciousQuestions.forEach { q ->
                    suspiciousResults[q.id] = generateLocalQuestionAudit(q)
                    auditedSoFar++
                    onProgress(auditedSoFar.coerceAtMost(total), total, q.question)
                }
            }
        }

        // 3. Assemble Audited Quiz & Summary
        val auditedQuestions = mutableListOf<QuestionSchema>()
        val records = mutableListOf<QuestionAuditRecord>()
        var correctedCount = 0
        var needsReviewCount = 0

        quiz.questions.forEach { question ->
            val auditRes = suspiciousResults[question.id] ?: cleanResults[question.id] ?: generateLocalQuestionAudit(question)

            if (auditRes.needsReview) {
                needsReviewCount++
            }

            val origDisplay = if (question.type == QuestionType.MCQ) {
                val optText = question.options.getOrNull(question.answer) ?: "Index ${question.answer}"
                "${(question.answer + 'A'.code).toChar()}. $optText"
            } else {
                question.fillBlankAnswer.ifBlank { question.acceptedAnswers.firstOrNull().orEmpty() }
            }

            val isChanged = auditRes.answerChanged && (
                (question.type == QuestionType.MCQ && auditRes.verifiedAnswerIndex != null && auditRes.verifiedAnswerIndex != question.answer) ||
                (question.type == QuestionType.FILL_BLANK && !auditRes.verifiedAnswerText.isNullOrBlank() && !auditRes.verifiedAnswerText.equals(question.fillBlankAnswer, ignoreCase = true))
            )

            if (isChanged) {
                correctedCount++
            }

            val corrDisplay = if (question.type == QuestionType.MCQ) {
                val idx = auditRes.verifiedAnswerIndex ?: question.answer
                val optText = question.options.getOrNull(idx) ?: "Index $idx"
                "${(idx + 'A'.code).toChar()}. $optText"
            } else {
                auditRes.verifiedAnswerText ?: origDisplay
            }

            records.add(
                QuestionAuditRecord(
                    questionId = question.id,
                    questionText = question.question,
                    originalAnswer = origDisplay,
                    correctedAnswer = corrDisplay,
                    changed = isChanged,
                    reason = auditRes.reason.ifBlank { if (isChanged) "AI verified and corrected answer" else "Answer confirmed accurate" },
                    confidence = auditRes.confidence,
                    needsReview = auditRes.needsReview,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Apply verified data to question
            val updatedQuestion = if (isChanged) {
                if (question.type == QuestionType.MCQ) {
                    question.copy(
                        verifiedAnswerIndex = auditRes.verifiedAnswerIndex,
                        isVerified = true,
                        verificationConfidence = auditRes.confidence,
                        verificationReason = auditRes.reason,
                        needsReview = auditRes.needsReview,
                        verifiedExplanation = auditRes.correctedExplanation ?: question.explanation,
                        lastVerifiedAt = System.currentTimeMillis(),
                        correctedOptions = if (auditRes.correctedOptions.isNotEmpty()) auditRes.correctedOptions else question.options
                    )
                } else {
                    question.copy(
                        verifiedFillBlankAnswer = auditRes.verifiedAnswerText,
                        verifiedAcceptedAnswers = if (auditRes.acceptedAnswers.isNotEmpty()) auditRes.acceptedAnswers else question.acceptedAnswers,
                        isVerified = true,
                        verificationConfidence = auditRes.confidence,
                        verificationReason = auditRes.reason,
                        needsReview = auditRes.needsReview,
                        verifiedExplanation = auditRes.correctedExplanation ?: question.explanation,
                        lastVerifiedAt = System.currentTimeMillis()
                    )
                }
            } else {
                question.copy(
                    isVerified = true,
                    verificationConfidence = auditRes.confidence,
                    verificationReason = auditRes.reason,
                    needsReview = auditRes.needsReview,
                    lastVerifiedAt = System.currentTimeMillis()
                )
            }

            auditedQuestions.add(updatedQuestion)
        }

        val updatedSchema = quiz.copy(questions = auditedQuestions)
        val summary = QuizAuditSummary(
            quizId = quiz.title.hashCode().toString(),
            totalQuestionsAudited = total,
            correctedCount = correctedCount,
            needsReviewCount = needsReviewCount,
            auditRecords = records,
            auditedAt = System.currentTimeMillis(),
            requiredNoAiCount = cleanResults.size,
            verifiedByAiCount = suspiciousQuestions.size
        )

        Result.success(Pair(updatedSchema, summary))
    }

    fun generateLocalQuestionAudit(question: QuestionSchema): QuestionAuditResult {
        if (question.type == QuestionType.MCQ) {
            val isOutOfBounds = question.answer !in question.options.indices
            val origText = question.options.getOrNull(question.answer).orEmpty()
            return QuestionAuditResult(
                questionId = question.id,
                isValid = !isOutOfBounds,
                needsReview = isOutOfBounds,
                questionText = question.question,
                questionType = QuestionType.MCQ,
                originalAnswerIndex = question.answer,
                verifiedAnswerIndex = if (!isOutOfBounds) question.answer else null,
                originalAnswerText = origText,
                verifiedAnswerText = origText,
                acceptedAnswers = emptyList(),
                answerChanged = false,
                confidence = 0.85,
                reason = if (isOutOfBounds) "MCQ answer index ${question.answer} is out of options range." else "AI verification unavailable. Original quiz answer preserved.",
                correctedOptions = emptyList(),
                correctedExplanation = question.explanation
            )
        } else {
            val isEmpty = question.fillBlankAnswer.isBlank() && question.acceptedAnswers.isEmpty()
            val text = question.fillBlankAnswer.ifBlank { question.acceptedAnswers.firstOrNull().orEmpty() }
            return QuestionAuditResult(
                questionId = question.id,
                isValid = !isEmpty,
                needsReview = isEmpty,
                questionText = question.question,
                questionType = QuestionType.FILL_BLANK,
                originalAnswerIndex = null,
                verifiedAnswerIndex = null,
                originalAnswerText = text,
                verifiedAnswerText = text,
                acceptedAnswers = question.acceptedAnswers,
                answerChanged = false,
                confidence = 0.85,
                reason = if (isEmpty) "Fill-blank answer is not configured." else "AI verification unavailable. Original quiz answer preserved.",
                correctedOptions = emptyList(),
                correctedExplanation = question.explanation
            )
        }
    }

    suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        try {
            provider.testConnection(config)
        } catch (e: Exception) {
            Result.failure(Exception("Connection test failed: ${e.message.orEmpty()}", e))
        }
    }

    private fun generateCacheKey(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String,
        providerType: AIProviderType,
        model: String,
        questionType: QuestionType = QuestionType.FILL_BLANK,
        storedAnswer: String = "",
        options: List<String> = emptyList()
    ): String {
        val optKey = if (options.isNotEmpty()) options.joinToString("||") else ""
        return "$PROMPT_VERSION::${providerType.id}::$model::${questionType.name}::${storedAnswer.trim().lowercase()}::${questionText.trim().lowercase()}||${acceptedAnswers.joinToString(",").lowercase()}||${optKey.lowercase()}||${userAnswer.trim().lowercase()}"
    }

    private fun generateLocalChatFallback(context: QuestionAiContext, userQuery: String): String {
        val stored = context.storedAnswer.ifBlank { context.acceptedAnswers.firstOrNull().orEmpty() }
        val userAns = context.userAnswerText ?: context.options.getOrNull(context.userOptionIndex ?: -1).orEmpty()

        return when {
            context.wasCorrected && (userQuery.contains("কেন পরিবর্তন", ignoreCase = true) || userQuery.contains("correct", ignoreCase = true) || userQuery.contains("পরিবর্তন", ignoreCase = true)) -> {
                "এই প্রশ্নটিতে কুইজের সংরক্ষিত মূল উত্তর ছিল ‘${context.originalAnswerDisplay.orEmpty()}’। কিন্তু এআই যাচাইকরণ নিশ্চিত করেছে যে সঠিক উত্তর হওয়া উচিত ‘${context.verifiedAnswerDisplay.orEmpty()}’। কারণ: ${context.correctionReason ?: "মূল উত্তরটি প্রশ্নের সাথে সঙ্গতিপূর্ণ ছিল না।"}"
            }
            userQuery.contains("নিশ্চিত", ignoreCase = true) || userQuery.contains("sure", ignoreCase = true) -> {
                "হ্যাঁ, প্রশ্ন ‘${context.questionText}’-এর তথ্য ও ব্যাকরণগত নিয়মাবলি পুনরায় বিশ্লেষণ করা হয়েছে। বর্তমান যাচাইকৃত উত্তর ‘${if (context.wasCorrected) context.verifiedAnswerDisplay else stored}’ সর্বাধিক নির্ভরযোগ্য।"
            }
            userQuery.contains("সহজ", ignoreCase = true) || userQuery.contains("বুঝাও", ignoreCase = true) -> {
                "এই প্রশ্নটিতে ‘${context.questionText}’ জানতে চাওয়া হয়েছে। এখানে সঠিক উত্তর হলো ‘$stored’। এটি বাক্যের অর্থ ও প্রাসঙ্গিক নিয়মানুযায়ী উপযুক্ত।"
            }
            userQuery.contains("ভুল", ignoreCase = true) && userAns.isNotBlank() -> {
                "তোমার দেওয়া উত্তর ছিল ‘$userAns’, কিন্তু প্রশ্নের সঠিক তথ্য বা ব্যাকরণ অনুযায়ী ‘$stored’ উত্তরটি বেশি গ্রহণযোগ্য।"
            }
            userQuery.contains("সঠিক", ignoreCase = true) -> {
                "এই প্রশ্নের সঠিক উত্তর হলো ‘$stored’।" + (if (context.explanation != null) " কারণ: ${context.explanation}" else "")
            }
            userQuery.contains("JSON", ignoreCase = true) -> {
                "কুইজ ফাইলে সংরক্ষিত উত্তরটি হলো: ‘$stored’। যদি এটি ভুল বা অসম্পূর্ণ মনে হয়, তবে কুইজ এডিটরে এটি পরিবর্তন করা সম্ভব।"
            }
            else -> {
                "প্রশ্ন: ${context.questionText}\nসংরক্ষিত সঠিক উত্তর: ‘$stored’" + (if (context.explanation != null) "\nব্যাখ্যা: ${context.explanation}" else "")
            }
        }
    }

    private fun generateLocalResultAnalysis(summary: QuizResultSummary): AiResultAnalysis {
        val accuracy = summary.accuracy
        val overall = when {
            accuracy >= 80f -> "চমৎকার পারফরম্যান্স! অধিকাংশ প্রশ্নের উত্তরই নির্ভুল হয়েছে।"
            accuracy >= 50f -> "ভালো চেষ্টা! আরও কিছু বিষয়ে পুনরাবৃত্তি করলে স্কোর আরও উন্নত হবে।"
            else -> "অনুশীলন চালিয়ে যাও! ভুল হওয়া প্রশ্নগুলো পুনরায় পর্যালোচনা করার পরামর্শ দেওয়া হচ্ছে।"
        }

        val strengths = if (summary.correctCount > 0) {
            listOf("সঠিক উত্তরের ধারাবাহিকতা", "কুইজে সক্রিয় অংশগ্রহণ")
        } else {
            listOf("কুইজ সম্পন্ন করার প্রচেষ্টা")
        }

        val weak = if (summary.wrongCount > 0) {
            listOf("ভুল উত্তর হওয়া প্রশ্নসমূহের মূল তত্ত্ব")
        } else emptyList()

        return AiResultAnalysis(
            overallSummary = overall,
            strengths = strengths,
            weakAreas = weak,
            recommendations = listOf("ভুল হওয়া প্রশ্নগুলো পুনরায় পরীক্ষা করুন", "প্রশ্নগুলো ভালোভাবে পড়ে উত্তর নির্বাচন করুন"),
            mistakeBreakdown = if (summary.wrongCount > 0) "মোট ${summary.wrongCount}টি প্রশ্নের উত্তর ভুল হয়েছে। সঠিক উত্তরগুলো লক্ষ্য করে প্রস্তুতি নিন।" else "কোনো প্রশ্ন ভুল হয়নি!"
        )
    }
}
