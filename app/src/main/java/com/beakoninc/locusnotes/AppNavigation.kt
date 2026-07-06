package com.beakoninc.locusnotes


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.beakoninc.locusnotes.ui.notes.NoteList
import com.beakoninc.locusnotes.ui.debug.DebugScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    noteIdToOpen: String? = null,
    onNoteOpened: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = "notes",
        modifier = modifier
    ) {
        composable("notes") {
            NoteList(
                navController = navController,
                noteIdToOpen = noteIdToOpen,
                onNoteOpened = onNoteOpened
            )
        }

        composable("map") {
            MapScreen(navController)
        }
        composable("debug") {
            DebugScreen(navController = navController)
        }
        // Add more destinations here as we develop them
    }
}

@Composable
fun MapScreen(navController: NavHostController) {
    // Placeholder until the OSMDroid map with note pins is implemented
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("Map coming soon", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your notes will appear as pins on a map",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
