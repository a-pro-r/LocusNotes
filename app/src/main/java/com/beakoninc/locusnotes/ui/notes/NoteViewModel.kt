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
import android.util.Log
import com.beakoninc.locusnotes.data.location.ActivityRecognitionManager
import com.beakoninc.locusnotes.data.service.ProximityManager
import com.google.android.gms.location.DetectedActivity

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    val locationService: LocationService,
    val activityRecognitionManager: ActivityRecognitionManager,
    val proximityManager: ProximityManager
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


    private val _nearbyNotes = MutableStateFlow<List<Note>>(emptyList())
    val nearbyNotes: StateFlow<List<Note>> = proximityManager.nearbyNotes

    companion object {
        private const val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles
        private const val TAG = "NoteViewModel"
    }

    fun checkNoteProximity() {
        proximityManager.checkNearbyNotes()
    }

    // Fix and enhance distance calculation
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Direct calculation using Android's built-in distance formula for more accuracy
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
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
    private fun getActivityString(type: Int): String = when (type) {
        DetectedActivity.STILL -> "Still"
        DetectedActivity.WALKING -> "Walking"
        DetectedActivity.RUNNING -> "Running"
        DetectedActivity.IN_VEHICLE -> "In Vehicle"
        DetectedActivity.ON_BICYCLE -> "On Bicycle"
        DetectedActivity.ON_FOOT -> "On Foot"
        DetectedActivity.TILTING -> "Tilting"
        DetectedActivity.UNKNOWN -> "Unknown"
        else -> "Unknown"
    }

}