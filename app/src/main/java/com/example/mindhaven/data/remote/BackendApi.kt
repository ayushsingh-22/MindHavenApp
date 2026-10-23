package com.example.mindhaven.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** Why a call failed. Deliberately contains NO response body, URL, header or token text. */
enum class FailureKind {
    /** The server answered with a non-2xx status. */
    Http,
    /** DNS failure / no route / connection refused. */
    Offline,
    Timeout,
    /** TLS or other transport problem. */
    Network,
    /** 2xx but the body did not match the contract. */
    BadResponse,
    /** No usable base URL for this build (blank, or cleartext not allowed). Nothing was sent. */
    NotConfigured
}

data class ApiError(
    val kind: FailureKind,
    val httpStatus: Int? = null,
    /** Backend `code` (see [ApiCodes]) when the error body had one. */
    val code: String? = null,
    /** Parsed Retry-After in seconds, when the server sent one. */
    val retryAfterSeconds: Long? = null
) {
    val isUnauthenticated: Boolean get() = httpStatus == 401 && (code == null || code == ApiCodes.UNAUTHENTICATED)
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** The backend calls MindHaven needs (Phase 2B.4 exchange/me, Phase 2B.5B.3 linking). Implementations must never log tokens or bodies. */
interface BackendApi {
    /** POST /api/auth/firebase/exchange with ONLY {"idToken": ...}. */
    suspend fun exchange(firebaseIdToken: String): ApiResult<ExchangeResponse>

    /** GET /api/identity/me with Authorization: Bearer <API token>. No user identifier is ever sent. */
    suspend fun me(apiToken: String): ApiResult<IdentityProfile>

    /** POST /api/auth/firebase/link/start with ONLY {"idToken","password","consent":true}. Public (no API token). */
    suspend fun linkStart(firebaseIdToken: String, webPassword: String): ApiResult<LinkStartResponse>

    /** POST /api/auth/firebase/link/complete with ONLY {"idToken","challenge","consent":true}. Public (no API token). */
    suspend fun linkComplete(firebaseIdToken: String, challenge: String): ApiResult<LinkCompleteResponse>
}

/** Shape of a link/start challenge: base64url of 32 random bytes (backend CHALLENGE_RE). */
internal val LINK_CHALLENGE_RE = Regex("^[A-Za-z0-9_-]{43}$")

/**
 * OkHttp implementation. [baseUrl] comes from BuildConfig (debug: dev backend / emulator host, release: explicit HTTPS
 * or empty). Cleartext http:// is honoured ONLY when [allowCleartext] is true (debug builds); in every other case the
 * call fails with [FailureKind.NotConfigured] without touching the network.
 */
class OkHttpBackendApi(
    baseUrl: String,
    private val callFactory: Call.Factory,
    private val allowCleartext: Boolean,
    private val json: kotlinx.serialization.json.Json = ApiJson
) : BackendApi {

    private val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()?.takeIf { it.isHttps || allowCleartext }

    override suspend fun exchange(firebaseIdToken: String): ApiResult<ExchangeResponse> {
        val url = base?.newBuilder()?.addPathSegments("api/auth/firebase/exchange")?.build()
            ?: return notConfigured()
        val body = json.encodeToString(ExchangeRequest.serializer(), ExchangeRequest(firebaseIdToken))
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        return execute(request) { text ->
            val parsed = runCatching { json.decodeFromString(ExchangeResponse.serializer(), text) }.getOrNull()
            if (parsed != null && parsed.success && parsed.token.isNotEmpty() && parsed.tokenType.equals("Bearer", true) &&
                parsed.user?.id?.isNotEmpty() == true && parsed.expiresAt > 0
            ) parsed else null
        }
    }

    override suspend fun me(apiToken: String): ApiResult<IdentityProfile> {
        val url = base?.newBuilder()?.addPathSegments("api/identity/me")?.build() ?: return notConfigured()
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $apiToken")
            .build()
        return execute(request) { text ->
            val parsed = runCatching { json.decodeFromString(IdentityMeResponse.serializer(), text) }.getOrNull()
            parsed?.takeIf { it.success }?.user?.takeIf { it.id.isNotEmpty() }
        }
    }

    override suspend fun linkStart(firebaseIdToken: String, webPassword: String): ApiResult<LinkStartResponse> {
        val url = base?.newBuilder()?.addPathSegments("api/auth/firebase/link/start")?.build() ?: return notConfigured()
        val body = json.encodeToString(LinkStartRequest.serializer(), LinkStartRequest(firebaseIdToken, webPassword, consent = true))
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        return execute(request) { text ->
            val parsed = runCatching { json.decodeFromString(LinkStartResponse.serializer(), text) }.getOrNull()
            parsed?.takeIf { it.success && LINK_CHALLENGE_RE.matches(it.challenge) && it.expiresIn in 1..MAX_CHALLENGE_TTL_SECONDS && it.expiresAt > 0 }
        }
    }

    override suspend fun linkComplete(firebaseIdToken: String, challenge: String): ApiResult<LinkCompleteResponse> {
        val url = base?.newBuilder()?.addPathSegments("api/auth/firebase/link/complete")?.build() ?: return notConfigured()
        val body = json.encodeToString(LinkCompleteRequest.serializer(), LinkCompleteRequest(firebaseIdToken, challenge, consent = true))
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        return execute(request) { text ->
            val parsed = runCatching { json.decodeFromString(LinkCompleteResponse.serializer(), text) }.getOrNull()
            parsed?.takeIf {
                it.success && it.linked && it.token.isNotEmpty() && it.tokenType.equals("Bearer", true) &&
                    it.user?.id?.isNotEmpty() == true && it.expiresAt > 0
            }
        }
    }

    private fun notConfigured() = ApiResult.Failure(ApiError(FailureKind.NotConfigured))

    private suspend fun <T> execute(request: Request, parse: (String) -> T?): ApiResult<T> {
        val response = try {
            callFactory.newCall(request).await()
        } catch (e: IOException) {
            return ApiResult.Failure(ApiError(classify(e)))
        }
        return response.use { r ->
            val text = try { r.peekBody(MAX_BODY_BYTES).string() } catch (e: IOException) { return ApiResult.Failure(ApiError(classify(e))) }
            if (r.isSuccessful) {
                val value = parse(text)
                if (value != null) ApiResult.Success(value)
                else ApiResult.Failure(ApiError(FailureKind.BadResponse, httpStatus = r.code))
            } else {
                ApiResult.Failure(
                    ApiError(
                        kind = FailureKind.Http,
                        httpStatus = r.code,
                        code = runCatching { json.decodeFromString(ErrorBody.serializer(), text).code }.getOrNull()?.takeIf { it.length <= 64 },
                        retryAfterSeconds = r.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
                    )
                )
            }
        }
    }

    private fun classify(e: IOException): FailureKind = when (e) {
        is UnknownHostException, is ConnectException -> FailureKind.Offline
        is InterruptedIOException -> FailureKind.Timeout // OkHttp reports its timeouts as (Socket)TimeoutException/InterruptedIOException
        is SSLException -> FailureKind.Network
        else -> FailureKind.Network
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_BODY_BYTES = 64L * 1024
        const val MAX_CHALLENGE_TTL_SECONDS = 300L
    }
}

/** Cancellation-aware bridge: cancelling the coroutine cancels the in-flight HTTP call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
