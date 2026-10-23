package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiCodes
import com.example.mindhaven.data.remote.ApiError
import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.FailureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** How the user can prove a RECENT real sign-in again (the backend checks Firebase auth_time, not token age). */
enum class ReauthMethod { Google, Password, SignInAgain }

enum class LinkStep { Starting, Completing, Recovering, Reauthenticating }

/** Why the last linking attempt did not finish. Messages: [linkProblemMessage]. Never carries backend text. */
enum class LinkProblem {
    PasswordRequired, ConsentRequired, WrongPassword, Expired, NotCompleted, Unconfirmed, RateLimited, Offline, Unavailable,
    FeatureDisabled, NotConfigured, ReauthFailed, WrongAccount, Unexpected
}

/** UI-facing linking progress. Contains NO password, challenge or token. */
sealed interface LinkState {
    /** Waiting for the MantraAI password and explicit consent. */
    data object Idle : LinkState
    /** Re-authentication succeeded; the user is asked for the MantraAI password (again). */
    data object Reauthenticated : LinkState
    data class Working(val step: LinkStep) : LinkState
    data class NeedsReauth(val method: ReauthMethod, val problem: LinkProblem? = null) : LinkState
    data class Failed(val problem: LinkProblem, val retryAfterSeconds: Long? = null) : LinkState
    data object Linked : LinkState
}

/** What the repository must do with the auth gate after a linking operation. */
sealed interface LinkOutcome {
    /** Linked (or recovered through the exchange): the canonical API session is installed. */
    data class Authenticated(val session: ApiSession) : LinkOutcome
    /** Stay in the restricted LinkRequired state; [AccountLinker.state] explains what to show. */
    data object Stay : LinkOutcome
    /** Permanent refusal: sign out everywhere and show [message]. */
    data class SignOut(val reason: BlockReason?, val message: String) : LinkOutcome
    /** Another linking operation is already running: this request was ignored (no network call). */
    data object Busy : LinkOutcome
    /** Logout / account switch happened meanwhile: the result belongs to nobody and is discarded. */
    data object Stale : LinkOutcome
}

/**
 * Phase 2B.5B.3: links the signed-in Firebase identity to the EXISTING MantraAI web account after the exchange answered
 * 409 ACCOUNT_LINK_REQUIRED. Contract: EmotionSense-AI/docs/PHASE2B5B2_ACCOUNT_LINKING.md (backend accountLinkRoutes.js).
 *
 *  - One [Mutex] (tryLock): start, complete, recovery and re-authentication never overlap; extra taps are [LinkOutcome.Busy].
 *  - The web password is used for exactly one start request and never stored. The challenge lives ONLY in [pending]
 *    (memory), is bound to the Firebase uid + session epoch, expires locally before the server does, and is dropped on
 *    success, on any ambiguous completion, on expiry and on [reset] (logout / cancel / account switch).
 *  - A challenge is reused ONLY after a refusal the backend makes before it touches the challenge (Firebase token refused,
 *    re-authentication required). After a timeout, a 5xx, an unparsable 2xx, LINK_CHALLENGE_INVALID or
 *    IDENTITY_ALREADY_LINKED it is never replayed: the exchange is asked instead (the link may have committed).
 *  - Freshness: the backend wants a Firebase sign-in younger than 10 minutes. This client asks for re-authentication when
 *    auth_time is older than [maxAuthAgeSeconds] (8 minutes, a margin for the two steps); the server stays authoritative,
 *    and after a successful re-authentication the local check is skipped so a wrong device clock cannot loop.
 *  - Every result is checked against the Firebase uid and session epoch it started with ([LinkOutcome.Stale] otherwise).
 */
class AccountLinker(
    private val gateway: FirebaseAuthGateway,
    private val session: BackendSessionManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAuthAgeSeconds: Long = 480,
    private val challengeSafetyMarginMs: Long = 15_000
) {
    private val api get() = session.api
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** In-memory only. toString() redacted. */
    private class Pending(val challenge: String, val deadlineMs: Long, val uid: String, val epoch: Long) {
        override fun toString(): String = "Pending(challenge=<redacted>)"
    }

    private data class Ctx(val uid: String, val epoch: Long)

    @Volatile private var pending: Pending? = null
    @Volatile private var reauthenticatedAtMs: Long? = null
    @Volatile private var forceTokenRefresh = false

    /** Test/diagnostic hook: whether a challenge is held (never the value). */
    internal val holdsChallenge: Boolean get() = pending != null

    /** Logout, cancel, account switch, or a fresh LinkRequired: forget everything. */
    fun reset() {
        pending = null
        reauthenticatedAtMs = null
        forceTokenRefresh = false
        _state.value = LinkState.Idle
    }

    // -------------------------------------------------------------------------------- public operations

    suspend fun link(password: String, consent: Boolean): LinkOutcome {
        if (!consent) { _state.value = LinkState.Failed(LinkProblem.ConsentRequired); return LinkOutcome.Stay }
        if (password.isEmpty()) { _state.value = LinkState.Failed(LinkProblem.PasswordRequired); return LinkOutcome.Stay }
        if (!mutex.tryLock()) return LinkOutcome.Busy
        try {
            val ctx = context() ?: return LinkOutcome.Stale
            pending = null
            _state.value = LinkState.Working(LinkStep.Starting)
            var refreshed = false
            while (true) {
                val token = token(ctx) ?: return halt(ctx)
                val received = now()
                val result = api.linkStart(token.token, password)
                if (!valid(ctx)) return LinkOutcome.Stale
                when (result) {
                    is ApiResult.Success -> {
                        val ttlMs = result.value.expiresIn * 1000
                        pending = Pending(result.value.challenge, received + ttlMs - challengeSafetyMarginMs, ctx.uid, ctx.epoch)
                        return completeLocked(ctx)
                    }
                    is ApiResult.Failure -> {
                        val e = result.error
                        if (e.isFirebaseTokenRefused() && !refreshed) { refreshed = true; forceTokenRefresh = true; continue }
                        return startFailure(ctx, e)
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun reauthenticateWithGoogle(googleIdToken: String): LinkOutcome =
        reauthenticate { gateway.reauthenticateWithGoogle(googleIdToken) }

    suspend fun reauthenticateWithPassword(firebasePassword: String): LinkOutcome {
        if (firebasePassword.isEmpty()) { _state.value = LinkState.NeedsReauth(ReauthMethod.Password, LinkProblem.PasswordRequired); return LinkOutcome.Stay }
        return reauthenticate { gateway.reauthenticateWithPassword(firebasePassword) }
    }

    // -------------------------------------------------------------------------------- steps

    private suspend fun completeLocked(ctx: Ctx): LinkOutcome {
        val p = pending ?: run { _state.value = LinkState.Failed(LinkProblem.Expired); return LinkOutcome.Stay }
        if (p.uid != ctx.uid || p.epoch != ctx.epoch) { pending = null; return LinkOutcome.Stale }
        if (now() >= p.deadlineMs) { pending = null; _state.value = LinkState.Failed(LinkProblem.Expired); return LinkOutcome.Stay }
        _state.value = LinkState.Working(LinkStep.Completing)
        var refreshed = false
        while (true) {
            val token = token(ctx) ?: return halt(ctx) // keeps the challenge
            val received = now()
            val result = api.linkComplete(token.token, p.challenge)
            if (!valid(ctx)) { pending = null; return LinkOutcome.Stale }
            when (result) {
                is ApiResult.Success -> {
                    pending = null
                    val body = result.value
                    val user = body.user ?: return recover(ctx, LinkProblem.Unconfirmed)
                    val adopted = session.adoptLinkedSession(body.token, body.expiresAt, body.expiresIn, user, ctx.uid, ctx.epoch, received)
                        ?: return LinkOutcome.Stale
                    _state.value = LinkState.Linked
                    return LinkOutcome.Authenticated(adopted)
                }
                is ApiResult.Failure -> {
                    val e = result.error
                    // Refusals made BEFORE the backend touches the challenge: the challenge is still valid.
                    if (e.isFirebaseTokenRefused() && !refreshed) { refreshed = true; forceTokenRefresh = true; continue }
                    if (e.code == ApiCodes.REAUTHENTICATION_REQUIRED) { _state.value = LinkState.NeedsReauth(reauthMethod()); return LinkOutcome.Stay }
                    pending = null // anything else: never replay this challenge
                    return completeFailure(ctx, e)
                }
            }
        }
    }

    private suspend fun recover(ctx: Ctx, ifStillRequired: LinkProblem): LinkOutcome {
        _state.value = LinkState.Working(LinkStep.Recovering)
        val result = session.ensureSession(forceExchange = true)
        if (!valid(ctx)) return LinkOutcome.Stale
        return when (result) {
            is SessionResult.Ready -> { _state.value = LinkState.Linked; LinkOutcome.Authenticated(result.session) }
            is SessionResult.Blocked ->
                // Still 409: the link definitely did not commit. Ask for the password again (fresh start, never a replay).
                if (result.reason == BlockReason.AccountLinkRequired) stay(if (ifStillRequired == LinkProblem.Unconfirmed) LinkProblem.NotCompleted else ifStillRequired)
                else LinkOutcome.SignOut(result.reason, blockedMessage(result.reason))
            is SessionResult.Temporary -> stay(if (result.reason == TemporaryReason.NotConfigured) LinkProblem.NotConfigured else LinkProblem.Unconfirmed, result.retryAfterSeconds)
            SessionResult.ReauthRequired -> LinkOutcome.SignOut(null, SIGN_IN_AGAIN)
            SessionResult.Rejected -> stay(LinkProblem.Unexpected)
            SessionResult.SignedOut -> LinkOutcome.Stale
        }
    }

    private suspend fun reauthenticate(block: suspend () -> Unit): LinkOutcome {
        if (!mutex.tryLock()) return LinkOutcome.Busy
        try {
            val ctx = context() ?: return LinkOutcome.Stale
            val method = reauthMethod()
            _state.value = LinkState.Working(LinkStep.Reauthenticating)
            try {
                block()
            } catch (e: NotSignedIn) {
                return LinkOutcome.Stale
            } catch (e: AuthFailure) {
                val problem = when (e.kind) {
                    AuthFailureKind.InvalidCredentials -> LinkProblem.ReauthFailed
                    AuthFailureKind.UserMismatch -> LinkProblem.WrongAccount
                    AuthFailureKind.Network -> LinkProblem.Offline
                    AuthFailureKind.TooManyRequests -> LinkProblem.RateLimited
                    AuthFailureKind.UserDisabled, AuthFailureKind.UserNotFound -> return LinkOutcome.SignOut(null, SIGN_IN_AGAIN)
                    else -> LinkProblem.Unexpected
                }
                _state.value = LinkState.NeedsReauth(method, problem)
                return LinkOutcome.Stay
            }
            if (!valid(ctx)) return LinkOutcome.Stale // re-authentication must never switch the account
            reauthenticatedAtMs = now()
            forceTokenRefresh = true // the next token must carry the new auth_time
            val p = pending
            if (p != null && now() < p.deadlineMs) return completeLocked(ctx)
            pending = null
            _state.value = LinkState.Reauthenticated
            return LinkOutcome.Stay
        } finally {
            mutex.unlock()
        }
    }

    // -------------------------------------------------------------------------------- failures

    private suspend fun startFailure(ctx: Ctx, e: ApiError): LinkOutcome = when {
        e.kind == FailureKind.NotConfigured -> stay(LinkProblem.NotConfigured)
        e.kind == FailureKind.Offline -> stay(LinkProblem.Offline)
        e.kind == FailureKind.Timeout || e.kind == FailureKind.Network -> stay(LinkProblem.Unavailable)
        e.kind == FailureKind.BadResponse -> stay(LinkProblem.Unexpected)
        e.code == ApiCodes.LINK_PROOF_FAILED -> stay(LinkProblem.WrongPassword)
        e.code == ApiCodes.REAUTHENTICATION_REQUIRED -> { _state.value = LinkState.NeedsReauth(reauthMethod()); LinkOutcome.Stay }
        e.isFirebaseTokenRefused() -> LinkOutcome.SignOut(null, SIGN_IN_AGAIN)
        // Already mapped (e.g. a previous completion committed but its response was lost): the exchange resolves it.
        e.code == ApiCodes.IDENTITY_ALREADY_LINKED -> recover(ctx, LinkProblem.NotCompleted)
        else -> commonFailure(e) ?: stay(LinkProblem.Unexpected)
    }

    private suspend fun completeFailure(ctx: Ctx, e: ApiError): LinkOutcome = when {
        e.kind == FailureKind.NotConfigured -> stay(LinkProblem.NotConfigured)
        e.isFirebaseTokenRefused() -> LinkOutcome.SignOut(null, SIGN_IN_AGAIN)
        e.code == ApiCodes.LINK_CHALLENGE_INVALID -> recover(ctx, LinkProblem.Expired)
        e.code == ApiCodes.IDENTITY_ALREADY_LINKED -> recover(ctx, LinkProblem.NotCompleted)
        e.httpStatus == 429 || e.httpStatus == 404 || e.httpStatus == 403 -> commonFailure(e) ?: stay(LinkProblem.Unexpected)
        // Timeout, offline, transport error, 5xx, unparsable 2xx, anything else: the link MAY have committed.
        else -> recover(ctx, LinkProblem.Unconfirmed)
    }

    /** Codes that mean the same at both steps. null = not one of them. */
    private fun commonFailure(e: ApiError): LinkOutcome? = when {
        e.code == ApiCodes.EMAIL_NOT_VERIFIED -> LinkOutcome.SignOut(BlockReason.EmailNotVerified, blockedMessage(BlockReason.EmailNotVerified))
        e.code == ApiCodes.UNSUPPORTED_SIGN_IN_METHOD -> LinkOutcome.SignOut(BlockReason.UnsupportedSignInMethod, blockedMessage(BlockReason.UnsupportedSignInMethod))
        e.code == ApiCodes.IDENTITY_DISABLED -> LinkOutcome.SignOut(BlockReason.IdentityDisabled, blockedMessage(BlockReason.IdentityDisabled))
        e.code == ApiCodes.IDENTITY_ALREADY_LINKED -> null // handled by the caller (recovery through the exchange)
        e.httpStatus == 429 -> stay(LinkProblem.RateLimited, e.retryAfterSeconds)
        e.httpStatus == 404 -> stay(LinkProblem.FeatureDisabled)
        e.httpStatus == 503 || (e.httpStatus != null && e.httpStatus in 500..599) -> stay(LinkProblem.Unavailable)
        else -> null
    }

    // -------------------------------------------------------------------------------- helpers

    /**
     * A Firebase ID token whose auth_time is recent enough, or null after setting the state (re-authentication needed /
     * Firebase unavailable). A normal getIdToken(false) is used; a refresh is forced only once after a refused token and
     * once after re-authentication (a refresh alone never makes a sign-in "recent").
     */
    private suspend fun token(ctx: Ctx): FirebaseIdToken? {
        val force = forceTokenRefresh
        val t = try {
            gateway.idTokenWithAuthTime(force)
        } catch (e: NotSignedIn) {
            return null
        } catch (e: AuthFailure) {
            _state.value = when (e.kind) {
                AuthFailureKind.Network -> LinkState.Failed(LinkProblem.Offline)
                AuthFailureKind.TooManyRequests -> LinkState.Failed(LinkProblem.RateLimited)
                else -> LinkState.Failed(LinkProblem.Unavailable)
            }
            return null
        }
        if (force) forceTokenRefresh = false
        if (!valid(ctx)) return null
        if (!isRecent(t)) { _state.value = LinkState.NeedsReauth(reauthMethod()); return null }
        return t
    }

    private fun isRecent(t: FirebaseIdToken): Boolean {
        reauthenticatedAtMs?.let { if (now() - it < REAUTH_TRUST_MS) return true }
        val authTime = t.authTimeSeconds ?: return true // unknown: the server decides
        return now() / 1000 - authTime <= maxAuthAgeSeconds
    }

    private fun reauthMethod(): ReauthMethod {
        val user = gateway.currentUser ?: return ReauthMethod.SignInAgain
        return when {
            user.usesGoogle -> ReauthMethod.Google
            user.usesPassword && !user.email.isNullOrEmpty() -> ReauthMethod.Password
            else -> ReauthMethod.SignInAgain
        }
    }

    /** After [token] returned null: stay (state already explains why) unless the identity changed meanwhile. */
    private fun halt(ctx: Ctx): LinkOutcome = if (valid(ctx)) LinkOutcome.Stay else LinkOutcome.Stale

    private fun context(): Ctx? = gateway.currentUser?.let { Ctx(it.uid, session.epoch) }
    private fun valid(ctx: Ctx) = session.epoch == ctx.epoch && gateway.currentUser?.uid == ctx.uid

    private fun stay(problem: LinkProblem, retryAfterSeconds: Long? = null): LinkOutcome {
        _state.value = LinkState.Failed(problem, retryAfterSeconds)
        return LinkOutcome.Stay
    }

    private fun ApiError.isFirebaseTokenRefused() = httpStatus == 401 && code == ApiCodes.INVALID_FIREBASE_TOKEN

    private companion object {
        const val REAUTH_TRUST_MS = 5 * 60_000L
        const val SIGN_IN_AGAIN = "Your sign-in expired. Please sign in again."
    }
}

/** User-facing text for a linking problem. Never reveals anything about another account. */
fun linkProblemMessage(problem: LinkProblem, retryAfterSeconds: Long? = null): String = when (problem) {
    LinkProblem.PasswordRequired -> "Please enter your password."
    LinkProblem.ConsentRequired -> "Please confirm that you want to link these accounts."
    LinkProblem.WrongPassword -> "That password is incorrect. Please try again."
    LinkProblem.Expired -> "This linking request expired. Please enter your password and try again."
    LinkProblem.NotCompleted -> "Linking didn't finish. Please enter your password and try again."
    LinkProblem.Unconfirmed -> "We couldn't confirm whether linking finished. Check your connection, then tap Link account again."
    LinkProblem.RateLimited -> retryAfterSeconds?.takeIf { it > 0 }
        ?.let { "Too many attempts. Please try again in about ${if (it >= 90) "${(it + 59) / 60} minutes" else "$it seconds"}." }
        ?: "Too many attempts. Please wait a few minutes and try again."
    LinkProblem.Offline -> "You appear to be offline. Check your connection and try again."
    LinkProblem.Unavailable -> "Your account service is temporarily unavailable. Please try again."
    LinkProblem.FeatureDisabled -> "Account linking isn't available yet. Please try again later or use another account."
    LinkProblem.NotConfigured -> "Account sync isn't set up in this build."
    LinkProblem.ReauthFailed -> "That password didn't work. Please try again."
    LinkProblem.WrongAccount -> "That is a different account. Please confirm with the account you signed in with."
    LinkProblem.Unexpected -> "Something went wrong. Please try again."
}
