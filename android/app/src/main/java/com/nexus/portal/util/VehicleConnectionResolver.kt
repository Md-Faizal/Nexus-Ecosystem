package com.nexus.portal.util

import android.content.Context
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleConverters
import kotlinx.coroutines.flow.firstOrNull

data class VehicleConnectionResolution(
    val vehicleId: String,
    val vehicleName: String,
    val vehicleType: String,
    val connectionLabel: String
)

/**
 * Scores connected MACs against garage vehicles.
 * Dash (+10): Vehicle Dash, Scooter Dash, Car Screen.
 * Helmet (+1): Helmet Intercom.
 * Helmet-only tie-break: prefer vehicle without a dash configured (V2 over V1).
 */
suspend fun resolveVehicleForConnectedMacs(
    macs: Set<String>,
    context: Context
): VehicleConnectionResolution? {
    val normalizedMacs = macs.map { BluetoothMacUtils.normalize(it) }.filter { it.isNotEmpty() }.toSet()
    if (normalizedMacs.isEmpty()) return null

    val db = RideDatabase.getDatabase(context)
    val vehicles = db.vehicleDao().getAllVehiclesSync()
    if (vehicles.isEmpty()) return null
    val converter = VehicleConverters()

    data class Candidate(
        val vehicleId: String,
        val vehicleName: String,
        val vehicleType: String,
        val score: Int,
        val hasDash: Boolean,
        val dashConnected: Boolean,
        val helmetConnected: Boolean
    )

    val candidates = vehicles.mapNotNull { v ->
        val devices = converter.toConnectedDevicesList(v.connectedDevicesJson)
        val dashDevices = devices.filter { BluetoothMacUtils.isDashCategory(it.deviceCategory) }
        val helmetDevices = devices.filter { BluetoothMacUtils.isHelmetCategory(it.deviceCategory) }
        val otherDevices = devices.filter {
            !BluetoothMacUtils.isDashCategory(it.deviceCategory) &&
                !BluetoothMacUtils.isHelmetCategory(it.deviceCategory)
        }

        val dashConnected = dashDevices.any { BluetoothMacUtils.containsMac(normalizedMacs, it.macAddress) }
        val helmetConnected = helmetDevices.any { BluetoothMacUtils.containsMac(normalizedMacs, it.macAddress) }
        val otherConnected = otherDevices.any { BluetoothMacUtils.containsMac(normalizedMacs, it.macAddress) }

        val score = (if (dashConnected) 10 else 0) +
            (if (helmetConnected) 1 else 0) +
            (if (otherConnected) 1 else 0)

        if (score == 0) null
        else Candidate(
            vehicleId = v.id,
            vehicleName = v.name,
            vehicleType = v.type,
            score = score,
            hasDash = dashDevices.isNotEmpty(),
            dashConnected = dashConnected,
            helmetConnected = helmetConnected
        )
    }

    if (candidates.isEmpty()) return null

    val maxScore = candidates.maxOf { it.score }
    val top = candidates.filter { it.score == maxScore }
    val anyDashLive = candidates.any { it.dashConnected }

    val best = if (anyDashLive) {
        top.sortedWith(compareByDescending<Candidate> { it.hasDash }).first()
    } else {
        top.sortedWith(compareBy<Candidate> { it.hasDash }).first()
    }

    val label = when {
        best.dashConnected && best.helmetConnected -> "Dash + Helmet"
        best.dashConnected -> "Dash Only"
        else -> "Helmet Only"
    }

    return VehicleConnectionResolution(
        vehicleId = best.vehicleId,
        vehicleName = best.vehicleName,
        vehicleType = best.vehicleType,
        connectionLabel = label
    )
}

/** Collect MACs that are saved in garage and currently connected (probe + active set). */
fun collectLiveGarageMacs(context: Context, activeMacs: Set<String>): Set<String> {
    val known = BluetoothMacUtils.getKnownMacs(context)
    val live = activeMacs.map { BluetoothMacUtils.normalize(it) }.filter { it.isNotEmpty() }.toMutableSet()
    live.addAll(BluetoothMacUtils.probeConnectedSavedMacs(context, known))
    return live
}
