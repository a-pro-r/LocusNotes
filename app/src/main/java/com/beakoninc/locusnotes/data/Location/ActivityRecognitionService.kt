package com.beakoninc.locusnotes.data.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _currentActivity = MutableStateFlow<String>("UNKNOWN")
    val currentActivity: StateFlow<String> = _currentActivity

    companion object {
        private const val TAG = "ActivityRecognition"
        const val ACTIVITY_TRANSITION_ACTION = "com.beakoninc.locusnotes.ACTIVITY_TRANSITION_ACTION"
    }

    fun startActivityRecognition() {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent(ACTIVITY_TRANSITION_ACTION)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Activity recognition registered")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error registering activity recognition: ${e.message}")
            }
    }

    fun handleActivityResult(result: ActivityRecognitionResult) {
        val activity = result.mostProbableActivity
        val activityStr = when (activity.type) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "Detected activity: $activityStr (${activity.confidence}%)")
        _currentActivity.value = activityStr
    }
}