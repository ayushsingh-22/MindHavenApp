package com.example.mindhaven.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.data.auth.AuthOutcome
import com.example.mindhaven.data.auth.AuthRepository
import com.example.mindhaven.data.auth.AuthUser
import com.example.mindhaven.data.auth.BackendStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    /** Navigation follows the current session state. */
    val authGate: LiveData<AuthGate> = MutableLiveData(repository.authGate.value)

    /** The signed-in user ONLY while authenticated with the backend (null otherwise). Used by existing screens. */
    val authState: LiveData<AuthUser?> = MutableLiveData(repository.authUser.value)

    /** Last user-facing authentication message (null = none). */
    val error: LiveData<String?> = MutableLiveData<String?>(null)

    val loading: LiveData<Boolean> = MutableLiveData(false)

    /** Backend session health for screens that want to offer "Retry". */
    val backendStatus: LiveData<BackendStatus> = MutableLiveData(repository.backendStatus.value)




    private fun <T> LiveData<T>.set(value: T) = (this as MutableLiveData<T>).postValue(value)

    init {
        viewModelScope.launch { repository.authGate.collect { authGate.set(it) } }
        viewModelScope.launch { repository.authUser.collect { authState.set(it) } }
        viewModelScope.launch { repository.backendStatus.collect { backendStatus.set(it) } }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) { setError("Please enter your email and password."); return }
        run { repository.signInWithEmail(email, password) }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) { setError("Please enter your email and a password."); return }
        run { repository.signUpWithEmail(email, password) }
    }

    fun retryBackend() {
        viewModelScope.launch {
            val outcome = repository.retryBackend()
            if (outcome is AuthOutcome.Failed) setError(outcome.message)
            if (outcome is AuthOutcome.VerificationRequired) setError(outcome.message)
        }
    }

    fun logout() {
        repository.logout()
        error.set(null)
    }

    fun setError(message: String?) = error.set(message)

    private fun run(onResult: ((Boolean, String?) -> Unit)? = null, block: suspend () -> AuthOutcome) {
        viewModelScope.launch {
            loading.set(true)
            error.set(null)
            val outcome = try { block() } finally { loading.set(false) }
            when (outcome) {
                // Navigation is NOT done here: AppNavigation reacts to authGate (single source of truth).
                is AuthOutcome.SignedIn -> onResult?.invoke(true, null)
                is AuthOutcome.VerificationRequired -> { error.set(outcome.message); onResult?.invoke(false, outcome.message) }
                is AuthOutcome.Failed -> { error.set(outcome.message); onResult?.invoke(false, outcome.message) }
            }
        }
    }
}
