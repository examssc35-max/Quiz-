package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

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
    val question: String,
    val options: List<String>,
    val answer: Int, // 0-based index of correct option
    val points: Int = 1,
    val explanation: String? = null
)

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
                    // Make unique if duplicated
                    ids.add("${id}_${i + 1}")
                } else {
                    ids.add(id)
                }

                val questionText = qObj.optString("question", "").trim()
                if (questionText.isEmpty()) {
                    throw IllegalArgumentException("Question #${i + 1} has empty text")
                }

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

                val points = qObj.optInt("points", 1).coerceAtLeast(1)
                val explanation = if (qObj.has("explanation") && !qObj.isNull("explanation")) {
                    qObj.optString("explanation", "").trim().ifEmpty { null }
                } else null

                questions.add(
                    QuestionSchema(
                        id = id,
                        question = questionText,
                        options = optionsList,
                        answer = answerIndex,
                        points = points,
                        explanation = explanation
                    )
                )
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

            val optionsArr = JSONArray()
            q.options.forEach { opt -> optionsArr.put(opt) }
            qObj.put("options", optionsArr)

            qObj.put("answer", q.answer)
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
