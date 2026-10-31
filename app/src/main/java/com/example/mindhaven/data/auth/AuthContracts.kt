package com.example.mindhaven.data.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val displayName: String?, val email: String?)
enum class TemporaryReason { Offline, Timeout, RateLimited, ServiceUnavailable, NotConfigured }
sealed interface AuthGate {
    data object SignedOut : AuthGate
    data class Verifying(val user: AuthUser) : AuthGate
    data class Authenticated(val user: AuthUser, val userId: String) : AuthGate
    data class BackendUnavailable(val user: AuthUser, val reason: TemporaryReason, val retryAfterSeconds: Long?) : AuthGate
}
sealed interface BackendStatus {
    data object Ok : BackendStatus
    data object Connecting : BackendStatus
    data class Unavailable(val reason: TemporaryReason, val retryAfterSeconds: Long?) : BackendStatus
}
sealed interface AuthOutcome {
    data class SignedIn(val user: AuthUser) : AuthOutcome
    data class VerificationRequired(val emailSent: Boolean, val message: String) : AuthOutcome
    data class Failed(val message: String) : AuthOutcome
}
interface AuthRepository {
    val authGate: StateFlow<AuthGate>
    val authUser: StateFlow<AuthUser?>
    val backendStatus: StateFlow<BackendStatus>
    suspend fun signInWithEmail(email: String, password: String): AuthOutcome
    suspend fun signUpWithEmail(email: String, password: String): AuthOutcome
    suspend fun retryBackend(): AuthOutcome?
    fun logout()
}
fun unavailableMessage(reason: TemporaryReason): String = when (reason) {
    TemporaryReason.Offline -> "You appear to be offline. Check your connection and retry."
    TemporaryReason.Timeout -> "Your account service is taking too long to respond. Please retry."
    TemporaryReason.RateLimited -> "Too many requests. Please wait a moment and retry."
    TemporaryReason.ServiceUnavailable -> "Your account service is temporarily unavailable. Please retry."
    TemporaryReason.NotConfigured -> "Account sync is not set up in this build."
}
