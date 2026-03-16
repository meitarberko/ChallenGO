package com.challengo.app.data.repository

import com.challengo.app.data.model.AppNotification
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotificationRepository(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val TYPE_COMMENT = "COMMENT"
        const val TYPE_LIKE = "LIKE"
        const val TYPE_REMINDER_CHALLENGE_NOT_DONE = "REMINDER_CHALLENGE_NOT_DONE"
        const val TYPE_REMINDER_NO_ROLL_24H = "REMINDER_NO_ROLL_24H"

        const val DESTINATION_POST_DETAILS = "POST_DETAILS"
        const val DESTINATION_ROLL_CHALLENGE = "ROLL_CHALLENGE"

        const val TITLE_NEW_COMMENT = "New comment"
        const val TITLE_NEW_LIKE = "New like"
        const val TITLE_DAILY_CHALLENGE_REMINDER = "Daily challenge reminder"
        const val TITLE_DAILY_CHALLENGE = "Daily challenge"

        const val BODY_DAILY_CHALLENGE_NOT_DONE = "You still haven't completed your daily challenge."
        const val BODY_DAILY_CHALLENGE_NO_ROLL = "You haven't rolled today's challenge yet. Tap to roll now."
    }

    fun observeUnreadCount(uid: String): Flow<Int> {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) {
            return callbackFlow {
                trySend(0)
                awaitClose { }
            }
        }
        return callbackFlow {
            val registration = notificationsCollection(trimmedUid)
                .whereEqualTo("read", false)
                .addSnapshotListener { snapshot, _ ->
                    trySend(snapshot?.size() ?: 0)
                }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    fun observeNotifications(uid: String): Flow<List<AppNotification>> {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) {
            return callbackFlow {
                trySend(emptyList())
                awaitClose { }
            }
        }
        return callbackFlow {
            val registration = notificationsCollection(trimmedUid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val items = snapshot?.documents.orEmpty().map { doc ->
                        val rawDeepLink = doc.get("deepLink")
                        val deepLink = if (rawDeepLink is Map<*, *>) {
                            rawDeepLink.entries.associate { entry ->
                                entry.key.toString() to entry.value.toString()
                            }
                        } else {
                            emptyMap()
                        }
                        AppNotification(
                            id = doc.getString("id") ?: doc.id,
                            type = doc.getString("type").orEmpty(),
                            createdAt = doc.getTimestamp("createdAt"),
                            read = doc.getBoolean("read") ?: false,
                            deepLink = deepLink,
                            actorUid = doc.getString("actorUid"),
                            actorUsername = doc.getString("actorUsername"),
                            postId = doc.getString("postId"),
                            commentText = doc.getString("commentText"),
                            messageTitle = doc.getString("messageTitle").orEmpty(),
                            messageBody = doc.getString("messageBody").orEmpty()
                        )
                    }
                    trySend(items)
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun markAllRead(uid: String) {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) return
        val unread = notificationsCollection(trimmedUid)
            .whereEqualTo("read", false)
            .get()
            .await()
        if (unread.isEmpty) return
        val batch = firestore.batch()
        unread.documents.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }

    suspend fun clearAll(uid: String) {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) return
        val snapshot = notificationsCollection(trimmedUid).get().await()
        if (snapshot.isEmpty) return
        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun createCommentNotification(
        targetUid: String,
        actorUid: String,
        actorUsername: String,
        postId: String,
        commentText: String
    ) {
        if (targetUid == actorUid) return
        val preview = buildCommentPreview(commentText)
        val title = TITLE_NEW_COMMENT
        val body = "$actorUsername commented: \"$preview\""
        createNotification(
            targetUid = targetUid,
            type = TYPE_COMMENT,
            messageTitle = title,
            messageBody = body,
            deepLink = mapOf(
                "destination" to DESTINATION_POST_DETAILS,
                "postId" to postId
            ),
            actorUid = actorUid,
            actorUsername = actorUsername,
            postId = postId,
            commentText = preview
        )
    }

    suspend fun createLikeNotification(
        targetUid: String,
        actorUid: String,
        actorUsername: String,
        postId: String
    ) {
        if (targetUid == actorUid) return
        createNotification(
            targetUid = targetUid,
            type = TYPE_LIKE,
            messageTitle = TITLE_NEW_LIKE,
            messageBody = "$actorUsername liked your post!",
            deepLink = mapOf(
                "destination" to DESTINATION_POST_DETAILS,
                "postId" to postId
            ),
            actorUid = actorUid,
            actorUsername = actorUsername,
            postId = postId
        )
    }

    suspend fun createChallengeNotDoneReminderNotification(
        targetUid: String,
        challengeId: String
    ) {
        createNotification(
            targetUid = targetUid,
            type = TYPE_REMINDER_CHALLENGE_NOT_DONE,
            messageTitle = TITLE_DAILY_CHALLENGE_REMINDER,
            messageBody = BODY_DAILY_CHALLENGE_NOT_DONE,
            deepLink = mapOf("destination" to DESTINATION_ROLL_CHALLENGE),
            postId = null,
            commentText = null,
            extraFields = mapOf("challengeId" to challengeId)
        )
    }

    suspend fun createNoRollReminderNotification(
        targetUid: String,
        reminderKey: String
    ) {
        createNotification(
            targetUid = targetUid,
            type = TYPE_REMINDER_NO_ROLL_24H,
            messageTitle = TITLE_DAILY_CHALLENGE,
            messageBody = BODY_DAILY_CHALLENGE_NO_ROLL,
            deepLink = mapOf("destination" to DESTINATION_ROLL_CHALLENGE),
            extraFields = mapOf("reminderKey" to reminderKey)
        )
    }

    private suspend fun createNotification(
        targetUid: String,
        type: String,
        messageTitle: String,
        messageBody: String,
        deepLink: Map<String, String>,
        actorUid: String? = null,
        actorUsername: String? = null,
        postId: String? = null,
        commentText: String? = null,
        extraFields: Map<String, Any?> = emptyMap()
    ) {
        val trimmedUid = targetUid.trim()
        if (trimmedUid.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val data = hashMapOf<String, Any?>(
            "id" to id,
            "type" to type,
            "createdAt" to FieldValue.serverTimestamp(),
            "read" to false,
            "deepLink" to deepLink,
            "actorUid" to actorUid,
            "actorUsername" to actorUsername,
            "postId" to postId,
            "commentText" to commentText,
            "messageTitle" to messageTitle,
            "messageBody" to messageBody
        )
        data.putAll(extraFields)
        notificationsCollection(trimmedUid).document(id).set(data).await()
    }

    suspend fun getActorUsername(uid: String): String {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) return "Someone"
        return try {
            val doc = firestore.collection("users").document(trimmedUid).get().await()
            doc.getString("username")?.trim().takeUnless { it.isNullOrEmpty() } ?: "Someone"
        } catch (_: Exception) {
            "Someone"
        }
    }

    suspend fun ensureCreatedAt(uid: String, notificationId: String) {
        notificationsCollection(uid).document(notificationId).update("createdAt", Timestamp.now()).await()
    }

    private fun notificationsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("notifications")

    private fun buildCommentPreview(text: String): String {
        val normalized = text.trim().replace("\\s+".toRegex(), " ")
        return if (normalized.length <= 100) normalized else normalized.take(97).trimEnd() + "..."
    }
}
