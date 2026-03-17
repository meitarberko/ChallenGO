package com.challengo.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.DailyChallenge
import com.challengo.app.data.repository.ChallengeRepository
import com.challengo.app.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CreatePostStatus {
    data object Idle : CreatePostStatus()
    data object Loading : CreatePostStatus()
    data class Success(val challengeId: String?) : CreatePostStatus()
    data class Error(val message: String) : CreatePostStatus()
}

data class CreatePostUiState(
    val challengeName: String = "",
    val hashtag: String = "",
    val hasActiveChallenge: Boolean = false,
    val isImageSelected: Boolean = false,
    val isLoading: Boolean = false,
    val isCreateEnabled: Boolean = false,
    val status: CreatePostStatus = CreatePostStatus.Idle
)

class CreatePostViewModel(
    private val challengeRepository: ChallengeRepository,
    private val postRepository: PostRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private var currentChallenge: DailyChallenge? = null
    private var selectedImageUri: Uri? = null
    private var descriptionText: String = ""

    init {
        observeDailyChallenge()
    }

    fun onDescriptionChanged(text: String) {
        descriptionText = text
    }

    fun onImageSelected(uri: Uri?) {
        selectedImageUri = uri
        updateState(status = CreatePostStatus.Idle)
    }

    fun submitPost() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            if (currentChallenge == null || currentChallenge?.isExpired() == true) {
                updateState(status = CreatePostStatus.Error(PostRepository.ERROR_NO_ACTIVE_CHALLENGE))
                return@launch
            }
            if (descriptionText.isBlank()) {
                updateState(status = CreatePostStatus.Error(PostRepository.ERROR_DESCRIPTION_REQUIRED))
                return@launch
            }
            if (selectedImageUri == null) {
                updateState(status = CreatePostStatus.Error(PostRepository.ERROR_IMAGE_REQUIRED))
                return@launch
            }

            updateState(isLoading = true, status = CreatePostStatus.Loading)
            val result = postRepository.createPost(
                userId = userId,
                description = descriptionText,
                localImageUri = selectedImageUri,
                activeChallenge = currentChallenge
            )
            if (result.isSuccess) {
                val completion = challengeRepository.completeChallengeAndAward(userId)
                if (completion.isSuccess) {
                    updateState(
                        isLoading = false,
                        status = CreatePostStatus.Success(currentChallenge?.challengeId)
                    )
                } else {
                    updateState(
                        isLoading = false,
                        status = CreatePostStatus.Success(currentChallenge?.challengeId)
                    )
                }
            } else {
                updateState(
                    isLoading = false,
                    status = CreatePostStatus.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create post"
                    )
                )
            }
        }
    }

    fun consumeStatus() {
        updateState(status = CreatePostStatus.Idle)
    }

    private fun observeDailyChallenge() {
        viewModelScope.launch {
            challengeRepository.getDailyChallenge(userId).collect { challenge ->
                if (challenge == null || challenge.isExpired() || challenge.pointsAwarded) {
                    if (challenge?.isExpired() == true) {
                        challengeRepository.deleteDailyChallenge(userId)
                    }
                    currentChallenge = null
                    updateState(
                        challengeName = "",
                        hashtag = "",
                        hasActiveChallenge = false
                    )
                } else {
                    currentChallenge = challenge
                    updateState(
                        challengeName = challenge.challengeText,
                        hashtag = normalizeHashtag(challenge.challengeHashtag),
                        hasActiveChallenge = true
                    )
                }
            }
        }
    }

    private fun updateState(
        challengeName: String = _uiState.value.challengeName,
        hashtag: String = _uiState.value.hashtag,
        hasActiveChallenge: Boolean = _uiState.value.hasActiveChallenge,
        isLoading: Boolean = _uiState.value.isLoading,
        status: CreatePostStatus = _uiState.value.status
    ) {
        val isCreateEnabled = hasActiveChallenge && selectedImageUri != null && !isLoading
        _uiState.value = CreatePostUiState(
            challengeName = challengeName,
            hashtag = hashtag,
            hasActiveChallenge = hasActiveChallenge,
            isImageSelected = selectedImageUri != null,
            isLoading = isLoading,
            isCreateEnabled = isCreateEnabled,
            status = status
        )
    }

    private fun normalizeHashtag(hashtag: String): String {
        return "#${hashtag.removePrefix("#").trim()}"
    }
}