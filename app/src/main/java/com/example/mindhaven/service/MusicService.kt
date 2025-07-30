package com.example.mindhaven.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.mindhaven.MainActivity
import androidx.media3.common.Player
import android.util.Log
@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
    private lateinit var notificationHelper: NotificationHelper
    private val binder = LocalBinder()

    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("MindHavenMediaSession")
            .setSessionActivity(pendingIntent)
            .build()

        notificationHelper = NotificationHelper(applicationContext, player, mediaSession!!)
        notificationHelper.showNotification()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        player.release()
        notificationHelper.hideNotification()
        super.onDestroy()
    }

    override fun onUpdateNotification(session: MediaSession) {
        if (player.isPlaying) {
            startForeground(NOTIFICATION_ID, null)
        } else {
            stopForeground(false)
        }
    }

fun playSound(resourceId: Int, title: String, isLooping: Boolean) {
    try {
        val mediaItem = MediaItem.fromUri("android.resource://$packageName/$resourceId")
        player.apply {
            setMediaItem(mediaItem)
            // Ensure the repeat mode is properly set based on isLooping
            repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
            play()
        }
        notificationHelper.updateMetadata(title, "Playing")
    } catch (e: Exception) {
        Log.e("MusicService", "Error playing sound", e)
    }
}

    fun pauseSound() = player.pause()
    fun stopSound() = player.stop()
}
