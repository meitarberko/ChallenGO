package com.challengo.app.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.Post
import com.challengo.app.data.repository.LikeUiState
import com.challengo.app.data.repository.LikeRepository
import com.challengo.app.data.repository.NotificationRepository
import com.challengo.app.data.repository.PostRepository
import com.challengo.app.data.repository.QuoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val postRepository: PostRepository,
    private val quoteRepository: QuoteRepository,
    private val likeRepository: LikeRepository,
    private val notificationRepository: NotificationRepository,
    private val currentUserId: String
) : ViewModel() {
    private val syncedLikePosts = mutableSetOf<String>()
    
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()
    
    private val _quote = MutableStateFlow<com.challengo.app.data.model.Quote?>(null)
    val quote: StateFlow<com.challengo.app.data.model.Quote?> = _quote.asStateFlow()

    private val _notificationCount = MutableStateFlow(0)
    val notificationCount: StateFlow<Int> = _notificationCount.asStateFlow()

    private val _likedState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val likedState: StateFlow<Map<String, Boolean>> = _likedState.asStateFlow()

    private val _likeErrors = MutableSharedFlow<String>()
    val likeErrors: SharedFlow<String> = _likeErrors
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        loadPosts()
        loadQuote()
        observeUnreadNotifications()
        observeSharedLikeStates()
    }
    
    private fun loadPosts() {
        viewModelScope.launch {
            postRepository.getAllPosts().collect { postsList ->
                _posts.value = applyLikeStateToPosts(postsList, likeRepository.likeStates.value)
                if (currentUserId.isNotBlank()) {
                    postsList.forEach { post ->
                        likeRepository.primeLikeState(post.id, post.likesCount)
                        if (syncedLikePosts.add(post.id)) {
                            launch {
                                likeRepository.syncLikeState(post.id, currentUserId, post.likesCount)
                            }
                        }
                    }
                }
            }
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            postRepository.syncPostsFromFirestore()
            _isLoading.value = false
        }
    }

    private fun observeUnreadNotifications() {
        if (currentUserId.isBlank()) {
            _notificationCount.value = 0
            return
        }
        viewModelScope.launch {
            notificationRepository.observeUnreadCount(currentUserId).collect { count ->
                _notificationCount.value = count
            }
        }
    }
    
    private fun loadQuote() {
        viewModelScope.launch {
            quoteRepository.getDailyQuote().collect { quote ->
                _quote.value = quote
            }
        }
        
        viewModelScope.launch {
            val cachedQuote = quoteRepository.getDailyQuoteSync()
            if (cachedQuote == null || isQuoteExpired(cachedQuote)) {
                quoteRepository.fetchDailyQuote()
            }
        }
    }
    
    private fun isQuoteExpired(quote: com.challengo.app.data.model.Quote): Boolean {
        val twelveHours = 12 * 60 * 60 * 1000L
        return System.currentTimeMillis() - quote.timestamp > twelveHours
    }
    
    fun refreshPosts() {
        viewModelScope.launch {
            _isLoading.value = true
            postRepository.syncPostsFromFirestore()
            _isLoading.value = false
        }
    }

    fun toggleLike(post: Post) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val currentCount = _posts.value.firstOrNull { it.id == post.id }?.likesCount ?: post.likesCount
            val result = likeRepository.toggleLike(post.id, currentUserId, currentCount)
            if (result.isFailure) {
                _likeErrors.emit(result.exceptionOrNull()?.message ?: "Failed to update like")
            }
        }
    }

    private fun observeSharedLikeStates() {
        viewModelScope.launch {
            likeRepository.likeStates.collect { likeStates ->
                _likedState.value = likeStates.mapValues { it.value.isLikedByMe }
                _posts.value = applyLikeStateToPosts(_posts.value, likeStates)
            }
        }
    }

    private fun applyLikeStateToPosts(
        posts: List<Post>,
        likeStates: Map<String, LikeUiState>
    ): List<Post> {
        return posts.map { post ->
            val state = likeStates[post.id]
            if (state == null) post else post.copy(likesCount = state.likesCount)
        }
    }
}
