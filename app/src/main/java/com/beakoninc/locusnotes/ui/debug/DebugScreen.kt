package com.beakoninc.locusnotes.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.ui.notes.NoteViewModel
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: NoteViewModel = hiltViewModel(),
    navController: NavController
) {
    val currentActivity by viewModel.activityRecognitionManager.currentActivity
        .collectAsState(initial = DetectedActivity.UNKNOWN)
    val nearbyNotes by viewModel.nearbyNotes.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Recognition Debug") },

                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Activity Status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Current Activity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        getActivityString(currentActivity),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Activity Updates: ${if (currentActivity != DetectedActivity.UNKNOWN) "Working" else "Not working"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentActivity != DetectedActivity.UNKNOWN)
                            Color.Green else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Activity Simulation
            Text(
                "Simulate Activity",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    scope.launch {
                        viewModel.activityRecognitionManager.simulateActivity(DetectedActivity.STILL)
                    }
                }) {
                    Text("Still")
                }

                Button(onClick = {
                    scope.launch {
                        viewModel.activityRecognitionManager.simulateActivity(DetectedActivity.WALKING)
                    }
                }) {
                    Text("Walking")
                }

                Button(onClick = {
                    scope.launch {
                        viewModel.activityRecognitionManager.simulateActivity(DetectedActivity.IN_VEHICLE)
                    }
                }) {
                    Text("Vehicle")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Proximity Check
            Button(
                onClick = {
                    scope.launch {
                        viewModel.checkNoteProximity()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Nearby Notes Now")

            }
            Text(
                "Last check: ${if (nearbyNotes.isEmpty()) "No nearby notes found" else "${nearbyNotes.size} notes nearby"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Nearby Notes Section
            Text(
                "Nearby Notes (${nearbyNotes.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (nearbyNotes.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        "No nearby notes found",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    items(nearbyNotes) { note ->
                        NearbyNoteItem(note = note)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyNoteItem(note: Note) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            note.locationName?.let {
                Text(
                    text = "Location: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (note.latitude != null && note.longitude != null) {
                Text(
                    text = "Coordinates: (${note.latitude}, ${note.longitude})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun getActivityString(type: Int): String = when (type) {
    DetectedActivity.STILL -> "Still"
    DetectedActivity.WALKING -> "Walking"
    DetectedActivity.RUNNING -> "Running"
    DetectedActivity.IN_VEHICLE -> "In Vehicle"
    DetectedActivity.ON_BICYCLE -> "On Bicycle"
    DetectedActivity.ON_FOOT -> "On Foot"
    DetectedActivity.TILTING -> "Tilting"
    DetectedActivity.UNKNOWN -> "Unknown"
    else -> "Unknown"
}