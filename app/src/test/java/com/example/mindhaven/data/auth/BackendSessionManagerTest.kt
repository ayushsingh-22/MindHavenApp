package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.FailureKind
import com.example.mindhaven.testsupport.FakeApi
import com.example.mindhaven.testsupport.FakeApi.Companion.http
import com.example.mindhaven.testsupport.FakeApi.Companion.net
import com.example.mindhaven.testsupport.FakeApi.Companion.ok
import com.example.mindhaven.testsupport.FakeGateway
import com.example.mindhaven.testsupport.fbUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendSessionManagerTest {

    private class Rig(
        val gateway: FakeGateway = FakeGateway(fbUser("fb-1")),
        val api: FakeApi = FakeApi(),
        scope: TestScope,
        withTimer: Boolean = false
    ) {
        val pauses = mutableListOf<Long>()
        val manager = BackendSessionManager(
            gateway, api,
            scope = if (withTimer) scope.backgroundScope else null,
            now = { scope.testScheduler.currentTime },
            pause = { pauses += it; kotlinx.coroutines.delay(it) }
        )
    }

    private fun TestScope.rig(withTimer: Boolean = false, gateway: FakeGateway = FakeGateway(fbUser("fb-1")), api: FakeApi = FakeApi()) = Rig(gateway, api, this, withTimer)

    private fun SessionResult.token(): String = (this as SessionResult.Ready).session.let { it.token }

    // ----------------------------------------------------------------------------- token lifetime

    @Test fun `a successful exchange is cached and the canonical id comes only from the backend`() = runTest {
        val r = rig(api = FakeApi(listOf(ok(userId = "neon-canonical"))))
        val first = r.manager.ensureSession() as SessionResult.Ready
        val second = r.manager.ensureSession() as SessionResult.Ready
        assertEquals(1, r.api.exchangeCalls)
        assertEquals("neon-canonical", first.session.user.id)
        assertNotEquals("the Firebase uid is never the application id", "fb-1", first.session.user.id)
        assertEquals(first.session.token, second.session.token)
        assertEquals(listOf(false), r.gateway.idTokenCalls) // getIdToken(false)
    }

    @Test fun `the 5 minute token is refreshed 30 seconds before it expires, not earlier, not later`() = runTest {
        val r = rig()
        r.manager.ensureSession()
        advanceTimeBy(269_000); r.manager.ensureSession()          // 31 s of life left: still cached
        assertEquals(1, r.api.exchangeCalls)
        advanceTimeBy(2_000); r.manager.ensureSession()            // 29 s left: inside the safety margin -> new exchange
        assertEquals(2, r.api.exchangeCalls)
    }

    @Test fun `proactive refresh re-exchanges by itself before expiry and keeps the chain going`() = runTest {
        val r = rig(withTimer = true)
        r.manager.ensureSession()
        assertEquals(1, r.api.exchangeCalls)
        advanceTimeBy(269_000); runCurrent()
        assertEquals("not yet", 1, r.api.exchangeCalls)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, r.api.exchangeCalls)
        advanceTimeBy(270_000); runCurrent()
        assertEquals("timer re-armed itself", 3, r.api.exchangeCalls)
        r.manager.clear() // stop the endless timer so runTest can finish
    }

    @Test fun `expiry is computed from expiresIn on the device clock, immune to clock skew`() = runTest {
        // Server timestamp is in the distant past (device clock far ahead of the server); the token must still be usable.
        val skewed = ok().let { ApiResult.Success(it.value.copy(expiresAt = 1_000)) }
        val r = rig(api = FakeApi(listOf(skewed)))
        r.manager.ensureSession(); r.manager.ensureSession()
        assertEquals(1, r.api.exchangeCalls)
    }

    // ----------------------------------------------------------------------------- coordination

    @Test fun `many simultaneous requests cause exactly ONE exchange`() = runTest {
        val r = rig(api = FakeApi(latencyMs = 500))
        val results = (1..50).map { async { r.manager.ensureSession() } }.awaitAll()
        assertEquals(1, r.api.exchangeCalls)
        assertEquals(1, results.map { it.token() }.toSet().size)
    }

    @Test fun `simultaneous authenticated calls share one exchange and each uses the same token`() = runTest {
        val r = rig(api = FakeApi(latencyMs = 200))
        (1..20).map { async { r.manager.fetchProfile() } }.awaitAll()
        assertEquals(1, r.api.exchangeCalls)
        assertEquals(20, r.api.meCalls)
        assertEquals(1, r.api.meTokens.toSet().size)
    }

    // ----------------------------------------------------------------------------- logout / account switch

    @Test fun `logout invalidates the cached credentials`() = runTest {
        val r = rig()
        r.manager.ensureSession()
        r.manager.clear(); r.gateway.signOut()
        assertEquals(SessionResult.SignedOut, r.manager.ensureSession())
        assertEquals(SessionState.SignedOut, r.manager.state.value)
        assertEquals(1, r.api.exchangeCalls)
    }

    @Test fun `account switching never reuses the previous user's API token`() = runTest {
        val r = rig(api = FakeApi(listOf(ok("neon-A"), ok("neon-B"))))
        val a = r.manager.ensureSession() as SessionResult.Ready
        r.gateway.setUser(fbUser("fb-2")) // different Firebase account, no explicit logout in between
        val b = r.manager.ensureSession() as SessionResult.Ready
        assertEquals("neon-A", a.session.user.id); assertEquals("neon-B", b.session.user.id)
        assertNotEquals(a.session.token, b.session.token)
        assertEquals(2, r.api.exchangeCalls)
        assertTrue("the second exchange used the second user's Firebase token", r.api.exchangeTokens[1].contains("fb-2"))
    }

    @Test fun `an exchange that completes after logout is discarded and never published`() = runTest {
        val r = rig(api = FakeApi(latencyMs = 1_000))
        val inFlight = async { r.manager.ensureSession() }
        runCurrent()
        r.manager.clear(); r.gateway.signOut() // user logs out while the exchange is on the wire
        assertEquals(SessionResult.SignedOut, inFlight.await())
        assertEquals(SessionState.SignedOut, r.manager.state.value)
        assertEquals(SessionResult.SignedOut, r.manager.ensureSession())
    }

    @Test fun `an authenticated call that finishes after logout is reported Stale`() = runTest {
        val r = rig(api = FakeApi())
        r.manager.ensureSession()
        val outcome = async { r.manager.authenticated { token -> r.manager.clear(); ApiResult.Success(token) } }
        assertEquals(Authenticated.Stale, outcome.await())
    }

    // ----------------------------------------------------------------------------- 401 handling (no loops)

    @Test fun `401 INVALID_FIREBASE_TOKEN forces ONE Firebase token refresh and ONE retry`() = runTest {
        val r = rig(api = FakeApi(listOf(http(401, "INVALID_FIREBASE_TOKEN"), ok())))
        assertTrue(r.manager.ensureSession() is SessionResult.Ready)
        assertEquals(listOf(false, true), r.gateway.idTokenCalls)
        assertEquals(2, r.api.exchangeCalls)
    }

    @Test fun `a second 401 requires re-authentication and stops (no infinite loop)`() = runTest {
        val r = rig(api = FakeApi(listOf(http(401, "INVALID_FIREBASE_TOKEN"))))
        assertEquals(SessionResult.ReauthRequired, r.manager.ensureSession())
        assertEquals(2, r.api.exchangeCalls)
        assertEquals("Firebase session is not destroyed by the manager", 0, r.gateway.signOutCount)
    }

    @Test fun `authenticated call 401 re-exchanges once and retries once`() = runTest {
        val r = rig(api = FakeApi(meScript = listOf(http(401, "UNAUTHENTICATED"), ApiResult.Success(FakeApi.profile("neon-user-1")))))
        val out = r.manager.fetchProfile()
        assertTrue(out is Authenticated.Ok)
        assertEquals(2, r.api.exchangeCalls); assertEquals(2, r.api.meCalls)
        assertNotEquals("retry uses the freshly exchanged token", r.api.meTokens[0], r.api.meTokens[1])
    }

    @Test fun `authenticated call that keeps getting 401 gives up after one retry`() = runTest {
        val r = rig(api = FakeApi(meScript = listOf(http(401, "UNAUTHENTICATED"))))
        val out = r.manager.fetchProfile()
        assertEquals(Authenticated.Session(SessionResult.ReauthRequired), out)
        assertEquals(2, r.api.exchangeCalls); assertEquals(2, r.api.meCalls)
    }

    @Test fun `403 USER_MISMATCH on an authenticated call is surfaced, not retried`() = runTest {
        val r = rig(api = FakeApi(meScript = listOf(http(403, "USER_MISMATCH"))))
        val out = r.manager.fetchProfile()
        assertTrue(out is Authenticated.ApiFailure); assertEquals(1, r.api.meCalls); assertEquals(1, r.api.exchangeCalls)
    }

    // ----------------------------------------------------------------------------- error contract

    private fun TestScope.blocked(status: Int, code: String): SessionResult {
        val r = rig(api = FakeApi(listOf(http(status, code))))
        var result: SessionResult = SessionResult.SignedOut
        // run synchronously inside the test scope
        kotlinx.coroutines.runBlocking { result = r.manager.ensureSession() }
        assertEquals("no retries for a permanent refusal", 1, r.api.exchangeCalls)
        assertTrue(r.pauses.isEmpty())
        return result
    }

    @Test fun `403 EMAIL_NOT_VERIFIED reloads the user and retries once when the user has verified meanwhile`() = runTest {
        val r = rig(gateway = FakeGateway(fbUser("fb-1", verified = false)), api = FakeApi(listOf(http(403, "EMAIL_NOT_VERIFIED"), ok())))
        r.gateway.reloadAs = fbUser("fb-1", verified = true)
        assertTrue(r.manager.ensureSession() is SessionResult.Ready)
        assertEquals(1, r.gateway.reloadCount); assertEquals(listOf(false, true), r.gateway.idTokenCalls)
    }

    @Test fun `403 EMAIL_NOT_VERIFIED stays blocked when still unverified`() = runTest {
        val r = rig(gateway = FakeGateway(fbUser("fb-1", verified = false)), api = FakeApi(listOf(http(403, "EMAIL_NOT_VERIFIED"))))
        assertEquals(SessionResult.Blocked(BlockReason.EmailNotVerified), r.manager.ensureSession())
        assertEquals(1, r.api.exchangeCalls)
    }

    @Test fun `403 UNSUPPORTED_SIGN_IN_METHOD, 403 IDENTITY_DISABLED and 409 ACCOUNT_LINK_REQUIRED are permanent`() = runTest {
        assertEquals(SessionResult.Blocked(BlockReason.UnsupportedSignInMethod), blocked(403, "UNSUPPORTED_SIGN_IN_METHOD"))
        assertEquals(SessionResult.Blocked(BlockReason.IdentityDisabled), blocked(403, "IDENTITY_DISABLED"))
        assertEquals(SessionResult.Blocked(BlockReason.AccountLinkRequired), blocked(409, "ACCOUNT_LINK_REQUIRED"))
    }

    @Test fun `400, 413 and 415 are rejected without retry`() = runTest {
        for (s in listOf(400 to "VALIDATION_FAILED", 413 to "", 415 to "UNSUPPORTED_MEDIA_TYPE")) {
            assertEquals(SessionResult.Rejected, blocked(s.first, s.second))
        }
    }

    @Test fun `429 honours Retry-After, retries a bounded number of times and then reports Temporary`() = runTest {
        val r = rig(api = FakeApi(listOf(http(429, "RATE_LIMITED", retryAfter = 7))))
        val result = r.manager.ensureSession()
        assertEquals(SessionResult.Temporary(TemporaryReason.RateLimited, 7), result)
        assertEquals("1 call + at most 2 automatic retries", 3, r.api.exchangeCalls)
        assertEquals(listOf(7_000L, 7_000L), r.pauses)
    }

    @Test fun `429 with a very long Retry-After is not waited out (no blocking, no retry)`() = runTest {
        val r = rig(api = FakeApi(listOf(http(429, "RATE_LIMITED", retryAfter = 120))))
        assertEquals(SessionResult.Temporary(TemporaryReason.RateLimited, 120), r.manager.ensureSession())
        assertEquals(1, r.api.exchangeCalls); assertTrue(r.pauses.isEmpty())
    }

    @Test fun `429 then success recovers`() = runTest {
        val r = rig(api = FakeApi(listOf(http(429, "RATE_LIMITED", retryAfter = 1), ok())))
        assertTrue(r.manager.ensureSession() is SessionResult.Ready)
        assertEquals(2, r.api.exchangeCalls)
    }

    @Test fun `503 backs off with growing waits (no busy loop) and keeps the Firebase session`() = runTest {
        val r = rig(api = FakeApi(listOf(http(503, "IDENTITY_UNAVAILABLE"))))
        assertEquals(SessionResult.Temporary(TemporaryReason.ServiceUnavailable, null), r.manager.ensureSession())
        assertEquals(3, r.api.exchangeCalls)
        assertEquals(listOf(1_000L, 2_000L), r.pauses)
        assertEquals(0, r.gateway.signOutCount)
    }

    @Test fun `timeouts (Render cold start) are retried with backoff and then reported as recoverable`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.Timeout))))
        assertEquals(SessionResult.Temporary(TemporaryReason.Timeout, null), r.manager.ensureSession())
        assertEquals(3, r.api.exchangeCalls); assertEquals(0, r.gateway.signOutCount)
    }

    @Test fun `offline is reported immediately and never signs the user out`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.Offline))))
        assertEquals(SessionResult.Temporary(TemporaryReason.Offline, null), r.manager.ensureSession())
        assertEquals(1, r.api.exchangeCalls); assertEquals(0, r.gateway.signOutCount)
    }

    @Test fun `backend not configured for this build is a recoverable state without network use`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.NotConfigured))))
        assertEquals(SessionResult.Temporary(TemporaryReason.NotConfigured, null), r.manager.ensureSession())
        assertEquals(1, r.api.exchangeCalls)
    }

    @Test fun `exchange route not enabled (404) is a temporary service problem, not a credential problem`() = runTest {
        val r = rig(api = FakeApi(listOf(http(404))))
        assertEquals(SessionResult.Temporary(TemporaryReason.ServiceUnavailable, null), r.manager.ensureSession())
        assertEquals(0, r.gateway.signOutCount)
    }

    @Test fun `a Firebase failure while getting the token is classified without touching the backend`() = runTest {
        val r = rig()
        r.gateway.idTokenFailure = AuthFailure(AuthFailureKind.Network)
        assertEquals(SessionResult.Temporary(TemporaryReason.Offline, null), r.manager.ensureSession())
        r.gateway.idTokenFailure = AuthFailure(AuthFailureKind.UserDisabled)
        assertEquals(SessionResult.ReauthRequired, r.manager.ensureSession())
        assertEquals(0, r.api.exchangeCalls)
    }

    @Test fun `ApiSession toString and states never contain the token`() = runTest {
        val r = rig()
        val s = (r.manager.ensureSession() as SessionResult.Ready).session
        assertFalse(s.toString().contains(s.token))
        assertFalse(r.manager.state.value.toString().contains(s.token))
    }
}
