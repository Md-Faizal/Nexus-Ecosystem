package com.nexus.portal.data.local.ridetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [RideSession::class, RideLocationPoint::class, VehicleEntity::class, PerformanceRunEntity::class], version = 18, exportSchema = false)
@TypeConverters(VehicleConverters::class)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun performanceRunDao(): PerformanceRunDao

    companion object {
        @Volatile
        private var INSTANCE: RideDatabase? = null

        fun getDatabase(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "ride_tracker_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

