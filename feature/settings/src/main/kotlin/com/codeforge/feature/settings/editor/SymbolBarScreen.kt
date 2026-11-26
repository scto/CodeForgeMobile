/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Symbole & Makros: Einträge der Symbolleiste anheften, sortieren, hinzufügen und als Makro mit
 * Cursor-Markierung `$0` anlegen.
 */
package com.codeforge.feature.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.editor.SymbolBarEntry
import com.codeforge.core.domain.editor.SymbolEntryKind
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSwitchRow
import com.codeforge.core.ui.symbolActionIcon
import com.codeforge.core.ui.symbolActionLabelRes

/** Route der Symbolleisten-Seite. */
@Composable
fun SymbolBarSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SymbolBarSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SymbolBarScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

private sealed interface SymbolDialog {
    data object AddSymbol : SymbolDialog
    data class Macro(val entry: SymbolBarEntry?) : SymbolDialog
}

@Composable
private fun SymbolBarScreen(
    uiState: SymbolBarUiState,
    onEvent: (SymbolBarUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<SymbolDialog?>(null) }
    SettingsScaffold(
        title = stringRes(R.string.settings_symbols_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onEvent(SymbolBarUiEvent.Reset) }, enabled = uiState.customized) {
                Icon(Icons.Filled.Restore, contentDescription = stringRes(R.string.settings_symbols_reset))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SettingsGroup(Modifier.padding(top = 8.dp)) {
                    SettingsSwitchRow(
                        title = stringRes(R.string.settings_symbol_bar_enabled),
                        summary = stringRes(R.string.settings_symbol_bar_enabled_desc),
                        checked = uiState.enabled,
                        onCheckedChange = { onEvent(SymbolBarUiEvent.EnabledToggled) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = stringRes(R.string.settings_symbols_language_extras),
                        summary = stringRes(R.string.settings_symbols_language_extras_desc),
                        checked = uiState.languageExtras,
                        onCheckedChange = { onEvent(SymbolBarUiEvent.LanguageExtrasToggled) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { dialog = SymbolDialog.AddSymbol }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(R.string.settings_symbols_add_symbol))
                    }
                    FilledTonalButton(onClick = { dialog = SymbolDialog.Macro(null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(R.string.settings_symbols_add_macro))
                    }
                }
                SettingsSectionLabel(stringRes(R.string.settings_symbols_list))
                SettingsHint(stringRes(R.string.settings_symbols_hint), Modifier.padding(horizontal = 4.dp))
            }
            itemsIndexed(uiState.entries, key = { _, entry -> entry.id }) { index, entry ->
                SymbolRow(
                    entry = entry,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.entries.lastIndex,
                    onEvent = { onEvent(it) },
                    onMoveUp = { onEvent(SymbolBarUiEvent.Move(index, -1)) },
                    onMoveDown = { onEvent(SymbolBarUiEvent.Move(index, 1)) },
                    onEdit = { if (entry.kind == SymbolEntryKind.MACRO) dialog = SymbolDialog.Macro(entry) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    when (val current = dialog) {
        SymbolDialog.AddSymbol -> AddSymbolDialog(
            onDismiss = { dialog = null },
            onAdd = { onEvent(SymbolBarUiEvent.AddSymbol(it)); dialog = null }
        )
        is SymbolDialog.Macro -> MacroDialog(
            entry = current.entry,
            onDismiss = { dialog = null },
            onSave = { label, text -> onEvent(SymbolBarUiEvent.SaveMacro(current.entry?.id, label, text)); dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun SymbolRow(
    entry: SymbolBarEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEvent: (SymbolBarUiEvent) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.clickable(enabled = entry.kind == SymbolEntryKind.MACRO, onClick = onEdit).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntryLabel(entry, Modifier.weight(1f))
            IconButton(onClick = { onEvent(SymbolBarUiEvent.TogglePinned(entry.id)) }) {
                Icon(
                    if (entry.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringRes(R.string.settings_symbols_pin),
                    tint = if (entry.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.kind != SymbolEntryKind.ACTION) {
                IconButton(onClick = { onEvent(SymbolBarUiEvent.Remove(entry.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringRes(R.string.common_loeschen))
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringRes(R.string.settings_symbols_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringRes(R.string.settings_symbols_move_down))
            }
        }
    }
}

@Composable
private fun EntryLabel(entry: SymbolBarEntry, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (entry.kind) {
            SymbolEntryKind.ACTION -> {
                val id = entry.id.removePrefix(SymbolBarEntry.ACTION_PREFIX)
                symbolActionIcon(id)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringRes(symbolActionLabelRes(id)), style = MaterialTheme.typography.bodyLarge)
            }
            SymbolEntryKind.SYMBOL -> {
                Text(entry.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            }
            SymbolEntryKind.MACRO -> {
                Column {
                    Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        entry.text.replace("\n", "⏎"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSymbolDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.settings_symbols_add_symbol)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_SYMBOL_LENGTH) text = it },
                label = { Text(stringRes(R.string.settings_symbols_symbol)) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(stringRes(R.string.settings_symbols_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } }
    )
}

@Composable
private fun MacroDialog(entry: SymbolBarEntry?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var label by remember { mutableStateOf(entry?.label.orEmpty()) }
    var text by remember { mutableStateOf(entry?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(if (entry == null) R.string.settings_symbols_add_macro else R.string.settings_symbols_edit_macro)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringRes(R.string.settings_symbols_macro_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringRes(R.string.settings_symbols_macro_text)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 3
                )
                Text(stringRes(R.string.settings_symbols_macro_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, text) }, enabled = label.isNotBlank() && text.isNotEmpty()) {
                Text(stringRes(R.string.common_speichern))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } }
    )
}

private const val MAX_SYMBOL_LENGTH = 8
