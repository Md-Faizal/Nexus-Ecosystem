package com.nexus.mobile.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexus.mobile.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationRule(
    val id: String,
    val title: String,
    val triggerSummary: String,
    val actionSummary: String,
    val enabled: Boolean,
)

data class AutomationEvent(
    val id: Long,
    val title: String,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Singleton
class MdnsAutomationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mdnsManager: MdnsDiscoveryManager,
) {
    private val _rules = MutableStateFlow(
        listOf(
            AutomationRule(
                id = "lan_auto_switch",
                title = "Auto-Switch to Local LAN",
                triggerSummary = "When _nexus._tcp. peer is discovered on Wi-Fi",
                actionSummary = "Route fleet & AI calls over direct LAN",
                enabled = true,
            ),
            AutomationRule(
                id = "arrived_home_notify",
                title = "Home Network Notification",
                triggerSummary = "When home Nexus node appears via mDNS",
                actionSummary = "Post FZ notification & sync status",
                enabled = true,
            ),
            AutomationRule(
                id = "away_cloud_fallback",
                title = "Central Cloud Fallback",
                triggerSummary = "When all mDNS LAN peers disconnect",
                actionSummary = "Failover seamlessly to faizal.uk mesh",
                enabled = true,
            ),
            AutomationRule(
                id = "night_sync_guard",
                title = "Nightly Digest & Backup Guard",
                triggerSummary = "Daily 12:00 AM – 9:00 AM",
                actionSummary = "Summarize Night Log & check GitHub backup",
                enabled = false,
            ),
        )
    )
    val rules: StateFlow<List<AutomationRule>> = _rules.asStateFlow()

    private val _events = MutableStateFlow<List<AutomationEvent>>(emptyList())
    val events: StateFlow<List<AutomationEvent>> = _events.asStateFlow()

    private var lastNotifiedPeer: String? = null

    init {
        ensureNotificationChannel()
        mdnsManager.setOnPeersChanged { peers, newlyResolved ->
            if (newlyResolved != null) {
                recordEvent(
                    title = "mDNS Peer Discovered",
                    detail = "${newlyResolved.nodeId} (${newlyResolved.host}:${newlyResolved.port})",
                )
                val notifyRule = _rules.value.firstOrNull { it.id == "arrived_home_notify" }?.enabled == true
                if (notifyRule && lastNotifiedPeer != newlyResolved.nodeId) {
                    lastNotifiedPeer = newlyResolved.nodeId
                    postNotification(
                        title = "Nexus LAN Connected",
                        body = "Discovered ${newlyResolved.nodeId} at ${newlyResolved.host}",
                    )
                }
            } else if (peers.isEmpty() && lastNotifiedPeer != null) {
                lastNotifiedPeer = null
                recordEvent(
                    title = "Switched to Central Cloud",
                    detail = "No local _nexus._tcp. peers on Wi-Fi",
                )
            }
        }
    }

    fun toggleRule(ruleId: String) {
        _rules.update { list ->
            list.map { if (it.id == ruleId) it.copy(enabled = !it.enabled) else it }
        }
    }

    fun recordEvent(title: String, detail: String) {
        _events.update { list ->
            (listOf(AutomationEvent(System.nanoTime(), title, detail)) + list).take(25)
        }
    }

    suspend fun pingTcp(host: String, port: Int): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
            }
            (System.nanoTime() - start) / 1_000_000L
        }
    }

    suspend fun sendWakeOnLan(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val hex = macAddress.replace(":", "").replace("-", "").trim()
            require(hex.length == 12) { "Invalid MAC address" }
            val macBytes = ByteArray(6) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val packetBytes = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) packetBytes[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packetBytes, 6 + i * 6, 6)
            }
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val address = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(packetBytes, packetBytes.size, address, 9))
            }
            recordEvent("Wake-on-LAN Sent", "Magic packet broadcast to $macAddress")
        }
    }

    fun postNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.fz_lime))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() and 0xFFFF).toInt(), notification)
    }

    private fun ensureNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nexus Automations",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "mDNS discovery, LAN mode, and fleet automation alerts"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "nexus_automations"
    }
}
