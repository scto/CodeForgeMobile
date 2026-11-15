package {{packageName}}.feature.today

import {{packageName}}.core.resources.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.domain.model.TaskStats

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun TodayRoute(onOpenTasks: () -> Unit, modifier: Modifier = Modifier, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(state = state, onOpenTasks = onOpenTasks, modifier = modifier)
}

/** Begrüßung, Fortschrittsring und die nächsten offenen Aufgaben. */
@Composable
fun TodayScreen(state: TodayUiState, onOpenTasks: () -> Unit, modifier: Modifier = Modifier) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (state.name.isBlank()) stringResource(R.string.today_hello) else stringResource(R.string.today_hello_name, state.name),
            style = MaterialTheme.typography.headlineMedium
        )
        ProgressCard(stats = state.stats, dailyGoal = state.dailyGoal, goalReached = state.goalReached)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.today_next), style = MaterialTheme.typography.titleMedium)
                if (state.nextTasks.isEmpty()) {
                    Text(stringResource(R.string.today_all_done), style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.nextTasks.forEach { title -> Text("• $title", style = MaterialTheme.typography.bodyMedium) }
                }
                FilledTonalButton(onClick = onOpenTasks, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.today_open_tasks))
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(stats: TaskStats, dailyGoal: Int, goalReached: Boolean, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { stats.progress },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("${(stats.progress * PERCENT).toInt()} %", style = MaterialTheme.typography.labelLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_done_of, stats.done, stats.total), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.today_goal, dailyGoal), style = MaterialTheme.typography.bodyMedium)
                if (goalReached) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.today_goal_reached), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private const val PERCENT = 100

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    TodayScreen(
        state = TodayUiState(
            name = "Alex",
            dailyGoal = 3,
            stats = TaskStats(total = 4, done = 1),
            nextTasks = listOf("Einkaufen", "Sport"),
            isLoading = false
        ),
        onOpenTasks = {}
    )
}
