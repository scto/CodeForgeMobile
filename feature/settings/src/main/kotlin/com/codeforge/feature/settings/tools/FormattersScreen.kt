// Modul: :feature:settings
package com.codeforge.feature.settings.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

/** Route „Formatierer verwalten“. */
@Composable
fun FormattersRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UserToolsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    FormattersScreen(modifier = modifier, state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/** Liste aller Formatierer (Plugins und eigene) mit Schalter; eigene lassen sich anlegen und löschen. */
@Composable
fun FormattersScreen(
    state: UserToolsUiState,
    onEvent: (UserToolsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    if (adding) FormatterDialog(onDismiss = { adding = false }, onSave = { adding = false; onEvent(UserToolsEvent.SaveFormatter(it)) })
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.user_formatters_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringRes(R.string.user_formatter_add)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") { Text(stringRes(R.string.user_formatters_intro), style = MaterialTheme.typography.bodyMedium) }
            if (state.formatters.isEmpty()) item(key = "empty") { Text(stringRes(R.string.user_formatters_empty)) }
            items(state.formatters, key = { it.pluginId + ":" + it.formatter.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(row.formatter.label, style = MaterialTheme.typography.titleMedium)
                                val exts = row.formatter.extensions.joinToString { ".$it" }
                                val origin = if (row.isUser) stringRes(R.string.user_origin_own) else row.pluginId
                                Text(listOf(origin, exts).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = row.enabled, onCheckedChange = { onEvent(UserToolsEvent.SetFormatterEnabled(row.pluginId, row.formatter.id, it, row.formatter.label)) })
                        }
                        if (row.isUser) {
                            Text(row.formatter.command.joinToString(" "), style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = { onEvent(UserToolsEvent.RemoveFormatter(row.formatter.id)) }) { Text(stringRes(R.string.common_entfernen)) }
                        }
                    }
                }
            }
        }
    }
}
