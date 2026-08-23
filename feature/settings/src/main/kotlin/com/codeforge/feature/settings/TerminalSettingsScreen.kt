/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val distros = listOf("alpine" to "Alpine", "ubuntu" to "Ubuntu", "debian" to "Debian")

@Composable
fun TerminalSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: TerminalSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TerminalSettingsScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun TerminalSettingsScreen(
    modifier: Modifier = Modifier,
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Terminal") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Standard-Distribution", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            distros.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = id == uiState.defaultDistro,
                            onClick = { onEvent(TerminalSettingsUiEvent.DistroSelected(id)) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = id == uiState.defaultDistro,
                        onClick = { onEvent(TerminalSettingsUiEvent.DistroSelected(id)) }
                    )
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Schriftgröße", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEvent(TerminalSettingsUiEvent.FontSizeChanged((uiState.fontSize - 1).coerceAtLeast(8))) }) {
                            Text("–")
                        }
                        Text("${uiState.fontSize}", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onEvent(TerminalSettingsUiEvent.FontSizeChanged((uiState.fontSize + 1).coerceAtMost(32))) }) {
                            Text("+")
                        }
                    }
                }
            }
        }
    }
}
