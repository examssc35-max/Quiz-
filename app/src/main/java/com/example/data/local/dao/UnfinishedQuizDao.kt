package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.UnfinishedQuizEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnfinishedQuizDao {

    @Query("SELECT * FROM unfinished_quizzes ORDER BY savedAt DESC LIMIT 1")
    fun getLatestUnfinishedQuiz(): Flow<UnfinishedQuizEntity?>

    @Query("SELECT * FROM unfinished_quizzes WHERE quizId = :quizId")
    suspend fun getUnfinishedQuiz(quizId: String): UnfinishedQuizEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUnfinishedQuiz(unfinished: UnfinishedQuizEntity)

    @Query("DELETE FROM unfinished_quizzes WHERE quizId = :quizId")
    suspend fun deleteUnfinishedQuiz(quizId: String)

    @Query("DELETE FROM unfinished_quizzes")
    suspend fun deleteAllUnfinishedQuizzes()
}
