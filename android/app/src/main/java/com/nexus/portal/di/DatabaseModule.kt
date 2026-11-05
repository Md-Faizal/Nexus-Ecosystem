package com.nexus.portal.di

import android.content.Context
import com.nexus.portal.data.local.ridetracker.RideDao
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleDao
import com.nexus.portal.data.local.ridetracker.PerformanceRunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRideDatabase(@ApplicationContext context: Context): RideDatabase {
        return RideDatabase.getDatabase(context)
    }

    @Provides
    fun provideRideDao(database: RideDatabase): RideDao {
        return database.rideDao()
    }

    @Provides
    fun provideVehicleDao(database: RideDatabase): VehicleDao {
        return database.vehicleDao()
    }
    
    @Provides
    fun providePerformanceRunDao(database: RideDatabase): PerformanceRunDao {
        return database.performanceRunDao()
    }

    @Provides
    @Singleton
    fun provideTimelineDatabase(@ApplicationContext context: Context): com.nexus.portal.data.local.timeline.TimelineDatabase {
        return com.nexus.portal.data.local.timeline.TimelineDatabase.getDatabase(context)
    }

    @Provides
    fun provideTimelineDao(database: com.nexus.portal.data.local.timeline.TimelineDatabase): com.nexus.portal.data.local.timeline.TimelineDao {
        return database.timelineDao()
    }
}
