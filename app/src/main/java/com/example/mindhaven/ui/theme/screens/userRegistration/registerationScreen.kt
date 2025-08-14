package com.example.mindhaven.ui.theme.screens.userRegistration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.ui.theme.components.LottieAnimationView
import com.example.mindhaven.utils.GoogleSignInHelper
import com.example.mindhaven.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun registrationScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val googleSignInHelper = GoogleSignInHelper(context)
        googleSignInHelper.handleSignInResult(data) { signInResult ->
            signInResult.fold(
                onSuccess = { idToken ->
                    authViewModel.signInWithGoogleIdToken(idToken)
                },
                onFailure = { exception ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Google Sign-In failed: ${exception.message}",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            )
        }
    }

    // Observe auth events
    LaunchedEffect(Unit) {
        authViewModel.uiEvent.collect { event ->
            when (event) {
                is AuthViewModel.AuthUiEvent.NavigateToHome -> {
                    navController.navigate("home") {
                        popUpTo("registration") { inclusive = true }
                    }
                }
                is AuthViewModel.AuthUiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Long
                    )
                }
                is AuthViewModel.AuthUiEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Long
                    )
                }
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LavenderBlush, PaleLavender, LavenderBlue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                "MindHaven",
                fontFamily = loraText,
                color = MediumPurple,
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            LottieAnimationView(
                animationResId = R.raw.mindfulness,
                alignment = Alignment.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Find your peace of mind",
                fontFamily = loraText,
                color = TwilightLavender,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Google Sign-In Button
            Button(
                onClick = {
                    val googleSignInHelper = GoogleSignInHelper(context)
                    val signInClient = googleSignInHelper.initializeGoogleSignIn()
                    val signInIntent = googleSignInHelper.getSignInIntent()
                    
                    if (signInIntent != null) {
                        googleSignInLauncher.launch(signInIntent)
                    } else {
                        // Fallback to OAuth flow
                        authViewModel.signInWithGoogle()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                elevation = ButtonDefaults.buttonElevation(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.google),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Continue with Google",
                        fontFamily = loraText,
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Email Login Button
            Button(
                onClick = { navController.navigate("emailLogin") },
                colors = ButtonDefaults.buttonColors(containerColor = MediumPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                elevation = ButtonDefaults.buttonElevation(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.email),
                        contentDescription = "Email Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Continue with Email",
                        fontFamily = loraText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}