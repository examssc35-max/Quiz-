package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.example.ui.components.CelebrationType
import java.util.Locale
import java.util.Random

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

    // Realistic Sound Synthesizers for Animations (User Request 2: "অ্যানিমেশনকে আরো রিয়েল স্টিক করে এবং সাউন্ড এড কর")
    private val fireworksPcm: ShortArray by lazy { generateFireworksPcm() }
    private val confettiPcm: ShortArray by lazy { generateConfettiPcm() }
    private val starPcm: ShortArray by lazy { generateShootingStarPcm() }
    private val fanfarePcm: ShortArray by lazy { generateVictoryFanfarePcm() }
    private val balloonsPcm: ShortArray by lazy { generateBalloonsPcm() }
    private val consolationPcm: ShortArray by lazy { generateConsolationPcm() }

    @Volatile
    private var activeCelebrationTrack: AudioTrack? = null
    private val audioLock = Any()

    fun playCelebrationSound(type: CelebrationType, soundEnabled: Boolean) {
        if (!soundEnabled) return
        when (type) {
            CelebrationType.FIREWORKS -> playPcm(fireworksPcm)
            CelebrationType.CONFETTI_RAIN -> playPcm(confettiPcm)
            CelebrationType.SHOOTING_STARS -> playPcm(starPcm)
            CelebrationType.GOLDEN_RINGS -> playPcm(fanfarePcm)
            CelebrationType.BALLOONS_FLOAT -> playPcm(balloonsPcm)
            CelebrationType.CONSOLATION_GLOW -> playPcm(consolationPcm)
        }
    }

    private fun playPcm(samples: ShortArray) {
        Thread {
            try {
                synchronized(audioLock) {
                    try {
                        activeCelebrationTrack?.stop()
                        activeCelebrationTrack?.release()
                    } catch (_: Exception) {}
                    activeCelebrationTrack = null
                }

                val sampleRate = 44100
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufSize = maxOf(minBuf, samples.size * 2)
                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufSize,
                        AudioTrack.MODE_STATIC
                    )
                }

                synchronized(audioLock) {
                    activeCelebrationTrack = track
                }

                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L / sampleRate) + 150L
                Thread.sleep(durationMs)

                synchronized(audioLock) {
                    if (activeCelebrationTrack === track) {
                        try {
                            track.stop()
                            track.release()
                        } catch (_: Exception) {}
                        activeCelebrationTrack = null
                    }
                }
            } catch (e: Exception) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun generateFireworksPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 1.5).toInt()
        val out = ShortArray(totalSamples)
        val rnd = Random(42)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            // 1. Rocket Launch Ascent Whistle & Fizz (0.00s to 0.24s)
            if (t < 0.24) {
                val progress = t / 0.24
                val freq = 320.0 + 1150.0 * progress
                val env = Math.sin(progress * Math.PI)
                val whistle = Math.sin(2.0 * Math.PI * freq * t)
                val hiss = (rnd.nextDouble() * 2.0 - 1.0) * 0.35
                sample = (whistle * 0.70 + hiss * 0.30) * env * 0.65
            } else {
                // 2. Primary Massive Detonation Blast Boom at 0.24s
                val explosionT = t - 0.24
                val boomEnv = Math.exp(-explosionT * 7.0)
                val boomFreq = 78.0 * Math.exp(-explosionT * 3.2) // Deep sub-bass punch
                val boom = Math.sin(2.0 * Math.PI * boomFreq * explosionT) * boomEnv * 0.95
                val blastNoise = (rnd.nextDouble() * 2.0 - 1.0) * Math.exp(-explosionT * 5.8) * 0.75

                // 3. Secondary Cascading Explosion Burst at 0.58s
                var boom2 = 0.0
                if (t >= 0.58) {
                    val exp2T = t - 0.58
                    val boom2Env = Math.exp(-exp2T * 8.0) * 0.65
                    val boom2Freq = 95.0 * Math.exp(-exp2T * 3.5)
                    boom2 = (Math.sin(2.0 * Math.PI * boom2Freq * exp2T) + (rnd.nextDouble() * 2.0 - 1.0) * 0.5) * boom2Env
                }

                // 4. Realistic Dragon-Egg Sparkle Crackles & Pops (from 0.38s to 1.35s)
                var crackles = 0.0
                if (explosionT in 0.14..1.15) {
                    val crackleDensity = if (explosionT in 0.25..0.75) 0.065 else 0.035
                    if (rnd.nextDouble() < crackleDensity) {
                        val sparkAmp = (rnd.nextDouble() * 2.0 - 1.0) * (1.1 - explosionT * 0.8)
                        val highFreq = 1800.0 + rnd.nextDouble() * 3200.0
                        val sparkSnap = Math.sin(2.0 * Math.PI * highFreq * explosionT) * 0.4
                        crackles = (sparkAmp + sparkSnap) * 0.85
                    }
                }

                sample = (boom * 0.65 + blastNoise * 0.45 + boom2 * 0.4 + crackles * 0.65) * 0.88
            }
            out[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun generateConfettiPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 1.05).toInt()
        val out = ShortArray(totalSamples)
        val rnd = Random(123)
        // Joyful bright pentatonic fanfare bells: C5, G5, C6, E6, G6
        val notes = doubleArrayOf(523.25, 783.99, 1046.50, 1318.51, 1567.98)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            if (t < 0.12) {
                // Energetic Party Popper Cannon Thump & Air Pop
                val popEnv = Math.exp(-t * 55.0)
                val popNoise = (rnd.nextDouble() * 2.0 - 1.0) * 0.95
                val popThump = Math.sin(2.0 * Math.PI * 120.0 * t) * 0.85
                sample = (popNoise * 0.55 + popThump * 0.45) * popEnv * 1.1
            } else {
                // Sparkling Glissando Bells
                val chimeT = t - 0.12
                for (nIdx in notes.indices) {
                    val noteStart = nIdx * 0.075
                    if (chimeT >= noteStart) {
                        val nt = chimeT - noteStart
                        val noteEnv = Math.exp(-nt * 6.5)
                        val fundamental = Math.sin(2.0 * Math.PI * notes[nIdx] * nt)
                        val shimmer = Math.sin(2.0 * Math.PI * (notes[nIdx] * 2.0) * nt) * 0.3
                        sample += (fundamental + shimmer) * noteEnv * 0.28
                    }
                }
            }
            out[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun generateShootingStarPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 0.95).toInt()
        val out = ShortArray(totalSamples)
        val rnd = Random(777)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val env = if (t < 0.2) t / 0.2 else Math.exp(-(t - 0.2) * 3.8)
            val freq = 2600.0 - 1400.0 * (t / 0.95)
            val tremolo = 1.0 + 0.4 * Math.sin(2.0 * Math.PI * 32.0 * t)
            val sine = Math.sin(2.0 * Math.PI * freq * t)
            val shimmer = Math.sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.35
            val sparkle = (rnd.nextDouble() * 2.0 - 1.0) * 0.15
            val sample = (sine * 0.55 + shimmer * 0.3 + sparkle) * env * tremolo * 0.72
            out[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun generateVictoryFanfarePcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 1.1).toInt()
        val out = ShortArray(totalSamples)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51) // C5, E5, G5, C6, E6
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (nIdx in notes.indices) {
                val noteStart = nIdx * 0.09
                if (t >= noteStart) {
                    val nt = t - noteStart
                    val env = Math.exp(-nt * 3.8)
                    val f = notes[nIdx]
                    val tone = Math.sin(2.0 * Math.PI * f * nt) * 0.65 +
                            Math.sin(2.0 * Math.PI * (f * 2) * nt) * 0.25 +
                            Math.sin(2.0 * Math.PI * (f * 3) * nt) * 0.10
                    sample += tone * env * 0.28
                }
            }
            out[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun generateBalloonsPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 1.15).toInt()
        val out = ShortArray(totalSamples)
        // Playful, cheerful ascending bubbly chimes: F5, A5, C6, D6, F6, A6, C7
        val notes = doubleArrayOf(698.46, 880.00, 1046.50, 1174.66, 1396.91, 1760.00, 2093.00)
        val rnd = Random(555)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (nIdx in notes.indices) {
                val noteStart = nIdx * 0.08
                if (t >= noteStart) {
                    val nt = t - noteStart
                    val env = Math.exp(-nt * 5.2)
                    // Bubbly chirp pitch envelope
                    val bubbleFreq = notes[nIdx] * (1.0 + 0.12 * Math.exp(-nt * 45.0))
                    val tone = Math.sin(2.0 * Math.PI * bubbleFreq * nt) * 0.70 +
                            Math.sin(2.0 * Math.PI * (bubbleFreq * 2.0) * nt) * 0.25
                    val bubbleNoise = (rnd.nextDouble() * 2.0 - 1.0) * Math.exp(-nt * 60.0) * 0.08
                    sample += (tone + bubbleNoise) * env * 0.24
                }
            }
            out[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun generateConsolationPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * 0.95).toInt()
        val out = ShortArray(totalSamples)
        // Warm comforting major-7th chord (F4, A4, C5, E5) with soothing vibrato
        val chords = doubleArrayOf(349.23, 440.0, 523.25, 659.25)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val env = if (t < 0.12) t / 0.12 else Math.exp(-(t - 0.12) * 3.2)
            var sample = 0.0
            val vibrato = 1.0 + 0.015 * Math.sin(2.0 * Math.PI * 4.5 * t)
            for (f in chords) {
                sample += Math.sin(2.0 * Math.PI * (f * vibrato) * t) * (0.8 / chords.size)
            }
            out[i] = ((sample * env).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    /**
     * AI Voice appreciation system for Practice Mode.
     * Respects cooldown, avoids repeat, works offline via Android TextToSpeech.
     * Leverages AIVoiceCongratulationEngine for unlimited words.
     */
    /**
     * Speaks short, punchy congratulation when a streak milestone (e.g. 3 correct) is reached.
     * Returns Pair(spokenText, displayTitle).
     */
    fun speakStreakCongratulation(
        streak: Int,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        customWordsString: String = "",
        languagePref: String = "auto"
    ): Pair<String, String> {
        val customWords = AIVoiceCongratulationEngine.parseCustomWords(customWordsString)
        val (spoken, displayTitle) = AIVoiceCongratulationEngine.generateShortStreakCongratulation(
            streak = streak,
            isBengaliQuiz = isBengaliQuiz,
            customWords = customWords,
            languagePref = languagePref
        )

        if (voiceEnabled && isTtsInitialized) {
            val now = System.currentTimeMillis()
            lastSpeechTimeMs = now
            val useBengali = when (languagePref) {
                "bn" -> true
                "en" -> false
                else -> isBengaliQuiz || spoken.any { it in '\u0980'..'\u09FF' }
            }
            speakInternal(spoken, useBengali, "streak_$now")
        }
        return Pair(spoken, displayTitle)
    }

    /**
     * Speaks short consolation when 2-3 wrong answers occur.
     * Returns Pair(spokenText, displayTitle).
     */
    fun speakConsolation(
        wrongCount: Int,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        languagePref: String = "auto"
    ): Pair<String, String> {
        val (spoken, displayTitle) = AIVoiceCongratulationEngine.generateShortConsolation(
            wrongCount = wrongCount,
            isBengaliQuiz = isBengaliQuiz,
            languagePref = languagePref
        )

        if (voiceEnabled && isTtsInitialized) {
            val now = System.currentTimeMillis()
            lastSpeechTimeMs = now
            val useBengali = when (languagePref) {
                "bn" -> true
                "en" -> false
                else -> isBengaliQuiz || spoken.any { it in '\u0980'..'\u09FF' }
            }
            speakInternal(spoken, useBengali, "consolation_$now")
        }
        return Pair(spoken, displayTitle)
    }

    /**
     * Legacy appreciation method for backward compatibility.
     */
    fun speakAppreciation(
        isCorrect: Boolean,
        streak: Int,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        customWordsString: String = "",
        languagePref: String = "auto"
    ) {
        if (isCorrect) {
            if (streak >= 3) {
                speakStreakCongratulation(streak, voiceEnabled, isBengaliQuiz, customWordsString, languagePref)
            }
        } else {
            speakConsolation(2, voiceEnabled, isBengaliQuiz, languagePref)
        }
    }

    /**
     * Speaks dynamic short congratulations upon Quiz Completion / Result screen.
     */
    fun speakResultCongratulations(
        summary: com.example.data.model.QuizResultSummary,
        voiceEnabled: Boolean,
        isBengaliQuiz: Boolean = false,
        customWordsString: String = "",
        languagePref: String = "auto"
    ) {
        if (!voiceEnabled || !isTtsInitialized) return

        val customWords = AIVoiceCongratulationEngine.parseCustomWords(customWordsString)

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
                val bnLocale = Locale.forLanguageTag("bn-BD")
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
            synchronized(audioLock) {
                activeCelebrationTrack?.stop()
                activeCelebrationTrack?.release()
                activeCelebrationTrack = null
            }
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
        try {
            toneGenerator?.release()
        } catch (e: Exception) {}
    }
}
