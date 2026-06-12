package com.aerolite.lgplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_progress WHERE mediaUri = :uri")
    suspend fun getProgress(uri: String): PlaybackProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgress)

    @Query("SELECT * FROM playback_progress ORDER BY lastUpdated DESC")
    suspend fun getAllHistory(): List<PlaybackProgress>

    @Query("DELETE FROM playback_progress WHERE mediaUri = :uri")
    suspend fun deleteProgress(uri: String)
}
