package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizMode
import com.example.data.model.QuizSchema
import com.example.data.repository.QuizRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuizEditFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: QuizRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = QuizRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testParseLenient_backwardCompatibility_noTypeField() {
        // Question has options but no type field: must be inferred as MCQ without crashing
        val legacyJson = """
            {
                "title": "Legacy Quiz",
                "questions": [
                    {
                        "id": "q1",
                        "question": "What is the capital of Bangladesh?",
                        "options": ["Chittagong", "Dhaka", "Sylhet"],
                        "answer": 1
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.parseLenient(legacyJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        assertEquals("Legacy Quiz", schema.title)
        assertEquals(1, schema.questions.size)
        assertEquals(QuestionType.MCQ, schema.questions[0].type)
        assertEquals(1, schema.questions[0].answer)
        assertTrue(schema.questions[0].isAnswerIndexValid)
    }

    @Test
    fun testParseLenient_fillBlank_supportsStringAndArrayAnswers() {
        val json = """
            {
                "title": "Fill in Blank Test",
                "questions": [
                    {
                        "id": "fb1",
                        "type": "fill_blank",
                        "question": "The national animal of Bangladesh is the _____ tiger.",
                        "answer": "Royal Bengal"
                    },
                    {
                        "id": "fb2",
                        "type": "fill_blank",
                        "question": "Capital city of Bangladesh?",
                        "answer": ["Dhaka", "ঢাকা", "dhaka"]
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.parseLenient(json)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        assertEquals(2, schema.questions.size)

        val q1 = schema.questions[0]
        assertEquals(QuestionType.FILL_BLANK, q1.type)
        assertEquals("Royal Bengal", q1.fillBlankAnswer)
        assertTrue(q1.acceptedAnswers.contains("Royal Bengal"))

        val q2 = schema.questions[1]
        assertEquals(QuestionType.FILL_BLANK, q2.type)
        assertEquals("Dhaka", q2.fillBlankAnswer)
        assertEquals(3, q2.acceptedAnswers.size)
        assertTrue(q2.acceptedAnswers.contains("ঢাকা"))
    }

    @Test
    fun testParseLenient_answerIndexSafety_doesNotCrashOnOutOfBounds() {
        // answer = 5 with only 2 options: must parse safely without throwing
        val brokenAnswerJson = """
            {
                "title": "Index Safety Test",
                "questions": [
                    {
                        "id": "q1",
                        "type": "mcq",
                        "question": "Test question?",
                        "options": ["Option 0", "Option 1"],
                        "answer": 5
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.parseLenient(brokenAnswerJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        val q = schema.questions[0]
        assertEquals(5, q.answer)
        assertFalse(q.isAnswerIndexValid)

        // Validation for saving should detect this invalid answer index safely
        val errors = QuizJsonParser.validateSchemaForSaving(schema)
        assertTrue(errors.any { it.contains("Answer index") && it.contains("out of bounds") })
    }

    @Test
    fun testParseLenient_resolvesStringAndLetterAnswersForMcq() {
        val stringAnswerJson = """
            {
                "title": "String Answer Test",
                "questions": [
                    {
                        "id": "q1",
                        "type": "mcq",
                        "question": "Letter answer?",
                        "options": ["A-val", "B-val", "C-val"],
                        "answer": "B"
                    },
                    {
                        "id": "q2",
                        "type": "mcq",
                        "question": "Text match answer?",
                        "options": ["Cat", "Dog", "Fish"],
                        "answer": "Dog"
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.parseLenient(stringAnswerJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        assertEquals(1, schema.questions[0].answer) // "B" -> 1
        assertEquals(1, schema.questions[1].answer) // "Dog" -> 1
        assertTrue(schema.questions[0].isAnswerIndexValid)
        assertTrue(schema.questions[1].isAnswerIndexValid)
    }

    @Test
    fun testParseLenient_mixedBengaliAndEnglishQuiz() {
        val mixedJson = """
            {
                "title": "বাংলাদেশ ও সাধারণ জ্ঞান (Mixed)",
                "description": "দ্বিভাষিক কুইজ",
                "questions": [
                    {
                        "id": "bn1",
                        "question": "বাংলাদেশের জাতীয় ফুল কোনটি?",
                        "options": ["শাপলা", "গোলাপ", "বেলী", "জবা"],
                        "answer": 0,
                        "explanation": "শাপলা বাংলাদেশের জাতীয় ফুল।"
                    },
                    {
                        "id": "en1",
                        "type": "fill_blank",
                        "question": "What is the capital of Bangladesh?",
                        "answer": ["Dhaka", "ঢাকা"]
                    }
                ]
            }
        """.trimIndent()

        val result = QuizJsonParser.parseLenient(mixedJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        assertEquals(2, schema.questions.size)
        assertEquals("বাংলাদেশ ও সাধারণ জ্ঞান (Mixed)", schema.title)
        assertEquals(QuestionType.MCQ, schema.questions[0].type)
        assertEquals("শাপলা", schema.questions[0].options[schema.questions[0].answer])
        assertEquals(QuestionType.FILL_BLANK, schema.questions[1].type)
        assertEquals("Dhaka", schema.questions[1].fillBlankAnswer)
    }

    @Test
    fun testValidateSchemaForSaving_detectsAllInvalidFields() {
        val invalidSchema = QuizSchema(
            title = "",
            timeLimit = -5,
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.MCQ,
                    question = "",
                    options = listOf("Only One"),
                    answer = 3,
                    points = 0
                ),
                QuestionSchema(
                    id = "q2",
                    type = QuestionType.FILL_BLANK,
                    question = "Valid text",
                    fillBlankAnswer = "",
                    acceptedAnswers = emptyList(),
                    points = 1
                )
            )
        )

        val errors = QuizJsonParser.validateSchemaForSaving(invalidSchema)
        assertTrue(errors.any { it.contains("title cannot be empty") })
        assertTrue(errors.any { it.contains("Time limit cannot be negative") })
        assertTrue(errors.any { it.contains("Question text cannot be empty") })
        assertTrue(errors.any { it.contains("Points must be at least 1") })
        assertTrue(errors.any { it.contains("at least 2 options") })
        assertTrue(errors.any { it.contains("Answer index") })
        assertTrue(errors.any { it.contains("requires at least one accepted answer") })
    }

    @Test
    fun testDatabase_insertOrUpdateQuiz_updatesExistingAndCleansUnfinished() = runBlocking {
        val initialSchema = QuizSchema(
            title = "Original Title",
            description = "Original Description",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.MCQ,
                    question = "Original question?",
                    options = listOf("A", "B"),
                    answer = 0
                )
            )
        )

        // 1. Insert new quiz
        val quizId = repository.insertOrUpdateQuiz(initialSchema, null)
        repository.toggleFavorite(quizId, false) // Mark as favorite

        val savedQuiz = repository.getQuizById(quizId)
        assertNotNull(savedQuiz)
        assertTrue(savedQuiz!!.isFavorite)

        // 2. Save unfinished attempt for this quiz
        db.unfinishedQuizDao().saveUnfinishedQuiz(
            UnfinishedQuizEntity(
                quizId = quizId,
                quizTitle = "Original Title",
                category = "General",
                mode = QuizMode.PRACTICE.name,
                currentQuestionIndex = 0,
                totalQuestions = 1,
                timeRemainingSeconds = 60,
                score = 0,
                streak = 0,
                questionOrderJson = "[\"q1\"]",
                optionOrderJson = "{}",
                answersStateJson = "{}",
                lockedStateJson = "{}"
            )
        )
        assertNotNull(db.unfinishedQuizDao().getUnfinishedQuiz(quizId))

        // 3. Edit and update the quiz
        val editedSchema = initialSchema.copy(
            title = "Edited Title",
            questions = listOf(
                QuestionSchema(
                    id = "q1",
                    type = QuestionType.MCQ,
                    question = "Edited question?",
                    options = listOf("A", "B", "C"),
                    answer = 2
                )
            )
        )
        val returnedId = repository.insertOrUpdateQuiz(editedSchema, quizId)
        assertEquals(quizId, returnedId) // ID must remain unchanged!

        // 4. Verify in DB
        val updatedQuiz = repository.getQuizById(quizId)
        assertNotNull(updatedQuiz)
        assertEquals("Edited Title", updatedQuiz!!.title)
        assertTrue(updatedQuiz.isFavorite) // Favorite status must remain intact!

        // 5. Stale unfinished progress must be removed
        val unfinishedAfterEdit = db.unfinishedQuizDao().getUnfinishedQuiz(quizId)
        assertNull(unfinishedAfterEdit)
    }
}
