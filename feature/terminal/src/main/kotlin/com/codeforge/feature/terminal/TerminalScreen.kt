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
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

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
                title = { Text(stringRes(R.string.terminal_terminal_termux)) },
                actions = {
                    when (uiState.sessionState) {
                        TerminalSessionState.RUNNING, TerminalSessionState.STARTING -> {
                            IconButton(onClick = { onEvent(TerminalUiEvent.StopSession) }) {
                                Icon(Icons.Filled.Stop, contentDescription = stringRes(R.string.terminal_sitzung_beenden))
                            }
                        }
                        else -> {
                            IconButton(onClick = { onEvent(TerminalUiEvent.StartSession) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringRes(R.string.terminal_shell_starten))
                            }
                        }
                    }
                    IconButton(onClick = { onEvent(TerminalUiEvent.ClearOutput) }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = stringRes(R.string.terminal_ausgabe_loeschen))
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
                    placeholder = { Text(stringRes(R.string.terminal_befehl_eingeben)) }
                )
                IconButton(
                    onClick = { onEvent(TerminalUiEvent.SendCommand) },
                    enabled = uiState.sessionState == TerminalSessionState.RUNNING
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringRes(R.string.terminal_senden))
                }
            }
        }
    }
}

private fun placeholderFor(state: TerminalSessionState): String = when (state) {
    TerminalSessionState.STOPPED -> Res.string(R.string.terminal_keine_aktive_sitzung_tippe_auf)
    TerminalSessionState.STARTING -> Res.string(R.string.terminal_shell_wird_gestartet)
    TerminalSessionState.FAILED -> Res.string(R.string.terminal_shell_konnte_nicht_gestartet_werden)
    TerminalSessionState.EXITED -> Res.string(R.string.terminal_shell_wurde_beendet)
    TerminalSessionState.RUNNING -> ""
}
