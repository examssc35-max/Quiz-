package com.example.data.model

import org.json.JSONObject

/**
 * Structured result of an AI answer evaluation.
 * Acts as an intelligent educational agent result, capable of challenging incorrect JSON answers.
 */
data class AiEvaluationResult(
    val isCorrect: Boolean,
    val confidence: Double = 0.95,
    val evaluationType: String = if (isCorrect) "exact_match" else "incorrect",
    val matchedAnswer: String? = null,
    val verifiedAnswer: String? = null,
    val needsReview: Boolean = false,
    val explanation_bn: String = "",
    val decision: String = if (isCorrect) "correct" else if (needsReview) "uncertain" else "wrong",
    val userAnswer: String = "",
    val jsonAnswer: String = "",
    val jsonAnswerCorrect: Boolean = true,
    val correctAnswer: String = verifiedAnswer ?: matchedAnswer.orEmpty(),
    val correctOptionIndex: Int? = null,
    val reason: String = "",
    val banglaExplanation: String = explanation_bn,
    val warning: String? = null
) {
    val effectiveExplanationBn: String
        get() = explanation_bn.ifBlank { banglaExplanation }

    val effectiveBanglaExplanation: String
        get() = banglaExplanation.ifBlank { explanation_bn }

    companion object {
        fun fromJson(
            jsonStr: String,
            fallbackAcceptedAnswer: String = "",
            userAnswerInput: String = ""
        ): AiEvaluationResult {
            val cleanJson = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonContent = extractJsonObject(cleanJson)
            val obj = JSONObject(jsonContent)

            val isCorrect = obj.optBoolean("isCorrect", false)
            val confidence = obj.optDouble("confidence", if (isCorrect) 0.95 else 0.85).coerceIn(0.0, 1.0)
            val decision = obj.optString("decision", if (isCorrect) "correct" else "wrong")
            val userAnswer = obj.optString("userAnswer", userAnswerInput)
            val jsonAnswer = obj.optString("jsonAnswer", fallbackAcceptedAnswer)
            val jsonAnswerCorrect = obj.optBoolean("jsonAnswerCorrect", true)

            val matchedAnswer = obj.optString("matchedAnswer", fallbackAcceptedAnswer)
                .ifBlank { fallbackAcceptedAnswer }
            val verifiedAnswer = obj.optString("verifiedAnswer", obj.optString("correctAnswer", matchedAnswer))
                .ifBlank { matchedAnswer }
            val correctAnswer = verifiedAnswer

            val evaluationTypeRaw = obj.optString("evaluationType")
            val evaluationType = if (evaluationTypeRaw.isNotBlank()) {
                evaluationTypeRaw
            } else if (isCorrect) {
                "semantic_match"
            } else {
                "incorrect"
            }

            val needsReview = obj.optBoolean("needsReview", confidence < 0.70 || decision == "uncertain" || decision == "json_error")

            val explanationBn = obj.optString("explanation_bn")
                .ifBlank { obj.optString("banglaExplanation") }
                .ifBlank { obj.optString("explanation", "") }

            val correctOptionIndex = if (obj.has("correctOptionIndex")) obj.optInt("correctOptionIndex") else null
            val reason = obj.optString("reason", "")
            val warning = if (obj.has("warning") && obj.optString("warning").isNotBlank()) {
                obj.optString("warning")
            } else if (!jsonAnswerCorrect) {
                "The stored JSON answer appears to be incorrect."
            } else null

            return AiEvaluationResult(
                isCorrect = isCorrect,
                confidence = confidence,
                evaluationType = evaluationType,
                matchedAnswer = matchedAnswer,
                verifiedAnswer = verifiedAnswer,
                needsReview = needsReview,
                explanation_bn = explanationBn,
                decision = decision,
                userAnswer = userAnswer,
                jsonAnswer = jsonAnswer,
                jsonAnswerCorrect = jsonAnswerCorrect,
                correctAnswer = correctAnswer,
                correctOptionIndex = correctOptionIndex,
                reason = reason,
                banglaExplanation = explanationBn,
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
