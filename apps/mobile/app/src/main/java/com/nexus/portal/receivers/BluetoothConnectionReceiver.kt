package com.nexus.portal.receivers

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexus.portal.MainActivity
import com.nexus.portal.core.AppEventBus
import com.nexus.portal.core.AppEvent
import com.nexus.portal.services.RideTrackerService
import com.nexus.portal.util.BluetoothMacUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives system BT ACL events and posts them to AppEventBus.
 *
 * Intentionally synchronous — isKnownVehicleMac() is a SharedPreferences read (< 1ms)
 * and AppEventBus.emitEvent() uses tryEmit() which is non-suspending and safe on the
 * main thread. Removing goAsync() + CoroutineScope eliminates the scope-leak that existed
 * when BroadcastReceiver instances were recycled between broadcasts.
 */
@AndroidEntryPoint
class BluetoothConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var appEventBus: AppEventBus

    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            android.Manifest.permission.BLUETOOTH
        }

        val mac = try {
            BluetoothMacUtils.normalize(device?.address)
        } catch (se: SecurityException) {
            Log.w("BTReceiver", "Missing Bluetooth permission: $permission")
            try {
                val permsIntent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_REQUEST_PERMISSIONS
                    putExtra(MainActivity.EXTRA_PERMISSIONS, arrayOf(permission))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(permsIntent)
            } catch (_: Exception) { }
            return
        }

        if (mac.isEmpty()) {
            Log.d("BTReceiver", "Empty MAC; ignoring")
            return
        }

        // isKnownVehicleMac is a synchronous SharedPreferences read — safe on receiver thread.
        if (!BluetoothMacUtils.isKnownVehicleMac(context, mac)) {
            Log.d("BTReceiver", "Ignoring non-garage device: $mac")
            return
        }

        val deviceName = try {
            device?.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Unknown"
        }

        try {
            when (intentAction) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d("BTReceiver", "Garage device connected: $mac")
                    // tryEmit is non-suspending; AppEventBus uses MutableSharedFlow with extraBufferCapacity
                    appEventBus.emitEvent(AppEvent.GarageDeviceConnected(mac, deviceName))

                    // Fallback: start RideTrackerService directly in case observer hasn't started yet
                    try {
                        val svcIntent = Intent(context, RideTrackerService::class.java).apply {
                            action = RideTrackerService.ACTION_DEVICE_CONNECTED
                            putExtra(RideTrackerService.EXTRA_MAC_ADDRESS, mac)
                            putExtra(RideTrackerService.EXTRA_DEVICE_NAME, deviceName)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, svcIntent)
                        } else {
                            context.startService(svcIntent)
                        }
                    } catch (e: Exception) {
                        Log.w("BTReceiver", "Failed to start RideTrackerService fallback", e)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d("BTReceiver", "Garage device disconnected: $mac")
                    BluetoothMacUtils.markDisconnected(mac)
                    appEventBus.emitEvent(AppEvent.GarageDeviceDisconnected(mac))
                }
            }
        } catch (e: Exception) {
            Log.e("BTReceiver", "Error handling BT event", e)
        }
    }
}
