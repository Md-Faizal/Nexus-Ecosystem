package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.geometry.LatLng
import com.nexus.portal.data.local.ridetracker.RideLocationPoint

fun createKmMarkerBitmap(km: Int): Bitmap {
    val s = 100
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Background circle
    p.color = Color.parseColor("#9C27B0")
    cv.drawCircle(s / 2f, s / 2f, s / 2f - 4f, p)
    
    // Border
    p.style = Paint.Style.STROKE
    p.color = Color.WHITE
    p.strokeWidth = 6f
    cv.drawCircle(s / 2f, s / 2f, s / 2f - 4f, p)
    
    // Text
    p.style = Paint.Style.FILL
    p.color = Color.WHITE
    p.textSize = 28f
    p.typeface = Typeface.DEFAULT_BOLD
    p.textAlign = Paint.Align.CENTER
    cv.drawText("${km} KM", s / 2f, s / 2f + 10f, p)
    
    return bm
}

fun createStartMarkerBitmap(): Bitmap {
    val s = 60
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.parseColor("#00E676") // Green
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)
    p.color = Color.WHITE
    cv.drawCircle(s / 2f, s / 2f, s / 4f, p)
    return bm
}

fun createEndMarkerBitmap(): Bitmap {
    val s = 60
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.parseColor("#FF1744") // Red
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)
    p.color = Color.WHITE
    cv.drawCircle(s / 2f, s / 2f, s / 4f, p)
    return bm
}

fun drawRouteOnMap(context: Context, mapplsMap: MapplsMap, points: List<RideLocationPoint>, showKilometerMarkers: Boolean = true) {
    if (points.isEmpty()) return

    // Multi-color polylines based on speed
    val maxSpeed = points.maxOfOrNull { it.speedMetersPerSec } ?: 1f
    var distanceSoFar = 0f
    var nextKmMarker = 1
    
    var currentSegmentColor = -1
    val currentSegmentPoints = mutableListOf<LatLng>()
    
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val speedRatio = p1.speedMetersPerSec / maxSpeed
        val segmentColor = when {
            speedRatio > 0.75f -> Color.parseColor("#FF1744") // Red
            speedRatio > 0.4f -> Color.parseColor("#FFC107")  // Amber
            else -> Color.parseColor("#00E676") // Green
        }
        
        if (segmentColor != currentSegmentColor) {
            if (currentSegmentPoints.isNotEmpty()) {
                currentSegmentPoints.add(LatLng(p1.latitude, p1.longitude))
                mapplsMap.addPolyline(PolylineOptions().addAll(currentSegmentPoints).color(currentSegmentColor).width(8f))
            }
            currentSegmentPoints.clear()
            currentSegmentPoints.add(LatLng(p1.latitude, p1.longitude))
            currentSegmentColor = segmentColor
        }
        currentSegmentPoints.add(LatLng(p2.latitude, p2.longitude))
        
        val results = FloatArray(1)
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        distanceSoFar += results[0]
        
        if (showKilometerMarkers && distanceSoFar >= nextKmMarker * 1000f) {
            val markerBm = createKmMarkerBitmap(nextKmMarker)
            val icon = IconFactory.getInstance(context).fromBitmap(markerBm)
            mapplsMap.addMarker(
                MarkerOptions()
                    .position(LatLng(p2.latitude, p2.longitude))
                    .icon(icon)
            )
            nextKmMarker++
        }
    }
    
    if (currentSegmentPoints.isNotEmpty()) {
        mapplsMap.addPolyline(PolylineOptions().addAll(currentSegmentPoints).color(currentSegmentColor).width(8f))
    }
    
    val startPt = points.first()
    val startIcon = IconFactory.getInstance(context).fromBitmap(createStartMarkerBitmap())
    mapplsMap.addMarker(MarkerOptions().position(LatLng(startPt.latitude, startPt.longitude)).icon(startIcon))
    
    val endPt = points.last()
    val endIcon = IconFactory.getInstance(context).fromBitmap(createEndMarkerBitmap())
    mapplsMap.addMarker(MarkerOptions().position(LatLng(endPt.latitude, endPt.longitude)).icon(endIcon))
}
