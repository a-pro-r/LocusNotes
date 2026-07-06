package com.beakoninc.locusnotes.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local implementation of NotificationTracker.
 * Tracks notification counts in a thread-safe map, persisted to SharedPreferences
 * so counts and the daily reset timestamp survive process death. Resets daily.
 */
@Singleton
class LocalNotificationTracker @Inject constructor(
    @ApplicationContext context: Context
) : NotificationTracker {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val noteNotificationCounts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var lastCountResetTime: Long

    init {
        lastCountResetTime = prefs.getLong(KEY_LAST_RESET, System.currentTimeMillis())
        // Restore persisted counts (all keys except the reset timestamp are counts)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_COUNT_PREFIX) && value is Int) {
                noteNotificationCounts[key.removePrefix(KEY_COUNT_PREFIX)] = value
            }
        }
        if (!prefs.contains(KEY_LAST_RESET)) {
            prefs.edit().putLong(KEY_LAST_RESET, lastCountResetTime).apply()
        }
        Log.d(TAG, "Restored ${noteNotificationCounts.size} notification counts")
    }

    override fun getCount(noteId: String): Int {
        return noteNotificationCounts[noteId] ?: 0
    }

    override fun incrementCount(noteId: String): Int {
        // merge is atomic on ConcurrentHashMap, so concurrent increments are not lost
        val newCount = noteNotificationCounts.merge(noteId, 1) { old, _ -> old + 1 }!!
        prefs.edit().putInt(KEY_COUNT_PREFIX + noteId, newCount).apply()
        Log.d(TAG, "Incremented count for note $noteId to $newCount")
        return newCount
    }

    @Synchronized
    override fun resetAllCounts() {
        noteNotificationCounts.clear()
        lastCountResetTime = System.currentTimeMillis()
        prefs.edit().clear().putLong(KEY_LAST_RESET, lastCountResetTime).apply()
        Log.d(TAG, "Reset all notification counts")
    }

    @Synchronized
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

    companion object {
        private const val TAG = "NotificationTracker"
        private const val PREFS_NAME = "notification_tracker"
        private const val KEY_LAST_RESET = "last_count_reset_time"
        private const val KEY_COUNT_PREFIX = "count_"
        private const val COUNT_RESET_INTERVAL = 86400000L // 24 hours
        private const val MAX_NOTIFICATIONS_PER_NOTE = 5
    }
}
