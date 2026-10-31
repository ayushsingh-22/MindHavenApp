package com.example.mindhaven.data.session

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BackendAuthNetworkTest {
    private class MemoryStore : TokenStore {
        @Volatile var pair: TokenPair? = TokenPair("expired", "refresh", "later", "later")
        override suspend fun read() = pair
        override suspend fun write(tokens: TokenPair) { pair = tokens }
        override suspend fun clear() { pair = null }
    }
    @Test fun `encrypted token envelope round trip and tampering rejection`() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val pair = TokenPair("access", "refresh", "expiry", "family-expiry")
        val encoded = TokenEnvelope.seal(pair, key)
        assertFalse(encoded.contains(pair.accessToken)); assertFalse(encoded.contains(pair.refreshToken))
        assertEquals(pair, TokenEnvelope.open(encoded, key))
        val bytes = java.util.Base64.getDecoder().decode(encoded); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { TokenEnvelope.open(java.util.Base64.getEncoder().encodeToString(bytes), key) }.isFailure)
    }
    @Test fun `five concurrent failures share one rotation`() {
        val server = MockWebServer(); val rotations = AtomicInteger()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path == "/api/auth/refresh") { rotations.incrementAndGet(); Thread.sleep(150); return MockResponse().setBody("""{"accessToken":"new","refreshToken":"child","accessExpiresAt":"later","refreshExpiresAt":"later"}""") }
            return if (request.getHeader("Authorization") == "Bearer new") MockResponse().setResponseCode(200) else MockResponse().setResponseCode(401)
        }}
        server.start(); try {
            val store = MemoryStore(); val network = BackendAuthNetwork(server.url("/"), store)
            val client = OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build()
            val pool = Executors.newFixedThreadPool(5)
            try { val futures = (1..5).map { pool.submit<Int> { client.newCall(Request.Builder().url(server.url("/api/profile")).build()).execute().use { it.code } } }; assertTrue(futures.all { it.get() == 200 }); assertEquals(1, rotations.get()) } finally { pool.shutdownNow() }
        } finally { server.shutdown() }
    }
    @Test fun `failed refresh clears tokens and never loops`() {
        val server=MockWebServer(); val rotations=AtomicInteger(); server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse { if(request.path=="/api/auth/refresh") rotations.incrementAndGet(); return MockResponse().setResponseCode(401) }}; server.start()
        try { val store=MemoryStore();val network=BackendAuthNetwork(server.url("/"),store);val client=OkHttpClient.Builder().addInterceptor(network.interceptor).authenticator(network.authenticator).build();client.newCall(Request.Builder().url(server.url("/api/profile")).build()).execute().use { assertEquals(401,it.code) };assertEquals(1,rotations.get());assertNull(runBlocking { store.read() });assertTrue(network.sessionInvalidated.value) } finally { server.shutdown() }
    }
    @Test fun `503 reports busy with retry hint`() {
        val server=MockWebServer(); server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "3"));server.start()
        try { val network=BackendAuthNetwork(server.url("/"),MemoryStore());val client=OkHttpClient.Builder().addInterceptor(network.interceptor).build();client.newCall(Request.Builder().url(server.url("/api/profile")).build()).execute().close();assertEquals(ConnectionState.ServiceBusy(3),network.state.value) } finally { server.shutdown() }
    }
}
