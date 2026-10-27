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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

@Composable
fun TerminalSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: TerminalSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TerminalSettingsScreen(modifier = modifier, uiState = uiState, onBack = onBack, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalSettingsScreen(
    modifier: Modifier = Modifier,
    uiState: TerminalSettingsUiState,
    onBack: () -> Unit = {},
    onEvent: (TerminalSettingsUiEvent) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.settings_terminal), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                stringRes(R.string.settings_die_shell_laeuft_in_der),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringRes(R.string.settings_terminal_ansicht),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = !uiState.useCompactTerminal,
                                onClick = { onEvent(TerminalSettingsUiEvent.UseCompactTerminalChanged(false)) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !uiState.useCompactTerminal,
                            onClick = { onEvent(TerminalSettingsUiEvent.UseCompactTerminalChanged(false)) }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                stringRes(R.string.settings_termux_ui),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringRes(R.string.settings_termux_ui_beschreibung),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.useCompactTerminal,
                                onClick = { onEvent(TerminalSettingsUiEvent.UseCompactTerminalChanged(true)) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.useCompactTerminal,
                            onClick = { onEvent(TerminalSettingsUiEvent.UseCompactTerminalChanged(true)) }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                stringRes(R.string.settings_terminal_kompakt),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringRes(R.string.settings_terminal_kompakt_beschreibung),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringRes(R.string.settings_schriftgroesse), style = MaterialTheme.typography.titleSmall)
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

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val stepIndex = TerminalScrollback.STEPS.indexOfFirst { it >= uiState.scrollbackRows }
                        .let { if (it < 0) TerminalScrollback.STEPS.lastIndex else it }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringRes(R.string.settings_terminal_scrollback), style = MaterialTheme.typography.titleSmall)
                        Text("${uiState.scrollbackRows}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        stringRes(R.string.settings_terminal_scrollback_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Slider(
                        value = stepIndex.toFloat(),
                        onValueChange = {
                            onEvent(TerminalSettingsUiEvent.ScrollbackChanged(TerminalScrollback.STEPS[it.toInt().coerceIn(0, TerminalScrollback.STEPS.lastIndex)]))
                        },
                        valueRange = 0f..TerminalScrollback.STEPS.lastIndex.toFloat(),
                        steps = TerminalScrollback.STEPS.size - 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) onEvent(TerminalSettingsUiEvent.FontFileChosen(uri)) }
            val fontMessage = uiState.fontMessageRes?.let { stringRes(it) }
            androidx.compose.runtime.LaunchedEffect(fontMessage) {
                if (fontMessage != null) {
                    android.widget.Toast.makeText(context, fontMessage, android.widget.Toast.LENGTH_SHORT).show()
                    onEvent(TerminalSettingsUiEvent.FontMessageShown)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringRes(R.string.settings_terminal_font), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (uiState.customFontInstalled) stringRes(R.string.settings_terminal_font_custom)
                        else stringRes(R.string.settings_terminal_font_system),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringRes(R.string.settings_terminal_font_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.TextButton(onClick = {
                            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
                        }) { Text(stringRes(R.string.settings_terminal_font_pick), maxLines = 1) }
                        if (uiState.customFontInstalled) {
                            androidx.compose.material3.TextButton(onClick = { onEvent(TerminalSettingsUiEvent.FontReset) }) {
                                Text(stringRes(R.string.settings_terminal_font_system), maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringRes(R.string.settings_terminal_cursor_style), style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "block" to R.string.settings_terminal_cursor_block,
                        "bar" to R.string.settings_terminal_cursor_bar,
                        "underline" to R.string.settings_terminal_cursor_underline
                    ).forEach { (style, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = uiState.cursorStyle == style,
                                    onClick = { onEvent(TerminalSettingsUiEvent.CursorStyleChanged(style)) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.cursorStyle == style,
                                onClick = { onEvent(TerminalSettingsUiEvent.CursorStyleChanged(style)) }
                            )
                            Text(stringRes(label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
