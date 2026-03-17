package com.challengo.app.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.Comment
import com.challengo.app.data.repository.CommentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommentViewModel(
    private val commentRepository: CommentRepository,
    private val postId: String,
    private val userId: String
) : ViewModel() {
    
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()
    
    private val _addState = MutableLiveData<AddState>()
    val addState: LiveData<AddState> = _addState
    
    init {
        loadComments()
    }
    
    private fun loadComments() {
        viewModelScope.launch {
            commentRepository.getPostComments(postId).collect { commentsList ->
                _comments.value = commentsList
            }
        }
        
        viewModelScope.launch {
            commentRepository.syncCommentsFromFirestore(postId)
        }
    }
    
    fun addComment(username: String, userProfileImageUri: String?, text: String) {
        viewModelScope.launch {
            _addState.value = AddState.Loading
            val result = commentRepository.addComment(postId, userId, username, userProfileImageUri, text)
            _addState.value = if (result.isSuccess) {
                AddState.Success(result.getOrNull())
            } else {
                AddState.Error(result.exceptionOrNull()?.message ?: "Failed to add comment")
            }
        }
    }
}

sealed class AddState {
    object Loading : AddState()
    data class Success(val comment: Comment?) : AddState()
    data class Error(val message: String) : AddState()
}

