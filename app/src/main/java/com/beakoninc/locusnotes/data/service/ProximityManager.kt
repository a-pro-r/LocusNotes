package com.beakoninc.locusnotes.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.beakoninc.locusnotes.R
import com.beakoninc.locusnotes.data.location.ActivityRecognitionManager
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProximityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,
    private val noteRepository: NoteRepository,
    private val activityRecognitionManager: ActivityRecognitionManager
) {
    private val _nearbyNotes = MutableStateFlow<List<Note>>(emptyList())
    val nearbyNotes: StateFlow<List<Note>> = _nearbyNotes.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val TAG = "ProximityManager"
    private val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles

    private var lastNotificationTime = 0L
    private val NOTIFICATION_COOLDOWN = 60000L // 1 minute between notifications
    private val notifiedNoteIds = mutableSetOf<String>()

    init {
        // Start adaptive proximity checks
        startHybridProximityChecks()
    }

    // Replace the old periodic check method with this hybrid approach
    private fun startHybridProximityChecks() {
        serviceScope.launch {
            // Collect activity changes to trigger immediate checks
            launch {
                activityRecognitionManager.currentActivity.collect { activity ->
                    when (activity) {
                        DetectedActivity.WALKING,
                        DetectedActivity.RUNNING,
                        DetectedActivity.ON_FOOT,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.IN_VEHICLE -> {
                            Log.d(TAG, "Movement detected (${getActivityString(activity)}), checking for nearby notes")
                            checkNearbyNotes()
                        }
                    }
                }
            }

            // Also do periodic checks with adaptive interval
            launch {
                while (isActive) {
                    // Determine check interval based on activity
                    val currentActivity = activityRecognitionManager.currentActivity.value
                    val isMoving = when (currentActivity) {
                        DetectedActivity.WALKING,
                        DetectedActivity.RUNNING,
                        DetectedActivity.ON_FOOT,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.IN_VEHICLE -> true
                        else -> false
                    }

                    val checkInterval = if (isMoving) {
                        Log.d(TAG, "User is moving, checking every 1 minute")
                        60000L // 1 minute when moving
                    } else {
                        Log.d(TAG, "User is stationary, checking every 5 minutes")
                        300000L // 5 minutes when stationary
                    }

                    checkNearbyNotes()
                    delay(checkInterval)
                }
            }
        }
    }

    fun checkNearbyNotes() {
        serviceScope.launch {
            try {
                val userLocation = locationService.getCurrentLocation()
                if (userLocation == null) {
                    Log.e(TAG, "Cannot check proximity: Current location is null")
                    return@launch
                }

                Log.d(TAG, "Checking note proximity at: (${userLocation.latitude}, ${userLocation.longitude})")

                val allNotes = noteRepository.getAllNotesFlow().first()
                Log.d(TAG, "Total notes to check: ${allNotes.size}")

                val nearbyNotesList = mutableListOf<Note>()

                allNotes.forEach { note ->
                    if (note.latitude != null && note.longitude != null) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            userLocation.latitude, userLocation.longitude,
                            note.latitude!!, note.longitude!!,
                            results
                        )
                        val distance = results[0].toDouble()

                        Log.d(TAG, "Note '${note.title}' distance: ${distance/1609.34} miles")

                        if (distance <= NEARBY_THRESHOLD_METERS) {
                            Log.d(TAG, "Note '${note.title}' is nearby!")
                            nearbyNotesList.add(note)
                        }
                    } else {
                        Log.d(TAG, "Note '${note.title}' has no location data")
                    }
                }

                // Update nearby notes state
                _nearbyNotes.value = nearbyNotesList

                // Only show notification if we have notes AND
                // either enough time has passed OR we have new notes
                if (nearbyNotesList.isNotEmpty()) {
                    val newNotes = nearbyNotesList.filter { it.id !in notifiedNoteIds }
                    val shouldNotify = newNotes.isNotEmpty() ||
                            (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_COOLDOWN)

                    if (shouldNotify) {
                        notifyNearbyNotes(nearbyNotesList)
                        lastNotificationTime = System.currentTimeMillis()
                        // Remember which notes we've notified about
                        nearbyNotesList.forEach { notifiedNoteIds.add(it.id) }
                    }
                }

                Log.d(TAG, "Found ${nearbyNotesList.size} notes within ${NEARBY_THRESHOLD_METERS/1609.34} miles")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking note proximity", e)
            }
        }
    }

    private fun notifyNearbyNotes(notes: List<Note>) {
        try {
            val title = "You have ${notes.size} nearby notes"
            val content = notes.joinToString(", ") { it.title }

            Log.d(TAG, "Preparing notification: $title - $content")

            val notification = NotificationCompat.Builder(context, ProximityService.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            // Check for notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {

                    with(NotificationManagerCompat.from(context)) {
                        notify(NOTIFICATION_ID, notification)
                    }
                    Log.d(TAG, "Notification shown successfully")
                } else {
                    Log.d(TAG, "Cannot show notification: permission not granted")
                }
            } else {
                // For Android 12 and below, permission check not needed at runtime
                with(NotificationManagerCompat.from(context)) {
                    notify(NOTIFICATION_ID, notification)
                }
                Log.d(TAG, "Notification shown successfully (pre-Android 13)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
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

    fun onDestroy() {
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}