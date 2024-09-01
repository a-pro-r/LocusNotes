package com.beakoninc.locusnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.beakoninc.locusnotes.data.model.Note

@Database(entities = [Note::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}