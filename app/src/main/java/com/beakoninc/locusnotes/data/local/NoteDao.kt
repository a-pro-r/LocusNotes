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
    @Query("SELECT * FROM notes WHERE title LIKE :query OR content LIKE :query")
    suspend fun searchNotes(query: String): List<Note>
}