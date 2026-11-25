package com.nexus.portal.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleConverters
import kotlinx.coroutines.flow.firstOrNull

object BluetoothMacUtils {

    private const val PREFS = "ride_tracker_bt_prefs"
    private const val KEY_KNOWN_MACS = "known_vehicle_macs"

    private val DASH_CATEGORIES = setOf(
        "Vehicle Dash",
        "Scooter Dash",
        "Car Screen"
    )

    fun isDashCategory(category: String?): Boolean {
        return category?.trim() in DASH_CATEGORIES
    }

    fun isHelmetCategory(category: String?): Boolean {
        return category?.trim() == "Helmet Intercom"
    }

    /** Canonical form: AA:BB:CC:DD:EE:FF (uppercase, colon-separated). */
    fun normalize(mac: String?): String {
        if (mac.isNullOrBlank()) return ""
        val clean = mac.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        if (clean.length != 12) return mac.trim().uppercase()
        return clean.chunked(2).joinToString(":")
    }

    fun macsEqual(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotEmpty() && na == nb
    }

    fun containsMac(set: Set<String>, mac: String): Boolean {
        val target = normalize(mac)
        return set.any { normalize(it) == target }
    }

    suspend fun refreshKnownMacsCache(context: Context) {
        val db = RideDatabase.getDatabase(context)
        val vehicles = db.vehicleDao().getAllVehiclesSync()
        val converter = VehicleConverters()
        val macs = vehicles.flatMap { v ->
            converter.toConnectedDevicesList(v.connectedDevicesJson)
                .mapNotNull { d -> normalize(d.macAddress).takeIf { it.isNotEmpty() } }
        }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_KNOWN_MACS, macs)
            .apply()
    }

    fun getKnownMacs(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_KNOWN_MACS, emptySet()) ?: emptySet()
        return raw.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
    }

    fun isKnownVehicleMac(context: Context, mac: String): Boolean {
        return containsMac(getKnownMacs(context), mac)
    }

    // Workaround for Android's stale Bluetooth cache: keep track of MACs we know just disconnected
    private val recentlyDisconnectedMacs = mutableMapOf<String, Long>()

    fun markDisconnected(mac: String) {
        recentlyDisconnectedMacs[normalize(mac)] = System.currentTimeMillis()
    }

    /**
     * Probes bonded + profile-connected devices for saved garage MACs.
     * Reflection isConnected() alone often returns false for scooter dash units.
     */
    fun probeConnectedSavedMacs(context: Context, savedMacs: Set<String>): Set<String> {
        if (savedMacs.isEmpty()) return emptySet()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            android.Manifest.permission.BLUETOOTH
        }
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return emptySet()
        }

        val found = mutableSetOf<String>()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return found
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

        if (manager != null) {
            val profiles = intArrayOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                BluetoothProfile.HID_DEVICE,
                BluetoothProfile.GATT
            )
            for (profile in profiles) {
                try {
                    manager.getConnectedDevices(profile).forEach { device ->
                        val normalized = normalize(device.address)
                        if (containsMac(savedMacs, normalized)) {
                            found.add(normalized)
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        // Removed buggy reflection checks (adapter.bondedDevices and getMethod("isConnected")).
        // These checks return stale cache data on modern Android versions, causing
        // offline devices to appear as connected (ghost connections).
        // Real-time raw ACL connections are now fully handled by BluetoothConnectionReceiver.

        // Filter out any devices that we *know* disconnected recently (within 60 seconds),
        // because Android's OS cache often lags behind the actual broadcast by up to 30s.
        val now = System.currentTimeMillis()
        recentlyDisconnectedMacs.entries.removeIf { now - it.value > 60_000 }
        
        return found.filter { !recentlyDisconnectedMacs.containsKey(it) }.toSet()
    }
}
