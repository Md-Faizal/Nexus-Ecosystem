package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nexus.portal.core.AppEvent
import com.nexus.portal.core.AppEventBus
import com.nexus.portal.services.RideTrackerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideTrackerAppObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appEventBus: AppEventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun startObserving() {
        scope.launch {
            appEventBus.events.collect { event ->
                when (event) {
                    is AppEvent.GarageDeviceConnected -> {
                        RideTrackerService.addActiveMac(context, event.macAddress)
                        val serviceIntent = Intent(context, RideTrackerService::class.java).apply {
                            this.action = RideTrackerService.ACTION_DEVICE_CONNECTED
                            putExtra(RideTrackerService.EXTRA_MAC_ADDRESS, event.macAddress)
                            putExtra(RideTrackerService.EXTRA_DEVICE_NAME, event.deviceName)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                    is AppEvent.GarageDeviceDisconnected -> {
                        RideTrackerService.removeActiveMac(context, event.macAddress)
                        val serviceIntent = Intent(context, RideTrackerService::class.java).apply {
                            this.action = RideTrackerService.ACTION_DEVICE_DISCONNECTED
                            putExtra(RideTrackerService.EXTRA_MAC_ADDRESS, event.macAddress)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            }
        }
    }
}
