package com.example.engine

import android.util.Log
import com.example.BuildConfig
import com.example.ai.AIPromptBuilder
import com.example.ai.AnswerEvaluationRequest
import com.example.ai.SmartNormalizer
import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionType
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

    suspend fun evaluateAnswerWithContext(
        request: AnswerEvaluationRequest
    ): Result<AiEvaluationResult> = evaluateAnswer(
        questionText = request.questionText,
        acceptedAnswers = request.acceptedAnswers,
        userAnswer = request.userAnswer
    )
}

/**
 * Real Gemini-powered Context-Aware Educational Answer Evaluator.
 * Implements the 6-step evaluation pipeline:
 * Step 1: Local exact/normalized comparison (bypasses AI call)
 * Step 2: Local semantic-safe rules
 * Step 3: AI contextual evaluation with complete sentence context
 * Step 4: Simple, student-friendly Bangla explanation
 * Step 5: Strict response validation & safety override
 * Step 6: Final result
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
        val request = AnswerEvaluationRequest(
            questionText = questionText,
            acceptedAnswers = acceptedAnswers,
            userAnswer = userAnswer,
            questionType = QuestionType.FILL_BLANK
        )
        evaluateAnswerWithContext(request)
    }

    override suspend fun evaluateAnswerWithContext(
        request: AnswerEvaluationRequest
    ): Result<AiEvaluationResult> = withContext(Dispatchers.IO) {
        val trimmedAnswer = request.userAnswer.trim()
        val allAccepted = request.acceptedAnswers.filter { it.isNotBlank() }.ifEmpty {
            if (request.storedAnswer.isNotBlank()) listOf(request.storedAnswer) else emptyList()
        }

        // =========================================================================
        // STEP 1 & STEP 2: Local exact / normalized comparison (Highest Priority)
        // AI must NOT be called when a deterministic exact match already proves the answer is correct.
        // =========================================================================
        val (isExactOrNorm, matched) = SmartNormalizer.checkExactOrNormalizedMatch(trimmedAnswer, allAccepted)
        if (isExactOrNorm && matched != null) {
            val evalType = if (trimmedAnswer.equals(matched, ignoreCase = false)) "exact_match" else "normalized_match"
            val expl = "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matched” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
            val exactResult = AiEvaluationResult(
                isCorrect = true,
                confidence = 1.0,
                evaluationType = evalType,
                matchedAnswer = matched,
                verifiedAnswer = matched,
                needsReview = false,
                explanation_bn = expl,
                reason = "Deterministic exact/normalized match against verified accepted answer",
                banglaExplanation = expl,
                decision = "correct",
                jsonAnswerCorrect = true
            )
            Log.d(TAG, "Step 1 exact match succeeded for: '$trimmedAnswer' -> '$matched'. Skipping AI call.")
            return@withContext Result.success(exactResult)
        }

        val cacheKey = generateCacheKey(request.questionText, allAccepted, trimmedAnswer)
        evaluationCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached evaluation for: $trimmedAnswer")
            return@withContext Result.success(cached)
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured. Falling back to local evaluation.")
            val fallback = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = allAccepted,
                userAnswer = trimmedAnswer,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
            return@withContext Result.success(fallback)
        }

        try {
            val prompt = AIPromptBuilder.buildEvaluationPrompt(request)
            val requestBodyJson = buildRequestBody(prompt)

            var result = callGeminiModel(PRIMARY_MODEL, requestBodyJson, allAccepted, trimmedAnswer)
            val primaryErrorMsg = result.exceptionOrNull()?.message.orEmpty()
            if (result.isFailure && (primaryErrorMsg.contains("404") || primaryErrorMsg.contains("not found", ignoreCase = true))) {
                Log.w(TAG, "Primary model unavailable, falling back to $FALLBACK_MODEL")
                result = callGeminiModel(FALLBACK_MODEL, requestBodyJson, allAccepted, trimmedAnswer)
            }

            if (result.isSuccess) {
                val evaluated = result.getOrThrow()
                evaluationCache[cacheKey] = evaluated
                result
            } else {
                val fallback = SmartNormalizer.createLocalEvaluation(
                    questionText = request.questionText,
                    acceptedAnswers = allAccepted,
                    userAnswer = trimmedAnswer,
                    offlineNote = true,
                    evaluationType = "ai_unavailable"
                )
                Result.success(fallback)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val exMsg = e.message.orEmpty()
            Log.e(TAG, "Error evaluating answer with AI: $exMsg", e)
            val fallback = SmartNormalizer.createLocalEvaluation(
                questionText = request.questionText,
                acceptedAnswers = allAccepted,
                userAnswer = trimmedAnswer,
                offlineNote = true,
                evaluationType = "ai_unavailable"
            )
            Result.success(fallback)
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
        acceptedAnswers: List<String>,
        userAnswer: String = ""
    ): Result<AiEvaluationResult> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    Log.e(TAG, "Gemini API call failed ($modelName, ${response.code}): $errorBody")
                    val failureDetail = if (errorBody.isNotBlank()) errorBody else "HTTP error ${response.code}"
                    return Result.failure(Exception("Gemini API error ${response.code}: $failureDetail"))
                }

                val responseString = response.body?.string().orEmpty()
                if (responseString.isBlank()) {
                    return Result.failure(Exception("Empty response from Gemini API"))
                }

                return try {
                    val rootJson = JSONObject(responseString)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        return Result.failure(Exception("No candidates returned from Gemini"))
                    }
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text").orEmpty()

                    if (text.isBlank()) {
                        return Result.failure(Exception("No text in candidate content"))
                    }

                    val fallbackAnswer = acceptedAnswers.firstOrNull().orEmpty()
                    val evaluationResult = AIPromptBuilder.parseEvaluationResponse(
                        rawResponse = text,
                        fallbackAcceptedAnswer = fallbackAnswer,
                        userAnswer = userAnswer,
                        acceptedAnswers = acceptedAnswers
                    )
                    Result.success(evaluationResult)
                } catch (e: Exception) {
                    val parseMsg = e.message.orEmpty()
                    Log.e(TAG, "Failed to parse Gemini output JSON: $parseMsg", e)
                    Result.failure(Exception(if (parseMsg.isBlank()) "Failed to parse Gemini output JSON" else parseMsg, e))
                }
            }
        } catch (e: Exception) {
            val netMsg = e.message.orEmpty()
            Log.e(TAG, "Network error during Gemini API call ($modelName): $netMsg", e)
            return Result.failure(Exception(if (netMsg.isBlank()) "Network error connecting to Gemini API" else netMsg, e))
        }
    }
}
