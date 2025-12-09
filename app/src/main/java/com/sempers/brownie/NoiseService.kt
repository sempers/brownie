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

class NoiseService : Service() {
    private val engine = NoiseEngine()
    private var isPlaying = false

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
    }

    override fun onDestroy() {

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")

        when (action) {
            "start" -> { }
            "play" -> playNoise()
            "pause" -> pauseNoise()
            "stop" -> stopService()
            "update" -> {
                val newSettings = intent?.getSerializableExtra("settings") as? NoiseEngineSettings
                if (newSettings != null) {
                    engine.updateSettings(newSettings);
                }
            }
        }

        if (action == "start") {
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

        return START_STICKY
    }

    private fun playNoise() {
        engine.start()
        isPlaying = true
    }

    private fun pauseNoise() {
        engine.stop()
        isPlaying = false
    }

    private fun stopService() {
        engine.stop()
        isPlaying = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = "noise"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Noise", NotificationManager.IMPORTANCE_LOW)
            )
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Brownie")
            .setContentText("Playing Brown noise")
            .setSmallIcon(R.drawable.icon)
            .build()
    }
}