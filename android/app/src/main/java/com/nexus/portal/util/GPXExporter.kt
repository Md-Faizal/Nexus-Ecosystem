package com.nexus.portal.util

import android.content.Context
import androidx.core.content.FileProvider
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GPXExporter {

    fun exportRide(context: Context, session: RideSession, points: List<RideLocationPoint>): File? {
        if (points.isEmpty()) return null

        try {
            val fileName = "Ride_${session.id}.gpx"
            val file = File(context.cacheDir, fileName)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            FileOutputStream(file).use { fos ->
                val writer = fos.writer()
                
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<gpx version=\"1.1\" creator=\"Nexus Portal\">\n")
                writer.write("  <trk>\n")
                writer.write("    <name>Ride ${session.id}</name>\n")
                writer.write("    <trkseg>\n")
                
                for (point in points) {
                    writer.write("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
                    writer.write("        <ele>${point.altitude}</ele>\n")
                    writer.write("        <time>${sdf.format(Date(point.timestamp))}</time>\n")
                    writer.write("        <extensions>\n")
                    writer.write("          <speed>${point.speedMetersPerSec}</speed>\n")
                    writer.write("          <gForce>${point.gForce}</gForce>\n")
                    writer.write("          <leanAngle>${point.leanAngle}</leanAngle>\n")
                    writer.write("          <accelLateral>${point.accelLateral}</accelLateral>\n")
                    writer.write("          <accelLongitudinal>${point.accelLongitudinal}</accelLongitudinal>\n")
                    writer.write("        </extensions>\n")
                    writer.write("      </trkpt>\n")
                }
                
                writer.write("    </trkseg>\n")
                writer.write("  </trk>\n")
                writer.write("</gpx>\n")
                
                writer.flush()
            }
            
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
