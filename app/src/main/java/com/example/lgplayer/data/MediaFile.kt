package com.example.lgplayer.data

import android.net.Uri

enum class MediaType {
    VIDEO, AUDIO
}

data class MediaFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long,
    val type: MediaType
)
