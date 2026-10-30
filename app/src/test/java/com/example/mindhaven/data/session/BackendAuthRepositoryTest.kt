package com.example.mindhaven.data.session

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class BackendAuthRepositoryTest {
    private class Store : TokenStore {
        var pair: TokenPair? = null
        override suspend fun read() = pair
        override suspend fun write(tokens: TokenPair) { pair=tokens }
        override suspend fun clear() { pair=null }
    }
    private suspend fun runCase(name: String, status: Int) {
        val server=MockWebServer();server.start();val store=Store();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val network=BackendAuthNetwork(server.url("/"),store)
            val client=OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build()
            val repository=BackendAuthRepository(server.url("/").toString(),store,network,client,scope)
            delay(20)
            val good = if(name=="login") """{"accessToken":"a","refreshToken":"r","accessExpiresAt":"e","refreshExpiresAt":"e","user":{"id":"u","name":"n"}}""" else "{}"
            server.enqueue(MockResponse().setResponseCode(status).setBody(good).addHeader("Retry-After","3"))
            val result=when(name) {
                "register" -> repository.register("a","b","c")
                "verify-email" -> repository.verifyEmail("a","b")
                "resend-verification" -> repository.resendVerification("a")
                "login" -> repository.login("a","b")
                "logout" -> repository.logout()
                "logout-all" -> repository.logoutAll()
                "forgot-password" -> repository.forgotPassword("a")
                "reset-password" -> repository.resetPassword("a","b","c")
                "change-password" -> repository.changePassword("a","b")
                "me" -> repository.me()
                "account" -> repository.deleteAccount("a")
                else -> error("Unknown operation")
            }
            when(status){ 200 -> assertTrue(result is AuthResult.Success); 503 -> assertEquals(AuthResult.ServiceBusy(3),result); else -> assertEquals(AuthResult.Invalid,result) }
            assertEquals("/api/auth/$name",server.takeRequest().path)
            if(name=="login" && status==200) assertTrue(repository.state.value is SessionState.LoggedIn)
        } finally { scope.cancel(); server.shutdown() }
    }
    @Test fun `all endpoint success and failure mappings`() = runBlocking {
        for (name in listOf("register","verify-email","resend-verification","login","logout","logout-all","forgot-password","reset-password","change-password","me","account")) {
            runCase(name,200);runCase(name,400);runCase(name,503)
        }
    }
    @Test fun `repository refresh rotates once and failed refresh logs out`() = runBlocking {
        val server=MockWebServer();server.start();val store=Store();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val network=BackendAuthNetwork(server.url("/"),store);val client=OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build()
            val repo=BackendAuthRepository(server.url("/").toString(),store,network,client,scope);delay(50)
            store.write(TokenPair("old","old-refresh","expiry","expiry"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"accessToken":"new","refreshToken":"new-refresh","accessExpiresAt":"e","refreshExpiresAt":"e"}"""))
            assertTrue(repo.refresh() is AuthResult.Success);assertEquals("/api/auth/refresh",server.takeRequest().path)
            server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After","4"))
            assertEquals(AuthResult.ServiceBusy(4),repo.refresh());assertNull(store.read())
            withTimeout(2000) { while(repo.state.value != SessionState.LoggedOut) delay(10) }
        } finally {scope.cancel();server.shutdown()}
    }
    @Test fun `network failure reports offline instead of invalid credentials`() = runBlocking {
        val server=MockWebServer();server.start();val url=server.url("/");server.shutdown()
        val store=Store();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try { val network=BackendAuthNetwork(url,store);val client=OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build();val repo=BackendAuthRepository(url.toString(),store,network,client,scope);delay(50);assertEquals(AuthResult.Offline,repo.login("a","b"));assertEquals(SessionState.Offline,repo.state.value) } finally { scope.cancel() }
    }
    @Test fun `correct password unverified maps to verification state`() = runBlocking {
        val server=MockWebServer();server.start(); val store=Store();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try { val network=BackendAuthNetwork(server.url("/"),store);val client=OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build();val repo=BackendAuthRepository(server.url("/").toString(),store,network,client,scope);delay(20);server.enqueue(MockResponse().setResponseCode(403));assertEquals(AuthResult.NeedsVerification,repo.login("a","b"));assertEquals(SessionState.NeedsVerification,repo.state.value) } finally {scope.cancel();server.shutdown()}
    }
}
