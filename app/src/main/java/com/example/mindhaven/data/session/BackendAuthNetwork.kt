package com.example.mindhaven.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class ServiceBusy(val retryAfterSeconds: Long?) : ConnectionState
}

/** Restrict credentials to the exact configured scheme, host, and port. */
class BackendAuthNetwork(private val base: HttpUrl, private val store: TokenStore) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = mutableState
    private val mutableSessionInvalidated = MutableStateFlow(false)
    val sessionInvalidated: StateFlow<Boolean> = mutableSessionInvalidated
    private val active = AtomicInteger()
    private val refreshClient = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private fun belongs(url: HttpUrl) = url.scheme == base.scheme && url.host == base.host && url.port == base.port && url.encodedPath.startsWith("/api/")
    private fun publicAuth(url: HttpUrl) = url.encodedPath in setOf("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password")
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val token = if (belongs(request.url) && !publicAuth(request.url)) runBlocking { store.read()?.accessToken } else null
        if (belongs(request.url) && !publicAuth(request.url) && token == null) mutableSessionInvalidated.value = true
        val outgoing = if (token != null) request.newBuilder().header("Authorization", "Bearer $token").build() else request
        if (active.incrementAndGet() == 1) mutableState.value = ConnectionState.Connecting
        try {
            val response = chain.proceed(outgoing)
            if (response.code == 503) mutableState.value = ConnectionState.ServiceBusy(response.header("Retry-After")?.toLongOrNull())
            response
        } finally {
            if (active.decrementAndGet() == 0 && mutableState.value is ConnectionState.Connecting) mutableState.value = ConnectionState.Idle
        }
    }
    suspend fun establishSession(pair: TokenPair) = mutex.withLock { store.write(pair); mutableSessionInvalidated.value = false }
    suspend fun clearSession() = mutex.withLock { store.clear(); mutableSessionInvalidated.value = true }
    suspend fun refresh(): Boolean = mutex.withLock {
        val current = store.read() ?: run { mutableSessionInvalidated.value = true; return@withLock false }
        rotate(current) != null
    }
    private suspend fun rotate(current: TokenPair): TokenPair? {
        val body = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
            put("refreshToken", kotlinx.serialization.json.JsonPrimitive(current.refreshToken))
        }).toRequestBody("application/json".toMediaType())
        val refresh = Request.Builder().url(base.newBuilder().encodedPath("/api/auth/refresh").build()).post(body).build()
        val pair = try {
            refreshClient.newCall(refresh).execute().use { result ->
                if (result.code == 503) mutableState.value = ConnectionState.ServiceBusy(result.header("Retry-After")?.toLongOrNull())
                if (result.isSuccessful) json.decodeFromString<TokenPair>(result.body?.string() ?: "") else null
            }
        } catch (_: Exception) { null }
        if (pair == null) { store.clear(); mutableSessionInvalidated.value = true; return null }
        try { store.write(pair) } catch (_: Exception) { store.clear(); mutableSessionInvalidated.value = true; return null }
        return pair
    }
    val authenticator = Authenticator { _: Route?, response: Response ->
        val request = response.request
        if (!belongs(request.url) || publicAuth(request.url) || response.priorResponse != null) return@Authenticator null
        val failed = request.header("Authorization")?.removePrefix("Bearer ") ?: return@Authenticator null
        runBlocking {
            mutex.withLock {
                val current = store.read() ?: run { mutableSessionInvalidated.value = true; return@withLock null }
                if (current.accessToken != failed) return@withLock request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
                val pair = rotate(current) ?: return@withLock null
                request.newBuilder().header("Authorization", "Bearer ${pair.accessToken}").build()
            }
        }
    }
}
