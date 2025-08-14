package com.example.mindhaven.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindhaven.data.repository.AuthRepository
import com.example.mindhaven.data.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AuthUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    val authState = authRepository.authState

    data class AuthUiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSignUpMode: Boolean = false
    )

    sealed class AuthUiEvent {
        data object NavigateToHome : AuthUiEvent()
        data object NavigateToSignUp : AuthUiEvent()
        data class ShowError(val message: String) : AuthUiEvent()
        data class ShowSuccess(val message: String) : AuthUiEvent()
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = authRepository.signInWithEmail(email, password)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.emit(AuthUiEvent.NavigateToHome)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Login failed"
                    )
                    _uiEvent.emit(AuthUiEvent.ShowError(exception.message ?: "Login failed"))
                }
            )
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = authRepository.signUpWithEmail(email, password)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.emit(AuthUiEvent.ShowSuccess("Check your email for verification link"))
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Sign up failed"
                    )
                    _uiEvent.emit(AuthUiEvent.ShowError(exception.message ?: "Sign up failed"))
                }
            )
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = authRepository.signInWithGoogle()
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    // Navigation will be handled by auth state flow
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Google sign in failed"
                    )
                    _uiEvent.emit(AuthUiEvent.ShowError(exception.message ?: "Google sign in failed"))
                }
            )
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = authRepository.signInWithGoogleIdToken(idToken)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.emit(AuthUiEvent.NavigateToHome)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Google sign in failed"
                    )
                    _uiEvent.emit(AuthUiEvent.ShowError(exception.message ?: "Google sign in failed"))
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = authRepository.resetPassword(email)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.emit(AuthUiEvent.ShowSuccess("Password reset email sent"))
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Password reset failed"
                    )
                    _uiEvent.emit(AuthUiEvent.ShowError(exception.message ?: "Password reset failed"))
                }
            )
        }
    }

    fun toggleSignUpMode() {
        _uiState.value = _uiState.value.copy(isSignUpMode = !_uiState.value.isSignUpMode)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun handleDeeplink(url: String) {
        authRepository.handleDeeplink(url)
    }
}
