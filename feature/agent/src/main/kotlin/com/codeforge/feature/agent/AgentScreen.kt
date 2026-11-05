/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Chat-Panel des Agenten für den Drawer: Verlauf mit Werkzeugstatus, Berechtigungsmodus, Freigabe-Karte,
 * Änderungszusammenfassung mit „Rückgängig“ und Eingabezeile.
 */
package com.codeforge.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.agent.PermissionMode

/** Einstieg des Panels; [rootPath] ist der Projektstamm. */
@Composable
fun AgentRoute(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: AgentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(rootPath) { viewModel.onEvent(AgentUiEvent.Initialize(rootPath)) }
    AgentScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Zustandsloses Panel (für Previews und Tests). */
@Composable
fun AgentScreen(
    state: AgentUiState,
    onEvent: (AgentUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Beim Streamen mitscrollen: Länge der Liste und Textlänge des letzten Eintrags.
    val lastLength = (state.items.lastOrNull() as? AgentChatItem.Assistant)?.text?.length ?: 0
    LaunchedEffect(state.items.size, lastLength) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Header(state, onEvent)
        if (state.items.isEmpty()) {
            Text(
                stringRes(R.string.agent_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(16.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { item -> ChatItem(item, running = state.running, onEvent = onEvent) }
            }
        }
        if (state.todos.isNotEmpty()) TodoStrip(state.todos)
        state.pendingApproval?.let { ApprovalCard(it, onEvent) }
        InputRow(state, onEvent)
    }
}

@Composable
private fun Header(state: AgentUiState, onEvent: (AgentUiEvent) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(R.string.agent_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEvent(AgentUiEvent.NewChat) }, enabled = !state.running) {
                Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.agent_new_chat))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(PermissionMode.ASK_EACH, R.string.agent_mode_ask, state, onEvent)
            ModeChip(PermissionMode.AUTO_ACCEPT, R.string.agent_mode_auto, state, onEvent)
            ModeChip(PermissionMode.PLAN_ONLY, R.string.agent_mode_plan, state, onEvent)
        }
    }
}

@Composable
private fun ModeChip(mode: PermissionMode, label: Int, state: AgentUiState, onEvent: (AgentUiEvent) -> Unit) {
    FilterChip(
        selected = state.mode == mode,
        onClick = { onEvent(AgentUiEvent.ModeChanged(mode)) },
        label = { Text(stringRes(label), style = MaterialTheme.typography.labelMedium) },
    )
}

@Composable
private fun ChatItem(item: AgentChatItem, running: Boolean, onEvent: (AgentUiEvent) -> Unit) {
    when (item) {
        is AgentChatItem.User -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(item.text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium) }

        is AgentChatItem.Assistant -> Text(item.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())

        is AgentChatItem.ToolCall -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.status == ToolCallStatus.RUNNING) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            when (item.status) {
                                ToolCallStatus.OK -> "✓"
                                ToolCallStatus.DENIED -> "⊘"
                                else -> "✗"
                            },
                            color = if (item.status == ToolCallStatus.OK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(item.summary, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (item.status == ToolCallStatus.ERROR || item.status == ToolCallStatus.DENIED) {
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        is AgentChatItem.Notice -> Text(
            item.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is AgentChatItem.Changes -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text(stringRes(R.string.agent_changes_title, item.paths.size), style = MaterialTheme.typography.labelLarge)
                item.paths.take(MAX_LISTED_PATHS).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (item.paths.size > MAX_LISTED_PATHS) Text("… +${item.paths.size - MAX_LISTED_PATHS}", style = MaterialTheme.typography.bodySmall)
                if (item.reverted) {
                    Text(stringRes(R.string.agent_reverted), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = { onEvent(AgentUiEvent.Revert(item.turnId)) }, enabled = !running) {
                        Text(stringRes(R.string.agent_revert))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoStrip(todos: List<AgentTodo>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        todos.forEach {
            val mark = when (it.status) {
                "done" -> "☑"
                "in_progress" -> "▶"
                else -> "☐"
            }
            Text("$mark ${it.content}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ApprovalCard(approval: PendingApproval, onEvent: (AgentUiEvent) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(stringRes(R.string.agent_approval_title), style = MaterialTheme.typography.labelLarge)
            Text(approval.summary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onEvent(AgentUiEvent.Decide(false)) }) { Text(stringRes(R.string.agent_deny)) }
                TextButton(onClick = { onEvent(AgentUiEvent.Decide(true)) }) { Text(stringRes(R.string.agent_approve)) }
            }
        }
    }
}

@Composable
private fun InputRow(state: AgentUiState, onEvent: (AgentUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { onEvent(AgentUiEvent.InputChanged(it)) },
            placeholder = { Text(stringRes(R.string.agent_input_hint)) },
            enabled = !state.running,
            maxLines = 5,
            modifier = Modifier.weight(1f),
        )
        if (state.running) {
            IconButton(onClick = { onEvent(AgentUiEvent.Stop) }) {
                Icon(Icons.Filled.Stop, contentDescription = stringRes(R.string.agent_stop))
            }
        } else {
            IconButton(onClick = { onEvent(AgentUiEvent.Send) }, enabled = state.input.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = stringRes(R.string.agent_send))
            }
        }
    }
}

private const val MAX_LISTED_PATHS = 8
