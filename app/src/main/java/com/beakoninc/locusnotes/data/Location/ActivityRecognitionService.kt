package com.beakoninc.locusnotes.data.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class ActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ActivityRecognition"
        const val UPDATE_INTERVAL = 1000L  // 0 means as fast as possible
        const val ACTION_PROCESS_ACTIVITY = "com.beakoninc.locusnotes.PROCESS_ACTIVITY"
    }

    private val _currentActivity = MutableStateFlow<Int>(DetectedActivity.UNKNOWN)
    val currentActivity: StateFlow<Int> = _currentActivity

    fun startTracking() {
        Log.d(TAG, "Starting activity recognition tracking")

        // Add enhanced logging
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Activity recognition permission not granted!")
            return
        }

        val intent = Intent(context, ActivityRecognitionReceiver::class.java).apply {
            action = ACTION_PROCESS_ACTIVITY
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Use both continuous updates and transitions
        ActivityRecognition.getClient(context)
            .requestActivityUpdates(UPDATE_INTERVAL, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully registered for activity updates")
                // Also set an initial activity
                _currentActivity.value = DetectedActivity.STILL
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register for updates: ${e.message}")
            }

        // Also request transition updates for better detection
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully registered for activity transitions")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register for transitions: ${e.message}")
            }
    }

    // Add enhanced processActivityResult with better logging
    fun processActivityResult(result: ActivityRecognitionResult) {
        result.probableActivities.maxByOrNull { it.confidence }?.let { mostProbableActivity ->
            Log.d(TAG, """
                Activity Detected: ${getActivityString(mostProbableActivity.type)}
                Confidence: ${mostProbableActivity.confidence}%
                Previous activity: ${getActivityString(_currentActivity.value)}
            """.trimIndent())

            // Only update if confidence is reasonable
            if (mostProbableActivity.confidence > 50) {
                _currentActivity.value = mostProbableActivity.type
            }
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
    fun simulateActivity(activityType: Int) {
        Log.d(TAG, "Simulating activity: ${getActivityString(activityType)}")
        _currentActivity.value = activityType
    }
    // Add a polling fallback mechanism for activity updates
    fun startPollingFallback() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(30000) // Check every 30 seconds
                Log.d(TAG, "Activity polling fallback: current=${getActivityString(_currentActivity.value)}")

                // If still unknown after 30 seconds, default to STILL
                if (_currentActivity.value == DetectedActivity.UNKNOWN) {
                    Log.d(TAG, "Activity unknown for too long, defaulting to STILL")
                    _currentActivity.value = DetectedActivity.STILL
                }
            }
        }
    }
}