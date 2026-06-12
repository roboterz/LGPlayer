package com.aerolite.lgplayer.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY addedAt DESC")
    fun getAllItems(): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItem)

    @Delete
    suspend fun removeItem(item: PlaylistItem)

    @Query("DELETE FROM playlist WHERE mediaUri = :uri")
    suspend fun removeItemByUri(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist WHERE mediaUri = :uri)")
    suspend fun isItemInPlaylist(uri: String): Boolean
}
