package com.beakoninc.locusnotes.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Woken by the OS when the user enters a note's geofence — including when the
 * app process is dead, which the old foreground-service polling never survived.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var proximityManager: ProximityManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GeofenceManager.ACTION_GEOFENCE_EVENT) return

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            Log.e(TAG, "Geofencing error: ${event?.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val noteIds = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (noteIds.isEmpty()) return
        Log.d(TAG, "Entered geofences for notes: $noteIds")

        // goAsync keeps the process alive until the suspend work finishes
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                proximityManager.onGeofencesEntered(noteIds, event.triggeringLocation)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling geofence event", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
