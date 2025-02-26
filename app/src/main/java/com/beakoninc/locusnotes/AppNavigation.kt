package com.beakoninc.locusnotes


import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.beakoninc.locusnotes.ui.notes.NoteList
import com.beakoninc.locusnotes.ui.debug.DebugScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "notes",
        modifier = modifier
    ) {
        composable("notes") {
            NoteList(navController = navController)
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
    // We'll implement this later
}