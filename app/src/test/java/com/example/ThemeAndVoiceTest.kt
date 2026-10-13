package com.example

import androidx.compose.ui.graphics.Color
import com.example.audio.AIVoiceCongratulationEngine
import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.ThemePreset
import com.example.ui.theme.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeAndVoiceTest {

    @Test
    fun testAllThemePresetsGenerateValidPalettes() {
        ThemePreset.entries.forEach { preset ->
            val palette = preset.palette
            assertNotNull(palette)
            assertNotNull(palette.primary)
            assertNotNull(palette.secondary)
            assertNotNull(palette.backgroundDark)
            assertTrue(palette.backgroundOverlayColors.isNotEmpty())
            assertTrue(palette.name.isNotBlank())
        }
    }

    @Test
    fun testCustomThemePaletteCreation() {
        val customColor = Color(0xFFFF5722)
        val secondaryColor = Color(0xFF00BCD4)
        val palette = ThemePalette.create(
            id = "custom",
            name = "My Custom Theme",
            bengaliName = "আমার কাস্টম থিম",
            primary = customColor,
            secondary = secondaryColor
        )

        assertEquals("custom", palette.id)
        assertEquals(customColor, palette.primary)
        assertEquals(secondaryColor, palette.secondary)
    }

    @Test
    fun testHexColorParser() {
        val parsed = parseHexColor("#3B82F6", Color.White)
        assertEquals(Color(0xFF3B82F6), parsed)

        val fallback = parseHexColor("invalid_hex", Color.Red)
        assertEquals(Color.Red, fallback)
    }

    @Test
    fun testAIVoiceUnlimitedBengaliCongratulation() {
        val customWords = listOf("সাবাশ", "মারহাবা")

        // Test streak appreciation in Bengali
        val phraseStreak5 = AIVoiceCongratulationEngine.generateAppreciationPhrase(
            isCorrect = true,
            streak = 5,
            isBengaliQuiz = true,
            customWords = customWords,
            languagePref = "bn"
        )

        assertNotNull(phraseStreak5)
        assertTrue(phraseStreak5.isNotBlank())
        // Should contain Bengali characters
        assertTrue(phraseStreak5.any { it in '\u0980'..'\u09FF' })

        // Test quiz completion congratulations
        val summary = QuizResultSummary(
            quizId = "q1",
            quizTitle = "বাংলাদেশ কুইজ",
            mode = QuizMode.PRACTICE,
            totalQuestions = 10,
            correctCount = 9,
            wrongCount = 1,
            unansweredCount = 0,
            score = 9,
            maxScore = 10,
            accuracy = 90.0f,
            timeTakenSeconds = 120,
            reviewItems = emptyList()
        )

        val completionPraise = AIVoiceCongratulationEngine.generateQuizCompletionCongratulations(
            summary = summary,
            isBengaliQuiz = true,
            customWords = customWords,
            languagePref = "bn"
        )

        assertNotNull(completionPraise)
        assertTrue(completionPraise.isNotBlank())
        assertTrue(completionPraise.any { it in '\u0980'..'\u09FF' })
    }

    @Test
    fun testAIVoiceUnlimitedEnglishCongratulation() {
        val customWords = listOf("Bravo", "Champion")

        val phrase = AIVoiceCongratulationEngine.generateAppreciationPhrase(
            isCorrect = true,
            streak = 4,
            isBengaliQuiz = false,
            customWords = customWords,
            languagePref = "en"
        )

        assertNotNull(phrase)
        assertTrue(phrase.isNotBlank())

        val summary = QuizResultSummary(
            quizId = "q2",
            quizTitle = "Science Quiz",
            mode = QuizMode.EXAM,
            totalQuestions = 10,
            correctCount = 10,
            wrongCount = 0,
            unansweredCount = 0,
            score = 10,
            maxScore = 10,
            accuracy = 100.0f,
            timeTakenSeconds = 85,
            reviewItems = emptyList()
        )

        val praise = AIVoiceCongratulationEngine.generateQuizCompletionCongratulations(
            summary = summary,
            isBengaliQuiz = false,
            customWords = customWords,
            languagePref = "en"
        )

        assertNotNull(praise)
        assertTrue(praise.contains("10"))
    }

    @Test
    fun testBackgroundPresetsAndSwatches() {
        assertTrue(com.example.ui.theme.SWATCH_COLORS.size >= 12)
        assertTrue(com.example.ui.theme.BACKGROUND_PRESETS.isNotEmpty())

        com.example.ui.theme.BACKGROUND_PRESETS.forEach { preset ->
            assertTrue(preset.hex.startsWith("#"))
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.bengaliName.isNotBlank())
            val color = parseHexColor(preset.hex, Color.Black)
            assertNotNull(color)
        }
    }

    @Test
    fun testGlassmorphicModifierPresets() {
        val intensities = com.example.ui.components.GlassIntensity.entries
        assertEquals(4, intensities.size)
        assertTrue(intensities.contains(com.example.ui.components.GlassIntensity.SUBTLE))
        assertTrue(intensities.contains(com.example.ui.components.GlassIntensity.REGULAR))
        assertTrue(intensities.contains(com.example.ui.components.GlassIntensity.ELEVATED))
        assertTrue(intensities.contains(com.example.ui.components.GlassIntensity.ACCENT))
    }
}
