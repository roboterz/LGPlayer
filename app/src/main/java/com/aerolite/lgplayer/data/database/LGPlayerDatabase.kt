package com.aerolite.lgplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PlaybackProgress::class, PlaylistItem::class], version = 2, exportSchema = false)
@TypeConverters(MediaTypeConverter::class)
abstract class LGPlayerDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: LGPlayerDatabase? = null

        fun getDatabase(context: Context): LGPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LGPlayerDatabase::class.java,
                    "lgplayer_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
