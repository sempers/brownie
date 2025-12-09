package com.sempers.brownie

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

class NoiseEngine {
    var isPlaying = false
    // Settings for foreground service's noise engine
    private var settings = NoiseEngineSettings()

    // Constants
    private val MIN_AMP = 10.0.pow(-72 / 20.0)
    private val SAMPLE_RATE = 44100

    // Modulations
    private val AM_SPEED = 0.0333    // Hz
    private val AM_DEPTH = 0.1
    private val DRIFT_SPEED = 0.0333 // Hz
    private val DRIFT_DEPTH = 0.05

    // Filter coefficients
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    // Filter states
    private var lState = FilterState()
    private var rState = FilterState()
    private var lastResonance = -1.0

    // Handler of updated data from the service
    var onSampleUpdate: ((a: Double, b: Double, c: Double, d: Double) -> Unit)? = null

    // Audiotrack object
    private lateinit var audioTrack: AudioTrack

    // Brown Noise generation
    fun generate(inBrown: Double, dispersion: Double): Double {
        var brown = inBrown
        var white = Random.nextDouble(-dispersion, dispersion)
        if ((brown == 1.0 && white > 0) || (brown == -1.0 && white < 0)) {
            white = -white
        }
        brown += white
        brown = brown.coerceIn(-1.0, 1.0)
        return brown
    }

    private fun calculateLPFCoefficients(resonance: Double) {
        val omega = (2.0 * PI * settings.cutoffFrequency / SAMPLE_RATE)
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0f * resonance)

        // LPF coefficients
        val b0 = (1 - cosOmega) / 2
        val b1 = 1 - cosOmega
        val b2 = (1 - cosOmega) / 2
        val a0 = 1 + alpha
        val a1 = -2 * cosOmega
        val a2 = 1 - alpha

        // Normalization
        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = a1 / a0
        this.a2 = a2 / a0

        lastResonance = resonance
    }

    // Bi-quad Low Pass Filter
    private fun applyLPF(input: Double, resonance: Double, state: FilterState): Double {
        if (lastResonance == -1.0 || lastResonance != resonance) {
            calculateLPFCoefficients(resonance)
        }
        // Calculating output
        val output = b0 * input + b1 * state.x1 + b2 * state.x2 - a1 * state.y1 - a2 * state.y2

        // Updating filter's states
        state.x2 = state.x1
        state.x1 = input
        state.y2 = state.y1
        state.y1 = output

        return output
    }

    fun start() {
        if (isPlaying) return

        isPlaying = true

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 8

        val buffer = ShortArray(bufferSize)
        var brown = Random.nextDouble(-settings.dispersion, settings.dispersion)
        var brownL = Random.nextDouble(-settings.dispersion, settings.dispersion)
        var brownR = Random.nextDouble(-settings.dispersion, settings.dispersion)
        var prev = brown
        var prevL = brownL
        var prevR = brownR
        var phaseAM = 0.0
        var phaseDrift = 0.0
        var autoGain = -1.0
        var avgBufferLevel = settings.normLevel
        var avgBufferRmsLevel = settings.normLevel

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        thread(priority = Thread.MAX_PRIORITY) {
            while (isPlaying) {
                var count = 0
                var sum = 0.0
                var sum2 = 0.0
                val smooth = settings.smoothness.coerceIn(0.1, 0.99) * 100.0
                val panDelta = settings.stereoWidth * 0.2
                val dispersion = settings.dispersion.coerceIn(0.01, 0.99)
                val resonance = (1.0 - settings.lpfCoefficient).coerceIn(0.1, 1.0)
                if (settings.autoNormalize) {
                    autoGain =
                        if (autoGain == -1.0) 1.0 else autoGain * settings.normLevel / avgBufferLevel
                }

                for (i in buffer.indices step 2) {
                    // Generating mono
                    brown = this.generate(brown, dispersion);
                    val sample = (prev * smooth + brown) / (smooth + 1)
                    var left = sample
                    var right = sample

                    // Generating two independent channels
                    if (settings.isTwoChannels) {
                        brownL = this.generate(brownL, dispersion)
                        brownR = this.generate(brownR, dispersion)
                        left = (prevL * smooth + brownL) / (smooth + 1)
                        right = (prevR * smooth + brownR) / (smooth + 1)
                    }

                    // Saving previous values
                    prev = sample
                    prevL = left
                    prevR = right

                    // Panning
                    if (!settings.isTwoChannels) {
                        left += panDelta * settings.stereoWidth
                        right -= panDelta * settings.stereoWidth
                    }
                    else {
                        left = settings.stereoWidth * left + (1 - settings.stereoWidth) * (left + right) / 2
                        right = settings.stereoWidth * right + (1 - settings.stereoWidth) * (left + right) / 2
                    }

                    // Amplitude modulation
                    if (settings.isAmplitudeModulation) {
                        val am = 1.0 + AM_DEPTH * sin(phaseAM)
                        phaseAM += 2.0 * PI * AM_SPEED / SAMPLE_RATE
                        left *= am
                        right *= am
                    }

                    // Stereo drift
                    if (settings.isStereoDrift) {
                        val drift = DRIFT_DEPTH * sin(phaseDrift)
                        phaseDrift += 2.0 * PI * DRIFT_SPEED / SAMPLE_RATE
                        left *= (1 - DRIFT_DEPTH * drift)
                        right *= (1 + DRIFT_DEPTH * drift)
                    }

                    // LPF
                    if (settings.lpfCoefficient >= 0.01) {
                        left = applyLPF(left, resonance, lState)
                        right = applyLPF(right, resonance, rState)
                    }

                    // Volume and auto-normalization
                    if (settings.autoNormalize) {
                        left *= autoGain
                        right*= autoGain
                    } else {
                        left *= settings.volume
                        right *= settings.volume
                    }

                    // Limiter
                    left = left.coerceIn(-0.9999, 0.9999)
                    right = right.coerceIn(-0.9999, 0.9999)

                    // Average on Buffer counting after Compressor and Volume
                    count++
                    sum += (abs(left) + abs(right)) / 2.0
                    sum2 += left*left + right*right

                    // Buffering
                    buffer[i]     = (left * Short.MAX_VALUE).roundToInt().toShort()
                    buffer[i + 1] = (right * Short.MAX_VALUE).roundToInt().toShort()
                }

                avgBufferLevel =  sum / count
                avgBufferRmsLevel = sqrt(sum2 / (2 * count))
                val dbValue = 20.0 * log10(max(avgBufferRmsLevel, MIN_AMP))

                onSampleUpdate?.invoke(avgBufferLevel, avgBufferRmsLevel, dbValue, autoGain)

                audioTrack.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        if (!isPlaying) return

        isPlaying = false

        try {
            audioTrack.stop();
            audioTrack.release();
        }
        catch (e: Exception)
        {
            // for the rare case when audiotrack is not fully initialized, so fast clicking ends up in an exception
        }
    }

    fun updateSettings(newSettings: NoiseEngineSettings) {
        settings = newSettings
    }
}