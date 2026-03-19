package com.challengo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.DailyChallenge
import com.challengo.app.data.repository.ChallengeRepository
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlobalNavViewModel(
    private val challengeRepository: ChallengeRepository,
    initialUserId: String
) : ViewModel() {
    companion object {
        private const val TAG = "GlobalNavViewModel"
    }

    enum class ChallengeCycleState {
        NO_CHALLENGE,
        ACTIVE_CAN_POST,
        DONE_WAITING
    }

    private val _dailyChallenge = MutableStateFlow<DailyChallenge?>(null)
    val dailyChallenge: StateFlow<DailyChallenge?> = _dailyChallenge.asStateFlow()

    private val _challengeCycleState = MutableStateFlow(ChallengeCycleState.NO_CHALLENGE)
    val challengeCycleState: StateFlow<ChallengeCycleState> = _challengeCycleState.asStateFlow()

    private val _hasActiveChallenge = MutableStateFlow(false)
    val hasActiveChallenge: StateFlow<Boolean> = _hasActiveChallenge.asStateFlow()

    private val _canRollChallenge = MutableStateFlow(true)
    val canRollChallenge: StateFlow<Boolean> = _canRollChallenge.asStateFlow()

    private val _remainingWaitMillis = MutableStateFlow(0L)
    val remainingWaitMillis: StateFlow<Long> = _remainingWaitMillis.asStateFlow()

    private var activeUserId: String = initialUserId
    private var observeJob: Job? = null
    private var tickerJob: Job? = null
    private var startupLogPrinted = false

    init {
        syncSession(initialUserId)
    }

    fun syncSession(userId: String?) {
        val normalizedUserId = userId.orEmpty()
        if (normalizedUserId == activeUserId && observeJob != null) {
            refreshChallengeState()
            return
        }
        activeUserId = normalizedUserId
        observeJob?.cancel()
        _dailyChallenge.value = null

        if (activeUserId.isBlank()) {
            _hasActiveChallenge.value = false
            _canRollChallenge.value = false
            _challengeCycleState.value = ChallengeCycleState.NO_CHALLENGE
            _remainingWaitMillis.value = 0L
            tickerJob?.cancel()
            return
        }
        startupLogPrinted = false
        observeChallengeState()
        startTicker()
    }

    fun refreshChallengeState() {
        val challenge = _dailyChallenge.value
        if (challenge == null) {
            _hasActiveChallenge.value = false
            _canRollChallenge.value = true
            _challengeCycleState.value = ChallengeCycleState.NO_CHALLENGE
            _remainingWaitMillis.value = 0L
            printStartupStateIfNeeded(
                activeChallengeId = null,
                activeChallengeRolledAt = null,
                challengeCompleted = false,
                computedState = _challengeCycleState.value
            )
            return
        }

        val hasValidChallengeId = challenge.challengeId.isNotBlank()
        val hasValidRollTime = challenge.rollTime > 0L
        val hasValidWindow = challenge.expiresAt > challenge.rollTime
        if (!hasValidChallengeId || !hasValidRollTime || !hasValidWindow) {
            viewModelScope.launch {
                challengeRepository.deleteDailyChallenge(activeUserId)
                _dailyChallenge.value = null
                _hasActiveChallenge.value = false
                _canRollChallenge.value = true
                _challengeCycleState.value = ChallengeCycleState.NO_CHALLENGE
                _remainingWaitMillis.value = 0L
                printStartupStateIfNeeded(
                    activeChallengeId = challenge.challengeId.ifBlank { null },
                    activeChallengeRolledAt = if (challenge.rollTime > 0L) challenge.rollTime else null,
                    challengeCompleted = challenge.pointsAwarded,
                    computedState = _challengeCycleState.value
                )
            }
            return
        }

        if (challenge.isExpired()) {
            viewModelScope.launch {
                challengeRepository.deleteDailyChallenge(activeUserId)
                _dailyChallenge.value = null
                refreshChallengeState()
            }
            return
        }

        if (challenge.pointsAwarded && challenge.getRemainingTimeMillis() <= 0L) {
            viewModelScope.launch {
                challengeRepository.deleteDailyChallenge(activeUserId)
                _dailyChallenge.value = null
                refreshChallengeState()
            }
            return
        }

        _hasActiveChallenge.value = true
        if (challenge.pointsAwarded) {
            _challengeCycleState.value = ChallengeCycleState.DONE_WAITING
            _canRollChallenge.value = false
            _remainingWaitMillis.value = challenge.getRemainingTimeMillis()
        } else {
            _challengeCycleState.value = ChallengeCycleState.ACTIVE_CAN_POST
            _canRollChallenge.value = false
            _remainingWaitMillis.value = 0L
        }
        printStartupStateIfNeeded(
            activeChallengeId = challenge.challengeId,
            activeChallengeRolledAt = challenge.rollTime,
            challengeCompleted = challenge.pointsAwarded,
            computedState = _challengeCycleState.value
        )
    }

    private fun observeChallengeState() {
        observeJob = viewModelScope.launch {
            challengeRepository.getDailyChallenge(activeUserId).collect { challenge ->
                _dailyChallenge.value = challenge
                refreshChallengeState()
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (activeUserId.isNotBlank()) {
                refreshChallengeState()
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        tickerJob?.cancel()
    }

    private fun printStartupStateIfNeeded(
        activeChallengeId: String?,
        activeChallengeRolledAt: Long?,
        challengeCompleted: Boolean,
        computedState: ChallengeCycleState
    ) {
        if (startupLogPrinted || activeUserId.isBlank()) {
            return
        }
        startupLogPrinted = true
        Log.d(
            TAG,
            "uid=$activeUserId activeChallengeId=${activeChallengeId ?: "null"} activeChallengeRolledAt=${activeChallengeRolledAt ?: "null"} challengeCompleted=$challengeCompleted computedState=$computedState"
        )
    }
}
