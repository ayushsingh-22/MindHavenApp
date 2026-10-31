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
import com.example.mindhaven.viewmodel.AuthFlowViewModel
import com.example.mindhaven.ui.theme.screens.userRegistration.LoginFlowScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.VerifyEmailScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.ForgotPasswordScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.ResetPasswordScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.ChangePasswordScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.DeleteAccountScreen
import androidx.compose.runtime.collectAsState

sealed class Screen(val route: String) {
    object registration : Screen("registration")
    object emailLogin : Screen("emailLogin")
    object welcome : Screen("welcome")
    object main : Screen("main")
    object signUp : Screen("signUp")
    object profile : Screen("profile")
    object launch : Screen(LAUNCH_ROUTE)
    object connection : Screen(CONNECTION_ROUTE)
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    flow: AuthFlowViewModel = hiltViewModel()
) {
    val flowUi by flow.ui.collectAsState()
    LaunchedEffect(flowUi.destination) {
        flowUi.destination?.let { route -> flow.clearNavigation(); navController.navigate(route) { launchSingleTop = true } }
    }
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
            LoginFlowScreen(flow, onForgot = { flow.resetMessage(); navController.navigate("forgotPassword") }, onRegister = { flow.resetMessage(); navController.navigate("signUp") })
        }

        composable(Screen.signUp.route) {
            SignUp(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.main.route, flow = flow
            )
        }

        composable("verifyEmail") { VerifyEmailScreen(flow) }
        composable("forgotPassword") { ForgotPasswordScreen(flow) }
        composable("resetPassword") { ResetPasswordScreen(flow) }
        composable("changePassword") { ChangePasswordScreen(flow) }
        composable("deleteAccount") { DeleteAccountScreen(flow) }

        composable(Screen.welcome.route) {
            welcomeScreen(navController = navController)
        }

        composable(Screen.profile.route) {
            profileScreen(
                navController = navController,
                authViewModel = authViewModel, flow = flow
            )
        }

        composable(Screen.main.route) {
            mainScreen(navController)
        }
    }
}
