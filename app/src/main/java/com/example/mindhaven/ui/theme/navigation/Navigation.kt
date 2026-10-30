package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.ui.theme.screens.ConnectionScreen
import com.example.mindhaven.ui.theme.screens.LaunchScreen
import com.example.mindhaven.ui.theme.screens.mainScreen
import com.example.mindhaven.ui.theme.screens.profileScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.emailLogin
import com.example.mindhaven.ui.theme.screens.userRegistration.registrationScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.SignUp
import com.example.mindhaven.ui.theme.screens.welcomeScreen
import com.example.mindhaven.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object registration : Screen("registration")
    object emailLogin : Screen("emailLogin")
    object welcome : Screen("welcome")
    object main : Screen("main")
    object signUp : Screen("signUp")
    object profile : Screen("profile")
    /** Verifying a restored/retried Firebase session with the backend. */
    object launch : Screen(LAUNCH_ROUTE)
    /** Firebase signed in, backend unreachable: Retry / Sign out. */
    object connection : Screen(CONNECTION_ROUTE)
    /** Firebase signed in, backend answered ACCOUNT_LINK_REQUIRED: restricted linking screen. */
    object linkAccount : Screen(LINK_ROUTE)
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // ONE source of truth: the authentication gate (Firebase + backend). Screens never navigate on auth themselves.
    val gate by authViewModel.authGate.observeAsState(authViewModel.authGate.value ?: AuthGate.SignedOut)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Keyed on (gate, current route): re-runs only when one of them changes, and authNavigationCommand returns null
    // once the destination matches, so duplicate emissions / recompositions never navigate twice.
    LaunchedEffect(gate, currentRoute) {
        val command = authNavigationCommand(gate, currentRoute) ?: return@LaunchedEffect
        navController.navigate(command.route) {
            if (command.clearBackStack) popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Fixed start destination: the graph is never rebuilt when the auth state changes.
    NavHost(
        navController = navController,
        startDestination = Screen.launch.route
    ) {
        composable(Screen.launch.route) {
            LaunchScreen()
        }

        composable(Screen.connection.route) {
            ConnectionScreen(
                gate = gate,
                onRetry = { authViewModel.retryBackend() },
                onSignOut = { authViewModel.logout() }
            )
        }

        composable(Screen.registration.route) {
            registrationScreen(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.main.route
            )
        }

        composable(Screen.emailLogin.route) {
            emailLogin(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.main.route
            )
        }

        composable(Screen.signUp.route) {
            SignUp(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.main.route
            )
        }

        composable(Screen.welcome.route) {
            welcomeScreen(navController = navController)
        }

        composable(Screen.profile.route) {
            profileScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.main.route) {
            mainScreen(navController)
        }
    }
}
