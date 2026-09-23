package com.example.data.model

import org.json.JSONObject

/**
 * Structured result of an AI answer evaluation.
 */
data class AiEvaluationResult(
    val isCorrect: Boolean,
    val confidence: Double,
    val reason: String,
    val banglaExplanation: String,
    val matchedAnswer: String? = null
) {
    companion object {
        fun fromJson(jsonStr: String, fallbackAcceptedAnswer: String = ""): AiEvaluationResult {
            val cleanJson = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(cleanJson)
            return AiEvaluationResult(
                isCorrect = obj.optBoolean("isCorrect", false),
                confidence = obj.optDouble("confidence", 0.9),
                reason = obj.optString("reason", ""),
                banglaExplanation = obj.optString("banglaExplanation", ""),
                matchedAnswer = obj.optString("matchedAnswer", fallbackAcceptedAnswer)
            )
        }
    }
}
