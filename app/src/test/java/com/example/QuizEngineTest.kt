package com.example

import com.example.data.model.QuestionSchema
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizMode
import com.example.data.model.QuizSchema
import com.example.data.samples.SampleQuizzes
import com.example.engine.QuizEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuizEngineTest {

    @Test
    fun testJsonParser_validatesAndParsesCorrectly() {
        val sample = SampleQuizzes.BENGALI_QUIZ
        val jsonString = QuizJsonParser.toJsonString(sample)

        val parseResult = QuizJsonParser.validateAndParse(jsonString)
        assertTrue(parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals(sample.title, parsed.title)
        assertEquals(sample.questions.size, parsed.questions.size)
        assertEquals("কাজী নজরুল ইসলাম", parsed.questions[0].options[parsed.questions[0].answer])
    }

    @Test
    fun testJsonParser_rejectsInvalidJson() {
        val invalidJson = """
            {
                "title": "Broken Quiz",
                "questions": [
                    {
                        "id": "q1",
                        "question": "Invalid answer index",
                        "options": ["A", "B"],
                        "answer": 5
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.validateAndParse(invalidJson)
        assertTrue(result.isFailure)
    }

    @Test
    fun testShuffle_preservesCorrectAnswerMapping() {
        // Test 50 iterations to ensure shuffling never breaks answer mapping
        val schema = QuizSchema(
            title = "Shuffle Test",
            description = "Test answer mapping integrity",
            category = "Testing",
            difficulty = "Easy",
            timeLimit = 60,
            shuffleQuestions = true,
            shuffleOptions = true,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "What is 2 + 2?",
                    options = listOf("1", "2", "3", "4", "5"),
                    answer = 3, // "4"
                    points = 2,
                    explanation = "Basic math"
                ),
                QuestionSchema(
                    id = "q2",
                    question = "Capital of France?",
                    options = listOf("Berlin", "Madrid", "Paris", "Rome"),
                    answer = 2, // "Paris"
                    points = 3,
                    explanation = "Geography"
                )
            )
        )

        repeat(50) {
            val engine = QuizEngine("test_quiz", schema, QuizMode.PRACTICE)
            assertEquals(2, engine.questions.size)

            for (activeQ in engine.questions) {
                val correctOptionText = activeQ.options[activeQ.correctAnswerIndex]
                if (activeQ.id == "q1") {
                    assertEquals("4", correctOptionText)
                } else if (activeQ.id == "q2") {
                    assertEquals("Paris", correctOptionText)
                }
            }
        }
    }

    @Test
    fun testPracticeMode_immediateLockAndScoring() {
        val schema = QuizSchema(
            title = "Practice Test",
            description = "",
            category = "Test",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Q1",
                    options = listOf("Wrong", "Right"),
                    answer = 1,
                    points = 5,
                    explanation = "Test explanation"
                )
            )
        )

        val engine = QuizEngine("p_test", schema, QuizMode.PRACTICE)
        assertFalse(engine.isCurrentQuestionLocked)

        // Select correct answer
        val feedback = engine.selectOption(1)
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(1, engine.streak)
        assertTrue(engine.isCurrentQuestionLocked)

        // Attempting to select another option while locked should do nothing
        val secondFeedback = engine.selectOption(0)
        assertEquals(null, secondFeedback)
        assertEquals(1, engine.selectedAnswers[0])
    }

    @Test
    fun testExamMode_submissionAndReview() = kotlinx.coroutines.runBlocking {
        val schema = QuizSchema(
            title = "Exam Test",
            description = "",
            category = "Test",
            difficulty = "Hard",
            timeLimit = 100,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(id = "q1", question = "Q1", options = listOf("A", "B"), answer = 0, points = 10),
                QuestionSchema(id = "q2", question = "Q2", options = listOf("C", "D"), answer = 1, points = 10)
            )
        )

        val engine = QuizEngine("exam_test", schema, QuizMode.EXAM)

        // Select option for Q1
        engine.selectOption(0) // Correct (A)
        assertEquals(0, engine.score) // Score not calculated yet in Exam Mode!

        // Switch answer before submission
        engine.selectOption(1) // Changed to B
        engine.selectOption(0) // Changed back to A

        engine.nextQuestion()
        // Q2 left unanswered

        val summary = engine.submitExamSync()
        assertEquals(10, summary.score)
        assertEquals(20, summary.maxScore)
        assertEquals(1, summary.correctCount)
        assertEquals(0, summary.wrongCount)
        assertEquals(1, summary.unansweredCount)
        assertEquals(50f, summary.accuracy, 0.01f)
    }

    @Test
    fun testOpenJsonDocumentContract_configuresIntentProperly() {
        val contract = com.example.ui.screens.OpenJsonDocumentContract()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = contract.createIntent(context, Unit)

        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue("Must include CATEGORY_OPENABLE", intent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)

        val mimeTypes = intent.getStringArrayExtra(android.content.Intent.EXTRA_MIME_TYPES)
        assertNotNull(mimeTypes)
        assertTrue(mimeTypes!!.contains("application/json"))
        assertTrue(mimeTypes.contains("text/json"))
        assertTrue(mimeTypes.contains("text/plain"))
        assertTrue(mimeTypes.contains("application/octet-stream"))
        assertTrue(mimeTypes.contains("*/*"))
    }

    @Test
    fun testBanglaJson_fileParsingAndEngineExecution() = kotlinx.coroutines.runBlocking {
        val file = java.io.File("/MT2/bangla.json")
        val jsonContent = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            QuizJsonParser.toJsonString(SampleQuizzes.BENGALI_QUIZ)
        }

        // Validate parsing
        val parseResult = QuizJsonParser.validateAndParse(jsonContent)
        assertTrue(parseResult.isSuccess)

        val quizSchema = parseResult.getOrThrow()
        assertEquals("বাংলাদেশ ও সাধারণ জ্ঞান", quizSchema.title)
        assertTrue(quizSchema.questions.isNotEmpty())

        // Verify Practice Mode
        val practiceEngine = QuizEngine("bangla_practice", quizSchema, QuizMode.PRACTICE)
        assertEquals(quizSchema.questions.size, practiceEngine.questions.size)
        val firstQ = practiceEngine.currentQuestion
        assertNotNull(firstQ)
        val feedback = practiceEngine.selectOption(firstQ!!.correctAnswerIndex)
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)

        // Verify Exam Mode
        val examEngine = QuizEngine("bangla_exam", quizSchema, QuizMode.EXAM)
        val examQ = examEngine.currentQuestion
        assertNotNull(examQ)
        examEngine.selectOption(examQ!!.correctAnswerIndex)
        val examSummary = examEngine.submitExamSync()
        assertTrue(examSummary.correctCount >= 1)
        assertTrue(examSummary.score > 0)
    }

    @Test
    fun testFileExtensionValidation_caseInsensitive() {
        val validFileNames = listOf("quiz.json", "BANGLA.JSON", "test.Json", "questions.jSoN")
        val invalidFileNames = listOf("quiz.txt", "document.pdf", "image.png", "notes.doc")

        for (name in validFileNames) {
            assertTrue("Expected $name to be valid", name.endsWith(".json", ignoreCase = true))
        }

        for (name in invalidFileNames) {
            assertFalse("Expected $name to be invalid", name.endsWith(".json", ignoreCase = true))
        }
    }

    // =========================================================================
    // SECTION 21: SPECIFIC TEST CASES FROM SPECIFICATION
    // =========================================================================

    @Test
    fun testScenario1_PracticeCorrect_ImmediateLockAndScore() {
        val schema = QuizSchema(
            title = "Test 1 Practice Correct",
            description = "",
            category = "General",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Capital of France?",
                    options = listOf("London", "Berlin", "Paris", "Rome"),
                    answer = 2, // Paris
                    points = 5,
                    explanation = "Paris is the capital"
                )
            )
        )

        val engine = QuizEngine("test_1", schema, QuizMode.PRACTICE)
        assertEquals(0, engine.score)
        val initialQState = engine.getQuestionState(0)
        assertEquals(com.example.engine.AnswerState.UNANSWERED, initialQState.answerState)
        assertFalse(initialQState.isLocked)

        // User taps correct answer C (index 2)
        val feedback = engine.selectOption(2)
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(1, engine.streak)

        // Reactive state check
        val updatedQState = engine.getQuestionState(0)
        assertTrue(updatedQState.isAnswered)
        assertTrue(updatedQState.isLocked)
        assertEquals(2, updatedQState.selectedOptionIndex)
        assertEquals(com.example.engine.AnswerState.CORRECT, updatedQState.answerState)
    }

    @Test
    fun testScenario2_PracticeWrong_ImmediateLockSelectedWrongCorrectRevealedNoScore() {
        val schema = QuizSchema(
            title = "Test 2 Practice Wrong",
            description = "",
            category = "General",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "2 + 2 = ?",
                    options = listOf("3", "4", "5", "6"),
                    answer = 1, // 4
                    points = 3,
                    explanation = "2 + 2 = 4"
                )
            )
        )

        val engine = QuizEngine("test_2", schema, QuizMode.PRACTICE)
        // User taps wrong answer A (index 0)
        val feedback = engine.selectOption(0)
        assertNotNull(feedback)
        assertFalse(feedback!!.isCorrect)
        assertEquals(0, engine.score) // No points awarded
        assertEquals(0, engine.streak) // Streak reset

        val state = engine.getQuestionState(0)
        assertTrue(state.isAnswered)
        assertTrue(state.isLocked)
        assertEquals(0, state.selectedOptionIndex)
        assertEquals(com.example.engine.AnswerState.INCORRECT, state.answerState)
        // Correct answer remains index 1
        assertEquals(1, engine.currentQuestion!!.correctAnswerIndex)
    }

    @Test
    fun testScenario3_RapidDoubleTap_OnlyOneScoreIncrement() {
        val schema = QuizSchema(
            title = "Test 3 Rapid Tap",
            description = "",
            category = "General",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Fast tap test",
                    options = listOf("Wrong", "Right"),
                    answer = 1,
                    points = 10,
                    explanation = ""
                )
            )
        )

        val engine = QuizEngine("test_3", schema, QuizMode.PRACTICE)

        // First tap: processes and awards 10 points
        val firstFeedback = engine.selectOption(1)
        assertNotNull(firstFeedback)
        assertEquals(10, engine.score)

        // Rapid second and third taps
        val secondFeedback = engine.selectOption(1)
        val thirdFeedback = engine.selectOption(0)

        // Must be null and ignored
        assertEquals(null, secondFeedback)
        assertEquals(null, thirdFeedback)
        // Score MUST remain 10, never 20 or 30!
        assertEquals(10, engine.score)
        assertEquals(1, engine.streak)
    }

    @Test
    fun testScenario4_ExamSelect_VisualSelectionOnlyNoScoreNoSpoiler() {
        val schema = QuizSchema(
            title = "Test 4 Exam Select",
            description = "",
            category = "Exam",
            difficulty = "Medium",
            timeLimit = 60,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Q1",
                    options = listOf("A", "B", "C", "D"),
                    answer = 2,
                    points = 5
                )
            )
        )

        val engine = QuizEngine("test_4", schema, QuizMode.EXAM)
        // Select option B (index 1)
        val feedback = engine.selectOption(1)
        // In exam mode, feedback is null (no live spoiler)
        assertEquals(null, feedback)
        assertEquals(0, engine.score) // Score is 0 until submit

        val state = engine.getQuestionState(0)
        assertTrue(state.isAnswered)
        assertFalse(state.isLocked) // NOT locked, can be changed
        assertEquals(1, state.selectedOptionIndex)
        assertEquals(com.example.engine.AnswerState.UNANSWERED, state.answerState) // State is not revealed
    }

    @Test
    fun testScenario5_ExamChangeAnswer_SelectionUpdatesNoValidationUntilSubmit() = kotlinx.coroutines.runBlocking {
        val schema = QuizSchema(
            title = "Test 5 Exam Change Answer",
            description = "",
            category = "Exam",
            difficulty = "Medium",
            timeLimit = 60,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Q1",
                    options = listOf("A", "B", "C", "D"),
                    answer = 3, // D is correct
                    points = 5
                )
            )
        )

        val engine = QuizEngine("test_5", schema, QuizMode.EXAM)
        // Select B (index 1)
        engine.selectOption(1)
        assertEquals(1, engine.getQuestionState(0).selectedOptionIndex)

        // Change to D (index 3)
        engine.selectOption(3)
        assertEquals(3, engine.getQuestionState(0).selectedOptionIndex)

        // Still score 0
        assertEquals(0, engine.score)

        // Now submit
        val summary = engine.submitExamSync()
        assertEquals(5, summary.score)
        assertEquals(1, summary.correctCount)
        assertEquals(3, summary.reviewItems[0].userAnswerIndex)
    }

    @Test
    fun testScenario6_ExamSubmit_AllAnswersValidatedAndRevealed() = kotlinx.coroutines.runBlocking {
        val schema = QuizSchema(
            title = "Test 6 Exam Submit",
            description = "",
            category = "Exam",
            difficulty = "Hard",
            timeLimit = 120,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(id = "q1", question = "Q1", options = listOf("A", "B"), answer = 0, points = 4),
                QuestionSchema(id = "q2", question = "Q2", options = listOf("C", "D"), answer = 1, points = 6),
                QuestionSchema(id = "q3", question = "Q3", options = listOf("E", "F"), answer = 0, points = 5)
            )
        )

        val engine = QuizEngine("test_6", schema, QuizMode.EXAM)
        engine.selectOption(0) // Q1 correct (A)
        engine.nextQuestion()
        engine.selectOption(0) // Q2 wrong (C, correct is D)
        engine.nextQuestion()
        // Q3 left unanswered

        val summary = engine.submitExamSync()
        assertEquals(4, summary.score)
        assertEquals(15, summary.maxScore)
        assertEquals(1, summary.correctCount)
        assertEquals(1, summary.wrongCount)
        assertEquals(1, summary.unansweredCount)

        // Post-submit questionStates are locked and revealed
        val stateQ1 = engine.getQuestionState(0)
        assertEquals(com.example.engine.AnswerState.CORRECT, stateQ1.answerState)
        assertTrue(stateQ1.isLocked)

        val stateQ2 = engine.getQuestionState(1)
        assertEquals(com.example.engine.AnswerState.INCORRECT, stateQ2.answerState)
        assertTrue(stateQ2.isLocked)

        val stateQ3 = engine.getQuestionState(2)
        assertEquals(com.example.engine.AnswerState.UNANSWERED, stateQ3.answerState)
        assertTrue(stateQ3.isLocked)
    }

    @Test
    fun testScenario7_ShuffledOptions_PreservesCorrectAnswerMapping() {
        val schema = QuizSchema(
            title = "Test 7 Shuffled Options",
            description = "",
            category = "Test",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = true,
            shuffleOptions = true,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Capital of Japan?",
                    options = listOf("Kyoto", "Osaka", "Tokyo", "Hiroshima"),
                    answer = 2, // "Tokyo"
                    points = 5
                ),
                QuestionSchema(
                    id = "q2",
                    question = "Largest planet?",
                    options = listOf("Earth", "Mars", "Jupiter", "Venus", "Saturn"),
                    answer = 2, // "Jupiter"
                    points = 5
                )
            )
        )

        repeat(30) {
            val engine = QuizEngine("shuffle_test", schema, QuizMode.PRACTICE)
            for (q in engine.questions) {
                val correctText = q.options[q.correctAnswerIndex]
                if (q.id == "q1") {
                    assertEquals("Tokyo", correctText)
                } else if (q.id == "q2") {
                    assertEquals("Jupiter", correctText)
                }
            }
        }
    }

    @Test
    fun testScenario8_Navigation_MaintainsPreviousAnswerStateAndStartsNewUnanswered() {
        val schema = QuizSchema(
            title = "Test 8 Navigation",
            description = "",
            category = "Test",
            difficulty = "Easy",
            timeLimit = 0,
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(id = "q1", question = "Q1", options = listOf("A", "B"), answer = 1, points = 2),
                QuestionSchema(id = "q2", question = "Q2", options = listOf("C", "D"), answer = 0, points = 3)
            )
        )

        val engine = QuizEngine("test_8", schema, QuizMode.PRACTICE)
        // Answer Q1 correctly
        engine.selectOption(1)
        assertTrue(engine.getQuestionState(0).isAnswered)
        assertEquals(com.example.engine.AnswerState.CORRECT, engine.getQuestionState(0).answerState)

        // Move to Q2
        assertTrue(engine.nextQuestion())
        assertEquals(1, engine.currentQuestionIndex)

        // Q2 must start clean and UNANSWERED
        val q2State = engine.getQuestionState(1)
        assertFalse(q2State.isAnswered)
        assertFalse(q2State.isLocked)
        assertEquals(com.example.engine.AnswerState.UNANSWERED, q2State.answerState)

        // Navigate back to Q1
        assertTrue(engine.previousQuestion())
        assertEquals(0, engine.currentQuestionIndex)

        // Q1 state must still be intact: answered, locked, correct
        val restoredQ1State = engine.getQuestionState(0)
        assertTrue(restoredQ1State.isAnswered)
        assertTrue(restoredQ1State.isLocked)
        assertEquals(1, restoredQ1State.selectedOptionIndex)
        assertEquals(com.example.engine.AnswerState.CORRECT, restoredQ1State.answerState)
    }

    @Test
    fun testFillBlank_ParsingAndBackwardCompatibility() {
        val mixedJson = """
            {
                "title": "Mixed Question Test",
                "questions": [
                    {
                        "id": "q1",
                        "question": "What is 2 + 2?",
                        "options": ["3", "4", "5"],
                        "answer": 1
                    },
                    {
                        "id": "q2",
                        "type": "fill_blank",
                        "question": "The capital of Bangladesh is _____.",
                        "answer": "Dhaka",
                        "acceptedAnswers": ["Dhaka", "ঢাকা"]
                    },
                    {
                        "id": "q3",
                        "type": "fill_blank",
                        "question": "Water chemical formula",
                        "answer": ["H2O", "h2o"]
                    }
                ]
            }
        """.trimIndent()

        val parsed = QuizJsonParser.validateAndParse(mixedJson).getOrThrow()
        assertEquals(3, parsed.questions.size)

        // Q1 is auto-detected as MCQ
        assertEquals(com.example.data.model.QuestionType.MCQ, parsed.questions[0].type)
        assertEquals(1, parsed.questions[0].answer)
        assertEquals(3, parsed.questions[0].options.size)

        // Q2 is FILL_BLANK with primary answer and accepted answers
        assertEquals(com.example.data.model.QuestionType.FILL_BLANK, parsed.questions[1].type)
        assertEquals("Dhaka", parsed.questions[1].fillBlankAnswer)
        assertTrue(parsed.questions[1].acceptedAnswers.contains("ঢাকা"))

        // Q3 is FILL_BLANK with array answer
        assertEquals(com.example.data.model.QuestionType.FILL_BLANK, parsed.questions[2].type)
        assertEquals("H2O", parsed.questions[2].fillBlankAnswer)
        assertEquals(2, parsed.questions[2].acceptedAnswers.size)
    }

    @Test
    fun testFillBlank_AnswerComparisonNormalization() {
        val accepted = listOf("Dhaka", "ঢাকা", "New York City")

        // Exact match
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("Dhaka", accepted))
        // Case-insensitivity
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("dhaka", accepted))
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("DHAKA", accepted))
        // Leading/trailing whitespace
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("   dhaka   ", accepted))
        // Internal multiple spaces
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("new   york   city", accepted))
        // Bengali exact
        assertTrue(com.example.data.model.AnswerComparison.isAnswerCorrect("ঢাকা", accepted))

        // Incorrect answers
        assertFalse(com.example.data.model.AnswerComparison.isAnswerCorrect("Chittagong", accepted))
        assertFalse(com.example.data.model.AnswerComparison.isAnswerCorrect("", accepted))
        assertFalse(com.example.data.model.AnswerComparison.isAnswerCorrect("   ", accepted))
    }

    @Test
    fun testFillBlank_PracticeMode_LiveFeedbackAndPoints() {
        val schema = QuizSchema(
            title = "Fill Blank Practice",
            questions = listOf(
                QuestionSchema(
                    id = "fb1",
                    type = com.example.data.model.QuestionType.FILL_BLANK,
                    question = "Capital of France?",
                    fillBlankAnswer = "Paris",
                    acceptedAnswers = listOf("Paris", "paris"),
                    points = 5
                )
            )
        )

        val engine = QuizEngine("fb_quiz", schema, QuizMode.PRACTICE)
        assertEquals(0, engine.score)
        assertEquals(0, engine.streak)

        // Submit correct answer
        val feedback = engine.submitTextAnswer("  paris  ")
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(1, engine.streak)

        // Question must now be locked
        assertTrue(engine.isCurrentQuestionLocked)
        assertEquals(com.example.engine.AnswerState.CORRECT, engine.getQuestionState(0).answerState)

        // Attempting to submit again should return null and not modify score
        val secondFeedback = engine.submitTextAnswer("Rome")
        assertEquals(null, secondFeedback)
        assertEquals(5, engine.score)
    }

    @Test
    fun testFillBlank_ExamMode_EditableAndSubmittedCorrectly() = kotlinx.coroutines.runBlocking {
        val schema = QuizSchema(
            title = "Fill Blank Exam",
            questions = listOf(
                QuestionSchema(
                    id = "fb1",
                    type = com.example.data.model.QuestionType.FILL_BLANK,
                    question = "Capital of Japan?",
                    fillBlankAnswer = "Tokyo",
                    acceptedAnswers = listOf("Tokyo", "tokyo"),
                    points = 2
                ),
                QuestionSchema(
                    id = "mcq1",
                    type = com.example.data.model.QuestionType.MCQ,
                    question = "2 + 2?",
                    options = listOf("3", "4"),
                    answer = 1,
                    points = 3
                )
            )
        )

        val engine = QuizEngine("exam_quiz", schema, QuizMode.EXAM)

        // Fill-in Q1: initial answer
        engine.updateExamTextAnswer("Kyoto")
        assertEquals("Kyoto", engine.userTextAnswers[0])
        assertFalse(engine.isCurrentQuestionLocked)

        // Update answer before submitting exam
        engine.updateExamTextAnswer("Tokyo")
        assertEquals("Tokyo", engine.userTextAnswers[0])

        // Move to Q2 and select correct MCQ option
        assertTrue(engine.nextQuestion())
        engine.selectOption(1)

        // Submit Exam
        val summary = engine.submitExamSync()
        assertEquals(2, summary.totalQuestions)
        assertEquals(2, summary.correctCount)
        assertEquals(0, summary.wrongCount)
        assertEquals(5, summary.score)
        assertEquals(100f, summary.accuracy)

        // Verify review items
        val q1Review = summary.reviewItems[0]
        assertEquals("Tokyo", q1Review.userAnswerText)
        assertEquals("Tokyo", q1Review.correctAnswerText)
        assertTrue(q1Review.isCorrect)

        val q2Review = summary.reviewItems[1]
        assertEquals("4", q2Review.userAnswerText)
        assertTrue(q2Review.isCorrect)
    }

    @Test
    fun testFillBlank_emptyAnswer_validatesAndImportsSuccessfully() {
        val json = """
            {
              "title": "Environmental Studies",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Air pollution is one kind of (a) — for the environment.",
                  "answer": "",
                  "points": 1,
                  "explanation": ""
                }
              ]
            }
        """.trimIndent()

        val parseResult = QuizJsonParser.validateAndParse(json)
        assertTrue("JSON with empty fill_blank answer must parse successfully", parseResult.isSuccess)

        val schema = parseResult.getOrThrow()
        assertEquals(1, schema.questions.size)
        val q = schema.questions[0]
        assertEquals("q1", q.id)
        assertEquals(com.example.data.model.QuestionType.FILL_BLANK, q.type)
        assertEquals("", q.fillBlankAnswer)
        assertTrue(q.acceptedAnswers.isEmpty())
        assertFalse(q.isAnswerConfigured)
    }

    @Test
    fun testFillBlank_emptyAnswer_practiceMode_notJudged() {
        val schema = QuizSchema(
            title = "Empty Answer Practice",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = com.example.data.model.QuestionType.FILL_BLANK,
                    question = "Air pollution is one kind of (a) — for the environment.",
                    fillBlankAnswer = "",
                    acceptedAnswers = emptyList(),
                    points = 2
                )
            )
        )

        val engine = QuizEngine("practice_empty", schema, QuizMode.PRACTICE)
        val activeQ = engine.currentQuestion
        assertNotNull(activeQ)
        assertFalse(activeQ!!.hasConfiguredAnswer)

        // Submit user answer
        val feedback = engine.submitTextAnswer("threat")
        assertNotNull(feedback)
        assertTrue("Feedback must flag answer as not set", feedback!!.isAnswerNotSet)
        assertFalse("Feedback must not mark as correct", feedback.isCorrect)
        assertEquals(0, feedback.pointsEarned)
        assertEquals(0, engine.score)
        assertEquals(0, engine.streak)
        assertEquals("Answer not available", feedback.correctTextAnswer)

        val state = engine.questionStates[0]
        assertNotNull(state)
        assertEquals(com.example.engine.AnswerState.ANSWER_NOT_SET, state!!.answerState)
        assertTrue(state.isLocked)
    }

    @Test
    fun testFillBlank_emptyAnswer_examMode_treatedAsUnansweredInScoring() = kotlinx.coroutines.runBlocking {
        val schema = QuizSchema(
            title = "Empty Answer Exam",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = com.example.data.model.QuestionType.FILL_BLANK,
                    question = "Air pollution is one kind of (a) — for the environment.",
                    fillBlankAnswer = "",
                    acceptedAnswers = emptyList(),
                    points = 5
                ),
                QuestionSchema(
                    id = "q2",
                    type = com.example.data.model.QuestionType.MCQ,
                    question = "2 + 2 = ?",
                    options = listOf("3", "4", "5"),
                    answer = 1,
                    points = 5
                )
            )
        )

        val engine = QuizEngine("exam_empty", schema, QuizMode.EXAM)

        // User enters an answer for Q1
        engine.updateExamTextAnswer("hazard")
        assertEquals("hazard", engine.userTextAnswers[0])

        // User answers Q2 correctly
        assertTrue(engine.nextQuestion())
        engine.selectOption(1)

        // Submit Exam
        val summary = engine.submitExamSync()
        assertEquals(2, summary.totalQuestions)
        // Q1 is treated as unanswered during scoring because its answer is not configured
        assertEquals(1, summary.correctCount)
        assertEquals(0, summary.wrongCount)
        assertEquals(1, summary.unansweredCount)
        assertEquals(5, summary.score)

        // Verify review items
        val q1Review = summary.reviewItems[0]
        assertTrue(q1Review.isAnswerNotSet)
        assertFalse(q1Review.isCorrect)
        assertEquals(0, q1Review.pointsEarned)
        assertEquals("hazard", q1Review.userAnswerText)
        assertEquals("Answer not available", q1Review.correctAnswerText)

        val q2Review = summary.reviewItems[1]
        assertFalse(q2Review.isAnswerNotSet)
        assertTrue(q2Review.isCorrect)
        assertEquals(5, q2Review.pointsEarned)
    }

    @Test
    fun testMcqValidation_remainsStrict() {
        val invalidMcqJson = """
            {
              "title": "Strict MCQ Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "mcq",
                  "question": "What is A?",
                  "options": ["Option 1", "Option 2"],
                  "answer": 5
                }
              ]
            }
        """.trimIndent()

        val result = QuizJsonParser.validateAndParse(invalidMcqJson)
        assertTrue("MCQ with out-of-bounds answer must fail validation", result.isFailure)
    }

    @Test
    fun testFillBlank_singleAnswer_matchingAndPractice() {
        val json = """
            {
              "title": "Single Answer Quiz",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Air pollution is a ______ to human health.",
                  "answer": "threat",
                  "points": 3
                }
              ]
            }
        """.trimIndent()

        val parseResult = QuizJsonParser.validateAndParse(json)
        assertTrue(parseResult.isSuccess)
        val schema = parseResult.getOrThrow()
        assertEquals("threat", schema.questions[0].fillBlankAnswer)
        assertEquals(listOf("threat"), schema.questions[0].acceptedAnswers)

        // Test exact match
        var engine = QuizEngine("single_q", schema, QuizMode.PRACTICE)
        var feedback = engine.submitTextAnswer("threat")
        assertNotNull(feedback)
        assertTrue("Exact match 'threat' should be correct", feedback!!.isCorrect)
        assertEquals(3, engine.score)
        assertEquals(1, engine.streak)

        // Test case insensitivity (English)
        engine = QuizEngine("single_q2", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("THREAT")
        assertNotNull(feedback)
        assertTrue("Uppercase 'THREAT' should be accepted as correct", feedback!!.isCorrect)

        // Test whitespace handling (leading/trailing and multiple spaces)
        engine = QuizEngine("single_q3", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("   threat   ")
        assertNotNull(feedback)
        assertTrue("Padded '  threat  ' should be accepted as correct", feedback!!.isCorrect)

        // Test wrong answer
        engine = QuizEngine("single_q4", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("danger")
        assertNotNull(feedback)
        assertFalse("'danger' should be marked wrong when only 'threat' is accepted", feedback!!.isCorrect)
        assertEquals(0, engine.score)
        assertEquals(0, engine.streak)
    }

    @Test
    fun testFillBlank_multipleAcceptedAnswers_threatAndDanger() {
        val json = """
            {
              "title": "Multiple Accepted Answers Quiz",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Pollution is a serious ______.",
                  "answer": ["threat", "danger"],
                  "points": 2
                }
              ]
            }
        """.trimIndent()

        val parseResult = QuizJsonParser.validateAndParse(json)
        assertTrue(parseResult.isSuccess)
        val schema = parseResult.getOrThrow()
        assertEquals(listOf("threat", "danger"), schema.questions[0].acceptedAnswers)

        // User: "threat" -> Correct
        var engine = QuizEngine("m_test1", schema, QuizMode.PRACTICE)
        var feedback = engine.submitTextAnswer("threat")
        assertNotNull(feedback)
        assertTrue("User 'threat' must be correct", feedback!!.isCorrect)
        assertEquals(2, engine.score)

        // User: "danger" -> Correct
        engine = QuizEngine("m_test2", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("danger")
        assertNotNull(feedback)
        assertTrue("User 'danger' must be correct", feedback!!.isCorrect)
        assertEquals(2, engine.score)

        // User: "pollution" -> Wrong
        engine = QuizEngine("m_test3", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("pollution")
        assertNotNull(feedback)
        assertFalse("User 'pollution' must be wrong", feedback!!.isCorrect)
        assertEquals(0, engine.score)
    }

    @Test
    fun testFillBlank_multipleAcceptedAnswers_plantGrowCultivate() {
        val json = """
            {
              "title": "Three Answers Quiz",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Farmers ______ crops in spring.",
                  "answer": ["plant", "grow", "cultivate"],
                  "points": 4
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()

        // User: "grow" -> Correct
        val engine = QuizEngine("three_answers", schema, QuizMode.PRACTICE)
        val feedback = engine.submitTextAnswer("grow")
        assertNotNull(feedback)
        assertTrue("User 'grow' must match accepted answers ['plant', 'grow', 'cultivate']", feedback!!.isCorrect)
        assertEquals(4, engine.score)
    }

    @Test
    fun testFillBlank_bengaliAcceptedAnswers() {
        val json = """
            {
              "title": "বাংলা কুইজ",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "পৃথিবীর একমাত্র উপগ্রহের নাম ______।",
                  "answer": ["চাঁদ", "চন্দ্র"],
                  "points": 1
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()

        // "চাঁদ" -> Correct
        var engine = QuizEngine("bn1", schema, QuizMode.PRACTICE)
        var feedback = engine.submitTextAnswer("চাঁদ")
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)

        // "  চন্দ্র  " -> Correct
        engine = QuizEngine("bn2", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("  চন্দ্র  ")
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)

        // "সূর্য" -> Wrong
        engine = QuizEngine("bn3", schema, QuizMode.PRACTICE)
        feedback = engine.submitTextAnswer("সূর্য")
        assertNotNull(feedback)
        assertFalse(feedback!!.isCorrect)
    }

    @Test
    fun testAiEvaluation_evaluatesWithAiAndReturnsBanglaExplanation() = kotlinx.coroutines.runBlocking {
        var aiCalled = false
        val mockEvaluator = object : com.example.engine.AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<com.example.data.model.AiEvaluationResult> {
                aiCalled = true
                return Result.success(
                    com.example.data.model.AiEvaluationResult(
                        isCorrect = true,
                        confidence = 0.99,
                        matchedAnswer = "element",
                        banglaExplanation = "তোমার উত্তরটি সঠিক। 'element' শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।",
                        reason = "Exact match fitting correctly in sentence context"
                    )
                )
            }
        }

        val json = """
            {
              "title": "Exact Match Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Air is an important ______.",
                  "answer": ["element"],
                  "points": 5
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()
        val engine = QuizEngine("exact_ai", schema, QuizMode.PRACTICE, aiEvaluator = mockEvaluator)

        val feedback = engine.submitPracticeFillBlankAnswer("  element  ")
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertTrue("AI evaluator must be called for every submitted answer in real time", aiCalled)
        assertNotNull(feedback.banglaExplanation)
        assertEquals(
            "তোমার উত্তরটি সঠিক। 'element' শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী উপযুক্তভাবে বসে।",
            feedback.banglaExplanation
        )
    }

    @Test
    fun testAiEvaluation_callsAiOnNonExactMatchAndAcceptsValidAlternative() = kotlinx.coroutines.runBlocking {
        var aiCalledWith: Triple<String, List<String>, String>? = null
        val mockEvaluator = object : com.example.engine.AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<com.example.data.model.AiEvaluationResult> {
                aiCalledWith = Triple(questionText, acceptedAnswers, userAnswer)
                return Result.success(
                    com.example.data.model.AiEvaluationResult(
                        isCorrect = true,
                        confidence = 0.95,
                        reason = "Alternative valid answer in context",
                        banglaExplanation = "তোমার উত্তরটি সঠিক। এখানে ‘disposal’ শব্দটি বাক্যের অর্থ ও grammar অনুযায়ী ঠিকভাবে বসে।",
                        matchedAnswer = "management"
                    )
                )
            }
        }

        val json = """
            {
              "title": "Alternative Answer Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Proper waste ______ is crucial.",
                  "answer": ["management"],
                  "points": 5
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()
        val engine = QuizEngine("alt_ai", schema, QuizMode.PRACTICE, aiEvaluator = mockEvaluator)

        val feedback = engine.submitPracticeFillBlankAnswer("disposal")
        assertNotNull(feedback)
        assertTrue("AI accepted alternative must mark correct", feedback!!.isCorrect)
        assertEquals(5, engine.score)
        assertEquals(1, engine.streak)
        assertNotNull(aiCalledWith)
        assertEquals("disposal", aiCalledWith?.third)
        assertTrue(feedback.isAiEvaluated)
        assertTrue(feedback.isAlternativeAccepted)
        assertEquals(
            "তোমার উত্তরটি সঠিক। এখানে ‘disposal’ শব্দটি বাক্যের অর্থ ও grammar অনুযায়ী ঠিকভাবে বসে।",
            feedback.banglaExplanation
        )
    }

    @Test
    fun testAiEvaluation_rejectsGrammaticallyWrongForm() = kotlinx.coroutines.runBlocking {
        val mockEvaluator = object : com.example.engine.AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<com.example.data.model.AiEvaluationResult> {
                return Result.success(
                    com.example.data.model.AiEvaluationResult(
                        isCorrect = false,
                        confidence = 0.98,
                        reason = "Singular noun required after 'the most important'",
                        banglaExplanation = "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।",
                        matchedAnswer = "element"
                    )
                )
            }
        }

        val json = """
            {
              "title": "Grammar Check Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Air is the most important ______ of human environment.",
                  "answer": "element",
                  "points": 5
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()
        val engine = QuizEngine("grammar_ai", schema, QuizMode.PRACTICE, aiEvaluator = mockEvaluator)

        val feedback = engine.submitPracticeFillBlankAnswer("elements")
        assertNotNull(feedback)
        assertFalse("AI must reject grammatically incorrect plural", feedback!!.isCorrect)
        assertEquals(0, engine.score)
        assertEquals(0, engine.streak)
        assertTrue(feedback.isAiEvaluated)
        assertFalse(feedback.isAlternativeAccepted)
        assertEquals(
            "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।",
            feedback.banglaExplanation
        )
    }

    @Test
    fun testAiEvaluation_fallsBackGracefullyWhenAiFails() = kotlinx.coroutines.runBlocking {
        val failingEvaluator = object : com.example.engine.AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<com.example.data.model.AiEvaluationResult> {
                return Result.failure(java.net.SocketTimeoutException("AI timeout"))
            }
        }

        val json = """
            {
              "title": "Fallback Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "These materials cause ______ to the environment.",
                  "answer": ["harm", "damage"],
                  "points": 5
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()
        val engine = QuizEngine("fallback_ai", schema, QuizMode.PRACTICE, aiEvaluator = failingEvaluator)

        val feedback = engine.submitPracticeFillBlankAnswer("hurt")
        assertNotNull(feedback)
        assertFalse("When AI fails and exact match fails, must mark incorrect", feedback!!.isCorrect)
        assertEquals(0, engine.score)
        assertNotNull(feedback.banglaExplanation)
        assertTrue(feedback.banglaExplanation!!.contains("harm, damage"))
    }

    @Test
    fun testAiEvaluation_preventsDuplicateSubmissions() = kotlinx.coroutines.runBlocking {
        val mockEvaluator = object : com.example.engine.AiAnswerEvaluator {
            override suspend fun evaluateAnswer(
                questionText: String,
                acceptedAnswers: List<String>,
                userAnswer: String
            ): Result<com.example.data.model.AiEvaluationResult> {
                return Result.success(
                    com.example.data.model.AiEvaluationResult(
                        isCorrect = true,
                        confidence = 0.9,
                        reason = "Valid",
                        banglaExplanation = "সঠিক",
                        matchedAnswer = "water"
                    )
                )
            }
        }

        val json = """
            {
              "title": "Duplicate Prevention Test",
              "questions": [
                {
                  "id": "q1",
                  "type": "fill_blank",
                  "question": "Plants need ______ to grow.",
                  "answer": "water",
                  "points": 2
                }
              ]
            }
        """.trimIndent()

        val schema = QuizJsonParser.validateAndParse(json).getOrThrow()
        val engine = QuizEngine("dup_ai", schema, QuizMode.PRACTICE, aiEvaluator = mockEvaluator)

        val firstFeedback = engine.submitPracticeFillBlankAnswer("liquid")
        assertNotNull(firstFeedback)

        // Second submission should be blocked because the question is locked
        val secondFeedback = engine.submitPracticeFillBlankAnswer("liquid")
        assertEquals(null, secondFeedback)
    }

    @Test
    fun testMcq_shuffleOptionsFalse_preservesExactIndex() {
        val schema = QuizSchema(
            title = "Unshuffled MCQ",
            shuffleQuestions = false,
            shuffleOptions = false,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    question = "Index 0 is correct",
                    options = listOf("CorrectZero", "WrongOne", "WrongTwo"),
                    answer = 0,
                    points = 5
                ),
                QuestionSchema(
                    id = "q2",
                    question = "Last index is correct",
                    options = listOf("WrongZero", "WrongOne", "CorrectLast"),
                    answer = 2,
                    points = 5
                )
            )
        )

        val engine = QuizEngine("unshuffled", schema, QuizMode.PRACTICE)
        assertEquals(0, engine.questions[0].correctAnswerIndex)
        assertEquals(2, engine.questions[1].correctAnswerIndex)

        // Select correct for Q1
        val feedback1 = engine.selectOption(0)
        assertNotNull(feedback1)
        assertTrue(feedback1!!.isCorrect)
        assertEquals(5, engine.score)

        // Move to Q2 and select correct for Q2
        assertTrue(engine.nextQuestion())
        val feedback2 = engine.selectOption(2)
        assertNotNull(feedback2)
        assertTrue(feedback2!!.isCorrect)
        assertEquals(10, engine.score)
    }

    @Test
    fun testMcq_shuffleOptionsTrue_preservesAnswerTextAcrossIndices() {
        val schema = QuizSchema(
            title = "Shuffled MCQ",
            shuffleQuestions = false,
            shuffleOptions = true,
            questions = listOf(
                QuestionSchema(
                    id = "q_first",
                    question = "Correct was at index 0",
                    options = listOf("TargetZero", "B", "C", "D"),
                    answer = 0,
                    points = 5
                ),
                QuestionSchema(
                    id = "q_last",
                    question = "Correct was at last index",
                    options = listOf("W1", "W2", "W3", "TargetLast"),
                    answer = 3,
                    points = 5
                )
            )
        )

        repeat(20) {
            val engine = QuizEngine("shuffled_repeat", schema, QuizMode.PRACTICE)
            val qFirst = engine.questions[0]
            val qLast = engine.questions[1]

            assertTrue(qFirst.correctAnswerIndex in 0..3)
            assertEquals("TargetZero", qFirst.options[qFirst.correctAnswerIndex])

            assertTrue(qLast.correctAnswerIndex in 0..3)
            assertEquals("TargetLast", qLast.options[qLast.correctAnswerIndex])

            // Verify selecting correct option yields isCorrect = true
            val fbFirst = engine.selectOption(qFirst.correctAnswerIndex)
            assertNotNull(fbFirst)
            assertTrue(fbFirst!!.isCorrect)
        }
    }

    @Test
    fun testMcq_invalidAnswerIndex_neverFallsBackToOptionZero() = kotlinx.coroutines.runBlocking {
        // Construct question with invalid answer index (out of bounds)
        val schema = QuizSchema(
            title = "Invalid Answer Quiz",
            questions = listOf(
                QuestionSchema(
                    id = "q_invalid",
                    type = com.example.data.model.QuestionType.MCQ,
                    question = "What has no valid answer?",
                    options = listOf("Option 0", "Option 1", "Option 2"),
                    answer = 99, // Out of bounds!
                    points = 5
                )
            )
        )

        val engine = QuizEngine("invalid_engine", schema, QuizMode.PRACTICE)
        val q = engine.questions[0]
        assertEquals(-1, q.correctAnswerIndex)
        assertFalse(q.hasConfiguredAnswer)

        // User picks Option 0: must NOT be awarded points or marked correct!
        val feedback = engine.selectOption(0)
        assertNotNull(feedback)
        assertFalse(feedback!!.isCorrect)
        assertTrue(feedback.isAnswerNotSet)
        assertEquals(0, feedback.pointsEarned)
        assertEquals(0, engine.score)

        // In Exam mode: submitExamSync must treat invalid question safely
        val examEngine = QuizEngine("invalid_exam", schema, QuizMode.EXAM)
        examEngine.selectOption(0)
        val summary = examEngine.submitExamSync()
        assertEquals(0, summary.score)
        assertEquals(0, summary.correctCount)
        assertEquals(1, summary.unansweredCount)
        assertTrue(summary.reviewItems[0].isAnswerNotSet)
        assertFalse(summary.reviewItems[0].isCorrect)
    }
}

