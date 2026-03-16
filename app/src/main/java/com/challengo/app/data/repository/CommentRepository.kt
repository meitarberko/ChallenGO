package com.challengo.app.data.repository

import com.challengo.app.data.local.dao.CommentDao
import com.challengo.app.data.model.Comment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CommentRepository(
    private val firestore: FirebaseFirestore,
    private val commentDao: CommentDao,
    private val notificationRepository: NotificationRepository
) {
    suspend fun addComment(
        postId: String,
        userId: String,
        username: String,
        userProfileImageUri: String?,
        text: String
    ): Result<Comment> {
        return try {
            val resolvedUsername = resolveUsername(userId, username, mutableMapOf())
            val commentId = UUID.randomUUID().toString()
            val comment = Comment(
                id = commentId,
                postId = postId,
                userId = userId,
                username = resolvedUsername,
                userProfileImageUri = userProfileImageUri,
                text = text,
                timestamp = System.currentTimeMillis()
            )

            val commentData = hashMapOf(
                "id" to comment.id,
                "postId" to comment.postId,
                "userId" to comment.userId,
                "username" to comment.username,
                "userProfileImageUri" to comment.userProfileImageUri,
                "text" to comment.text,
                "timestamp" to comment.timestamp
            )

            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId).set(commentData).await()

            firestore.collection("posts").document(postId)
                .update("commentsCount", com.google.firebase.firestore.FieldValue.increment(1)).await()

            val postOwnerUid = firestore.collection("posts")
                .document(postId)
                .get()
                .await()
                .getString("userId")
                .orEmpty()
            if (postOwnerUid.isNotBlank() && postOwnerUid != userId) {
                notificationRepository.createCommentNotification(
                    targetUid = postOwnerUid,
                    actorUid = userId,
                    actorUsername = resolvedUsername,
                    postId = postId,
                    commentText = comment.text
                )
            }

            commentDao.insertComment(comment)

            Result.success(comment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPostComments(postId: String): Flow<List<Comment>> {
        return commentDao.getPostComments(postId)
    }

    suspend fun syncCommentsFromFirestore(postId: String) {
        try {
            val snapshot = firestore.collection("posts").document(postId)
                .collection("comments")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .get()
                .await()
            val usernameCache = mutableMapOf<String, String>()
            val comments = snapshot.documents.map { doc ->
                val userId = doc.getString("userId") ?: ""
                Comment(
                    id = doc.getString("id") ?: doc.id,
                    postId = doc.getString("postId") ?: postId,
                    userId = userId,
                    username = resolveUsername(
                        userId = userId,
                        username = doc.getString("username"),
                        cache = usernameCache
                    ),
                    userProfileImageUri = doc.getString("userProfileImageUri"),
                    text = doc.getString("text") ?: "",
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }

            commentDao.insertComments(comments)
        } catch (e: Exception) {
        }
    }

    private suspend fun resolveUsername(
        userId: String,
        username: String?,
        cache: MutableMap<String, String>
    ): String {
        val candidate = username?.trim()
        if (!candidate.isNullOrEmpty()) {
            return candidate
        }
        if (userId.isBlank()) {
            return "Unknown user"
        }
        cache[userId]?.let { return it }
        return try {
            val resolved = firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .getString("username")
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "Unknown user"
            cache[userId] = resolved
            resolved
        } catch (_: Exception) {
            "Unknown user"
        }
    }
}
