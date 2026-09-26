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

class GeminiProvider(
    private val client: OkHttpClient = defaultClient
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.GEMINI

    companion object {
        private const val TAG = "GeminiProvider"
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
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }

        val prompt = AIPromptBuilder.buildEvaluationPrompt(request)
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            parts.put(partObj)
            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)

            val systemInstruction = JSONObject().apply {
                val sysParts = JSONArray()
                sysParts.put(JSONObject().put("text", AIPromptBuilder.buildSystemInstruction()))
                put("parts", sysParts)
            }
            put("systemInstruction", systemInstruction)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", config.temperature.toDouble())
            }
            put("generationConfig", generationConfig)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errBody = response.body?.string().orEmpty()
                    val sanitizedError = sanitizeError(errBody)
                    Log.e(TAG, "Gemini call failed with code $code: $sanitizedError")
                    return@withContext Result.failure(Exception("Gemini error ($code): $sanitizedError"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from Gemini"))
                }

                val rootObj = JSONObject(responseBody)
                val candidates = rootObj.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(Exception("No candidates returned from Gemini"))
                }

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text").orEmpty()

                if (text.isBlank()) {
                    return@withContext Result.failure(Exception("Empty candidate text from Gemini"))
                }

                val fallback = request.acceptedAnswers.firstOrNull().orEmpty()
                val parsed = AIPromptBuilder.parseEvaluationResponse(text, fallback)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "Gemini request failed: $msg")
            Result.failure(Exception("Gemini error: $msg", e))
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.effectiveApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key is missing. Please enter a valid Gemini API Key."))
        }

        val model = config.effectiveModel
        val requestJson = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", "Respond with {\"status\":\"ok\"}"))
            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Result.success("Connection successful! Gemini ($model) responded in ${duration}ms.")
                } else {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    val helpfulHint = when (code) {
                        400 -> "Invalid request parameters or model name."
                        403, 401 -> "Invalid or restricted API Key."
                        404 -> "Model '$model' was not found. Try 'gemini-2.5-flash'."
                        429 -> "Quota or rate limit exceeded on Gemini account."
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
