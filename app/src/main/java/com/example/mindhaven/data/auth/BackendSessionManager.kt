package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiCodes
import com.example.mindhaven.data.remote.ApiError
import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.ApiUser
import com.example.mindhaven.data.remote.BackendApi
import com.example.mindhaven.data.remote.FailureKind
import com.example.mindhaven.data.remote.IdentityProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** In-memory backend credential. NEVER persisted. toString() is redacted. */
class ApiSession(
    internal val token: String,
    /** Server-reported expiry (epoch ms). Informational: the clocks of server and device may differ. */
    val serverExpiresAtMs: Long,
    /** Device-clock expiry computed from `expiresIn` at receipt (immune to clock skew). */
    val expiresAtMs: Long,
    /** Canonical Neon users.id, taken ONLY from the backend response. */
    val user: ApiUser,
    internal val firebaseUid: String,
    internal val generation: Long
) {
    override fun toString(): String = "ApiSession(user=${user.id}, token=<redacted>)"
}

enum class BlockReason { EmailNotVerified, UnsupportedSignInMethod, IdentityDisabled, AccountLinkRequired }
enum class TemporaryReason { Offline, Timeout, RateLimited, ServiceUnavailable, NotConfigured }

sealed interface SessionResult {
    data class Ready(val session: ApiSession) : SessionResult
    data object SignedOut : SessionResult

    /** The backend refuses this identity for a reason retrying cannot fix. */
    data class Blocked(val reason: BlockReason) : SessionResult

    /** The Firebase token was refused even after one forced refresh: sign in again. */
    data object ReauthRequired : SessionResult

    /** Worth retrying later. The Firebase session must be kept. */
    data class Temporary(val reason: TemporaryReason, val retryAfterSeconds: Long?) : SessionResult

    /** Bad request / unexpected contract: not retryable, show a generic message. */
    data object Rejected : SessionResult
}

sealed interface Authenticated<out T> {
    data class Ok<T>(val value: T) : Authenticated<T>
    data class ApiFailure(val error: ApiError) : Authenticated<Nothing>
    data class Session(val result: SessionResult) : Authenticated<Nothing>

    /** The user logged out / switched while the call was in flight: the result must be discarded. */
    data object Stale : Authenticated<Nothing>
}

sealed interface SessionState {
    data object SignedOut : SessionState
    data object Exchanging : SessionState
    data class Ready(val user: ApiUser, val expiresAtMs: Long) : SessionState
    data class Problem(val result: SessionResult) : SessionState
}

/**
 * Owns the short-lived (5 minute) MantraAI API token.
 *
 *  - The token lives in memory only; after a process restart it is obtained again through the exchange using the
 *    Firebase session restored by FirebaseAuth.
 *  - One Mutex serialises exchanges: any number of simultaneous callers cause ONE exchange.
 *  - A token is refreshed [refreshMarginMs] (30 s) before it expires, lazily on use and, when a [scope] is given,
 *    proactively by a timer.
 *  - Every logout/account change bumps a generation counter and wipes the cache; work started under an older
 *    generation can neither be reused nor publish results ([Authenticated.Stale]).
 *  - Retries are bounded: one forced Firebase-token refresh on 401, one reload on EMAIL_NOT_VERIFIED, at most
 *    [maxAutoRetries] waits for 429/5xx/timeout (honouring Retry-After up to [maxAutoWaitSeconds]).
 */
class BackendSessionManager(
    private val gateway: FirebaseAuthGateway,
    internal val api: BackendApi,
    private val scope: CoroutineScope? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val refreshMarginMs: Long = 30_000,
    private val maxAutoRetries: Int = 2,
    private val maxAutoWaitSeconds: Long = 30
) {
    private val mutex = Mutex()
    private val generation = AtomicLong(0)
    private val cache = AtomicReference<ApiSession?>(null)
    private val _state = MutableStateFlow<SessionState>(SessionState.SignedOut)
    @Volatile private var refreshJob: Job? = null

    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Monotonic identity epoch; changes on every [clear]. */
    val epoch: Long get() = generation.get()

    /** Forget everything (logout / account switch). Safe to call from any thread, never blocks. */
    fun clear() {
        generation.incrementAndGet()
        cache.set(null)
        refreshJob?.cancel()
        refreshJob = null
        _state.value = SessionState.SignedOut
    }

    /** A valid session, performing at most one exchange even under heavy concurrency. */
    suspend fun ensureSession(forceExchange: Boolean = false): SessionResult = mutex.withLock {
        val user = gateway.currentUser ?: return@withLock SessionResult.SignedOut.also { clear() }
        val cached = cache.get()
        if (cached != null && cached.firebaseUid != user.uid) clear() // account switch: never reuse another user's token
        val current = cache.get()
        if (!forceExchange && current != null && current.firebaseUid == user.uid && now() < current.expiresAtMs - refreshMarginMs) {
            return@withLock SessionResult.Ready(current)
        }
        val gen = generation.get()
        _state.value = SessionState.Exchanging
        val result = exchangeWithPolicy(user.uid, gen)
        // If the identity changed while we were waiting on the network, drop the outcome.
        if (generation.get() != gen || gateway.currentUser?.uid != user.uid) return@withLock SessionResult.SignedOut
        when (result) {
            is SessionResult.Ready -> {
                cache.set(result.session)
                _state.value = SessionState.Ready(result.session.user, result.session.expiresAtMs)
                scheduleRefresh(result.session)
            }
            else -> {
                cache.set(null)
                _state.value = if (result is SessionResult.SignedOut) SessionState.SignedOut else SessionState.Problem(result)
            }
        }
        result
    }

    /**
     * Runs an authenticated backend call with a valid token. On 401 it re-exchanges ONCE and retries ONCE; a second 401
     * becomes [SessionResult.ReauthRequired]. Never loops.
     */
    suspend fun <T> authenticated(call: suspend (apiToken: String) -> ApiResult<T>): Authenticated<T> {
        var forced = false
        while (true) {
            val ready = when (val s = ensureSession(forceExchange = forced)) {
                is SessionResult.Ready -> s.session
                else -> return Authenticated.Session(s)
            }
            val gen = ready.generation
            val result = call(ready.token)
            if (generation.get() != gen) return Authenticated.Stale
            when (result) {
                is ApiResult.Success -> return Authenticated.Ok(result.value)
                is ApiResult.Failure -> {
                    if (result.error.isUnauthenticated) {
                        if (forced) return Authenticated.Session(SessionResult.ReauthRequired)
                        cache.compareAndSet(ready, null) // only drop the token that was actually rejected
                        forced = true
                        continue
                    }
                    return Authenticated.ApiFailure(result.error)
                }
            }
        }
    }

    /**
     * Installs the API token returned by POST /api/auth/firebase/link/complete (account linking) exactly as if it had come
     * from an exchange: in memory only, same expiry policy, same refresh timer (later refreshes use the exchange, which
     * now resolves the linked identity). Refused (false) if a logout/account switch happened since [expectedEpoch] or the
     * Firebase user is no longer [firebaseUid]: a token never outlives the identity it was issued for.
     */
    suspend fun adoptLinkedSession(
        token: String,
        serverExpiresAtMs: Long,
        expiresInSeconds: Long,
        user: ApiUser,
        firebaseUid: String,
        expectedEpoch: Long,
        receivedAtMs: Long
    ): ApiSession? = mutex.withLock {
        if (generation.get() != expectedEpoch || gateway.currentUser?.uid != firebaseUid) return@withLock null
        if (token.isEmpty() || user.id.isEmpty()) return@withLock null
        val local = if (expiresInSeconds in 1..MAX_SANE_TTL_SECONDS) receivedAtMs + expiresInSeconds * 1000 else serverExpiresAtMs
        val session = ApiSession(token, serverExpiresAtMs, local, user, firebaseUid, expectedEpoch)
        cache.set(session)
        _state.value = SessionState.Ready(user, local)
        scheduleRefresh(session)
        session
    }

    /** GET /api/identity/me. The canonical id is whatever the backend answers for the token subject. */
    suspend fun fetchProfile(): Authenticated<IdentityProfile> = authenticated { api.me(it) }

    // ------------------------------------------------------------------------------------------

    private suspend fun exchangeWithPolicy(uid: String, gen: Long): SessionResult {
        var forceFirebaseRefresh = false
        var refreshedForInvalidToken = false
        var reloadedForVerification = false
        var autoRetries = 0

        suspend fun backoff(reason: TemporaryReason, error: ApiError, waitMs: Long?): SessionResult? {
            val retryAfter = error.retryAfterSeconds
            if (autoRetries >= maxAutoRetries || (retryAfter != null && retryAfter > maxAutoWaitSeconds)) {
                return SessionResult.Temporary(reason, retryAfter)
            }
            val wait = if (retryAfter != null) maxOf(retryAfter * 1000, MIN_WAIT_MS) else (waitMs ?: (MIN_WAIT_MS shl autoRetries))
            autoRetries += 1
            pause(wait)
            return null // keep looping
        }

        while (true) {
            if (generation.get() != gen || gateway.currentUser?.uid != uid) return SessionResult.SignedOut

            val firebaseToken = try {
                gateway.idToken(forceFirebaseRefresh)
            } catch (e: NotSignedIn) {
                return SessionResult.SignedOut
            } catch (e: AuthFailure) {
                return when (e.kind) {
                    AuthFailureKind.Network -> SessionResult.Temporary(TemporaryReason.Offline, null)
                    AuthFailureKind.TooManyRequests -> SessionResult.Temporary(TemporaryReason.RateLimited, null)
                    AuthFailureKind.UserDisabled, AuthFailureKind.UserNotFound, AuthFailureKind.InvalidCredentials -> SessionResult.ReauthRequired
                    else -> SessionResult.Temporary(TemporaryReason.ServiceUnavailable, null)
                }
            }
            forceFirebaseRefresh = false

            val received = now()
            when (val r = api.exchange(firebaseToken)) {
                is ApiResult.Success -> {
                    val body = r.value
                    val user = body.user ?: return SessionResult.Rejected
                    // Prefer the relative lifetime (clock-skew proof); fall back to the server timestamp.
                    val local = if (body.expiresIn in 1..MAX_SANE_TTL_SECONDS) received + body.expiresIn * 1000 else body.expiresAt
                    return SessionResult.Ready(ApiSession(body.token, body.expiresAt, local, user, uid, gen))
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    val status = e.httpStatus
                    when {
                        e.kind == FailureKind.NotConfigured -> return SessionResult.Temporary(TemporaryReason.NotConfigured, null)
                        e.kind == FailureKind.Offline -> return SessionResult.Temporary(TemporaryReason.Offline, null)
                        e.kind == FailureKind.Timeout -> backoff(TemporaryReason.Timeout, e, null)?.let { return it }
                        e.kind == FailureKind.Network -> backoff(TemporaryReason.Offline, e, null)?.let { return it }
                        e.kind == FailureKind.BadResponse -> return SessionResult.Rejected
                        status == 401 -> {
                            if (refreshedForInvalidToken) return SessionResult.ReauthRequired
                            refreshedForInvalidToken = true
                            forceFirebaseRefresh = true // one forced Firebase-token refresh, one retry
                        }
                        e.code == ApiCodes.EMAIL_NOT_VERIFIED -> {
                            // A token minted before the user verified carries email_verified=false: reload + force refresh once.
                            if (!reloadedForVerification) {
                                reloadedForVerification = true
                                if (gateway.reload()?.isEmailVerified == true) { forceFirebaseRefresh = true; continue }
                            }
                            return SessionResult.Blocked(BlockReason.EmailNotVerified)
                        }
                        e.code == ApiCodes.UNSUPPORTED_SIGN_IN_METHOD -> return SessionResult.Blocked(BlockReason.UnsupportedSignInMethod)
                        e.code == ApiCodes.IDENTITY_DISABLED -> return SessionResult.Blocked(BlockReason.IdentityDisabled)
                        e.code == ApiCodes.ACCOUNT_LINK_REQUIRED -> return SessionResult.Blocked(BlockReason.AccountLinkRequired)
                        status == 400 || status == 413 || status == 415 -> return SessionResult.Rejected
                        status == 429 -> backoff(TemporaryReason.RateLimited, e, null)?.let { return it }
                        status == 404 -> return SessionResult.Temporary(TemporaryReason.ServiceUnavailable, null) // exchange not enabled here
                        status != null && status in 500..599 -> backoff(TemporaryReason.ServiceUnavailable, e, null)?.let { return it }
                        else -> return SessionResult.Rejected
                    }
                }
            }
        }
    }

    private fun scheduleRefresh(session: ApiSession) {
        val s = scope ?: return
        refreshJob?.cancel()
        refreshJob = s.launch {
            val wait = (session.expiresAtMs - refreshMarginMs - now()).coerceAtLeast(0)
            delay(wait)
            if (generation.get() != session.generation) return@launch
            ensureSession(forceExchange = true) // the token is at the end of its life; success reschedules this timer
        }
    }

    private companion object {
        const val MIN_WAIT_MS = 1_000L
        const val MAX_SANE_TTL_SECONDS = 3_600L
    }
}
