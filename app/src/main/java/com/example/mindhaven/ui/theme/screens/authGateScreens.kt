package com.example.mindhaven.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindhaven.data.auth.AuthGate
import com.example.mindhaven.data.auth.TemporaryReason
import com.example.mindhaven.data.auth.unavailableMessage
import com.example.mindhaven.ui.theme.LavenderBlue
import com.example.mindhaven.ui.theme.LavenderBlush
import com.example.mindhaven.ui.theme.MediumPurple
import com.example.mindhaven.ui.theme.PaleLavender
import com.example.mindhaven.ui.theme.TwilightLavender

private val gateBackground = Brush.verticalGradient(listOf(LavenderBlush, PaleLavender, LavenderBlue))

/** Shown while a restored (or retried) session is being verified with the backend. */
@Composable
fun LaunchScreen() {
    Box(modifier = Modifier.fillMaxSize().background(gateBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MindHaven", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MediumPurple)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = MediumPurple)
            Spacer(Modifier.height(16.dp))
            Text("Connecting to your account…", fontSize = 15.sp, color = TwilightLavender)
        }
    }
}

/**
 * The backend could not verify the account (offline, timeout, 429, 503...).
 * The user is NOT inside the app; they can retry (bounded, see BackendSessionManager) or sign out.
 */
@Composable
fun ConnectionScreen(gate: AuthGate, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val retrying = gate is AuthGate.Verifying
    val unavailable = gate as? AuthGate.BackendUnavailable
    Box(modifier = Modifier.fillMaxSize().background(gateBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Can't reach your account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MediumPurple)
            Spacer(Modifier.height(12.dp))
            Text(
                text = unavailable?.let { unavailableMessage(it.reason) } ?: "Checking your account…",
                fontSize = 15.sp, color = TwilightLavender, textAlign = TextAlign.Center
            )
            unavailable?.retryAfterSeconds?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(8.dp))
                Text("You can try again in about $it seconds.", fontSize = 13.sp, color = TwilightLavender)
            }
            Spacer(Modifier.height(28.dp))
            if (retrying) {
                CircularProgressIndicator(color = MediumPurple)
            } else if (unavailable?.reason != TemporaryReason.NotConfigured) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
                ) { Text("Retry", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignOut, enabled = !retrying) { Text("Sign out", color = MediumPurple) }
        }
    }
}
