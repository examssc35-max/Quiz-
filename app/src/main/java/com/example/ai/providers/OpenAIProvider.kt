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

class OpenAIProvider(
    private val client: OkHttpClient = defaultClient
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.OPENAI

    companion object {
        private const val TAG = "OpenAIProvider"
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
        callOpenAiChat(prompt, config, jsonMode = true).map { text ->
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
        callOpenAiChat(prompt, config, jsonMode = false)
    }

    override suspend fun analyzeQuizResult(
        summary: QuizResultSummary,
        config: AIConfig
    ): Result<AiResultAnalysis> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildResultAnalysisPrompt(summary)
        callOpenAiChat(prompt, config, jsonMode = true).map { text ->
            AIPromptBuilder.parseResultAnalysis(text)
        }
    }

    override suspend fun auditQuestion(
        question: QuestionSchema,
        config: AIConfig
    ): Result<QuestionAuditResult> = withContext(Dispatchers.IO) {
        val prompt = AIPromptBuilder.buildQuestionAuditPrompt(question)
        callOpenAiChat(prompt, config, jsonMode = true).map { text ->
            AIPromptBuilder.parseQuestionAudit(question, text)
        }
    }

    override suspend fun testConnection(config: AIConfig): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.effectiveApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key is missing. Please enter your OpenAI API Key."))
        }

        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 5)
            put("stream", false)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("messages", messages)
        }

        val url = com.example.ai.ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Result.success("Connection successful! OpenAI ($model) responded in ${duration}ms.")
                } else {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    val helpfulHint = when (code) {
                        401 -> "Invalid OpenAI API Key."
                        404 -> "Model '$model' or endpoint not found."
                        429 -> "Rate limit or quota exceeded on OpenAI account."
                        else -> err
                    }
                    Result.failure(Exception("HTTP $code: $helpfulHint"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message.orEmpty()}", e))
        }
    }

    private fun callOpenAiChat(prompt: String, config: AIConfig, jsonMode: Boolean): Result<String> {
        val apiKey = config.effectiveApiKey
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("OpenAI API key is not configured"))
        }

        val baseUrl = config.effectiveBaseUrl.trimEnd('/')
        val model = config.effectiveModel

        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", if (jsonMode) config.temperature.toDouble() else 0.4)
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }

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
            put("stream", false)
        }

        val url = com.example.ai.ChatUrlNormalizer.normalize(config.effectiveBaseUrl)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = sanitizeError(response.body?.string().orEmpty())
                    Log.e(TAG, "OpenAI call failed ($code): $err")
                    return Result.failure(Exception("OpenAI error ($code): $err"))
                }

                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return Result.failure(Exception("Empty response from OpenAI"))
                }

                val rootObj = JSONObject(responseBody)
                val choices = rootObj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return Result.failure(Exception("No choices returned from OpenAI"))
                }

                val message = choices.getJSONObject(0).optJSONObject("message")
                val text = message?.optString("content").orEmpty()
                if (text.isBlank()) {
                    return Result.failure(Exception("Empty content from OpenAI"))
                }

                return Result.success(text)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().ifBlank { "Network connection error" }
            Log.e(TAG, "OpenAI request failed: $msg")
            return Result.failure(Exception("OpenAI error: $msg", e))
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
