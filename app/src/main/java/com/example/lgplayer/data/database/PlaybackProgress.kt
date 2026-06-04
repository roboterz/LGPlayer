package com.example.lgplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val mediaUri: String,
    val position: Long,
    val duration: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
