package com.beakoninc.locusnotes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class LocusNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize OSMDroid
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        // Note: activity recognition tracking and the proximity foreground service
        // are started from MainActivity once runtime permissions are granted.
        // Starting a location-type foreground service here (before the user grants
        // location permission) crashes with SecurityException on Android 14+.
    }
}