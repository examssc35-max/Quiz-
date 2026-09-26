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
import com.example.engine.AiAnswerEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Central AI Manager that acts as an abstraction layer between QuizEngine and AI providers.
 * Manages provider dispatching, memory caching, error resilience, and smart fallback.
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
        AIProviderType.CUSTOM to CustomApiProvider()
    )

    fun getCurrentConfig(): AIConfig {
        return storage?.getConfig() ?: AIConfig()
    }

    fun saveConfig(config: AIConfig) {
        storage?.saveConfig(config)
        // Clear cache when settings change so new evaluation takes effect
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
        val trimmedAnswer = userAnswer.trim()
        val config = getCurrentConfig()

        // Cache key incorporates provider and model to ensure cache correctness
        val cacheKey = generateCacheKey(
            questionText,
            acceptedAnswers,
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
                questionText = questionText,
                acceptedAnswers = acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            return@withContext Result.success(localResult)
        }

        // 2. If no API key is configured for the provider, fall back gracefully
        if (!config.isKeyConfigured && config.providerType != AIProviderType.CUSTOM) {
            Log.w(TAG, "No API key configured for ${config.providerType.displayName}. Falling back to smart normalizer.")
            val localResult = SmartNormalizer.createLocalEvaluation(
                questionText = questionText,
                acceptedAnswers = acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            return@withContext Result.success(localResult)
        }

        // 3. Dispatch to selected AI Provider
        val provider = providers[config.providerType] ?: providers.getValue(AIProviderType.GEMINI)
        val request = AnswerEvaluationRequest(
            questionText = questionText,
            acceptedAnswers = acceptedAnswers,
            userAnswer = trimmedAnswer
        )

        try {
            val providerResult = provider.evaluateAnswer(request, config)

            if (providerResult.isSuccess) {
                val eval = providerResult.getOrThrow()
                evaluationCache[cacheKey] = eval
                Result.success(eval)
            } else {
                val err = providerResult.exceptionOrNull()
                Log.w(TAG, "Provider ${config.providerType.displayName} returned failure: ${err?.message}")
                // Graceful fallback to SmartNormalizer so the quiz continues without interruption
                val fallbackResult = SmartNormalizer.createLocalEvaluation(
                    questionText = questionText,
                    acceptedAnswers = acceptedAnswers,
                    userAnswer = trimmedAnswer,
                    offlineNote = true
                )
                Result.success(fallbackResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AI evaluation with ${config.providerType.displayName}", e)
            val fallbackResult = SmartNormalizer.createLocalEvaluation(
                questionText = questionText,
                acceptedAnswers = acceptedAnswers,
                userAnswer = trimmedAnswer,
                offlineNote = true
            )
            Result.success(fallbackResult)
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
}
