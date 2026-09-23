package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.QuizEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 5")
    fun getRecentQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :id")
    suspend fun getQuizById(id: String): QuizEntity?

    @Query("SELECT * FROM quizzes WHERE id = :id")
    fun getQuizByIdFlow(id: String): Flow<QuizEntity?>

    @Query("SELECT COUNT(*) FROM quizzes")
    suspend fun getQuizCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quizzes: List<QuizEntity>)

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Query("UPDATE quizzes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE quizzes SET lastPlayedAt = :playedAt, totalAttempts = totalAttempts + 1, bestScore = CASE WHEN bestScore IS NULL OR :score > bestScore THEN :score ELSE bestScore END WHERE id = :id")
    suspend fun recordQuizCompletion(id: String, playedAt: Long, score: Int)

    @Query("DELETE FROM quizzes WHERE id = :id")
    suspend fun deleteQuizById(id: String)

    @Query("DELETE FROM quizzes")
    suspend fun deleteAllQuizzes()
}
