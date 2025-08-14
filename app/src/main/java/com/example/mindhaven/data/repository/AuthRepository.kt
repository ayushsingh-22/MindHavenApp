package com.example.mindhaven.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val auth: Auth = supabase.auth

    val authState: Flow<AuthState> = auth.sessionStatus.map { sessionStatus ->
        when (sessionStatus) {
            is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> AuthState.Authenticated
            is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated -> AuthState.Unauthenticated
            is io.github.jan.supabase.auth.status.SessionStatus.Initializing -> AuthState.Loading
            is io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure -> AuthState.Error("Session expired")
        }
    }

    // Sign up with email and password
    suspend fun signUpWithEmail(email: String, password: String): Result<UserSession?> {
        return try {
            val user = auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign in with email and password
    suspend fun signInWithEmail(email: String, password: String): Result<UserSession?> {
        return try {
            val session = auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign in with Google OAuth
    suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            auth.signInWith(Google)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign in with Google ID Token (for native Google Sign-In)
    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserSession?> {
        return try {
            val session = auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign out
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get current session
    fun getCurrentSession(): UserSession? {
        return auth.currentSessionOrNull()
    }

    // Check if user is authenticated
    fun isAuthenticated(): Boolean {
        return auth.currentSessionOrNull() != null
    }

    // Handle deeplinks for OAuth callback
    fun handleDeeplink(url: String) {
        supabase.handleDeeplinks(url)
    }

    // Reset password
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
