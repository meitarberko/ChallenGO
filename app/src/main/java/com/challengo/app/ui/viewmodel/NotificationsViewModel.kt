package com.challengo.app.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.model.AppNotification
import com.challengo.app.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val userId: String
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        observeNotifications()
    }

    fun markAllRead() {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                notificationRepository.markAllRead(userId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearAll() {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                notificationRepository.clearAll(userId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private fun observeNotifications() {
        if (userId.isBlank()) {
            _notifications.value = emptyList()
            return
        }
        viewModelScope.launch {
            notificationRepository.observeNotifications(userId).collect { items ->
                _notifications.value = items
            }
        }
    }
}