package com.example.mindhaven.model

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.example.mindhaven.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@Composable
fun rememberGoogleSignInLauncher(
    authViewModel: AuthViewModel,
    navController: NavHostController,
    onSuccessRoute: String
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val context = LocalContext.current

    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        if (data == null) {
            Toast.makeText(context, "No sign-in data returned", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    "Missing ID token. Did you call requestIdToken(...) when configuring GoogleSignInOptions?",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            authViewModel.signInWithGoogle(idToken) { success: Boolean, error: String? ->
                if (success) {
                    navController.navigate(onSuccessRoute) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    Toast.makeText(
                        context,
                        error ?: "Google sign-in failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(
                context,
                e.message ?: "Google sign-in failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}