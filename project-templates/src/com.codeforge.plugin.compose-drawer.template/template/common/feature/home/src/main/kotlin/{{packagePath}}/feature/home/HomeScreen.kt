package {{packageName}}.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun HomeRoute(onOpenNotes: () -> Unit, modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state, onOpenNotes = onOpenNotes, modifier = modifier)
}

/** Startseite: Begrüßung, Anzahl der Notizen und die neuesten Titel. */
@Composable
fun HomeScreen(state: HomeUiState, onOpenNotes: () -> Unit, modifier: Modifier = Modifier) {
    if (state.isLoading) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.home_welcome_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        pluralStringResource(R.plurals.home_note_count, state.noteCount, state.noteCount),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (state.latestTitles.isNotEmpty()) {
                    Text(stringResource(R.string.home_latest), style = MaterialTheme.typography.labelLarge)
                    state.latestTitles.forEach { title -> Text("• $title", style = MaterialTheme.typography.bodyMedium) }
                }
                Button(onClick = onOpenNotes, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.home_open_notes))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(noteCount = 3, latestTitles = listOf("Willkommen", "Einkaufen"), isLoading = false),
        onOpenNotes = {}
    )
}
