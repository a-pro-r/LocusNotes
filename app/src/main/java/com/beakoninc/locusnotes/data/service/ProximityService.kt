package com.beakoninc.locusnotes.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beakoninc.locusnotes.MainActivity
import com.beakoninc.locusnotes.R
import com.beakoninc.locusnotes.data.location.ActivityRecognitionManager
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class ProximityService : Service() {
    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    @Inject
    lateinit var locationService: LocationService

    @Inject
    lateinit var noteRepository: NoteRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val TAG = "ProximityService"
    private val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("LocusNotes is monitoring nearby notes"))
        Log.d(TAG, "Proximity service created")

        // Start monitoring for activity changes
        monitorUserActivity()
    }

    private fun monitorUserActivity() {
        serviceScope.launch {
            activityRecognitionManager.currentActivity.collect { activity ->
                Log.d(TAG, "Activity changed in service: ${getActivityString(activity)}")

                // Check for nearby notes when user is moving
                when (activity) {
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    DetectedActivity.ON_FOOT,
                    DetectedActivity.ON_BICYCLE,
                    DetectedActivity.IN_VEHICLE -> {
                        checkNearbyNotes()
                    }
                }
            }
        }
    }

    private fun checkNearbyNotes() {
        serviceScope.launch {
            try {
                val currentLocation = locationService.getCurrentLocation()
                if (currentLocation == null) {
                    Log.d(TAG, "Cannot check nearby notes: location is null")
                    return@launch
                }

                Log.d(TAG, "Checking for notes near: ${currentLocation.latitude}, ${currentLocation.longitude}")

                val allNotes = withContext(Dispatchers.IO) {
                    noteRepository.getAllNotesFlow().first()
                }

                val nearbyNotes = allNotes.filter { note ->
                    note.latitude != null && note.longitude != null &&
                            calculateDistance(
                                currentLocation.latitude, currentLocation.longitude,
                                note.latitude!!, note.longitude!!
                            ) <= NEARBY_THRESHOLD_METERS
                }

                if (nearbyNotes.isNotEmpty()) {
                    Log.d(TAG, "Found ${nearbyNotes.size} nearby notes")
                    notifyNearbyNotes(nearbyNotes)
                } else {
                    Log.d(TAG, "No nearby notes found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking nearby notes", e)
            }
        }
    }

    private fun notifyNearbyNotes(notes: List<Note>) {
        val notification = createNotification(
            "You have ${notes.size} notes nearby",
            notes.joinToString(", ") { it.title }
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NEARBY_NOTIFICATION_ID, notification)
    }

    private fun createNotification(title: String, content: String = "Monitoring your location for nearby notes"): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LocusNotes Proximity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for nearby notes"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371e3 // meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val latDiff = Math.toRadians(lat2 - lat1)
        val lonDiff = Math.toRadians(lon2 - lon1)

        val a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Proximity service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "proximity_channel"
        private const val NOTIFICATION_ID = 1001
        private const val NEARBY_NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, ProximityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}