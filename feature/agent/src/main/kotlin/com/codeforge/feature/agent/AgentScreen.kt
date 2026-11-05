/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Chat-Panel des Agenten für den Drawer: Verlauf mit Werkzeugstatus, Berechtigungsmodus, Freigabe-Karte,
 * Änderungszusammenfassung mit „Rückgängig“ und Eingabezeile.
 */
package com.codeforge.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.codeforge.libs.mcp.McpStatus

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
    if (state.showConnections) ConnectionsDialog(state, onEvent)
}

@Composable
private fun Header(state: AgentUiState, onEvent: (AgentUiEvent) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(R.string.agent_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEvent(AgentUiEvent.ShowConnections(true)) }) {
                Icon(Icons.Filled.Extension, contentDescription = stringRes(R.string.agent_connections_title))
            }
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
                item.files.take(MAX_LISTED_PATHS).forEach { file -> ChangedFileRow(file) }
                if (item.files.size > MAX_LISTED_PATHS) Text("… +${item.files.size - MAX_LISTED_PATHS}", style = MaterialTheme.typography.bodySmall)
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
private fun ChangedFileRow(file: ChangedFile) {
    var expanded by rememberSaveable(file.path) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            (if (expanded) "▾ " else "▸ ") + file.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.clickable(enabled = file.diff.isNotEmpty()) { expanded = !expanded }.padding(vertical = 2.dp),
        )
        if (expanded) DiffView(file.diff)
    }
}

/** Unified-Diff mit farbigen Plus-/Minus-Zeilen, waagerecht scrollbar und in der Höhe begrenzt. */
@Composable
private fun DiffView(diff: String, modifier: Modifier = Modifier) {
    val added = MaterialTheme.colorScheme.primary
    val removed = MaterialTheme.colorScheme.error
    val hunk = MaterialTheme.colorScheme.tertiary
    val text = remember(diff, added, removed, hunk) {
        buildAnnotatedString {
            diff.lines().forEachIndexed { i, line ->
                if (i > 0) append('\n')
                val color = when {
                    line.startsWith("@@") -> hunk
                    line.startsWith("+") -> added
                    line.startsWith("-") -> removed
                    else -> null
                }
                if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
            }
        }
    }
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    )
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
            approval.preview?.let { DiffView(it, Modifier.padding(top = 6.dp)) }
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

@Composable
private fun ConnectionsDialog(state: AgentUiState, onEvent: (AgentUiEvent) -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onEvent(AgentUiEvent.ShowConnections(false)) },
        title = { Text(stringRes(R.string.agent_connections_title)) },
        confirmButton = { TextButton(onClick = { onEvent(AgentUiEvent.ShowConnections(false)) }) { Text(stringRes(R.string.agent_close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.agent_connections_hint), style = MaterialTheme.typography.bodySmall)
                if (state.mcpServers.isEmpty()) {
                    Text(stringRes(R.string.agent_connections_empty), style = MaterialTheme.typography.bodySmall)
                }
                state.mcpServers.forEach { server -> McpServerRow(server, onEvent) }
                Text(stringRes(R.string.agent_connections_add), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(target, { target = it }, label = { Text(stringRes(R.string.agent_connections_target)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.agent_connections_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text(stringRes(R.string.agent_connections_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(
                    enabled = target.isNotBlank(),
                    onClick = {
                        onEvent(AgentUiEvent.AddMcpServer(target, name, token))
                        target = ""
                        name = ""
                        token = ""
                    },
                ) { Text(stringRes(R.string.agent_connections_add_button)) }
            }
        },
    )
}

@Composable
private fun McpServerRow(server: McpServerUi, onEvent: (AgentUiEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(server.name + if (server.fromProject) " · .mcp.json" else "", style = MaterialTheme.typography.labelLarge)
            Text(server.target, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            val status = when (server.status) {
                McpStatus.READY -> stringRes(R.string.agent_mcp_ready, server.toolCount)
                McpStatus.CONNECTING -> stringRes(R.string.agent_mcp_connecting)
                McpStatus.DISABLED -> stringRes(R.string.agent_mcp_disabled)
                McpStatus.NEEDS_TRUST -> stringRes(R.string.agent_mcp_needs_trust)
                McpStatus.FAILED -> stringRes(R.string.agent_mcp_failed, server.message.orEmpty())
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (server.status == McpStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                if (server.status == McpStatus.NEEDS_TRUST) {
                    TextButton(onClick = { onEvent(AgentUiEvent.TrustMcpServer(server.name)) }) { Text(stringRes(R.string.agent_mcp_trust)) }
                }
                if (server.status == McpStatus.FAILED) {
                    TextButton(onClick = { onEvent(AgentUiEvent.ReconnectMcpServer(server.name)) }) { Text(stringRes(R.string.agent_mcp_reconnect)) }
                }
                if (!server.fromProject) {
                    TextButton(onClick = { onEvent(AgentUiEvent.ToggleMcpServer(server.name)) }) {
                        Text(stringRes(if (server.status == McpStatus.DISABLED) R.string.agent_mcp_enable else R.string.agent_mcp_disable))
                    }
                    TextButton(onClick = { onEvent(AgentUiEvent.RemoveMcpServer(server.name)) }) { Text(stringRes(R.string.agent_mcp_remove)) }
                }
            }
        }
    }
}
