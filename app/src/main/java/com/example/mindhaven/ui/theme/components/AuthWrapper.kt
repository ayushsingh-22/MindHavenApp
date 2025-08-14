package com.example.mindhaven.ui.theme.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.mindhaven.data.repository.AuthState
import com.example.mindhaven.viewmodel.AuthViewModel

@Composable
fun AuthWrapper(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    authenticatedContent: @Composable () -> Unit,
    unauthenticatedContent: @Composable () -> Unit
) {
    val authState = authViewModel.authState.collectAsState(initial = AuthState.Loading)

    when (authState.value) {
        is AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Authenticated -> {
            authenticatedContent()
        }
        is AuthState.Unauthenticated -> {
            unauthenticatedContent()
        }
        is AuthState.Error -> {
            // In case of error, show unauthenticated content
            unauthenticatedContent()
        }
    }
}
