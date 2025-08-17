package com.example.mindhaven.ui.theme.screens.userRegistration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.navigation.NavHostController
import com.example.mindhaven.R
import com.example.mindhaven.model.rememberGoogleSignInLauncher
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.ui.theme.components.LottieAnimationView
import com.example.mindhaven.ui.theme.navigation.Screen
import com.example.mindhaven.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@Composable
fun RegistrationScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onSuccessRoute: String // 👈 NEW PARAMETER
) {
    val context = LocalContext.current
    val user by authViewModel.authState.observeAsState()

    // Google Sign-In launcher (shared logic)
    val launcher = rememberGoogleSignInLauncher(
        authViewModel = authViewModel,
        navController = navController,
        onSuccessRoute = onSuccessRoute
    )

    // Google Sign-In Client
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    // Navigate if user is already authenticated
    LaunchedEffect(user) {
        if (user != null) {
            navController.navigate(onSuccessRoute) {
                popUpTo(0) { inclusive = true }
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
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Title
            Text(
                text = "MindHaven",
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MediumPurple
                )
            )

            LottieAnimationView(
                animationResId = R.raw.yoga,
                alignment = Alignment.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Find your peace of mind",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TwilightLavender
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Google Sign-In Button
            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                elevation = ButtonDefaults.buttonElevation(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.google),
                        contentDescription = "Google Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Continue with Google",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Email emailLogin Button
            Button(
                onClick = { navController.navigate(Screen.emailLogin.route) },
                colors = ButtonDefaults.buttonColors(containerColor = MediumPurple),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                elevation = ButtonDefaults.buttonElevation(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.email),
                        contentDescription = "Email Logo",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Continue with Email",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Decorative Info Text
            Text(
                text = "Safe, Simple & Secure",
                fontSize = 14.sp,
                color = Plum,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
