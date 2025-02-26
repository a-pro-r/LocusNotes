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
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val notificationTracker: NotificationTracker  // Add this parameter
) {
    private val _nearbyNotes = MutableStateFlow<List<Note>>(emptyList())
    val nearbyNotes: StateFlow<List<Note>> = _nearbyNotes.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val TAG = "ProximityManager"
    private val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles

    private var lastNotificationTime = 0L
    private val NOTIFICATION_COOLDOWN = 60000L // 1 minute between notifications

    init {
        // Start proximity checks with activity recognition
        startHybridProximityChecks()
    }

    private fun startHybridProximityChecks() {
        serviceScope.launch {
            // Check daily reset
            launch {
                while (isActive) {
                    notificationTracker.checkAndResetCountsIfNeeded()
                    delay(3600000) // Check once per hour
                }
            }

            // Activity-based immediate checks
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

            // Adaptive periodic checks based on activity
            launch {
                while (isActive) {
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
                        Log.d(TAG, "User is stationary, checking every 20 minutes")
                        1200000L // 20 minutes when stationary
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
                // Check if counts need to be reset
                notificationTracker.checkAndResetCountsIfNeeded()

                val userLocation = locationService.getCurrentLocation()
                if (userLocation == null) {
                    Log.e(TAG, "Cannot check proximity: Current location is null")
                    return@launch
                }

                Log.d(TAG, "Checking note proximity at: (${userLocation.latitude}, ${userLocation.longitude})")

                val allNotes = noteRepository.getAllNotesFlow().first()

                // Find all nearby notes
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

                        if (distance <= NEARBY_THRESHOLD_METERS) {
                            Log.d(TAG, "Note '${note.title}' is nearby! (${distance/1609.34} miles)")
                            nearbyNotesList.add(note)
                        }
                    }
                }

                // Update state for debug screen and other UI
                _nearbyNotes.value = nearbyNotesList

                // Filter notes that have reached notification limit
                val notesUnderLimit = nearbyNotesList.filter { note ->
                    val notificationCount = notificationTracker.getCount(note.id)
                    val maxCount = notificationTracker.getMaxNotificationsPerNote()
                    val underLimit = notificationCount < maxCount

                    if (!underLimit) {
                        Log.d(TAG, "Note '${note.title}' has reached notification limit (${notificationCount}/${maxCount})")
                    }

                    underLimit
                }

                // Show notification if we have eligible notes
                if (notesUnderLimit.isNotEmpty()) {
                    // Only notify if enough time has passed since last notification
                    if (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_COOLDOWN) {
                        notifyNearbyNotes(notesUnderLimit)

                        // Increment counts for notified notes
                        notesUnderLimit.forEach { note ->
                            notificationTracker.incrementCount(note.id)
                        }

                        lastNotificationTime = System.currentTimeMillis()
                    } else {
                        Log.d(TAG, "Skipping notification due to cooldown period")
                    }
                } else if (nearbyNotesList.isNotEmpty()) {
                    Log.d(TAG, "Notes found but all have reached daily notification limit")
                } else {
                    Log.d(TAG, "No nearby notes found")
                }
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