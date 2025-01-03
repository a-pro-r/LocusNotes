package com.beakoninc.locusnotes.data.location

import android.content.Context
import android.location.Location
import android.util.Log
import com.beakoninc.locusnotes.data.model.Location as AppLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/search"
    private val USER_AGENT = "LocusNotes Android App"

    suspend fun getCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener { exception ->
                    Log.e("LocationService", "Error getting location", exception)
                    continuation.resume(null)
                }
            } catch (e: SecurityException) {
                Log.e("LocationService", "Security exception getting location", e)
                continuation.resume(null)
            }
        }

    suspend fun searchLocations(query: String): List<AppLocation> = withContext(Dispatchers.IO) {
        if (query.length < 3) return@withContext emptyList() // Don't search for very short queries

        try {
            delay(1000) // Nominatim's usage policy

            val currentLocation = getCurrentLocation()
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = buildSearchUrl(encodedQuery, currentLocation)

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15000
                readTimeout = 15000
            }

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("LocationService", "Search response for '$query': $response")
                    parseNominatimResponse(response, currentLocation)
                } else {
                    Log.e("LocationService", "HTTP Error: ${connection.responseCode}")
                    emptyList()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Error searching locations", e)
            emptyList()
        }
    }

    private fun buildSearchUrl(query: String, currentLocation: Location?): URL {
        val urlBuilder = StringBuilder("$NOMINATIM_BASE_URL?")
        urlBuilder.append("q=$query")
        urlBuilder.append("&format=json")
        urlBuilder.append("&addressdetails=1") // Get detailed address components
        urlBuilder.append("&limit=5") // Limit to 5 most relevant results

        // Add location bias if available
        if (currentLocation != null) {
            urlBuilder.append("&lat=${currentLocation.latitude}")
            urlBuilder.append("&lon=${currentLocation.longitude}")
            urlBuilder.append("&bounded=1") // Prefer results near the location
        }

        return URL(urlBuilder.toString())
    }

    private fun parseNominatimResponse(
        jsonString: String,
        currentLocation: Location?
    ): List<AppLocation> {
        return try {
            val jsonArray = JSONArray(jsonString)
            val locations = mutableListOf<AppLocation>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val address = obj.getJSONObject("address")

                // Build primary name
                val name = buildPrimaryName(address, obj)

                val location = AppLocation(
                    name = name,
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lon"),
                    address = formatFullAddress(address),
                    placeId = obj.getString("place_id"),
                    streetNumber = address.optString("house_number"),
                    route = address.optString("road"),
                    locality = getLocality(address),
                    administrativeArea = address.optString("state"),
                    country = address.optString("country"),
                    postalCode = address.optString("postcode")
                )

                // Add distance from current location if available
                currentLocation?.let {
                    val distance = calculateDistance(
                        it.latitude, it.longitude,
                        location.latitude, location.longitude
                    )
                    // Only add locations within reasonable distance (e.g., 50km)
                    if (distance <= 50) {
                        locations.add(location)
                    }
                } ?: locations.add(location)
            }

            locations.sortedBy {
                calculateRelevanceScore(it, query = jsonString, currentLocation)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Error parsing response", e)
            emptyList()
        }
    }

    private fun buildPrimaryName(address: JSONObject, fullObject: JSONObject): String {
        return buildString {
            // Try to build a readable name from components
            val houseName = address.optString("house_name")
            val houseNumber = address.optString("house_number")
            val road = address.optString("road")
            val amenity = address.optString("amenity")

            when {
                amenity.isNotEmpty() -> append(amenity)
                houseName.isNotEmpty() -> append(houseName)
                houseNumber.isNotEmpty() && road.isNotEmpty() ->
                    append("$houseNumber $road")
                road.isNotEmpty() -> append(road)
                else -> append(fullObject.optString("display_name").split(",")[0])
            }

            // Add locality if not already included
            val locality = getLocality(address)
            if (locality.isNotEmpty() && !contains(locality, ignoreCase = true)) {
                append(", $locality")
            }
        }
    }

    private fun formatFullAddress(address: JSONObject): String {
        return buildString {
            val components = listOf(
                address.optString("house_number"),
                address.optString("road"),
                getLocality(address),
                address.optString("state"),
                address.optString("postcode"),
                address.optString("country")
            )

            append(components.filter { it.isNotEmpty() }.joinToString(", "))
        }
    }

    private fun getLocality(address: JSONObject): String {
        return address.optString("city")
            .ifEmpty { address.optString("town") }
            .ifEmpty { address.optString("village") }
            .ifEmpty { address.optString("suburb") }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
        return r * c
    }

    private fun calculateRelevanceScore(
        location: AppLocation,
        query: String,
        currentLocation: Location?
    ): Double {
        var score = 0.0

        // Distance score (if current location available)
        currentLocation?.let {
            val distance = calculateDistance(
                it.latitude, it.longitude,
                location.latitude, location.longitude
            )
            score += distance * 0.5 // Weight distance less than exact matches
        }

        // Exact name matches
        if (location.name.contains(query, ignoreCase = true)) {
            score -= 100 // Prioritize exact matches
        }

        return score
    }
}