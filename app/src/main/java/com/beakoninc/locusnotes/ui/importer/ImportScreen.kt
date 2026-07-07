package com.beakoninc.locusnotes.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun ImportScreen(
    navController: NavController,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.loadFiles(uris) }

    when (val s = state) {
        is ImportViewModel.UiState.Idle -> IdleContent(
            onPickFiles = { filePicker.launch(arrayOf("*/*")) }
        )

        is ImportViewModel.UiState.Loaded -> LoadedContent(
            lists = s.lists,
            nonPlacesSkipped = s.nonPlacesSkipped,
            onToggle = viewModel::toggleList,
            onImport = viewModel::startImport,
            onStartOver = viewModel::reset
        )

        is ImportViewModel.UiState.Importing -> ImportingContent(s)

        is ImportViewModel.UiState.Finished -> FinishedContent(
            result = s,
            onViewNotes = {
                navController.navigate("notes") { popUpTo("notes") { inclusive = true } }
            },
            onImportMore = viewModel::reset,
            onRetryUnresolved = viewModel::retryUnresolved,
            onImportWithoutLocation = viewModel::importUnresolvedWithoutLocation
        )

        is ImportViewModel.UiState.Error -> ErrorContent(
            message = s.message,
            onRetry = viewModel::reset
        )
    }
}

@Composable
private fun IdleContent(onPickFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Import from Google Maps", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Export your saved places from Google Takeout (takeout.google.com → Saved), " +
                    "then pick the zip or the CSV files here. Each list becomes a tag.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFiles) {
            Text("Choose Takeout files")
        }
    }
}

@Composable
private fun LoadedContent(
    lists: List<ImportViewModel.ListSelection>,
    nonPlacesSkipped: Int,
    onToggle: (String) -> Unit,
    onImport: () -> Unit,
    onStartOver: () -> Unit
) {
    val selectedPlaces = lists.filter { it.selected }.sumOf { it.places.size }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Choose lists to import",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
        )
        Text(
            "Places without coordinates are looked up online — large imports can take a few minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (nonPlacesSkipped > 0) {
            Text(
                "$nonPlacesSkipped saved items aren't places (movies, books, images) and were set aside.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(lists, key = { it.name }) { list ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Checkbox(
                        checked = list.selected,
                        onCheckedChange = { onToggle(list.name) }
                    )
                    Text(
                        list.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${list.places.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onStartOver, modifier = Modifier.weight(1f)) {
                Text("Start over")
            }
            Button(
                onClick = onImport,
                enabled = selectedPlaces > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Import $selectedPlaces places")
            }
        }
    }
}

@Composable
private fun ImportingContent(state: ImportViewModel.UiState.Importing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            progress = if (state.total == 0) 0f else state.done.toFloat() / state.total,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Importing ${state.done + 1} of ${state.total}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.currentTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Keep the app open — looking up locations online.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FinishedContent(
    result: ImportViewModel.UiState.Finished,
    onViewNotes: () -> Unit,
    onImportMore: () -> Unit,
    onRetryUnresolved: () -> Unit,
    onImportWithoutLocation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Imported ${result.imported} places", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("${result.withLocation} have an exact location and will remind you nearby.")
                if (result.duplicatesSkipped > 0) {
                    append("\n${result.duplicatesSkipped} already imported were skipped.")
                }
                if (result.unresolved.isNotEmpty()) {
                    append(
                        "\n${result.unresolved.size} couldn't be located yet — " +
                                "retry, or import them without a location."
                    )
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (result.unresolved.isNotEmpty()) {
            Button(onClick = onRetryUnresolved) {
                Text("Retry ${result.unresolved.size} unresolved")
            }
            TextButton(onClick = onImportWithoutLocation) {
                Text("Import them without location")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onViewNotes) { Text("View notes") }
        } else {
            Button(onClick = onViewNotes) { Text("View notes") }
        }
        TextButton(onClick = onImportMore) { Text("Import more") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}
