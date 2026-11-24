package com.nexus.portal.companion

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class CompanionConnectionMode {
    OFFLINE,
    LOCAL_LAN,
    CENTRAL_CLOUD
}

data class CompanionEngineStatus(
    val connectionMode: CompanionConnectionMode,
    val isAuthenticated: Boolean,
    val activeLanPeerCount: Int,
    val primaryPeerHost: String?,
    val deviceId: String
)

/**
 * NexusCompanionEngine is the central coordinator for Nexus Fleet Mobile on Android.
 * Orchestrates unified authentication, local LAN zero-conf peer discovery (mDNS),
 * and automatic failover between local LAN and Central Cloud Hub.
 */
@Singleton
class NexusCompanionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val authBridge: NexusAuthBridge,
    val pairingManager: DevicePairingManager,
    val mdnsDiscovery: MdnsLocalDiscovery
) {
    private val tag = "NexusCompanionEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _engineStatus = MutableStateFlow(
        CompanionEngineStatus(
            connectionMode = CompanionConnectionMode.OFFLINE,
            isAuthenticated = authBridge.sessionState.value.isAuthenticated,
            activeLanPeerCount = 0,
            primaryPeerHost = null,
            deviceId = authBridge.getDeviceId()
        )
    )
    val engineStatus: StateFlow<CompanionEngineStatus> = _engineStatus.asStateFlow()

    init {
        // Observe auth state changes
        scope.launch {
            authBridge.sessionState.collect { session ->
                _engineStatus.value = _engineStatus.value.copy(
                    isAuthenticated = session.isAuthenticated,
                    deviceId = session.deviceId
                )
            }
        }

        // Observe discovered LAN mDNS peers
        scope.launch {
            mdnsDiscovery.discoveredPeers.collect { peers ->
                val count = peers.size
                val primary = peers.values.firstOrNull()?.host
                val mode = when {
                    count > 0 -> CompanionConnectionMode.LOCAL_LAN
                    authBridge.sessionState.value.isAuthenticated -> CompanionConnectionMode.CENTRAL_CLOUD
                    else -> CompanionConnectionMode.OFFLINE
                }

                _engineStatus.value = _engineStatus.value.copy(
                    activeLanPeerCount = count,
                    primaryPeerHost = primary,
                    connectionMode = mode
                )
                Log.d(tag, "Engine updated: mode=$mode, LAN peers=$count, primary=$primary")
            }
        }
    }

    /**
     * Start companion networking and zero-conf LAN discovery.
     */
    fun start() {
        Log.i(tag, "Starting Nexus Companion Engine (Device ID: ${authBridge.getDeviceId()})...")
        mdnsDiscovery.startDiscovery()
    }

    /**
     * Stop companion background networking.
     */
    fun stop() {
        Log.i(tag, "Stopping Nexus Companion Engine...")
        mdnsDiscovery.stopDiscovery()
        pairingManager.cancelPairing()
    }
}
