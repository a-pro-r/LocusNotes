package com.beakoninc.locusnotes.data.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.beakoninc.locusnotes.data.model.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LocationService @Inject constructor(
    private val context: Context
) {
    private val geocoder = Geocoder(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                val cancellationToken = CancellationTokenSource().token
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            }
        }

    suspend fun searchLocations(query: String, currentLocation: Location? = null): List<Location> =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val results = ArrayList<Location>()
                    geocoder.getFromLocationName(query, 5) { addresses ->
                        addresses.forEach { address ->
                            results.add(
                                Location(
                                    name = address.featureName ?: address.locality ?: query,
                                    latitude = address.latitude,
                                    longitude = address.longitude,
                                    address = address.getAddressLine(0)
                                )
                            )
                        }
                    }
                    sortByProximity(results, currentLocation)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5)?.map { address ->
                        Location(
                            name = address.featureName ?: address.locality ?: query,
                            latitude = address.latitude,
                            longitude = address.longitude,
                            address = address.getAddressLine(0)
                        )
                    }?.let { locations ->
                        sortByProximity(locations, currentLocation)
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun sortByProximity(locations: List<Location>, currentLocation: Location?): List<Location> {
        if (currentLocation == null) return locations

        return locations.sortedBy { location ->
            calculateDistance(
                currentLocation.latitude,
                currentLocation.longitude,
                location.latitude,
                location.longitude
            )
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val R = 6371 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}