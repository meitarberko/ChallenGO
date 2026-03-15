package com.challengo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val uid: String,
    val username: String,
    val email: String,
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,
    val profileImageUri: String? = null,
    val totalPoints: Int = 0,
    val challengesCompleted: Int = 0,
    val level: Int = 1,
    val lastChallengeCompletedAt: Long? = null,
    val currentStreak: Int = 0
) {
    fun calculateLevel(): Int {
        return (totalPoints / 50) + 1
    }
}