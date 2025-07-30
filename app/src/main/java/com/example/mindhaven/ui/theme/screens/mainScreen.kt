package com.example.mindhaven.ui.theme.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.viewmodel.MeditationViewModel
import com.example.mindhaven.viewmodel.SleepViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mainScreen(navController: NavHostController) {
    val (selectedSection, setSelectedSection) = remember { mutableStateOf("meditation") }

    val meditationViewModel: MeditationViewModel = viewModel()
    val sleepViewModel: SleepViewModel = viewModel()

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
                modifier = Modifier.fillMaxWidth().size(height = 90.dp, width = 1.dp),
                title = {
                    Text(
                        "MindHaven",
                        fontFamily = loraText,
                        fontWeight = FontWeight.SemiBold,
                        color = Black
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PaleLavender
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = PaleLavender,
                contentColor = Black,
                modifier = Modifier.fillMaxWidth().size(height = 90.dp, width = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { setSelectedSection("meditation") },
                        modifier = Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == "meditation") ElectricLavender else PaleLavender
                        )
                    ) {
                        Text("Meditation", color = if (selectedSection == "meditation") White else Black)
                    }
                    Button(
                        onClick = { setSelectedSection("sleep") },
                        modifier = Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == "sleep") ElectricLavender else PaleLavender
                        )
                    ) {
                        Text("Sleep", color = if (selectedSection == "sleep") White else Black)
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
            verticalArrangement = Arrangement.Center
        ) {
            when (selectedSection) {
                "meditation" -> {
                    MeditationScreen(viewModel = meditationViewModel)
                }
                "sleep" -> {
                    sleepScreen(viewModel = sleepViewModel)
                }
            }
        }
    }
}
