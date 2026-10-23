package com.example.mindhaven.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.data.auth.AuthOutcome
import com.example.mindhaven.data.auth.AuthRepository
import com.example.mindhaven.data.auth.AuthUser
import com.example.mindhaven.data.auth.BackendStatus
import com.example.mindhaven.data.auth.GoogleSignInIntentSource
import com.example.mindhaven.data.auth.LinkState
import com.example.mindhaven.data.auth.ProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI-facing authentication state, written to match the CURRENT Compose call sites:
 *   authState (LiveData<AuthUser?>), error (LiveData<String?>), login(email, password), signUp(email, password),
 *   signInWithGoogle(idToken) { success, error -> }, getGoogleSignInIntent(context), logout(), setError(msg).
 *
 * No Activity/Context is stored: [getGoogleSignInIntent] ignores its argument (kept only for source compatibility) and
 * the Google client lives in [GoogleSignInProvider], which holds the application context.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val google: GoogleSignInIntentSource
) : ViewModel() {

    /**
     * Single source of truth for navigation (Firebase + backend). Seeded synchronously from the repository, so the first
     * frame after a restart already knows whether a session is being verified.
     */
    val authGate: LiveData<AuthGate> = MutableLiveData(repository.authGate.value)

    /** The signed-in user ONLY while authenticated with the backend (null otherwise). Used by existing screens. */
    val authState: LiveData<AuthUser?> = MutableLiveData(repository.authUser.value)

    /** Last user-facing authentication message (null = none). */
    val error: LiveData<String?> = MutableLiveData<String?>(null)

    val loading: LiveData<Boolean> = MutableLiveData(false)

    /** Backend session health for screens that want to offer "Retry". */
    val backendStatus: LiveData<BackendStatus> = MutableLiveData(repository.backendStatus.value)

    val profile: LiveData<ProfileState> = MutableLiveData(repository.profile.value)

    /** Account-linking progress (restricted LinkRequired state). Never contains a password, challenge or token. */
    val linkState: LiveData<LinkState> = MutableLiveData(repository.linkState.value)

    /** The one running linking operation (start+complete, or a re-authentication). Extra taps are ignored. */
    private var linkJob: Job? = null

    private fun <T> LiveData<T>.set(value: T) = (this as MutableLiveData<T>).postValue(value)

    init {
        viewModelScope.launch { repository.authGate.collect { authGate.set(it) } }
        viewModelScope.launch { repository.authUser.collect { authState.set(it) } }
        viewModelScope.launch { repository.backendStatus.collect { backendStatus.set(it) } }
        viewModelScope.launch { repository.profile.collect { profile.set(it) } }
        viewModelScope.launch { repository.linkState.collect { linkState.set(it) } }
        viewModelScope.launch { repository.restoreNotice.collect { n -> if (n != null) error.set(n) } }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) { setError("Please enter your email and password."); return }
        run { repository.signInWithEmail(email, password) }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) { setError("Please enter your email and a password."); return }
        run { repository.signUpWithEmail(email, password) }
    }

    /** Google ID token from the Google Sign-In screen (NOT the Firebase ID token). */
    fun signInWithGoogle(googleIdToken: String, onResult: (Boolean, String?) -> Unit) {
        run(onResult) { repository.signInWithGoogle(googleIdToken) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getGoogleSignInIntent(context: Context): Intent = google.signInIntent()

    /** "Retry" after the backend was unreachable (Firebase stays signed in). */
    fun retryBackend() {
        viewModelScope.launch {
            val outcome = repository.retryBackend()
            if (outcome is AuthOutcome.Failed) setError(outcome.message)
            if (outcome is AuthOutcome.Blocked) setError(outcome.message)
            if (outcome is AuthOutcome.VerificationRequired) setError(outcome.message)
        }
    }

    fun logout() {
        linkJob?.cancel()
        repository.logout()
        error.set(null)
    }

    // ------------------------------------------------------------------ account linking (Phase 2B.5B.3)

    /**
     * Link with the MantraAI web password and the user's explicit consent. The password is handed straight to the
     * repository (one request) and never kept here. Navigation on success is driven by authGate, not by this call.
     */
    fun linkAccount(webPassword: String, consent: Boolean) = linkOperation { repository.linkAccount(webPassword, consent) }

    /** Google ID token from a fresh Google Sign-In (re-authentication for a recent sign-in). */
    fun reauthenticateWithGoogle(googleIdToken: String) = linkOperation { repository.reauthenticateWithGoogle(googleIdToken) }

    /** The MindHaven (Firebase) password for re-authentication. Never stored. */
    fun reauthenticateWithPassword(firebasePassword: String) = linkOperation { repository.reauthenticateWithPassword(firebasePassword) }

    /** Cancel / "Use another account": signs out of Firebase + Google and returns to the sign-in screens. */
    fun cancelLinking() {
        linkJob?.cancel()
        linkJob = null
        repository.cancelLinking()
        error.set(null)
    }

    private fun linkOperation(block: suspend () -> Unit) {
        if (linkJob?.isActive == true) return // no overlapping start/complete/re-authentication
        linkJob = viewModelScope.launch { block() }
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
                // Firebase kept, backend unreachable: the connection screen explains it (no error toast).
                is AuthOutcome.BackendUnavailable -> onResult?.invoke(false, null)
                is AuthOutcome.VerificationRequired -> { error.set(outcome.message); onResult?.invoke(false, outcome.message) }
                is AuthOutcome.Blocked -> { error.set(outcome.message); onResult?.invoke(false, outcome.message) }
                // Firebase kept; AppNavigation shows the restricted linking screen (no error toast).
                is AuthOutcome.LinkRequired -> onResult?.invoke(false, null)
                is AuthOutcome.Failed -> { error.set(outcome.message); onResult?.invoke(false, outcome.message) }
            }
        }
    }
}
