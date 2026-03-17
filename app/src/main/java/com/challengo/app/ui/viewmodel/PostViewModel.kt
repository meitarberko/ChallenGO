package com.challengo.app.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.Post
import com.challengo.app.data.repository.LikeUiState
import com.challengo.app.data.repository.LikeRepository
import com.challengo.app.data.repository.PostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PostViewModel(
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val userId: String
) : ViewModel() {
    
    private val _createState = MutableLiveData<CreateState>()
    val createState: LiveData<CreateState> = _createState
    
    private val _updateState = MutableLiveData<UpdateState>()
    val updateState: LiveData<UpdateState> = _updateState
    
    private val _deleteState = MutableLiveData<DeleteState>()
    val deleteState: LiveData<DeleteState> = _deleteState
    
    private val _likeState = MutableLiveData<LikeState>()
    val likeState: LiveData<LikeState> = _likeState

    private val _isLiked = MutableLiveData<Boolean>()
    val isLiked: LiveData<Boolean> = _isLiked

    val likeStates: StateFlow<Map<String, LikeUiState>> = likeRepository.likeStates
    
    fun createPost(
        username: String,
        userProfileImageUri: String?,
        text: String,
        imageUri: String,
        hashtag: String,
        challengeId: String?
    ) {
        _createState.value = CreateState.Error("Use Create Post screen flow")
    }
    
    fun updatePost(postId: String, text: String, postImageUri: String?) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            val result = postRepository.updatePost(postId, text, postImageUri)
            _updateState.value = if (result.isSuccess) {
                UpdateState.Success(result.getOrNull())
            } else {
                UpdateState.Error(result.exceptionOrNull()?.message ?: "Failed to update post")
            }
        }
    }
    
    fun deletePost(postId: String) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Loading
            val result = postRepository.deletePost(postId)
            _deleteState.value = if (result.isSuccess) {
                DeleteState.Success
            } else {
                DeleteState.Error(result.exceptionOrNull()?.message ?: "Failed to delete post")
            }
        }
    }
    
    fun toggleLike(postId: String, currentLikesCount: Int) {
        viewModelScope.launch {
            val result = likeRepository.toggleLike(postId, userId, currentLikesCount)
            _likeState.value = if (result.isSuccess) {
                val liked = result.getOrNull() ?: false
                _isLiked.value = liked
                LikeState.Success(liked)
            } else {
                LikeState.Error(result.exceptionOrNull()?.message ?: "Failed to toggle like")
            }
        }
    }

    fun syncLikeState(postId: String, currentLikesCount: Int? = null) {
        viewModelScope.launch {
            _isLiked.value = likeRepository.syncLikeState(postId, userId, currentLikesCount)
        }
    }
    
    suspend fun isLiked(postId: String): Boolean {
        return likeRepository.isLiked(postId, userId)
    }

    fun primeLikeState(postId: String, likesCount: Int, isLiked: Boolean? = null) {
        likeRepository.primeLikeState(postId, likesCount, isLiked)
    }

    fun observePost(postId: String): Flow<Post?> {
        return postRepository.observePost(postId)
    }
}

sealed class CreateState {
    object Loading : CreateState()
    data class Success(val post: Post?) : CreateState()
    data class Error(val message: String) : CreateState()
}

sealed class UpdateState {
    object Loading : UpdateState()
    data class Success(val post: Post?) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed class DeleteState {
    object Loading : DeleteState()
    object Success : DeleteState()
    data class Error(val message: String) : DeleteState()
}

sealed class LikeState {
    data class Success(val isLiked: Boolean) : LikeState()
    data class Error(val message: String) : LikeState()
}

