package com.example.ui.navigation

import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary

sealed class Screen {
    object Welcome : Screen()
    object Home : Screen()
    object Quizzes : Screen()
    data class QuizDetail(val quizId: String) : Screen()
    data class QuizPlay(
        val quizId: String,
        val mode: QuizMode,
        val resumeUnfinished: Boolean = false
    ) : Screen()
    data class Result(val summary: QuizResultSummary) : Screen()
    data class Review(val summary: QuizResultSummary) : Screen()
    object ImportQuiz : Screen()
    data class QuizEditor(val existingQuizId: String? = null) : Screen()
    object Statistics : Screen()
    object Settings : Screen()
}
