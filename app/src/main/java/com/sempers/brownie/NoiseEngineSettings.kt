package com.sempers.brownie

import java.io.Serializable

data class NoiseEngineSettings (
    var dispersion: Double = 0.3,
    var smoothness: Double = 0.7,
    var stereoWidth: Double = 0.9,
    var lpfCoefficient: Double = 0.0,
    var volume: Double = 1.0,
    var isTwoChannels: Boolean = true,
    var autoNormalize: Boolean = false,
    var isAmplitudeModulation: Boolean = false,
    var isStereoDrift: Boolean = false
): Serializable