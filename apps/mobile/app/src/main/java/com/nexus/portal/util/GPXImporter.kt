package com.nexus.portal.util

import android.content.Context
import android.net.Uri
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import com.nexus.portal.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

object GPXImporter {

    suspend fun importRide(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            val points = mutableListOf<RideLocationPoint>()
            
            var eventType = parser.eventType
            var currentLat: Double? = null
            var currentLon: Double? = null
            var currentEle: Double = 0.0
            var currentTime: Long = 0
            
            // Custom extensions
            var currentSpeed: Float = 0f
            var currentGForce: Float = 0f
            var currentLeanAngle: Float = 0f
            var currentAccelLat: Float = 0f
            var currentAccelLong: Float = 0f

            var currentText = ""

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val sessionId = UUID.randomUUID().toString()
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trkpt" -> {
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                currentEle = 0.0
                                currentTime = 0
                                currentSpeed = 0f
                                currentGForce = 0f
                                currentLeanAngle = 0f
                                currentAccelLat = 0f
                                currentAccelLong = 0f
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        currentText = parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "ele" -> currentEle = currentText.toDoubleOrNull() ?: 0.0
                            "time" -> {
                                try {
                                    currentTime = sdf.parse(currentText)?.time ?: 0
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            "speed" -> currentSpeed = currentText.toFloatOrNull() ?: 0f
                            "gForce" -> currentGForce = currentText.toFloatOrNull() ?: 0f
                            "leanAngle" -> currentLeanAngle = currentText.toFloatOrNull() ?: 0f
                            "accelLateral" -> currentAccelLat = currentText.toFloatOrNull() ?: 0f
                            "accelLongitudinal" -> currentAccelLong = currentText.toFloatOrNull() ?: 0f
                            "trkpt" -> {
                                if (currentLat != null && currentLon != null && currentTime > 0) {
                                    val point = RideLocationPoint(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        latitude = currentLat,
                                        longitude = currentLon,
                                        altitude = currentEle,
                                        speedMetersPerSec = currentSpeed,
                                        leanAngle = currentLeanAngle,
                                        gForce = currentGForce,
                                        accelLateral = currentAccelLat,
                                        accelLongitudinal = currentAccelLong,
                                        timestamp = currentTime
                                    )
                                    points.add(point)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (points.isEmpty()) return@withContext null

            // Generate Session stats based on points
            var maxSpeed = 0f
            var maxLean = 0f
            var peakG = 0f
            var totalDistance = 0f

            points.sortBy { it.timestamp }
            
            for (i in points.indices) {
                val pt = points[i]
                if (pt.speedMetersPerSec > maxSpeed) maxSpeed = pt.speedMetersPerSec
                if (Math.abs(pt.leanAngle) > maxLean) maxLean = Math.abs(pt.leanAngle)
                if (Math.abs(pt.gForce) > peakG) peakG = Math.abs(pt.gForce)

                if (i > 0) {
                    val prev = points[i - 1]
                    val loc1 = android.location.Location("").apply {
                        latitude = prev.latitude
                        longitude = prev.longitude
                    }
                    val loc2 = android.location.Location("").apply {
                        latitude = pt.latitude
                        longitude = pt.longitude
                    }
                    totalDistance += loc1.distanceTo(loc2)
                }
            }

            val startTime = points.first().timestamp
            val endTime = points.last().timestamp
            val avgSpeed = if (endTime > startTime) {
                totalDistance / ((endTime - startTime) / 1000f)
            } else 0f

            val session = RideSession(
                id = sessionId,
                deviceName = "Imported GPX",
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = totalDistance,
                maxSpeedKmh = maxSpeed * 3.6f,
                averageSpeedKmh = avgSpeed * 3.6f,
                maxLeanAngle = maxLean,
                peakLatG = peakG,
                flowRating = 100, // Default value for imported rides
                vehicleType = "Motorcycle" // Or unknown
            )

            val db = RideDatabase.getDatabase(context)
            db.rideDao().upsertSession(session)
            points.forEach { db.rideDao().insertLocationPoint(it) }

            return@withContext sessionId

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            inputStream?.close()
        }
    }
}
