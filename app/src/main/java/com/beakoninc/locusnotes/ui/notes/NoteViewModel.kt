package com.beakoninc.locusnotes.ui.notes

import androidx.lifecycle.viewModelScope
import com.beakoninc.locusnotes.BaseViewModel
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : BaseViewModel<NoteViewModel.State>(State()) {

    data class State(
        val notes: List<Note> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    val notesFlow: StateFlow<List<Note>> = noteRepository.getAllNotesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            val newNote = Note(title = title, content = content)
            noteRepository.insertNote(newNote)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }

    fun searchNotes(query: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            try {
                val searchResults = noteRepository.searchNotes(query)
                setState { copy(notes = searchResults, isLoading = false) }
            } catch (e: Exception) {
                setState { copy(error = e.message, isLoading = false) }
            }
        }
    }
}