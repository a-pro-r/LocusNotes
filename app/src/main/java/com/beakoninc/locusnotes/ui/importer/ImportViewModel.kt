package com.beakoninc.locusnotes.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.data.repository.NoteRepository
import com.beakoninc.locusnotes.data.takeout.MapsUrlResolver
import com.beakoninc.locusnotes.data.takeout.TakeoutParser
import com.beakoninc.locusnotes.data.takeout.TakeoutPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val locationService: LocationService,
    private val mapsUrlResolver: MapsUrlResolver
) : ViewModel() {

    data class ListSelection(
        val name: String,
        val places: List<TakeoutPlace>,
        val selected: Boolean = true
    )

    sealed interface UiState {
        object Idle : UiState
        data class Loaded(
            val lists: List<ListSelection>,
            /** Saved items that aren't places (movies, books, images) — not imported. */
            val nonPlacesSkipped: Int
        ) : UiState
        data class Importing(val done: Int, val total: Int, val currentTitle: String) : UiState
        data class Finished(
            val imported: Int,
            val withLocation: Int,
            val duplicatesSkipped: Int,
            /** Places that couldn't be located; not imported until retried or forced. */
            val unresolved: List<TakeoutPlace>
        ) : UiState

        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state = _state.asStateFlow()

    fun reset() {
        _state.value = UiState.Idle
    }

    fun toggleList(name: String) {
        val loaded = _state.value as? UiState.Loaded ?: return
        _state.value = loaded.copy(lists = loaded.lists.map {
            if (it.name == name) it.copy(selected = !it.selected) else it
        })
    }

    fun loadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lists = mutableMapOf<String, MutableList<TakeoutPlace>>()
                var nonPlaces = 0
                uris.forEach { uri -> nonPlaces += loadUri(uri, lists) }
                if (lists.isEmpty()) {
                    _state.value = UiState.Error(
                        if (nonPlaces > 0)
                            "Those files contain $nonPlaces saved items, but none are places " +
                                    "(movies, books, and images can't be imported)."
                        else
                            "No saved places found. Pick the Takeout zip, or the CSV files inside Takeout/Saved."
                    )
                } else {
                    _state.value = UiState.Loaded(
                        lists = lists.map { ListSelection(it.key, it.value) }
                            .sortedBy { it.name.lowercase() },
                        nonPlacesSkipped = nonPlaces
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read Takeout files", e)
                _state.value = UiState.Error("Couldn't read those files: ${e.message}")
            }
        }
    }

    /** Adds place rows to [lists]; returns how many non-place saves were skipped. */
    private fun loadUri(uri: Uri, lists: MutableMap<String, MutableList<TakeoutPlace>>): Int {
        var nonPlaces = 0

        fun addList(listName: String, text: String) {
            val (places, others) = TakeoutParser.parseCsv(listName, text).partition { it.isPlace }
            nonPlaces += others.size
            if (places.isNotEmpty()) lists.getOrPut(listName) { mutableListOf() } += places
        }

        val name = displayName(uri) ?: "Imported"
        if (name.endsWith(".zip", ignoreCase = true)) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.substringAfterLast('/')
                        if (!entry.isDirectory && entryName.endsWith(".csv", ignoreCase = true)) {
                            addList(entryName.removeSuffix(".csv"), zip.readBytes().decodeToString())
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } else {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() } ?: return 0
            addList(name.removeSuffix(".csv"), text)
        }
        return nonPlaces
    }

    fun startImport() {
        val loaded = _state.value as? UiState.Loaded ?: return
        val places = loaded.lists.filter { it.selected }.flatMap { it.places }
        if (places.isEmpty()) return
        resolveAndImport(places, priorImported = 0, priorLocated = 0, priorDuplicates = 0)
    }

    /** Second pass over places the first run couldn't locate (network hiccups). */
    fun retryUnresolved() {
        val finished = _state.value as? UiState.Finished ?: return
        if (finished.unresolved.isEmpty()) return
        resolveAndImport(
            finished.unresolved,
            priorImported = finished.imported,
            priorLocated = finished.withLocation,
            priorDuplicates = finished.duplicatesSkipped
        )
    }

    /** Imports the leftovers as location-less notes (still tagged and searchable). */
    fun importUnresolvedWithoutLocation() {
        val finished = _state.value as? UiState.Finished ?: return
        if (finished.unresolved.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            finished.unresolved.forEach { place -> insertNote(place, lat = null, lon = null, address = null) }
            _state.value = finished.copy(
                imported = finished.imported + finished.unresolved.size,
                unresolved = emptyList()
            )
        }
    }

    private fun resolveAndImport(
        places: List<TakeoutPlace>,
        priorImported: Int,
        priorLocated: Int,
        priorDuplicates: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = noteRepository.getAllNotesFlow().first()
            // Re-importing the same Takeout twice shouldn't duplicate notes:
            // a note counts as already imported if its title + list tag match
            val existingKeys = existing
                .flatMap { note -> note.tags.map { tag -> dupKey(note.title, tag) } }
                .toHashSet()
            val geocodeCache = mutableMapOf<String, Location?>()
            val unresolved = mutableListOf<TakeoutPlace>()
            val unresolvedKeys = HashSet<String>()

            var imported = priorImported
            var withLocation = priorLocated
            var duplicates = priorDuplicates

            places.forEachIndexed { index, place ->
                _state.value = UiState.Importing(index, places.size, place.title)

                val key = dupKey(place.title, place.listName)
                if (key in existingKeys) {
                    duplicates++
                    return@forEachIndexed
                }

                // Exact first: coordinates from the URL, else the pin Google saved
                var lat = place.latitude
                var lon = place.longitude
                var address: String? = null
                if (lat == null || lon == null) {
                    delay(RESOLVE_DELAY_MS)
                    mapsUrlResolver.resolve(place.url)?.let { (la, lo) ->
                        lat = la
                        lon = lo
                    }
                }
                // Last resort: geocode by name, hinted with the list's region
                if (lat == null || lon == null) {
                    geocodeFallback(place, geocodeCache)?.let { match ->
                        lat = match.latitude
                        lon = match.longitude
                        address = match.address
                    }
                }

                if (lat == null || lon == null) {
                    // Don't import silently unlocated — offer retry/force instead
                    if (unresolvedKeys.add(key)) unresolved += place
                    return@forEachIndexed
                }

                existingKeys += key
                insertNote(place, lat, lon, address)
                imported++
                withLocation++
            }

            _state.value = UiState.Finished(imported, withLocation, duplicates, unresolved)
        }
    }

    private suspend fun geocodeFallback(
        place: TakeoutPlace,
        cache: MutableMap<String, Location?>
    ): Location? {
        val hint = regionHint(place.listName)
        val queries = listOfNotNull(hint?.let { "${place.title}, $it" }, place.title)
        for (query in queries) {
            val match = cache.getOrPut(query.lowercase()) {
                delay(GEOCODE_DELAY_MS) // stay polite to Photon's free API
                runCatching {
                    locationService.searchLocations(query).first().firstOrNull()
                }.getOrNull()
            }
            if (match?.latitude != null) return match
        }
        return null
    }

    /**
     * List names like "Arizona 😍" or "New York" disambiguate geocoding
     * ("Badwater, California"); Google's generic list names don't.
     */
    private fun regionHint(listName: String): String? {
        val cleaned = listName
            .replace(Regex("""\(\d+\)$"""), "")
            .replace(Regex("""[^\p{L}\p{N}\s'-]"""), "")
            .trim()
        return cleaned.takeIf { it.isNotEmpty() && cleaned.lowercase() !in GENERIC_LIST_NAMES }
    }

    private suspend fun insertNote(place: TakeoutPlace, lat: Double?, lon: Double?, address: String?) {
        val located = lat != null && lon != null
        noteRepository.insertNote(
            Note(
                title = place.title,
                content = listOf(place.note, place.comment)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n"),
                tags = (listOf(place.listName) + place.tags).distinct(),
                locationName = if (located) place.title else null,
                latitude = lat,
                longitude = lon,
                address = address
            )
        )
    }

    private fun dupKey(title: String, tag: String) = "${title.trim().lowercase()}|${tag.trim().lowercase()}"

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        private const val TAG = "ImportViewModel"
        private const val RESOLVE_DELAY_MS = 800L
        private const val GEOCODE_DELAY_MS = 400L
        private val GENERIC_LIST_NAMES = setOf(
            "default list", "want to go", "favorite places", "favorites",
            "starred places", "labeled places", "saved places", "travel plans",
            "saved for later"
        )
    }
}
