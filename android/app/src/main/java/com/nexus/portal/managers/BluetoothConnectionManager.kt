package com.nexus.portal.managers

import kotlinx.coroutines.flow.Flow
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connected(val macAddress: String, val deviceName: String) : ConnectionState()
}

/**
 * App-wide Bluetooth Connection Manager.
 * Provides a clean cold Flow of BT ACL connection events usable by any service or sub-app.
 *
 * Architecture mirrors SensorRepository: the BroadcastReceiver is registered automatically
 * when the flow is collected and unregistered via awaitClose when collection is cancelled.
 * No manual startObserving/stopObserving required — the collector's coroutine lifetime
 * is the sole gate for receiver registration.
 *
 * Usage:
 *   serviceScope.launch {
 *       bluetoothConnectionManager.connectionState.collect { state -> ... }
 *   }
 */
interface BluetoothConnectionManager {
    val connectionState: Flow<ConnectionState>
}

class DefaultBluetoothConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothConnectionManager {

    override val connectionState: Flow<ConnectionState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val mac = device?.address ?: return

                val deviceName = try {
                    device.name ?: "Unknown"
                } catch (e: SecurityException) {
                    "Unknown"
                }

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        trySend(ConnectionState.Connected(mac, deviceName))
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        trySend(ConnectionState.Disconnected)
                    }
                }
            }
        }

        // Always register when the flow is collected — no isObserving guard.
        // The callbackFlow lifecycle is the sole gate for BroadcastReceiver registration.
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Ignore if not registered
            }
        }
    }
}
