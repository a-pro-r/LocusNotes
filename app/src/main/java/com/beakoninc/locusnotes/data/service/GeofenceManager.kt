package com.beakoninc.locusnotes.data.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Keeps one OS geofence registered per located note (up to the platform limit
 * of 100) so the system wakes [GeofenceBroadcastReceiver] when the user enters
 * a note's area. Replaces the old always-on polling foreground service: the OS
 * evaluates fences with shared, low-power location, and events are delivered
 * even when the app process is dead.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val locationService: LocationService
) {
    private val geofencingClient = LocationServices.getGeofencingClient(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var observeJob: Job? = null

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        // The system attaches the triggering fences as extras, so on Android 12+
        // the PendingIntent must be mutable.
        val mutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )
    }

    /**
     * Re-registers the geofence set whenever a located note is added, moved or
     * deleted. Safe to call multiple times.
     */
    @Synchronized
    fun startMonitoring() {
        if (observeJob?.isActive == true) return
        Log.d(TAG, "Starting geofence monitoring")
        observeJob = scope.launch {
            noteRepository.getAllNotesFlow()
                .map { notes -> notes.filter { it.latitude != null && it.longitude != null } }
                .distinctUntilChangedBy { notes ->
                    notes.map { Triple(it.id, it.latitude, it.longitude) }
                }
                .collectLatest { registerGeofences(it) }
        }
    }

    /** Fire-and-forget refresh, e.g. right after a permission grant. */
    fun refreshNow() {
        scope.launch { refreshGeofences() }
    }

    /** One-shot re-registration; used after reboot when the OS has cleared all fences. */
    suspend fun refreshGeofences() {
        val notes = noteRepository.getAllNotesFlow().first()
            .filter { it.latitude != null && it.longitude != null }
        registerGeofences(notes)
    }

    private suspend fun registerGeofences(notes: List<Note>) {
        if (!hasGeofencePermissions()) {
            Log.w(TAG, "Missing background location permission; geofences not registered")
            return
        }

        if (notes.isEmpty()) {
            geofencingClient.removeGeofences(geofencePendingIntent).awaitCompletion()
            Log.d(TAG, "No located notes; geofences cleared")
            return
        }

        // The OS allows 100 fences per app — keep the ones nearest to the user
        val selected = if (notes.size > MAX_GEOFENCES) {
            val here = locationService.getCurrentLocation()
            if (here != null) {
                notes.sortedBy { distanceMeters(here.latitude, here.longitude, it.latitude!!, it.longitude!!) }
                    .take(MAX_GEOFENCES)
            } else {
                notes.take(MAX_GEOFENCES)
            }
        } else {
            notes
        }

        val fences = selected.map { note ->
            Geofence.Builder()
                .setRequestId(note.id)
                .setCircularRegion(note.latitude!!, note.longitude!!, RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        val request = GeofencingRequest.Builder()
            // Fire right away for fences the user is already standing inside
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()

        try {
            geofencingClient.removeGeofences(geofencePendingIntent).awaitCompletion()
            geofencingClient.addGeofences(request, geofencePendingIntent).awaitCompletion()
            Log.d(TAG, "Registered ${fences.size} geofences")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission revoked while registering geofences", e)
        }
    }

    private fun hasGeofencePermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Geofencing on Android 10+ only delivers events with background location
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    private suspend fun <T> Task<T>.awaitCompletion(): T? =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    Log.w(TAG, "Geofencing task failed", task.exception)
                    continuation.resume(null)
                }
            }
        }

    companion object {
        private const val TAG = "GeofenceManager"
        const val ACTION_GEOFENCE_EVENT = "com.beakoninc.locusnotes.GEOFENCE_EVENT"
        private const val MAX_GEOFENCES = 100
        const val RADIUS_METERS = 3218.69f // 2 miles, same threshold the polling used
    }
}
