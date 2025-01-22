package com.beakoninc.locusnotes.data.location

import android.content.Context
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.beakoninc.locusnotes.data.model.Location
import kotlin.coroutines.resume

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(
                        Location(
                            name = "Current Location",
                            latitude = location.latitude,
                            longitude = location.longitude,
                        )
                    )
                } else {
                    continuation.resume(null)
                }
            }.addOnFailureListener { exception ->
                Log.e("LocationService", "Error getting location", exception)
                continuation.resume(null)
            }
        } catch (e: SecurityException) {
            Log.e("LocationService", "Security exception getting location", e)
            continuation.resume(null)
        }
    }

    suspend fun searchLocations(
        query: String,
        currentLocation: Location? = null
    ): Flow<List<Location>> = flow {
        if (query.length < 2) {
            emit(emptyList())
            return@flow
        }

        try {
            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host("photon.komoot.io")
                .addPathSegment("api")
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "5")

            // Add location bias if available
            if (currentLocation?.latitude != null && currentLocation.longitude != null) {
                urlBuilder
                    .addQueryParameter("lat", currentLocation.latitude.toString())
                    .addQueryParameter("lon", currentLocation.longitude.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                Log.e("LocationService", "Error: ${response.code}")
                emit(emptyList())
                return@flow
            }

            val jsonResponse = JSONObject(response.body?.string() ?: "")
            val features = jsonResponse.getJSONArray("features")

            val locations = (0 until features.length()).map { i ->
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")

                val location = Location(
                    name = buildLocationName(properties),
                    latitude = coordinates.getDouble(1),
                    longitude = coordinates.getDouble(0),
                    address = buildAddress(properties),
                    placeId = properties.optString("osm_id"),
                    streetNumber = properties.optString("housenumber"),
                    route = properties.optString("street"),
                    locality = properties.optString("city"),
                    administrativeArea = properties.optString("state"),
                    country = properties.optString("country"),
                    postalCode = properties.optString("postcode")
                )

                // Calculate distance if current location is available
                if (currentLocation?.latitude != null && currentLocation.longitude != null) {
                    location.copy(
                        distanceMeters = calculateDistance(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            location.latitude ?: 0.0,
                            location.longitude ?: 0.0
                        )
                    )
                } else {
                    location
                }
            }

            // Sort by distance if available
            val sortedLocations = if (currentLocation != null) {
                locations.sortedBy { it.distanceMeters }
            } else {
                locations
            }

            emit(sortedLocations)

        } catch (e: Exception) {
            Log.e("LocationService", "Error searching locations", e)
            emit(emptyList())
        }
    }.debounce(300)
        .flowOn(Dispatchers.IO)

    private fun buildLocationName(properties: JSONObject): String {
        return buildString {
            val name = properties.optString("name")
            val street = properties.optString("street")
            val houseNumber = properties.optString("housenumber")
            val city = properties.optString("city")

            if (name.isNotEmpty()) {
                append(name)
            } else if (street.isNotEmpty()) {
                if (houseNumber.isNotEmpty()) {
                    append(houseNumber)
                    append(" ")
                }
                append(street)
            }

            if (city.isNotEmpty() && !name.contains(city)) {
                append(", ")
                append(city)
            }
        }
    }

    private fun buildAddress(properties: JSONObject): String {
        return buildString {
            val components = listOfNotNull(
                properties.optString("housenumber"),
                properties.optString("street"),
                properties.optString("city"),
                properties.optString("state"),
                properties.optString("postcode"),
                properties.optString("country")
            ).filter { it.isNotEmpty() }

            append(components.joinToString(", "))
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371e3 // Earth's radius in meters
        val lat1Rad = lat1 * Math.PI/180
        val lat2Rad = lat2 * Math.PI/180
        val latDiff = (lat2-lat1) * Math.PI/180
        val lonDiff = (lon2-lon1) * Math.PI/180

        val a = Math.sin(latDiff/2) * Math.sin(latDiff/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(lonDiff/2) * Math.sin(lonDiff/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

        return earthRadius * c
    }
}