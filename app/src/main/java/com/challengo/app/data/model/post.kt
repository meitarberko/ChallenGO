package com.challengo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class Post(
    @PrimaryKey
    val id: String,
    val userId: String,
    val username: String,
    val userProfileImageUri: String? = null,
    val text: String,
    val postImageUri: String?,
    val hashtag: String,
    val challengeId: String? = null,
    val challengeName: String? = null,
    val points: Int = 10,
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val commentsCount: Int = 0
) {
    fun getTimeAgo(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
}

@Entity(tableName = "post_likes")
data class PostLike(
    @PrimaryKey
    val id: String, // postId_userId
    val postId: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey
    val id: String,
    val postId: String,
    val userId: String,
    val username: String,
    val userProfileImageUri: String? = null,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getTimeAgo(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
}