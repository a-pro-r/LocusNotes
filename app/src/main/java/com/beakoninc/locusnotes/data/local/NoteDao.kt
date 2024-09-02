package com.beakoninc.locusnotes.data.local

import androidx.room.*
import com.beakoninc.locusnotes.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)
    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(content) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchNotes(query: String): List<Note>
}