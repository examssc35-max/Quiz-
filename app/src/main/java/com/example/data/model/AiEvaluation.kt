package com.example.data.model

import org.json.JSONObject

/**
 * Structured result of an AI answer evaluation.
 * Acts as an intelligent educational agent result, capable of challenging incorrect JSON answers.
 */
data class AiEvaluationResult(
    val isCorrect: Boolean,
    val confidence: Double = 0.95,
    val decision: String = if (isCorrect) "correct" else "wrong", // "correct", "wrong", "uncertain", "json_error"
    val userAnswer: String = "",
    val jsonAnswer: String = "",
    val jsonAnswerCorrect: Boolean = true,
    val correctAnswer: String = "",
    val correctOptionIndex: Int? = null,
    val matchedAnswer: String? = null,
    val reason: String = "",
    val banglaExplanation: String = "",
    val warning: String? = null
) {
    companion object {
        fun fromJson(jsonStr: String, fallbackAcceptedAnswer: String = ""): AiEvaluationResult {
            val cleanJson = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonContent = extractJsonObject(cleanJson)
            val obj = JSONObject(jsonContent)

            val isCorrect = obj.optBoolean("isCorrect", false)
            val confidence = obj.optDouble("confidence", if (isCorrect) 0.95 else 0.85)
            val decision = obj.optString("decision", if (isCorrect) "correct" else "wrong")
            val userAnswer = obj.optString("userAnswer", "")
            val jsonAnswer = obj.optString("jsonAnswer", fallbackAcceptedAnswer)
            val jsonAnswerCorrect = obj.optBoolean("jsonAnswerCorrect", true)
            val correctAnswer = obj.optString("correctAnswer", fallbackAcceptedAnswer)
            val correctOptionIndex = if (obj.has("correctOptionIndex")) obj.optInt("correctOptionIndex") else null
            val matchedAnswer = obj.optString("matchedAnswer", fallbackAcceptedAnswer)
            val reason = obj.optString("reason", "")
            val banglaExplanation = obj.optString("banglaExplanation", "")
            val warning = if (obj.has("warning") && obj.optString("warning").isNotBlank()) {
                obj.optString("warning")
            } else if (!jsonAnswerCorrect) {
                "The stored JSON answer appears to be incorrect."
            } else null

            return AiEvaluationResult(
                isCorrect = isCorrect,
                confidence = confidence,
                decision = decision,
                userAnswer = userAnswer,
                jsonAnswer = jsonAnswer,
                jsonAnswerCorrect = jsonAnswerCorrect,
                correctAnswer = correctAnswer,
                correctOptionIndex = correctOptionIndex,
                matchedAnswer = matchedAnswer.ifBlank { fallbackAcceptedAnswer },
                reason = reason,
                banglaExplanation = banglaExplanation,
                warning = warning
            )
        }

        private fun extractJsonObject(text: String): String {
            val firstBrace = text.indexOf('{')
            val lastBrace = text.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                return text.substring(firstBrace, lastBrace + 1)
            }
            return text
        }
    }
}
