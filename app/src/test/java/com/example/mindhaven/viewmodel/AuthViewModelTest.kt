package com.example.mindhaven.viewmodel

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.data.auth.BackendSessionManager
import com.example.mindhaven.data.auth.DefaultAuthRepository
import com.example.mindhaven.data.auth.GoogleSignInIntentSource
import com.example.mindhaven.data.auth.LinkState
import com.example.mindhaven.testsupport.FakeApi
import com.example.mindhaven.testsupport.FakeApi.Companion.http
import com.example.mindhaven.testsupport.FakeGateway
import com.example.mindhaven.testsupport.FakeGoogle
import com.example.mindhaven.testsupport.fbUser
import com.example.mindhaven.ui.theme.navigation.NavCommand
import com.example.mindhaven.ui.theme.navigation.authNavigationCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End to end below the UI: AuthViewModel (the object the Compose screens observe) + the REAL repository and session
 * manager, with fake Firebase and backend. Proves the LiveData the NavHost reacts to changes right after the exchange.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule val instantLiveData = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun reset() = Dispatchers.resetMain()

    private fun TestScope.viewModel(gateway: FakeGateway, api: FakeApi): AuthViewModel {
        val session = BackendSessionManager(gateway, api, scope = null, now = { testScheduler.currentTime }, pause = { kotlinx.coroutines.delay(it) })
        val repo = DefaultAuthRepository(gateway, session, FakeGoogle(), backgroundScope, now = { testScheduler.currentTime })
        return AuthViewModel(repo, GoogleSignInIntentSource { Intent() })
    }

    @Test fun `email login updates authGate LiveData to Authenticated as soon as the exchange succeeds`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { signInResult = fbUser("fb-1", verified = true) }
        val vm = viewModel(gateway, FakeApi())
        advanceUntilIdle()
        assertEquals(AuthGate.SignedOut, vm.authGate.value)
        vm.login("a@example.invalid", "pw-123456")
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.Authenticated)
        assertEquals("fb-1", vm.authState.value?.uid)
        assertEquals("the navigation decision on the login screen is: go to main", NavCommand("main"), authNavigationCommand(vm.authGate.value!!, "emailLogin"))
        assertFalse(vm.loading.value!!)
        assertNull(vm.error.value)
    }

    @Test fun `google login callback reports success and the gate opens`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { googleResult = fbUser("fb-g", providers = listOf("google.com")) }
        val vm = viewModel(gateway, FakeApi())
        var callback: Pair<Boolean, String?>? = null
        vm.signInWithGoogle("google-token") { ok, err -> callback = ok to err }
        advanceUntilIdle()
        assertEquals(true to null, callback)
        assertTrue(vm.authGate.value is AuthGate.Authenticated)
    }

    @Test fun `409 navigates from the login screen to the restricted linking screen, never to main, without an error toast`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { googleResult = fbUser("fb-g", providers = listOf("google.com")) }
        val vm = viewModel(gateway, FakeApi(listOf(http(409, "ACCOUNT_LINK_REQUIRED"))))
        var callback: Pair<Boolean, String?>? = null
        vm.signInWithGoogle("g") { ok, err -> callback = ok to err }
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.LinkRequired)
        assertEquals(NavCommand("linkAccount"), authNavigationCommand(vm.authGate.value!!, "emailLogin"))
        assertNull(vm.authState.value)
        assertEquals(false to null, callback)
        assertNull(vm.error.value)
    }

    @Test fun `503 keeps Firebase, shows the connection screen, and Retry success navigates to main`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { googleResult = fbUser("fb-g", providers = listOf("google.com")) }
        val vm = viewModel(gateway, FakeApi(listOf(http(503), http(503), http(503), FakeApi.ok())))
        vm.signInWithGoogle("g") { _, _ -> }
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.BackendUnavailable)
        assertEquals(NavCommand("connection"), authNavigationCommand(vm.authGate.value!!, "emailLogin"))
        assertEquals(0, gateway.signOutCount)
        vm.retryBackend()
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.Authenticated)
        assertEquals(NavCommand("main"), authNavigationCommand(vm.authGate.value!!, "connection"))
    }

    @Test fun `restart starts in Verifying (launch screen), then opens only after the backend accepts`() = runTest(dispatcher) {
        val gateway = FakeGateway(fbUser("fb-1", providers = listOf("google.com")))
        val vm = viewModel(gateway, FakeApi(latencyMs = 300))
        assertTrue("first frame after restart: verifying, not Home", vm.authGate.value is AuthGate.Verifying)
        assertNull(authNavigationCommand(vm.authGate.value!!, "launch"))
        advanceTimeBy(100); runCurrent()
        assertTrue("still verifying while the backend has not answered", vm.authGate.value is AuthGate.Verifying)
        advanceTimeBy(1_000); runCurrent() // background work: advance virtual time explicitly
        assertTrue(vm.authGate.value is AuthGate.Authenticated)
    }

    @Test fun `logout goes back to SignedOut (welcome with a cleared back stack)`() = runTest(dispatcher) {
        val gateway = FakeGateway(fbUser("fb-1", providers = listOf("google.com")))
        val vm = viewModel(gateway, FakeApi())
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.Authenticated)
        vm.logout()
        advanceUntilIdle()
        assertEquals(AuthGate.SignedOut, vm.authGate.value)
        assertEquals(NavCommand("welcome", clearBackStack = true), authNavigationCommand(vm.authGate.value!!, "profile"))
    }

    @Test fun `linking through the ViewModel - duplicate taps start once, success navigates to main, cancel signs out`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { googleResult = fbUser("fb-g", providers = listOf("google.com")) }
        val api = FakeApi(listOf(http(409, "ACCOUNT_LINK_REQUIRED"), FakeApi.ok("neon-web"))).apply {
            latencyMs = 200; linkCompleteScript = listOf(FakeApi.linked("neon-web"))
        }
        val vm = viewModel(gateway, api)
        vm.signInWithGoogle("g") { _, _ -> }
        advanceUntilIdle()
        assertTrue(vm.authGate.value is AuthGate.LinkRequired)
        assertEquals(LinkState.Idle, vm.linkState.value)
        vm.linkAccount("web-pw", consent = true); vm.linkAccount("web-pw", consent = true); vm.linkAccount("web-pw", consent = true)
        advanceUntilIdle()
        assertEquals(1, api.linkStartCalls); assertEquals(1, api.linkCompleteCalls)
        assertEquals("neon-web", (vm.authGate.value as AuthGate.Authenticated).userId)
        assertEquals(NavCommand("main"), authNavigationCommand(vm.authGate.value!!, "linkAccount"))

        // A second user who cancels instead.
        val g2 = FakeGateway().apply { googleResult = fbUser("fb-h", providers = listOf("google.com")) }
        val vm2 = viewModel(g2, FakeApi(listOf(http(409, "ACCOUNT_LINK_REQUIRED"))))
        vm2.signInWithGoogle("g") { _, _ -> }
        advanceUntilIdle()
        vm2.cancelLinking()
        advanceUntilIdle()
        assertEquals(AuthGate.SignedOut, vm2.authGate.value); assertEquals(1, g2.signOutCount)
        assertEquals(NavCommand("welcome"), authNavigationCommand(vm2.authGate.value!!, "linkAccount"))
    }
}
