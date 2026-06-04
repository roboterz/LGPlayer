package com.example.lgplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlaybackProgress::class], version = 1, exportSchema = false)
abstract class LGPlayerDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile
        private var INSTANCE: LGPlayerDatabase? = null

        fun getDatabase(context: Context): LGPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LGPlayerDatabase::class.java,
                    "lgplayer_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
