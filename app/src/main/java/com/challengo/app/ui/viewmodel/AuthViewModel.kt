package com.challengo.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challengo.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState.asStateFlow()

    private val _selectedProfileImageUri = MutableStateFlow<Uri?>(null)
    val selectedProfileImageUri: StateFlow<Uri?> = _selectedProfileImageUri.asStateFlow()

    private val _registerUiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    fun login(email: String, password: String) {
        runAuthAction(_loginState) { authRepository.login(email, password) }
    }

    fun onProfileImageSelected(uri: Uri?) {
        _selectedProfileImageUri.value = uri
    }

    fun register(
        email: String,
        password: String,
        username: String,
        firstName: String,
        lastName: String,
        age: Int,
        selectedImageUri: Uri? = _selectedProfileImageUri.value
    ) {
        if (_registerUiState.value is RegisterUiState.Loading) {
            return
        }
        viewModelScope.launch {
            _registerUiState.value = RegisterUiState.Loading
            val result = authRepository.register(
                email = email,
                password = password,
                username = username,
                firstName = firstName,
                lastName = lastName,
                age = age,
                selectedImageUri = selectedImageUri
            )
            _registerUiState.value = if (result.isSuccess) {
                RegisterUiState.Success(result.getOrNull())
            } else {
                RegisterUiState.Error(result.exceptionOrNull()?.message ?: "Auth failed")
            }
        }
    }

    fun consumeLoginState() {
        if (_loginState.value !is AuthState.Loading) {
            _loginState.value = AuthState.Idle
        }
    }

    fun consumeRegisterState() {
        if (_registerUiState.value !is RegisterUiState.Loading) {
            _registerUiState.value = RegisterUiState.Idle
        }
    }

    fun isLoggedIn(): Boolean {
        return authRepository.isLoggedIn()
    }

    fun getCurrentUserId(): String? {
        return authRepository.currentUser?.uid
    }

    private fun runAuthAction(
        stateFlow: MutableStateFlow<AuthState>,
        block: suspend () -> Result<com.google.firebase.auth.FirebaseUser>
    ) {
        if (stateFlow.value is AuthState.Loading) {
            return
        }
        viewModelScope.launch {
            stateFlow.value = AuthState.Loading
            val result = block()
            stateFlow.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull())
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Auth failed")
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: com.google.firebase.auth.FirebaseUser?) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Loading : RegisterUiState()
    data class Success(val user: com.google.firebase.auth.FirebaseUser?) : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}