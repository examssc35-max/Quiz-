package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.QuizAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizAttemptDao {

    @Query("SELECT * FROM quiz_attempts ORDER BY timestamp DESC")
    fun getAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts ORDER BY timestamp DESC LIMIT 5")
    fun getRecentAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE quizId = :quizId ORDER BY timestamp DESC")
    fun getAttemptsForQuiz(quizId: String): Flow<List<QuizAttemptEntity>>

    @Query("SELECT COUNT(*) FROM quiz_attempts")
    fun getTotalAttemptsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Query("DELETE FROM quiz_attempts")
    suspend fun deleteAllAttempts()
}
