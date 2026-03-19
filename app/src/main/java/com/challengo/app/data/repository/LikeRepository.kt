package com.challengo.app.data.repository

import android.util.Log
import com.challengo.app.data.local.dao.PostLikeDao
import com.challengo.app.data.model.PostLike
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class LikeRepository(
    private val firestore: FirebaseFirestore,
    private val postLikeDao: PostLikeDao,
    private val notificationRepository: NotificationRepository,
    private val postRepository: PostRepository
) {
    companion object {
        private const val TAG = "LikeRepository"
        private val likeStateStore = MutableStateFlow<Map<String, LikeUiState>>(emptyMap())
    }

    val likeStates: StateFlow<Map<String, LikeUiState>> = likeStateStore.asStateFlow()

    suspend fun syncLikeState(postId: String, userId: String, currentLikesCount: Int? = null): Boolean {
        return try {
            val likeId = userId
            val legacyLikeId = "${postId}_$userId"
            Log.d(
                TAG,
                "event=sync_like_state_start authUid=${currentAuthUid() ?: "null"} postPath=posts/$postId likePath=posts/$postId/likes/$likeId legacyLikePath=posts/$postId/likes/$legacyLikeId requestedUid=$userId"
            )
            val likeDoc = firestore.collection("posts").document(postId)
                .collection("likes")
                .document(likeId)
                .get()
                .await()
            val legacyLikeDoc = firestore.collection("posts").document(postId)
                .collection("likes")
                .document(legacyLikeId)
                .get()
                .await()

            val isLiked = likeDoc.exists() || legacyLikeDoc.exists()
            if (isLiked) {
                postLikeDao.insertLike(
                    PostLike(
                        id = likeId,
                        postId = postId,
                        userId = userId,
                        timestamp = likeDoc.getLong("timestamp")
                            ?: legacyLikeDoc.getLong("timestamp")
                            ?: System.currentTimeMillis()
                    )
                )
            } else {
                postLikeDao.deleteLike(postId, userId)
            }
            val resolvedCount = resolveLikeCount(postId, currentLikesCount)
            upsertLikeState(postId, isLiked, resolvedCount)
            Log.d(
                TAG,
                "event=sync_like_state postId=$postId uid=$userId isLiked=$isLiked likeCount=${likeStateStore.value[postId]?.likesCount}"
            )
            isLiked
        } catch (e: Exception) {
            val cachedLiked = postLikeDao.getUserLike(postId, userId) != null
            val fallbackCount = if (cachedLiked) max(1, currentLikesCount ?: likeStateStore.value[postId]?.likesCount ?: 0) else currentLikesCount
            upsertLikeState(postId, cachedLiked, fallbackCount)
            Log.e(
                TAG,
                "event=sync_like_state_fail authUid=${currentAuthUid() ?: "null"} postPath=posts/$postId requestedUid=$userId message=${e.message}",
                e
            )
            cachedLiked
        }
    }

    fun primeLikeState(postId: String, likesCount: Int, isLiked: Boolean? = null) {
        val existing = likeStateStore.value[postId]
        val resolvedLiked = isLiked ?: existing?.isLikedByMe ?: false
        upsertLikeState(postId, resolvedLiked, likesCount)
    }

    suspend fun toggleLike(postId: String, userId: String, currentLikesCount: Int): Result<Boolean> {
        return try {
            val postRef = firestore.collection("posts").document(postId)
            val likeId = userId
            val legacyLikeId = "${postId}_$userId"
            val likeRef = postRef.collection("likes").document(likeId)
            val legacyLikeRef = postRef.collection("likes").document(legacyLikeId)
            val now = System.currentTimeMillis()
            val beforeState = likeStateStore.value[postId]
            Log.d(
                TAG,
                "event=toggle_like_start authUid=${currentAuthUid() ?: "null"} postPath=${postRef.path} likePath=${postRef.path}/likes/$likeId legacyLikePath=${postRef.path}/likes/$legacyLikeId requestedUid=$userId"
            )
            val previousLiked = beforeState?.isLikedByMe ?: syncLikeState(postId, userId, currentLikesCount)
            val optimisticLiked = !previousLiked
            val optimisticCount = max(0, currentLikesCount + if (optimisticLiked) 1 else -1)
            upsertLikeState(postId, optimisticLiked, optimisticCount)
            postRepository.updateLikeCountLocal(postId, optimisticCount)
            Log.d(
                TAG,
                "event=toggle_like_optimistic postId=$postId uid=$userId wasLiked=$previousLiked nowLiked=$optimisticLiked likeCount=$optimisticCount"
            )

            val result = firestore.runTransaction { transaction ->
                val isLiked = transaction.get(likeRef).exists() || transaction.get(legacyLikeRef).exists()
                val postSnapshot = transaction.get(postRef)
                val currentLikes = postSnapshot.getLong("likeCount")
                    ?: postSnapshot.getLong("likesCount")
                    ?: 0

                if (isLiked) {
                    transaction.delete(likeRef)
                    transaction.delete(legacyLikeRef)
                    val updatedCount = max(0, currentLikes - 1)
                    transaction.update(postRef, "likeCount", updatedCount)
                    false to updatedCount.toInt()
                } else {
                    transaction.set(
                        likeRef,
                        hashMapOf(
                            "id" to likeId,
                            "postId" to postId,
                            "userId" to userId,
                            "timestamp" to now
                        )
                    )
                    val updatedCount = currentLikes + 1
                    transaction.update(postRef, "likeCount", updatedCount)
                    true to updatedCount.toInt()
                }
            }.await()
            val isLikedNow = result.first
            val serverCount = result.second
            upsertLikeState(postId, isLikedNow, serverCount)
            postRepository.updateLikeCountLocal(postId, serverCount)
            Log.d(
                TAG,
                "event=toggle_like_server postId=$postId uid=$userId isLiked=$isLikedNow likeCount=$serverCount"
            )

            if (isLikedNow) {
                postLikeDao.insertLike(
                    PostLike(
                        id = likeId,
                        postId = postId,
                        userId = userId,
                        timestamp = now
                    )
                )
                val postOwnerUid = firestore.collection("posts")
                    .document(postId)
                    .get()
                    .await()
                    .getString("userId")
                    .orEmpty()
                if (postOwnerUid.isNotBlank() && postOwnerUid != userId) {
                    val actorUsername = notificationRepository.getActorUsername(userId)
                    runCatching {
                        notificationRepository.createLikeNotification(
                            targetUid = postOwnerUid,
                            actorUid = userId,
                            actorUsername = actorUsername,
                            postId = postId
                        )
                    }.onFailure { notificationError ->
                        Log.w(
                            TAG,
                            "event=toggle_like_notification_fail authUid=${currentAuthUid() ?: "null"} notificationPath=users/$postOwnerUid/notifications targetUid=$postOwnerUid actorUid=$userId postId=$postId message=${notificationError.message}",
                            notificationError
                        )
                    }
                }
            } else {
                postLikeDao.deleteLike(postId, userId)
            }

            Result.success(isLikedNow)
        } catch (e: Exception) {
            val fallbackLiked = likeStateStore.value[postId]?.isLikedByMe
            if (fallbackLiked != null) {
                upsertLikeState(postId, !fallbackLiked, currentLikesCount)
                postRepository.updateLikeCountLocal(postId, currentLikesCount)
            }
            Log.e(TAG, "event=toggle_like_fail postId=$postId uid=$userId", e)
            Result.failure(e)
        }
    }

    suspend fun isLiked(postId: String, userId: String): Boolean {
        return syncLikeState(postId, userId)
    }

    fun getPostLikes(postId: String): Flow<List<PostLike>> {
        return postLikeDao.getPostLikes(postId)
    }

    private fun upsertLikeState(postId: String, isLiked: Boolean, likesCount: Int?) {
        likeStateStore.value = likeStateStore.value.toMutableMap().apply {
            val existing = this[postId]
            val resolvedCount = likesCount ?: existing?.likesCount ?: 0
            val fixedCount = when {
                isLiked && resolvedCount <= 0 -> 1
                else -> max(0, resolvedCount)
            }
            if (isLiked && fixedCount == 1 && resolvedCount <= 0) {
                Log.w(TAG, "event=like_invariant_fix postId=$postId resolvedCount=$resolvedCount")
            }
            this[postId] = LikeUiState(
                isLikedByMe = isLiked,
                likesCount = fixedCount
            )
        }
    }

    private suspend fun resolveLikeCount(postId: String, currentLikesCount: Int?): Int {
        if (currentLikesCount != null) {
            return max(0, currentLikesCount)
        }
        val cached = likeStateStore.value[postId]?.likesCount
        if (cached != null) {
            return max(0, cached)
        }
        return try {
            val snap = firestore.collection("posts").document(postId).get().await()
            max(
                0,
                (snap.getLong("likeCount")
                    ?: snap.getLong("likesCount")
                    ?: 0L).toInt()
            )
        } catch (_: Exception) {
            0
        }
    }

    private fun currentAuthUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
}

data class LikeUiState(
    val isLikedByMe: Boolean,
    val likesCount: Int
)
