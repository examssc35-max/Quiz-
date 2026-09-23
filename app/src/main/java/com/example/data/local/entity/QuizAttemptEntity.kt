package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey
    val id: String,
    val quizId: String,
    val quizTitle: String,
    val mode: String, // PRACTICE or EXAM
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val score: Int,
    val maxScore: Int,
    val accuracy: Float,
    val timeTakenSeconds: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val reviewDataJson: String = "[]"
)
