package com.challengo.app.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.DailyChallenge
import com.challengo.app.data.repository.ChallengeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChallengeViewModel(
    private val challengeRepository: ChallengeRepository,
    private val userId: String
) : ViewModel() {

    private val _dailyChallenge = MutableStateFlow<DailyChallenge?>(null)
    val dailyChallenge: StateFlow<DailyChallenge?> = _dailyChallenge.asStateFlow()

    private val _rollState = MutableLiveData<RollState>()
    val rollState: LiveData<RollState> = _rollState

    init {
        loadDailyChallenge()
    }

    private fun loadDailyChallenge() {
        viewModelScope.launch {
            challengeRepository.getDailyChallenge(userId).collect { challenge ->
                if (challenge != null && challenge.isExpired()) {
                    challengeRepository.deleteDailyChallenge(userId)
                    _dailyChallenge.value = null
                } else {
                    _dailyChallenge.value = challenge
                }
            }
        }
    }

    fun rollChallenge() {
        viewModelScope.launch {
            _rollState.value = RollState.Loading
            val result = challengeRepository.rollDailyChallenge(userId)
            _rollState.value = if (result.isSuccess) {
                RollState.Success(result.getOrNull())
            } else {
                RollState.Error(result.exceptionOrNull()?.message ?: "Failed to roll challenge")
            }
        }
    }

    fun canRollChallenge(): Boolean {
        val challenge = _dailyChallenge.value
        return challenge == null || challenge.isExpired()
    }

    fun clearExpiredChallengeIfNeeded() {
        viewModelScope.launch {
            val challenge = challengeRepository.getDailyChallengeSync(userId)
            if (challenge != null && challenge.isExpired()) {
                challengeRepository.deleteDailyChallenge(userId)
                _dailyChallenge.value = null
            }
        }
    }
}

sealed class RollState {
    object Loading : RollState()
    data class Success(val challenge: DailyChallenge?) : RollState()
    data class Error(val message: String) : RollState()
}