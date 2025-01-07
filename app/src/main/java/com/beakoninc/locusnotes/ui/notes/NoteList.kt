package com.beakoninc.locusnotes.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beakoninc.locusnotes.data.model.Location
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.ui.components.LocationAutocomplete
import com.beakoninc.locusnotes.ui.components.SearchBar
import com.beakoninc.locusnotes.ui.components.TagInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.beakoninc.locusnotes.data.location.LocationService


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteList(viewModel: NoteViewModel = hiltViewModel(),
             navController: NavController
) {
    val notes by viewModel.notesFlow.collectAsState()
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }


    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.searchNotes(it) },
                onSearchClear = { viewModel.clearSearch() }
            )

            Text(
                "Your Notes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            if (searchQuery.isNotEmpty()) {
                NoteSearchResults(searchResults, searchQuery, onNoteClick = { selectedNoteId = it.id })
            } else if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet. Add your first note!")
                }
            } else {
                LazyColumn {
                    items(notes) { note ->
                        NoteListItem(
                            note = note,
                            onShowDetails = { selectedNoteId = it.id },
                            onEdit = {
                                selectedNoteId = note.id
                                showEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteNote(note)
                                if (selectedNoteId == note.id) {
                                    selectedNoteId = null
                                }
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddNoteDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add note")
        }
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onNoteAdded = { title, content, tags, location ->
                viewModel.addNote(
                    title = title,
                    content = content,
                    tags = tags,
                    location = location
                )
                showAddNoteDialog = false
            },
            locationService = viewModel.locationService
        )
    }

    selectedNoteId?.let { id ->
        viewModel.getNote(id)?.let { note ->
            NoteDetailDialog(
                note = note,
                onDismiss = { selectedNoteId = null }
            )
        }
    }

    if (showEditDialog) {
        // Modified: Use selectedNoteId to get the most up-to-date note for editing
        selectedNoteId?.let { id ->
            viewModel.getNote(id)?.let { note ->
                EditNoteDialog(
                    note = note,
                    onDismiss = {
                        showEditDialog = false
                    },
                    onNoteEdited = { title, content ->
                        val updatedNote = note.copy(title = title, content = content)
                        viewModel.updateNote(updatedNote)
                        showEditDialog = false
                    }
                )
            }
        }
    }
}
@Composable
fun NoteSearchResults(
    searchResults: List<Note>,
    searchQuery: String,
    onNoteClick: (Note) -> Unit
) {
    LazyColumn {
        items(searchResults) { note ->
            NoteItemWithHighlight(note, searchQuery, onNoteClick)
        }
    }
}
@Composable
fun NoteItemWithHighlight(
    note: Note,
    searchQuery: String,
    onClick: (Note) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick(note) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = highlightText(note.title, searchQuery),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = highlightText(note.content, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun highlightText(text: String, query: String): AnnotatedString {
    return buildAnnotatedString {
        val lowercaseText = text.lowercase()
        val lowercaseQuery = query.lowercase()
        var startIndex = 0
        while (true) {
            val index = lowercaseText.indexOf(lowercaseQuery, startIndex)
            if (index == -1) {
                append(text.substring(startIndex))
                break
            }
            append(text.substring(startIndex, index))
            withStyle(style = SpanStyle(background = Color.Yellow)) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun NoteListItem(
    note: Note,
    onShowDetails: (Note) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onShowDetails(note) },
                    onLongPress = {
                        showDropdown = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                onEdit()
                                showDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                onDelete()
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            if (note.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    maxItemsInEachRow = Int.MAX_VALUE
                ) {
                    note.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) },
                            modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailDialog(note: Note, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Content
                Text(
                    note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags Section
                if (note.tags.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        maxItemsInEachRow = Int.MAX_VALUE
                    ) {
                        note.tags.forEach { tag ->
                            AssistChip(
                                onClick = { },
                                label = { Text(tag) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }

                // Location Section
                if (note.locationName != null) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Location",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                note.locationName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            note.address?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (note.latitude != null && note.longitude != null) {
                                Text(
                                    "Coordinates: ${String.format("%.6f", note.latitude)}, ${String.format("%.6f", note.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun NoteItem(note: Note) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(note.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AddNoteDialog(
    onDismiss: () -> Unit,
    onNoteAdded: (String, String, List<String>, Location?) -> Unit,
    locationService: LocationService
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var location by remember { mutableStateOf<Location?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a new note") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.height(150.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TagInput(
                    tags = tags,
                    onTagsChanged = { tags = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LocationAutocomplete(
                    initialLocation = location,
                    onLocationSelected = { location = it },
                    locationService = locationService
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty() || content.isNotEmpty()) {
                        onNoteAdded(title, content, tags, location)
                    }
                    onDismiss()
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onNoteEdited: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Note") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.height(200.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onNoteEdited(title, content) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}