package com.beakoninc.locusnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.beakoninc.locusnotes.data.service.ProximityManager
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class LocusNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize OSMDroid
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        // The channel must exist before a geofence event posts a notification —
        // possibly with no activity ever having run in this process.
        createProximityNotificationChannel()

        // Note: geofence registration and activity recognition tracking are
        // started from MainActivity once runtime permissions are granted.
    }

    private fun createProximityNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ProximityManager.CHANNEL_ID,
                "LocusNotes Proximity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for nearby notes"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}