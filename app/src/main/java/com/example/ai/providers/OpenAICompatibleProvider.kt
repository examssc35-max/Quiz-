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

class OpenAICompatibleProvider(
    private val client: OkHttpClient = defaultClient
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.OPENAI_COMPATIBLE

    companion object {
        private const val TAG = "OpenAICompatible"
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
        callEndpointChat(prompt, config).map { text ->
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
        callEndpointChat(prompt, config)
    }

    override suspend fun analyzeQuizResult(
        summary: QuizResultSummary,
        config: AIConfig
    ): Result<AiResultAnalysis> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildResultAnalysisPrompt(summary)
        callEndpointChat(prompt, config).map { text ->
            AIPromptBuilder.parseResultAnalysis(text)
        }
    }

    override suspend fun auditQuestion(
        question: QuestionSchema,
        config: AIConfig
    ): Result<QuestionAuditResult> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildQuestionAuditPrompt(question)
        callEndpointChat(prompt, config).map { text ->
            AIPromptBuilder.parseQuestionAudit(question.id, text)
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Base URL is required. Please enter an API endpoint."))
        }

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

        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val builder = Request.Builder()
            .url(url)
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
                    Result.success("Connection successful! Endpoint responded in ${duration}ms.")
                } else {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    Result.failure(Exception("HTTP $code: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message.orEmpty()}", e))
        }
    }

    private fun callEndpointChat(prompt: String, config: AIConfig): Result<String> {
        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Base URL is required for OpenAI-compatible endpoint"))
        }

        val model = config.effectiveModel
        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", config.temperature.toDouble())

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", AIPromptBuilder.buildSystemInstruction())
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val builder = Request.Builder()
            .url(url)
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
                    val err = sanitizeError(response.body?.string().orEmpty())
                    Log.e(TAG, "OpenAI-compatible call failed ($code): $err")
                    return Result.failure(Exception("API error ($code): $err"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return Result.failure(Exception("Empty response from API endpoint"))
                }

                val rootObj = JSONObject(responseBody)
                val choices = rootObj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return Result.failure(Exception("No choices returned from API"))
                }

                val message = choices.getJSONObject(0).optJSONObject("message")
                val text = message?.optString("content").orEmpty()
                if (text.isBlank()) {
                    return Result.failure(Exception("Empty content from API"))
                }

                return Result.success(text)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "Request failed: $msg")
            return Result.failure(Exception("Endpoint error: $msg", e))
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
