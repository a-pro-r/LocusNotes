package com.beakoninc.locusnotes.ui.components

import android.Manifest
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material3.Divider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationAutocomplete(
    initialLocation: Location? = null,
    onLocationSelected: (Location?) -> Unit,
    locationService: LocationService
) {
    var query by remember { mutableStateOf(initialLocation?.name ?: "") }
    var locations by remember { mutableStateOf<List<Location>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val locationPermissionState = rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        onPermissionResult = { isGranted ->
            if (isGranted) {
                // Launch coroutine to get current location when permission is granted
                coroutineScope.launch {
                    currentLocation = locationService.getCurrentLocation()
                }
            }
        }
    )


    // Get current location when composable is first created if permission is already granted
    LaunchedEffect(Unit) {
        if (locationPermissionState.status.isGranted) {
            currentLocation = locationService.getCurrentLocation()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                if (newQuery.isEmpty()) {
                    onLocationSelected(null)
                    showSuggestions = false
                    locations = emptyList()
                } else {
                    showSuggestions = true
                    coroutineScope.launch {
                        isSearching = true
                        try {
                            locationService.searchLocations(newQuery, currentLocation)
                                .collect { results ->
                                    locations = results
                                    isSearching = false
                                }
                        } catch (e: Exception) {
                            Log.e("LocationAutoComplete", "Error searching", e)
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search location") },
            leadingIcon = {
                Icon(Icons.Default.LocationOn, "Location")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onLocationSelected(null)
                        showSuggestions = false
                        locations = emptyList()
                    }) {
                        Icon(Icons.Default.Close, "Clear")
                    }
                }
            },
            singleLine = true
        )

        AnimatedVisibility(
            visible = showSuggestions && (locations.isNotEmpty() || isSearching),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    LazyColumn {
                        items(locations) { location ->
                            LocationSuggestionItem(
                                location = location,
                                onClick = {
                                    query = location.name
                                    onLocationSelected(location)
                                    showSuggestions = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSuggestionItem(
    location: Location,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                location.address?.let { address ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                location.distanceMeters?.let { distance ->
                    Text(
                        text = "%.1f km away".format(distance / 1000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}