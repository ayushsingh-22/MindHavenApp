package com.example.mindhaven.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindhaven.ui.theme.screens.mainScreen
import com.example.mindhaven.ui.theme.screens.welcomeScreen

@Composable
fun navigation(navController: NavHostController) {

    NavHost(navController = navController, startDestination = "welcome") {

         composable("welcome") { welcomeScreen(navController) }
         composable("mainScreen") { mainScreen(navController) }
    }

}