package com.beakoninc.locusnotes.data.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ActivityRecognition"
        const val UPDATE_INTERVAL = 0L  // 0 means as fast as possible
        const val ACTION_PROCESS_ACTIVITY = "com.beakoninc.locusnotes.PROCESS_ACTIVITY"
    }

    private val _currentActivity = MutableStateFlow<Int>(DetectedActivity.UNKNOWN)
    val currentActivity: StateFlow<Int> = _currentActivity

    fun startTracking() {
        Log.d(TAG, "Starting activity recognition tracking")
        val intent = Intent(context, ActivityRecognitionReceiver::class.java).apply {
            action = ACTION_PROCESS_ACTIVITY
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        ActivityRecognition.getClient(context)
            .requestActivityUpdates(UPDATE_INTERVAL, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully registered for updates")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register for updates: ${e.message}")
            }
    }

    fun processActivityResult(result: ActivityRecognitionResult) {
        result.probableActivities.maxByOrNull { it.confidence }?.let { mostProbableActivity ->
            Log.d(TAG, """
                Activity Detected: ${getActivityString(mostProbableActivity.type)}
                Confidence: ${mostProbableActivity.confidence}%
            """.trimIndent())
            _currentActivity.value = mostProbableActivity.type
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
}