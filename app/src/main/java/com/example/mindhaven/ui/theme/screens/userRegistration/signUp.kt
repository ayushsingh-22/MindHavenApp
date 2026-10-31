package com.example.mindhaven.ui.theme.screens.userRegistration

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.viewmodel.AuthViewModel
import com.example.mindhaven.viewmodel.AuthFlowViewModel
import androidx.compose.ui.text.input.VisualTransformation
import com.example.mindhaven.ui.theme.navigation.Screen

@Composable
fun SignUp(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onSuccessRoute: String,
    flow: AuthFlowViewModel
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val flowUi by flow.ui.collectAsState()
    val error = flowUi.message
    val loading = flowUi.busy

    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Navigation after a successful sign-in is driven by AuthViewModel.authGate in AppNavigation (single source of truth).

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Create Account 🚀",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MediumPurple
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign up to begin your wellness journey with MindHaven",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = TwilightLavender
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name", color = Color.Black)},
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it  },
                label = { Text("Email" , color = Color.Black) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.Black) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
              trailingIcon = {
                  IconButton(onClick = { passwordVisible = !passwordVisible }) {
                      Icon(
                          imageVector = if (passwordVisible)
                              androidx.compose.material.icons.Icons.Default.Visibility
                          else
                              androidx.compose.material.icons.Icons.Default.VisibilityOff,
                          contentDescription = if (passwordVisible) "Hide password" else "Show password",
                          tint = Color.Black
                      )
                  }
              }
            )


            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password", color = Color.Black) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
            )

            error?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("At least 12 characters; at most 72 UTF-8 bytes.", color = TwilightLavender)

            // Sign Up Button
            Button(
                onClick = {
                    if (password == confirmPassword) {
                        flow.register(name, email, password)
                    } else {

                        flow.showMessage("Passwords do not match.")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
            ) {
                Text(
                    if (loading) "Please wait…" else "Sign Up",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(20.dp))

            // emailLogin option
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Already have an account? ", color = TwilightLavender)
                Text(
                    "Login",
                    color = MediumPurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        navController.navigate("emailLogin")
                    }
                )
            }
        }
    }
}