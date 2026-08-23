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
        topBar = { TopAppBar(title = { Text("Editor") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tab-Größe", style = MaterialTheme.typography.titleSmall)
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
                        Text("Tree-sitter verwenden", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Präziseres Syntax-Highlighting statt regelbasiertem TextMate-Grammar.",
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
                placeholder = { Text("z.B. dark_plus") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.lspServerPath,
                onValueChange = { onEvent(EditorSettingsUiEvent.LspServerPathChanged(it)) },
                label = { Text("LSP-Server-Pfad") },
                placeholder = { Text("z.B. /root/.local/bin/kotlin-language-server") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
