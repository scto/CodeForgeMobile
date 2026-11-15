package {{packageName}}.feature.profile

import {{packageName}}.core.resources.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.model.TaskStats
import kotlin.math.roundToInt

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun ProfileRoute(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Name, Tagesziel und Statistik. */
@Composable
fun ProfileScreen(state: ProfileUiState, onEvent: (ProfileUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        OutlinedTextField(
            value = state.profile.name,
            onValueChange = { onEvent(ProfileUiEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_goal, state.profile.dailyGoal), style = MaterialTheme.typography.titleSmall)
            Slider(
                value = state.profile.dailyGoal.toFloat(),
                onValueChange = { onEvent(ProfileUiEvent.GoalChanged(it.roundToInt())) },
                valueRange = 1f..MAX_GOAL.toFloat(),
                steps = MAX_GOAL - 2
            )
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(value = state.stats.total, label = stringResource(R.string.profile_stat_total), modifier = Modifier.weight(1f))
                Stat(value = state.stats.done, label = stringResource(R.string.profile_stat_done), modifier = Modifier.weight(1f))
                Stat(value = state.stats.open, label = stringResource(R.string.profile_stat_open), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

private const val MAX_GOAL = 10

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(state = ProfileUiState(Profile("Alex", 4), TaskStats(total = 5, done = 2)), onEvent = {})
}
