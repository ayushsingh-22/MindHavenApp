package com.example.mindhaven.ui.theme.navigation

import com.example.mindhaven.data.auth.AuthGate

/** A navigation instruction derived from the authentication state. [clearBackStack]: drop everything below the target. */
data class NavCommand(val route: String, val clearBackStack: Boolean = true)

/** Routes reachable while signed out (the user may move freely between them; no auth navigation happens). */
val SIGNED_OUT_ROUTES = setOf("welcome", "registration", "emailLogin", "signUp", "verifyEmail", "forgotPassword", "resetPassword")

/** Routes of the authenticated app area. */
val AUTHENTICATED_ROUTES = setOf("main", "profile", "changePassword", "deleteAccount")

const val LAUNCH_ROUTE = "launch"
const val CONNECTION_ROUTE = "connection"

/**
 * The ONLY place that decides auth-driven navigation. Pure (no Android types) so it is unit-tested.
 *
 * Returns null when the current destination already matches the state, which makes the decision idempotent: repeated or
 * duplicate state emissions, recompositions and the navigation's own back-stack update never navigate twice.
 *
 *  - SignedOut          -> welcome (unless already on a signed-out screen, e.g. the login form showing an error)
 *  - Verifying          -> launch screen (or stay on the connection screen while a Retry runs)
 *  - Authenticated      -> main (unless already inside the app area)
 *  - BackendUnavailable -> connection screen (Retry / Sign out); never the app area
 *
 * Every command clears the back stack, so Back can never return to the login screens after success, nor into the app
 * after logout.
 */
fun authNavigationCommand(gate: AuthGate, currentRoute: String?): NavCommand? {
    if (currentRoute == null) return null // the graph is not attached yet; the effect re-runs once it is
    val target = when (gate) {
        AuthGate.SignedOut -> if (currentRoute in SIGNED_OUT_ROUTES) null else "welcome"
        is AuthGate.Verifying -> if (currentRoute == LAUNCH_ROUTE || currentRoute == CONNECTION_ROUTE || currentRoute in SIGNED_OUT_ROUTES) null else LAUNCH_ROUTE
        is AuthGate.Authenticated -> if (currentRoute in AUTHENTICATED_ROUTES) null else "main"
        is AuthGate.BackendUnavailable -> if (currentRoute == CONNECTION_ROUTE || currentRoute in SIGNED_OUT_ROUTES) null else CONNECTION_ROUTE
    }
    return target?.let { NavCommand(it) }
}
