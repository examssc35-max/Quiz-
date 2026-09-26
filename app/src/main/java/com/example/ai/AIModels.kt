package com.example.ai

import com.example.BuildConfig

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
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        requiresBaseUrl = true,
        popularModels = listOf("llama-3.3-70b-versatile", "mistral-small-latest", "gpt-4o-mini")
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
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
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
    val languageHint: String? = null
)
