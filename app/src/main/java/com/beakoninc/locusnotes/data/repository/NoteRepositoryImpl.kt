// New code: Create NoteRepositoryImpl.kt
package com.beakoninc.locusnotes.data.repository

import com.beakoninc.locusnotes.data.local.NoteDao
import com.beakoninc.locusnotes.data.model.Note
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(private val noteDao: NoteDao) : NoteRepository {
    override fun getAllNotesFlow(): Flow<List<Note>> = noteDao.getAllNotes()
    override suspend fun getNoteById(id: String): Note? = noteDao.getNoteById(id)
    override suspend fun insertNote(note: Note) = noteDao.insertNote(note)
    override suspend fun updateNote(note: Note) = noteDao.updateNote(note)
    override suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)
    override suspend fun searchNotes(query: String): List<Note> {
        return noteDao.searchNotes("%$query%")
    }
}