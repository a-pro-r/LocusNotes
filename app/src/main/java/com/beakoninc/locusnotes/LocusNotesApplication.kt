package com.beakoninc.locusnotes

import android.app.Application
import com.beakoninc.locusnotes.data.location.ActivityRecognitionManager
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import javax.inject.Inject
import com.beakoninc.locusnotes.data.service.ProximityService

@HiltAndroidApp
class LocusNotesApplication : Application() {
    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    override fun onCreate() {
        super.onCreate()

        // Initialize OSMDroid
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        // Start activity recognition
        activityRecognitionManager.startTracking()

        // Start proximity service
        ProximityService.startService(this)
    }
}