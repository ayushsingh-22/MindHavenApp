package com.example.mindhaven.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** What the app needs to know about the signed-in Firebase user. Contains no token. */
data class FirebaseSession(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    /** Firebase provider ids, e.g. "google.com", "password". */
    val providerIds: List<String>
) {
    val usesGoogle: Boolean get() = "google.com" in providerIds
    val usesPassword: Boolean get() = "password" in providerIds

    /**
     * Whether this account may be exchanged with the backend. Google accounts come with a verified e-mail;
     * e-mail/password accounts must have verified their address first (the backend refuses otherwise).
     */
    val isEligibleForBackend: Boolean get() = usesGoogle || isEmailVerified
}

enum class AuthFailureKind {
    InvalidCredentials, UserNotFound, WeakPassword, EmailAlreadyInUse, UserDisabled, TooManyRequests, Network, Unknown,
    /** Re-authentication was attempted with a DIFFERENT account than the signed-in one. */
    UserMismatch
}

/**
 * A Firebase ID token plus the moment the user last REALLY signed in ([authTimeSeconds], the token's `auth_time` claim).
 * Refreshing a token does not move auth_time; only a sign-in or re-authentication does. toString() is redacted.
 */
class FirebaseIdToken(val token: String, val authTimeSeconds: Long?) {
    override fun toString(): String = "FirebaseIdToken(token=<redacted>, authTimeSeconds=$authTimeSeconds)"
}

/** Firebase failure with a classification only; the Firebase message is never carried (it can quote user input). */
class AuthFailure(val kind: AuthFailureKind) : Exception(kind.name)

/** Testable boundary around the Firebase Auth SDK. */
interface FirebaseAuthGateway {
    /** Snapshot of the persisted/current Firebase user (restored by the SDK after a process restart). */
    val currentUser: FirebaseSession?

    /** Emits the current user (or null) now and on every sign-in/sign-out. */
    fun authStates(): Flow<FirebaseSession?>

    @Throws(AuthFailure::class)
    suspend fun signInWithEmail(email: String, password: String): FirebaseSession

    /** Creates the account (which also signs it in). */
    @Throws(AuthFailure::class)
    suspend fun createWithEmail(email: String, password: String): FirebaseSession

    @Throws(AuthFailure::class)
    suspend fun signInWithGoogleIdToken(googleIdToken: String): FirebaseSession

    /**
     * The FIREBASE ID token (not the Google sign-in token) for the backend exchange: currentUser.getIdToken(forceRefresh).
     * Throws [AuthFailure] (or [NotSignedIn]).
     */
    @Throws(AuthFailure::class, NotSignedIn::class)
    suspend fun idToken(forceRefresh: Boolean): String

    /** Like [idToken], but also returns the token's auth_time (account linking needs a RECENT real sign-in). */
    @Throws(AuthFailure::class, NotSignedIn::class)
    suspend fun idTokenWithAuthTime(forceRefresh: Boolean): FirebaseIdToken

    /** Re-authenticates the CURRENT user with a new Google ID token (moves auth_time). Throws [AuthFailure] / [NotSignedIn]. */
    @Throws(AuthFailure::class, NotSignedIn::class)
    suspend fun reauthenticateWithGoogle(googleIdToken: String)

    /** Re-authenticates the CURRENT e-mail/password user with their Firebase password (never stored). */
    @Throws(AuthFailure::class, NotSignedIn::class)
    suspend fun reauthenticateWithPassword(password: String)

    /** Re-reads the user from Firebase (picks up a newly verified e-mail). */
    suspend fun reload(): FirebaseSession?

    suspend fun sendEmailVerification()

    fun signOut()
}

class NotSignedIn : Exception("not signed in")

internal fun FirebaseUser.toSession() = FirebaseSession(
    uid = uid,
    email = email,
    displayName = displayName,
    isEmailVerified = isEmailVerified,
    providerIds = providerData.map { it.providerId }.filter { it != "firebase" }
)

internal fun Throwable.toAuthFailure(): AuthFailure = when (this) {
    is AuthFailure -> this
    is FirebaseAuthWeakPasswordException -> AuthFailure(AuthFailureKind.WeakPassword)
    is FirebaseAuthUserCollisionException -> AuthFailure(AuthFailureKind.EmailAlreadyInUse)
    is FirebaseAuthInvalidUserException -> AuthFailure(
        when (errorCode) {
            "ERROR_USER_DISABLED" -> AuthFailureKind.UserDisabled
            "ERROR_USER_MISMATCH" -> AuthFailureKind.UserMismatch
            else -> AuthFailureKind.UserNotFound
        }
    )
    is FirebaseAuthInvalidCredentialsException -> AuthFailure(AuthFailureKind.InvalidCredentials)
    is FirebaseTooManyRequestsException -> AuthFailure(AuthFailureKind.TooManyRequests)
    is FirebaseNetworkException, is IOException -> AuthFailure(AuthFailureKind.Network)
    is FirebaseAuthException -> AuthFailure(AuthFailureKind.Unknown)
    else -> AuthFailure(AuthFailureKind.Unknown)
}

/** Real implementation over the singleton FirebaseAuth provided by Hilt (AppModule). */
class DefaultFirebaseAuthGateway(private val auth: FirebaseAuth) : FirebaseAuthGateway {

    override val currentUser: FirebaseSession? get() = auth.currentUser?.toSession()

    override fun authStates(): Flow<FirebaseSession?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmail(email: String, password: String): FirebaseSession =
        guarded { auth.signInWithEmailAndPassword(email.trim(), password).await().user!!.toSession() }

    override suspend fun createWithEmail(email: String, password: String): FirebaseSession =
        guarded { auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!.toSession() }

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): FirebaseSession = guarded {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(credential).await().user!!.toSession()
    }

    override suspend fun idToken(forceRefresh: Boolean): String {
        val user = auth.currentUser ?: throw NotSignedIn()
        return guarded { user.getIdToken(forceRefresh).await().token ?: throw AuthFailure(AuthFailureKind.Unknown) }
    }

    override suspend fun idTokenWithAuthTime(forceRefresh: Boolean): FirebaseIdToken {
        val user = auth.currentUser ?: throw NotSignedIn()
        return guarded {
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token ?: throw AuthFailure(AuthFailureKind.Unknown)
            FirebaseIdToken(token, result.authTimestamp.takeIf { it > 0 })
        }
    }

    override suspend fun reauthenticateWithGoogle(googleIdToken: String) {
        val user = auth.currentUser ?: throw NotSignedIn()
        guarded { user.reauthenticate(GoogleAuthProvider.getCredential(googleIdToken, null)).await() }
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw NotSignedIn()
        val email = user.email ?: throw AuthFailure(AuthFailureKind.Unknown)
        guarded { user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await() }
    }

    override suspend fun reload(): FirebaseSession? {
        val user = auth.currentUser ?: return null
        runCatching { user.reload().await() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return auth.currentUser?.toSession()
    }

    override suspend fun sendEmailVerification() {
        val user = auth.currentUser ?: return
        guarded { user.sendEmailVerification().await() }
    }

    override fun signOut() = auth.signOut()

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toAuthFailure()
    }
}
