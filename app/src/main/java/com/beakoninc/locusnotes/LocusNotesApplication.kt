package com.beakoninc.locusnotes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class LocusNotesApplication : Application(){
    override fun onCreate() {
        super.onCreate()

        // Initialize OSMDroid
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
    }
}