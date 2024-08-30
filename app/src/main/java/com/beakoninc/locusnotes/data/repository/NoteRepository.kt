package com.beakoninc.locusnotes.data.repository

import com.beakoninc.locusnotes.data.model.Note
import kotlinx.coroutines.flow.Flow

// Defines operations that can be performed on Note data
interface NoteRepository {
    // Retrieves all notes as a Flow for real-time updates
    fun getAllNotesFlow(): Flow<List<Note>>

    // Retrieves all notes
    suspend fun getAllNotes(): List<Note>

    // Retrieves a specific note by its ID
    suspend fun getNoteById(id: String): Note?

    // Inserts a new note and returns its ID
    suspend fun insertNote(note: Note): String

    // Updates an existing note
    suspend fun updateNote(note: Note)

    // Deletes a note
    suspend fun deleteNote(note: Note)

    // Searches notes based on a query string
    suspend fun searchNotes(query: String): List<Note>
}