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
    fun testExamMode_submissionAndReview() {
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

        val summary = engine.submitExam()
        assertEquals(10, summary.score)
        assertEquals(20, summary.maxScore)
        assertEquals(1, summary.correctCount)
        assertEquals(0, summary.wrongCount)
        assertEquals(1, summary.unansweredCount)
        assertEquals(50f, summary.accuracy, 0.01f)
    }
}
