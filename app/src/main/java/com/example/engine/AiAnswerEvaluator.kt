package com.example.engine

import android.util.Log
import com.example.BuildConfig
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

interface AiAnswerEvaluator {
    suspend fun evaluateAnswer(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): Result<AiEvaluationResult>
}

/**
 * Gemini-powered AI Answer Evaluator for fill-in-the-blank questions.
 * Adheres strictly to grammar, sentence context, part-of-speech, and anti-overcorrection rules.
 */
class GeminiAiAnswerEvaluator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val client: OkHttpClient = defaultClient
) : AiAnswerEvaluator {

    companion object {
        private const val TAG = "GeminiEvaluator"
        private const val PRIMARY_MODEL = "gemini-2.5-flash"
        private const val FALLBACK_MODEL = "gemini-3.5-flash"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun evaluateAnswer(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured")
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }

        try {
            val prompt = buildEvaluationPrompt(questionText, acceptedAnswers, userAnswer)
            val requestBodyJson = buildRequestBody(prompt)

            // Try primary model first, fallback to secondary if 404 or model not found
            var result = callGeminiModel(PRIMARY_MODEL, requestBodyJson, acceptedAnswers)
            if (result.isFailure && result.exceptionOrNull()?.message?.contains("404") == true) {
                Log.w(TAG, "Primary model 404, falling back to $FALLBACK_MODEL")
                result = callGeminiModel(FALLBACK_MODEL, requestBodyJson, acceptedAnswers)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating answer with AI", e)
            Result.failure(e)
        }
    }

    private fun buildEvaluationPrompt(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): String {
        val acceptedListStr = acceptedAnswers.joinToString(", ") { "\"$it\"" }
        return """
            You are an expert English language and educational quiz evaluator with expertise in grammar, syntax, and semantics.
            Your task is to evaluate a student's answer to a fill-in-the-blank question.

            Question sentence: "$questionText"
            Authoritative accepted answer(s): [$acceptedListStr]
            Student submitted answer: "$userAnswer"

            EVALUATION CRITERIA:
            1. Analyze meaning, sentence context, grammar, part of speech, tense/form, singular/plural, and natural English usage.
            2. If the user's answer is a genuinely valid, natural alternative that fits the exact sentence structure and retains the intended meaning, mark isCorrect: true.
            3. DO NOT OVER-CORRECT:
               - Do NOT accept vaguely related words.
               - Do NOT accept words with different meanings or connotations.
               - Do NOT accept grammatically incorrect forms (e.g. plural when singular is required, wrong tense, wrong part of speech).
               - Do NOT accept words that alter the meaning of the sentence.
               - When uncertain, mark isCorrect: false.
            4. Bangla Explanation (MUST be in clear, simple, natural Bengali):
               - If CORRECT:
                 Tell why the answer is correct.
                 If it is an alternative answer, explain why it is acceptable in this sentence.
                 Example: "তোমার উত্তরটি সঠিক। এখানে ‘management’ শব্দটি বাক্যের অর্থ ও grammar অনুযায়ী ঠিকভাবে বসে।"
               - If WRONG:
                 Explain:
                 1. Why the student's answer does not fit the sentence.
                 2. Why the accepted answer fits.
                 3. The relevant grammar or meaning in simple Bengali.
                 Example: "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।"

            Respond ONLY with valid JSON in this exact structure:
            {
              "isCorrect": boolean,
              "confidence": number,
              "reason": "Brief English rationale",
              "banglaExplanation": "বাংলায় সহজ ও স্পষ্ট ব্যাখ্যা",
              "matchedAnswer": "most relevant accepted answer"
            }
        """.trimIndent()
    }

    private fun buildRequestBody(prompt: String): String {
        val root = JSONObject()
        val contents = JSONArray()
        val contentObj = JSONObject()
        val parts = JSONArray()
        val partObj = JSONObject()

        partObj.put("text", prompt)
        parts.put(partObj)
        contentObj.put("parts", parts)
        contents.put(contentObj)
        root.put("contents", contents)

        val generationConfig = JSONObject()
        generationConfig.put("responseMimeType", "application/json")
        generationConfig.put("temperature", 0.1)
        root.put("generationConfig", generationConfig)

        return root.toString()
    }

    private fun callGeminiModel(
        modelName: String,
        jsonBody: String,
        acceptedAnswers: List<String>
    ): Result<AiEvaluationResult> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API call failed ($modelName, ${response.code}): $errorBody")
                return Result.failure(Exception("Gemini API error ${response.code}: $errorBody"))
            }

            val responseString = response.body?.string()
                ?: return Result.failure(Exception("Empty response from Gemini API"))

            return try {
                val rootJson = JSONObject(responseString)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return Result.failure(Exception("No candidates returned from Gemini"))
                }
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")

                if (text.isNullOrBlank()) {
                    return Result.failure(Exception("No text in candidate content"))
                }

                val fallbackAnswer = acceptedAnswers.firstOrNull() ?: ""
                val evaluationResult = AiEvaluationResult.fromJson(text, fallbackAnswer)
                Result.success(evaluationResult)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Gemini output JSON", e)
                Result.failure(e)
            }
        }
    }
}
