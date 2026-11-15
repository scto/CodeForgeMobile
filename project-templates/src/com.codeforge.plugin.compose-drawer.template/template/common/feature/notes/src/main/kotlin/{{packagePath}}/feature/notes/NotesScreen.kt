package {{packageName}}.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun NotesRoute(modifier: Modifier = Modifier, viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotesScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Notizliste mit Hinzufügen-Knopf und Dialog. */
@Composable
fun NotesScreen(state: NotesUiState, onEvent: (NotesUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (state.notes.isEmpty()) {
            Text(
                stringResource(R.string.notes_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(note = note, onDelete = { onEvent(NotesUiEvent.DeleteClicked(note.id)) })
                }
            }
        }
        FloatingActionButton(
            onClick = { onEvent(NotesUiEvent.AddClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.notes_add))
        }
    }
    state.draft?.let { draft -> NoteDialog(draft = draft, onEvent = onEvent) }
}

@Composable
private fun NoteCard(note: Note, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                if (note.body.isNotEmpty()) {
                    Text(
                        note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.notes_delete))
            }
        }
    }
}

@Composable
private fun NoteDialog(draft: NoteDraft, onEvent: (NotesUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(NotesUiEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.notes_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onEvent(NotesUiEvent.TitleChanged(it)) },
                    label = { Text(stringResource(R.string.notes_title)) },
                    isError = draft.showTitleError,
                    supportingText = if (draft.showTitleError) {
                        { Text(stringResource(R.string.notes_title_missing)) }
                    } else {
                        null
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.body,
                    onValueChange = { onEvent(NotesUiEvent.BodyChanged(it)) },
                    label = { Text(stringResource(R.string.notes_body)) },
                    minLines = 3
                )
            }
        },
        confirmButton = { TextButton(onClick = { onEvent(NotesUiEvent.SaveClicked) }) { Text(stringResource(R.string.notes_save)) } },
        dismissButton = { TextButton(onClick = { onEvent(NotesUiEvent.DialogDismissed) }) { Text(stringResource(R.string.notes_cancel)) } }
    )
}

@Preview(showBackground = true)
@Composable
private fun NotesScreenPreview() {
    NotesScreen(
        state = NotesUiState(notes = listOf(Note(1, "Einkaufen", "Milch, Brot", 0L), Note(2, "Idee", "", 0L))),
        onEvent = {}
    )
}
