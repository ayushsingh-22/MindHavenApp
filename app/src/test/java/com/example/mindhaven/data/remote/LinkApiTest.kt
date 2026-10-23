package com.example.mindhaven.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * link/start + link/complete over the real OkHttp client against an in-process MockWebServer. Response bodies mirror
 * backend/src/routes/accountLinkRoutes.js exactly (status codes, field names, error envelope). No real backend.
 */
class LinkApiTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(400, TimeUnit.MILLISECONDS).connectTimeout(1, TimeUnit.SECONDS).followRedirects(false).build()

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { runCatching { server.shutdown() } }

    private fun api(cleartext: Boolean = true) = OkHttpBackendApi(server.url("/").toString(), client, cleartext)
    private fun enqueue(status: Int, body: String, vararg headers: Pair<String, String>) {
        val r = MockResponse().setResponseCode(status).setBody(body).setHeader("Content-Type", "application/json")
        headers.forEach { (k, v) -> r.setHeader(k, v) }
        server.enqueue(r)
    }
    private fun failure(r: ApiResult<*>) = (r as ApiResult.Failure).error

    private val challenge = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE"
    private val started = """{"success":true,"challenge":"$challenge","expiresAt":1760000300000,"expiresIn":300}"""
    private val linked = """{"success":true,"token":"api.jwt.value","tokenType":"Bearer","expiresAt":1760000600000,"expiresIn":300,
        "linked":true,"user":{"id":"neon-web-1","email":"a@example.invalid","fullName":"A","avatarUrl":null}}"""
    private fun error(code: String) = """{"success":false,"error":"backend text that must never be shown","code":"$code","requestId":"req-1"}"""

    @Test fun `start sends ONLY idToken, password and consent=true to the documented path, without an API token`() = runBlocking {
        enqueue(201, started)
        val r = api().linkStart("FIREBASE.ID.TOKEN", "web-password") as ApiResult.Success
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", req.method); assertEquals("/api/auth/firebase/link/start", req.path)
        assertNull(req.getHeader("Authorization"))
        val sent = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("idToken", "password", "consent"), sent.keys)
        assertTrue(sent.getValue("consent").jsonPrimitive.boolean)
        assertEquals(challenge, r.value.challenge); assertEquals(300, r.value.expiresIn); assertEquals(1760000300000, r.value.expiresAt)
    }

    @Test fun `complete sends ONLY idToken, challenge and consent=true and parses the canonical user + token`() = runBlocking {
        enqueue(200, linked)
        val r = api().linkComplete("FIREBASE.ID.TOKEN.2", challenge) as ApiResult.Success
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/auth/firebase/link/complete", req.path); assertNull(req.getHeader("Authorization"))
        val sent = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("idToken", "challenge", "consent"), sent.keys)
        assertEquals("neon-web-1", r.value.user?.id); assertTrue(r.value.linked); assertEquals("Bearer", r.value.tokenType)
    }

    @Test fun `strict parsing - malformed challenge, lifetime over 5 minutes, linked=false, wrong token type are rejected`() = runBlocking {
        for (bad in listOf(
            """{"success":true,"challenge":"short","expiresAt":1,"expiresIn":300}""",
            """{"success":true,"challenge":"$challenge","expiresAt":1,"expiresIn":301}""",
            """{"success":false,"challenge":"$challenge","expiresAt":1,"expiresIn":300}""",
            """not json"""
        )) {
            enqueue(201, bad)
            assertEquals(bad, FailureKind.BadResponse, failure(api().linkStart("t", "p")).kind)
        }
        for (bad in listOf(
            linked.replace("\"linked\":true", "\"linked\":false"),
            linked.replace("\"Bearer\"", "\"Basic\""),
            linked.replace("\"id\":\"neon-web-1\"", "\"id\":\"\""),
            linked.replace("\"token\":\"api.jwt.value\"", "\"token\":\"\"")
        )) {
            enqueue(200, bad)
            assertEquals(FailureKind.BadResponse, failure(api().linkComplete("t", challenge)).kind)
        }
    }

    @Test fun `documented error codes, the flag-off 404 and Retry-After are surfaced (never the backend text)`() = runBlocking {
        val cases = listOf(
            Triple(400, "CONSENT_REQUIRED", null), Triple(400, "VALIDATION_FAILED", null), Triple(400, "LINK_CHALLENGE_INVALID", null),
            Triple(401, "INVALID_FIREBASE_TOKEN", null), Triple(401, "REAUTHENTICATION_REQUIRED", null), Triple(401, "LINK_PROOF_FAILED", null),
            Triple(403, "EMAIL_NOT_VERIFIED", null), Triple(403, "UNSUPPORTED_SIGN_IN_METHOD", null), Triple(403, "IDENTITY_DISABLED", null),
            Triple(409, "IDENTITY_ALREADY_LINKED", null), Triple(429, "RATE_LIMITED", "900"), Triple(503, "IDENTITY_UNAVAILABLE", null)
        )
        for ((status, code, retry) in cases) {
            if (retry != null) enqueue(status, error(code), "Retry-After" to retry) else enqueue(status, error(code))
            val e = failure(api().linkStart("t", "p"))
            assertEquals(code, status, e.httpStatus); assertEquals(code, e.code)
            assertEquals(retry?.toLong(), e.retryAfterSeconds)
            assertFalse(e.toString().contains("backend text"))
        }
        enqueue(404, """{"success":false,"error":"Endpoint not found"}""")
        val off = failure(api().linkComplete("t", challenge))
        assertEquals(404, off.httpStatus); assertNull(off.code)
    }

    @Test fun `a lost response is a Timeout, not a success or a refusal`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(FailureKind.Timeout, failure(api().linkComplete("t", challenge)).kind)
    }

    @Test fun `cleartext is refused without touching the network when not allowed (release)`() = runBlocking {
        assertEquals(FailureKind.NotConfigured, failure(api(cleartext = false).linkStart("t", "p")).kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun `request and response objects never print secrets`() {
        val texts = listOf(
            LinkStartRequest("FIREBASE.TOKEN", "web-password", true).toString(),
            LinkCompleteRequest("FIREBASE.TOKEN", challenge, true).toString(),
            LinkStartResponse(true, challenge, 1, 300).toString(),
            LinkCompleteResponse(true, "api.jwt.value", "Bearer", 1, 300, true, ApiUser("neon-web-1")).toString(),
            com.example.mindhaven.data.auth.FirebaseIdToken("FIREBASE.TOKEN", 1).toString()
        ).joinToString()
        for (secret in listOf("FIREBASE.TOKEN", "web-password", challenge, "api.jwt.value")) assertFalse(secret, texts.contains(secret))
    }
}
