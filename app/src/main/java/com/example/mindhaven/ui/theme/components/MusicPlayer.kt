package com.example.mindhaven.ui.theme.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mindhaven.model.SoundItem
import com.example.mindhaven.ui.theme.*
import coil.compose.AsyncImage


@Composable
fun MusicPlayer(
    sound: SoundItem,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onTimerChange: (Long) -> Unit,
    selectedTimer: Long = 15L * 60 * 1000
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = LavenderBlush),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = sound.imageUrl,
                    contentDescription = sound.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = sound.title,
                    style = Typography.titleMedium,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onPlayPause,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MediumPurple
                    )
                }

                IconButton(
                    onClick = onStop,
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = TwilightLavender
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    0L to "∞",
                    15L to "15 min",
                    30L to "30 min",
                    60L to "1 hr"
                ).forEach { (min, label) ->
                    val timerValue = min * 60 * 1000
                    val isSelected = timerValue == selectedTimer

                    Button(
                        onClick = { onTimerChange(timerValue) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ElectricLavender else PaleLavender,
                            contentColor = if (isSelected) Color.White else Color.Black
                        )
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}
