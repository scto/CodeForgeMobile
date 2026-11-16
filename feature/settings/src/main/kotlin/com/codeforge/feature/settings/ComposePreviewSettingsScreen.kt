/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

/** Route der Vorschau-Einstellungen. */
@Composable
fun ComposePreviewSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComposePreviewSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ComposePreviewSettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Einstellungen der Compose-Vorschau: Wahl zwischen kompiliertem und interpretiertem Weg. */
@Composable
fun ComposePreviewSettingsScreen(
    state: ComposePreviewSettingsUiState,
    onEvent: (ComposePreviewSettingsUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.settings_compose_preview_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringRes(R.string.settings_compose_preview_compiled), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(
                                if (state.compiled) R.string.settings_compose_preview_compiled_on
                                else R.string.settings_compose_preview_compiled_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.compiled,
                        enabled = !state.isLoading,
                        onCheckedChange = { onEvent(ComposePreviewSettingsUiEvent.CompiledChanged(it)) },
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringRes(R.string.settings_compose_preview_compare_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringRes(R.string.settings_compose_preview_compare_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
