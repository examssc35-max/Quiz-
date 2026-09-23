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
     */
    fun speakAppreciation(
        isCorrect: Boolean,
        streak: Int,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false
    ) {
        if (!voiceEnabled || !isTtsInitialized) return

        val now = System.currentTimeMillis()
        // Minimum 2.5 second cooldown between voice cheers
        if (now - lastSpeechTimeMs < 2500L) return

        val phrase = if (isCorrect) {
            when {
                streak >= 5 -> listOf("Incredible streak!", "You are unstoppable!", "Spectacular!", "Outstanding!").random()
                streak >= 3 -> listOf("Brilliant!", "Excellent work!", "Keep it up!", "Amazing!").random()
                streak == 2 -> listOf("Good job!", "Great!", "Well done!").random()
                else -> null // Don't speak on first correct answer to avoid noise
            }
        } else {
            // Encouraging & supportive for wrong answers, never shaming
            listOf("Keep trying!", "You can do it!", "Don't give up!").random()
        }

        if (phrase != null) {
            lastSpeechTimeMs = now
            try {
                tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "appreciation_$now")
            } catch (e: Exception) {
                // Ignore
            }
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
