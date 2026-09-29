package com.example.ai

import com.example.BuildConfig
import com.example.data.model.QuestionType

enum class AIProviderType(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val requiresBaseUrl: Boolean,
    val popularModels: List<String>
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        requiresBaseUrl = false,
        popularModels = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.1-pro-preview")
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.openai.com/v1/",
        requiresBaseUrl = false,
        popularModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-haiku-20241022",
        defaultBaseUrl = "https://api.anthropic.com/v1/",
        requiresBaseUrl = false,
        popularModels = listOf("claude-3-5-haiku-20241022", "claude-3-5-sonnet-20241022")
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultModel = "google/gemini-2.5-flash",
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        requiresBaseUrl = false,
        popularModels = listOf(
            "google/gemini-2.5-flash",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-haiku",
            "meta-llama/llama-3.3-70b-instruct"
        )
    ),
    OPENAI_COMPATIBLE(
        id = "openai_compatible",
        displayName = "OpenAI Compatible",
        defaultModel = "openai/gpt-oss-120b:groq",
        defaultBaseUrl = "https://router.huggingface.co/v1",
        requiresBaseUrl = true,
        popularModels = listOf(
            "openai/gpt-oss-120b:groq",
            "meta-llama/Llama-3.3-70B-Instruct",
            "mistralai/Mistral-7B-Instruct-v0.3",
            "llama-3.3-70b-versatile",
            "gpt-4o-mini"
        )
    ),
    HUGGING_FACE(
        id = "huggingface",
        displayName = "Hugging Face",
        defaultModel = "openai/gpt-oss-120b:groq",
        defaultBaseUrl = "https://router.huggingface.co/v1",
        requiresBaseUrl = true,
        popularModels = listOf(
            "openai/gpt-oss-120b:groq",
            "meta-llama/Llama-3.3-70B-Instruct",
            "Qwen/Qwen2.5-72B-Instruct",
            "mistralai/Mistral-7B-Instruct-v0.3"
        )
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Endpoint",
        defaultModel = "custom-model",
        defaultBaseUrl = "",
        requiresBaseUrl = true,
        popularModels = emptyList()
    );

    companion object {
        fun fromId(id: String?): AIProviderType {
            val clean = id?.trim()?.lowercase() ?: ""
            if (clean == "huggingface" || clean == "hf") return HUGGING_FACE
            if (clean == "openai_compatible" || clean == "openai-compatible") return OPENAI_COMPATIBLE
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) } ?: GEMINI
        }
    }
}

data class AIConfig(
    val enabled: Boolean = true,
    val providerType: AIProviderType = AIProviderType.GEMINI,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.1f,
    val customHeaders: Map<String, String> = emptyMap()
) {
    val effectiveModel: String
        get() = model.trim().ifEmpty { providerType.defaultModel }

    val effectiveBaseUrl: String
        get() = baseUrl.trim().ifEmpty { providerType.defaultBaseUrl }

    /**
     * Resolves the API key to use. If custom API key is not entered and Gemini is selected,
     * checks if BuildConfig.GEMINI_API_KEY is available and configured.
     */
    val effectiveApiKey: String
        get() {
            val key = apiKey.trim()
            if (key.isNotEmpty()) return key
            if (providerType == AIProviderType.GEMINI) {
                val buildConfigKey = BuildConfig.GEMINI_API_KEY
                if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
                    return buildConfigKey
                }
            }
            return ""
        }

    val isKeyConfigured: Boolean
        get() = effectiveApiKey.isNotBlank()
}

data class AnswerEvaluationRequest(
    val questionText: String,
    val acceptedAnswers: List<String>,
    val userAnswer: String,
    val languageHint: String? = null,
    val questionType: QuestionType = QuestionType.FILL_BLANK,
    val options: List<String> = emptyList(),
    val storedCorrectOptionIndex: Int = -1,
    val storedAnswer: String = acceptedAnswers.firstOrNull().orEmpty(),
    val explanation: String? = null
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuestionAiContext(
    val questionId: String = "",
    val questionText: String,
    val type: QuestionType,
    val options: List<String> = emptyList(),
    val storedAnswer: String = "",
    val acceptedAnswers: List<String> = emptyList(),
    val storedCorrectOptionIndex: Int = -1,
    val userAnswerText: String? = null,
    val userOptionIndex: Int? = null,
    val explanation: String? = null,
    val quizTitle: String = "",
    val wasCorrected: Boolean = false,
    val originalAnswerDisplay: String? = null,
    val verifiedAnswerDisplay: String? = null,
    val correctionReason: String? = null,
    val auditConfidence: Double? = null
)

data class AiResultAnalysis(
    val overallSummary: String,
    val strengths: List<String>,
    val weakAreas: List<String>,
    val recommendations: List<String>,
    val mistakeBreakdown: String
)

data class QuestionAuditResult(
    val questionId: String,
    val isValid: Boolean = true,
    val needsReview: Boolean = false,
    val questionText: String = "",
    val questionType: QuestionType = QuestionType.MCQ,
    val originalAnswerIndex: Int? = null,
    val verifiedAnswerIndex: Int? = null,
    val originalAnswerText: String? = null,
    val verifiedAnswerText: String? = null,
    val acceptedAnswers: List<String> = emptyList(),
    val answerChanged: Boolean = false,
    val confidence: Double = 0.95,
    val reason: String = "",
    val correctedOptions: List<String> = emptyList(),
    val correctedExplanation: String? = null,
    val isSuspicious: Boolean = answerChanged || needsReview || !isValid,
    val issueDescription: String? = if (answerChanged || needsReview) reason else null,
    val suggestedCorrectAnswer: String? = verifiedAnswerText,
    val suggestedCorrectIndex: Int? = verifiedAnswerIndex
)

data class QuestionAuditRecord(
    val questionId: String,
    val questionText: String = "",
    val originalAnswer: String,
    val correctedAnswer: String,
    val changed: Boolean,
    val reason: String,
    val confidence: Double,
    val needsReview: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuizAuditSummary(
    val quizId: String,
    val totalQuestionsAudited: Int,
    val correctedCount: Int,
    val needsReviewCount: Int,
    val auditRecords: List<QuestionAuditRecord> = emptyList(),
    val auditedAt: Long = System.currentTimeMillis()
)
