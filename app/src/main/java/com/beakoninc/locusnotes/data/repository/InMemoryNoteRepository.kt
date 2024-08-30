package com.beakoninc.locusnotes.data.repository

import com.beakoninc.locusnotes.data.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryNoteRepository @Inject constructor() : NoteRepository {
    private val notes = MutableStateFlow<List<Note>>(emptyList())

    override fun getAllNotesFlow(): Flow<List<Note>> = notes

    override suspend fun getAllNotes(): List<Note> = notes.value

    override suspend fun getNoteById(id: String): Note? = notes.value.find { it.id == id }

    override suspend fun insertNote(note: Note): String {
        notes.value += note
        return note.id
    }

    override suspend fun updateNote(note: Note) {
        notes.value = notes.value.map { if (it.id == note.id) note else it }
    }

    override suspend fun deleteNote(note: Note) {
        notes.value = notes.value.filter { it.id != note.id }
    }


    override suspend fun searchNotes(query: String): List<Note> {
        return notes.value.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
        }
    }
}