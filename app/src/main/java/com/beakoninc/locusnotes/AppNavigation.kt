package com.beakoninc.locusnotes

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beakoninc.locusnotes.ui.notes.NoteList

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "notes") {
        composable("notes") {
            NoteList()
        }

        composable("home") {
            HomeScreen(navController)
        }
        // Add more destinations here as we develop them
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    // We'll implement this later
}