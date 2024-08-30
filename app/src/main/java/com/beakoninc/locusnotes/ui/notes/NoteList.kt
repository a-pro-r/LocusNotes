package com.beakoninc.locusnotes.ui.notes

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.beakoninc.locusnotes.data.model.Note

@Composable
fun NoteList(viewModel: NoteViewModel = hiltViewModel()) {
    val notes by viewModel.notesFlow.collectAsState()

    LazyColumn {
        items(notes) { note ->
            NoteItem(note)
        }
    }
}

@Composable
fun NoteItem(note: Note) {
    // Simple representation of a note, can be expanded later
    Text(text = note.title)
}