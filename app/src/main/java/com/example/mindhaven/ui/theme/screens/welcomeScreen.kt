package com.example.mindhaven.ui.theme.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.Orchid
import com.example.mindhaven.ui.theme.Periwinkle
import com.example.mindhaven.viewmodel.AuthViewModel

@Composable
fun welcomeScreen(navController: NavHostController, authViewModel: AuthViewModel) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Periwinkle)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MeditationLottieAnimation()

            Button(
                onClick = { navController.navigate("main") },
                modifier = Modifier
                    .offset(y = (-50).dp)
                    .size(width = 220.dp, height = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orchid)
            ) {
                Text(
                    text = "Begin Your Journey",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button
            Button(
                onClick = {
                    authViewModel.logout()
                    Toast.makeText(context, "You logged out successfully", Toast.LENGTH_SHORT).show()
                    navController.navigate("registration") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                },
                modifier = Modifier
                    .size(width = 220.dp, height = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))
            ) {
                Text(
                    text = "Logout",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}


@Composable
fun MeditationLottieAnimation() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.yoga))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier
            .size(800.dp)
            .rotate(-2f)
    )
}
