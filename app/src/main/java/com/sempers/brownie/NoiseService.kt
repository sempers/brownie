package com.sempers.brownie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import android.app.PendingIntent

class NoiseService : Service() {
    private val engine = NoiseEngine()

    private val channelId = "Brownie"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        engine.onSampleUpdate = { linearValue, rmsValue, dbValue, autoGain ->
            val intent = Intent("com.sempers.brownie.SAMPLE_UPDATE")
            intent.`package` = applicationContext.packageName
            intent.putExtra("linearValue", linearValue)
            intent.putExtra("rmsValue", rmsValue)
            intent.putExtra("dbValue", dbValue)
            intent.putExtra("autoGain", autoGain)
            sendBroadcast(intent)
        }


        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Brown Noise Playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")

        when (action) {
            "start" -> {
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(1, notification)
                }
            }
            "play" -> { playNoise(); updateNotification() }
            "pause" -> { pauseNoise(); updateNotification() }
            "stop" -> stopService()
            "update" -> {
                val newSettings = intent?.getSerializableExtra("settings") as? NoiseEngineSettings
                if (newSettings != null) {
                    engine.updateSettings(newSettings);
                }
            }
        }

        return START_STICKY
    }

    fun toggleUpdate(success: Boolean, state: Boolean) {
        val intent = Intent("com.sempers.brownie.TOGGLE_UPDATE")
        intent.`package` = applicationContext.packageName
        intent.putExtra("success", success)
        intent.putExtra("state", state)
        sendBroadcast(intent)
    }

    private fun playNoise() {
        if (!engine.isPlaying) {
            engine.start()
            toggleUpdate(true, engine.isPlaying)
        } else {
            toggleUpdate(false, engine.isPlaying)
        }

    }

    private fun pauseNoise() {
        if (engine.isPlaying) {
            engine.stop()
            toggleUpdate(true, engine.isPlaying)
        } else {
            toggleUpdate(false, engine.isPlaying)
        }
    }

    private fun stopService() {
        engine.stop()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val actionIntent = Intent(this, NoiseService::class.java).apply {
            putExtra("action", if (engine.isPlaying) "pause" else "play")
        }

        val pendingAction = PendingIntent.getService(
            this,
            0,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIcon = if (engine.isPlaying) R.drawable.pause_circle_small else R.drawable.play_circle_small

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Brownie")
            .setContentText(if (engine.isPlaying) "Playing now" else "Paused now")
            .addAction(actionIcon, if (engine.isPlaying) "Pause" else "Play", pendingAction)
            .setSmallIcon(R.drawable.icon)
            .setOngoing(engine.isPlaying)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = createNotification()
        manager.notify(1, notification)
    }
}