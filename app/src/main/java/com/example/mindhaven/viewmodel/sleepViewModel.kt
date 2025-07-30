package com.example.mindhaven.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mindhaven.R
import com.example.mindhaven.model.SoundItem
import com.example.mindhaven.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private var mediaServiceConnection: ServiceConnection? = null
    private var mediaService: MusicService? = null
    private var activeTimerJob: Job? = null

    var selectedTimer by mutableLongStateOf(15L * 60 * 1000) // Default to 15 minutes

    var isPlaying by mutableStateOf(false)
        private set

    var selectedSound by mutableStateOf<SoundItem?>(null)
        private set

    var showLottieAnimation by mutableStateOf(false)
        private set

    val sleepSounds = listOf(
        SoundItem(
            id = 1,
            title = "Calming Rain",
            thumbnailResId = R.drawable.rain,
            soundResId = R.raw.calming_rain,
        ),
        SoundItem(
            id = 2,
            title = "Bird Chripping",
            thumbnailResId = R.drawable.forest_birds,
            soundResId = R.raw.bird_chrip,
        ),
        SoundItem(
            id = 3,
            title = "Meditation Bell",
            thumbnailResId = R.drawable.meditation_bell,
            soundResId = R.raw.meditation_bell,
        ),
        SoundItem(
            id = 4,
            title = "Tibet Bowl",
            thumbnailResId = R.drawable.tibet_bowl,
            soundResId = R.raw.tibet_bowl,
        ),
        SoundItem(
            id = 5,
            title = "River Flow",
            thumbnailResId = R.drawable.river_flow,
            soundResId = R.raw.river_flow,
        ),
        SoundItem(
            id = 6,
            title = "Wind",
            thumbnailResId = R.drawable.wind,
            soundResId = R.raw.wind_tree,
        )
    )

    init {
        connectToMediaService()
    }

    private fun connectToMediaService() {
        val serviceIntent = Intent(getApplication(), MusicService::class.java)
        getApplication<Application>().startService(serviceIntent)

        mediaServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as MusicService.LocalBinder
                mediaService = binder.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mediaService = null
            }
        }

        getApplication<Application>().bindService(
            serviceIntent,
            mediaServiceConnection!!,
            Context.BIND_AUTO_CREATE
        )
    }

    fun selectSound(sound: SoundItem) {
        if (selectedSound?.id != sound.id) {
            stop()
            selectedSound = sound
            showLottieAnimation = false
        }
    }

    fun play(timerDuration: Long = selectedTimer) {
        stop() // Stop any previous playback and timer

        selectedSound?.let { sound ->
            // Always start with looping enabled for consistent experience
            val isLooping = true

            mediaService?.playSound(sound.soundResId, sound.title, isLooping)
            isPlaying = true
            showLottieAnimation = true

            // Set timer only if not infinite (not 0)
            if (timerDuration > 0) {
                startTimer(timerDuration)
            }
        }
    }

    fun pause() {
        mediaService?.pauseSound()
        isPlaying = false
        // Don't cancel the timer when pausing - just pause the sound
    }

    fun stop() {
        mediaService?.stopSound()
        isPlaying = false
        showLottieAnimation = false
        cancelTimer()
    }

    private fun startTimer(duration: Long) {
        // Cancel any existing timer
        cancelTimer()

        // Use coroutine for timer instead of handler
        activeTimerJob = viewModelScope.launch {
            delay(duration)
            // Check if we're still playing the same sound
            stop()
        }
    }

    private fun cancelTimer() {
        activeTimerJob?.cancel()
        activeTimerJob = null
    }

    fun clearSelection() {
        stop()
        selectedSound = null
        showLottieAnimation = false
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        cancelTimer()

        // Unbind from service when ViewModel is cleared
        mediaServiceConnection?.let {
            getApplication<Application>().unbindService(it)
        }
    }
}