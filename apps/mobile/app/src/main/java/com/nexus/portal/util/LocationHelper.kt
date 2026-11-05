package com.nexus.portal.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object LocationHelper {

    suspend fun getCurrentLocationAndWeather(context: Context): WeatherInfo? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        try {
            // Actively request a highly accurate fresh location from the GPS hardware
            val location = try {
                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            } catch (e: Exception) {
                fusedLocationClient.lastLocation.await()
            } ?: return@withContext null

            val lat = location.latitude
            val lon = location.longitude

            // Get City Name
            var cityName = "Unknown"
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    cityName = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "Unknown"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Get Weather from Open-Meteo
            var temperature = "--"
            var weatherDesc = "Unknown"
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val currentWeather = json.getJSONObject("current_weather")
                    temperature = "${currentWeather.getDouble("temperature")}°C"
                    
                    val weatherCode = currentWeather.getInt("weathercode")
                    weatherDesc = mapWeatherCode(weatherCode)
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext WeatherInfo(lat, lon, cityName, temperature, weatherDesc)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75 -> "Snow fall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Clear"
        }
    }

    suspend fun fetchPOIs(lat: Double, lon: Double, amenity: String): List<POI> = withContext(Dispatchers.IO) {
        val radius = 3000 // 3km radius
        // Encode the query properly
        val query = "[out:json][timeout:10];node[\"amenity\"=\"$amenity\"](around:$radius,$lat,$lon);out 20;"
        val urlString = "https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(query, "UTF-8")

        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val elements = json.getJSONArray("elements")
                val pois = mutableListOf<POI>()
                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    val pLat = element.getDouble("lat")
                    val pLon = element.getDouble("lon")
                    val tags = element.optJSONObject("tags")
                    val name = tags?.optString("name", "Unknown") ?: "Unknown"
                    if (name != "Unknown") {
                        pois.add(POI(pLat, pLon, name, amenity))
                    }
                }
                return@withContext pois
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }
}

data class WeatherInfo(
    val lat: Double,
    val lon: Double,
    val city: String,
    val temperature: String,
    val description: String
)

data class POI(
    val lat: Double,
    val lon: Double,
    val name: String,
    val type: String
)
