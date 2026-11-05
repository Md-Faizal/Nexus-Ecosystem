package com.nexus.portal.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AppEvent {
    data class GarageDeviceConnected(val macAddress: String, val deviceName: String) : AppEvent()
    data class GarageDeviceDisconnected(val macAddress: String) : AppEvent()
    // Other system-wide events can be added here
}

/**
 * Industrial Standard Event Bus.
 * Allows decoupling of modules. Main app broadcasts events here,
 * and sub-apps (like Ride Tracker) observe them to start their services.
 */
@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun emitEvent(event: AppEvent) {
        _events.tryEmit(event)
    }
}
