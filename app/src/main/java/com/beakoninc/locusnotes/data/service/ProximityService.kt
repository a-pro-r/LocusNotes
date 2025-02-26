package com.beakoninc.locusnotes.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beakoninc.locusnotes.MainActivity
import com.beakoninc.locusnotes.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProximityService : Service() {
    @Inject
    lateinit var proximityManager: ProximityManager

    private val TAG = "ProximityService"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createServiceNotification())
        Log.d(TAG, "Proximity service created")

        // ProximityManager now handles all monitoring
    }

    private fun createServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocusNotes is running")
            .setContentText("Monitoring your location for nearby notes")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LocusNotes Proximity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for nearby notes"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        proximityManager.onDestroy()
        Log.d(TAG, "Proximity service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "proximity_channel"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, ProximityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}