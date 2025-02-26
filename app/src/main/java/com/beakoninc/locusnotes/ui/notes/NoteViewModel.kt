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
import com.google.android.gms.location.DetectedActivity

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
    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    private val _nearbyNotes = MutableStateFlow<List<Note>>(emptyList())
    val nearbyNotes: StateFlow<List<Note>> = _nearbyNotes.asStateFlow()

    companion object {
        private const val NEARBY_THRESHOLD_METERS = 3218.69 // 2 miles
        private const val TAG = "NoteViewModel"
    }
    init {
        viewModelScope.launch {
            activityRecognitionManager.currentActivity.collect { activity ->
                Log.d(TAG, "Activity changed: ${getActivityString(activity)}")

                // Check proximity when user is in motion
                when (activity) {
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    DetectedActivity.ON_FOOT,
                    DetectedActivity.ON_BICYCLE,
                    DetectedActivity.IN_VEHICLE -> {
                        Log.d(TAG, "User is moving, checking nearby notes")
                        checkNoteProximity()
                    }
                }
            }
        }
    }
    fun checkNoteProximity() {
        viewModelScope.launch {
            try {
                val userLocation = locationService.getCurrentLocation()
                if (userLocation == null) {
                    Log.d(TAG, "Cannot check proximity: Current location is null")
                    return@launch
                }

                Log.d(TAG, "Checking note proximity at: (${userLocation.latitude}, ${userLocation.longitude})")

                val allNotes = notesFlow.value
                val nearbyNotesList = allNotes.filter { note ->
                    note.latitude != null && note.longitude != null &&
                            calculateDistance(
                                userLocation.latitude, userLocation.longitude,
                                note.latitude!!, note.longitude!!
                            ) <= NEARBY_THRESHOLD_METERS
                }

                _nearbyNotes.value = nearbyNotesList

                Log.d(TAG, "Found ${nearbyNotesList.size} notes within ${NEARBY_THRESHOLD_METERS / 1609.34} miles")
                nearbyNotesList.forEach { note ->
                    Log.d(TAG, "Nearby note: ${note.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking note proximity", e)
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371e3 // meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val latDiff = Math.toRadians(lat2 - lat1)
        val lonDiff = Math.toRadians(lon2 - lon1)

        val a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
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