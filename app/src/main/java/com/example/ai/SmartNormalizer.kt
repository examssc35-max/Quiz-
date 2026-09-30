package com.example.ai

import com.example.data.model.AiEvaluationResult
import java.text.Normalizer
import java.util.Locale

/**
 * Smart local normalizer for educational quiz answers.
 * Implements Step 1 (Local exact/normalized comparison) and Step 2 (Local semantic-safe rules)
 * of the answer evaluation pipeline.
 *
 * Normalizes harmless formatting differences:
 * - leading/trailing spaces
 * - repeated harmless spaces
 * - harmless English capitalization ("Daily", "daily", "DAILY")
 * - Unicode normalization (NFC)
 * - equivalent basic punctuation (wrapping quotes, harmless trailing periods)
 * - English digits ↔ Bengali digits ↔ Arabic-Indic digits
 * - Comma separators in numbers (1,000 ↔ 1000, ১,০০০ ↔ ১০০০)
 * - Decimal variations & range dashes
 * - Common units (kg ↔ কেজি, km ↔ কিমি, % ↔ শতাংশ, etc.)
 *
 * Preserves Bengali script and does NOT alter semantic word meaning or apply unsafe stemming.
 */
object SmartNormalizer {

    /**
     * Safe answer normalizer implementing Step 1 exact/normalized comparison rules.
     * Applies NFC Unicode normalization, space trimming and collapsing, harmless English lowercasing,
     * quote cleaning, and harmless trailing punctuation stripping without altering meaning.
     */
    fun normalizeSafe(text: String): String {
        if (text.isBlank()) return ""

        // 1. Unicode NFC Normalization
        var str = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)

        // 2. Collapse internal multiple spaces
        str = str.replace(Regex("\\s+"), " ")

        // 3. Normalize quotes and apostrophes
        str = str.replace(Regex("[‘’`']"), "'")
            .replace(Regex("[\"“”]"), "\"")

        // 4. Strip harmless wrapping quotes or parentheses
        if ((str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) ||
            (str.startsWith("'") && str.endsWith("'") && str.length >= 2) ||
            (str.startsWith("(") && str.endsWith(")") && str.length >= 2)
        ) {
            str = str.substring(1, str.length - 1).trim()
        }

        // 5. Strip harmless trailing punctuation like '.' or ',' if user typed it at the end of a single word/phrase
        // (unless it's an abbreviation like "e.g." or a decimal number)
        if (str.endsWith(".") && !str.contains("..") && !Regex("\\d\\.\\d?$").containsMatchIn(str) && str.count { it == '.' } == 1) {
            str = str.removeSuffix(".").trim()
        }
        if (str.endsWith(",") || str.endsWith("!")) {
            str = str.dropLast(1).trim()
        }

        // 6. Harmless English lowercasing (no-op for Bengali)
        str = str.lowercase(Locale.ROOT)

        return str.trim()
    }

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
     * Step 1 & Step 2 deterministic check:
     * Compares student's answer against all accepted answers.
     * Evaluates:
     * 1. Exact case-sensitive match
     * 2. Case-insensitive harmless English match (e.g. "Daily" vs "daily")
     * 3. Safe normalized match (normalizeSafe: quotes, spaces, trailing period)
     * 4. Smart number/digit/unit normalized match (normalize: 1000 kg vs ১,০০০ কেজি, 60-70% vs ৬০–৭০%)
     *
     * Returns Pair(isMatch: Boolean, matchedAnswer: String?)
     * Highest priority in evaluation pipeline: AI is NEVER called when this returns true!
     */
    fun checkExactOrNormalizedMatch(
        userAnswer: String,
        acceptedAnswers: List<String>
    ): Pair<Boolean, String?> {
        val trimmedUser = userAnswer.trim()
        if (trimmedUser.isEmpty() || acceptedAnswers.isEmpty()) {
            return Pair(false, null)
        }

        // 1. Exact match (highest priority)
        val exactMatch = acceptedAnswers.firstOrNull { it.trim().equals(trimmedUser, ignoreCase = false) }
        if (exactMatch != null) {
            return Pair(true, exactMatch)
        }

        // 2. English case-insensitive trimmed match (e.g. "Daily" == "daily", "DAILY" == "daily")
        val caseInsensitiveMatch = acceptedAnswers.firstOrNull { it.trim().equals(trimmedUser, ignoreCase = true) }
        if (caseInsensitiveMatch != null) {
            return Pair(true, caseInsensitiveMatch)
        }

        // 3. Safe normalized match (quotes stripped, NFC normalized, harmless trailing period stripped)
        val safeUser = normalizeSafe(trimmedUser)
        if (safeUser.isNotEmpty()) {
            val safeMatch = acceptedAnswers.firstOrNull { normalizeSafe(it) == safeUser }
            if (safeMatch != null) {
                return Pair(true, safeMatch)
            }
        }

        // 4. Number, digit, dash, and unit equivalence (Step 2 semantic-safe rules)
        val fullNormUser = normalize(trimmedUser)
        if (fullNormUser.isNotEmpty()) {
            val fullNormMatch = acceptedAnswers.firstOrNull { normalize(it) == fullNormUser }
            if (fullNormMatch != null) {
                return Pair(true, fullNormMatch)
            }
        }

        return Pair(false, null)
    }

    /**
     * Checks if the user's answer is equivalent to any accepted answer after smart normalization.
     */
    fun isEquivalentlyNormalized(userAnswer: String, acceptedAnswers: List<String>): Boolean {
        return checkExactOrNormalizedMatch(userAnswer, acceptedAnswers).first
    }

    /**
     * Finds the first accepted answer that matches the normalized user answer, or null.
     */
    fun findMatchedAnswer(userAnswer: String, acceptedAnswers: List<String>): String? {
        return checkExactOrNormalizedMatch(userAnswer, acceptedAnswers).second
    }

    /**
     * Creates a high quality fallback AI evaluation result using smart local normalization.
     * Guaranteed never to crash, handles offline mode, and provides student-friendly Bangla explanation.
     */
    fun createLocalEvaluation(
        questionText: String,
        acceptedAnswers: List<String>,
        userAnswer: String,
        offlineNote: Boolean = false,
        evaluationType: String? = null
    ): AiEvaluationResult {
        val trimmed = userAnswer.trim()
        val allAccepted = acceptedAnswers.filter { it.isNotBlank() }
        val (isMatch, matched) = checkExactOrNormalizedMatch(trimmed, allAccepted)
        val chosenMatched = matched ?: allAccepted.firstOrNull().orEmpty()

        val isExact = trimmed.equals(chosenMatched, ignoreCase = false)
        val evalType = evaluationType ?: if (isMatch) {
            if (isExact) "exact_match" else "normalized_match"
        } else {
            if (offlineNote) "ai_unavailable" else "incorrect"
        }

        val noteSuffix = if (offlineNote) " (অফলাইন মোডে যাচাইকৃত)" else ""

        val banglaExpl = if (isMatch) {
            "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$chosenMatched” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে$noteSuffix।"
        } else {
            val acceptedListStr = if (allAccepted.size > 1) {
                allAccepted.joinToString(", ")
            } else {
                chosenMatched.ifEmpty { "উপযুক্ত শব্দ" }
            }
            "তোমার উত্তর “$trimmed” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: “$acceptedListStr”$noteSuffix।"
        }

        return AiEvaluationResult(
            isCorrect = isMatch,
            confidence = if (isMatch) 1.0 else 0.80,
            evaluationType = evalType,
            matchedAnswer = chosenMatched,
            verifiedAnswer = chosenMatched,
            needsReview = !isMatch && offlineNote,
            explanation_bn = banglaExpl,
            reason = if (isMatch) "Deterministic local exact/normalized match" else "Local match not found (AI unavailable or offline)",
            banglaExplanation = banglaExpl,
            decision = if (isMatch) "correct" else if (offlineNote) "uncertain" else "wrong"
        )
    }
}
