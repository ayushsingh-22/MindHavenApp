package com.example.mindhaven.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** HTTP-level tests against a real in-process MockWebServer (no Firebase, no real backend, no network). */
class OkHttpBackendApiTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(400, TimeUnit.MILLISECONDS).connectTimeout(1, TimeUnit.SECONDS).followRedirects(false).build()

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { runCatching { server.shutdown() } }

    private fun api(base: String = server.url("/").toString(), cleartext: Boolean = true) = OkHttpBackendApi(base, client, cleartext)

    private val exchangeJson = """{"success":true,"token":"api.jwt.value","tokenType":"Bearer","expiresAt":4102444800000,"expiresIn":300,
        "provisioned":true,"user":{"id":"neon-1","email":"a@example.invalid","fullName":null,"avatarUrl":null},"futureField":123}"""
    private val meJson = """{"success":true,"user":{"id":"neon-1","email":"a@example.invalid","fullName":"A","avatarUrl":null,"createdAt":"2026-01-01T00:00:00.000Z",
        "emailVerified":true,"hasPassword":false,"identities":[{"provider":"firebase","signInMethod":"google.com","emailVerified":true,"linkMethod":"created",
        "linkedAt":"2026-01-01T00:00:00.000Z","lastLoginAt":null,"disabled":false}]}}"""

    private fun enqueue(status: Int, body: String = "{}", vararg headers: Pair<String, String>) {
        val r = MockResponse().setResponseCode(status).setBody(body).setHeader("Content-Type", "application/json")
        headers.forEach { (k, v) -> r.setHeader(k, v) }
        server.enqueue(r)
    }

    private fun failure(r: ApiResult<*>) = (r as ApiResult.Failure).error

    // ------------------------------------------------------------------ exchange contract

    @Test fun `exchange sends ONLY the idToken as json to the documented path and parses the success contract`() = runBlocking {
        enqueue(200, exchangeJson)
        val result = api().exchange("FIREBASE.ID.TOKEN")
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", req.method)
        assertEquals("/api/auth/firebase/exchange", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        assertNull("exchange is public: no Authorization header", req.getHeader("Authorization"))
        val sent = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("idToken"), sent.keys)
        assertEquals("FIREBASE.ID.TOKEN", sent["idToken"].toString().trim('"'))
        val ok = (result as ApiResult.Success).value
        assertEquals("api.jwt.value", ok.token); assertEquals("Bearer", ok.tokenType)
        assertEquals(300, ok.expiresIn); assertEquals(4102444800000, ok.expiresAt)
        assertTrue(ok.provisioned); assertEquals("neon-1", ok.user!!.id)
    }

    @Test fun `request body stays far below the 8 KB backend limit`() = runBlocking {
        enqueue(200, exchangeJson)
        api().exchange("a".repeat(2000))
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)!!.bodySize < 8 * 1024)
    }

    @Test fun `every documented error status maps to status, code and nothing else`() = runBlocking {
        val cases = listOf(
            Triple(400, "VALIDATION_FAILED", null), Triple(401, "INVALID_FIREBASE_TOKEN", null),
            Triple(403, "EMAIL_NOT_VERIFIED", null), Triple(403, "UNSUPPORTED_SIGN_IN_METHOD", null),
            Triple(403, "IDENTITY_DISABLED", null), Triple(409, "ACCOUNT_LINK_REQUIRED", null),
            Triple(413, null, null), Triple(415, "UNSUPPORTED_MEDIA_TYPE", null),
            Triple(429, "RATE_LIMITED", "7"), Triple(503, "IDENTITY_UNAVAILABLE", null), Triple(404, null, null)
        )
        for ((status, code, retryAfter) in cases) {
            val body = if (code == null) "{}" else """{"success":false,"error":"Sensitive text that must not surface","code":"$code","requestId":"r1"}"""
            if (retryAfter != null) enqueue(status, body, "Retry-After" to retryAfter) else enqueue(status, body)
            val e = failure(api().exchange("t"))
            assertEquals(FailureKind.Http, e.kind); assertEquals(status, e.httpStatus); assertEquals(code, e.code)
            assertEquals(retryAfter?.toLong(), e.retryAfterSeconds)
            assertFalse("error text must not be carried", e.toString().contains("Sensitive"))
        }
    }

    @Test fun `non-json and oversized error bodies do not crash and carry no code`() = runBlocking {
        enqueue(502, "<html>Bad gateway</html>")
        val e = failure(api().exchange("t"))
        assertEquals(502, e.httpStatus); assertNull(e.code)
        enqueue(503, "x".repeat(300_000))
        assertEquals(503, failure(api().exchange("t")).httpStatus)
    }

    @Test fun `Retry-After that is not a number of seconds is ignored`() = runBlocking {
        enqueue(429, """{"code":"RATE_LIMITED"}""", "Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT")
        assertNull(failure(api().exchange("t")).retryAfterSeconds)
    }

    @Test fun `2xx with a body that violates the contract is BadResponse, never a session`() = runBlocking {
        for (body in listOf("""{"success":true}""", """{"success":true,"token":"x","tokenType":"Basic","expiresAt":1,"expiresIn":300,"user":{"id":"u"}}""",
            """{"success":true,"token":"x","tokenType":"Bearer","expiresAt":1,"expiresIn":300}""", "not json", """{"success":false,"token":"x","tokenType":"Bearer","expiresAt":1,"user":{"id":"u"}}""")) {
            enqueue(200, body)
            assertEquals(FailureKind.BadResponse, failure(api().exchange("t")).kind)
        }
    }

    // ------------------------------------------------------------------ transport failures

    @Test fun `server not reachable gives Offline`() = runBlocking {
        val base = server.url("/").toString(); server.shutdown()
        assertEquals(FailureKind.Offline, failure(api(base).exchange("t")).kind)
    }

    @Test fun `unresolvable host gives Offline`() = runBlocking {
        assertEquals(FailureKind.Offline, failure(api("http://definitely-not-a-real-host.invalid").exchange("t")).kind)
    }

    @Test fun `no answer within the read timeout gives Timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(FailureKind.Timeout, failure(api().exchange("t")).kind)
    }

    @Test fun `cancelling the coroutine cancels the HTTP call promptly and surfaces cancellation`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val slow = OkHttpBackendApi(server.url("/").toString(), OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build(), true)
        val job = async { slow.exchange("t") }
        server.takeRequest(2, TimeUnit.SECONDS)
        withTimeout(3000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    // ------------------------------------------------------------------ identity/me

    @Test fun `me sends Bearer token, no userId anywhere, and returns the canonical id from the response`() = runBlocking {
        enqueue(200, meJson)
        val result = api().me("API.JWT")
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", req.method); assertEquals("/api/identity/me", req.path)
        assertEquals("Bearer API.JWT", req.getHeader("Authorization"))
        assertFalse(req.path!!.contains("userId", true) || req.path!!.contains("user_id", true) || req.path!!.contains("?"))
        assertEquals(0L, req.bodySize)
        val p = (result as ApiResult.Success).value
        assertEquals("neon-1", p.id); assertEquals(1, p.identities.size)
        assertEquals("google.com", p.identities[0].signInMethod); assertFalse(p.hasPassword); assertTrue(p.emailVerified)
    }

    @Test fun `me 401 and 403 USER_MISMATCH and 404 are reported precisely`() = runBlocking {
        enqueue(401, """{"success":false,"error":"x","code":"UNAUTHENTICATED"}""")
        val a = failure(api().me("t")); assertTrue(a.isUnauthenticated)
        enqueue(403, """{"success":false,"code":"USER_MISMATCH"}""")
        val b = failure(api().me("t")); assertEquals("USER_MISMATCH", b.code); assertFalse(b.isUnauthenticated)
        enqueue(404, """{"success":false,"code":"USER_NOT_FOUND"}""")
        assertEquals("USER_NOT_FOUND", failure(api().me("t")).code)
    }

    // ------------------------------------------------------------------ configuration and secrecy

    @Test fun `blank base url or cleartext without permission sends nothing`() = runBlocking {
        for (api in listOf(api(base = ""), api(base = "   "), api(base = server.url("/").toString(), cleartext = false), api(base = "not a url"))) {
            assertEquals(FailureKind.NotConfigured, failure(api.exchange("t")).kind)
            assertEquals(FailureKind.NotConfigured, failure(api.me("t")).kind)
        }
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test fun `https is accepted without the cleartext permission (URL validation only)`() {
        // Constructing with https and cleartext=false must keep a usable base (no network call is made here).
        val result = runBlocking { OkHttpBackendApi("https://dev.example.invalid", OkHttpClient.Builder().connectTimeout(200, TimeUnit.MILLISECONDS).build(), false).exchange("t") }
        assertTrue("https base is considered configured (failure is a transport failure, not NotConfigured)", failure(result).kind != FailureKind.NotConfigured)
    }

    @Test fun `tokens never appear in toString of requests, responses or errors`() {
        assertFalse(ExchangeRequest("SECRET-FIREBASE-TOKEN").toString().contains("SECRET"))
        val resp = ExchangeResponse(true, "SECRET-API-TOKEN", "Bearer", 1, 300, false, ApiUser("u"))
        assertFalse(resp.toString().contains("SECRET"))
        assertFalse(ApiError(FailureKind.Http, 401, "UNAUTHENTICATED").toString().contains("SECRET"))
    }

    @Test fun `redirects are not followed, so a credential can never be forwarded elsewhere`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.1:1/steal"))
        val e = failure(api().me("API.JWT"))
        assertEquals(302, e.httpStatus)
        assertEquals(1, server.requestCount)
    }
}
