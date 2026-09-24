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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface AiAnswerEvaluator {
    suspend fun evaluateAnswer(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): Result<AiEvaluationResult>
}

/**
 * Real Gemini-powered AI Answer Evaluator for fill-in-the-blank questions.
 * Analyzes sentence meaning, context, grammar, part of speech, tense, singular/plural,
 * and whether the answer naturally replaces the blank.
 * Includes in-memory caching to avoid redundant API calls for the same answer.
 */
class GeminiAiAnswerEvaluator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val client: OkHttpClient = defaultClient
) : AiAnswerEvaluator {

    companion object {
        private const val TAG = "GeminiEvaluator"
        private const val PRIMARY_MODEL = "gemini-2.5-flash"
        private const val FALLBACK_MODEL = "gemini-3.5-flash"

        // In-memory cache across questions and sessions
        private val evaluationCache = ConcurrentHashMap<String, AiEvaluationResult>()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun evaluateAnswer(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val trimmedAnswer = userAnswer.trim()
        val cacheKey = generateCacheKey(questionText, acceptedAnswers, trimmedAnswer)

        // Return cached result immediately if already evaluated
        evaluationCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached evaluation for: $trimmedAnswer")
            return@withContext Result.success(cached)
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured")
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }

        try {
            val prompt = buildEvaluationPrompt(questionText, acceptedAnswers, trimmedAnswer)
            val requestBodyJson = buildRequestBody(prompt)

            // Try primary model first, fallback to secondary if 404 or model not found
            var result = callGeminiModel(PRIMARY_MODEL, requestBodyJson, acceptedAnswers)
            if (result.isFailure && result.exceptionOrNull()?.message?.contains("404") == true) {
                Log.w(TAG, "Primary model 404, falling back to $FALLBACK_MODEL")
                result = callGeminiModel(FALLBACK_MODEL, requestBodyJson, acceptedAnswers)
            }

            if (result.isSuccess) {
                val evaluated = result.getOrThrow()
                evaluationCache[cacheKey] = evaluated
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating answer with AI", e)
            Result.failure(e)
        }
    }

    private fun generateCacheKey(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): String {
        return "${questionText.trim().lowercase()}||${acceptedAnswers.joinToString(",").lowercase()}||${userAnswer.trim().lowercase()}"
    }

    private fun buildEvaluationPrompt(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String
    ): String {
        val acceptedListStr = acceptedAnswers.joinToString(", ") { "\"$it\"" }
        return """
            You are an expert English language and educational quiz evaluator with expertise in grammar, syntax, and semantics.
            Your task is to evaluate a student's answer to a fill-in-the-blank question using the real English sentence context.

            SENTENCE / QUESTION: "$questionText"
            BLANK POSITION: Identify the blank in the sentence (typically marked by '______', '___', '[...]', or markers like '(a) —', '(b) —', '(c) —').
            STORED ACCEPTED ANSWER(S): [$acceptedListStr]
            STUDENT SUBMITTED ANSWER: "$userAnswer"

            CRITICAL EVALUATION RULES:
            Judge the STUDENT'S ANSWER against the ACTUAL SENTENCE, not simply comparing two words.
            The goal is: "Does this answer naturally, grammatically, and semantically correctly fit this exact blank in this exact sentence?"

            MUST ANALYZE:
            1. Sentence meaning and whole sentence context
            2. Grammar & syntax around the blank
            3. Part of speech (noun, verb, adjective, adverb, preposition, etc.) required by the position
            4. Tense and verb form (past, present, participle, gerund, etc.)
            5. Singular / plural agreement (e.g. if the sentence requires a singular noun, a plural noun is WRONG)
            6. Natural English usage and collocation
            7. Meaning of the student's word in this context
            8. Whether the user's answer can naturally replace the blank
            9. Whether it is a genuinely valid alternative answer (e.g. if stored answer is "harm", and user writes "damage", and "damage" is valid in context, return isCorrect: true)
            10. Do NOT accept a word merely because it is a synonym if it violates the grammar or alters the sentence meaning!
                Example:
                If sentence is "Air is the most important (a) — of human environment."
                Stored: "element"
                User: "elements"
                -> MUST be marked isCorrect: false, because the sentence requires the singular form!

            BANGLA EXPLANATION RULES (বাংলায় ব্যাখ্যা):
            After every submission, generate a clear, natural, helpful Bangla explanation.
            Do NOT give generic or lazy explanations such as "তোমার উত্তর সঠিক উত্তরের সাথে মেলেনি।"
            The explanation must actually explain the sentence and grammatical context!

            - If CORRECT:
              Explain why the user's answer fits the sentence.
              Example: "তোমার উত্তরটি সঠিক। 'damage' শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।"

            - If WRONG:
              Explain:
              1. কেন user-এর উত্তরটি এই বাক্যে ভুল (যেমন: grammar, plural/singular, tense, বা অর্থের অমিল)
              2. কেন accepted answer সঠিক
              3. বাক্যের অর্থ ও grammar সহজ বাংলায় বুঝিয়ে দাও।
              Example: "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।"

            RESPOND ONLY WITH STRICT VALID JSON matching this exact structure:
            {
              "isCorrect": boolean,
              "confidence": number,
              "matchedAnswer": "most relevant accepted answer or the valid word",
              "banglaExplanation": "বাংলায় সহজ ও স্পষ্ট ব্যাখ্যা",
              "reason": "Brief English rationale explaining the linguistic decision"
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
