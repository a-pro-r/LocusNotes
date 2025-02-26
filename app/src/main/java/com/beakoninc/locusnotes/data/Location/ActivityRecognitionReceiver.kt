package com.beakoninc.locusnotes.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActivityRecognitionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var activityManager: ActivityRecognitionManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ActivityRecognitionManager.ACTION_PROCESS_ACTIVITY) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent)
                result?.let {
                    activityManager.processActivityResult(it)
                }
            }
        }
    }
}