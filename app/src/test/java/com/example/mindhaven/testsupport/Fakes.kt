package com.example.mindhaven.testsupport

import com.example.mindhaven.data.auth.AuthFailure
import com.example.mindhaven.data.auth.AuthFailureKind
import com.example.mindhaven.data.auth.FirebaseAuthGateway
import com.example.mindhaven.data.auth.FirebaseIdToken
import com.example.mindhaven.data.auth.FirebaseSession
import com.example.mindhaven.data.auth.GoogleSignOutHandle
import com.example.mindhaven.data.auth.NotSignedIn
import com.example.mindhaven.data.remote.ApiError
import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.ApiUser
import com.example.mindhaven.data.remote.BackendApi
import com.example.mindhaven.data.remote.ExchangeResponse
import com.example.mindhaven.data.remote.FailureKind
import com.example.mindhaven.data.remote.IdentityProfile
import com.example.mindhaven.data.remote.LinkCompleteResponse
import com.example.mindhaven.data.remote.LinkStartResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic Firebase user. */
fun fbUser(uid: String = "fb-uid-1", verified: Boolean = true, providers: List<String> = listOf("password"), email: String? = "$uid@example.invalid") =
    FirebaseSession(uid = uid, email = email, displayName = "Name $uid", isEmailVerified = verified, providerIds = providers)

/** Scriptable stand-in for the Firebase Auth SDK. Contains no real credentials and never touches the network. */
class FakeGateway(initial: FirebaseSession? = null) : FirebaseAuthGateway {
    private val state = MutableStateFlow(initial)
    override val currentUser: FirebaseSession? get() = state.value

    var idTokenCalls = mutableListOf<Boolean>() // forceRefresh flag of each call
    var idTokenFailure: Throwable? = null
    var signOutCount = 0
    var verificationMails = 0
    var reloadCount = 0
    /** What reload() reports (null = keep current). */
    var reloadAs: FirebaseSession? = null
    var signInResult: FirebaseSession? = null
    var signInFailure: AuthFailure? = null
    var createResult: FirebaseSession? = null
    var googleResult: FirebaseSession? = null
    var sendMailFailure: AuthFailure? = null
    /** auth_time (epoch seconds) reported with every ID token; null = not reported. */
    var authTimeSeconds: Long? = null
    /** Moves auth_time to this value on a successful re-authentication (models Firebase). */
    var reauthAuthTimeSeconds: Long? = null
    var reauthFailure: AuthFailure? = null
    val reauthCalls = mutableListOf<String>() // "google" / "password" only: never the credential

    fun setUser(user: FirebaseSession?) { state.value = user }
    override fun authStates(): Flow<FirebaseSession?> = state

    override suspend fun signInWithEmail(email: String, password: String): FirebaseSession {
        signInFailure?.let { throw it }
        return (signInResult ?: fbUser()).also { state.value = it }
    }

    override suspend fun createWithEmail(email: String, password: String): FirebaseSession =
        (createResult ?: fbUser(verified = false)).also { state.value = it }

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): FirebaseSession =
        (googleResult ?: fbUser(providers = listOf("google.com"))).also { state.value = it }

    override suspend fun idToken(forceRefresh: Boolean): String {
        idTokenCalls += forceRefresh
        idTokenFailure?.let { throw it }
        val user = state.value ?: throw NotSignedIn()
        return "firebase-id-token-for-${user.uid}-${idTokenCalls.size}"
    }

    override suspend fun idTokenWithAuthTime(forceRefresh: Boolean): FirebaseIdToken =
        FirebaseIdToken(idToken(forceRefresh), authTimeSeconds)

    override suspend fun reauthenticateWithGoogle(googleIdToken: String) = reauth("google")
    override suspend fun reauthenticateWithPassword(password: String) = reauth("password")
    private fun reauth(kind: String) {
        state.value ?: throw NotSignedIn()
        reauthCalls += kind
        reauthFailure?.let { throw it }
        reauthAuthTimeSeconds?.let { authTimeSeconds = it }
    }

    override suspend fun reload(): FirebaseSession? {
        reloadCount += 1
        reloadAs?.let { state.value = it }
        return state.value
    }

    override suspend fun sendEmailVerification() {
        sendMailFailure?.let { throw it }
        verificationMails += 1
    }

    override fun signOut() { signOutCount += 1; state.value = null }
}

class FakeGoogle : GoogleSignOutHandle {
    var signOuts = 0
    override fun signOut() { signOuts += 1 }
}

/** Scriptable backend. [exchangeScript] answers calls in order; the last entry repeats. */
class FakeApi(
    var exchangeScript: List<ApiResult<ExchangeResponse>> = listOf(ok()),
    var meScript: List<ApiResult<IdentityProfile>> = listOf(ApiResult.Success(profile("neon-user-1"))),
    /** Virtual latency so that concurrent callers really overlap. */
    var latencyMs: Long = 0
) : BackendApi {
    val exchangeTokens = mutableListOf<String>()
    /** link/start answers in order (last repeats). Default: a valid challenge. */
    var linkStartScript: List<ApiResult<LinkStartResponse>> = listOf(linkStarted())
    /** link/complete answers in order (last repeats). Default: linked to neon-user-1. */
    var linkCompleteScript: List<ApiResult<LinkCompleteResponse>> = listOf(linked())
    /** Hook run when a call starts (e.g. to switch the Firebase user mid-flight). */
    var onLinkCall: ((String) -> Unit)? = null
    val linkStartTokens = mutableListOf<String>()
    val linkStartPasswords = mutableListOf<String>()
    val linkCompleteTokens = mutableListOf<String>()
    val linkCompleteChallenges = mutableListOf<String>()
    var linkStartCalls = 0
    var linkCompleteCalls = 0
    /** Calls in flight right now / maximum ever observed (proves serialisation). */
    var linkInFlight = 0
    var maxLinkInFlight = 0
    val meTokens = mutableListOf<String>()
    var exchangeCalls = 0
    var meCalls = 0
    var tokenSerial = 0

    override suspend fun exchange(firebaseIdToken: String): ApiResult<ExchangeResponse> {
        exchangeTokens += firebaseIdToken
        val i = exchangeCalls++
        if (latencyMs > 0) delay(latencyMs)
        val scripted = exchangeScript[minOf(i, exchangeScript.lastIndex)]
        // Give every success its own token so reuse of an old token is detectable.
        return if (scripted is ApiResult.Success) ApiResult.Success(scripted.value.copy(token = "api-token-${++tokenSerial}")) else scripted
    }

    override suspend fun me(apiToken: String): ApiResult<IdentityProfile> {
        meTokens += apiToken
        val i = meCalls++
        if (latencyMs > 0) delay(latencyMs)
        return meScript[minOf(i, meScript.lastIndex)]
    }

    override suspend fun linkStart(firebaseIdToken: String, webPassword: String): ApiResult<LinkStartResponse> {
        linkStartTokens += firebaseIdToken; linkStartPasswords += webPassword
        val i = linkStartCalls++
        return inFlight("start") { linkStartScript[minOf(i, linkStartScript.lastIndex)] }
    }

    override suspend fun linkComplete(firebaseIdToken: String, challenge: String): ApiResult<LinkCompleteResponse> {
        linkCompleteTokens += firebaseIdToken; linkCompleteChallenges += challenge
        val i = linkCompleteCalls++
        return inFlight("complete") {
            val scripted = linkCompleteScript[minOf(i, linkCompleteScript.lastIndex)]
            if (scripted is ApiResult.Success) ApiResult.Success(scripted.value.copy(token = "api-token-${++tokenSerial}")) else scripted
        }
    }

    private suspend fun <T> inFlight(name: String, block: () -> T): T {
        linkInFlight += 1; maxLinkInFlight = maxOf(maxLinkInFlight, linkInFlight)
        try {
            onLinkCall?.invoke(name)
            if (latencyMs > 0) delay(latencyMs)
            return block()
        } finally { linkInFlight -= 1 }
    }

    companion object {
        /** A syntactically valid (43 base64url chars) synthetic challenge. */
        const val CHALLENGE = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE"
        fun linkStarted(challenge: String = CHALLENGE, expiresIn: Long = 300) =
            ApiResult.Success(LinkStartResponse(true, challenge, 4_000_000_000_000, expiresIn))
        fun linked(userId: String = "neon-user-1") = ApiResult.Success(
            LinkCompleteResponse(true, "placeholder", "Bearer", 4_000_000_000_000, 300, true, ApiUser(userId, "u@example.invalid", "N", null))
        )
        fun ok(userId: String = "neon-user-1", expiresIn: Long = 300, provisioned: Boolean = false) = ApiResult.Success(
            ExchangeResponse(true, "placeholder", "Bearer", 4_000_000_000_000, expiresIn, provisioned, ApiUser(userId, "u@example.invalid", "N", null))
        )
        fun http(status: Int, code: String? = null, retryAfter: Long? = null) =
            ApiResult.Failure(ApiError(FailureKind.Http, status, code, retryAfter))
        fun net(kind: FailureKind) = ApiResult.Failure(ApiError(kind))
        fun profile(id: String) = IdentityProfile(id = id, email = "u@example.invalid")
    }
}
