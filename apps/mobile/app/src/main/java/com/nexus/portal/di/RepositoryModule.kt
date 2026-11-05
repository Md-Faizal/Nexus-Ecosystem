package com.nexus.portal.di

import com.nexus.portal.managers.BluetoothConnectionManager
import com.nexus.portal.managers.DefaultBluetoothConnectionManager
import com.nexus.portal.data.repository.DefaultLocationRepository
import com.nexus.portal.data.repository.DefaultSensorRepository
import com.nexus.portal.data.repository.LocationRepository
import com.nexus.portal.data.repository.SensorRepository
import com.nexus.portal.ui.screens.hub.apps.ridetracker.data.DefaultRideRepository
import com.nexus.portal.ui.screens.hub.apps.ridetracker.data.RideRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        defaultLocationRepository: DefaultLocationRepository
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindSensorRepository(
        defaultSensorRepository: DefaultSensorRepository
    ): SensorRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothConnectionManager(
        defaultBluetoothConnectionManager: DefaultBluetoothConnectionManager
    ): BluetoothConnectionManager

    @Binds
    @Singleton
    abstract fun bindRideRepository(
        defaultRideRepository: DefaultRideRepository
    ): RideRepository

    @Binds
    @Singleton
    abstract fun bindTimelineRepository(
        defaultTimelineRepository: com.nexus.portal.data.repository.DefaultTimelineRepository
    ): com.nexus.portal.data.repository.TimelineRepository
}
