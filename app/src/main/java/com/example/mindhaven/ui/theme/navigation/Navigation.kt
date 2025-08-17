package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindhaven.ui.theme.screens.mainScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.emailLogin
import com.example.mindhaven.ui.theme.screens.userRegistration.RegistrationScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.SignUp
import com.example.mindhaven.ui.theme.screens.welcomeScreen
import com.example.mindhaven.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Registration : Screen("registration")
    object emailLogin : Screen("emailLogin")
    object Welcome : Screen("welcome")
    object Main : Screen("main")
    object signUp : Screen("signUp")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val user by authViewModel.authState.observeAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(Screen.Registration.route) {
            RegistrationScreen(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.Main.route
            )
        }

        composable(Screen.emailLogin.route) {
            emailLogin(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.Main.route
            )
        }

        composable(Screen.signUp.route) {
            SignUp(
                navController = navController,
                authViewModel = authViewModel,
                onSuccessRoute = Screen.Main.route
            )
        }

        composable(Screen.Welcome.route) {
            welcomeScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.Main.route) {
            mainScreen(navController)
        }
    }
}