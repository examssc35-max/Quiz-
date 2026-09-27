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
import kotlinx.coroutines.Dispatchers
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

        // In-memory cache across questions and sessions
        private val evaluationCache = ConcurrentHashMap<String, AiEvaluationResult>()

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
    }

    fun resetConfig() {
        storage?.resetConfig()
        evaluationCache.clear()
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

    suspend fun evaluateDetailed(request: AnswerEvaluationRequest): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val trimmedAnswer = request.userAnswer.trim()
        val config = getCurrentConfig()

        val cacheKey = generateCacheKey(
            request.questionText,
            request.acceptedAnswers,
            trimmedAnswer,
            config.providerType,
            config.effectiveModel
        )

        evaluationCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached evaluation for: $trimmedAnswer")
            return@withContext Result.success(cached)
        }

        // 1. If AI is toggled OFF in settings, evaluate via smart local normalization
        if (!config.enabled) {
            Log.d(TAG, "AI is disabled by user settings. Using smart local evaluation.")
            val localResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = request.acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            return@withContext Result.success(localResult)
        }

        // 2. If no API key is configured for the provider, fall back gracefully
        if (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM) {
            Log.w(TAG, "No API key configured for ${config.providerType.displayName}. Falling back to smart normalizer.")
            val localResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = request.acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            return@withContext Result.success(localResult)
        }

        // 3. Dispatch to selected AI Provider
        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)

        try {
            val providerResult = provider.evaluateAnswer(request, config)

            if (providerResult.isSuccess) {
                val eval = providerResult.getOrThrow()
                evaluationCache[cacheKey] = eval
                Result.success(eval)
            } else {
                val err = providerResult.exceptionOrNull()
                Log.w(TAG, "Provider ${config.providerType.displayName} returned failure: ${err?.message}")
                val fallbackResult = SmartNormalizer.createLocalEvaluation(
                    questionText = request.questionText,
                    acceptedAnswers = request.acceptedAnswers,
                    userAnswer = trimmedAnswer,
                    offlineNote = true
                )
                Result.success(fallbackResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AI evaluation with ${config.providerType.displayName}", e)
            val fallbackResult = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = request.acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            Result.success(fallbackResult)
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
            val res = provider.analyzeQuizResult(summary, config)
            if (res.isSuccess) res else Result.success(generateLocalResultAnalysis(summary))
        } catch (e: Exception) {
            Result.success(generateLocalResultAnalysis(summary))
        }
    }

    suspend fun auditQuestion(question: QuestionSchema): Result<QuestionAuditResult> = withContext(Dispatchers.IO) {
        val config = getCurrentConfig()

        if (!config.enabled || (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM)) {
            return@withContext Result.success(
                QuestionAuditResult(
                    questionId = question.id,
                    isSuspicious = false,
                    issueDescription = null,
                    suggestedCorrectAnswer = null,
                    suggestedCorrectIndex = null,
                    confidence = 0.5
                )
            )
        }

        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        try {
            provider.auditQuestion(question, config)
        } catch (e: Exception) {
            Result.failure(e)
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
        model: String
    ): String {
        return "${providerType.id}::$model::${questionText.trim().lowercase()}||${acceptedAnswers.joinToString(",").lowercase()}||${userAnswer.trim().lowercase()}"
    }

    private fun generateLocalChatFallback(context: QuestionAiContext, userQuery: String): String {
        val stored = context.storedAnswer.ifBlank { context.acceptedAnswers.firstOrNull().orEmpty() }
        val userAns = context.userAnswerText ?: context.options.getOrNull(context.userOptionIndex ?: -1).orEmpty()

        return when {
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
