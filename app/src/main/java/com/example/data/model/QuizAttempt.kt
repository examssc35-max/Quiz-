package com.example.data.model

enum class QuizMode {
    PRACTICE,
    EXAM
}

data class QuestionReviewItem(
    val questionNumber: Int,
    val questionText: String,
    val options: List<String> = emptyList(),
    val userAnswerIndex: Int?, // null if skipped/unanswered (for MCQ)
    val correctAnswerIndex: Int = -1, // for MCQ
    val userAnswerText: String?, // Selected option (MCQ) or typed text (Fill Blank)
    val correctAnswerText: String, // Correct option (MCQ) or primary correct answer (Fill Blank)
    val isCorrect: Boolean,
    val pointsEarned: Int,
    val maxPoints: Int,
    val explanation: String?,
    val questionType: QuestionType = QuestionType.MCQ,
    val acceptedAnswers: List<String> = emptyList(),
    val isAnswerNotSet: Boolean = false,
    val banglaExplanation: String? = null
)

data class QuizResultSummary(
    val quizId: String,
    val quizTitle: String,
    val mode: QuizMode,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val score: Int,
    val maxScore: Int,
    val accuracy: Float,
    val timeTakenSeconds: Int,
    val reviewItems: List<QuestionReviewItem>
)
