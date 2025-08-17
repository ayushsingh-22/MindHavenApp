package com.example.mindhaven.ui.theme.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
import com.example.mindhaven.R
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun profileScreen(navController: NavHostController, authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val user by authViewModel.authState.observeAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Enhanced user stats with more interactivity
    var meditationStreak by remember { mutableStateOf(15) }
    var totalHours by remember { mutableStateOf(42) }
    var badgesEarned by remember { mutableStateOf(7) }
    var sleepGoalHours by remember { mutableStateOf(8) }
    var currentMood by remember { mutableStateOf("😊") }
    var showMoodPicker by remember { mutableStateOf(false) }
    var showStatsDetail by remember { mutableStateOf(false) }

    // Achievement badges
    val badges = remember {
        listOf(
            Badge("🧘‍♂️", "Meditation Master", true),
            Badge("🔥", "7-Day Streak", true),
            Badge("⭐", "Night Owl", true),
            Badge("🌙", "Sleep Champion", false),
            Badge("💎", "Mindful Diamond", false),
            Badge("🏆", "Zen Master", false)
        )
    }

    // Daily tips rotation
    val dailyTips = remember {
        listOf(
            "Take a deep breath and relax your mind for 5 minutes.",
            "Try the 4-7-8 breathing technique before sleep.",
            "Practice gratitude by listing 3 things you're thankful for.",
            "Do a body scan meditation to release tension.",
            "Set a consistent bedtime routine for better sleep."
        )
    }
    var currentTipIndex by remember { mutableStateOf(0) }

    // Greeting based on time
    val greeting = remember {
        val hour = LocalTime.now().hour
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Profile",
                            fontWeight = FontWeight.Bold,
                            color = MediumPurple
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Mood selector
                        Text(
                            text = currentMood,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable { showMoodPicker = true }
                                .padding(8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaleLavender)
            )
        },
        containerColor = LavenderBlush
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Enhanced personalized greeting with time-based emoji
            AnimatedGreeting(greeting = greeting, userName = user?.displayName ?: "Meditator")

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced profile picture with level indicator
            ProfilePictureWithLevel(
                level = meditationStreak / 7 + 1,
                onClick = {
                    Toast.makeText(context, "Change profile picture clicked!", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced Stats Cards with animations
            AnimatedStatsRow(
                meditationStreak = meditationStreak,
                totalHours = totalHours,
                badgesEarned = badgesEarned,
                sleepGoal = sleepGoalHours,
                onStatsClick = { showStatsDetail = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Badges Section
            BadgesSection(badges = badges)

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Actions with icons
            QuickActionsSection(
                onEditProfile = {
                    Toast.makeText(context, "Edit Profile clicked!", Toast.LENGTH_SHORT).show()
                },
                onSettings = {
                    Toast.makeText(context, "Settings clicked!", Toast.LENGTH_SHORT).show()
                },
                onSleepMode = {
                    Toast.makeText(context, "Sleep mode activated!", Toast.LENGTH_SHORT).show()
                },
                onLogout = {
                    authViewModel.logout()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    navController.navigate("welcome") {
                        popUpTo(0)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced daily tip card with tip rotation
            InteractiveDailyTipCard(
                dailyTip = dailyTips[currentTipIndex],
                onNextTip = {
                    currentTipIndex = (currentTipIndex + 1) % dailyTips.size
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Enhanced Floating Action Buttons
        FloatingActionButtons(
            onQuickMeditation = {
                Toast.makeText(context, "Starting Quick Meditation...", Toast.LENGTH_SHORT).show()
            },
            onSleepSession = {
                Toast.makeText(context, "Starting Sleep Session...", Toast.LENGTH_SHORT).show()
            }
        )

        // Mood Picker Dialog
        if (showMoodPicker) {
            MoodPickerDialog(
                currentMood = currentMood,
                onMoodSelected = { mood ->
                    currentMood = mood
                    showMoodPicker = false
                },
                onDismiss = { showMoodPicker = false }
            )
        }

        // Stats Detail Dialog
        if (showStatsDetail) {
            StatsDetailDialog(
                meditationStreak = meditationStreak,
                totalHours = totalHours,
                badgesEarned = badgesEarned,
                sleepGoal = sleepGoalHours,
                onDismiss = { showStatsDetail = false }
            )
        }
    }
}

@Composable
fun AnimatedGreeting(greeting: String, userName: String) {
    val timeEmoji = when (greeting) {
        "Good Morning" -> "🌅"
        "Good Afternoon" -> "☀️"
        "Good Evening" -> "🌇"
        else -> "🌙"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "greeting")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Text(
        text = "$greeting, $userName $timeEmoji",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MediumPurple,
        textAlign = TextAlign.Center,
        modifier = Modifier.scale(scale)
    )
}

@Composable
fun ProfilePictureWithLevel(level: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Periwinkle, LightMagenta),
                        radius = 200f
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
        }

        // Level badge
        Badge(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-10).dp)
        ) {
            Text(
                text = "Lv.$level",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun AnimatedStatsRow(
    meditationStreak: Int,
    totalHours: Int,
    badgesEarned: Int,
    sleepGoal: Int,
    onStatsClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStatsClick() }
    ) {
        AnimatedStatCard(title = "Streak", value = "$meditationStreak", unit = "days", icon = "🔥")
        AnimatedStatCard(title = "Hours", value = "$totalHours", unit = "h", icon = "⏰")
        AnimatedStatCard(title = "Badges", value = "$badgesEarned", unit = "", icon = "🏅")
        AnimatedStatCard(title = "Sleep Goal", value = "$sleepGoal", unit = "h", icon = "😴")
    }
}

@Composable
fun AnimatedStatCard(title: String, value: String, unit: String, icon: String) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .height(100.dp)
            .width(80.dp)
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = !isPressed
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightLavender)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = "$value$unit",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Heliotrope
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = TwilightLavender,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BadgesSection(badges: List<Badge>) {
    Column {
        Text(
            text = "Achievements 🏆",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MediumPurple,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(badges) { badge ->
                BadgeItem(badge = badge)
            }
        }
    }
}

@Composable
fun BadgeItem(badge: Badge) {
    Card(
        modifier = Modifier.size(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isEarned) LightLavender else Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = badge.icon,
                fontSize = 24.sp,
                modifier = Modifier.alpha(if (badge.isEarned) 1f else 0.3f)
            )
            Text(
                text = badge.name,
                fontSize = 8.sp,
                color = if (badge.isEarned) TwilightLavender else Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

@Composable
fun QuickActionsSection(
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onSleepMode: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ActionButtonWithIcon(
                title = "Edit Profile",
                icon = Icons.Default.Person,
                color = Orchid,
                modifier = Modifier.weight(1f),
                onClick = onEditProfile
            )
            ActionButtonWithIcon(
                title = "Settings",
                icon = Icons.Default.Settings,
                color = MediumPurple,
                modifier = Modifier.weight(1f),
                onClick = onSettings
            )
        }

        ActionButtonWithIcon(
            title = "Sleep Mode",
            icon = Icons.Default.Nightlight,
            color = Color(0xFF6B73FF),
            modifier = Modifier.fillMaxWidth(),
            onClick = onSleepMode
        )

        ActionButtonWithIcon(
            title = "Logout",
            icon = Icons.Default.ExitToApp,
            color = Color.Red.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth(),
            onClick = onLogout
        )
    }
}

@Composable
fun ActionButtonWithIcon(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun InteractiveDailyTipCard(dailyTip: String, onNextTip: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.yoga))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PaleLavender),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = dailyTip,
                    fontSize = 16.sp,
                    color = TwilightLavender,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onNextTip,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Next Tip",
                    color = MediumPurple,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MediumPurple,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FloatingActionButtons(
    onQuickMeditation: () -> Unit,
    onSleepSession: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sleep session FAB
            FloatingActionButton(
                onClick = onSleepSession,
                containerColor = Color(0xFF6B73FF),
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Text(text = "😴", fontSize = 24.sp)
            }

            // Meditation FAB
            FloatingActionButton(
                onClick = onQuickMeditation,
                containerColor = LightMagenta,
                contentColor = Color.White,
                modifier = Modifier.size(64.dp)
            ) {
                Text(text = "🧘‍♂️", fontSize = 28.sp)
            }
        }
    }
}

@Composable
fun MoodPickerDialog(
    currentMood: String,
    onMoodSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val moods = listOf("😊", "😌", "😴", "🧘‍♂️", "😇", "😐", "😔", "😫", "😴")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?") },
        text = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moods) { mood ->
                    Text(
                        text = mood,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .clickable { onMoodSelected(mood) }
                            .padding(8.dp)
                            .background(
                                if (mood == currentMood) LightLavender else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StatsDetailDialog(
    meditationStreak: Int,
    totalHours: Int,
    badgesEarned: Int,
    sleepGoal: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Progress", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                StatDetailRow("Current Streak", "$meditationStreak days in a row 🔥")
                StatDetailRow("Total Meditation", "$totalHours hours of peace ⏰")
                StatDetailRow("Achievements", "$badgesEarned badges earned 🏅")
                StatDetailRow("Sleep Goal", "$sleepGoal hours per night 😴")
                StatDetailRow("Level", "Level ${meditationStreak / 7 + 1} Meditator ⭐")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun StatDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, color = MediumPurple)
    }
}

data class Badge(
    val icon: String,
    val name: String,
    val isEarned: Boolean
)