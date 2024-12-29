package com.beakoninc.locusnotes.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    val locationService: LocationService
) : ViewModel() {

    val notesFlow: StateFlow<List<Note>> = noteRepository.getAllNotesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun selectNote(note: Note) {
        _selectedNote.value = note
    }

    fun getNote(id: String): Note? {
        return notesFlow.value.find { it.id == id }
    }


    // Update the addNote function in NoteViewModel
    fun addNote(
        title: String,
        content: String,
        tags: List<String>,
        location: Location? = null
    ) {
        viewModelScope.launch {
            val newNote = Note(
                title = title,
                content = content,
                tags = tags,
                locationName = location?.name,
                latitude = location?.latitude,
                longitude = location?.longitude,
                address = location?.address,
                createdAt = System.currentTimeMillis()
            )
            noteRepository.insertNote(newNote)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = note
            }
        }
    }

    fun clearSelectedNote() {
        _selectedNote.value = null
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
            if (_selectedNote.value?.id == note.id) {
                clearSelectedNote()
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<Note>>(emptyList())
    val searchResults: StateFlow<List<Note>> = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun searchNotes(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val results = noteRepository.searchNotes(query)
                _searchResults.value = results
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

}