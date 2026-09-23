package com.example.data.model

enum class QuizMode {
    PRACTICE,
    EXAM
}

data class QuestionReviewItem(
    val questionNumber: Int,
    val questionText: String,
    val options: List<String>,
    val userAnswerIndex: Int?, // null if skipped/unanswered
    val correctAnswerIndex: Int,
    val userAnswerText: String?,
    val correctAnswerText: String,
    val isCorrect: Boolean,
    val pointsEarned: Int,
    val maxPoints: Int,
    val explanation: String?
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
