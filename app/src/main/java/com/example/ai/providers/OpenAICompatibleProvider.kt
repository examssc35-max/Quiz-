package com.example.ai.providers

import android.util.Log
import com.example.ai.AIConfig
import com.example.ai.AIPromptBuilder
import com.example.ai.AIProvider
import com.example.ai.AIProviderType
import com.example.ai.AiResultAnalysis
import com.example.ai.AnswerEvaluationRequest
import com.example.ai.ChatMessage
import com.example.ai.ChatUrlNormalizer
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

/**
 * Robust OpenAI-compatible Chat Completions Provider supporting Hugging Face Inference Endpoints,
 * Groq, Together AI, vLLM, Ollama, and arbitrary OpenAI-compatible servers.
 */
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
                .readTimeout(35, TimeUnit.SECONDS)
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
        val targetUrl = ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Base URL is required. Please enter an API endpoint (e.g. https://router.huggingface.co/v1).")
            )
        }

        val model = config.effectiveModel.trim()
        if (model.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Model name is required (e.g. openai/gpt-oss-120b:groq).")
            )
        }

        // Standard OpenAI-compatible Chat Completions request body
        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("stream", false)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are the Quiz Explore AI Agent.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "2 + 2 = ?")
                })
            }
            put("messages", messages)
        }

        val builder = Request.Builder()
            .url(targetUrl)
            .addHeader("Content-Type", "application/json")

        if (config.effectiveApiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.effectiveApiKey.trim()}")
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
                val code = response.code
                val rawBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val parseResult = extractChatContent(rawBody)
                    val aiAnswer = parseResult.getOrDefault("OK").trim()
                    Log.d(TAG, "Test connection succeeded in ${duration}ms at $targetUrl with model $model")
                    Result.success(
                        "✓ Connection successful (${duration}ms)\nModel: $model\nEndpoint: $targetUrl\nAI Answer: $aiAnswer"
                    )
                } else {
                    val snippet = sanitizeErrorBody(rawBody)
                    // NEVER LOG API KEY OR AUTH HEADER
                    Log.e(TAG, "Test connection failed: status=$code, url=$targetUrl, model=$model, body=$snippet")
                    val errorMsg = formatHttpErrorMessage(code, targetUrl, model, config.providerType.displayName, snippet)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection failed" }
            Log.e(TAG, "Test connection exception for $targetUrl: $msg")
            Result.failure(Exception("✕ Connection failed: $msg\nTarget URL: $targetUrl\nModel: $model", e))
        }
    }

    private fun callEndpointChat(prompt: String, config: AIConfig): Result<String> {
        val targetUrl = ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        if (targetUrl.isBlank()) {
            return Result.failure(IllegalStateException("Base URL is required for OpenAI-compatible endpoint"))
        }

        val model = config.effectiveModel.trim()

        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", config.temperature.toDouble())
            put("stream", false)

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

        val builder = Request.Builder()
            .url(targetUrl)
            .addHeader("Content-Type", "application/json")

        if (config.effectiveApiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.effectiveApiKey.trim()}")
        }

        config.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.addHeader(k, v)
            }
        }

        val httpRequest = builder.post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)).build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val code = response.code
                val rawBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val snippet = sanitizeErrorBody(rawBody)
                    // NEVER LOG API KEY OR AUTH HEADER
                    Log.e(TAG, "Chat request failed: status=$code, url=$targetUrl, model=$model, body=$snippet")
                    val errorMsg = formatHttpErrorMessage(code, targetUrl, model, config.providerType.displayName, snippet)
                    return Result.failure(Exception(errorMsg))
                }

                return extractChatContent(rawBody)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "OpenAI-compatible request exception: $msg")
            return Result.failure(Exception("Endpoint error: $msg\nTarget URL: $targetUrl\nModel: $model", e))
        }
    }

    private fun extractChatContent(responseBody: String): Result<String> {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(Exception("Empty response body received from AI endpoint"))
        }

        return try {
            val rootObj = JSONObject(trimmed)

            // Check for OpenAI-style error object
            if (rootObj.has("error")) {
                val errorObj = rootObj.optJSONObject("error")
                val msg = errorObj?.optString("message") ?: rootObj.optString("error")
                return Result.failure(Exception("Server returned error: $msg"))
            }

            val choices = rootObj.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("No choices returned from model. Response: ${trimmed.take(150)}"))
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            if (message != null) {
                val content = message.optString("content").trim()
                if (content.isNotEmpty()) {
                    return Result.success(content)
                }
            }

            // Fallback for completion-style format
            val textFallback = firstChoice.optString("text").trim()
            if (textFallback.isNotEmpty()) {
                return Result.success(textFallback)
            }

            Result.failure(Exception("Model response choices had no 'content' or 'text'"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse OpenAI-compatible response: ${e.message}\nRaw: ${trimmed.take(200)}", e))
        }
    }

    private fun sanitizeErrorBody(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return try {
            val json = JSONObject(trimmed)
            val errorVal = json.opt("error")
            val msg = when (errorVal) {
                is JSONObject -> errorVal.optString("message").ifBlank { errorVal.optString("detail") }
                is String -> errorVal
                else -> json.optString("message").ifBlank { json.optString("detail") }
            }
            if (msg.isNotBlank()) msg else trimmed.take(300)
        } catch (e: Exception) {
            val clean = trimmed.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            clean.take(300)
        }
    }

    private fun formatHttpErrorMessage(
        statusCode: Int,
        url: String,
        model: String,
        provider: String,
        serverMsg: String
    ): String {
        val serverSnippet = if (serverMsg.isNotBlank()) "\nServer message: $serverMsg" else ""
        val statusDesc = when (statusCode) {
            404 -> "404 — Endpoint or model/provider configuration not found."
            401 -> "401 — Unauthorized. Invalid or missing API key for $provider."
            403 -> "403 — Forbidden access to model $model."
            429 -> "429 — Rate limit or quota exceeded for $provider."
            else -> "$statusCode ($provider error)"
        }
        return "✕ Connection failed\nHTTP status: $statusDesc\nTarget URL: $url\nModel: $model$serverSnippet"
    }
}
