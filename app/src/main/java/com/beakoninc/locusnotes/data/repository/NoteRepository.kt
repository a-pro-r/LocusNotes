// Modified code: Update NoteRepository.kt
package com.beakoninc.locusnotes.data.repository

import com.beakoninc.locusnotes.data.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotesFlow(): Flow<List<Note>>
    suspend fun getNoteById(id: String): Note?
    suspend fun insertNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun searchNotes(query: String): List<Note>
}