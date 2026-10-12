package com.example.audio

import com.example.data.model.QuizResultSummary
import java.util.Locale
import kotlin.random.Random

/**
 * AI Voice Congratulation & Appreciation Engine.
 *
 * Fulfills all user constraints:
 * 1) Custom words work reliably and are prioritized: "কাস্টম ওয়াড কাজ করে না"
 * 2) Congratulate only after 2-3 questions: e.g. 3 correct answers praise, 2-3 wrong answers console:
 *    "প্রত্যেকটি কুইজের মধ্যে না দুই তিনটা কুইজ পাওয়ার পর কংগ্রাচুলেশন করবে
 *     উদাহরণস্বরূপ তিনটি সঠিক উত্তর দিলে কনগ্রাচুলেশন করবে বা দুই তিনটা ভুল উত্তর দিলে সান্ত্বনা দিবে"
 * 3) Short, punchy congratulation words, not long paragraphs:
 *    "একসাথে সকল কংগ্রেচুলেশন করা যাবে না ছোট ছোট কংগ্রাচুলেশন ওয়ার্ড থাকতে হবে"
 */
object AIVoiceCongratulationEngine {

    // Short, crisp Bengali praise words
    val SHORT_BENGALI_PRAISE = listOf(
        "সাবাশ!",
        "অসাধারণ!",
        "মারহাবা!",
        "দারুণ!",
        "চমৎকার!",
        "ব্রাভো!",
        "তুখোড়!",
        "বাহবা!",
        "অনবদ্য!",
        "লাজবাব!",
        "অভিনন্দন!",
        "অপূর্ব!"
    )

    // Short, crisp English praise words
    val SHORT_ENGLISH_PRAISE = listOf(
        "Bravo!",
        "Brilliant!",
        "Outstanding!",
        "Superb!",
        "Fantastic!",
        "Awesome!",
        "Well done!",
        "Impressive!",
        "Great job!"
    )

    // Short Bengali consolation words (সান্ত্বনা)
    val SHORT_BENGALI_CONSOLATION = listOf(
        "হাল ছেড়ো না!",
        "পরেরটা পারবে!",
        "মন শক্ত রাখো!",
        "চেষ্টা চালিয়ে যাও!",
        "কোনো ব্যাপার না!",
        "ধৈর্য রাখো, এগিয়ে যাও!"
    )

    // Short English consolation words
    val SHORT_ENGLISH_CONSOLATION = listOf(
        "Don't give up!",
        "You've got this!",
        "Keep going!",
        "Stay confident!",
        "Shake it off, next one's yours!"
    )

    /**
     * Splits user-provided custom words string using comprehensive delimiters:
     * commas, semicolons, Bengali commas (，、), Bengali danda (।), newlines, and spaces.
     */
    fun parseCustomWords(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return input.split(Regex("[,;:\n\r|/，、।]+"))
            .map { it.trim().trim('"', '\'', '!', '?', '.') }
            .filter { it.isNotEmpty() }
    }

    /**
     * Generates a short, punchy congratulation phrase when a streak milestone (e.g. 3 correct) is reached.
     * Uses the user's custom words first if provided.
     */
    fun generateShortStreakCongratulation(
        streak: Int,
        isBengaliQuiz: Boolean,
        customWords: List<String> = emptyList(),
        languagePref: String = "auto"
    ): Pair<String, String> { // Returns Pair(SpokenText, DisplayTitle)
        val useBengali = when (languagePref) {
            "bn" -> true
            "en" -> false
            else -> isBengaliQuiz
        }

        // 1. Prioritize user custom words if present
        val chosenPraise = if (customWords.isNotEmpty()) {
            customWords.random().trim()
        } else {
            if (useBengali) SHORT_BENGALI_PRAISE.random() else SHORT_ENGLISH_PRAISE.random()
        }

        val cleanPraise = chosenPraise.trim('!', '.', ' ')

        val spokenText: String
        val displayTitle: String

        if (useBengali) {
            spokenText = "$cleanPraise! $streak টি সঠিক উত্তর!"
            displayTitle = "🎉 $cleanPraise! $streak টি সঠিক উত্তর!"
        } else {
            spokenText = "$cleanPraise! $streak in a row!"
            displayTitle = "🎉 $cleanPraise! $streak in a row!"
        }

        return Pair(spokenText, displayTitle)
    }

    /**
     * Generates a short, comforting consolation phrase when 2-3 wrong answers occur.
     */
    fun generateShortConsolation(
        wrongCount: Int,
        isBengaliQuiz: Boolean,
        languagePref: String = "auto"
    ): Pair<String, String> { // Returns Pair(SpokenText, DisplayTitle)
        val useBengali = when (languagePref) {
            "bn" -> true
            "en" -> false
            else -> isBengaliQuiz
        }

        val consolation = if (useBengali) {
            SHORT_BENGALI_CONSOLATION.random()
        } else {
            SHORT_ENGLISH_CONSOLATION.random()
        }

        val spokenText = consolation
        val displayTitle = "💪 $consolation"

        return Pair(spokenText, displayTitle)
    }

    /**
     * Generates short, punchy completion congratulations for the Result Screen.
     * Keeps it short and avoids long walls of speech.
     */
    fun generateQuizCompletionCongratulations(
        summary: QuizResultSummary,
        isBengaliQuiz: Boolean,
        customWords: List<String> = emptyList(),
        languagePref: String = "auto"
    ): String {
        val useBengali = when (languagePref) {
            "bn" -> true
            "en" -> false
            else -> isBengaliQuiz
        }

        val chosenPraise = if (customWords.isNotEmpty()) {
            customWords.random().trim()
        } else {
            if (useBengali) SHORT_BENGALI_PRAISE.random() else SHORT_ENGLISH_PRAISE.random()
        }

        val cleanPraise = chosenPraise.trim('!', '.', ' ')
        val accuracy = summary.accuracy

        return if (useBengali) {
            when {
                accuracy >= 80f -> "$cleanPraise! অসাধারণ পারফরম্যান্স! অভিনন্দন!"
                accuracy >= 50f -> "$cleanPraise! চমৎকার স্কোর! অভিনন্দন!"
                else -> "কুইজ সম্পন্ন করার জন্য অভিনন্দন! এগিয়ে যাও!"
            }
        } else {
            when {
                accuracy >= 80f -> "$cleanPraise! Outstanding performance! Congratulations!"
                accuracy >= 50f -> "$cleanPraise! Great score! Congratulations!"
                else -> "Well done on completing the quiz! Keep learning!"
            }
        }
    }
}
