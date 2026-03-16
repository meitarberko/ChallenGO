package com.challengo.app.data.repository

import android.net.Uri
import android.util.Log
import com.challengo.app.data.local.dao.PostDao
import com.challengo.app.data.model.DailyChallenge
import com.challengo.app.data.model.Post
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.max

class PostRepository(
    private val firestore: FirebaseFirestore,
    private val postDao: PostDao
) {
    companion object {
        private const val TAG = "PostRepository"
        private const val FLOW_CREATE_POST = "CREATE_POST"
        const val ERROR_NO_ACTIVE_CHALLENGE = "No active challenge found. Roll one to create a post."
        const val ERROR_DESCRIPTION_REQUIRED = "Please enter post text"
        const val ERROR_IMAGE_REQUIRED = "Please select an image"
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val postsStore = MutableStateFlow<Map<String, Post>>(emptyMap())
        private var postsStoreInitialized = false
    }

    init {
        initializeStore()
    }

    suspend fun createPost(
        userId: String,
        description: String,
        localImageUri: Uri?,
        activeChallenge: DailyChallenge?
    ): Result<Post> {
        return try {
            if (description.isBlank()) {
                return Result.failure(IllegalArgumentException(ERROR_DESCRIPTION_REQUIRED))
            }
            if (localImageUri == null) {
                return Result.failure(IllegalArgumentException(ERROR_IMAGE_REQUIRED))
            }
            if (activeChallenge == null || activeChallenge.isExpired()) {
                return Result.failure(IllegalStateException(ERROR_NO_ACTIVE_CHALLENGE))
            }

            val userSnapshot = firestore.collection("users").document(userId).get().await()
            val username = userSnapshot.getString("username").orEmpty()
            if (username.isBlank()) {
                return Result.failure(IllegalStateException("Failed to load user profile"))
            }

            val userProfileImageUri = userSnapshot.getString("profileImageUri") ?: userSnapshot.getString("profileImageUrl")
            val postId = UUID.randomUUID().toString()
            val postImageUri = localImageUri.toString()
            val createdAtMillis = System.currentTimeMillis()
            val normalizedHashtag = normalizeHashtag(activeChallenge.challengeHashtag)

            val post = Post(
                id = postId,
                userId = userId.trim(),
                username = username.trim(),
                userProfileImageUri = userProfileImageUri,
                text = description.trim(),
                postImageUri = postImageUri,
                hashtag = normalizedHashtag,
                challengeId = activeChallenge.challengeId,
                challengeName = activeChallenge.challengeText,
                points = 10,
                timestamp = createdAtMillis,
                likesCount = 0,
                commentsCount = 0
            )

            val postData = hashMapOf(
                "id" to post.id,
                "userId" to post.userId,
                "username" to post.username,
                "userProfileImageUri" to post.userProfileImageUri,
                "description" to post.text,
                "text" to post.text,
                "postImageUri" to post.postImageUri,
                "hashtag" to post.hashtag,
                "challengeId" to post.challengeId,
                "challengeHashtag" to normalizedHashtag,
                "challengeName" to post.challengeName,
                "points" to post.points,
                "timestamp" to post.timestamp,
                "createdAt" to FieldValue.serverTimestamp(),
                "likeCount" to 0,
                "commentCount" to 0,
                "commentsCount" to 0
            )
            val postRef = firestore.collection("posts").document(postId)
            postRef.set(postData).await()

            postDao.insertPost(post)
            upsertPostInStore(post)
            Result.success(post)
        } catch (e: Exception) {
            Log.e(TAG, "flow=$FLOW_CREATE_POST event=create_post_fail uid=$userId", e)
            Result.failure(e)
        }
    }

    suspend fun updatePost(postId: String, text: String, postImageUri: String?): Result<Post> {
        return try {
            val existingPost = postDao.getPost(postId) ?: return Result.failure(Exception("Post not found"))

            val updatedPost = existingPost.copy(
                text = text,
                postImageUri = postImageUri ?: existingPost.postImageUri
            )

            val updates = hashMapOf<String, Any>(
                "text" to text
            )
            if (postImageUri != null) {
                updates["postImageUri"] = postImageUri
            }
            firestore.collection("posts").document(postId).update(updates).await()

            postDao.updatePost(updatedPost)
            upsertPostInStore(updatedPost)

            Result.success(updatedPost)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            val postRef = firestore.collection("posts").document(postId)

            val comments = postRef.collection("comments").get().await()
            comments.documents.forEach { it.reference.delete().await() }

            val likes = postRef.collection("likes").get().await()
            likes.documents.forEach { it.reference.delete().await() }

            postRef.delete().await()

            postDao.deletePost(postId)
            postsStore.value = postsStore.value.toMutableMap().apply { remove(postId) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllPosts(): Flow<List<Post>> {
        initializeStore()
        return observePosts()
    }

    fun getUserPosts(userId: String): Flow<List<Post>> {
        initializeStore()
        return observePosts().map { posts -> posts.filter { it.userId == userId } }
    }

    fun observePost(postId: String): Flow<Post?> {
        initializeStore()
        return postsStore.asStateFlow().map { map -> map[postId] }
    }

    fun updateLikeCountLocal(postId: String, likeCount: Int) {
        val clamped = max(0, likeCount)
        val post = postsStore.value[postId] ?: return
        val updated = post.copy(likesCount = clamped)
        upsertPostInStore(updated)
        repositoryScope.launch {
            postDao.updatePost(updated)
        }
    }

    suspend fun syncPostsFromFirestore() {
        try {
            val snapshot = firestore.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val posts = snapshot.documents.map { doc ->
                Post(
                    id = doc.getString("id") ?: doc.id,
                    userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "",
                    userProfileImageUri = doc.getString("userProfileImageUri") ?: doc.getString("userProfileImageUrl"),
                    text = doc.getString("description")
                        ?: doc.getString("text")
                        ?: "",
                    postImageUri = doc.getString("postImageUri") ?: doc.getString("imageUrl"),
                    hashtag = doc.getString("hashtag") ?: "",
                    challengeId = doc.getString("challengeId"),
                    challengeName = doc.getString("challengeName"),
                    points = doc.getLong("points")?.toInt() ?: 10,
                    timestamp = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("timestamp")
                        ?: System.currentTimeMillis(),
                    likesCount = doc.getLong("likeCount")?.toInt()
                        ?: doc.getLong("likesCount")?.toInt()
                        ?: 0,
                    commentsCount = doc.getLong("commentsCount")?.toInt()
                        ?: doc.getLong("commentCount")?.toInt()
                        ?: 0
                )
            }

            postDao.insertPosts(posts)
            postsStore.value = posts.associateBy { it.id }
        } catch (_: Exception) {
        }
    }

    private fun observePosts(): Flow<List<Post>> {
        return postsStore.asStateFlow().map { map ->
            map.values.sortedByDescending { it.timestamp }
        }
    }

    private fun initializeStore() {
        synchronized(PostRepository::class.java) {
            if (postsStoreInitialized) return
            postsStoreInitialized = true
        }
        repositoryScope.launch {
            postDao.getAllPosts().collect { posts ->
                postsStore.value = posts.associateBy { it.id }
            }
        }
    }

    private fun upsertPostInStore(post: Post) {
        postsStore.value = postsStore.value.toMutableMap().apply {
            this[post.id] = post
        }
    }

    private fun normalizeHashtag(value: String): String {
        return value.trim().removePrefix("#")
    }
}

