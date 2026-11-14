package {{packageName}}.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Startbildschirm ohne Logik. */
@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Text(state.greeting, style = MaterialTheme.typography.headlineMedium)
            Text(stringCount(state.count), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { onEvent(HomeUiEvent.IncrementClicked) }) { Text("+1") }
        }
    }
}

private fun stringCount(count: Int): String = "Zähler: $count"

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(state = HomeUiState(greeting = "Hallo", count = 3, isLoading = false), onEvent = {})
}
