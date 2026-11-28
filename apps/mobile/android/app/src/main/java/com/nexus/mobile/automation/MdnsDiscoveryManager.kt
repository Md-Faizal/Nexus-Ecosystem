package com.nexus.mobile.automation

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredMdnsPeer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val nodeId: String,
    val role: String,
    val version: String,
    val discoveredAtMs: Long = System.currentTimeMillis(),
)

/**
 * Native Android NsdManager wrapper browsing `_nexus._tcp.` on the local Wi-Fi LAN,
 * matching `NEXUS_MDNS_SERVICE_TYPE` in `apps/desktop/src/modules/mdns_discovery.rs`.
 */
@Singleton
class MdnsDiscoveryManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val serviceType = "_nexus._tcp."
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _peers = MutableStateFlow<List<DiscoveredMdnsPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredMdnsPeer>> = _peers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var activeListener: NsdManager.DiscoveryListener? = null
    private var onPeerChangedCallback: ((List<DiscoveredMdnsPeer>, DiscoveredMdnsPeer?) -> Unit)? = null

    fun setOnPeersChanged(listener: (List<DiscoveredMdnsPeer>, DiscoveredMdnsPeer?) -> Unit) {
        onPeerChangedCallback = listener
    }

    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (activeListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isScanning.value = false
                activeListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isScanning.value = false
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                _isScanning.value = true
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                _isScanning.value = false
                activeListener = null
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _peers.update { list ->
                    list.filterNot { it.serviceName == serviceInfo.serviceName }
                }
                onPeerChangedCallback?.invoke(_peers.value, null)
            }
        }
        activeListener = listener
        runCatching {
            val lock = wifiManager?.createMulticastLock("nexus_mdns_lock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            runCatching { multicastLock?.release() }
            multicastLock = null
            activeListener = null
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        val listener = activeListener ?: return
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
        runCatching { multicastLock?.release() }
        multicastLock = null
        activeListener = null
        _isScanning.value = false
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    resolved.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    resolved.host?.hostAddress
                } ?: return

                val attrs = resolved.attributes
                val nodeId = attrs["node_id"]?.decodeToString() ?: resolved.serviceName
                val role = attrs["role"]?.decodeToString() ?: "node"
                val version = attrs["version"]?.decodeToString() ?: ""
                val peer = DiscoveredMdnsPeer(
                    serviceName = resolved.serviceName,
                    host = hostAddress,
                    port = resolved.port,
                    nodeId = nodeId,
                    role = role,
                    version = version,
                )
                _peers.update { current ->
                    (current.filterNot { it.serviceName == peer.serviceName } + peer)
                        .sortedBy { it.serviceName }
                }
                onPeerChangedCallback?.invoke(_peers.value, peer)
            }
        }
        runCatching { manager.resolveService(serviceInfo, resolveListener) }
    }
}
