package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AISettingsStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AIConfig> = _configFlow.asStateFlow()

    fun getConfig(): AIConfig = _configFlow.value

    fun saveConfig(config: AIConfig) {
        prefs.edit()
            .putBoolean(KEY_AI_ENABLED, config.enabled)
            .putString(KEY_PROVIDER_TYPE, config.providerType.id)
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putString(KEY_CUSTOM_HEADERS, serializeHeaders(config.customHeaders))
            .apply()

        _configFlow.value = config
    }

    fun resetConfig() {
        prefs.edit().clear().apply()
        _configFlow.value = AIConfig()
    }

    private fun loadConfig(): AIConfig {
        val enabled = prefs.getBoolean(KEY_AI_ENABLED, true)
        val providerId = prefs.getString(KEY_PROVIDER_TYPE, AIProviderType.GEMINI.id)
        val providerType = AIProviderType.fromId(providerId)
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val model = prefs.getString(KEY_MODEL, "") ?: ""
        val baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
        val temperature = prefs.getFloat(KEY_TEMPERATURE, 0.1f)
        val headersJson = prefs.getString(KEY_CUSTOM_HEADERS, "") ?: ""

        return AIConfig(
            enabled = enabled,
            providerType = providerType,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            temperature = temperature,
            customHeaders = deserializeHeaders(headersJson)
        )
    }

    private fun serializeHeaders(headers: Map<String, String>): String {
        return try {
            val obj = JSONObject()
            headers.forEach { (k, v) -> obj.put(k, v) }
            obj.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun deserializeHeaders(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val map = mutableMapOf<String, String>()
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val PREFS_NAME = "quiz_ai_provider_settings"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_PROVIDER_TYPE = "ai_provider_type"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_TEMPERATURE = "ai_temperature"
        private const val KEY_CUSTOM_HEADERS = "ai_custom_headers"

        @Volatile
        private var INSTANCE: AISettingsStorage? = null

        fun getInstance(context: Context): AISettingsStorage {
            return INSTANCE ?: synchronized(this) {
                val instance = AISettingsStorage(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
