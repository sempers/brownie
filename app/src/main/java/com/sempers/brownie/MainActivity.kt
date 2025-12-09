package com.sempers.brownie

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var isPlaying = false
    private lateinit var bgImage: ImageView
    private lateinit var overlayImage: ImageView
    private lateinit var textDbValue: TextView
    private lateinit var btnPlayPause: ImageView
    private var settings = NoiseEngineSettings()

    // Background change
    private val bgImages = arrayOf(
        R.drawable.background0,
        R.drawable.background1,
        R.drawable.background2,
        R.drawable.background3,
        R.drawable.background4,
        R.drawable.background5,
        R.drawable.background6,
        R.drawable.background7,
        R.drawable.background8,
        R.drawable.background9,
        R.drawable.background10,
        R.drawable.background11,
        R.drawable.background12,
        R.drawable.background13,
        R.drawable.background14,
        R.drawable.background15,
        R.drawable.background16,
        R.drawable.background17,
        R.drawable.background18,
        R.drawable.background19,
        R.drawable.background20
    )
    private var currentBgIndex = 0
    private val bgChangeInterval = 30_000L

    private fun crossfade(front: ImageView, back: ImageView, frontRes: Int, backRes: Int) {
        front.setImageResource(frontRes)
        back.setImageResource(backRes)
        back.alpha = 0f
        val fadeIn = ObjectAnimator.ofFloat(back, "alpha", 0f, 1f)
        val fadeOut = ObjectAnimator.ofFloat(front, "alpha", 1f, 0f)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, fadeOut)
        animatorSet.duration = 1000
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }

    private val handler = Handler(Looper.getMainLooper())

    private val bgRunnable = object : Runnable {
        override fun run() {
            val nextIndex = (currentBgIndex + 1) % bgImages.size
            crossfade(bgImage, overlayImage, bgImages[currentBgIndex], bgImages[nextIndex])
            currentBgIndex = nextIndex
            handler.postDelayed(this, bgChangeInterval)
        }
    }

    // Media Session
    private lateinit var mediaSession: MediaSession

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initMediaSession() {
        mediaSession = MediaSession(this, "NoiseSession")

        mediaSession.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = mediaButtonIntent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (event?.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            togglePlayPause()
                            return true
                        }
                    }
                }

                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })

        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE
            )
            .setState(PlaybackState.STATE_PAUSED, 0, 1f)
            .build()

        mediaSession.setPlaybackState(state)
        mediaSession.isActive = true
    }

    // Noise Service
    private fun startNoiseService() {
        val intent = Intent(this, NoiseService::class.java)
        intent.`package` = "com.brownie.sempers"
        intent.putExtra("action", "start")
        ContextCompat.startForegroundService(this, intent)
    }

    private fun playNoiseService() {
        val intent = Intent(this, NoiseService::class.java)
        intent.`package` = "com.brownie.sempers"
        intent.putExtra("action", "play")
        startService(intent)
    }

    private fun pauseNoiseService() {
        val intent = Intent(this, NoiseService::class.java)
        intent.`package` = "com.brownie.sempers"
        intent.putExtra("action", "pause")
        startService(intent)
    }

    private fun stopNoiseService() {
        val intent = Intent(this, NoiseService::class.java)
        intent.`package` = "com.brownie.sempers"
        intent.putExtra("action", "stop")
        startService(intent)
    }
    private fun updateNoiseService() {
        val intent = Intent(this, NoiseService::class.java)
        intent.`package` = "com.brownie.sempers"
        intent.putExtra("action", "update")
        intent.putExtra("settings", settings)
        startService(intent)
    }

    // Save settings
    private fun saveSettings() {
        val prefs = getSharedPreferences("BrowniePrefs", Context.MODE_PRIVATE)

        with(prefs.edit()) {
            putFloat("dispersion", settings.dispersion.toFloat())
            putFloat("smoothness", settings.smoothness.toFloat())
            putFloat("panorama", settings.stereoWidth.toFloat())
            putFloat("compression", settings.lpfCoefficient.toFloat())
            putFloat("volume", settings.volume.toFloat())
            putBoolean("2channels", settings.isTwoChannels)
            putBoolean("autonormalize", settings.autoNormalize)
            putBoolean("am", settings.isAmplitudeModulation)
            putBoolean("sd", settings.isStereoDrift)
            apply()
        }
    }

    // Receiving updates from NoiseEngine through NoiseService
    private var lastDbValue = 0.0

    private val sampleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isPlaying) {
                textDbValue.text = "Pause"
            }
            else {
                val linearValue = intent?.getDoubleExtra("linearValue", 0.0) ?: 0.0
                val rmsValue = intent?.getDoubleExtra("rmsValue", 0.0) ?: 0.0
                val dbValue = intent?.getDoubleExtra("dbValue", 0.0) ?: 0.0
                val autoGain = intent?.getDoubleExtra("autoGain", 1.0) ?: 1.0
                lastDbValue = dbValue
                textDbValue.text = String.format("%.1f dB", dbValue)
            }
        }
    }

    private val toggleUpdateReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success: Boolean? = intent?.getBooleanExtra("success", false)
            val state: Boolean? = intent?.getBooleanExtra("state", false)
            if (success == null || state == null || !success)
                return

            if (state) {
                textDbValue.text = String.format("%.1f dB", lastDbValue)
                btnPlayPause.setImageResource(R.drawable.pause_circle)
                isPlaying = true
            }
            else {
                textDbValue.text = "Pause"
                btnPlayPause.setImageResource(R.drawable.play_circle)
                isPlaying = false
            }
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            pauseNoiseService()
        } else {
            updateNoiseService()
            playNoiseService()
        }
    }

    // Notifications request
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean -> {}
            // nothing to do
        }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //notifications
        askNotificationPermission()

        initMediaSession()

        textDbValue = findViewById<TextView>(R.id.textDbValue)
        btnPlayPause = findViewById<ImageButton>(R.id.buttonPlayPause)


        // background changing
        bgImage = findViewById<ImageView>(R.id.imageBackground)
        overlayImage = findViewById<ImageView>(R.id.imageOverlay)
        bgImage.setImageResource(bgImages[currentBgIndex])
        handler.postDelayed(bgRunnable, bgChangeInterval)

        // StatusBar fix
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#1E130A")
        }

        // Pause initially
        btnPlayPause.setImageResource(R.drawable.play_circle)
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // drop down Advanced settings
        val header = findViewById<TextView>(R.id.advancedSettingsHeader)
        val container = findViewById<LinearLayout>(R.id.advancedSettingsContainer)
        container.visibility = View.GONE
        header.setOnClickListener {
            if (container.visibility == View.VISIBLE) {
                container.visibility = View.GONE
                header.text = "Advanced settings ▼"
            }
            else {
                container.visibility = View.VISIBLE
                header.text = "Advanced settings ▲"
            }
        }


        // Reading settings initially
        val prefs = getSharedPreferences("BrowniePrefs", MODE_PRIVATE)
        settings.dispersion = prefs.getFloat("dispersion", 0.3f).toDouble()
        settings.smoothness = prefs.getFloat("smoothness", 0.5f).toDouble()
        settings.stereoWidth = prefs.getFloat("panorama", 1.0f).toDouble()
        settings.lpfCoefficient = prefs.getFloat("compression", 0.0f).toDouble()
        settings.volume = prefs.getFloat("volume", 1.0f).toDouble()
        settings.isTwoChannels = prefs.getBoolean("2channels", true)
        settings.autoNormalize = prefs.getBoolean("autonormalize", false)
        settings.isAmplitudeModulation = prefs.getBoolean("am", false)
        settings.isStereoDrift = prefs.getBoolean("sd", false)

        // Initialization from NoiseEngine
        val seekDispersion = findViewById<SeekBar>(R.id.seekDispersion)
        val textDispersion = findViewById<TextView>(R.id.textDispersionValue)
        val seekSmoothness = findViewById<SeekBar>(R.id.seekSmoothness)
        val textSmoothness = findViewById<TextView>(R.id.textSmoothnessValue)
        val seekStereoWidth = findViewById<SeekBar>(R.id.seekStereoWidth)
        val textStereoWidth = findViewById<TextView>(R.id.textStereoWidthValue)
        val seekLPF = findViewById<SeekBar>(R.id.seekLPFCoef)
        val textLPF = findViewById<TextView>(R.id.textLPFCoefValue)
        val seekVolume = findViewById<SeekBar>(R.id.seekVolume)
        val textVolume = findViewById<TextView>(R.id.textVolumeValue)
        val checkTwoChannels = findViewById<CheckBox>(R.id.checkboxIsTwoChannels)
        val checkAutoNormalize = findViewById<CheckBox>(R.id.checkboxAutoNormalize)
        val checkAM = findViewById<CheckBox>(R.id.checkboxAM)
        val checkSD = findViewById<CheckBox>(R.id.checkboxStereoDrift)

        seekDispersion.progress = (settings.dispersion * 100.0).toInt()
        textDispersion.text = (settings.dispersion * 100.0).roundToInt().toString() + "%"
        seekSmoothness.progress = (settings.smoothness * 100.0).roundToInt()
        textSmoothness.text = (settings.smoothness * 100.0).roundToInt().toString() + "%"
        seekStereoWidth.progress = (settings.stereoWidth * 100.0).roundToInt()
        textStereoWidth.text = (settings.stereoWidth * 100.0).roundToInt().toString() + "%"
        seekLPF.progress = (settings.lpfCoefficient * 100.0).roundToInt()
        textLPF.text = (settings.lpfCoefficient * 100.0).roundToInt().toString() + "%"
        seekVolume.progress = (settings.volume * 100.0).roundToInt()
        textVolume.text = (settings.volume * 100.0).roundToInt().toString() + "%"
        checkTwoChannels.isChecked = settings.isTwoChannels
        checkAutoNormalize.isChecked = settings.autoNormalize
        checkAM.isChecked = settings.isAmplitudeModulation
        checkSD.isChecked = settings.isStereoDrift
        
        // Handlers
        seekDispersion.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 0.01
                textDispersion.text = "$progress%"
                settings.dispersion = value
                updateNoiseService()
                saveSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekSmoothness.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 0.01
                textSmoothness.text = "$progress%"
                settings.smoothness = value
                updateNoiseService()
                saveSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekStereoWidth.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 0.01
                textStereoWidth.text = "$progress%"
                settings.stereoWidth = value
                updateNoiseService()
                saveSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekLPF.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 0.01
                textLPF.text = "$progress%"
                settings.lpfCoefficient = value
                updateNoiseService()
                saveSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekVolume.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 0.01
                textVolume.text = "$progress%"
                settings.volume = value
                updateNoiseService()
                saveSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        checkTwoChannels.setOnCheckedChangeListener { _, isChecked ->
            settings.isTwoChannels = isChecked
            updateNoiseService()
            saveSettings()
        }

        checkAutoNormalize.setOnCheckedChangeListener { _, isChecked ->
            settings.autoNormalize = isChecked
            seekVolume.isEnabled = !isChecked
            updateNoiseService()
            saveSettings()
        }

        checkAM.setOnCheckedChangeListener { _, isChecked ->
            settings.isAmplitudeModulation = isChecked
            updateNoiseService()
            saveSettings()
        }

        checkSD.setOnCheckedChangeListener { _, isChecked ->
            settings.isStereoDrift = isChecked
            updateNoiseService()
            saveSettings()
        }
    }



    override fun onStart()
    {
        super.onStart()
        registerReceiver(sampleUpdateReceiver, IntentFilter("com.sempers.brownie.SAMPLE_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(toggleUpdateReceiver, IntentFilter("com.sempers.brownie.TOGGLE_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
        startNoiseService()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    override fun onStop() {
        super.onStop()
        saveSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
        stopNoiseService()
        unregisterReceiver(sampleUpdateReceiver)
        unregisterReceiver(toggleUpdateReceiver)
        handler.removeCallbacks(bgRunnable)
    }
}