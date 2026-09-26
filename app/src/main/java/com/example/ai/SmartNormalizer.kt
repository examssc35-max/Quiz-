package com.example.ai

import com.example.data.model.AiEvaluationResult
import java.util.Locale

/**
 * Smart local normalizer for educational quiz answers.
 * Normalizes harmless formatting differences before calling AI or during offline fallback:
 * - English digits ↔ Bengali digits ↔ Arabic-Indic digits
 * - Comma separators in numbers (1,000 ↔ 1000, ১,০০০ ↔ ১০০০)
 * - Decimal variations
 * - Dash variations (hyphen, en-dash, em-dash, minus, "to", "থেকে")
 * - Spaces around symbols and punctuation
 * - Common units (kg ↔ কেজি, km ↔ কিমি, % ↔ শতাংশ, etc.)
 * - Case insensitivity & whitespace trimming
 *
 * Does NOT alter semantic word meaning or apply aggressive spelling changes.
 */
object SmartNormalizer {

    /**
     * Translates Bengali digits ('০'..'৯') and Arabic-Indic digits ('٠'..'٩') to ASCII ('0'..'9').
     */
    fun normalizeDigits(input: String): String {
        val sb = java.lang.StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u09E6'..'\u09EF' -> sb.append((ch - '\u09E6' + '0'.code).toChar()) // Bengali
                in '\u0660'..'\u0669' -> sb.append((ch - '\u0660' + '0'.code).toChar()) // Arabic-Indic
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Full normalization pipeline for comparison.
     */
    fun normalize(text: String): String {
        var str = text.trim()
        if (str.isEmpty()) return ""

        // 1. Digits translation to 0-9
        str = normalizeDigits(str)

        // 2. Normalize commas in numbers: e.g. 1,000 -> 1000, 50,000 -> 50000
        str = str.replace(Regex("(?<=\\d),(?=\\d)"), "")

        // 3. Normalize various dashes, minus signs, and range indicators to a single '-'
        // Dash characters: \u002D (hyphen), \u2010, \u2011, \u2012, \u2013 (en-dash), \u2014 (em-dash), \u2015, \u2212 (minus), ~
        str = str.replace(Regex("(?<=\\d)\\s*(?:to|থেকে|–|—|−|-|~)\\s*(?=\\d)"), "-")
        str = str.replace(Regex("[\\u2010-\\u2015\\u2212]"), "-")

        // 4. Normalize spaces around hyphens, slashes, and percentage symbols
        str = str.replace(Regex("\\s*-\\s*"), "-")
        str = str.replace(Regex("\\s*/\\s*"), "/")
        str = str.replace(Regex("(?<=\\d)\\s*%(?=\\b|\\s|$)"), "%")

        // 5. Normalize common units into standard tokens
        str = normalizeUnits(str)

        // 6. Normalize quotes and apostrophes
        str = str.replace(Regex("[‘’`']"), "'")
            .replace(Regex("[\"“”]"), "\"")

        // 7. Strip wrapping quotation marks or parentheses if user entered them
        if ((str.startsWith("\"") && str.endsWith("\"")) ||
            (str.startsWith("'") && str.endsWith("'")) ||
            (str.startsWith("(") && str.endsWith(")"))
        ) {
            str = str.substring(1, str.length - 1).trim()
        }

        // 8. Collapse internal multiple whitespace to a single space and lowercase English
        str = str.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

        return str.trim()
    }

    /**
     * Maps common English and Bengali units to canonical token representations.
     * Uses non-ASCII boundary patterns instead of \b so Unicode Bengali characters match correctly.
     */
    private fun normalizeUnits(input: String): String {
        var s = input

        // Percentage: %, percent, percentage, শতাংশ, ভাগ (when following a number or range), পারসেন্ট
        s = s.replace(Regex("(?<=^|\\d|-|\\s)(?:percent|percentage|শতাংশ|পারসেন্ট|ভাগ)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), "%")

        // Kilogram: kg, kgs, kilogram, kilograms, কেজি, কিলোগ্রাম
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:kg|kgs|kilogram|kilograms|কেজি|কিলোগ্রাম)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_kg__")

        // Kilometer: km, kms, kilometer, kilometers, কিমি, কিলোমিটার
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:km|kms|kilometer|kilometers|কিমি|কিলোমিটার)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_km__")

        // Centimeter: cm, centimeter, centimeters, সেমি, সেন্টিমিটার
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:cm|centimeter|centimeters|সেমি|সেন্টিমিটার)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_cm__")

        // Meter: meter, meters, মিটার
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:meters|meter|মিটার|মি\\.)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_m__")
        s = s.replace(Regex("(?<=\\d)\\s*m(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_m__")

        // Gram: g, gm, gms, gram, grams, গ্রাম
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:gms|gm|grams|gram|গ্রাম)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_g__")
        s = s.replace(Regex("(?<=\\d)\\s*g(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_g__")

        // Liter: l, ltr, liter, liters, লিটার
        s = s.replace(Regex("(?<=^|\\d|\\s)(?:ltr|liters|liter|লিটার)(?=$|\\s|[.,;])", RegexOption.IGNORE_CASE), " __unit_l__")

        return s
    }

    /**
     * Checks if the user's answer is equivalent to any accepted answer after smart normalization.
     */
    fun isEquivalentlyNormalized(userAnswer: String, acceptedAnswers: List<String>): Boolean {
        val normalizedUser = normalize(userAnswer)
        if (normalizedUser.isEmpty()) return false

        return acceptedAnswers.any { accepted ->
            val normalizedAccepted = normalize(accepted)
            normalizedUser == normalizedAccepted
        }
    }

    /**
     * Finds the first accepted answer that matches the normalized user answer, or null.
     */
    fun findMatchedAnswer(userAnswer: String, acceptedAnswers: List<String>): String? {
        val normalizedUser = normalize(userAnswer)
        if (normalizedUser.isEmpty()) return null

        return acceptedAnswers.firstOrNull { accepted ->
            normalize(accepted) == normalizedUser
        }
    }

    /**
     * Creates a high quality fallback AI evaluation result using smart local normalization.
     */
    fun createLocalEvaluation(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String,
        offlineNote: Boolean = false
    ): AiEvaluationResult {
        val trimmed = userAnswer.trim()
        val isCorrect = isEquivalentlyNormalized(trimmed, acceptedAnswers)
        val matched = findMatchedAnswer(trimmed, acceptedAnswers) ?: acceptedAnswers.firstOrNull().orEmpty()

        val banglaExpl = if (isCorrect) {
            val noteSuffix = if (offlineNote) " (অফলাইন মোডে যাচাইকৃত)" else ""
            "তোমার উত্তরটি সঠিক। ‘$matched’ (বা সমমানের ‘$trimmed’) বাক্যের অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসেছে$noteSuffix।"
        } else {
            val acceptedListStr = if (acceptedAnswers.size > 1) {
                acceptedAnswers.joinToString(", ")
            } else {
                matched.ifEmpty { "উপযুক্ত শব্দ" }
            }
            "তোমার উত্তর ‘$trimmed’ এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও grammar অনুযায়ী এখানে সঠিক উত্তর: $acceptedListStr।"
        }

        return AiEvaluationResult(
            isCorrect = isCorrect,
            confidence = if (isCorrect) 0.98 else 0.85,
            reason = if (isCorrect) "Smart normalized match against accepted answer" else "Answer does not match expected answers",
            banglaExplanation = banglaExpl,
            matchedAnswer = matched
        )
    }
}
