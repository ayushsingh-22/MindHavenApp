package com.example.mindhaven.ui.theme.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.components.LottieAnimationView
import com.example.mindhaven.ui.theme.components.MusicPlayer
import com.example.mindhaven.ui.theme.components.SoundCard
import com.example.mindhaven.viewmodel.SleepViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun sleepScreen(viewModel: SleepViewModel) {
    val selectedSound = viewModel.selectedSound
    var selectedTimer by remember { mutableLongStateOf(15L * 60 * 1000) }
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity

    BackHandler {
        if (selectedSound != null) {
            // If in player view, go back to list
            viewModel.stop()
            viewModel.clearSelection()
        } else {
            // If in list view, show exit confirmation
            showExitDialog = true
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to close the app?") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    activity?.finish()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LottieAnimationView(
            animationResId = R.raw.sleepmeditation,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        )

        if (selectedSound == null) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.sleepSounds) { soundItem ->
                    SoundCard(
                        soundItem = soundItem,
                        isPlaying = false,
                        onPlayPause = {
                            viewModel.selectSound(soundItem)
                            viewModel.play(selectedTimer)
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Player view content
            }
        }

        selectedSound?.let {
            MusicPlayer(
                sound = it,
                isPlaying = viewModel.isPlaying,
                onPlayPause = {
                    if (viewModel.isPlaying) viewModel.pause()
                    else viewModel.play(selectedTimer)
                },
                onStop = {
                    viewModel.stop()
                    viewModel.clearSelection()
                },
                onTimerChange = { selectedTimer = it },
                selectedTimer = selectedTimer
            )
        }
    }
}