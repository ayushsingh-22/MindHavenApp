package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val user by authViewModel.authState.observeAsState()

    // React to auth state changes
    LaunchedEffect(user) {
        if (user != null) {
            // User is logged in → navigate to Main Screen
            navController.navigate(Screen.main.route) {
                popUpTo(0) // Clear backstack
            }
        } else {
            // User is logged out → navigate to Welcome Screen
            navController.navigate(Screen.welcome.route) {
                popUpTo(0) // Clear backstack
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (user != null) Screen.main.route else Screen.welcome.route
    ) {
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
