package com.example.mindhaven.ui.theme.screens.userRegistration

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.ui.theme.components.LottieAnimationView
import com.example.mindhaven.viewmodel.AuthViewModel


@Composable
fun registrationScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    var showEmailDialog by remember { mutableStateOf(false) }

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
                animationResId = R.raw.yoga,
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

            // 🔹 Google login (not wired yet)
            Button(
                onClick = { /* TODO: Implement Google Sign-In */ },
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

            // 🔹 Email Login Button
            Button(
                onClick = { showEmailDialog = true },
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

        // 🔹 Email/Password dialog
        if (showEmailDialog) {
            EmailLoginDialog(
                onDismiss = { showEmailDialog = false },
                authViewModel = authViewModel
            )
        }
    }
}

@Composable
fun EmailLoginDialog(
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val error by authViewModel.error.observeAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Login / Sign Up with Email") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    authViewModel.login(email, password)
                    onDismiss()
                }) {
                    Text("Login")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    authViewModel.signUp(email, password)
                    onDismiss()
                }) {
                    Text("Sign Up")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
