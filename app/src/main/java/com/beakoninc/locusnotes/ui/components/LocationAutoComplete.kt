package com.beakoninc.locusnotes.ui.components

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var showSuggestions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                locationService.getCurrentLocation()?.let { location ->
                    currentLocation = Location(
                        name = "Current Location",
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissionState.status == PermissionStatus.Granted) {
            locationService.getCurrentLocation()?.let { location ->
                currentLocation = Location(
                    name = "Current Location",
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
        } else {
            locationPermissionState.launchPermissionRequest()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                if (newQuery.isEmpty()) {
                    onLocationSelected(null)
                    showSuggestions = false
                } else {
                    showSuggestions = true
                    coroutineScope.launch {
                        isSearching = true
                        delay(300) // Debounce typing
                        try {
                            locations = locationService.searchLocations(newQuery, currentLocation)
                        } finally {
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search location") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onLocationSelected(null)
                        showSuggestions = false
                    }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        AnimatedVisibility(
            visible = showSuggestions && (locations.isNotEmpty() || isSearching),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!location.address.isNullOrBlank()) {
                    Text(
                        text = location.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    Divider()
}