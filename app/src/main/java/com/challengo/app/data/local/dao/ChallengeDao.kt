package com.challengo.app.data.local.dao

import androidx.room.*
import com.challengo.app.data.model.Challenge
import com.challengo.app.data.model.DailyChallenge
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getChallenge(id: String): Challenge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: Challenge)

    @Query("SELECT * FROM daily_challenge WHERE userId = :userId")
    fun getDailyChallenge(userId: String): Flow<DailyChallenge?>

    @Query("SELECT * FROM daily_challenge WHERE userId = :userId")
    suspend fun getDailyChallengeSync(userId: String): DailyChallenge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyChallenge(dailyChallenge: DailyChallenge)

    @Query("DELETE FROM daily_challenge WHERE userId = :userId")
    suspend fun deleteDailyChallenge(userId: String)
}