package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiError
import com.example.mindhaven.data.remote.IdentityProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Signed-in user as the UI sees it. */
data class AuthUser(val uid: String, val displayName: String?, val email: String?)

/**
 * THE navigation-relevant authentication state. Exactly one source of truth for "may this user use the app?".
 *
 *  - [SignedOut]: no Firebase session, or a sign-in is still in progress on the login screens.
 *  - [Verifying]: a Firebase session exists (restored after a restart, or retrying) and the backend exchange is running.
 *  - [Authenticated]: Firebase AND the backend accepted the identity. [userId] is the canonical Neon users.id.
 *  - [BackendUnavailable]: Firebase is signed in, but the backend could not be reached. Firebase is kept; Retry is offered.
 *  - [LinkRequired]: Firebase is signed in, but the backend answered 409 ACCOUNT_LINK_REQUIRED (a MantraAI account with
 *    this verified e-mail exists and is not linked). RESTRICTED: no API token, no app screens, only the linking screen.
 */
sealed interface AuthGate {
    data object SignedOut : AuthGate
    data class Verifying(val user: AuthUser) : AuthGate
    data class Authenticated(val user: AuthUser, val userId: String) : AuthGate
    data class BackendUnavailable(val user: AuthUser, val reason: TemporaryReason, val retryAfterSeconds: Long?) : AuthGate
    data class LinkRequired(val user: AuthUser) : AuthGate
}

/** What the UI should say about the backend session. Contains no token. */
sealed interface BackendStatus {
    /** Nothing to show (signed out, or the session is healthy). */
    data object Ok : BackendStatus
    data object Connecting : BackendStatus
    /** Firebase is signed in but the backend cannot be reached right now. Retry is offered. */
    data class Unavailable(val reason: TemporaryReason, val retryAfterSeconds: Long?) : BackendStatus
}

sealed interface ProfileState {
    data object Idle : ProfileState
    data object Loading : ProfileState
    data class Loaded(val profile: IdentityProfile) : ProfileState
    data class Failed(val error: ApiError?) : ProfileState
}

/** Result of an interactive sign-in / registration. [message] is safe to display. */
sealed interface AuthOutcome {
    /** Firebase and the backend accepted the identity. */
    data class SignedIn(val user: AuthUser) : AuthOutcome
    /** Firebase is signed in but the backend is temporarily unreachable. NOT authenticated; Retry is offered. */
    data class BackendUnavailable(val user: AuthUser, val reason: TemporaryReason, val message: String) : AuthOutcome
    /** The e-mail must be verified first. The user was signed out; [emailSent] tells whether a link went out. */
    data class VerificationRequired(val emailSent: Boolean, val message: String) : AuthOutcome
    /** The backend refuses this identity. The user was signed out. */
    data class Blocked(val reason: BlockReason, val message: String) : AuthOutcome
    /** A MantraAI account with this e-mail exists: Firebase is kept, the restricted linking screen is shown. */
    data class LinkRequired(val user: AuthUser) : AuthOutcome
    data class Failed(val message: String) : AuthOutcome
}

interface AuthRepository {
    /** Single source of truth for navigation. */
    val authGate: StateFlow<AuthGate>

    /** The user ONLY while [AuthGate.Authenticated] (null otherwise). Kept for existing screens. */
    val authUser: StateFlow<AuthUser?>
    val backendStatus: StateFlow<BackendStatus>
    val profile: StateFlow<ProfileState>

    suspend fun signInWithEmail(email: String, password: String): AuthOutcome
    suspend fun signUpWithEmail(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(googleIdToken: String): AuthOutcome

    /** Tries the backend again while Firebase stays signed in ([AuthGate.BackendUnavailable] or an authenticated banner). */
    suspend fun retryBackend(): AuthOutcome?

    /** Reloads GET /api/identity/me into [profile]. Results from a previous user are discarded. */
    suspend fun refreshProfile()

    /** Last notice produced by a restore-time block (e.g. the backend refused the restored identity). */
    val restoreNotice: StateFlow<String?>

    fun logout()

    // ------------------------------------------------------------------ account linking (Phase 2B.5B.3)

    /** Progress of the linking flow while [authGate] is [AuthGate.LinkRequired]. Contains no secret. */
    val linkState: StateFlow<LinkState>

    /** Links the signed-in Firebase identity to the existing MantraAI account. Ignored unless [AuthGate.LinkRequired]. */
    suspend fun linkAccount(webPassword: String, consent: Boolean)

    /** Proves a recent sign-in again with a new GOOGLE ID token (when [LinkState.NeedsReauth] asks for Google). */
    suspend fun reauthenticateWithGoogle(googleIdToken: String)

    /** Proves a recent sign-in again with the Firebase (MindHaven) password (when it asks for Password). Never stored. */
    suspend fun reauthenticateWithPassword(firebasePassword: String)

    /** Cancel / "Use another account": forget linking state and sign out of Firebase + Google. Deletes nothing. */
    fun cancelLinking()
}

/**
 * Orchestrates Firebase sign-in, the backend exchange and logout.
 *
 * Access to the app ([AuthGate.Authenticated]) is granted ONLY after the backend exchange succeeded, both after an
 * interactive sign-in and after a process restart (a persisted FirebaseAuth session alone is never enough). Temporary
 * backend failures keep the Firebase session and produce [AuthGate.BackendUnavailable]; permanent refusals sign out.
 * Every outcome is published only if no logout/account switch happened meanwhile (session epoch check).
 */
class DefaultAuthRepository(
    private val gateway: FirebaseAuthGateway,
    private val session: BackendSessionManager,
    private val google: GoogleSignOutHandle,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val linker: AccountLinker = AccountLinker(gateway, session, now)
) : AuthRepository {

    private val _gate = MutableStateFlow<AuthGate>(AuthGate.SignedOut)
    private val _authUser = MutableStateFlow<AuthUser?>(null)
    private val _backend = MutableStateFlow<BackendStatus>(BackendStatus.Ok)
    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Idle)
    private val _notice = MutableStateFlow<String?>(null)

    override val authGate: StateFlow<AuthGate> = _gate.asStateFlow()
    override val authUser: StateFlow<AuthUser?> = _authUser.asStateFlow()
    override val backendStatus: StateFlow<BackendStatus> = _backend.asStateFlow()
    override val profile: StateFlow<ProfileState> = _profile.asStateFlow()
    override val restoreNotice: StateFlow<String?> = _notice.asStateFlow()
    override val linkState: StateFlow<LinkState> = linker.state

    @Volatile private var interactive = false
    private var lastVerificationMailAt: Long? = null

    init {
        // 1) Process restart: FirebaseAuth restored its session. Do NOT grant access yet: verify with the backend first.
        gateway.currentUser?.let { restored ->
            if (restored.isEligibleForBackend) {
                setGate(AuthGate.Verifying(restored.toAuthUser()))
                val epoch = session.epoch
                scope.launch { verifyWithBackend(restored, epoch, forceExchange = false) }
            } else {
                gateway.signOut() // never keep an unverified e-mail/password session around
            }
        }
        // 2) Sign-outs / account changes that happen elsewhere (revoked, deleted, other account) drop all state.
        scope.launch {
            gateway.authStates().collect { fb ->
                if (interactive) return@collect // interactive flows publish their own outcome
                val shownUid = signedInUser(_gate.value)?.uid
                if (fb == null) {
                    if (_gate.value != AuthGate.SignedOut) wipeLocalState()
                } else if (shownUid != null && shownUid != fb.uid) {
                    wipeLocalState() // never carry the previous user's state or token over
                }
            }
        }
    }

    // ---------------------------------------------------------------------------- sign-in flows

    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome = interactiveFlow {
        val fb = gateway.signInWithEmail(email, password)
        val fresh = gateway.reload() ?: fb
        if (!fresh.isEligibleForBackend) return@interactiveFlow requireVerification()
        completeSignIn(fresh)
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthOutcome = interactiveFlow {
        gateway.createWithEmail(email, password)
        val sent = sendVerificationMail()
        gateway.signOut() // the backend only accepts verified e-mails: sign in again after verifying
        wipeLocalState()
        AuthOutcome.VerificationRequired(
            emailSent = sent,
            message = if (sent) "Account created. We sent a verification link to your email. Verify it, then log in."
            else "Account created. Verify your email (log in to receive a new link), then log in again."
        )
    }

    override suspend fun signInWithGoogle(googleIdToken: String): AuthOutcome = interactiveFlow {
        completeSignIn(gateway.signInWithGoogleIdToken(googleIdToken))
    }

    private suspend fun interactiveFlow(block: suspend () -> AuthOutcome): AuthOutcome {
        interactive = true
        _notice.value = null
        return try {
            block()
        } catch (e: AuthFailure) {
            AuthOutcome.Failed(messageFor(e.kind))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } finally {
            interactive = false
        }
    }

    private suspend fun requireVerification(): AuthOutcome {
        val sent = sendVerificationMail()
        gateway.signOut()
        wipeLocalState()
        return AuthOutcome.VerificationRequired(
            emailSent = sent,
            message = if (sent) "Please verify your email first. We sent you a new verification link; then log in again."
            else "Please verify your email first (check your inbox), then log in again."
        )
    }

    /** Resend is throttled client-side (Firebase also rate-limits) so repeated logins cannot spam the mailbox. */
    private suspend fun sendVerificationMail(): Boolean {
        val t = now()
        lastVerificationMailAt?.let { last -> if (t - last < VERIFICATION_COOLDOWN_MS) return false }
        return try { gateway.sendEmailVerification(); lastVerificationMailAt = t; true } catch (e: AuthFailure) { false }
    }

    /** Interactive sign-in: Firebase succeeded; now the backend decides. */
    private suspend fun completeSignIn(fb: FirebaseSession): AuthOutcome {
        val epoch = session.epoch
        val result = session.ensureSession()
        if (session.epoch != epoch || gateway.currentUser?.uid != fb.uid) {
            return AuthOutcome.Failed("Sign-in was interrupted. Please try again.") // logout/switch happened meanwhile
        }
        return publish(fb, result)
    }

    /**
     * Maps a backend result to the gate. Blocking results sign out; temporary ones keep Firebase.
     * Callers must have checked that the epoch/user did not change while waiting.
     */
    private suspend fun publish(fb: FirebaseSession, result: SessionResult): AuthOutcome {
        val user = fb.toAuthUser()
        return when (result) {
            is SessionResult.Ready -> {
                _backend.value = BackendStatus.Ok
                setGate(AuthGate.Authenticated(user, result.session.user.id))
                scope.launch { refreshProfile() }
                AuthOutcome.SignedIn(user)
            }
            is SessionResult.Temporary -> {
                _backend.value = BackendStatus.Unavailable(result.reason, result.retryAfterSeconds)
                setGate(AuthGate.BackendUnavailable(user, result.reason, result.retryAfterSeconds))
                AuthOutcome.BackendUnavailable(user, result.reason, unavailableMessage(result.reason))
            }
            // A MantraAI account with this e-mail exists: keep Firebase, open ONLY the restricted linking screen.
            is SessionResult.Blocked -> if (result.reason == BlockReason.AccountLinkRequired) {
                session.clear() // no API token may exist in this state
                linker.reset()
                _backend.value = BackendStatus.Ok
                setGate(AuthGate.LinkRequired(user))
                AuthOutcome.LinkRequired(user)
            } else signOutBlocked(result)
            SessionResult.ReauthRequired -> { signOutEverywhere(); AuthOutcome.Failed("Your sign-in expired. Please sign in again.") }
            SessionResult.Rejected -> { signOutEverywhere(); AuthOutcome.Failed("We couldn't sign you in right now. Please try again.") }
            SessionResult.SignedOut -> {
                // Never leave a hidden Firebase session behind a signed-out UI.
                if (gateway.currentUser?.uid == fb.uid) signOutEverywhere() else wipeLocalState()
                AuthOutcome.Failed("Sign-in was interrupted. Please try again.")
            }
        }
    }

    private suspend fun signOutBlocked(blocked: SessionResult.Blocked): AuthOutcome {
        val message = blockedMessage(blocked.reason)
        return if (blocked.reason == BlockReason.EmailNotVerified) {
            requireVerification()
        } else {
            signOutEverywhere()
            AuthOutcome.Blocked(blocked.reason, message)
        }
    }

    // ---------------------------------------------------------------------------- restore / retry

    /** Restore after a restart, or Retry: backend verification of an existing Firebase session. */
    private suspend fun verifyWithBackend(fb: FirebaseSession, epoch: Long, forceExchange: Boolean): AuthOutcome? {
        _backend.value = BackendStatus.Connecting
        val result = session.ensureSession(forceExchange = forceExchange)
        // Logged out or switched while we waited: this result belongs to nobody, publish nothing.
        if (session.epoch != epoch || gateway.currentUser?.uid != fb.uid) return null
        val outcome = publish(fb, result)
        if (outcome is AuthOutcome.Blocked) _notice.value = outcome.message
        if (outcome is AuthOutcome.Failed && result == SessionResult.ReauthRequired) _notice.value = outcome.message
        return outcome
    }

    override suspend fun retryBackend(): AuthOutcome? {
        val fb = gateway.currentUser ?: return null
        val epoch = session.epoch
        if (_gate.value !is AuthGate.Authenticated) setGate(AuthGate.Verifying(fb.toAuthUser()))
        return verifyWithBackend(fb, epoch, forceExchange = true)
    }

    override suspend fun refreshProfile() {
        val epoch = session.epoch
        _profile.value = ProfileState.Loading
        val outcome = session.fetchProfile()
        // Anything that finished after a logout / account change belongs to the previous user: drop it.
        if (session.epoch != epoch || _gate.value !is AuthGate.Authenticated) return
        _profile.value = when (outcome) {
            is Authenticated.Ok -> ProfileState.Loaded(outcome.value)
            is Authenticated.ApiFailure -> ProfileState.Failed(outcome.error)
            is Authenticated.Session -> ProfileState.Failed(null)
            Authenticated.Stale -> return
        }
    }

    // ---------------------------------------------------------------------------- account linking

    override suspend fun linkAccount(webPassword: String, consent: Boolean) =
        linking { linker.link(webPassword, consent) }

    override suspend fun reauthenticateWithGoogle(googleIdToken: String) =
        linking { linker.reauthenticateWithGoogle(googleIdToken) }

    override suspend fun reauthenticateWithPassword(firebasePassword: String) =
        linking { linker.reauthenticateWithPassword(firebasePassword) }

    override fun cancelLinking() {
        signOutEverywhere() // also resets the linker (challenge, state); nothing is deleted anywhere
    }

    /** Runs a linking operation only in the restricted state, and publishes its outcome only for the same user. */
    private suspend fun linking(op: suspend () -> LinkOutcome) {
        val gate = _gate.value as? AuthGate.LinkRequired ?: return
        when (val outcome = op()) {
            is LinkOutcome.Authenticated -> {
                // The session was adopted under the same epoch + Firebase uid; re-check the gate was not left meanwhile.
                val still = _gate.value as? AuthGate.LinkRequired
                if (still == null || still.user.uid != gate.user.uid || outcome.session.firebaseUid != gate.user.uid) return
                _backend.value = BackendStatus.Ok
                _notice.value = null
                setGate(AuthGate.Authenticated(gate.user, outcome.session.user.id))
                scope.launch { refreshProfile() }
            }
            is LinkOutcome.SignOut -> {
                if ((_gate.value as? AuthGate.LinkRequired)?.user?.uid != gate.user.uid) return
                signOutEverywhere()
                _notice.value = outcome.message
            }
            LinkOutcome.Stay, LinkOutcome.Busy, LinkOutcome.Stale -> Unit
        }
    }

    // ---------------------------------------------------------------------------- logout

    override fun logout() {
        signOutEverywhere()
    }

    private fun signOutEverywhere() {
        session.clear() // invalidates the cached API token and any in-flight work first
        wipeLocalState()
        runCatching { gateway.signOut() }
        runCatching { google.signOut() }
    }

    private fun wipeLocalState() {
        session.clear()
        linker.reset()
        _profile.value = ProfileState.Idle
        _backend.value = BackendStatus.Ok
        setGate(AuthGate.SignedOut)
    }

    private fun setGate(gate: AuthGate) {
        _gate.value = gate
        _authUser.value = (gate as? AuthGate.Authenticated)?.user
    }

    private fun signedInUser(gate: AuthGate): AuthUser? = when (gate) {
        AuthGate.SignedOut -> null
        is AuthGate.Verifying -> gate.user
        is AuthGate.Authenticated -> gate.user
        is AuthGate.BackendUnavailable -> gate.user
        is AuthGate.LinkRequired -> gate.user
    }

    private fun FirebaseSession.toAuthUser() = AuthUser(uid, displayName, email)

    private fun messageFor(kind: AuthFailureKind) = when (kind) {
        AuthFailureKind.InvalidCredentials, AuthFailureKind.UserNotFound -> "Incorrect email or password."
        AuthFailureKind.WeakPassword -> "Please choose a stronger password (at least 6 characters)."
        AuthFailureKind.EmailAlreadyInUse -> "An account with this email already exists. Try logging in instead."
        AuthFailureKind.UserDisabled -> "This account has been disabled. Please contact support."
        AuthFailureKind.TooManyRequests -> "Too many attempts. Please wait a few minutes and try again."
        AuthFailureKind.Network -> "No internet connection. Please check your connection and try again."
        AuthFailureKind.Unknown -> "Something went wrong. Please try again."
        AuthFailureKind.UserMismatch -> "That is a different account. Please use the account you signed in with."
    }

    private companion object {
        const val VERIFICATION_COOLDOWN_MS = 60_000L
    }
}

/** Shown while Firebase is signed in but the MantraAI backend is unreachable. */
fun unavailableMessage(reason: TemporaryReason): String = when (reason) {
    TemporaryReason.Offline -> "You appear to be offline. Check your connection and retry."
    TemporaryReason.Timeout -> "Your account service is taking too long to respond. Please retry."
    TemporaryReason.RateLimited -> "Too many requests. Please wait a moment and retry."
    TemporaryReason.ServiceUnavailable -> "Your account service is temporarily unavailable. Please retry."
    TemporaryReason.NotConfigured -> "Account sync isn't set up in this build."
}

/** Notices shown for backend refusals. They never reveal anything about another account. */
fun blockedMessage(reason: BlockReason): String = when (reason) {
    BlockReason.EmailNotVerified -> "Please verify your email address and try again."
    BlockReason.UnsupportedSignInMethod -> "This sign-in method isn't supported yet. Please use Google or email and password."
    BlockReason.IdentityDisabled -> "This account can't sign in right now. Please contact support."
    BlockReason.AccountLinkRequired ->
        "An account with this email already exists on MantraAI. Link it securely with your MantraAI password to continue. " +
            "Your existing account was not changed."
}
