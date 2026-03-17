package com.challengo.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.Post
import com.challengo.app.data.model.User
import com.challengo.app.data.repository.LikeRepository
import com.challengo.app.data.repository.PostRepository
import com.challengo.app.data.repository.UserProfileStats
import com.challengo.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val userId: String,
    private val currentViewerId: String
) : ViewModel() {
    private val syncedLikePosts = mutableSetOf<String>()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _userPosts = MutableStateFlow<List<Post>>(emptyList())
    val userPosts: StateFlow<List<Post>> = _userPosts.asStateFlow()

    private val _updateState = MutableLiveData<ProfileUpdateState>()
    val updateState: LiveData<ProfileUpdateState> = _updateState

    private val _profileStats = MutableStateFlow(UserProfileStats(points = 0, hashtags = emptyList()))
    val profileStats: StateFlow<UserProfileStats> = _profileStats.asStateFlow()

    init {
        loadUser()
        loadUserPosts()
        loadProfileStats()
        refreshUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            userRepository.getUser(userId).collect { user ->
                _user.value = user
            }
        }
    }

    private fun loadUserPosts() {
        viewModelScope.launch {
            postRepository.getUserPosts(userId).collect { posts ->
                _userPosts.value = posts
                if (currentViewerId.isNotBlank()) {
                    posts.forEach { post ->
                        likeRepository.primeLikeState(post.id, post.likesCount)
                        if (syncedLikePosts.add(post.id)) {
                            launch {
                                likeRepository.syncLikeState(post.id, currentViewerId, post.likesCount)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadProfileStats() {
        viewModelScope.launch {
            val result = userRepository.getUserProfileStats(userId)
            if (result.isSuccess) {
                _profileStats.value = result.getOrNull() ?: UserProfileStats(0, emptyList())
            }
        }
    }

    private fun refreshUser() {
        viewModelScope.launch {
            userRepository.syncUserFromFirestore(userId)
        }
    }

    fun updateProfile(username: String?, selectedImageUri: Uri?, clearProfileImage: Boolean) {
        viewModelScope.launch {
            if (username == null && selectedImageUri == null && !clearProfileImage) {
                _updateState.value = ProfileUpdateState.Success(_user.value)
                return@launch
            }
            _updateState.value = ProfileUpdateState.Loading
            val result = userRepository.updateUserProfile(
                uid = userId,
                username = username,
                profileImageUri = selectedImageUri?.toString(),
                clearProfileImage = clearProfileImage
            )
            _updateState.value = if (result.isSuccess) {
                refreshUser()
                loadProfileStats()
                ProfileUpdateState.Success(result.getOrNull())
            } else {
                ProfileUpdateState.Error(result.exceptionOrNull()?.message ?: "Failed to update profile")
            }
        }
    }
}

sealed class ProfileUpdateState {
    object Loading : ProfileUpdateState()
    data class Success(val user: User?) : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}