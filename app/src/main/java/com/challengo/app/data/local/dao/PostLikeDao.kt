package com.challengo.app.data.local.dao

import androidx.room.*
import com.challengo.app.data.model.PostLike
import kotlinx.coroutines.flow.Flow

@Dao
interface PostLikeDao {
    @Query("SELECT * FROM post_likes WHERE postId = :postId AND userId = :userId")
    suspend fun getUserLike(postId: String, userId: String): PostLike?

    @Query("SELECT * FROM post_likes WHERE postId = :postId")
    fun getPostLikes(postId: String): Flow<List<PostLike>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: PostLike)

    @Query("DELETE FROM post_likes WHERE postId = :postId AND userId = :userId")
    suspend fun deleteLike(postId: String, userId: String)
}