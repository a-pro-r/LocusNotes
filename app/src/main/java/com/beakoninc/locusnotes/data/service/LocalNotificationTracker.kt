package com.beakoninc.locusnotes.data.service

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of NotificationTracker.
 * Tracks notification counts using a HashMap and resets daily.
 */
@Singleton
class LocalNotificationTracker @Inject constructor() : NotificationTracker {
    private val noteNotificationCounts = mutableMapOf<String, Int>()
    private var lastCountResetTime = System.currentTimeMillis()
    private val COUNT_RESET_INTERVAL = 86400000L // 24 hours
    private val MAX_NOTIFICATIONS_PER_NOTE = 5
    private val TAG = "NotificationTracker"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getCount(noteId: String): Int {
        return noteNotificationCounts.getOrDefault(noteId, 0)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun incrementCount(noteId: String): Int {
        val current = noteNotificationCounts.getOrDefault(noteId, 0)
        val newCount = current + 1
        noteNotificationCounts[noteId] = newCount
        Log.d(TAG, "Incremented count for note $noteId to $newCount")
        return newCount
    }

    override fun resetAllCounts() {
        noteNotificationCounts.clear()
        lastCountResetTime = System.currentTimeMillis()
        Log.d(TAG, "Reset all notification counts")
    }

    override fun checkAndResetCountsIfNeeded(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCountResetTime >= COUNT_RESET_INTERVAL) {
            resetAllCounts()
            return true
        }
        return false
    }

    override fun getMaxNotificationsPerNote(): Int {
        return MAX_NOTIFICATIONS_PER_NOTE
    }
}