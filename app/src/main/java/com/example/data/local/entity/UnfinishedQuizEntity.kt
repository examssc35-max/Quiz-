package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unfinished_quizzes")
data class UnfinishedQuizEntity(
    @PrimaryKey
    val quizId: String,
    val quizTitle: String,
    val category: String,
    val mode: String, // PRACTICE or EXAM
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val timeRemainingSeconds: Int,
    val score: Int,
    val streak: Int,
    val questionOrderJson: String, // JSON array of question IDs
    val optionOrderJson: String, // JSON object mapping questionId to ordered option strings
    val answersStateJson: String, // JSON object mapping question index/id to selected option index
    val lockedStateJson: String, // JSON object mapping question index to locked boolean
    val savedAt: Long = System.currentTimeMillis()
)
