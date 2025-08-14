package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindhaven.ui.theme.screens.mainScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.EmailLogin
import com.example.mindhaven.ui.theme.screens.userRegistration.SignUpScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.registrationScreen
import com.example.mindhaven.ui.theme.screens.welcomeScreen

@Composable
fun navigation( navController: NavHostController) {
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