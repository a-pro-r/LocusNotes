package com.beakoninc.locusnotes.data.service

/**
 * Interface for tracking notification counts per note.
 * This abstraction allows for easy switching between local storage and cloud-based
 * implementations in the future.
 */
interface NotificationTracker {
    /**
     * Get the number of times a note has been notified about today
     * @param noteId The unique identifier of the note
     * @return The notification count for today
     */
    fun getCount(noteId: String): Int

    /**
     * Increment the notification count for a note
     * @param noteId The unique identifier of the note
     * @return The new count after incrementing
     */
    fun incrementCount(noteId: String): Int

    /**
     * Reset all notification counts
     */
    fun resetAllCounts()

    /**
     * Check if counts need to be reset (e.g., new day) and reset if needed
     * @return true if counts were reset, false otherwise
     */
    fun checkAndResetCountsIfNeeded(): Boolean

    /**
     * Get the maximum allowed notifications per note per day
     */
    fun getMaxNotificationsPerNote(): Int
}