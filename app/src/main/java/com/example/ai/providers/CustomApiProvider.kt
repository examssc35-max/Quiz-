package com.example.ai.providers

import android.util.Log
import com.example.ai.AIConfig
import com.example.ai.AIPromptBuilder
import com.example.ai.AIProvider
import com.example.ai.AIProviderType
import com.example.ai.AiResultAnalysis
import com.example.ai.AnswerEvaluationRequest
import com.example.ai.ChatMessage
import com.example.ai.QuestionAiContext
import com.example.ai.QuestionAuditResult
import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionSchema
import com.example.data.model.QuizResultSummary
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
        val prompt = AIPromptBuilder.buildEvaluationPrompt(request)
        callCustomEndpoint(prompt, config).map { text ->
            val fallback = request.acceptedAnswers.firstOrNull().orEmpty()
            AIPromptBuilder.parseEvaluationResponse(text, fallback)
        }
    }

    override suspend fun chatFollowUp(
        context: QuestionAiContext,
        history: List<ChatMessage>,
        userMessage: String,
        config: AIConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildFollowUpChatPrompt(context, history, userMessage)
        callCustomEndpoint(prompt, config)
    }

    override suspend fun analyzeQuizResult(
        summary: QuizResultSummary,
        config: AIConfig
    ): Result<AiResultAnalysis> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildResultAnalysisPrompt(summary)
        callCustomEndpoint(prompt, config).map { text ->
            AIPromptBuilder.parseResultAnalysis(text)
        }
    }

    override suspend fun auditQuestion(
        question: QuestionSchema,
        config: AIConfig
    ): Result<QuestionAuditResult> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildQuestionAuditPrompt(question)
        callCustomEndpoint(prompt, config).map { text ->
            AIPromptBuilder.parseQuestionAudit(question, text)
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val endpointUrl = if (config.effectiveBaseUrl.endsWith("/v1", ignoreCase = true) || config.effectiveBaseUrl.contains("huggingface", ignoreCase = true)) {
            com.example.ai.ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        } else {
            config.effectiveBaseUrl.trim()
        }
        if (endpointUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Endpoint URL is required."))
        }

        val requestJson = JSONObject().apply {
            put("model", config.effectiveModel)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are the Quiz Explore AI Agent.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "2 + 2 = ?")
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
                    Result.success("✓ Connection successful (${duration}ms)\nModel: ${config.effectiveModel}\nEndpoint: $endpointUrl")
                } else {
                    val code = response.code
                    val rawBody = response.body?.string().orEmpty().trim()
                    val snippet = if (rawBody.isNotBlank()) {
                        try {
                            val json = JSONObject(rawBody)
                            val errorVal = json.opt("error")
                            when (errorVal) {
                                is JSONObject -> errorVal.optString("message").ifBlank { errorVal.optString("detail") }
                                is String -> errorVal
                                else -> json.optString("message").ifBlank { json.optString("detail") }
                            }.ifBlank { rawBody.take(200) }
                        } catch (e: Exception) {
                            rawBody.replace(Regex("<[^>]*>"), " ").trim().take(200)
                        }
                    } else ""
                    val serverSnippet = if (snippet.isNotBlank()) "\nServer message: $snippet" else ""
                    val statusDesc = when (code) {
                        404 -> "404 — Endpoint or model/provider configuration not found."
                        401 -> "401 — Unauthorized. Invalid or missing API key."
                        403 -> "403 — Forbidden access to model."
                        429 -> "429 — Rate limit or quota exceeded."
                        else -> "$code (Custom endpoint error)"
                    }
                    Result.failure(Exception("✕ Connection failed\nHTTP status: $statusDesc\nTarget URL: $endpointUrl\nModel: ${config.effectiveModel}$serverSnippet"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("✕ Connection failed: ${e.message.orEmpty()}\nTarget URL: $endpointUrl\nModel: ${config.effectiveModel}", e))
        }
    }

    private fun callCustomEndpoint(prompt: String, config: AIConfig): Result<String> {
        val endpointUrl = if (config.effectiveBaseUrl.endsWith("/v1", ignoreCase = true) || config.effectiveBaseUrl.contains("huggingface", ignoreCase = true)) {
            com.example.ai.ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        } else {
            config.effectiveBaseUrl.trim()
        }
        if (endpointUrl.isBlank()) {
            return Result.failure(IllegalStateException("Custom API endpoint URL is not configured"))
        }

        val requestJson = JSONObject().apply {
            put("model", config.effectiveModel)
            put("prompt", prompt)
            put("temperature", config.temperature.toDouble())
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", AIPromptBuilder.buildSystemInstruction())
                })
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
                    Log.e(TAG, "Custom endpoint failed ($code): $err")
                    return Result.failure(Exception("Endpoint error ($code): $err"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return Result.failure(Exception("Empty response from custom endpoint"))
                }

                val extractedText = extractResponseText(responseBody)
                return Result.success(extractedText)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "Custom endpoint failed: $msg")
            return Result.failure(Exception("Custom endpoint error: $msg", e))
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
