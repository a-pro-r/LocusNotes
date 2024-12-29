package com.beakoninc.locusnotes.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.beakoninc.locusnotes.data.model.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.util.Log
import java.util.Locale

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geocoder = Geocoder(context, Locale.getDefault())
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): android.location.Location? =
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

    suspend fun searchLocations(query: String, currentLocation: Location? = null): List<Location> =
        withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<Location>()

                // First try to get user's current location
                val userLocation = getCurrentLocation()

                if (userLocation != null) {
                    Log.d("LocationService", "Searching near user location: ${userLocation.latitude}, ${userLocation.longitude}")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(
                            userLocation.latitude,
                            userLocation.longitude,
                            15
                        ) { addresses ->
                            addresses
                                .filter { address ->
                                    addressMatchesQuery(address, query)
                                }
                                .forEach { address ->
                                    results.add(createLocationFromAddress(address))
                                }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(
                            userLocation.latitude,
                            userLocation.longitude,
                            15
                        )?.filter { address ->
                            addressMatchesQuery(address, query)
                        }?.forEach { address ->
                            results.add(createLocationFromAddress(address))
                        }
                    }
                }

                Log.d("LocationService", "Found ${results.size} nearby results")
                results

            } catch (e: Exception) {
                Log.e("LocationService", "Error searching locations", e)
                emptyList()
            }
        }

    private fun addressMatchesQuery(address: Address, query: String): Boolean {
        val searchQuery = query.lowercase()
        return address.featureName?.lowercase()?.contains(searchQuery) == true ||
                address.thoroughfare?.lowercase()?.contains(searchQuery) == true ||
                address.locality?.lowercase()?.contains(searchQuery) == true ||
                address.premises?.lowercase()?.contains(searchQuery) == true ||
                address.subLocality?.lowercase()?.contains(searchQuery) == true
    }

    private fun createLocationFromAddress(address: Address): Location {
        val name = buildDisplayName(address)
        Log.d("LocationService", "Creating location: $name at ${address.latitude}, ${address.longitude}")
        return Location(
            name = name,
            latitude = address.latitude,
            longitude = address.longitude,
            address = address.getAddressLine(0)
        )
    }

    private fun buildDisplayName(address: Address): String {
        return when {
            !address.featureName.isNullOrBlank() && !address.featureName.contains(Regex("^\\d+$")) ->
                address.featureName
            !address.thoroughfare.isNullOrBlank() -> {
                if (!address.subThoroughfare.isNullOrBlank()) {
                    "${address.subThoroughfare} ${address.thoroughfare}"
                } else {
                    address.thoroughfare
                }
            }
            !address.locality.isNullOrBlank() -> address.locality
            else -> address.getAddressLine(0) ?: ""
        }.trim()
    }
}