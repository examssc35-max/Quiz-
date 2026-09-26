package com.example

import com.example.ai.AIConfig
import com.example.ai.AIManager
import com.example.ai.AIPromptBuilder
import com.example.ai.AIProviderType
import com.example.ai.AnswerEvaluationRequest
import com.example.ai.SmartNormalizer
import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizMode
import com.example.data.model.QuizSchema
import com.example.engine.AiAnswerEvaluator
import com.example.engine.AnswerState
import com.example.engine.QuizEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AISystemTest {

    // =========================================================================
    // 1. SMART LOCAL NORMALIZATION TESTS
    // =========================================================================

    @Test
    fun testSmartNormalizer_numericalEquivalence_bengaliAndEnglishPercentages() {
        val stored = listOf("৬০–৭০%")

        // Example 1 from specification: 60-70% vs ৬০–৭০%
        assertTrue("60-70% must match ৬০–৭০%", SmartNormalizer.isEquivalentlyNormalized("60-70%", stored))
        assertTrue("60 - 70 % with spaces must match ৬০–৭০%", SmartNormalizer.isEquivalentlyNormalized("60 - 70 %", stored))
        assertTrue("৬০ - ৭০% with spaces must match ৬০–৭০%", SmartNormalizer.isEquivalentlyNormalized("৬০ - ৭০%", stored))
        assertTrue("60 to 70% must match ৬০–৭০%", SmartNormalizer.isEquivalentlyNormalized("60 to 70%", stored))
        assertTrue("৬০ থেকে ৭০% must match ৬০–৭০%", SmartNormalizer.isEquivalentlyNormalized("৬০ থেকে ৭০%", stored))

        // Different numbers must NOT match
        assertFalse("50-60% must not match 60-70%", SmartNormalizer.isEquivalentlyNormalized("50-60%", stored))
    }

    @Test
    fun testSmartNormalizer_unitEquivalence_kgAndBengali() {
        val stored = listOf("১,০০০ কেজি")

        // Example 2 from specification: 1000 kg vs ১,০০০ কেজি
        assertTrue("1000 kg must match ১,০০০ কেজি", SmartNormalizer.isEquivalentlyNormalized("1000 kg", stored))
        assertTrue("1000kg must match ১,০০০ কেজি", SmartNormalizer.isEquivalentlyNormalized("1000kg", stored))
        assertTrue("১০০০ কেজি must match ১,০০০ কেজি", SmartNormalizer.isEquivalentlyNormalized("১০০০ কেজি", stored))
        assertTrue("1000 kilogram must match ১,০০০ কেজি", SmartNormalizer.isEquivalentlyNormalized("1000 kilogram", stored))
        assertTrue("1,000 kg must match ১,০০০ কেজি", SmartNormalizer.isEquivalentlyNormalized("1,000 kg", stored))

        // Wrong amount or wrong unit must NOT match
        assertFalse("500 kg must not match 1000 kg", SmartNormalizer.isEquivalentlyNormalized("500 kg", stored))
        assertFalse("1000 g must not match 1000 kg", SmartNormalizer.isEquivalentlyNormalized("1000 g", stored))
    }

    @Test
    fun testSmartNormalizer_commonUnits_km_m_cm_g() {
        // km
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("5 km", listOf("৫ কিমি")))
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("5 kilometer", listOf("৫ কিলোমিটার")))

        // meters
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("100 m", listOf("১০০ মিটার")))

        // cm
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("25 cm", listOf("২৫ সেন্টিমিটার")))

        // grams
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("500 g", listOf("৫০০ গ্রাম")))
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("500 gm", listOf("500 grams")))
    }

    @Test
    fun testSmartNormalizer_punctuationAndWhitespace() {
        val accepted = listOf("Dhaka")

        assertTrue(SmartNormalizer.isEquivalentlyNormalized("  dhaka  ", accepted))
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("\"dhaka\"", accepted))
        assertTrue(SmartNormalizer.isEquivalentlyNormalized("(Dhaka)", accepted))
    }

    @Test
    fun testSmartNormalizer_doesNotOverAcceptDifferentWords() {
        // Words with different meanings or grammatical forms must NOT be auto-matched by local normalizer
        // They must proceed to the AI evaluator!
        assertFalse(SmartNormalizer.isEquivalentlyNormalized("elements", listOf("element")))
        assertFalse(SmartNormalizer.isEquivalentlyNormalized("damage", listOf("harm")))
        assertFalse(SmartNormalizer.isEquivalentlyNormalized("disposal", listOf("management")))
    }

    // =========================================================================
    // 2. AI PROMPT BUILDER TESTS
    // =========================================================================

    @Test
    fun testAIPromptBuilder_buildsStrictPromptWithContextAndRules() {
        val request = AnswerEvaluationRequest(
            questionText = "Air is the most important (a) — of human environment.",
            acceptedAnswers = listOf("element"),
            userAnswer = "elements"
        )

        val prompt = AIPromptBuilder.buildEvaluationPrompt(request)
        assertTrue(prompt.contains("Air is the most important (a) — of human environment."))
        assertTrue(prompt.contains("elements"))
        assertTrue(prompt.contains("element"))
        assertTrue(prompt.contains("Singular / plural agreement"))
        assertTrue(prompt.contains("banglaExplanation"))
    }

    @Test
    fun testAIPromptBuilder_parsesJsonResponseCorrectly() {
        val json = """
            ```json
            {
              "isCorrect": true,
              "confidence": 0.95,
              "matchedAnswer": "damage",
              "banglaExplanation": "তোমার উত্তরটি সঠিক। 'damage' শব্দটি এই বাক্যে উপযুক্তভাবে বসে।",
              "reason": "Synonym with valid syntax"
            }
            ```
        """.trimIndent()

        val result = AIPromptBuilder.parseEvaluationResponse(json, "harm")
        assertTrue(result.isCorrect)
        assertEquals(0.95, result.confidence, 0.01)
        assertEquals("damage", result.matchedAnswer)
        assertEquals("তোমার উত্তরটি সঠিক। 'damage' শব্দটি এই বাক্যে উপযুক্তভাবে বসে।", result.banglaExplanation)
    }

    // =========================================================================
    // 3. AI MANAGER ARCHITECTURE & PROVIDER TESTS
    // =========================================================================

    @Test
    fun testAIManager_providerTypesAndDefaults() {
        assertEquals("gemini-2.5-flash", AIProviderType.GEMINI.defaultModel)
        assertEquals("gpt-4o-mini", AIProviderType.OPENAI.defaultModel)
        assertEquals("claude-3-5-haiku-20241022", AIProviderType.ANTHROPIC.defaultModel)
        assertEquals("google/gemini-2.5-flash", AIProviderType.OPENROUTER.defaultModel)
        assertTrue(AIProviderType.OPENAI_COMPATIBLE.requiresBaseUrl)
        assertTrue(AIProviderType.CUSTOM.requiresBaseUrl)
    }

    @Test
    fun testAIManager_offlineFallbackWhenNoKeyConfigured() = runBlocking {
        val manager = AIManager(null) // Empty config with no API key
        val result = manager.evaluateAnswer(
            questionText = "Air is the most important ______.",
            acceptedAnswers = listOf("element"),
            userAnswer = "element"
        )

        assertTrue(result.isSuccess)
        val evaluation = result.getOrThrow()
        assertTrue("Exact match should succeed via fallback normalizer", evaluation.isCorrect)
        assertNotNull(evaluation.banglaExplanation)
    }

    @Test
    fun testAIManager_numericalAndUnitEquivalenceInOfflineFallback() = runBlocking {
        val manager = AIManager(null)

        // Example 1: 60-70% vs ৬০–৭০%
        val res1 = manager.evaluateAnswer(
            questionText = "মানুষের শরীরের কত ভাগ পানি?",
            acceptedAnswers = listOf("৬০–৭০%"),
            userAnswer = "60-70%"
        )
        assertTrue(res1.isSuccess)
        assertTrue("60-70% must be evaluated as correct against ৬০–৭০%", res1.getOrThrow().isCorrect)

        // Example 2: 1000 kg vs ১,০০০ কেজি
        val res2 = manager.evaluateAnswer(
            questionText = "ধানের ওজন কত?",
            acceptedAnswers = listOf("১,০০০ কেজি"),
            userAnswer = "1000 kg"
        )
        assertTrue(res2.isSuccess)
        assertTrue("1000 kg must be evaluated as correct against ১,০০০ কেজি", res2.getOrThrow().isCorrect)
    }

    // =========================================================================
    // 4. QUIZ ENGINE + AI INTEGRATION FOR SPECIFICATION EXAMPLES
    // =========================================================================

    @Test
    fun testSpecificationExample1_NumericalEquivalence() = runBlocking {
        val schema = QuizSchema(
            title = "Biology GK",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.FILL_BLANK,
                    question = "মানুষের শরীরের কত ভাগ পানি?",
                    fillBlankAnswer = "৬০–৭০%",
                    acceptedAnswers = listOf("৬০–৭০%"),
                    points = 5
                )
            )
        )

        val engine = QuizEngine("bio_quiz", schema, QuizMode.PRACTICE)
        val feedback = engine.submitPracticeFillBlankAnswer("60-70%")

        assertNotNull(feedback)
        assertTrue("Example 1: '60-70%' must be CORRECT for '৬০–৭০%'", feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(1, engine.streak)
        assertEquals(AnswerState.CORRECT, engine.getQuestionState(0).answerState)
    }

    @Test
    fun testSpecificationExample2_UnitEquivalence() = runBlocking {
        val schema = QuizSchema(
            title = "Physics Measurement",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.FILL_BLANK,
                    question = "১ মেট্রিক টন সমান কত?",
                    fillBlankAnswer = "১,০০০ কেজি",
                    acceptedAnswers = listOf("১,০০০ কেজি"),
                    points = 5
                )
            )
        )

        val engine = QuizEngine("physics_quiz", schema, QuizMode.PRACTICE)
        val feedback = engine.submitPracticeFillBlankAnswer("1000 kg")

        assertNotNull(feedback)
        assertTrue("Example 2: '1000 kg' must be CORRECT for '১,০০০ কেজি'", feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(AnswerState.CORRECT, engine.getQuestionState(0).answerState)
    }

    @Test
    fun testSpecificationExample3_GrammarCheckSingularVsPlural() = runBlocking {
        // AI evaluator that enforces the grammatical rule: singular required
        val grammarEvaluator = object : AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<AiEvaluationResult> {
                val isCorrect = userAnswer.trim() == "element"
                return Result.success(
                    AiEvaluationResult(
                        isCorrect = isCorrect,
                        confidence = 0.99,
                        reason = if (isCorrect) "Correct singular" else "Grammar requires singular form",
                        banglaExplanation = if (isCorrect) {
                            "সঠিক উত্তর।"
                        } else {
                            "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।"
                        },
                        matchedAnswer = "element"
                    )
                )
            }
        }

        val schema = QuizSchema(
            title = "English Grammar",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.FILL_BLANK,
                    question = "Air is the most important (a) — of human environment.",
                    fillBlankAnswer = "element",
                    acceptedAnswers = listOf("element"),
                    points = 5
                )
            )
        )

        val engine = QuizEngine("grammar_quiz", schema, QuizMode.PRACTICE, aiEvaluator = grammarEvaluator)
        val feedback = engine.submitPracticeFillBlankAnswer("elements")

        assertNotNull(feedback)
        assertFalse("Example 3: 'elements' must be WRONG because singular is required", feedback!!.isCorrect)
        assertEquals(0, engine.score)
        assertEquals(0, engine.streak)
        assertEquals(AnswerState.INCORRECT, engine.getQuestionState(0).answerState)
        assertTrue(feedback.banglaExplanation!!.contains("singular"))
    }

    @Test
    fun testSpecificationExample4_ValidAlternativeContextuallyAccepted() = runBlocking {
        // AI evaluator that semantically validates "damage" for "harm" in context
        val contextEvaluator = object : AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<AiEvaluationResult> {
                val fits = userAnswer.trim() in listOf("harm", "damage")
                return Result.success(
                    AiEvaluationResult(
                        isCorrect = fits,
                        confidence = 0.95,
                        reason = "Natural collocation in context",
                        banglaExplanation = "তোমার উত্তরটি সঠিক। 'damage' শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী যথাযথভাবে বসে।",
                        matchedAnswer = "harm"
                    )
                )
            }
        }

        val schema = QuizSchema(
            title = "Environment Studies",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.FILL_BLANK,
                    question = "These materials cause (d) — to the environment.",
                    fillBlankAnswer = "harm",
                    acceptedAnswers = listOf("harm"),
                    points = 5
                )
            )
        )

        val engine = QuizEngine("env_quiz", schema, QuizMode.PRACTICE, aiEvaluator = contextEvaluator)
        val feedback = engine.submitPracticeFillBlankAnswer("damage")

        assertNotNull(feedback)
        assertTrue("Example 4: 'damage' must be accepted as valid alternative for 'harm'", feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertTrue(feedback.isAlternativeAccepted)
    }

    @Test
    fun testSpecificationExample5_MultipleStoredAnswersBothAccepted() = runBlocking {
        val schema = QuizSchema(
            title = "Waste Management",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.FILL_BLANK,
                    question = "Proper waste ______ is crucial.",
                    fillBlankAnswer = "management",
                    acceptedAnswers = listOf("management", "disposal"),
                    points = 5
                )
            )
        )

        // Test first answer "management"
        val engine1 = QuizEngine("waste1", schema, QuizMode.PRACTICE)
        val fb1 = engine1.submitPracticeFillBlankAnswer("management")
        assertNotNull(fb1)
        assertTrue("Example 5: 'management' must be accepted", fb1!!.isCorrect)

        // Test second answer "disposal"
        val engine2 = QuizEngine("waste2", schema, QuizMode.PRACTICE)
        val fb2 = engine2.submitPracticeFillBlankAnswer("disposal")
        assertNotNull(fb2)
        assertTrue("Example 5: 'disposal' must be accepted", fb2!!.isCorrect)
    }
}
