package com.challengo.app.data.local.dao

import androidx.room.*
import com.challengo.app.data.model.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<Post>>
    
    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY timestamp DESC")
    fun getUserPosts(userId: String): Flow<List<Post>>
    
    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPost(postId: String): Post?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<Post>)
    
    @Update
    suspend fun updatePost(post: Post)
    
    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePost(postId: String)
    
    @Query("DELETE FROM posts")
    suspend fun clearAllPosts()
}
