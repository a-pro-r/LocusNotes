package com.beakoninc.locusnotes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.beakoninc.locusnotes.data.location.ActivityRecognitionManager
import com.beakoninc.locusnotes.data.service.ProximityService
import com.beakoninc.locusnotes.ui.theme.LocusNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Build
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d(TAG, "Permission results: $results")
        startLocationFeaturesIfPermitted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        setContent {
            LocusNotesTheme {
                MainScreen()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        ) {
            needed += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            startLocationFeaturesIfPermitted()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startLocationFeaturesIfPermitted() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.w(TAG, "Location permission not granted; proximity features disabled")
            return
        }
        // Activity recognition tracking checks its own permission internally
        activityRecognitionManager.startTracking()
        ProximityService.startService(this)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "MainActivity"
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
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Build, contentDescription = "Debug") },
            label = { Text("Debug") },
            selected = navController.currentDestination?.route == "debug",
            onClick = {
                navController.navigate("debug")
                onItemClick()
            }
        )
    }
}