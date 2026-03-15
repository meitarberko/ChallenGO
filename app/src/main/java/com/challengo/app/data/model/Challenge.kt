package com.challengo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "challenges")
data class Challenge(
    @PrimaryKey
    val id: String,
    val text: String,
    val category: String,
    val difficulty: Int, // 1 = easy, 2 = medium, 3 = hard
    val hashtag: String,
    val points: Int = when (difficulty) {
        1 -> 10
        2 -> 20
        3 -> 30
        else -> 10
    }
)

@Entity(tableName = "daily_challenge")
data class DailyChallenge(
    @PrimaryKey
    val userId: String,
    val challengeId: String,
    val challengeText: String,
    val challengeCategory: String,
    val challengeDifficulty: Int,
    val challengeHashtag: String,
    val challengePoints: Int,
    val rollTime: Long, // timestamp when challenge was rolled
    val expiresAt: Long, // timestamp when challenge expires (24 hours after roll)
    val pointsAwarded: Boolean = false,
    val completedAt: Long? = null
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
    
    fun getRemainingTimeMillis(): Long {
        return maxOf(0, expiresAt - System.currentTimeMillis())
    }
}
