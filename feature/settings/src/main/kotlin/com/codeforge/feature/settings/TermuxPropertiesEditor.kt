/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Abschnitt „termux.properties“: Pfad, Bearbeiten-Schaltfläche, Rückmeldung; öffnet den Vollbild-Editor. */
@Composable
internal fun TermuxPropertiesCard(
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringRes(R.string.settings_terminal_properties), style = MaterialTheme.typography.titleSmall)
            Text(
                stringRes(R.string.settings_terminal_properties_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                uiState.propertiesPath,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { onEvent(TerminalSettingsUiEvent.PropertiesEditRequested) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringRes(R.string.settings_terminal_properties_edit)) }
            uiState.propertiesMessage?.takeIf { uiState.propertiesDraft == null }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    uiState.propertiesDraft?.let { draft ->
        TermuxPropertiesEditorDialog(
            initial = draft,
            error = uiState.propertiesMessage,
            onSave = { onEvent(TerminalSettingsUiEvent.PropertiesSaved(it)) },
            onClose = { onEvent(TerminalSettingsUiEvent.PropertiesEditCancelled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermuxPropertiesEditorDialog(
    initial: String,
    error: String?,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (text != initial) confirmDiscard = true else onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringRes(R.string.settings_terminal_properties)) },
                    navigationIcon = {
                        IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = stringRes(android.R.string.cancel)) }
                    },
                    actions = {
                        TextButton(onClick = { onSave(text) }, enabled = text != initial) {
                            Text(stringRes(R.string.settings_terminal_properties_save))
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringRes(R.string.settings_terminal_properties_discard)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringRes(R.string.settings_terminal_properties_discard_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringRes(android.R.string.cancel)) }
            },
        )
    }
}
