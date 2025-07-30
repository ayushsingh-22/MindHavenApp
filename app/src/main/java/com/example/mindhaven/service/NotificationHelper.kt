package com.example.mindhaven.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.example.mindhaven.MainActivity
import com.example.mindhaven.R

@UnstableApi
class NotificationHelper(
    private val context: Context,
    private val player: ExoPlayer,
    private val mediaSession: MediaSession
) {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mindhaven_channel"
        private const val CHANNEL_NAME = "MindHaven Playback"
    }

    private var playerNotificationManager: PlayerNotificationManager? = null
    private var currentTitle: String = "MindHaven"
    private var currentText: String = "Meditation & Sleep Sound"

    init {
        createNotificationChannel()
    }

    fun updateMetadata(title: String, text: String) {
        currentTitle = title
        currentText = text
        playerNotificationManager?.invalidate()
    }

    fun showNotification() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player) = currentTitle

                override fun createCurrentContentIntent(player: Player): PendingIntent {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    return PendingIntent.getActivity(
                        context, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                override fun getCurrentContentText(player: Player) = currentText

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? = null
            })
            .setSmallIconResourceId(R.drawable.logo)
            .setChannelNameResourceId(R.string.app_name)
            .setChannelDescriptionResourceId(R.string.app_name)
            .build()

        playerNotificationManager?.setMediaSessionToken(mediaSession.platformToken)
        playerNotificationManager?.setPlayer(player)
    }

    fun hideNotification() {
        playerNotificationManager?.setPlayer(null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls for MindHaven"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}