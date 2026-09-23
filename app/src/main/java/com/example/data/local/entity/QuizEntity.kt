package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val questionCount: Int,
    val timeLimit: Int,
    val shuffleQuestions: Boolean,
    val shuffleOptions: Boolean,
    val jsonContent: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val totalAttempts: Int = 0,
    val bestScore: Int? = null,
    val maxPossibleScore: Int = 0
)
