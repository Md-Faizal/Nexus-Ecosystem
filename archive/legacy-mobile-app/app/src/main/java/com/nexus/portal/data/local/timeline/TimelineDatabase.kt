package com.nexus.portal.data.local.timeline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TimelineLocationPoint::class],
    version = 2,
    exportSchema = false
)
abstract class TimelineDatabase : RoomDatabase() {

    abstract fun timelineDao(): TimelineDao

    companion object {
        @Volatile
        private var INSTANCE: TimelineDatabase? = null

        fun getDatabase(context: Context): TimelineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TimelineDatabase::class.java,
                    "timeline_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
