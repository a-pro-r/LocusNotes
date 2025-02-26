package com.beakoninc.locusnotes.data.service

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beakoninc.locusnotes.R
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

@Singleton
class ProximityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,
    private val noteRepository: NoteRepository
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
        // Start polling for nearby notes
        startPeriodicProximityChecks()
    }

    private fun startPeriodicProximityChecks() {
        serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "Running periodic proximity check")
                checkNearbyNotes()
                delay(30000) // Check every 30 seconds
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

    fun onDestroy() {
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}