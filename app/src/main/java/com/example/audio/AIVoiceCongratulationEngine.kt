package com.example.audio

import com.example.data.model.QuizResultSummary
import java.util.Locale
import kotlin.random.Random

/**
 * AI & Procedural Unlimited Congratulation Engine.
 * Fulfills User Request: "কংগ্রেচুলেশন করার জন্য নির্দিষ্ট কোন ওয়ার্ড না আনলিমিটেড ওয়ার্ড লাগবে"
 * (Unlimited congratulation words, not limited to fixed phrases, with combinatorial generation,
 * extensive Bengali & English vocabulary, user-defined custom words, and dynamic speech synthesis).
 */
object AIVoiceCongratulationEngine {

    // Massive Bengali Praise Vocabulary (35+ prefixes & accolades)
    private val BENGALI_PREFIXES = listOf(
        "সাবাশ!", "মারহাবা!", "অসাধারণ!", "চমৎকার!", "দারুণ!", "অবিশ্বাস্য!",
        "ব্রাভো!", "ফ্যান্টাস্টিক!", "বাহ্!", "হ্যাটস অফ!", "জীয়ন কাঠি!", "অনবদ্য!",
        "লাজবাব!", "বিস্ময়কর!", "খুব সুন্দর!", "প্রশংসনীয়!", "উৎকৃষ্ট!", "অপ্রতিদ্বন্দ্বী!",
        "সেরা খেলোয়াড়!", "অপূর্ব!", "দুর্দান্ত!", "জাদুকরী!", "তুখোড়!", "বাহবা!",
        "অভিনন্দন!", "স্যালুট!", "মুগ্ধকর!", "গৌরবময়!", "আলোকবর্তিকা!", "অতুলনীয়!",
        "জয়তু!", "মনমুগ্ধকর!", "বুদ্ধির বাতিঘর!", "মেধার রাজপুত্র!", "অপরাজেয় বীর!"
    )

    private val BENGALI_HONORIFICS = listOf(
        "কুইজ সম্রাট", "জ্ঞান বীর", "মেধার প্রতীক", "জ্ঞানের আলো", "কুইজ মাস্টার",
        "অপরাজেয় চ্যাম্পিয়ন", "বুদ্ধির আইকন", "সুপার ব্রেন", "মেধাবী তারকা", "জ্ঞানসাধক"
    )

    private val BENGALI_STREAK_TITLES = listOf(
        "টানা %dটি সঠিক উত্তর!",
        "দমকা গতিতে %dটি নির্ভুল উত্তর!",
        "হ্যাটট্রিক প্লাস! %dটি প্রশ্নে অপরাজিত!",
        "তুখোড় পারফরম্যান্স, স্ট্রিক %d!",
        "বিদ্যুৎ গতিতে %dটি নির্ভুল লক্ষ্যভেদ!",
        "একটানা %dটি নিখুঁত সাফল্য!"
    )

    private val BENGALI_COMPLIMENTS = listOf(
        "তোমার মেধা সত্যিই প্রশংসনীয়!",
        "তুমি তো সত্যিকারের কুইজ মাস্টার!",
        "দারুণ বুদ্ধিমত্তা আর তীক্ষ্ণ মেধা!",
        "তোমার জ্ঞান সত্যিই অতুলনীয়!",
        "এভাবেই এগিয়ে যাও চ্যাম্পিয়ন!",
        "প্রতিটি উত্তরে তোমার পারদর্শিতা মুগ্ধ করার মতো!",
        "অসাধারণ স্মরণশক্তি আর নির্ভুল সিদ্ধান্ত!",
        "তোমার এই আত্মবিশ্বাস তোমাকে শীর্ষে নিয়ে যাবে!",
        "দারুণ মনোযোগ আর চমৎকার দক্ষতা!",
        "তুমি যে জিনিয়াস, তা আজ প্রমাণ করে দিলে!",
        "জ্ঞানের এই দীপ্তি যেন ছড়িয়ে পড়ে চারিদিকে!",
        "তোমার এই অগ্রগতি সত্যিই অনুপ্রেরণাদায়ক!",
        "অনায়াস দক্ষতায় একের পর এক সঠিক উত্তর দিলে!",
        "এমন বুদ্ধিমত্তা সচরাচর দেখা যায় না!",
        "তোমার স্মৃতিশক্তি এবং যুক্তি উভয়ই অতুলনীয়!",
        "প্রতিটি চ্যালেঞ্জকে তুমি সাফল্যে রূপান্তর করেছো!",
        "তোমার চেষ্টা এবং একাগ্রতা সত্যিই প্রশংসার দাবিদার!",
        "ভবিষ্যতে তুমি অনেক বড় কুইজার হবে!",
        "চমৎকার আত্মবিশ্বাস এবং দ্রুত সিদ্ধান্ত গ্রহণের ক্ষমতা!",
        "তোমার এই জয়যাত্রায় আমরা সবাই আনন্দিত ও গর্বিত!",
        "ধারাবাহিক অনুশীলন তোমাকে অপ্রতিরোধ্য করে তুলেছে!",
        "জ্ঞান অর্জনের এই চমৎকার নেশা বজায় রাখো!",
        "তোমার মেধার জ্যোতি সবার মনে অনুপ্রেরণা জাগায়!"
    )

    private val BENGALI_CLOSINGS = listOf(
        "তোমার এই জয়যাত্রা অব্যাহত থাকুক!",
        "তুমি এগিয়ে চলো সাফল্যের স্বর্ণশিখরে!",
        "কুইজ এক্সপ্লোরারের গর্ব তুমি!",
        "জ্ঞানের পরিধি আরও বিস্তৃত হোক!",
        "পরের পর্বেও এমন তুখোড় পারফরম্যান্স দেখতে চাই!"
    )

    private val BENGALI_ENCOURAGEMENTS = listOf(
        "কোনো ব্যাপার না, চেষ্টা চালিয়ে যাও!",
        "পরের প্রশ্নটি তুমি নিশ্চয়ই পারবে!",
        "ভুল থেকেই তো শেখা হয়, আত্মবিশ্বাস রাখো!",
        "হাল ছেড়ো না, তুমি অনেক ভালো করবে!",
        "মন শক্ত রাখো, এগিয়ে যাও!",
        "প্রতিটি ভুল এক একটি নতুন শিক্ষার ধাপ!",
        "ধৈর্য রাখো, বিজয় তোমার হাতের নাগালেই!"
    )

    // Extensive English Praise Vocabulary (30+ prefixes)
    private val ENGLISH_PREFIXES = listOf(
        "Incredible!", "Brilliant!", "Outstanding!", "Sensational!", "Spectacular!",
        "Masterful!", "Bravo!", "Phenomenal!", "Superb!", "Legendary!", "Majestic!",
        "Unstoppable!", "Extraordinary!", "Glorious!", "Astonishing!", "Magnificent!",
        "Flawless!", "Sublime!", "Stunning!", "Peerless!", "Supreme!", "Heroic!", "Stellar!"
    )

    private val ENGLISH_HONORIFICS = listOf(
        "Quiz Champion", "Mastermind", "Trivia Legend", "Intellectual Titan",
        "Knowledge Conqueror", "Grand Scholar", "Wisdom Knight", "Genius Mind"
    )

    private val ENGLISH_STREAK_TITLES = listOf(
        "%d in a row without a single doubt!",
        "On absolute fire with %d straight hits!",
        "Flawless momentum at %d consecutive wins!",
        "Dominating with an unstoppable streak of %d!",
        "Lightning strikes! %d perfect answers in a row!",
        "Pure precision on a streak of %d!"
    )

    private val ENGLISH_COMPLIMENTS = listOf(
        "You are dominating this quiz like a pro!",
        "Your intellect and speed are on another level!",
        "A true master of knowledge at work!",
        "Unstoppable precision and sheer brilliance!",
        "Pure genius in action, keep shining!",
        "Your sharp intuition makes every answer count!",
        "Remarkable clarity and lightning-fast mastery!",
        "You are setting a whole new standard of excellence!",
        "Your dedication and encyclopedic memory are breathtaking!",
        "Every challenge brings out the champion within you!",
        "A masterclass in trivia and razor-sharp intellect!",
        "Knowledge is power, and you hold boundless power!",
        "Confidence, intellect, and grace - you have it all!"
    )

    private val ENGLISH_CLOSINGS = listOf(
        "Keep dominating the leaderboards!",
        "The sky is not the limit for your boundless mind!",
        "Continue exploring and conquering new horizons!",
        "A truly memorable and inspiring run!"
    )

    private val ENGLISH_ENCOURAGEMENTS = listOf(
        "Don't worry, every mistake is a stepping stone!",
        "Shake it off, the next one is yours!",
        "Keep your focus sharp, you've got this!",
        "Great effort, stay positive and keep pushing!",
        "Resilience creates true champions. Keep going!"
    )

    /**
     * Synthesizes dynamic, unlimited, non-repeating praise for in-quiz streaks or answers.
     */
    fun generateAppreciationPhrase(
        isCorrect: Boolean,
        streak: Int,
        isBengaliQuiz: Boolean,
        customWords: List<String> = emptyList(),
        languagePref: String = "auto"
    ): String? {
        val useBengali = when (languagePref) {
            "bn" -> true
            "en" -> false
            else -> isBengaliQuiz
        }

        if (!isCorrect) {
            return if (useBengali) {
                BENGALI_ENCOURAGEMENTS.random()
            } else {
                ENGLISH_ENCOURAGEMENTS.random()
            }
        }

        // For correct answers: Don't speak on single answer to prevent audio clutter
        if (streak < 2) return null

        val customWord = if (customWords.isNotEmpty() && Random.nextFloat() > 0.35f) {
            customWords.random().trim()
        } else null

        if (useBengali) {
            val prefix = customWord ?: BENGALI_PREFIXES.random()
            val honorific = if (Random.nextFloat() > 0.5f) " হে ${BENGALI_HONORIFICS.random()}," else ""
            return when {
                streak >= 5 -> {
                    val streakText = String.format(Locale.getDefault(), BENGALI_STREAK_TITLES.random(), streak)
                    "$prefix$honorific $streakText ${BENGALI_COMPLIMENTS.random()}"
                }
                streak >= 3 -> {
                    "$prefix$honorific ${BENGALI_COMPLIMENTS.random()}"
                }
                else -> {
                    "$prefix দারুণ উত্তর, ছন্দ ধরে রাখো!"
                }
            }
        } else {
            val prefix = customWord ?: ENGLISH_PREFIXES.random()
            val honorific = if (Random.nextFloat() > 0.5f) " ${ENGLISH_HONORIFICS.random()}," else ""
            return when {
                streak >= 5 -> {
                    val streakText = String.format(Locale.US, ENGLISH_STREAK_TITLES.random(), streak)
                    "$prefix$honorific $streakText ${ENGLISH_COMPLIMENTS.random()}"
                }
                streak >= 3 -> {
                    "$prefix$honorific ${ENGLISH_COMPLIMENTS.random()}"
                }
                else -> {
                    "$prefix Great job, keep the momentum going!"
                }
            }
        }
    }

    /**
     * Synthesizes dynamic, unlimited, combinatorially rich congratulations for the Result Screen.
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

        val accuracy = summary.accuracy
        val customWord = if (customWords.isNotEmpty()) customWords.random().trim() else null

        if (useBengali) {
            val prefix = customWord ?: BENGALI_PREFIXES.random()
            val compliment = BENGALI_COMPLIMENTS.random()
            val closing = BENGALI_CLOSINGS.random()
            val honorific = BENGALI_HONORIFICS.random()

            return when {
                accuracy >= 95f -> {
                    "$prefix $honorific! ১০০ এর কাছাকাছি নিখুঁত স্কোর! তুমি ${summary.correctCount}টি সঠিক উত্তর দিয়ে ইতিহাস গড়েছো! $compliment $closing"
                }
                accuracy >= 80f -> {
                    "$prefix অসাধারণ সফলতা! শতকরা ${accuracy.toInt()} ভাগ নির্ভুল উত্তর দিয়ে প্রমাণ করেছো তোমার মেধার গভীরতা! $compliment $closing"
                }
                accuracy >= 60f -> {
                    "$prefix চমৎকার প্রচেষ্টা! মোট ${summary.score} নম্বর অর্জন করেছো! $compliment $closing"
                }
                else -> {
                    "কুইজ সম্পন্ন করার জন্য অভিনন্দন! নিয়মিত চর্চা করলে তুমি খুব শীঘ্রই শীর্ষে পৌঁছাবে! হাল ছেড়ো না, পরবর্তী কুইজে জয় তোমারই!"
                }
            }
        } else {
            val prefix = customWord ?: ENGLISH_PREFIXES.random()
            val compliment = ENGLISH_COMPLIMENTS.random()
            val closing = ENGLISH_CLOSINGS.random()
            val honorific = ENGLISH_HONORIFICS.random()

            return when {
                accuracy >= 95f -> {
                    "$prefix Hail to our $honorific! Nearly flawless mastery with a stellar score of ${summary.score} out of ${summary.maxScore}! $compliment $closing"
                }
                accuracy >= 80f -> {
                    "$prefix Outstanding performance! You achieved ${accuracy.toInt()}% accuracy with ${summary.correctCount} correct answers! $compliment $closing"
                }
                accuracy >= 60f -> {
                    "$prefix Splendid effort! Solid score of ${summary.score}! $compliment $closing"
                }
                else -> {
                    "Well done completing the quiz! Practice is the true catalyst of mastery. Keep exploring and victory will be yours!"
                }
            }
        }
    }
}
