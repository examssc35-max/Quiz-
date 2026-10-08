package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class QuestionType {
    MCQ,
    FILL_BLANK;

    companion object {
        fun fromString(typeStr: String?): QuestionType? {
            if (typeStr == null) return null
            val normalized = typeStr.lowercase().trim().replace("-", "_").replace(" ", "_")
            return when (normalized) {
                "mcq", "multiple_choice", "multiplechoice", "choice", "single_choice" -> MCQ
                "fill_blank", "fill_in_the_blank", "fillblank", "fill_in", "fill", "blank", "text" -> FILL_BLANK
                else -> null
            }
        }
    }
}

object AnswerComparison {
    fun normalize(text: String): String {
        return com.example.ai.SmartNormalizer.normalizeSafe(text)
    }

    fun isAnswerCorrect(userAnswer: String, acceptedAnswers: List<String>): Boolean {
        val trimmed = userAnswer.trim()
        if (trimmed.isEmpty()) return false
        return com.example.ai.SmartNormalizer.checkExactOrNormalizedMatch(trimmed, acceptedAnswers).first
    }
}

data class QuizSchema(
    val version: Int = 1,
    val title: String,
    val description: String = "",
    val category: String = "General",
    val difficulty: String = "Medium",
    val timeLimit: Int = 0, // seconds, 0 = untimed
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val questions: List<QuestionSchema> = emptyList()
)

data class QuestionSchema(
    val id: String,
    val type: QuestionType = QuestionType.MCQ,
    val question: String,
    val options: List<String> = emptyList(),
    val answer: Int = 0, // 0-based index of original correct option for MCQ
    val fillBlankAnswer: String = "", // Primary original correct answer for FILL_BLANK
    val acceptedAnswers: List<String> = emptyList(), // All original accepted answers for FILL_BLANK
    val points: Int = 1,
    val explanation: String? = null,
    // AI Verification & Correction fields:
    val verifiedAnswerIndex: Int? = null,
    val verifiedFillBlankAnswer: String? = null,
    val verifiedAcceptedAnswers: List<String>? = null,
    val verifiedExplanation: String? = null,
    val isVerified: Boolean = false,
    val verificationConfidence: Double = 0.0,
    val verificationReason: String? = null,
    val needsReview: Boolean = false,
    val lastVerifiedAt: Long? = null,
    val correctedOptions: List<String>? = null
) {
    // Secondary constructor for existing code constructing MCQ questions without 'type'
    constructor(
        id: String,
        question: String,
        options: List<String>,
        answer: Int,
        points: Int = 1,
        explanation: String? = null
    ) : this(
        id = id,
        type = QuestionType.MCQ,
        question = question,
        options = options,
        answer = answer,
        fillBlankAnswer = "",
        acceptedAnswers = emptyList(),
        points = points,
        explanation = explanation
    )

    // Effective verified properties used by QuizEngine and Student Answer Evaluation (Priority: Verified AI -> Local -> Original)
    val effectiveAnswerIndex: Int
        get() = verifiedAnswerIndex ?: answer

    val effectiveFillBlankAnswer: String
        get() = verifiedFillBlankAnswer?.ifBlank { null } ?: fillBlankAnswer

    val effectiveAcceptedAnswers: List<String>
        get() = if (!verifiedAcceptedAnswers.isNullOrEmpty()) verifiedAcceptedAnswers else acceptedAnswers

    val effectiveExplanation: String?
        get() = verifiedExplanation?.ifBlank { null } ?: explanation

    val effectiveOptions: List<String>
        get() = if (!correctedOptions.isNullOrEmpty()) correctedOptions else options

    val wasAnswerCorrected: Boolean
        get() = isVerified && ((type == QuestionType.MCQ && verifiedAnswerIndex != null && verifiedAnswerIndex != answer) ||
                (type == QuestionType.FILL_BLANK && verifiedFillBlankAnswer != null && !verifiedFillBlankAnswer.equals(fillBlankAnswer, ignoreCase = true)))

    val isAnswerConfigured: Boolean
        get() = if (type == QuestionType.FILL_BLANK) {
            effectiveFillBlankAnswer.isNotBlank() || effectiveAcceptedAnswers.any { it.isNotBlank() }
        } else {
            effectiveOptions.isNotEmpty() && effectiveAnswerIndex in effectiveOptions.indices
        }

    val isAnswerIndexValid: Boolean
        get() = (type != QuestionType.MCQ) || (options.isNotEmpty() && answer in options.indices)
}

object QuizJsonParser {

    fun validateAndParse(jsonString: String): Result<QuizSchema> {
        return runCatching {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("JSON content is empty")
            }
            val root = JSONObject(trimmed)

            val version = root.optInt("version", 1)
            val title = root.optString("title", "").trim()
            if (title.isEmpty()) {
                throw IllegalArgumentException("Quiz title is required")
            }

            val description = root.optString("description", "")
            val category = root.optString("category", "General").ifBlank { "General" }
            val difficulty = root.optString("difficulty", "Medium").ifBlank { "Medium" }
            val timeLimit = root.optInt("timeLimit", 0).coerceAtLeast(0)
            val shuffleQuestions = root.optBoolean("shuffleQuestions", false)
            val shuffleOptions = root.optBoolean("shuffleOptions", false)

            if (!root.has("questions")) {
                throw IllegalArgumentException("Missing 'questions' array in JSON")
            }

            val questionsArray = root.getJSONArray("questions")
            if (questionsArray.length() == 0) {
                throw IllegalArgumentException("Quiz must contain at least one question")
            }

            val questions = mutableListOf<QuestionSchema>()
            val ids = mutableSetOf<String>()

            for (i in 0 until questionsArray.length()) {
                val qObj = questionsArray.getJSONObject(i)
                val id = qObj.optString("id", "q_${i + 1}").ifBlank { "q_${i + 1}" }
                if (ids.contains(id)) {
                    ids.add("${id}_${i + 1}")
                } else {
                    ids.add(id)
                }

                val questionText = qObj.optString("question", "").trim()
                if (questionText.isEmpty()) {
                    throw IllegalArgumentException("Question #${i + 1} has empty text")
                }

                // Detect question type
                val rawType = qObj.optString("type", "").trim()
                val parsedType = QuestionType.fromString(rawType)

                val questionType = when {
                    parsedType != null -> parsedType
                    qObj.has("options") -> QuestionType.MCQ
                    qObj.has("answer") && (qObj.get("answer") is String || qObj.get("answer") is JSONArray) -> QuestionType.FILL_BLANK
                    rawType.isNotEmpty() -> throw IllegalArgumentException("Question #${i + 1} has invalid type: '$rawType'")
                    else -> throw IllegalArgumentException("Question #${i + 1} must specify 'type' or provide 'options'")
                }

                val points = qObj.optInt("points", 1).coerceAtLeast(1)
                val explanation = if (qObj.has("explanation") && !qObj.isNull("explanation")) {
                    qObj.optString("explanation", "").trim().ifEmpty { null }
                } else null

                if (questionType == QuestionType.MCQ) {
                    if (!qObj.has("options")) {
                        throw IllegalArgumentException("Question #${i + 1} is missing 'options' array")
                    }

                    val optionsArray = qObj.getJSONArray("options")
                    if (optionsArray.length() < 2) {
                        throw IllegalArgumentException("Question #${i + 1} must have at least 2 options")
                    }

                    val optionsList = mutableListOf<String>()
                    for (j in 0 until optionsArray.length()) {
                        optionsList.add(optionsArray.getString(j).trim())
                    }

                    val rawAnswer = if (qObj.has("answer")) qObj.get("answer") else -1
                    val answerIndex = when (rawAnswer) {
                        is Number -> rawAnswer.toInt()
                        is String -> {
                            val trimmed = rawAnswer.trim()
                            val asInt = trimmed.toIntOrNull()
                            if (asInt != null) {
                                asInt
                            } else if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'Z') {
                                trimmed[0].uppercaseChar() - 'A'
                            } else {
                                val matchIdx = optionsList.indexOfFirst { it.trim().equals(trimmed, ignoreCase = true) }
                                if (matchIdx != -1) matchIdx else -1
                            }
                        }
                        else -> -1
                    }
                    if (answerIndex < 0 || answerIndex >= optionsList.size) {
                        throw IllegalArgumentException(
                            "Question #${i + 1} has invalid answer index $answerIndex (must be between 0 and ${optionsList.size - 1})"
                        )
                    }

                    // Check for AI verification fields (safe reading)
                    val isVerified = qObj.optBoolean("is_verified", qObj.optBoolean("isVerified", false))
                    val verifiedAnswerIndex = when {
                        qObj.has("verified_answer_index") -> qObj.optInt("verified_answer_index")
                        qObj.has("verifiedAnswerIndex") -> qObj.optInt("verifiedAnswerIndex")
                        qObj.has("verified_answer") && qObj.get("verified_answer") is Number -> qObj.optInt("verified_answer")
                        qObj.has("verifiedAnswer") && qObj.get("verifiedAnswer") is Number -> qObj.optInt("verifiedAnswer")
                        else -> null
                    }
                    val confidence = qObj.optDouble("confidence", qObj.optDouble("verificationConfidence", 0.0))
                    val reason = qObj.optString("reason", qObj.optString("verificationReason", "")).ifBlank { null }
                    val needsReview = qObj.optBoolean("needs_review", qObj.optBoolean("needsReview", false))
                    val verifiedExplanation = qObj.optString("corrected_explanation", qObj.optString("verifiedExplanation", "")).ifBlank { null }
                    val lastVerifiedAt = if (qObj.has("last_verified_at")) qObj.optLong("last_verified_at") else if (qObj.has("lastVerifiedAt")) qObj.optLong("lastVerifiedAt") else null

                    val correctedOptionsList = if (qObj.has("corrected_options")) {
                        val corrArr = qObj.getJSONArray("corrected_options")
                        val list = mutableListOf<String>()
                        for (idx in 0 until corrArr.length()) list.add(corrArr.getString(idx).trim())
                        list
                    } else null

                    questions.add(
                        QuestionSchema(
                            id = id,
                            type = QuestionType.MCQ,
                            question = questionText,
                            options = optionsList,
                            answer = answerIndex,
                            points = points,
                            explanation = explanation,
                            verifiedAnswerIndex = verifiedAnswerIndex,
                            isVerified = isVerified,
                            verificationConfidence = confidence,
                            verificationReason = reason,
                            needsReview = needsReview,
                            verifiedExplanation = verifiedExplanation,
                            lastVerifiedAt = lastVerifiedAt,
                            correctedOptions = correctedOptionsList
                        )
                    )
                } else {
                    // Fill-in-the-blank question: allow "answer": "" or missing/empty answers
                    val acceptedAnswers = mutableListOf<String>()
                    val rawAnswer = if (qObj.has("answer")) qObj.get("answer") else ""

                    when (rawAnswer) {
                        is JSONArray -> {
                            for (j in 0 until rawAnswer.length()) {
                                val item = rawAnswer.getString(j).trim()
                                if (item.isNotEmpty() && !acceptedAnswers.contains(item)) {
                                    acceptedAnswers.add(item)
                                }
                            }
                        }
                        is String -> {
                            val str = rawAnswer.trim()
                            if (str.isNotEmpty()) {
                                acceptedAnswers.add(str)
                            }
                        }
                        else -> {
                            val str = rawAnswer.toString().trim()
                            if (str.isNotEmpty() && str != "null") {
                                acceptedAnswers.add(str)
                            }
                        }
                    }

                    // Optional extra accepted answers array
                    if (qObj.has("acceptedAnswers")) {
                        val extraArr = qObj.getJSONArray("acceptedAnswers")
                        for (j in 0 until extraArr.length()) {
                            val item = extraArr.getString(j).trim()
                            if (item.isNotEmpty() && !acceptedAnswers.contains(item)) {
                                acceptedAnswers.add(item)
                            }
                        }
                    }

                    // For type = "fill_blank", allow "answer": "" (do NOT require a non-empty answer)
                    val primaryAnswer = acceptedAnswers.firstOrNull() ?: ""

                    // Check for AI verification fields for fill_blank
                    val isVerified = qObj.optBoolean("is_verified", qObj.optBoolean("isVerified", false))
                    val verifiedFillBlankAnswer = when {
                        qObj.has("verified_fill_blank_answer") -> qObj.optString("verified_fill_blank_answer")
                        qObj.has("verifiedFillBlankAnswer") -> qObj.optString("verifiedFillBlankAnswer")
                        qObj.has("verified_answer") && qObj.get("verified_answer") is String -> qObj.optString("verified_answer")
                        qObj.has("verifiedAnswer") && qObj.get("verifiedAnswer") is String -> qObj.optString("verifiedAnswer")
                        else -> null
                    }?.ifBlank { null }

                    val verifiedAcceptedList = if (qObj.has("verified_accepted_answers")) {
                        val vArr = qObj.getJSONArray("verified_accepted_answers")
                        val list = mutableListOf<String>()
                        for (idx in 0 until vArr.length()) list.add(vArr.getString(idx).trim())
                        list
                    } else null

                    val confidence = qObj.optDouble("confidence", qObj.optDouble("verificationConfidence", 0.0))
                    val reason = qObj.optString("reason", qObj.optString("verificationReason", "")).ifBlank { null }
                    val needsReview = qObj.optBoolean("needs_review", qObj.optBoolean("needsReview", false))
                    val verifiedExplanation = qObj.optString("corrected_explanation", qObj.optString("verifiedExplanation", "")).ifBlank { null }
                    val lastVerifiedAt = if (qObj.has("last_verified_at")) qObj.optLong("last_verified_at") else if (qObj.has("lastVerifiedAt")) qObj.optLong("lastVerifiedAt") else null

                    questions.add(
                        QuestionSchema(
                            id = id,
                            type = QuestionType.FILL_BLANK,
                            question = questionText,
                            options = emptyList(),
                            answer = 0,
                            fillBlankAnswer = primaryAnswer,
                            acceptedAnswers = acceptedAnswers,
                            points = points,
                            explanation = explanation,
                            verifiedFillBlankAnswer = verifiedFillBlankAnswer,
                            verifiedAcceptedAnswers = verifiedAcceptedList,
                            isVerified = isVerified,
                            verificationConfidence = confidence,
                            verificationReason = reason,
                            needsReview = needsReview,
                            verifiedExplanation = verifiedExplanation,
                            lastVerifiedAt = lastVerifiedAt
                        )
                    )
                }
            }

            QuizSchema(
                version = version,
                title = title,
                description = description,
                category = category,
                difficulty = difficulty,
                timeLimit = timeLimit,
                shuffleQuestions = shuffleQuestions,
                shuffleOptions = shuffleOptions,
                questions = questions
            )
        }
    }

    fun toJsonString(quiz: QuizSchema, indentSpaces: Int = 2): String {
        val root = JSONObject()
        root.put("version", quiz.version)
        root.put("title", quiz.title)
        root.put("description", quiz.description)
        root.put("category", quiz.category)
        root.put("difficulty", quiz.difficulty)
        root.put("timeLimit", quiz.timeLimit)
        root.put("shuffleQuestions", quiz.shuffleQuestions)
        root.put("shuffleOptions", quiz.shuffleOptions)

        val questionsArray = JSONArray()
        quiz.questions.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("question", q.question)

            if (q.type == QuestionType.FILL_BLANK) {
                qObj.put("type", "fill_blank")
                if (q.acceptedAnswers.size > 1) {
                    val ansArr = JSONArray()
                    q.acceptedAnswers.forEach { ansArr.put(it) }
                    qObj.put("answer", ansArr)
                } else {
                    qObj.put("answer", q.fillBlankAnswer.ifEmpty { q.acceptedAnswers.firstOrNull() ?: "" })
                }
                if (q.isVerified && q.verifiedFillBlankAnswer != null) {
                    qObj.put("verified_answer", q.verifiedFillBlankAnswer)
                    qObj.put("verified_fill_blank_answer", q.verifiedFillBlankAnswer)
                }
                if (q.isVerified && !q.verifiedAcceptedAnswers.isNullOrEmpty()) {
                    val vArr = JSONArray()
                    q.verifiedAcceptedAnswers.forEach { vArr.put(it) }
                    qObj.put("verified_accepted_answers", vArr)
                }
            } else {
                qObj.put("type", "mcq")
                val optionsArr = JSONArray()
                q.options.forEach { opt -> optionsArr.put(opt) }
                qObj.put("options", optionsArr)
                qObj.put("answer", q.answer)
                if (q.isVerified && q.verifiedAnswerIndex != null) {
                    qObj.put("verified_answer", q.verifiedAnswerIndex)
                    qObj.put("verified_answer_index", q.verifiedAnswerIndex)
                }
                if (q.isVerified && !q.correctedOptions.isNullOrEmpty()) {
                    val corrArr = JSONArray()
                    q.correctedOptions.forEach { corrArr.put(it) }
                    qObj.put("corrected_options", corrArr)
                }
            }

            if (q.isVerified) {
                qObj.put("is_verified", true)
                if (q.verificationConfidence > 0) qObj.put("confidence", q.verificationConfidence)
                if (q.verificationReason != null) qObj.put("reason", q.verificationReason)
                if (q.needsReview) qObj.put("needs_review", true)
                if (q.verifiedExplanation != null) qObj.put("corrected_explanation", q.verifiedExplanation)
                if (q.lastVerifiedAt != null) qObj.put("last_verified_at", q.lastVerifiedAt)
            }

            qObj.put("points", q.points)
            if (q.explanation != null) {
                qObj.put("explanation", q.explanation)
            }
            questionsArray.put(qObj)
        }
        root.put("questions", questionsArray)

        return root.toString(indentSpaces)
    }

    /**
     * Lenient parser designed for the Quiz Editor and loading existing/imported/legacy quizzes.
     * Never crashes on missing optional fields, missing question types, or out-of-range answer indices.
     * Preserves question properties so the user can inspect, correct, and edit them safely.
     */
    fun parseLenient(jsonString: String): Result<QuizSchema> {
        return runCatching {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("JSON content is empty")
            }
            val root = JSONObject(trimmed)

            val version = root.optInt("version", 1)
            val title = root.optString("title", "").trim().ifBlank { "Untitled Quiz" }
            val description = root.optString("description", "")
            val category = root.optString("category", "General").ifBlank { "General" }
            val difficulty = root.optString("difficulty", "Medium").ifBlank { "Medium" }
            val timeLimit = root.optInt("timeLimit", 0).coerceAtLeast(0)
            val shuffleQuestions = root.optBoolean("shuffleQuestions", false)
            val shuffleOptions = root.optBoolean("shuffleOptions", false)

            val questions = mutableListOf<QuestionSchema>()
            val questionsArray = root.optJSONArray("questions")

            if (questionsArray != null) {
                val ids = mutableSetOf<String>()
                for (i in 0 until questionsArray.length()) {
                    val qObj = questionsArray.optJSONObject(i) ?: continue
                    val id = qObj.optString("id", "q_${i + 1}").ifBlank { "q_${i + 1}" }
                    val uniqueId = if (ids.add(id)) id else "${id}_${i + 1}"

                    val questionText = qObj.optString("question", "").trim().ifBlank { "Question ${i + 1}" }

                    // Detect question type with full backward compatibility
                    val rawType = qObj.optString("type", "").trim()
                    val parsedType = QuestionType.fromString(rawType)
                    val questionType = when {
                        parsedType != null -> parsedType
                        qObj.has("options") -> QuestionType.MCQ
                        qObj.has("answer") && (qObj.get("answer") is String || qObj.get("answer") is JSONArray) -> QuestionType.FILL_BLANK
                        else -> if (qObj.has("options")) QuestionType.MCQ else QuestionType.FILL_BLANK
                    }

                    val points = qObj.optInt("points", 1).coerceAtLeast(1)
                    val explanation = if (qObj.has("explanation") && !qObj.isNull("explanation")) {
                        qObj.optString("explanation", "").trim().ifEmpty { null }
                    } else null

                    // Verification fields
                    val isVerified = qObj.optBoolean("is_verified", qObj.optBoolean("isVerified", false))
                    val confidence = qObj.optDouble("confidence", qObj.optDouble("verificationConfidence", 0.0))
                    val reason = qObj.optString("reason", qObj.optString("verificationReason", "")).ifBlank { null }
                    val needsReview = qObj.optBoolean("needs_review", qObj.optBoolean("needsReview", false))
                    val verifiedExplanation = qObj.optString("corrected_explanation", qObj.optString("verifiedExplanation", "")).ifBlank { null }
                    val lastVerifiedAt = if (qObj.has("last_verified_at")) qObj.optLong("last_verified_at") else if (qObj.has("lastVerifiedAt")) qObj.optLong("lastVerifiedAt") else null

                    if (questionType == QuestionType.MCQ) {
                        val optionsList = mutableListOf<String>()
                        val optionsArray = qObj.optJSONArray("options")
                        if (optionsArray != null) {
                            for (j in 0 until optionsArray.length()) {
                                optionsList.add(optionsArray.opt(j)?.toString()?.trim() ?: "")
                            }
                        }
                        if (optionsList.isEmpty()) {
                            optionsList.add("Option A")
                            optionsList.add("Option B")
                        }

                        // Parse answer index safely:
                        val rawAnswer = if (qObj.has("answer")) qObj.get("answer") else 0
                        val answerIndex = when (rawAnswer) {
                            is Number -> rawAnswer.toInt()
                            is String -> {
                                val t = rawAnswer.trim()
                                val asInt = t.toIntOrNull()
                                if (asInt != null) {
                                    asInt
                                } else if (t.length == 1 && t[0].uppercaseChar() in 'A'..'Z') {
                                    t[0].uppercaseChar() - 'A'
                                } else {
                                    val idx = optionsList.indexOfFirst { it.trim().equals(t, ignoreCase = true) }
                                    if (idx != -1) idx else 0
                                }
                            }
                            else -> 0
                        }

                        val verifiedAnswerIndex = when {
                            qObj.has("verified_answer_index") -> qObj.optInt("verified_answer_index")
                            qObj.has("verifiedAnswerIndex") -> qObj.optInt("verifiedAnswerIndex")
                            qObj.has("verified_answer") && qObj.get("verified_answer") is Number -> qObj.optInt("verified_answer")
                            qObj.has("verifiedAnswer") && qObj.get("verifiedAnswer") is Number -> qObj.optInt("verifiedAnswer")
                            else -> null
                        }

                        val correctedOptionsList = if (qObj.has("corrected_options")) {
                            val corrArr = qObj.getJSONArray("corrected_options")
                            val list = mutableListOf<String>()
                            for (idx in 0 until corrArr.length()) list.add(corrArr.getString(idx).trim())
                            list
                        } else null

                        questions.add(
                            QuestionSchema(
                                id = uniqueId,
                                type = QuestionType.MCQ,
                                question = questionText,
                                options = optionsList,
                                answer = answerIndex,
                                points = points,
                                explanation = explanation,
                                verifiedAnswerIndex = verifiedAnswerIndex,
                                isVerified = isVerified,
                                verificationConfidence = confidence,
                                verificationReason = reason,
                                needsReview = needsReview,
                                verifiedExplanation = verifiedExplanation,
                                lastVerifiedAt = lastVerifiedAt,
                                correctedOptions = correctedOptionsList
                            )
                        )
                    } else {
                        // FILL_BLANK
                        val acceptedAnswers = mutableListOf<String>()
                        val rawAnswer = if (qObj.has("answer")) qObj.get("answer") else ""
                        when (rawAnswer) {
                            is JSONArray -> {
                                for (j in 0 until rawAnswer.length()) {
                                    val item = rawAnswer.opt(j)?.toString()?.trim() ?: ""
                                    if (item.isNotEmpty() && !acceptedAnswers.contains(item)) {
                                        acceptedAnswers.add(item)
                                    }
                                }
                            }
                            is String -> {
                                val s = rawAnswer.trim()
                                if (s.isNotEmpty()) acceptedAnswers.add(s)
                            }
                            is Number -> {
                                val s = rawAnswer.toString().trim()
                                if (s.isNotEmpty()) acceptedAnswers.add(s)
                            }
                            else -> {
                                val s = rawAnswer.toString().trim()
                                if (s.isNotEmpty() && s != "null") acceptedAnswers.add(s)
                            }
                        }

                        val extraArr = qObj.optJSONArray("acceptedAnswers") ?: qObj.optJSONArray("accepted_answers")
                        if (extraArr != null) {
                            for (j in 0 until extraArr.length()) {
                                val item = extraArr.opt(j)?.toString()?.trim() ?: ""
                                if (item.isNotEmpty() && !acceptedAnswers.contains(item)) {
                                    acceptedAnswers.add(item)
                                }
                            }
                        }

                        val primaryAnswer = qObj.optString("fillBlankAnswer", "").trim().ifEmpty {
                            qObj.optString("fill_blank_answer", "").trim().ifEmpty {
                                acceptedAnswers.firstOrNull() ?: ""
                            }
                        }
                        if (primaryAnswer.isNotEmpty() && !acceptedAnswers.contains(primaryAnswer)) {
                            acceptedAnswers.add(0, primaryAnswer)
                        }

                        val verifiedFillBlankAnswer = when {
                            qObj.has("verified_fill_blank_answer") -> qObj.optString("verified_fill_blank_answer")
                            qObj.has("verifiedFillBlankAnswer") -> qObj.optString("verifiedFillBlankAnswer")
                            qObj.has("verified_answer") && qObj.get("verified_answer") is String -> qObj.optString("verified_answer")
                            qObj.has("verifiedAnswer") && qObj.get("verifiedAnswer") is String -> qObj.optString("verifiedAnswer")
                            else -> null
                        }?.ifBlank { null }

                        val verifiedAcceptedList = if (qObj.has("verified_accepted_answers")) {
                            val vArr = qObj.getJSONArray("verified_accepted_answers")
                            val list = mutableListOf<String>()
                            for (idx in 0 until vArr.length()) list.add(vArr.getString(idx).trim())
                            list
                        } else null

                        questions.add(
                            QuestionSchema(
                                id = uniqueId,
                                type = QuestionType.FILL_BLANK,
                                question = questionText,
                                options = emptyList(),
                                answer = 0,
                                fillBlankAnswer = primaryAnswer,
                                acceptedAnswers = acceptedAnswers,
                                points = points,
                                explanation = explanation,
                                verifiedFillBlankAnswer = verifiedFillBlankAnswer,
                                verifiedAcceptedAnswers = verifiedAcceptedList,
                                isVerified = isVerified,
                                verificationConfidence = confidence,
                                verificationReason = reason,
                                needsReview = needsReview,
                                verifiedExplanation = verifiedExplanation,
                                lastVerifiedAt = lastVerifiedAt
                            )
                        )
                    }
                }
            }

            QuizSchema(
                version = version,
                title = title,
                description = description,
                category = category,
                difficulty = difficulty,
                timeLimit = timeLimit,
                shuffleQuestions = shuffleQuestions,
                shuffleOptions = shuffleOptions,
                questions = questions
            )
        }
    }

    fun validateSchemaForSaving(schema: QuizSchema): List<String> {
        val errors = mutableListOf<String>()
        if (schema.title.isBlank()) {
            errors.add("Quiz title cannot be empty")
        }
        if (schema.timeLimit < 0) {
            errors.add("Time limit cannot be negative")
        }
        if (schema.questions.isEmpty()) {
            errors.add("Quiz must contain at least one question")
        }
        schema.questions.forEachIndexed { idx, q ->
            val num = idx + 1
            if (q.question.isBlank()) {
                errors.add("Question #$num: Question text cannot be empty")
            }
            if (q.points < 1) {
                errors.add("Question #$num: Points must be at least 1")
            }
            if (q.type == QuestionType.MCQ) {
                if (q.options.size < 2) {
                    errors.add("Question #$num: Multiple choice question must have at least 2 options")
                } else if (q.options.any { it.isBlank() }) {
                    errors.add("Question #$num: All options must contain text")
                }
                if (q.options.isNotEmpty() && q.answer !in q.options.indices) {
                    errors.add("Question #$num: Answer index (${q.answer}) is out of bounds (0..${q.options.lastIndex})")
                }
            } else if (q.type == QuestionType.FILL_BLANK) {
                if (q.fillBlankAnswer.isBlank() && q.acceptedAnswers.isEmpty()) {
                    errors.add("Question #$num: Fill-in-the-blank question requires at least one accepted answer")
                }
            }
        }
        return errors
    }
}
