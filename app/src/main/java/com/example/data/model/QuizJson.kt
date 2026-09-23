package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class QuestionType {
    MCQ,
    FILL_BLANK;

    companion object {
        fun fromString(typeStr: String?): QuestionType? {
            return when (typeStr?.lowercase()?.trim()) {
                "mcq" -> MCQ
                "fill_blank", "fill-in-the-blank", "fillblank", "fill_in_the_blank" -> FILL_BLANK
                else -> null
            }
        }
    }
}

object AnswerComparison {
    fun normalize(text: String): String {
        return text.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    fun isAnswerCorrect(userAnswer: String, acceptedAnswers: List<String>): Boolean {
        val normalizedUser = normalize(userAnswer)
        if (normalizedUser.isEmpty()) return false
        return acceptedAnswers.any { accepted ->
            normalize(accepted) == normalizedUser
        }
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
    val answer: Int = 0, // 0-based index of correct option for MCQ
    val fillBlankAnswer: String = "", // Primary correct answer for FILL_BLANK
    val acceptedAnswers: List<String> = emptyList(), // All accepted answers for FILL_BLANK
    val points: Int = 1,
    val explanation: String? = null
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

    val isAnswerConfigured: Boolean
        get() = if (type == QuestionType.FILL_BLANK) {
            fillBlankAnswer.isNotBlank() || acceptedAnswers.any { it.isNotBlank() }
        } else {
            options.isNotEmpty() && answer in options.indices
        }
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

                    val answerIndex = qObj.optInt("answer", -1)
                    if (answerIndex < 0 || answerIndex >= optionsList.size) {
                        throw IllegalArgumentException(
                            "Question #${i + 1} has invalid answer index $answerIndex (must be between 0 and ${optionsList.size - 1})"
                        )
                    }

                    questions.add(
                        QuestionSchema(
                            id = id,
                            type = QuestionType.MCQ,
                            question = questionText,
                            options = optionsList,
                            answer = answerIndex,
                            points = points,
                            explanation = explanation
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
                            explanation = explanation
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
            } else {
                qObj.put("type", "mcq")
                val optionsArr = JSONArray()
                q.options.forEach { opt -> optionsArr.put(opt) }
                qObj.put("options", optionsArr)
                qObj.put("answer", q.answer)
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
}
