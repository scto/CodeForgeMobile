// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.TerminalSessionState

@Composable
fun TerminalRoute(
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TerminalScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun TerminalScreen(
    modifier: Modifier = Modifier,
    uiState: TerminalUiState,
    onEvent: (TerminalUiEvent) -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.distro.isNotBlank()) uiState.distro else "Terminal") },
                actions = {
                    when (uiState.sessionState) {
                        TerminalSessionState.RUNNING, TerminalSessionState.STARTING -> {
                            IconButton(onClick = { onEvent(TerminalUiEvent.StopSession) }) {
                                Icon(Icons.Filled.Stop, contentDescription = "Sitzung beenden")
                            }
                        }
                        else -> {
                            IconButton(onClick = { onEvent(TerminalUiEvent.StartSession) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Shell starten")
                            }
                        }
                    }
                    IconButton(onClick = { onEvent(TerminalUiEvent.ClearOutput) }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "Ausgabe löschen")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = uiState.outputText.ifBlank { placeholderFor(uiState.sessionState) },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { onEvent(TerminalUiEvent.InputChanged(it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    enabled = uiState.sessionState == TerminalSessionState.RUNNING,
                    placeholder = { Text("Befehl eingeben …") }
                )
                IconButton(
                    onClick = { onEvent(TerminalUiEvent.SendCommand) },
                    enabled = uiState.sessionState == TerminalSessionState.RUNNING
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Senden")
                }
            }
        }
    }
}

private fun placeholderFor(state: TerminalSessionState): String = when (state) {
    TerminalSessionState.STOPPED -> "Keine aktive Sitzung. Tippe auf ▶, um eine Shell zu starten."
    TerminalSessionState.STARTING -> "Shell wird gestartet …"
    TerminalSessionState.FAILED -> "Shell konnte nicht gestartet werden."
    TerminalSessionState.EXITED -> "Shell wurde beendet."
    TerminalSessionState.RUNNING -> ""
}
