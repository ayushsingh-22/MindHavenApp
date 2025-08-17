package com.example.mindhaven.ui.theme.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.viewmodel.MeditationViewModel
import com.example.mindhaven.viewmodel.SleepViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun mainScreen(navController: NavHostController) {
    val (selectedSection, setSelectedSection) = remember { mutableStateOf("meditation") }

    val meditationViewModel: MeditationViewModel = viewModel()
    val sleepViewModel: SleepViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    var greeting by remember { mutableStateOf("Hello") }

    // Animated greeting based on time or just simple animation
    LaunchedEffect(Unit) {
        val greetings = listOf("Hello", "Welcome Back", "Namaste 🙏")
        while (true) {
            for (g in greetings) {
                greeting = g
                delay(3000)
            }
        }
    }

    LaunchedEffect(selectedSection) {
        when (selectedSection) {
            "meditation" -> {
                sleepViewModel.stop()
                sleepViewModel.clearSelection()
            }
            "sleep" -> {
                meditationViewModel.stop()
                meditationViewModel.clearSelection()
            }
        }
    }

    Scaffold(
        containerColor = Periwinkle,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                title = {
                    Column {
                        Text(
                            text = "MindHaven",
                            fontFamily = loraText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = MediumPurple
                        )
                        Text(
                            text = greeting,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TwilightLavender
                        )
                    }
                },
                actions = {
                    // Profile Icon
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .background(LavenderBlush)
                            .clickable { navController.navigate("profile") },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_myplaces),
                            contentDescription = "Profile",
                            modifier = Modifier.size(35.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaleLavender)
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = PaleLavender,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth().height(90.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Meditation Button
                    Button(
                        onClick = { setSelectedSection("meditation") },
                        modifier = Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == "meditation") ElectricLavender else PaleLavender
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Text(
                            "Meditation",
                            color = if (selectedSection == "meditation") White else MediumPurple
                        )
                    }
                    // Sleep Button
                    Button(
                        onClick = { setSelectedSection("sleep") },
                        modifier = Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == "sleep") ElectricLavender else PaleLavender
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Text(
                            "Sleep",
                            color = if (selectedSection == "sleep") White else MediumPurple
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            when (selectedSection) {
                "meditation" -> MeditationScreen(viewModel = meditationViewModel)
                "sleep" -> sleepScreen(viewModel = sleepViewModel)
            }
        }
    }
}
