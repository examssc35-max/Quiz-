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

class AnthropicProvider(
    private val client: OkHttpClient = defaultClient
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.ANTHROPIC

    companion object {
        private const val TAG = "AnthropicProvider"
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
        val apiKey = config.effectiveApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Anthropic API key is not configured"))
        }

        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("system", AIPromptBuilder.buildSystemInstruction())
            put("temperature", config.temperature.toDouble())

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", AIPromptBuilder.buildEvaluationPrompt(request))
                })
            }
            put("messages", messages)
        }

        val url = "$baseUrl/messages"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    Log.e(TAG, "Anthropic call failed with code $code: $err")
                    return@withContext Result.failure(Exception("Anthropic error ($code): $err"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from Anthropic"))
                }

                val rootObj = JSONObject(responseBody)
                val contentArray = rootObj.optJSONArray("content")
                if (contentArray == null || contentArray.length() == 0) {
                    return@withContext Result.failure(Exception("No content returned from Anthropic"))
                }

                val text = contentArray.getJSONObject(0).optString("text").orEmpty()
                if (text.isBlank()) {
                    return@withContext Result.failure(Exception("Empty text content from Anthropic"))
                }

                val fallback = request.acceptedAnswers.firstOrNull().orEmpty()
                val parsed = AIPromptBuilder.parseEvaluationResponse(text, fallback)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "Anthropic request failed: $msg")
            Result.failure(Exception("Anthropic error: $msg", e))
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.effectiveApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key is missing. Please enter your Anthropic API Key."))
        }

        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 5)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("messages", messages)
        }

        val url = "$baseUrl/messages"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Result.success("Connection successful! Claude ($model) responded in ${duration}ms.")
                } else {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    val helpfulHint = when (code) {
                        401 -> "Invalid Anthropic API Key."
                        404 -> "Model '$model' or messages endpoint not found."
                        429 -> "Rate limit exceeded on Anthropic account."
                        else -> err
                    }
                    Result.failure(Exception("HTTP $code: $helpfulHint"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message.orEmpty()}", e))
        }
    }

    private fun sanitizeError(raw: String): String {
        return try {
            val json = JSONObject(raw)
            val errorObj = json.optJSONObject("error")
            errorObj?.optString("message", raw) ?: raw
        } catch (e: Exception) {
            raw.take(200)
        }
    }
}
