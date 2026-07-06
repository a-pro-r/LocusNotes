package com.beakoninc.locusnotes.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONException

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        // New format: JSON array (handles tags containing commas safely)
        if (value.startsWith("[")) {
            try {
                val array = JSONArray(value)
                return (0 until array.length()).map { array.getString(it) }
            } catch (e: JSONException) {
                // Fall through to legacy parsing
            }
        }
        // Legacy format: comma-separated (rows written before the JSON migration)
        return value.split(",")
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        if (list.isEmpty()) return ""
        return JSONArray(list).toString()
    }
}
