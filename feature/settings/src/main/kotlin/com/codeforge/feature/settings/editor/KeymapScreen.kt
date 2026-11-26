/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Tastenbelegung wie in CodeAssist: Kürzel antippen, neue Tasten drücken (Esc bricht ab, Backspace entfernt);
 * ohne Hardware-Tastatur lässt sich der Akkord auch über Modifikator-Chips und Tastennamen eingeben.
 */
package com.codeforge.feature.settings.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.editor.KeyChord
import com.codeforge.core.domain.editor.Keymap
import com.codeforge.core.domain.editor.KeymapAction
import com.codeforge.core.domain.editor.KeymapCategory
import com.codeforge.core.domain.editor.display
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsLinkRow
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSwitchRow
import com.codeforge.core.ui.keyName

/** Route der Tastenbelegung. */
@Composable
fun KeymapSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: KeymapSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    KeymapScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
private fun KeymapScreen(
    uiState: KeymapUiState,
    onEvent: (KeymapUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keymap = remember(uiState.overrides) { uiState.keymap }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<KeymapAction?>(null) }
    val labels = KeymapAction.entries.associateWith { keymapActionLabel(it) }
    val filtered = KeymapAction.entries.filter { action ->
        query.isBlank() ||
            labels.getValue(action).contains(query, ignoreCase = true) ||
            keymap.chordFor(action)?.display()?.contains(query, ignoreCase = true) == true
    }

    SettingsScaffold(title = stringRes(R.string.settings_keymap_title), onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SettingsHint(stringRes(R.string.settings_keymap_hint), Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringRes(R.string.settings_keymap_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                SettingsSectionLabel(stringRes(R.string.settings_keymap_modified, keymap.modifiedCount)) {
                    TextButton(onClick = { onEvent(KeymapUiEvent.ResetAll) }, enabled = keymap.modifiedCount > 0) {
                        Text(stringRes(R.string.settings_keymap_reset_all), style = MaterialTheme.typography.labelSmall)
                    }
                }
                SettingsGroup {
                    SettingsSwitchRow(
                        title = stringRes(R.string.settings_keymap_double_shift),
                        summary = stringRes(R.string.settings_keymap_double_shift_desc),
                        checked = uiState.doubleShiftPalette,
                        onCheckedChange = { onEvent(KeymapUiEvent.DoubleShiftToggled) }
                    )
                }
            }
            KeymapCategory.entries.forEach { category ->
                val actions = filtered.filter { it.category == category }
                if (actions.isNotEmpty()) {
                    item(key = category.name) {
                        SettingsSectionLabel(keymapCategoryLabel(category))
                        SettingsGroup {
                            actions.forEachIndexed { index, action ->
                                if (index > 0) SettingsDivider()
                                KeymapRow(
                                    label = labels.getValue(action),
                                    chord = keymap.chordFor(action),
                                    modified = keymap.isModified(action),
                                    onClick = { editing = action }
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { action ->
        KeyCaptureDialog(
            action = action,
            label = labels.getValue(action),
            keymap = keymap,
            onDismiss = { editing = null },
            onApply = { chord -> onEvent(KeymapUiEvent.SetBinding(action, chord)); editing = null },
            onReset = { onEvent(KeymapUiEvent.ResetBinding(action)); editing = null }
        )
    }
}

@Composable
private fun KeymapRow(label: String, chord: KeyChord?, modified: Boolean, onClick: () -> Unit) {
    SettingsLinkRow(
        title = label,
        summary = if (modified) stringRes(R.string.settings_keymap_changed) else null,
        onClick = onClick,
        trailing = { ChordBadge(chord, highlighted = modified) }
    )
}

@Composable
private fun ChordBadge(chord: KeyChord?, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = chord?.display() ?: stringRes(R.string.settings_keymap_unbound),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private enum class Modifiers { CTRL, ALT, SHIFT, META }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyCaptureDialog(
    action: KeymapAction,
    label: String,
    keymap: Keymap,
    onDismiss: () -> Unit,
    onApply: (KeyChord?) -> Unit,
    onReset: () -> Unit
) {
    var chord by remember(action) { mutableStateOf(keymap.chordFor(action)) }
    var keyText by remember(action) { mutableStateOf(chord?.let { KeyChord.displayKey(it.key) }.orEmpty()) }
    var captureFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun withModifier(mod: Modifiers, on: Boolean) {
        val base = chord ?: KeyChord.keyFromText(keyText)?.let { KeyChord(it) } ?: return
        chord = when (mod) {
            Modifiers.CTRL -> base.copy(ctrl = on)
            Modifiers.ALT -> base.copy(alt = on)
            Modifiers.SHIFT -> base.copy(shift = on)
            Modifiers.META -> base.copy(meta = on)
        }
    }

    val conflict = chord?.let { keymap.conflictWith(action, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(
                            width = if (captureFocused) 2.dp else 1.dp,
                            color = if (captureFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged { captureFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                            val name = event.key.keyName()
                            val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed
                            when {
                                name in KeyChord.MODIFIER_KEYS -> { /* auf die eigentliche Taste warten */ }
                                plain && name == KEY_ESCAPE -> { onDismiss() }
                                plain && name == KEY_BACKSPACE -> { onApply(null) }
                                else -> {
                                    chord = KeyChord(name, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed)
                                    keyText = KeyChord.displayKey(name)
                                }
                            }
                            true
                        }
                ) {
                    Text(
                        text = chord?.display() ?: stringRes(R.string.settings_keymap_press_keys),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(stringRes(R.string.settings_keymap_manual), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Modifiers.entries.forEach { mod ->
                        val on = when (mod) {
                            Modifiers.CTRL -> chord?.ctrl == true
                            Modifiers.ALT -> chord?.alt == true
                            Modifiers.SHIFT -> chord?.shift == true
                            Modifiers.META -> chord?.meta == true
                        }
                        FilterChip(selected = on, onClick = { withModifier(mod, !on) }, label = { Text(mod.label()) })
                    }
                }
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { text ->
                        keyText = text
                        val key = KeyChord.keyFromText(text)
                        chord = key?.let { (chord ?: KeyChord(it)).copy(key = it) }
                    },
                    label = { Text(stringRes(R.string.settings_keymap_key_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (chord?.producesText == true) {
                    Text(
                        stringRes(R.string.settings_keymap_types_text),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (conflict != null) {
                    Text(
                        stringRes(R.string.settings_keymap_conflict, keymapActionLabel(conflict)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReset) { Text(stringRes(R.string.settings_keymap_default)) }
                    TextButton(onClick = { onApply(null) }) { Text(stringRes(R.string.settings_keymap_remove)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(chord) }, enabled = chord != null && chord?.producesText == false) {
                Text(stringRes(R.string.settings_keymap_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } }
    )
}

private fun Modifiers.label(): String = when (this) {
    Modifiers.CTRL -> "Ctrl"
    Modifiers.ALT -> "Alt"
    Modifiers.SHIFT -> "Shift"
    Modifiers.META -> "Meta"
}

private const val KEY_ESCAPE = "ESCAPE"
private const val KEY_BACKSPACE = "DEL"

@Composable
private fun keymapCategoryLabel(category: KeymapCategory): String = stringRes(
    when (category) {
        KeymapCategory.EDITING -> R.string.settings_keymap_cat_editing
        KeymapCategory.CODE -> R.string.settings_keymap_cat_code
        KeymapCategory.NAVIGATION -> R.string.settings_keymap_cat_navigation
        KeymapCategory.TABS -> R.string.settings_keymap_cat_tabs
        KeymapCategory.FOLDING -> R.string.settings_keymap_cat_folding
    }
)

/** Anzeigename einer Aktion. */
@Composable
internal fun keymapActionLabel(action: KeymapAction): String {
    action.tabIndex?.let { return stringRes(R.string.keymap_action_go_to_tab, it + 1) }
    return stringRes(
        when (action) {
            KeymapAction.UNDO -> R.string.keymap_action_undo
            KeymapAction.REDO -> R.string.keymap_action_redo
            KeymapAction.SAVE -> R.string.keymap_action_save
            KeymapAction.TOGGLE_COMMENT -> R.string.keymap_action_toggle_comment
            KeymapAction.DUPLICATE_LINE -> R.string.keymap_action_duplicate_line
            KeymapAction.DELETE_LINE -> R.string.keymap_action_delete_line
            KeymapAction.MOVE_LINE_UP -> R.string.keymap_action_move_line_up
            KeymapAction.MOVE_LINE_DOWN -> R.string.keymap_action_move_line_down
            KeymapAction.FORMAT -> R.string.keymap_action_format
            KeymapAction.QUICK_FIX -> R.string.keymap_action_quick_fix
            KeymapAction.REFACTOR -> R.string.keymap_action_refactor
            KeymapAction.RENAME -> R.string.keymap_action_rename
            KeymapAction.DEFINITION -> R.string.keymap_action_definition
            KeymapAction.REFERENCES -> R.string.keymap_action_references
            KeymapAction.HOVER -> R.string.keymap_action_hover
            KeymapAction.SIGNATURE_HELP -> R.string.keymap_action_signature_help
            KeymapAction.ORGANIZE_IMPORTS -> R.string.keymap_action_organize_imports
            KeymapAction.COMMAND_PALETTE -> R.string.keymap_action_command_palette
            KeymapAction.FIND -> R.string.keymap_action_find
            KeymapAction.GO_TO_LINE -> R.string.keymap_action_go_to_line
            KeymapAction.OUTLINE -> R.string.keymap_action_outline
            KeymapAction.NEXT_TAB -> R.string.keymap_action_next_tab
            KeymapAction.PREVIOUS_TAB -> R.string.keymap_action_previous_tab
            KeymapAction.CLOSE_TAB -> R.string.keymap_action_close_tab
            KeymapAction.CLOSE_OTHER_TABS -> R.string.keymap_action_close_other_tabs
            KeymapAction.FOLD_TOGGLE -> R.string.keymap_action_fold_toggle
            KeymapAction.FOLD_ALL -> R.string.keymap_action_fold_all
            KeymapAction.UNFOLD_ALL -> R.string.keymap_action_unfold_all
            else -> R.string.keymap_action_go_to_tab_generic
        }
    )
}
