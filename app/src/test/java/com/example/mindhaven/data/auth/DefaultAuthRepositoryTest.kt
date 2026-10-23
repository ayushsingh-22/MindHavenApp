package com.example.mindhaven.data.auth

import com.example.mindhaven.data.remote.ApiResult
import com.example.mindhaven.data.remote.FailureKind
import com.example.mindhaven.testsupport.FakeApi
import com.example.mindhaven.testsupport.FakeApi.Companion.http
import com.example.mindhaven.testsupport.FakeApi.Companion.net
import com.example.mindhaven.testsupport.FakeApi.Companion.ok
import com.example.mindhaven.testsupport.FakeGateway
import com.example.mindhaven.testsupport.FakeGoogle
import com.example.mindhaven.testsupport.fbUser
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {

    private class Rig(scope: TestScope, val gateway: FakeGateway, val api: FakeApi) {
        val google = FakeGoogle()
        val session = BackendSessionManager(gateway, api, scope = null, now = { scope.testScheduler.currentTime }, pause = { kotlinx.coroutines.delay(it) })
        val repo = DefaultAuthRepository(gateway, session, google, scope.backgroundScope, now = { scope.testScheduler.currentTime })
        /** Every gate value ever published, in order. */
        val gates = mutableListOf<AuthGate>()
        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { repo.authGate.collect { gates += it } }
        }
        val everAuthenticated get() = gates.any { it is AuthGate.Authenticated }
    }

    private fun TestScope.rig(user: FirebaseSession? = null, api: FakeApi = FakeApi()) = Rig(this, FakeGateway(user), api).also { runCurrent() }

    // ------------------------------------------------------------------ email/password

    @Test fun `email login becomes Authenticated immediately when the exchange succeeds, and not before`() = runTest {
        val r = rig(api = FakeApi(latencyMs = 500))
        r.gateway.signInResult = fbUser("fb-1", verified = true)
        val pending = async { r.repo.signInWithEmail("a@example.invalid", "pw-123456") }
        runCurrent()
        assertEquals("gate must not open while the backend has not answered", AuthGate.SignedOut, r.repo.authGate.value)
        assertNull(r.repo.authUser.value)
        advanceTimeBy(600)
        assertTrue(pending.await() is AuthOutcome.SignedIn)
        assertEquals(AuthGate.Authenticated(AuthUser("fb-1", "Name fb-1", "fb-1@example.invalid"), "neon-user-1"), r.repo.authGate.value)
        assertEquals("fb-1", r.repo.authUser.value?.uid)
        assertEquals(1, r.api.exchangeCalls)
        assertTrue("exchange used getIdToken(false)", r.gateway.idTokenCalls.first() == false)
    }

    @Test fun `unverified e-mail login never reaches the backend, sends a (throttled) verification mail and signs out`() = runTest {
        val r = rig()
        r.gateway.signInResult = fbUser("fb-1", verified = false)
        val first = r.repo.signInWithEmail("a@example.invalid", "pw") as AuthOutcome.VerificationRequired
        assertTrue(first.emailSent)
        assertEquals(0, r.api.exchangeCalls); assertEquals(1, r.gateway.signOutCount)
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertFalse(r.everAuthenticated)
        val second = r.repo.signInWithEmail("a@example.invalid", "pw") as AuthOutcome.VerificationRequired
        assertFalse("second attempt within the cooldown does not send another mail", second.emailSent)
        assertEquals(1, r.gateway.verificationMails)
        advanceTimeBy(61_000)
        assertTrue((r.repo.signInWithEmail("a@example.invalid", "pw") as AuthOutcome.VerificationRequired).emailSent)
        assertEquals(2, r.gateway.verificationMails)
    }

    @Test fun `registration creates the account, sends the verification mail, signs out and does not call the backend`() = runTest {
        val r = rig()
        val outcome = r.repo.signUpWithEmail("new@example.invalid", "pw-123456") as AuthOutcome.VerificationRequired
        assertTrue(outcome.emailSent)
        assertEquals(1, r.gateway.verificationMails); assertEquals(1, r.gateway.signOutCount)
        assertEquals(0, r.api.exchangeCalls); assertFalse(r.everAuthenticated)
    }

    @Test fun `wrong password shows a safe message and never opens the gate`() = runTest {
        val r = rig()
        r.gateway.signInFailure = AuthFailure(AuthFailureKind.InvalidCredentials)
        val out = r.repo.signInWithEmail("a@example.invalid", "bad") as AuthOutcome.Failed
        assertEquals("Incorrect email or password.", out.message)
        assertEquals(0, r.api.exchangeCalls); assertFalse(r.everAuthenticated)
    }

    @Test fun `firebase failure kinds map to safe user messages (no provider text)`() = runTest {
        val r = rig()
        for (kind in AuthFailureKind.entries) {
            r.gateway.signInFailure = AuthFailure(kind)
            val msg = (r.repo.signInWithEmail("a@example.invalid", "x") as AuthOutcome.Failed).message
            assertTrue(msg.isNotBlank()); assertFalse(msg.contains("Exception")); assertFalse(msg.contains("firebase", true))
        }
    }

    // ------------------------------------------------------------------ Google

    @Test fun `google login becomes Authenticated immediately after a successful exchange of the FIREBASE token`() = runTest {
        val r = rig()
        r.gateway.googleResult = fbUser("fb-g", providers = listOf("google.com"))
        assertTrue(r.repo.signInWithGoogle("RAW-GOOGLE-ID-TOKEN") is AuthOutcome.SignedIn)
        assertTrue(r.repo.authGate.value is AuthGate.Authenticated)
        assertEquals("neon-user-1", (r.repo.authGate.value as AuthGate.Authenticated).userId)
        assertFalse("the raw Google token is never sent to the backend", r.api.exchangeTokens.any { it.contains("RAW-GOOGLE") })
        assertTrue(r.api.exchangeTokens.single().startsWith("firebase-id-token-for-fb-g"))
    }

    // ------------------------------------------------------------------ refusals never open the gate

    @Test fun `409 ACCOUNT_LINK_REQUIRED never opens the gate - restricted LinkRequired, Firebase kept, no API session`() = runTest {
        val r = rig(api = FakeApi(listOf(http(409, "ACCOUNT_LINK_REQUIRED"))))
        r.gateway.googleResult = fbUser("fb-g", providers = listOf("google.com"))
        val out = r.repo.signInWithGoogle("g") as AuthOutcome.LinkRequired
        assertEquals("fb-g", out.user.uid)
        assertEquals(AuthGate.LinkRequired(AuthUser("fb-g", "Name fb-g", "fb-g@example.invalid")), r.repo.authGate.value)
        assertFalse(r.everAuthenticated); assertNull(r.repo.authUser.value)
        assertEquals("Firebase is kept so the user can link", 0, r.gateway.signOutCount); assertEquals(0, r.google.signOuts)
        assertEquals(1, r.api.exchangeCalls)
        assertEquals("no API token exists while LinkRequired", SessionState.SignedOut, r.session.state.value)
        assertEquals(ProfileState.Idle, r.repo.profile.value)
    }

    @Test fun `401 INVALID_FIREBASE_TOKEN twice, IDENTITY_DISABLED and UNSUPPORTED_SIGN_IN_METHOD never open the gate`() = runTest {
        for (script in listOf(listOf(http(401, "INVALID_FIREBASE_TOKEN")), listOf(http(403, "IDENTITY_DISABLED")), listOf(http(403, "UNSUPPORTED_SIGN_IN_METHOD")))) {
            val r = rig(api = FakeApi(script))
            val out = r.repo.signInWithGoogle("g")
            assertTrue(out is AuthOutcome.Blocked || out is AuthOutcome.Failed)
            assertFalse(r.everAuthenticated); assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertEquals(1, r.gateway.signOutCount)
        }
    }

    @Test fun `400 and contract violations never open the gate`() = runTest {
        for (script in listOf(listOf(http(400, "VALIDATION_FAILED")), listOf(net(FailureKind.BadResponse)))) {
            val r = rig(api = FakeApi(script))
            assertTrue(r.repo.signInWithGoogle("g") is AuthOutcome.Failed)
            assertFalse(r.everAuthenticated)
        }
    }

    // ------------------------------------------------------------------ temporary failures: Firebase kept, NOT authenticated

    @Test fun `503 keeps Firebase signed in, is NOT authenticated, shows BackendUnavailable, and Retry success authenticates`() = runTest {
        val r = rig(api = FakeApi(listOf(http(503, "IDENTITY_UNAVAILABLE"), http(503, "IDENTITY_UNAVAILABLE"), http(503, "IDENTITY_UNAVAILABLE"), ok())))
        r.gateway.googleResult = fbUser("fb-g", providers = listOf("google.com"))
        val out = r.repo.signInWithGoogle("g")
        assertTrue(out is AuthOutcome.BackendUnavailable)
        assertEquals(0, r.gateway.signOutCount)
        assertTrue(r.repo.authGate.value is AuthGate.BackendUnavailable)
        assertNull("not authenticated while the backend is down", r.repo.authUser.value)
        assertFalse(r.everAuthenticated)
        assertTrue(r.repo.retryBackend() is AuthOutcome.SignedIn)
        assertTrue(r.repo.authGate.value is AuthGate.Authenticated)
        assertEquals(BackendStatus.Ok, r.repo.backendStatus.value)
    }

    @Test fun `offline at login keeps the Firebase session and reports BackendUnavailable(Offline)`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.Offline))))
        val out = r.repo.signInWithGoogle("g") as AuthOutcome.BackendUnavailable
        assertEquals(TemporaryReason.Offline, out.reason)
        assertEquals(0, r.gateway.signOutCount)
        assertEquals(TemporaryReason.Offline, (r.repo.authGate.value as AuthGate.BackendUnavailable).reason)
    }

    @Test fun `retry that fails again stays BackendUnavailable and keeps Firebase`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.Offline))))
        r.repo.signInWithGoogle("g")
        assertTrue(r.repo.retryBackend() is AuthOutcome.BackendUnavailable)
        assertTrue(r.repo.authGate.value is AuthGate.BackendUnavailable); assertEquals(0, r.gateway.signOutCount)
    }

    @Test fun `retry that now answers 409 goes to the restricted LinkRequired state instead of authenticating`() = runTest {
        val r = rig(api = FakeApi(listOf(net(FailureKind.Offline), http(409, "ACCOUNT_LINK_REQUIRED"))))
        r.repo.signInWithGoogle("g")
        assertTrue(r.repo.retryBackend() is AuthOutcome.LinkRequired)
        assertTrue(r.repo.authGate.value is AuthGate.LinkRequired); assertFalse(r.everAuthenticated)
        assertNull(r.repo.authUser.value)
    }

    // ------------------------------------------------------------------ restart: verify before granting access

    @Test fun `restart with a persisted Firebase session shows Verifying first and grants access only after the backend accepts`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")), api = FakeApi(latencyMs = 500))
        assertEquals("persisted Firebase session alone is NOT access", AuthGate.Verifying(AuthUser("fb-1", "Name fb-1", "fb-1@example.invalid")), r.repo.authGate.value)
        assertNull(r.repo.authUser.value)
        advanceTimeBy(600)
        assertTrue(r.repo.authGate.value is AuthGate.Authenticated)
        assertEquals("a brand-new manager has no token, so it exchanged (nothing was persisted)", 1, r.api.exchangeCalls)
    }

    @Test fun `restart with the backend down keeps Firebase but never grants access`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")), api = FakeApi(listOf(http(503))))
        advanceTimeBy(10_000)
        assertTrue(r.repo.authGate.value is AuthGate.BackendUnavailable)
        assertNull(r.repo.authUser.value); assertEquals(0, r.gateway.signOutCount); assertFalse(r.everAuthenticated)
    }

    @Test fun `restart answered with 409 re-attempts the exchange and lands in LinkRequired, never granting access`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")), api = FakeApi(listOf(http(409, "ACCOUNT_LINK_REQUIRED"))))
        advanceTimeBy(10_000)
        assertTrue(r.repo.authGate.value is AuthGate.LinkRequired); assertFalse(r.everAuthenticated)
        assertEquals(1, r.api.exchangeCalls); assertEquals(0, r.gateway.signOutCount)
        assertEquals(LinkState.Idle, r.repo.linkState.value)
    }

    @Test fun `an unverified e-mail account restored from disk is signed out and never verified with the backend`() = runTest {
        val r = rig(user = fbUser("fb-1", verified = false, providers = listOf("password")))
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertEquals(1, r.gateway.signOutCount); assertEquals(0, r.api.exchangeCalls)
    }

    // ------------------------------------------------------------------ logout and isolation

    @Test fun `logout returns to SignedOut and clears the profile, the cached token, Firebase and Google`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")))
        runCurrent()
        assertTrue(r.repo.profile.value is ProfileState.Loaded)
        r.repo.logout()
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertNull(r.repo.authUser.value)
        assertEquals(ProfileState.Idle, r.repo.profile.value)
        assertEquals(SessionState.SignedOut, r.session.state.value)
        assertEquals(1, r.gateway.signOutCount); assertEquals(1, r.google.signOuts)
        assertEquals(SessionResult.SignedOut, r.session.ensureSession())
    }

    @Test fun `logout while a restore is verifying - the late backend answer never grants access`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")), api = FakeApi(latencyMs = 1_000))
        assertTrue(r.repo.authGate.value is AuthGate.Verifying)
        r.repo.logout()
        advanceTimeBy(5_000)
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertFalse(r.everAuthenticated)
    }

    @Test fun `account switch during an in-flight interactive login cannot publish the previous identity`() = runTest {
        val r = rig(api = FakeApi(latencyMs = 1_000))
        r.gateway.googleResult = fbUser("fb-A", providers = listOf("google.com"))
        val pending = async { r.repo.signInWithGoogle("g") }
        runCurrent()
        r.gateway.setUser(fbUser("fb-B", providers = listOf("google.com"))) // a different account appears mid-flight
        advanceTimeBy(5_000)
        val out = pending.await()
        assertTrue(out is AuthOutcome.Failed)
        assertFalse("A's exchange result must not open the gate for anyone", r.everAuthenticated)
    }

    @Test fun `a profile response that arrives after logout is never published`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")), api = FakeApi(latencyMs = 1_000))
        advanceTimeBy(1_500) // exchange done, profile request now in flight
        r.repo.logout()
        advanceTimeBy(5_000)
        assertEquals(ProfileState.Idle, r.repo.profile.value); assertEquals(AuthGate.SignedOut, r.repo.authGate.value)
    }

    @Test fun `account switch behind the app's back drops all state of the previous user`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")))
        runCurrent()
        r.gateway.setUser(fbUser("fb-2", providers = listOf("google.com")))
        runCurrent()
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value)
        assertEquals(ProfileState.Idle, r.repo.profile.value)
        assertEquals(SessionState.SignedOut, r.session.state.value)
    }

    @Test fun `sign-out elsewhere (revoked, deleted) clears the app state`() = runTest {
        val r = rig(user = fbUser("fb-1", providers = listOf("google.com")))
        runCurrent()
        r.gateway.setUser(null); runCurrent()
        assertEquals(AuthGate.SignedOut, r.repo.authGate.value); assertEquals(ProfileState.Idle, r.repo.profile.value)
    }

    @Test fun `the canonical id in the gate and profile comes from the backend, not the Firebase uid`() = runTest {
        val r = rig(user = fbUser("fb-uid-xyz", providers = listOf("google.com")), api = FakeApi(listOf(ok("neon-canonical")), listOf(ApiResult.Success(FakeApi.profile("neon-canonical")))))
        runCurrent()
        assertEquals("neon-canonical", (r.repo.authGate.value as AuthGate.Authenticated).userId)
        assertEquals("neon-canonical", (r.repo.profile.value as ProfileState.Loaded).profile.id)
        assertTrue(r.api.meTokens.single().startsWith("api-token-"))
    }
}
