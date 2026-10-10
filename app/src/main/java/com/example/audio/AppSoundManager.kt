package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class AppSoundManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var toneGenerator: ToneGenerator? = null
    private var lastSpeechTimeMs: Long = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        initToneGenerator()
        initTts()
    }

    private fun initToneGenerator() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setSpeechRate(1.05f)
                        tts?.setPitch(1.05f)
                        isTtsInitialized = true
                    }
                }
            }
        } catch (e: Exception) {
            isTtsInitialized = false
        }
    }

    fun playCorrectSound(soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            // Ignore if muted or unavailable
        }
    }

    fun playWrongSound(soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 160)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun playClickSound(soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun vibrate(vibrationEnabled: Boolean, isSuccess: Boolean = true) {
        if (!vibrationEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isSuccess) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 70), -1)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (isSuccess) 40L else 100L)
            }
        } catch (e: Exception) {
            // Graceful fallback
        }
    }

    /**
     * AI Voice appreciation system for Practice Mode.
     * Respects cooldown, avoids repeat, works offline via Android TextToSpeech.
     * Leverages AIVoiceCongratulationEngine for unlimited words.
     */
    fun speakAppreciation(
        isCorrect: Boolean,
        streak: Int,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        customWordsString: String = "",
        languagePref: String = "auto"
    ) {
        if (!voiceEnabled || !isTtsInitialized) return

        val now = System.currentTimeMillis()
        // Minimum 2.2 second cooldown between voice cheers
        if (now - lastSpeechTimeMs < 2200L) return

        val customWords = customWordsString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val phrase = AIVoiceCongratulationEngine.generateAppreciationPhrase(
            isCorrect = isCorrect,
            streak = streak,
            isBengaliQuiz = isBengaliQuiz,
            customWords = customWords,
            languagePref = languagePref
        )

        if (!phrase.isNullOrBlank()) {
            lastSpeechTimeMs = now
            val useBengali = when (languagePref) {
                "bn" -> true
                "en" -> false
                else -> isBengaliQuiz || phrase.any { it in '\u0980'..'\u09FF' }
            }
            speakInternal(phrase, useBengali, "appreciation_$now")
        }
    }

    /**
     * Speaks dynamic unlimited congratulations upon Quiz Completion / Result screen.
     */
    fun speakResultCongratulations(
        summary: com.example.data.model.QuizResultSummary,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        customWordsString: String = "",
        languagePref: String = "auto"
    ) {
        if (!voiceEnabled || !isTtsInitialized) return

        val customWords = customWordsString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val phrase = AIVoiceCongratulationEngine.generateQuizCompletionCongratulations(
            summary = summary,
            isBengaliQuiz = isBengaliQuiz,
            customWords = customWords,
            languagePref = languagePref
        )

        val useBengali = when (languagePref) {
            "bn" -> true
            "en" -> false
            else -> isBengaliQuiz || phrase.any { it in '\u0980'..'\u09FF' }
        }

        speakInternal(phrase, useBengali, "result_${System.currentTimeMillis()}")
    }

    /**
     * Directly speaks any test or custom phrase (useful for Settings preview).
     */
    fun speakCustomText(text: String, isBengali: Boolean = false) {
        if (!isTtsInitialized || text.isBlank()) return
        val containsBengali = isBengali || text.any { it in '\u0980'..'\u09FF' }
        speakInternal(text, containsBengali, "test_${System.currentTimeMillis()}")
    }

    private fun speakInternal(text: String, isBengali: Boolean, utteranceId: String) {
        try {
            if (isBengali) {
                val bnLocale = Locale("bn", "BD")
                val res = tts?.setLanguage(bnLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            } else {
                tts?.setLanguage(Locale.US)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            // Graceful fallback
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
        try {
            toneGenerator?.release()
        } catch (e: Exception) {}
    }
}
