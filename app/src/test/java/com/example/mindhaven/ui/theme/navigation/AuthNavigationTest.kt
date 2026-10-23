package com.example.mindhaven.ui.theme.navigation

import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.data.auth.AuthUser
import com.example.mindhaven.data.auth.TemporaryReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the single auth-navigation decision used by AppNavigation (pure; no Android runtime). */
class AuthNavigationTest {
    private val userA = AuthUser("fb-A", "A", "a@example.invalid")
    private val userB = AuthUser("fb-B", "B", "b@example.invalid")
    private val authA = AuthGate.Authenticated(userA, "neon-A")
    private val authB = AuthGate.Authenticated(userB, "neon-B")

    /**
     * Minimal stand-in for NavHost + the LaunchedEffect in AppNavigation: applies every command (clearing the back stack
     * as the real popUpTo(graph){inclusive} does) and records navigations. Back pops one entry.
     */
    private class FakeNav(start: String = LAUNCH_ROUTE) {
        val stack = ArrayDeque(listOf(start))
        val navigations = mutableListOf<String>()
        val current get() = stack.lastOrNull()
        fun onState(gate: AuthGate) {
            // The real effect re-runs when the route changes; loop until the decision settles (it must settle at once).
            repeat(3) {
                val cmd = authNavigationCommand(gate, current) ?: return
                if (cmd.clearBackStack) stack.clear()
                stack.addLast(cmd.route); navigations += cmd.route
            }
            error("navigation did not settle")
        }
        fun userOpens(route: String) { stack.addLast(route) } // a screen's own non-auth navigation (e.g. welcome -> emailLogin)
        fun back(): String? { stack.removeLastOrNull(); return current }
    }

    @Test fun `cold start without a session goes from launch to welcome`() {
        val nav = FakeNav()
        nav.onState(AuthGate.SignedOut)
        assertEquals(listOf("welcome"), nav.navigations)
    }

    @Test fun `email login on the login screen goes to main immediately on success and clears the back stack`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut)
        nav.userOpens("registration"); nav.userOpens("emailLogin")
        nav.onState(AuthGate.SignedOut) // while signing in: stays on the login form (it shows the progress/error)
        assertEquals("emailLogin", nav.current)
        nav.onState(authA) // exchange succeeded
        assertEquals("main", nav.current)
        assertEquals(listOf("main"), nav.stack.toList())
        assertNull("Back cannot return to the login screens", nav.back())
    }

    @Test fun `google login from the registration screen goes to main immediately`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("registration")
        nav.onState(authA)
        assertEquals(listOf("main"), nav.stack.toList())
    }

    @Test fun `failed, blocked or 409 sign-ins keep the user on the login screen (never main)`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("emailLogin")
        nav.onState(AuthGate.SignedOut) // Blocked/Failed/VerificationRequired all publish SignedOut
        assertEquals("emailLogin", nav.current)
        assertTrue("main" !in nav.navigations)
    }

    @Test fun `503 or offline goes to the connection screen, never to main`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("emailLogin")
        nav.onState(AuthGate.BackendUnavailable(userA, TemporaryReason.ServiceUnavailable, null))
        assertEquals(listOf(CONNECTION_ROUTE), nav.stack.toList())
        assertTrue("main" !in nav.navigations)
    }

    @Test fun `retry from the connection screen stays there while verifying, then goes to main on success`() {
        val nav = FakeNav(); nav.onState(AuthGate.BackendUnavailable(userA, TemporaryReason.Offline, null))
        nav.onState(AuthGate.Verifying(userA))
        assertEquals("spinner on the same screen, no navigation", CONNECTION_ROUTE, nav.current)
        nav.onState(authA)
        assertEquals(listOf("main"), nav.stack.toList())
    }

    @Test fun `app restart verifies on the launch screen before main`() {
        val nav = FakeNav()
        nav.onState(AuthGate.Verifying(userA))
        assertEquals("no navigation needed, launch already shows the progress", LAUNCH_ROUTE, nav.current)
        assertTrue(nav.navigations.isEmpty())
        nav.onState(authA)
        assertEquals(listOf("main"), nav.stack.toList())
    }

    @Test fun `app restart with the backend down shows the connection screen`() {
        val nav = FakeNav(); nav.onState(AuthGate.Verifying(userA))
        nav.onState(AuthGate.BackendUnavailable(userA, TemporaryReason.Timeout, null))
        assertEquals(listOf(CONNECTION_ROUTE), nav.stack.toList())
    }

    @Test fun `logout from profile returns to welcome and clears the back stack`() {
        val nav = FakeNav(); nav.onState(authA); nav.userOpens("profile")
        nav.onState(AuthGate.SignedOut)
        assertEquals(listOf("welcome"), nav.stack.toList())
        assertNull("Back cannot return into the app after logout", nav.back())
    }

    @Test fun `rapid repeated identical states navigate exactly once`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("emailLogin")
        repeat(10) { nav.onState(authA) }
        assertEquals(listOf("welcome", "main"), nav.navigations)
    }

    @Test fun `navigating inside the app (main to profile) is never undone by auth navigation`() {
        val nav = FakeNav(); nav.onState(authA); nav.userOpens("profile")
        nav.onState(authA)
        assertEquals("profile", nav.current)
    }

    @Test fun `account switch passes through SignedOut and cannot keep the previous user's screens`() {
        val nav = FakeNav(); nav.onState(authA); nav.userOpens("profile")
        nav.onState(AuthGate.SignedOut) // repository wipes A first
        assertEquals(listOf("welcome"), nav.stack.toList())
        nav.userOpens("emailLogin")
        nav.onState(authB)
        assertEquals(listOf("main"), nav.stack.toList())
    }

    @Test fun `no navigation before the graph is attached`() {
        assertNull(authNavigationCommand(authA, null))
    }

    @Test fun `every command clears the back stack`() {
        for (gate in listOf(AuthGate.SignedOut, AuthGate.Verifying(userA), authA, AuthGate.BackendUnavailable(userA, TemporaryReason.Offline, null))) {
            for (route in listOf(LAUNCH_ROUTE, CONNECTION_ROUTE, "welcome", "emailLogin", "main", "profile")) {
                authNavigationCommand(gate, route)?.let { assertTrue(it.clearBackStack) }
            }
        }
    }

    // ------------------------------------------------------------------ Phase 2B.5B.3: restricted account linking

    private val linkA = AuthGate.LinkRequired(userA)

    @Test fun `409 on the login screen goes straight to the linking screen with a cleared back stack`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("registration"); nav.userOpens("emailLogin")
        nav.onState(linkA)
        assertEquals(listOf(LINK_ROUTE), nav.stack.toList())
        assertNull("Back cannot return to the login form (or anywhere else)", nav.back())
    }

    @Test fun `LinkRequired never opens the app area, and repeated emissions navigate once`() {
        val nav = FakeNav(); nav.onState(AuthGate.SignedOut); nav.userOpens("emailLogin")
        repeat(10) { nav.onState(linkA) }
        assertEquals(listOf("welcome", LINK_ROUTE), nav.navigations)
        for (route in AUTHENTICATED_ROUTES) assertEquals(NavCommand(LINK_ROUTE), authNavigationCommand(linkA, route))
    }

    @Test fun `successful linking goes to main exactly once and Back cannot return to the password screen`() {
        val nav = FakeNav(); nav.onState(linkA)
        nav.onState(authA); nav.onState(authA)
        assertEquals(listOf("main"), nav.stack.toList())
        assertEquals(listOf(LINK_ROUTE, "main"), nav.navigations)
        assertNull(nav.back())
    }

    @Test fun `cancel from the linking screen returns to welcome`() {
        val nav = FakeNav(); nav.onState(linkA)
        nav.onState(AuthGate.SignedOut)
        assertEquals(listOf("welcome"), nav.stack.toList())
    }

    @Test fun `restart that ends in 409 goes launch then linking screen, never main`() {
        val nav = FakeNav(); nav.onState(AuthGate.Verifying(userA)); nav.onState(linkA)
        assertEquals(listOf(LINK_ROUTE), nav.stack.toList()); assertTrue("main" !in nav.navigations)
    }

    @Test fun `the linking route carries no arguments and every LinkRequired command clears the back stack`() {
        assertFalse(LINK_ROUTE.contains("{") || LINK_ROUTE.contains("?") || LINK_ROUTE.contains("/"))
        for (route in listOf(LAUNCH_ROUTE, CONNECTION_ROUTE, "welcome", "emailLogin", "main", "profile")) {
            authNavigationCommand(linkA, route)?.let { assertTrue(it.clearBackStack); assertEquals(LINK_ROUTE, it.route) }
        }
    }
}
