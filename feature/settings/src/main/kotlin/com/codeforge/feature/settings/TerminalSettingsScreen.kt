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
import androidx.compose.runtime.setValue
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
                        androidx.compose.material3.TextButton(onClick = { onEvent(TerminalSettingsUiEvent.BundledFontChosen) }) {
                            Text(stringRes(R.string.settings_terminal_font_jetbrains), maxLines = 1)
                        }
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

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(R.string.settings_terminal_kill_on_exit), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(R.string.settings_terminal_kill_on_exit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = uiState.killSessionsOnExit,
                        onCheckedChange = { onEvent(TerminalSettingsUiEvent.KillSessionsOnExitChanged(it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(R.string.settings_terminal_clipboard_shortcuts), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(R.string.settings_terminal_clipboard_shortcuts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = uiState.clipboardShortcuts,
                        onCheckedChange = { onEvent(TerminalSettingsUiEvent.ClipboardShortcutsChanged(it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BackupCard(uiState = uiState, onEvent = onEvent)

            Spacer(modifier = Modifier.height(16.dp))

            ExtraKeysCard(uiState = uiState, onEvent = onEvent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraKeysCard(
    modifier: Modifier = Modifier,
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit
) {
    var text by androidx.compose.runtime.remember(uiState.extraKeys) {
        androidx.compose.runtime.mutableStateOf(uiState.extraKeys)
    }
    val valid by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { ExtraKeysConfig.isValid(text) } }
    val changed = ExtraKeysConfig.normalize(text) != uiState.extraKeys
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringRes(R.string.settings_terminal_extra_keys), style = MaterialTheme.typography.titleSmall)
            Text(
                stringRes(R.string.settings_terminal_extra_keys_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtraKeysConfig.PRESETS.forEach { (label, value) ->
                    androidx.compose.material3.AssistChip(
                        onClick = { text = value },
                        label = { Text(stringRes(label), maxLines = 1) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                minLines = 3,
                isError = !valid,
                supportingText = if (valid) null else ({ Text(stringRes(R.string.settings_terminal_extra_keys_invalid)) })
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = { onEvent(TerminalSettingsUiEvent.ExtraKeysSaved(text)) },
                    enabled = valid && changed
                ) { Text(stringRes(R.string.settings_terminal_extra_keys_save)) }
                if (uiState.extraKeysCustom) {
                    androidx.compose.material3.TextButton(onClick = { onEvent(TerminalSettingsUiEvent.ExtraKeysReset) }) {
                        Text(stringRes(R.string.settings_terminal_extra_keys_reset))
                    }
                }
            }
            Text(
                stringRes(R.string.settings_terminal_extra_keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun BackupCard(
    modifier: Modifier = Modifier,
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit
) {
    var pendingRestore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }
    val createLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri -> if (uri != null) onEvent(TerminalSettingsUiEvent.BackupRequested(uri)) }
    val openLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringRes(R.string.settings_terminal_backup), style = MaterialTheme.typography.titleSmall)
            Text(
                stringRes(R.string.settings_terminal_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.backupBusy) {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.TextButton(onClick = { onEvent(TerminalSettingsUiEvent.BackupCancelled) }) {
                    Text(stringRes(R.string.settings_terminal_backup_cancel))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())
                        createLauncher.launch("codeforge-terminal-$stamp.tar.gz")
                    }) { Text(stringRes(R.string.settings_terminal_backup_create)) }
                    androidx.compose.material3.OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringRes(R.string.settings_terminal_backup_restore))
                    }
                }
            }
            uiState.backupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    pendingRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringRes(R.string.settings_terminal_backup_restore)) },
            text = { Text(stringRes(R.string.settings_terminal_restore_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingRestore = null
                    onEvent(TerminalSettingsUiEvent.RestoreRequested(uri))
                }) { Text(stringRes(R.string.settings_terminal_backup_restore)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) {
                    Text(stringRes(android.R.string.cancel))
                }
            }
        )
    }
}
