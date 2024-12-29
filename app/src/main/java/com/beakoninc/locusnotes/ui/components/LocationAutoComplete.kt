package com.beakoninc.locusnotes.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.beakoninc.locusnotes.data.model.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationAutocomplete(
    initialLocation: Location? = null,
    onLocationSelected: (Location?) -> Unit
) {
    var query by remember { mutableStateOf(initialLocation?.name ?: "") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var locations by remember { mutableStateOf<List<Location>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { Geocoder(context) }

    // Get current location for sorting suggestions
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { userLocation ->
                userLocation?.let { location ->
                    // We can use this location to sort suggestions by distance
                    // Store the user's location for reference
                    val userLat = location.latitude
                    val userLng = location.longitude
                }
            }
        }
    }

    // Handle location search with debounce
    LaunchedEffect(query) {
        if (query.length >= 3) {
            isSearching = true
            delay(500) // Debounce typing

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(query, 5) { addresses ->
                        locations = addresses.map { address ->
                            Location(
                                name = address.featureName ?: address.locality ?: query,
                                latitude = address.latitude,
                                longitude = address.longitude,
                                address = address.getAddressLine(0)
                            )
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    locations = geocoder.getFromLocationName(query, 5)?.map { address ->
                        Location(
                            name = address.featureName ?: address.locality ?: query,
                            latitude = address.latitude,
                            longitude = address.longitude,
                            address = address.getAddressLine(0)
                        )
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                locations = emptyList()
            }

            isSearching = false
            isDropdownExpanded = locations.isNotEmpty()
        } else {
            locations = emptyList()
            isDropdownExpanded = false
        }
    }

    Column {
        TextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                if (newQuery.isEmpty()) {
                    onLocationSelected(null)
                }
            },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = "Location")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onLocationSelected(null)
                        isDropdownExpanded = false
                    }) {
                        Icon(Icons.Default.Close, "Clear location")
                    }
                }
            },
            singleLine = true
        )

        if (isDropdownExpanded && locations.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                LazyColumn {
                    items(locations) { location ->
                        ListItem(
                            headlineContent = { Text(location.name) },
                            supportingContent = location.address?.let { { Text(it) } },
                            leadingContent = {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                query = location.name
                                onLocationSelected(location)
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}