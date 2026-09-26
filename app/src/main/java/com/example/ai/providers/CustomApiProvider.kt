package com.example.ai.providers

import android.util.Log
import com.example.ai.AIConfig
import com.example.ai.AIPromptBuilder
import com.example.ai.AIProvider
import com.example.ai.AIProviderType
import com.example.ai.AnswerEvaluationRequest
import com.example.data.model.AiEvaluationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CustomApiProvider(
    private val client: OkHttpClient = defaultClient
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.CUSTOM

    companion object {
        private const val TAG = "CustomApiProvider"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun evaluateAnswer(
        request: AnswerEvaluationRequest,
        config: AIConfig
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val endpointUrl = config.effectiveBaseUrl.trim()
        if (endpointUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Custom API endpoint URL is not configured"))
        }

        val prompt = AIPromptBuilder.buildEvaluationPrompt(request)
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("temperature", config.temperature.toDouble())
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val builder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")

        if (config.effectiveApiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.effectiveApiKey}")
        }

        config.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.addHeader(k, v)
            }
        }

        val httpRequest = builder.post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)).build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string().orEmpty()
                    Log.e(TAG, "Custom endpoint failed with code $code: $err")
                    return@withContext Result.failure(Exception("Endpoint error ($code): $err"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from custom endpoint"))
                }

                // Try parsing standard formats: choices[0].message.content, text, response, or raw JSON
                val extractedText = extractResponseText(responseBody)
                val fallback = request.acceptedAnswers.firstOrNull().orEmpty()
                val parsed = AIPromptBuilder.parseEvaluationResponse(extractedText, fallback)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "Custom endpoint failed: $msg")
            Result.failure(Exception("Custom endpoint error: $msg", e))
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val endpointUrl = config.effectiveBaseUrl.trim()
        if (endpointUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Endpoint URL is required."))
        }

        val requestJson = JSONObject().apply {
            put("model", config.effectiveModel)
            put("test", true)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
        }

        val builder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")

        if (config.effectiveApiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.effectiveApiKey}")
        }

        config.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.addHeader(k, v)
            }
        }

        val httpRequest = builder.post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val startTime = System.currentTimeMillis()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Result.success("Connection successful! Custom endpoint responded in ${duration}ms.")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message.orEmpty()}", e))
        }
    }

    private fun extractResponseText(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            if (obj.has("choices")) {
                obj.optJSONArray("choices")?.getJSONObject(0)?.optJSONObject("message")?.optString("content") ?: raw
            } else if (obj.has("text")) {
                obj.optString("text", raw)
            } else if (obj.has("response")) {
                obj.optString("response", raw)
            } else {
                raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}
