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
import com.example.mindhaven.ui.theme.screens.userRegistration.registrationScreen
import com.example.mindhaven.ui.theme.screens.welcomeScreen
import com.example.mindhaven.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Registration : Screen("registration")
    object Welcome : Screen("welcome")
    object Main : Screen("main")
}

@Composable
fun AppNavigation(navController: NavHostController, authViewModel: AuthViewModel = hiltViewModel()) {
    val user by authViewModel.authState.observeAsState()

    LaunchedEffect(user) {
        if (user != null) {
            navController.navigate(Screen.Welcome.route) {
                popUpTo(0)
            }
        } else {
            navController.navigate(Screen.Registration.route) {
                popUpTo(0)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (user == null) Screen.Registration.route else Screen.Welcome.route
    ) {
        composable(Screen.Registration.route) { registrationScreen(navController, authViewModel)}
        composable(Screen.Welcome.route) { welcomeScreen(navController, authViewModel)}
        composable(Screen.Main.route) { mainScreen(navController) }
    }
}