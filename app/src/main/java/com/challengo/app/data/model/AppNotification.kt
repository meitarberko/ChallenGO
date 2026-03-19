package com.challengo.app.data.model

import com.google.firebase.Timestamp

data class AppNotification(
    val id: String,
    val type: String,
    val createdAt: Timestamp?,
    val read: Boolean,
    val deepLink: Map<String, String>,
    val actorUid: String? = null,
    val actorUsername: String? = null,
    val postId: String? = null,
    val commentText: String? = null,
    val messageTitle: String,
    val messageBody: String
) {
    fun relativeTimeMillis(now: Long = System.currentTimeMillis()): Long {
        val createdAtMillis = createdAt?.toDate()?.time ?: now
        return now - createdAtMillis
    }
}
