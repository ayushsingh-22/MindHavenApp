package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindhaven.data.repository.AuthState
import com.example.mindhaven.ui.theme.components.AuthWrapper
import com.example.mindhaven.ui.theme.screens.mainScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.EmailLogin
import com.example.mindhaven.ui.theme.screens.userRegistration.SignUpScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.registrationScreen
import com.example.mindhaven.ui.theme.screens.welcomeScreen
import com.example.mindhaven.viewmodel.AuthViewModel

@Composable
fun AuthAwareNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState = authViewModel.authState.collectAsState(initial = AuthState.Loading)
    
    val startDestination = when (authState.value) {
        is AuthState.Authenticated -> "mainScreen"
        else -> "registerationScreen"
    }
    
    NavHost(navController = navController, startDestination = startDestination) {
        // Authentication Screens
        composable("registerationScreen") { registrationScreen(navController) }
        composable("registration") { registrationScreen(navController) }
        composable("emailLogin") { EmailLogin(navController = navController) }
        composable("signup") { SignUpScreen(navController = navController) }
        
        // Authenticated Screens
        composable("welcome") { welcomeScreen(navController) }
        composable("mainScreen") { mainScreen(navController) }
        composable("home") { mainScreen(navController) }
    }
}

// Original navigation function (kept for compatibility)
@Composable
fun navigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "registerationScreen") {
        composable("welcome") { welcomeScreen(navController) }
        composable("mainScreen") { mainScreen(navController) }
        composable("home") { mainScreen(navController) }
        composable("registerationScreen") { registrationScreen(navController) }
        composable("registration") { registrationScreen(navController) }
        composable("emailLogin") { EmailLogin(navController = navController) }
        composable("signup") { SignUpScreen(navController = navController) }
    }
}
