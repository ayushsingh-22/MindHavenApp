package com.example.mindhaven.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

sealed interface SessionState {
    data object LoggedOut : SessionState
    data object Connecting : SessionState
    data class LoggedIn(val userId: String, val name: String?) : SessionState
    data object NeedsVerification : SessionState
    data class ServiceBusy(val retryAfterSeconds: Long?) : SessionState
    data object Offline : SessionState
}
sealed interface AuthResult {
    data class Success(val data: JsonObject = JsonObject(emptyMap())) : AuthResult { override fun toString() = "Success(redacted)" }
    data object Invalid : AuthResult
    data object NeedsVerification : AuthResult
    data class ServiceBusy(val retryAfterSeconds: Long?) : AuthResult
    data object Offline : AuthResult
}

/** Backend-owned auth. No request bodies, bearer credentials or responses are logged. */
class BackendAuthRepository(
    baseUrl: String, private val store: TokenStore, private val network: BackendAuthNetwork,
    private val client: OkHttpClient, private val scope: CoroutineScope
) : AuthActions {
    private val base = baseUrl.trimEnd('/').toHttpUrlOrNull()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Connecting)
    val state: StateFlow<SessionState> = mutableState
    init {
        scope.launch {
            network.sessionInvalidated.collect { if (it) mutableState.value = SessionState.LoggedOut }
        }
        scope.launch { if (store.read() == null) mutableState.value = SessionState.LoggedOut else restore() }
    }
    private suspend fun call(path: String, method: String, fields: Map<String, String> = emptyMap()): AuthResult = withContext(Dispatchers.IO) {
        val root = base ?: return@withContext AuthResult.Offline
        val builder = Request.Builder().url(root.newBuilder().encodedPath("/api/auth/$path").build())
        val payload = buildJsonObject { fields.forEach { (k, v) -> put(k, v) } }.toString().toRequestBody("application/json".toMediaType())
        val request = when(method) { "GET" -> builder.get(); "DELETE" -> builder.delete(payload); else -> builder.post(payload) }.build()
        try { client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {
                    val text = response.body?.string().orEmpty()
                    val result = runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                    AuthResult.Success(result)
                }
                response.code == 503 -> { val retry = response.header("Retry-After")?.toLongOrNull(); mutableState.value = SessionState.ServiceBusy(retry); AuthResult.ServiceBusy(retry) }
                path == "login" && response.code == 403 -> AuthResult.NeedsVerification
                else -> AuthResult.Invalid
            }
        } } catch (_: IOException) { mutableState.value = SessionState.Offline; AuthResult.Offline }
    }
    private fun body(vararg fields: Pair<String,String>) = mapOf(*fields)
    override suspend fun register(email: String, password: String, name: String): AuthResult {
        val result = call("register", "POST", body("email" to email, "password" to password, "name" to name))
        if (result is AuthResult.Success) mutableState.value = SessionState.NeedsVerification
        return result
    }
    override suspend fun verifyEmail(email: String, otp: String) = call("verify-email", "POST", body("email" to email, "otp" to otp))
    override suspend fun resendVerification(email: String) = call("resend-verification", "POST", body("email" to email))
    override suspend fun forgotPassword(email: String) = call("forgot-password", "POST", body("email" to email))
    override suspend fun resetPassword(email: String, otp: String, password: String) = call("reset-password", "POST", body("email" to email, "otp" to otp, "password" to password))
    suspend fun me() = call("me", "GET")
    suspend fun refresh(): AuthResult {
        if (withContext(Dispatchers.IO) { network.refresh() }) return AuthResult.Success()
        val state = network.state.value
        return if (state is ConnectionState.ServiceBusy) AuthResult.ServiceBusy(state.retryAfterSeconds) else AuthResult.Invalid
    }
    override suspend fun login(email: String, password: String): AuthResult {
        mutableState.value = SessionState.Connecting
        val result = call("login", "POST", body("email" to email, "password" to password))
        when(result) {
            is AuthResult.Success -> {
                val pair = runCatching { json.decodeFromJsonElement<TokenPair>(result.data) }.getOrNull()
                val user = result.data["user"]?.jsonObject
                val id = user?.get("id")?.jsonPrimitive?.contentOrNull
                if (pair == null || id.isNullOrEmpty()) { mutableState.value = SessionState.LoggedOut; return AuthResult.Invalid }
                try { network.establishSession(pair) } catch (_: Exception) { network.clearSession(); mutableState.value = SessionState.LoggedOut; return AuthResult.Invalid }
                mutableState.value = SessionState.LoggedIn(id, user["name"]?.jsonPrimitive?.contentOrNull)
            }
            AuthResult.NeedsVerification -> mutableState.value = SessionState.NeedsVerification
            is AuthResult.ServiceBusy -> mutableState.value = SessionState.ServiceBusy(result.retryAfterSeconds)
            AuthResult.Offline -> mutableState.value = SessionState.Offline
            else -> mutableState.value = SessionState.LoggedOut
        }
        return result
    }
    suspend fun restore() {
        mutableState.value = SessionState.Connecting
        when(val result = me()) {
            is AuthResult.Success -> {
                val user = result.data["user"]?.jsonObject; val id = user?.get("id")?.jsonPrimitive?.contentOrNull
                if (id.isNullOrEmpty()) { network.clearSession(); mutableState.value = SessionState.LoggedOut }
                else mutableState.value = SessionState.LoggedIn(id, user["fullName"]?.jsonPrimitive?.contentOrNull)
            }
            is AuthResult.ServiceBusy -> mutableState.value = SessionState.ServiceBusy(result.retryAfterSeconds)
            AuthResult.Offline -> mutableState.value = SessionState.Offline
            else -> { store.clear(); mutableState.value = SessionState.LoggedOut }
        }
    }
    override suspend fun logout(): AuthResult { val result=call("logout", "POST"); network.clearSession(); mutableState.value=SessionState.LoggedOut; return result }
    override suspend fun logoutAll(): AuthResult { val result=call("logout-all", "POST"); if (result is AuthResult.Success) { network.clearSession(); mutableState.value=SessionState.LoggedOut }; return result }
    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult { val result=call("change-password", "POST", body("currentPassword" to currentPassword,"newPassword" to newPassword)); if(result is AuthResult.Success){network.clearSession();mutableState.value=SessionState.LoggedOut};return result }
    override suspend fun deleteAccount(password: String): AuthResult { val result=call("account", "DELETE", body("password" to password));if(result is AuthResult.Success){network.clearSession();mutableState.value=SessionState.LoggedOut};return result }
}
