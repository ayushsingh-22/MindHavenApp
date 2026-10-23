package com.example.mindhaven.data.remote

import com.example.mindhaven.data.auth.Authenticated
import com.example.mindhaven.data.auth.BackendSessionManager
import com.example.mindhaven.data.auth.BlockReason
import com.example.mindhaven.data.auth.SessionResult
import com.example.mindhaven.data.auth.TemporaryReason
import com.example.mindhaven.testsupport.FakeGateway
import com.example.mindhaven.testsupport.fbUser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Replays REAL responses captured from the Phase 2B.3 Express code (identityRoutes.js behind errorHandler / requireAuth /
 * express-rate-limit; Firebase and the database were mocked when capturing) through the real OkHttp client and the
 * session manager. Fixture file: src/test/resources/contract/backend-responses.json
 */
class BackendContractFixtureTest {
    private lateinit var server: MockWebServer
    private lateinit var fixtures: JsonObject
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).followRedirects(false).build()

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        val text = checkNotNull(javaClass.classLoader!!.getResourceAsStream("contract/backend-responses.json")) { "fixture missing" }.bufferedReader().readText()
        fixtures = Json.parseToJsonElement(text).jsonObject
    }

    @After fun stop() { runCatching { server.shutdown() } }

    private fun api() = OkHttpBackendApi(server.url("/").toString(), client, allowCleartext = true)

    private fun enqueue(name: String, times: Int = 1) {
        val f = fixtures.getValue(name).jsonObject
        repeat(times) {
            val r = MockResponse().setResponseCode(f.getValue("status").jsonPrimitive.int)
                .setHeader("Content-Type", "application/json").setBody(f.getValue("body").toString())
            f["retryAfter"]?.jsonPrimitive?.takeIf { it.content != "null" }?.let { r.setHeader("Retry-After", it.content) }
            server.enqueue(r)
        }
    }

    private fun failure(r: ApiResult<*>) = (r as ApiResult.Failure).error

    @Test fun `real exchange success parses into the documented contract`() = runBlocking {
        enqueue("exchange_ok")
        val ok = (api().exchange("firebase.id.token") as ApiResult.Success).value
        assertEquals("API.JWT.FIXTURE-TOKEN", ok.token); assertEquals("Bearer", ok.tokenType)
        assertEquals(300, ok.expiresIn); assertTrue(ok.expiresAt > 1_700_000_000_000)
        assertTrue(ok.provisioned)
        assertEquals("11111111-2222-4333-8444-555555555555", ok.user!!.id)
        assertEquals("Test Person", ok.user!!.fullName); assertNull(ok.user!!.avatarUrl)
    }

    @Test fun `every real error response maps to its documented status and code`() = runBlocking {
        val expected = listOf(
            Triple("exchange_400_validation", 400, "VALIDATION_FAILED"),
            Triple("exchange_401_invalid_token", 401, "INVALID_FIREBASE_TOKEN"),
            Triple("exchange_403_email_not_verified", 403, "EMAIL_NOT_VERIFIED"),
            Triple("exchange_403_unsupported_method", 403, "UNSUPPORTED_SIGN_IN_METHOD"),
            Triple("exchange_403_identity_disabled", 403, "IDENTITY_DISABLED"),
            Triple("exchange_409_link_required", 409, "ACCOUNT_LINK_REQUIRED"),
            Triple("exchange_415_wrong_type", 415, "UNSUPPORTED_MEDIA_TYPE"),
            Triple("exchange_503_unavailable", 503, "IDENTITY_UNAVAILABLE"),
            Triple("exchange_429_rate_limited", 429, "RATE_LIMITED"),
            Triple("exchange_404_disabled_route", 404, null)
        )
        for ((name, status, code) in expected) {
            enqueue(name)
            val e = failure(api().exchange("t"))
            assertEquals(name, status, e.httpStatus); assertEquals(name, code, e.code)
        }
        enqueue("exchange_413_too_large")
        assertEquals(413, failure(api().exchange("t")).httpStatus) // body is not the JSON envelope: still classified safely
    }

    @Test fun `the real 429 carries a Retry-After header that is parsed`() = runBlocking {
        enqueue("exchange_429_rate_limited")
        val e = failure(api().exchange("t"))
        assertEquals(900L, e.retryAfterSeconds)
    }

    @Test fun `real identity me response parses and keeps the canonical id and linked metadata`() = runBlocking {
        enqueue("me_ok")
        val p = (api().me("API.JWT") as ApiResult.Success).value
        assertEquals("11111111-2222-4333-8444-555555555555", p.id)
        assertTrue(p.emailVerified); assertFalse(p.hasPassword)
        val i = p.identities.single()
        assertEquals("firebase", i.provider); assertEquals("google.com", i.signInMethod); assertEquals("created", i.linkMethod)
        assertFalse(i.disabled); assertTrue(i.lastLoginAt != null)
        assertFalse("no provider subject or hash is part of the contract", fixtures.getValue("me_ok").toString().let { it.contains("providerSub") || it.contains("passwordHash") })
    }

    @Test fun `real me error responses`() = runBlocking {
        enqueue("me_401_unauthenticated"); assertTrue(failure(api().me("t")).isUnauthenticated)
        enqueue("me_403_user_mismatch"); assertEquals("USER_MISMATCH", failure(api().me("t")).code)
        enqueue("me_404_not_found"); assertEquals("USER_NOT_FOUND", failure(api().me("t")).code)
    }

    // ---- the same real responses through the session manager (policy end to end) ----------------------------

    private fun manager(gateway: FakeGateway = FakeGateway(fbUser("fb-1"))) =
        BackendSessionManager(gateway, api(), scope = null, pause = { /* no real waiting in tests */ }) to gateway

    @Test fun `real success yields a session whose user is the canonical Neon id, not the Firebase uid`() = runBlocking {
        enqueue("exchange_ok")
        val (m, _) = manager()
        val ready = m.ensureSession() as SessionResult.Ready
        assertEquals("11111111-2222-4333-8444-555555555555", ready.session.user.id)
        assertEquals(1, server.requestCount)
        val lifeMs = ready.session.expiresAtMs - System.currentTimeMillis()
        assertTrue("about 5 minutes of life, from expiresIn", lifeMs in 290_000L..300_500L)
    }

    @Test fun `real 409 and 403 refusals are permanent blocks with no retries`() = runBlocking {
        for ((name, reason) in listOf("exchange_409_link_required" to BlockReason.AccountLinkRequired,
            "exchange_403_identity_disabled" to BlockReason.IdentityDisabled, "exchange_403_unsupported_method" to BlockReason.UnsupportedSignInMethod)) {
            server.shutdown(); server = MockWebServer().also { it.start() }
            enqueue(name)
            val (m, _) = manager()
            assertEquals(name, SessionResult.Blocked(reason), m.ensureSession())
            assertEquals("no retry for $name", 1, server.requestCount)
        }
    }

    @Test fun `real 401 is retried exactly once with a refreshed Firebase token, then requires re-authentication`() = runBlocking {
        enqueue("exchange_401_invalid_token", times = 5)
        val (m, gateway) = manager()
        assertEquals(SessionResult.ReauthRequired, m.ensureSession())
        assertEquals(2, server.requestCount)
        assertEquals(listOf(false, true), gateway.idTokenCalls)
    }

    @Test fun `real 429 with Retry-After 900 is not waited out and is reported as recoverable`() = runBlocking {
        enqueue("exchange_429_rate_limited")
        val (m, gateway) = manager()
        assertEquals(SessionResult.Temporary(TemporaryReason.RateLimited, 900), m.ensureSession())
        assertEquals(1, server.requestCount); assertEquals(0, gateway.signOutCount)
    }

    @Test fun `real 503 is retried a bounded number of times and the Firebase session is kept`() = runBlocking {
        enqueue("exchange_503_unavailable", times = 6)
        val (m, gateway) = manager()
        assertEquals(SessionResult.Temporary(TemporaryReason.ServiceUnavailable, null), m.ensureSession())
        assertEquals(3, server.requestCount); assertEquals(0, gateway.signOutCount)
    }

    @Test fun `real end to end - exchange then identity me through the manager`() = runBlocking {
        enqueue("exchange_ok"); enqueue("me_ok")
        val (m, _) = manager()
        val out = m.fetchProfile() as Authenticated.Ok
        assertEquals("11111111-2222-4333-8444-555555555555", out.value.id)
        server.takeRequest(1, TimeUnit.SECONDS)
        val me = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/identity/me", me.path)
        assertEquals("Bearer API.JWT.FIXTURE-TOKEN", me.getHeader("Authorization"))
    }

    @Test fun `real me 401 triggers one re-exchange and one retry, then stops`() = runBlocking {
        enqueue("exchange_ok"); enqueue("me_401_unauthenticated"); enqueue("exchange_ok"); enqueue("me_401_unauthenticated")
        val (m, _) = manager()
        val out = m.fetchProfile()
        assertEquals(Authenticated.Session(SessionResult.ReauthRequired), out)
        assertEquals("exchange, me, exchange, me", 4, server.requestCount)
    }
}
