package com.nexus.portal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import javax.inject.Inject
import com.nexus.portal.ui.screens.hub.apps.ridetracker.RideTrackerAppObserver

@HiltAndroidApp
class NexusApplication : Application() {
    
    @Inject lateinit var rideTrackerAppObserver: RideTrackerAppObserver
    
    override fun onCreate() {
        super.onCreate()
        // Initialize global components here if needed
        rideTrackerAppObserver.startObserving()
    }
}
