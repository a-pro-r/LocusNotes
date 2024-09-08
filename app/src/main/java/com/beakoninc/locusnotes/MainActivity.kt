package com.beakoninc.locusnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.beakoninc.locusnotes.ui.theme.LocusNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocusNotesTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(navController) {
                    scope.launch {
                        drawerState.close()
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LocusNotes") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            AppNavigation(navController, Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun NavigationDrawerContent(navController: NavController, onItemClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("LocusNotes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.List, contentDescription = "Notes") },
            label = { Text("Notes") },
            selected = navController.currentDestination?.route == "notes",
            onClick = {
                navController.navigate("notes")
                onItemClick()
            }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Place, contentDescription = "Map") },
            label = { Text("Map") },
            selected = navController.currentDestination?.route == "map",
            onClick = {
                navController.navigate("map")
                onItemClick()
            }
        )
    }
}