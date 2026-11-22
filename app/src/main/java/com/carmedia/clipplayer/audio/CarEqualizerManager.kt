package com.carmedia.clipplayer.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.carmedia.clipplayer.model.EqBand

class CarEqualizerManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("car_equalizer_settings", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var currentAudioSessionId: Int = 0

    var isEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", true)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
            equalizer?.enabled = value
            bassBoost?.enabled = value
            loudnessEnhancer?.enabled = value
        }

    var isLowCutEnabled: Boolean
        get() = prefs.getBoolean("eq_low_cut", false)
        set(value) {
            prefs.edit().putBoolean("eq_low_cut", value).apply()
            applyFrequencyCuts()
        }

    var isHighCutEnabled: Boolean
        get() = prefs.getBoolean("eq_high_cut", false)
        set(value) {
            prefs.edit().putBoolean("eq_high_cut", value).apply()
            applyFrequencyCuts()
        }

    var bassBoostStrength: Short
        get() = prefs.getInt("bass_boost_strength", 300).toShort()
        set(value) {
            prefs.edit().putInt("bass_boost_strength", value.toInt()).apply()
            try {
                if (bassBoost?.strengthSupported == true) {
                    bassBoost?.setStrength(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    var loudnessGainMb: Int
        get() = prefs.getInt("loudness_gain_mb", 300)
        set(value) {
            prefs.edit().putInt("loudness_gain_mb", value).apply()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    loudnessEnhancer?.setTargetGain(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
        release()
        currentAudioSessionId = audioSessionId

        try {
            // Equalizer (Graphic Multi-band)
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }

            // Bass Boost (Low-end punch)
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength(bassBoostStrength)
                }
            }

            // Loudness Enhancer for Car Audio (Dynamic Range / Overall Gain)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = isEnabled
                    setTargetGain(loudnessGainMb)
                }
            }

            restoreSavedBands()
            applyFrequencyCuts()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBands(): List<EqBand> {
        val eq = equalizer ?: return emptyList()
        val numBands = eq.numberOfBands
        val range = eq.bandLevelRange
        val minGain = range[0]
        val maxGain = range[1]

        val list = mutableListOf<EqBand>()
        for (i in 0 until numBands) {
            val bandIndex = i.toShort()
            val centerFreq = eq.getCenterFreq(bandIndex) / 1000 // Convert mHz to Hz
            val savedGain = prefs.getInt("band_$bandIndex", eq.getBandLevel(bandIndex).toInt()).toShort()
            list.add(EqBand(bandIndex, centerFreq, savedGain, minGain, maxGain))
        }
        return list
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, gainMb)
            prefs.edit().putInt("band_$bandIndex", gainMb.toInt()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreSavedBands() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands
        for (i in 0 until numBands) {
            val band = i.toShort()
            val savedGain = prefs.getInt("band_$band", 0).toShort()
            try {
                eq.setBandLevel(band, savedGain)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyFrequencyCuts() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        if (numBands == 0) return

        val range = eq.bandLevelRange
        val minGain = range[0] // e.g. -1500 mB (-15 dB)

        // Low Cut (HPF): Attenuate lowest band (usually ~60Hz) to prevent door speaker distortion
        if (isLowCutEnabled) {
            eq.setBandLevel(0.toShort(), (minGain * 0.8f).toInt().toShort())
        } else {
            val saved = prefs.getInt("band_0", 0).toShort()
            eq.setBandLevel(0.toShort(), saved)
        }

        // High Cut (LPF): Attenuate highest band (usually ~14kHz) to prevent harsh tweeter distortion
        val highestBand = (numBands - 1).toShort()
        if (isHighCutEnabled) {
            eq.setBandLevel(highestBand, (minGain * 0.8f).toInt().toShort())
        } else {
            val saved = prefs.getInt("band_$highestBand", 0).toShort()
            eq.setBandLevel(highestBand, saved)
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val maxGain = range[1]

        when (presetName) {
            "FLAT" -> {
                for (i in 0 until numBands) {
                    setBandGain(i.toShort(), 0)
                }
                bassBoostStrength = 0
            }
            "CAR_BASS" -> {
                for (i in 0 until numBands) {
                    val gain = when (i) {
                        0 -> (maxGain * 0.7f).toInt().toShort() // Heavy sub-bass
                        1 -> (maxGain * 0.4f).toInt().toShort() // Mid-bass punch
                        numBands - 1 -> (maxGain * 0.3f).toInt().toShort() // Crisp highs
                        else -> 0.toShort()
                    }
                    setBandGain(i.toShort(), gain)
                }
                bassBoostStrength = 650
            }
            "VOCAL" -> {
                for (i in 0 until numBands) {
                    val gain = when {
                        i == numBands / 2 -> (maxGain * 0.5f).toInt().toShort()
                        i == (numBands / 2) + 1 -> (maxGain * 0.4f).toInt().toShort()
                        else -> 0.toShort()
                    }
                    setBandGain(i.toShort(), gain)
                }
                bassBoostStrength = 150
            }
            "ROCK" -> {
                for (i in 0 until numBands) {
                    val gain = when (i) {
                        0 -> (maxGain * 0.5f).toInt().toShort()
                        1 -> (maxGain * 0.2f).toInt().toShort()
                        numBands / 2 -> (-maxGain * 0.2f).toInt().toShort() // V-Shape dip
                        numBands - 1 -> (maxGain * 0.5f).toInt().toShort()
                        else -> 0.toShort()
                    }
                    setBandGain(i.toShort(), gain)
                }
                bassBoostStrength = 400
            }
        }
        applyFrequencyCuts()
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            equalizer = null
            bassBoost = null
            loudnessEnhancer = null
            currentAudioSessionId = 0
        }
    }
}
