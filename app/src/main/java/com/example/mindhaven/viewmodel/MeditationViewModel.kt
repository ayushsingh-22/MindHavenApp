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
import com.example.mindhaven.model.SoundItem
import com.example.mindhaven.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class MeditationViewModel(application: Application) : AndroidViewModel(application) {

    private var mediaServiceConnection: ServiceConnection? = null
    private var mediaService: MusicService? = null
    private var activeTimerJob: Job? = null

    var selectedTimer by mutableLongStateOf(15L * 60 * 1000)

    var isPlaying by mutableStateOf(false)
        private set

    var selectedSound by mutableStateOf<SoundItem?>(null)
        private set

    var showLottieAnimation by mutableStateOf(false)
        private set

     val meditationSounds = listOf(
           SoundItem(
               id = 1,
               title = "Bird Chirping",
               imageUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f6004003d86aea9d1/view?project=688e73ad002a00fce059&mode=admin",
               audioUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5d0700007afbdece/view?project=688e73ad002a00fce059&mode=admin"
           ),
           SoundItem(
               id = 2,
               title = "River Sounds",
               imageUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f603c0023b548e5f2/view?project=688e73ad002a00fce059&mode=admin",
               audioUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5ddd0004ba9ad2dc/view?project=688e73ad002a00fce059&mode=admin"
           ),
           SoundItem(
               id = 3,
               title = "Meditation Bell",
               imageUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688fd283002713e0a8dc/view?project=688e73ad002a00fce059&mode=admin",
               audioUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5d1d000bb4901c31/view?project=688e73ad002a00fce059&mode=adminn"
           ),
         SoundItem(
             id = 4,
             title = "Tibet Bowl",
             imageUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f601200079a609a32/view?project=688e73ad002a00fce059&mode=admi",
             audioUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5dfe0019ed01cef0/view?project=688e73ad002a00fce059&mode=admin"
         ),
         SoundItem(
             id = 5,
             title = "Wind Ambience",
             imageUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5fe700073bccc8a0/view?project=688e73ad002a00fce059&mode=admin",
             audioUrl = "https://syd.cloud.appwrite.io/v1/storage/buckets/688e78db003b3cca2de2/files/688f5dc200145854e754/view?project=688e73ad002a00fce059&mode=admin"
         ),


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
        stop()

        selectedSound?.let { sound ->
            val isLooping = true

            mediaService?.playSound(sound, isLooping)
            isPlaying = true
            showLottieAnimation = true

            if (timerDuration > 0) {
                startTimer(timerDuration)
            }
        }
    }

    fun pause() {
        mediaService?.pauseSound()
        isPlaying = false
    }

    fun stop() {
        mediaService?.stopSound()
        isPlaying = false
        showLottieAnimation = false
        cancelTimer()
    }

    private fun startTimer(duration: Long) {
        cancelTimer()

        activeTimerJob = viewModelScope.launch {
            delay(duration)
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

        mediaServiceConnection?.let {
            getApplication<Application>().unbindService(it)
        }
    }
}