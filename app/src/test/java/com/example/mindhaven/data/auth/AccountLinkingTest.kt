package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.FailureKind
import com.example.mindhaven.testsupport.FakeApi
import com.example.mindhaven.testsupport.FakeApi.Companion.CHALLENGE
import com.example.mindhaven.testsupport.FakeApi.Companion.http
import com.example.mindhaven.testsupport.FakeApi.Companion.linkStarted
import com.example.mindhaven.testsupport.FakeApi.Companion.linked
import com.example.mindhaven.testsupport.FakeApi.Companion.net
import com.example.mindhaven.testsupport.FakeApi.Companion.ok
import com.example.mindhaven.testsupport.FakeGateway
import com.example.mindhaven.testsupport.FakeGoogle
import com.example.mindhaven.testsupport.fbUser
import com.example.mindhaven.ui.theme.navigation.LINK_ROUTE
import com.example.mindhaven.ui.theme.navigation.NavCommand
import com.example.mindhaven.ui.theme.navigation.authNavigationCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2B.5B.3 account linking below the UI: the REAL repository, session manager and AccountLinker with a fake Firebase
 * gateway and a scripted fake backend (no network, no Firebase, no database).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountLinkingTest {

    private val link409 = http(409, "ACCOUNT_LINK_REQUIRED")
    private val password = "synthetic-web-password"

    private class Rig(scope: TestScope, val gateway: FakeGateway, val api: FakeApi) {
        val google = FakeGoogle()
        val session = BackendSessionManager(gateway, api, scope = null, now = { scope.testScheduler.currentTime }, pause = { kotlinx.coroutines.delay(it) })
        val linker = AccountLinker(gateway, session, now = { scope.testScheduler.currentTime })
        val repo = DefaultAuthRepository(gateway, session, google, scope.backgroundScope, now = { scope.testScheduler.currentTime }, linker = linker)
        val gates = mutableListOf<AuthGate>()
        val states = mutableListOf<LinkState>()
        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { repo.authGate.collect { gates += it } }
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { repo.linkState.collect { states += it } }
        }
        val authenticated get() = repo.authGate.value as? AuthGate.Authenticated
    }

    /** A Google user whose exchange answers 409, then [after] for every later exchange. */
    private fun TestScope.linkRequired(
        providers: List<String> = listOf("google.com"),
        after: List<ApiResult<com.example.mindhaven.data.remote.ExchangeResponse>> = listOf(ok("neon-web-user")),
        configure: FakeApi.() -> Unit = {}
    ): Rig {
        val api = FakeApi(exchangeScript = listOf(link409) + after).apply {
            linkCompleteScript = listOf(linked("neon-web-user"))
            configure()
        }
        val r = Rig(this, FakeGateway(), api)
        r.gateway.googleResult = fbUser("fb-1", providers = providers)
        r.gateway.signInResult = fbUser("fb-1", providers = providers)
        runCurrent()
        return r
    }

    private suspend fun Rig.signIn() {
        val out = if (gateway.googleResult!!.usesGoogle) repo.signInWithGoogle("g") else repo.signInWithEmail("a@example.invalid", "fb-pw")
        assertTrue(out is AuthOutcome.LinkRequired)
        assertTrue(repo.authGate.value is AuthGate.LinkRequired)
    }

    // ------------------------------------------------------------------ restricted state

    @Test fun `LinkRequired holds no API session and is never the app area`() = runTest {
        val r = linkRequired(); r.signIn()
        assertNull(r.repo.authUser.value)
        assertEquals(SessionState.SignedOut, r.session.state.value)
        assertEquals(NavCommand(LINK_ROUTE), authNavigationCommand(r.repo.authGate.value, "emailLogin"))
        assertNull("already there: no duplicate navigation", authNavigationCommand(r.repo.authGate.value, LINK_ROUTE))
        // An authenticated call made in this state can never obtain a token (the exchange still answers 409).
        r.api.exchangeScript = listOf(link409)
        assertTrue(r.session.fetchProfile() is Authenticated.Session)
        assertEquals(0, r.api.meCalls)
    }

    @Test fun `unchecked consent or an empty password never reach the network`() = runTest {
        val r = linkRequired(); r.signIn()
        r.repo.linkAccount(password, consent = false)
        assertEquals(LinkState.Failed(LinkProblem.ConsentRequired), r.repo.linkState.value)
        r.repo.linkAccount("", consent = true)
        assertEquals(LinkState.Failed(LinkProblem.PasswordRequired), r.repo.linkState.value)
        assertEquals(0, r.api.linkStartCalls); assertEquals(0, r.api.linkCompleteCalls)
        assertTrue(r.repo.authGate.value is AuthGate.LinkRequired)
    }

    @Test fun `linking is ignored outside the LinkRequired state`() = runTest {
        val r = Rig(this, FakeGateway(), FakeApi())
        r.repo.linkAccount(password, consent = true)
        assertEquals(0, r.api.linkStartCalls)
    }

    // ------------------------------------------------------------------ success

    @Test fun `success - start then complete with a fresh token, the original canonical id, one API session, no challenge left`() = runTest {
        val r = linkRequired(); r.signIn()
        r.repo.linkAccount(password, consent = true)
        runCurrent()
        assertEquals(1, r.api.linkStartCalls); assertEquals(1, r.api.linkCompleteCalls)
        assertEquals(listOf(password), r.api.linkStartPasswords)
        assertEquals("complete uses exactly the challenge from start", listOf(CHALLENGE), r.api.linkCompleteChallenges)
        assertTrue("both steps use Firebase ID tokens", (r.api.linkStartTokens + r.api.linkCompleteTokens).all { it.startsWith("firebase-id-token-for-fb-1") })
        val auth = r.authenticated!!
        assertEquals("canonical id from the backend, never the Firebase uid", "neon-web-user", auth.userId)
        assertEquals(NavCommand("main"), authNavigationCommand(auth, LINK_ROUTE))
        assertNull("success navigation is idempotent", authNavigationCommand(auth, "main"))
        assertTrue(r.session.state.value is SessionState.Ready)
        assertFalse(r.linker.holdsChallenge)
        assertEquals(LinkState.Linked, r.repo.linkState.value)
        assertEquals(1, r.api.exchangeCalls)
        assertTrue("/api/identity/me is loaded with the linked API token", r.api.meTokens.single().startsWith("api-token-"))
        assertEquals("the normal tokens were fetched without forcing a refresh", listOf(false, false, false), r.gateway.idTokenCalls)
    }

    @Test fun `a later exchange refresh keeps the same canonical id`() = runTest {
        val r = linkRequired(); r.signIn()
        r.repo.linkAccount(password, consent = true)
        val again = r.session.ensureSession(forceExchange = true) as SessionResult.Ready
        assertEquals("neon-web-user", again.session.user.id)
    }

    // ------------------------------------------------------------------ refusals

    @Test fun `wrong password stays restricted, never completes and never holds a token`() = runTest {
        val r = linkRequired { linkStartScript = listOf(http(401, "LINK_PROOF_FAILED")) }; r.signIn()
        r.repo.linkAccount("wrong", consent = true)
        assertEquals(LinkState.Failed(LinkProblem.WrongPassword), r.repo.linkState.value)
        assertTrue(r.repo.authGate.value is AuthGate.LinkRequired)
        assertEquals(0, r.api.linkCompleteCalls); assertEquals(SessionState.SignedOut, r.session.state.value)
        assertFalse(r.linker.holdsChallenge)
    }

    @Test fun `start and complete are serialised - overlapping taps are ignored`() = runTest {
        val r = linkRequired { latencyMs = 500 }; r.signIn()
        val first = async { r.repo.linkAccount(password, consent = true) }
        runCurrent()
        r.repo.linkAccount(password, consent = true) // second tap while the first runs
        r.repo.reauthenticateWithGoogle("g")         // and a re-authentication attempt
        first.await()
        assertEquals(1, r.api.linkStartCalls); assertEquals(1, r.api.linkCompleteCalls)
        assertEquals(1, r.api.maxLinkInFlight); assertTrue(r.gateway.reauthCalls.isEmpty())
        assertNotNull(r.authenticated)
    }

    @Test fun `INVALID_FIREBASE_TOKEN - one forced Firebase refresh, then success`() = runTest {
        val r = linkRequired { linkStartScript = listOf(http(401, "INVALID_FIREBASE_TOKEN"), linkStarted()) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(2, r.api.linkStartCalls)
        assertTrue(true in r.gateway.idTokenCalls)
        assertNotNull(r.authenticated)
    }

    @Test fun `INVALID_FIREBASE_TOKEN twice signs out with a notice`() = runTest {
        val r = linkRequired { linkStartScript = listOf(http(401, "INVALID_FIREBASE_TOKEN")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value)
        assertEquals(1, r.gateway.signOutCount); assertNotNull(r.repo.restoreNotice.value)
    }

    @Test fun `permanent refusals (email not verified, disabled, unsupported) sign out, never authenticate`() = runTest {
        for (code in listOf("EMAIL_NOT_VERIFIED", "IDENTITY_DISABLED", "UNSUPPORTED_SIGN_IN_METHOD")) {
            val r = linkRequired { linkStartScript = listOf(http(403, code)) }; r.signIn()
            r.repo.linkAccount(password, consent = true)
            assertEquals(code, AuthGate.SignedOut, r.repo.authGate.value)
            assertTrue(r.gates.none { it is AuthGate.Authenticated })
        }
    }

    @Test fun `feature disabled (404), rate limited (429 with Retry-After) and 503 stay restricted with guidance`() = runTest {
        val cases = listOf(
            http(404) to LinkState.Failed(LinkProblem.FeatureDisabled),
            http(429, "RATE_LIMITED", retryAfter = 600) to LinkState.Failed(LinkProblem.RateLimited, 600),
            http(503, "IDENTITY_UNAVAILABLE") to LinkState.Failed(LinkProblem.Unavailable),
            net(FailureKind.Offline) to LinkState.Failed(LinkProblem.Offline)
        )
        for ((answer, expected) in cases) {
            val r = linkRequired { linkStartScript = listOf(answer) }; r.signIn()
            r.repo.linkAccount(password, consent = true)
            assertEquals(expected, r.repo.linkState.value)
            assertTrue(r.repo.authGate.value is AuthGate.LinkRequired); assertEquals(0, r.api.linkCompleteCalls)
        }
        assertTrue(linkProblemMessage(LinkProblem.RateLimited, 600).contains("10 minutes"))
    }

    // ------------------------------------------------------------------ freshness / re-authentication

    @Test fun `stale Firebase sign-in asks for real Google re-authentication BEFORE any start request`() = runTest {
        val r = linkRequired(); r.signIn()
        r.gateway.authTimeSeconds = -3_600 // signed in an hour ago
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.NeedsReauth(ReauthMethod.Google), r.repo.linkState.value)
        assertEquals("a token refresh is NOT a re-authentication: nothing was sent", 0, r.api.linkStartCalls)
        r.gateway.reauthAuthTimeSeconds = 0
        r.repo.reauthenticateWithGoogle("fresh-google-id-token")
        assertEquals(listOf("google"), r.gateway.reauthCalls)
        assertEquals(LinkState.Reauthenticated, r.repo.linkState.value)
        r.repo.linkAccount(password, consent = true)
        assertNotNull(r.authenticated)
        assertTrue("the first token after re-authentication is force-refreshed", r.gateway.idTokenCalls.contains(true))
    }

    @Test fun `email-password users re-authenticate with their MindHaven password, wrong password is explained`() = runTest {
        val r = linkRequired(providers = listOf("password")); r.signIn()
        r.gateway.authTimeSeconds = -3_600
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.NeedsReauth(ReauthMethod.Password), r.repo.linkState.value)
        r.gateway.reauthFailure = AuthFailure(AuthFailureKind.InvalidCredentials)
        r.repo.reauthenticateWithPassword("wrong-firebase-pw")
        assertEquals(LinkState.NeedsReauth(ReauthMethod.Password, LinkProblem.ReauthFailed), r.repo.linkState.value)
        r.gateway.reauthFailure = AuthFailure(AuthFailureKind.UserMismatch)
        r.repo.reauthenticateWithPassword("x")
        assertEquals(LinkState.NeedsReauth(ReauthMethod.Password, LinkProblem.WrongAccount), r.repo.linkState.value)
        r.gateway.reauthFailure = null; r.gateway.reauthAuthTimeSeconds = 0
        r.repo.reauthenticateWithPassword("right-firebase-pw")
        assertEquals(LinkState.Reauthenticated, r.repo.linkState.value)
        assertTrue(r.repo.authGate.value is AuthGate.LinkRequired)
    }

    @Test fun `an unknown provider gets the safe sign-in-again path`() = runTest {
        val r = linkRequired(providers = listOf("password")); r.signIn()
        r.gateway.setUser(fbUser("fb-1", providers = emptyList()))
        r.gateway.authTimeSeconds = -3_600
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.NeedsReauth(ReauthMethod.SignInAgain), r.repo.linkState.value)
    }

    @Test fun `server REAUTHENTICATION_REQUIRED at complete keeps the challenge in memory and resumes with it after re-auth`() = runTest {
        val r = linkRequired { linkCompleteScript = listOf(http(401, "REAUTHENTICATION_REQUIRED"), linked("neon-web-user")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.NeedsReauth(ReauthMethod.Google), r.repo.linkState.value)
        assertTrue(r.linker.holdsChallenge)
        r.repo.reauthenticateWithGoogle("g")
        assertEquals("no second start: the same challenge was completed", 1, r.api.linkStartCalls)
        assertEquals(listOf(CHALLENGE, CHALLENGE), r.api.linkCompleteChallenges)
        assertEquals("neon-web-user", r.authenticated!!.userId)
    }

    @Test fun `challenge expiry - a held challenge is not used after its local deadline`() = runTest {
        val r = linkRequired { linkCompleteScript = listOf(http(401, "REAUTHENTICATION_REQUIRED")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        advanceTimeBy(290_000) // past expiresIn (300 s) minus the safety margin
        r.repo.reauthenticateWithGoogle("g")
        assertEquals(LinkState.Reauthenticated, r.repo.linkState.value)
        assertEquals("the expired challenge was never sent again", 1, r.api.linkCompleteCalls)
        assertFalse(r.linker.holdsChallenge)
    }

    @Test fun `a challenge that already expired on arrival is never sent`() = runTest {
        val r = linkRequired { linkStartScript = listOf(linkStarted(expiresIn = 10)) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.Failed(LinkProblem.Expired), r.repo.linkState.value)
        assertEquals(0, r.api.linkCompleteCalls)
    }

    // ------------------------------------------------------------------ ambiguous completion -> exchange recovery

    @Test fun `complete timeout - exchange recovery succeeds, the challenge is NOT replayed`() = runTest {
        val r = linkRequired { linkCompleteScript = listOf(net(FailureKind.Timeout)) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(1, r.api.linkCompleteCalls)
        assertEquals("initial 409 + one recovery exchange", 2, r.api.exchangeCalls)
        assertEquals("neon-web-user", r.authenticated!!.userId)
    }

    @Test fun `complete timeout - exchange still 409 - start again guidance, no replay, no retry loop`() = runTest {
        val r = linkRequired(after = listOf(link409)) { linkCompleteScript = listOf(net(FailureKind.Timeout)) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.Failed(LinkProblem.NotCompleted), r.repo.linkState.value)
        assertEquals(1, r.api.linkCompleteCalls); assertEquals(2, r.api.exchangeCalls)
        assertFalse(r.linker.holdsChallenge); assertTrue(r.repo.authGate.value is AuthGate.LinkRequired)
    }

    @Test fun `complete 5xx with the exchange offline - unconfirmed, nothing replayed`() = runTest {
        val r = linkRequired(after = listOf(net(FailureKind.Offline))) { linkCompleteScript = listOf(http(503, "IDENTITY_UNAVAILABLE")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.Failed(LinkProblem.Unconfirmed), r.repo.linkState.value)
        assertEquals(1, r.api.linkCompleteCalls); assertFalse(r.linker.holdsChallenge)
    }

    @Test fun `LINK_CHALLENGE_INVALID (replayed or expired) - exchange first, then an expired-request message`() = runTest {
        val r = linkRequired(after = listOf(link409)) { linkCompleteScript = listOf(http(400, "LINK_CHALLENGE_INVALID")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.Failed(LinkProblem.Expired), r.repo.linkState.value)
        assertEquals(2, r.api.exchangeCalls); assertEquals(1, r.api.linkCompleteCalls)
    }

    @Test fun `IDENTITY_ALREADY_LINKED at start recovers through the exchange`() = runTest {
        val r = linkRequired { linkStartScript = listOf(http(409, "IDENTITY_ALREADY_LINKED")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(0, r.api.linkCompleteCalls)
        assertEquals("neon-web-user", r.authenticated!!.userId)
    }

    @Test fun `a retry after an unconfirmed completion starts fresh (new start, never the old challenge)`() = runTest {
        val r = linkRequired(after = listOf(net(FailureKind.Offline), ok("neon-web-user"))) {
            linkCompleteScript = listOf(net(FailureKind.Timeout))
            linkStartScript = listOf(linkStarted(), http(409, "IDENTITY_ALREADY_LINKED"))
        }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertEquals(LinkState.Failed(LinkProblem.Unconfirmed), r.repo.linkState.value)
        r.repo.linkAccount(password, consent = true)
        assertEquals(2, r.api.linkStartCalls); assertEquals(1, r.api.linkCompleteCalls)
        assertEquals("neon-web-user", r.authenticated!!.userId)
    }

    // ------------------------------------------------------------------ cancel, logout, account switch, process death

    @Test fun `cancel signs out of Firebase and Google, forgets the challenge and returns to the sign-in screens`() = runTest {
        val r = linkRequired { linkCompleteScript = listOf(http(401, "REAUTHENTICATION_REQUIRED")) }; r.signIn()
        r.repo.linkAccount(password, consent = true)
        assertTrue(r.linker.holdsChallenge)
        r.repo.cancelLinking()
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value)
        assertEquals(1, r.gateway.signOutCount); assertEquals(1, r.google.signOuts)
        assertFalse(r.linker.holdsChallenge); assertEquals(LinkState.Idle, r.repo.linkState.value)
        assertEquals(NavCommand("welcome"), authNavigationCommand(r.repo.authGate.value, LINK_ROUTE))
        assertEquals(SessionState.SignedOut, r.session.state.value)
    }

    @Test fun `account switched mid-flight - the result is discarded, nobody is authenticated`() = runTest {
        val r = linkRequired(); r.signIn()
        r.api.onLinkCall = { step -> if (step == "start") r.gateway.setUser(fbUser("fb-OTHER", providers = listOf("google.com"))) }
        r.repo.linkAccount(password, consent = true)
        assertEquals(0, r.api.linkCompleteCalls)
        assertTrue(r.gates.none { it is AuthGate.Authenticated })
        assertFalse(r.linker.holdsChallenge)
    }

    @Test fun `logout during completion - the linked token is never adopted`() = runTest {
        val r = linkRequired(); r.signIn()
        r.api.onLinkCall = { step -> if (step == "complete") r.repo.logout() }
        r.repo.linkAccount(password, consent = true)
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value)
        assertTrue(r.gates.none { it is AuthGate.Authenticated })
        assertEquals(SessionState.SignedOut, r.session.state.value)
    }

    @Test fun `process recreation - nothing secret survives, the restored session re-exchanges and lands in LinkRequired`() = runTest {
        val first = linkRequired { linkCompleteScript = listOf(http(401, "REAUTHENTICATION_REQUIRED")) }; first.signIn()
        first.repo.linkAccount(password, consent = true)
        assertTrue(first.linker.holdsChallenge)
        // "Process death": only the Firebase SDK's own persisted user survives; a brand-new object graph is built.
        val restored = Rig(this, FakeGateway(fbUser("fb-1", providers = listOf("google.com"))), FakeApi(exchangeScript = listOf(link409)))
        runCurrent()
        assertTrue(restored.repo.authGate.value is AuthGate.LinkRequired)
        assertFalse(restored.linker.holdsChallenge)
        assertEquals(LinkState.Idle, restored.repo.linkState.value)
        assertEquals(1, restored.api.exchangeCalls)
    }
}
