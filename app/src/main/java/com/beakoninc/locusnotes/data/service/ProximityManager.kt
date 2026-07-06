package com.beakoninc.locusnotes.data.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.beakoninc.locusnotes.MainActivity
import com.beakoninc.locusnotes.R
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates which notes are near a given location and posts the notification.
 * Continuous polling is gone — proximity is now driven by [GeofenceManager]
 * events in the background, plus a one-shot [checkNearbyNotes] when the app
 * opens so the UI's "Nearby" section stays fresh.
 */
@Singleton
class ProximityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,
    private val noteRepository: NoteRepository,
    private val notificationTracker: NotificationTracker
) {
    private val _nearbyNotes = MutableStateFlow<List<Note>>(emptyList())
    val nearbyNotes: StateFlow<List<Note>> = _nearbyNotes.asStateFlow()

    // Distance in meters per nearby note id, so the UI can show "0.4 mi away"
    private val _nearbyDistances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val nearbyDistances: StateFlow<Map<String, Double>> = _nearbyDistances.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val TAG = "ProximityManager"
    private val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles

    private var lastNotificationTime = 0L
    private val NOTIFICATION_COOLDOWN = 60000L // 1 minute between notifications

    /** One-shot check from the device's current location (app open, debug button). */
    fun checkNearbyNotes() {
        serviceScope.launch {
            val userLocation = locationService.getCurrentLocation()
            if (userLocation == null) {
                Log.e(TAG, "Cannot check proximity: Current location is null")
                return@launch
            }
            evaluateNearbyNotes(userLocation)
        }
    }

    /**
     * Called by [GeofenceBroadcastReceiver] on fence entry. Uses the location that
     * triggered the fence (free — no new GPS fix); a full nearby scan from there
     * produces one digest notification instead of one per fence.
     */
    suspend fun onGeofencesEntered(
        noteIds: List<String>,
        triggeringLocation: android.location.Location?
    ) {
        Log.d(TAG, "Geofence entry for ${noteIds.size} note(s)")
        val userLocation = triggeringLocation?.let {
            Location(name = "Current Location", latitude = it.latitude, longitude = it.longitude)
        } ?: locationService.getCurrentLocation()

        if (userLocation == null) {
            Log.e(TAG, "Geofence fired but no location available")
            return
        }
        evaluateNearbyNotes(userLocation)
    }

    private suspend fun evaluateNearbyNotes(userLocation: Location) {
        try {
            notificationTracker.checkAndResetCountsIfNeeded()

            Log.d(TAG, "Checking note proximity at: (${userLocation.latitude}, ${userLocation.longitude})")

            val allNotes = noteRepository.getAllNotesFlow().first()

            val distances = mutableMapOf<String, Double>()
            val nearbyNotesList = allNotes.filter { note ->
                val lat = note.latitude
                val lon = note.longitude
                if (lat == null || lon == null) return@filter false

                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    userLocation.latitude, userLocation.longitude, lat, lon, results
                )
                val nearby = results[0] <= NEARBY_THRESHOLD_METERS
                if (nearby) {
                    distances[note.id] = results[0].toDouble()
                    Log.d(TAG, "Note '${note.title}' is nearby! (${results[0] / 1609.34} miles)")
                }
                nearby
            }

            // Update state for the Nearby section and debug screen
            _nearbyNotes.value = nearbyNotesList
            _nearbyDistances.value = distances

            // Filter notes that have reached notification limit
            val notesUnderLimit = nearbyNotesList.filter { note ->
                val notificationCount = notificationTracker.getCount(note.id)
                val maxCount = notificationTracker.getMaxNotificationsPerNote()
                val underLimit = notificationCount < maxCount

                if (!underLimit) {
                    Log.d(TAG, "Note '${note.title}' has reached notification limit ($notificationCount/$maxCount)")
                }

                underLimit
            }

            if (notesUnderLimit.isNotEmpty()) {
                if (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_COOLDOWN) {
                    notifyNearbyNotes(notesUnderLimit)

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

    private fun notifyNearbyNotes(notes: List<Note>) {
        try {
            val title = "You have ${notes.size} nearby notes"
            val content = notes.joinToString(", ") { it.title }

            Log.d(TAG, "Preparing notification: $title - $content")

            // Tapping the notification opens the app; with a single note it deep-links
            // straight to that note's details (MainActivity reads EXTRA_NOTE_ID).
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (notes.size == 1) putExtra(MainActivity.EXTRA_NOTE_ID, notes.first().id)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
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

    companion object {
        const val CHANNEL_ID = "proximity_channel"
        private const val NOTIFICATION_ID = 1002
    }
}
