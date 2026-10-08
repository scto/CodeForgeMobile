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
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
fun EditorSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    EditorSettingsScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun EditorSettingsScreen(
    modifier: Modifier = Modifier,
    uiState: EditorSettingsUiState,
    onEvent: (EditorSettingsUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringRes(R.string.common_editor)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringRes(R.string.settings_tab_groesse), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize - 1).coerceAtLeast(1))) }) {
                            Text("–")
                        }
                        Text("${uiState.tabSize}", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize + 1).coerceAtMost(8))) }) {
                            Text("+")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(R.string.settings_tree_sitter_verwenden), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(R.string.settings_praeziseres_syntax_highlighting_statt),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = uiState.useTreeSitter,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.TreeSitterToggled) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.textmateTheme,
                onValueChange = { onEvent(EditorSettingsUiEvent.TextmateThemeChanged(it)) },
                label = { Text("TextMate-Theme") },
                placeholder = { Text(stringRes(R.string.settings_dark_plus)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.lspServerPath,
                onValueChange = { onEvent(EditorSettingsUiEvent.LspServerPathChanged(it)) },
                label = { Text("LSP-Server-Pfad") },
                placeholder = { Text(stringRes(R.string.settings_lsp_path_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringRes(R.string.settings_schriftgroesse), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.FontSizeChanged((uiState.fontSize - 1).coerceAtLeast(8))) }) {
                            Text("–")
                        }
                        Text("${uiState.fontSize}sp", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.FontSizeChanged((uiState.fontSize + 1).coerceAtMost(32))) }) {
                            Text("+")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.fontFamily,
                onValueChange = { onEvent(EditorSettingsUiEvent.FontFamilyChanged(it)) },
                label = { Text(stringRes(R.string.settings_schriftart_asset_pfad)) },
                placeholder = { Text(stringRes(R.string.settings_fonts_jetbrainsmono_regular_ttf)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            EditorToggleRow(
                title = Res.string(R.string.settings_zeilenumbruch_word_wrap),
                checked = uiState.wordWrap,
                onToggle = { onEvent(EditorSettingsUiEvent.WordWrapToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_nicht_druckbare_zeichen_anzeigen),
                description = Res.string(R.string.settings_leerzeichen_tabs_und_zeilenumbrueche_a),
                checked = uiState.showNonPrintableChars,
                onToggle = { onEvent(EditorSettingsUiEvent.NonPrintableCharsToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_sticky_scroll),
                description = Res.string(R.string.settings_zeigt_den_umschliessenden_code_block),
                checked = uiState.stickyScrollEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.StickyScrollToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_text_lupe_magnifier),
                description = Res.string(R.string.settings_vergroesserungsglas_bei_cursor_platzie),
                checked = uiState.magnifierEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.MagnifierToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_klammern_anfuehrungszeichen_automatisc),
                checked = uiState.symbolPairAutocompleteEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.SymbolPairAutocompleteToggled) }
            )
        }
    }
}

@Composable
private fun EditorToggleRow(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}
