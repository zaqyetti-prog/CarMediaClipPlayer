package com.carmedia.clipplayer.model

import android.net.Uri

data class VideoItem(
    val id: String,
    val title: String,
    val path: String,
    val uri: Uri,
    val durationMs: Long,
    val folderPath: String,
    val folderName: String
)

data class FolderItem(
    val name: String,
    val path: String,
    val videos: MutableList<VideoItem> = mutableListOf()
) {
    val videoCount: Int
        get() = videos.size
}

data class EqBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    var gainMb: Short,
    val minGainMb: Short,
    val maxGainMb: Short
) {
    fun getFormattedFreq(): String {
        return if (centerFreqHz >= 1000) {
            val khz = centerFreqHz / 1000.0
            if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format("%.1f kHz", khz)
        } else {
            "$centerFreqHz Hz"
        }
    }

    fun getFormattedGain(): String {
        val db = gainMb / 100
        return if (db > 0) "+$db dB" else "$db dB"
    }
}
