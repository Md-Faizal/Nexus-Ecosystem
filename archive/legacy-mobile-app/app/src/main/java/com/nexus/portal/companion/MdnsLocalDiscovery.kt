package com.nexus.portal.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredPeer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Android NSD (Network Service Discovery) manager for discovering local Nexus Fleet
 * nodes advertising the `_nexus._tcp` mDNS service on the local Wi-Fi / LAN.
 */
@Singleton
class MdnsLocalDiscovery @Inject constructor(
    private val context: Context
) {
    private val tag = "NexusMdnsDiscovery"
    private val serviceType = "_nexus._tcp."

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    fun startDiscovery() {
        if (discoveryListener != null) {
            Log.d(tag, "mDNS discovery already active.")
            return
        }

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            Log.e(tag, "NSD Service is unavailable on this device.")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(tag, "Local mDNS Service discovery started for: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Service discovery hit: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")
                if (serviceInfo.serviceType.contains("_nexus._tcp")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.w(tag, "Local mDNS Service lost: ${serviceInfo.serviceName}")
                _discoveredPeers.value = _discoveredPeers.value - serviceInfo.serviceName
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(tag, "mDNS Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Failed to start discovery: error $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Failed to stop discovery: error $errorCode")
                nsdManager = null
                discoveryListener = null
            }
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception starting NSD discovery: ${e.message}", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolverListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "Resolve failed for ${serviceInfo.serviceName}: error $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host: InetAddress? = resolvedInfo.host
                val port = resolvedInfo.port
                val hostAddress = host?.hostAddress ?: return

                Log.i(tag, "Resolved Nexus Peer: ${resolvedInfo.serviceName} at $hostAddress:$port")
                val peer = DiscoveredPeer(
                    serviceName = resolvedInfo.serviceName,
                    host = hostAddress,
                    port = port
                )
                _discoveredPeers.value = _discoveredPeers.value + (resolvedInfo.serviceName to peer)
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolverListener)
        } catch (e: Exception) {
            Log.w(tag, "Failed to initiate resolveService: ${e.message}")
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(tag, "Error stopping service discovery: ${e.message}")
            }
        }
        discoveryListener = null
        nsdManager = null
    }
}
