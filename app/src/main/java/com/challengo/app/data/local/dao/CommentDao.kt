package com.challengo.app.data.local.dao

import androidx.room.*
import com.challengo.app.data.model.Comment
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getPostComments(postId: String): Flow<List<Comment>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<Comment>)
    
    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)
    
    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun deletePostComments(postId: String)
}
