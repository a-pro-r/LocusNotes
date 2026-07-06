package com.beakoninc.locusnotes.ui.notes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.beakoninc.locusnotes.data.location.LocationService
import com.beakoninc.locusnotes.data.model.Location
import com.beakoninc.locusnotes.data.model.Note
import com.beakoninc.locusnotes.ui.components.LocationAutocomplete
import com.beakoninc.locusnotes.ui.components.SearchBar
import com.beakoninc.locusnotes.ui.components.TagInput
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteList(viewModel: NoteViewModel = hiltViewModel(),
             navController: NavController,
             noteIdToOpen: String? = null,
             onNoteOpened: () -> Unit = {}
) {
    val notes by viewModel.notesFlow.collectAsState()
    val nearbyNotes by viewModel.nearbyNotes.collectAsState()
    val nearbyDistances by viewModel.nearbyDistances.collectAsState()
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var editNoteId by remember { mutableStateOf<String?>(null) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Nearby reminders silently never fire without "Allow all the time" location;
    // re-check on resume so the banner disappears after the user fixes it in Settings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    var backgroundLocationMissing by remember { mutableStateOf(isBackgroundLocationMissing(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                backgroundLocationMissing = isBackgroundLocationMissing(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun deleteWithUndo(note: Note) {
        viewModel.deleteNote(note)
        if (selectedNoteId == note.id) selectedNoteId = null
        if (editNoteId == note.id) editNoteId = null
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreNote(note)
        }
    }

    // Deep link from a tapped proximity notification
    LaunchedEffect(noteIdToOpen) {
        if (noteIdToOpen != null) {
            selectedNoteId = noteIdToOpen
            onNoteOpened()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.searchNotes(it) },
                onSearchClear = { viewModel.clearSearch() }
            )

            if (backgroundLocationMissing && !bannerDismissed) {
                NearbyAlertsBanner(onDismiss = { bannerDismissed = true })
            }

            if (searchQuery.isNotEmpty()) {
                NoteSearchResults(searchResults, searchQuery, onNoteClick = { selectedNoteId = it.id })
            } else if (notes.isEmpty()) {
                EmptyNotesMessage()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp) // keep last card clear of the FAB
                ) {
                    if (nearbyNotes.isNotEmpty()) {
                        item { SectionHeader("Nearby") }
                        items(nearbyNotes, key = { "nearby-${it.id}" }) { note ->
                            SwipeActionsContainer(
                                modifier = Modifier.animateItemPlacement(),
                                onDelete = { deleteWithUndo(note) },
                                onEdit = { editNoteId = note.id }
                            ) {
                                NoteListItem(
                                    note = note,
                                    nearby = true,
                                    distanceMeters = nearbyDistances[note.id],
                                    onShowDetails = { selectedNoteId = it.id },
                                    onEdit = { editNoteId = note.id },
                                    onDelete = { deleteWithUndo(note) }
                                )
                            }
                        }
                        item { SectionHeader("All notes") }
                    }
                    items(notes, key = { it.id }) { note ->
                        SwipeActionsContainer(
                            modifier = Modifier.animateItemPlacement(),
                            onDelete = { deleteWithUndo(note) },
                            onEdit = { editNoteId = note.id }
                        ) {
                            NoteListItem(
                                note = note,
                                onShowDetails = { selectedNoteId = it.id },
                                onEdit = { editNoteId = note.id },
                                onDelete = { deleteWithUndo(note) }
                            )
                        }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp) // stay clear of the FAB
        )
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

    editNoteId?.let { id ->
        viewModel.getNote(id)?.let { note ->
            EditNoteDialog(
                note = note,
                onDismiss = { editNoteId = null },
                onNoteEdited = { title, content, tags, location ->
                    val updatedNote = note.copy(
                        title = title,
                        content = content,
                        tags = tags,
                        locationName = location?.name,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        address = location?.address,
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.updateNote(updatedNote)
                    editNoteId = null
                },
                locationService = viewModel.locationService
            )
        }
    }
}
private fun isBackgroundLocationMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

@Composable
private fun NearbyAlertsBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Location isn't set to \"Allow all the time\" — you won't get reminders near your notes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }) {
                Text("Enable")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionsContainer(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    onDelete()
                    true
                }
                // Edit shouldn't remove the card: fire the action but reject the
                // dismiss so the card springs back while the dialog opens
                DismissValue.DismissedToEnd -> {
                    onEdit()
                    false
                }
                else -> false
            }
        }
    )
    // A note deleted and then restored via Undo comes back with its saved swipe
    // state still "dismissed" — snap it back so the card is visible again
    LaunchedEffect(Unit) {
        if (dismissState.currentValue != DismissValue.Default) {
            dismissState.snapTo(DismissValue.Default)
        }
    }
    SwipeToDismiss(
        state = dismissState,
        modifier = modifier,
        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
        background = {
            val editing = dismissState.dismissDirection == DismissDirection.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (editing) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = if (editing) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (editing) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit note",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 28.dp)
                    )
                }
            }
        },
        dismissContent = { content() }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyNotesMessage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("No notes yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to add a note tied to a place",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NoteSearchResults(
    searchResults: List<Note>,
    searchQuery: String,
    onNoteClick: (Note) -> Unit
) {
    if (searchResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No notes match \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)) {
            items(searchResults, key = { it.id }) { note ->
                NoteItemWithHighlight(note, searchQuery, onNoteClick)
            }
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
    // Theme-aware highlight (yellow is unreadable in dark mode)
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
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
            withStyle(style = highlightStyle) {
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
    onDelete: () -> Unit,
    nearby: Boolean = false,
    distanceMeters: Double? = null
) {
    var showDropdown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        colors = if (nearby) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
                    // Cap visible tags so tag-heavy notes don't balloon the card
                    note.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) },
                            modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (note.tags.size > 3) {
                        AssistChip(
                            onClick = { },
                            label = { Text("+${note.tags.size - 3}") },
                            modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.locationName != null) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.locationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = listOfNotNull(
                        distanceMeters?.let { formatDistance(it) },
                        relativeTime(note.updatedAt)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

private fun formatDistance(meters: Double): String {
    val miles = meters / 1609.34
    return if (miles < 0.1) "right here" else String.format("%.1f mi away", miles)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailDialog(note: Note, onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                                TextButton(
                                    onClick = {
                                        val geoUri = Uri.parse(
                                            "geo:${note.latitude},${note.longitude}" +
                                                    "?q=${note.latitude},${note.longitude}(${Uri.encode(note.title)})"
                                        )
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                                        } catch (e: ActivityNotFoundException) {
                                            Log.w("NoteDetailDialog", "No maps app installed")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open in Maps")
                                }
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
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onNoteEdited: (String, String, List<String>, Location?) -> Unit,
    locationService: LocationService
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var tags by remember { mutableStateOf(note.tags) }

    // initial location state if note has location data
    var initialLocation by remember {
        mutableStateOf<Location?>(
            if (note.locationName != null && note.latitude != null && note.longitude != null) {
                Location(
                    name = note.locationName,
                    latitude = note.latitude,
                    longitude = note.longitude,
                    address = note.address
                )
            } else null
        )
    }
    var selectedLocation by remember { mutableStateOf(initialLocation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Note") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                TagInput(
                    tags = tags,
                    onTagsChanged = { tags = it }
                )

                LocationAutocomplete(
                    initialLocation = initialLocation,
                    onLocationSelected = { selectedLocation = it },
                    locationService = locationService
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onNoteEdited(title, content, tags, selectedLocation)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}