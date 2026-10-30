package com.example.mindhaven.data.session

import com.example.mindhaven.data.auth.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Compatibility for existing screens; no Firebase or Google service is used. */
class ScreenAuthAdapter(private val backend: BackendAuthRepository, private val scope: CoroutineScope) : AuthRepository {
    private val gate = MutableStateFlow<AuthGate>(AuthGate.SignedOut)
    private val user = MutableStateFlow<AuthUser?>(null)
    private val status = MutableStateFlow<BackendStatus>(BackendStatus.Ok)
    private val profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    private val notice = MutableStateFlow<String?>(null)
    private val links = MutableStateFlow<LinkState>(LinkState.Idle)
    override val authGate: StateFlow<AuthGate> = gate
    override val authUser: StateFlow<AuthUser?> = user
    override val backendStatus: StateFlow<BackendStatus> = status
    override val profile: StateFlow<ProfileState> = profileState
    override val restoreNotice: StateFlow<String?> = notice
    override val linkState: StateFlow<LinkState> = links
    init { scope.launch { backend.state.collect { state ->
        when(state) {
            is SessionState.LoggedIn -> { val u = AuthUser(state.userId,state.name,null); user.value=u;gate.value=AuthGate.Authenticated(u,state.userId);status.value=BackendStatus.Ok }
            SessionState.Connecting -> { user.value=null; gate.value=AuthGate.Verifying(AuthUser("",null,null));status.value=BackendStatus.Connecting }
            is SessionState.ServiceBusy -> { user.value=null;gate.value=AuthGate.BackendUnavailable(AuthUser("",null,null),TemporaryReason.ServiceUnavailable,state.retryAfterSeconds);status.value=BackendStatus.Unavailable(TemporaryReason.ServiceUnavailable,state.retryAfterSeconds) }
            SessionState.Offline -> { user.value=null;gate.value=AuthGate.BackendUnavailable(AuthUser("",null,null),TemporaryReason.Offline,null);status.value=BackendStatus.Unavailable(TemporaryReason.Offline,null) }
            else -> { user.value=null;gate.value=AuthGate.SignedOut;status.value=BackendStatus.Ok }
        }
    } } }
    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome = when(val r=backend.login(email,password)) {
        is AuthResult.Success -> AuthOutcome.SignedIn(AuthUser((backend.state.value as SessionState.LoggedIn).userId,null,null))
        AuthResult.NeedsVerification -> AuthOutcome.VerificationRequired(false,"Please verify your email before signing in.")
        is AuthResult.ServiceBusy -> AuthOutcome.Failed("Service busy. Please retry.")
        AuthResult.Offline -> AuthOutcome.Failed("Unable to connect. Please retry.")
        else -> AuthOutcome.Failed("Invalid email or password.")
    }
    override suspend fun signUpWithEmail(email: String, password: String): AuthOutcome = when(backend.register(email,password,"")) {
        is AuthResult.Success -> AuthOutcome.VerificationRequired(true,"If eligible, a verification code will be sent.")
        is AuthResult.ServiceBusy -> AuthOutcome.Failed("Service busy. Please retry.")
        AuthResult.Offline -> AuthOutcome.Failed("Unable to connect. Please retry.")
        else -> AuthOutcome.Failed("Unable to process the request.")
    }
    override suspend fun retryBackend(): AuthOutcome? { backend.restore(); return null }
    override suspend fun refreshProfile() { backend.me() }
    override fun logout() { scope.launch { backend.logout() } }
    override suspend fun signInWithGoogle(googleIdToken: String): AuthOutcome = AuthOutcome.Failed("This sign-in method is unavailable.")
    override suspend fun linkAccount(webPassword: String, consent: Boolean) = Unit
    override suspend fun reauthenticateWithGoogle(googleIdToken: String) = Unit
    override suspend fun reauthenticateWithPassword(firebasePassword: String) = Unit
    override fun cancelLinking() = Unit
}
