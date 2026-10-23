// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiContextSource
import com.codeforge.core.domain.ai.AiPreparedContext
import com.codeforge.core.domain.ai.AiProposedChange
import com.codeforge.core.domain.ai.AiRole
import com.codeforge.core.domain.ai.AiUsage
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    history: List<AiChatMessage>,
    isStreaming: Boolean,
    streamingText: String,
    tokenUsage: AiUsage?,
    preparedContext: AiPreparedContext?,
    selectedSources: Set<AiContextSource>,
    onToggleSource: (AiContextSource) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onInsertText: (String) -> Unit,
    onApplyDiff: (AiProposedChange) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var isContextDetailsExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(history.size, streamingText.length) {
        if (history.isNotEmpty() || streamingText.isNotEmpty()) {
            listState.animateScrollToItem((history.size).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringRes(R.string.ai_assistant_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringRes(R.string.ai_settings_title))
                }

                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.Delete, contentDescription = stringRes(R.string.ai_clear_history))
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringRes(R.string.common_schliessen))
                }
            }

            // Context sources row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ContextSourceChip(
                    source = AiContextSource.CURRENT_FILE,
                    label = stringRes(R.string.ai_context_current_file),
                    selected = selectedSources.contains(AiContextSource.CURRENT_FILE),
                    onToggle = onToggleSource
                )
                ContextSourceChip(
                    source = AiContextSource.SELECTION,
                    label = stringRes(R.string.ai_context_selection),
                    selected = selectedSources.contains(AiContextSource.SELECTION),
                    onToggle = onToggleSource
                )
                ContextSourceChip(
                    source = AiContextSource.OPEN_FILES,
                    label = stringRes(R.string.ai_context_open_files),
                    selected = selectedSources.contains(AiContextSource.OPEN_FILES),
                    onToggle = onToggleSource
                )
                ContextSourceChip(
                    source = AiContextSource.BUILD_PROBLEMS,
                    label = stringRes(R.string.ai_context_build_problems),
                    selected = selectedSources.contains(AiContextSource.BUILD_PROBLEMS),
                    onToggle = onToggleSource
                )
                ContextSourceChip(
                    source = AiContextSource.LSP_DIAGNOSTICS,
                    label = stringRes(R.string.ai_context_lsp_diagnostics),
                    selected = selectedSources.contains(AiContextSource.LSP_DIAGNOSTICS),
                    onToggle = onToggleSource
                )
                ContextSourceChip(
                    source = AiContextSource.GIT_DIFF,
                    label = stringRes(R.string.ai_context_git_diff),
                    selected = selectedSources.contains(AiContextSource.GIT_DIFF),
                    onToggle = onToggleSource
                )
            }

            // "What will be sent" preview header
            preparedContext?.let { ctx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isContextDetailsExpanded = !isContextDetailsExpanded }) {
                        Text(
                            text = "${stringRes(R.string.ai_context_what_will_be_sent)} (${ctx.totalCharacters} Chars)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            imageVector = if (isContextDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (ctx.redactedCount > 0) {
                        Text(
                            text = " · " + stringRes(R.string.ai_context_redacted_notice, ctx.redactedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                AnimatedVisibility(visible = isContextDetailsExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            ctx.items.forEach { item ->
                                val title = when (item.source) {
                                    AiContextSource.CURRENT_FILE -> stringRes(R.string.ai_context_current_file)
                                    AiContextSource.SELECTION -> stringRes(R.string.ai_context_selection)
                                    AiContextSource.OPEN_FILES -> item.title.ifBlank { stringRes(R.string.ai_context_open_files) }
                                    AiContextSource.BUILD_PROBLEMS -> stringRes(R.string.ai_context_build_problems)
                                    AiContextSource.LSP_DIAGNOSTICS -> stringRes(R.string.ai_context_lsp_diagnostics)
                                    AiContextSource.GIT_DIFF -> stringRes(R.string.ai_context_git_diff)
                                }
                                Text(
                                    text = stringRes(R.string.ai_context_item_summary, title, item.content.length),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (ctx.isTruncated) {
                                Text(
                                    text = stringRes(R.string.ai_context_truncated_notice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Token usage summary
            tokenUsage?.let { u ->
                Text(
                    text = stringRes(R.string.ai_tokens_usage, u.promptTokens, u.completionTokens, u.totalTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { message ->
                    ChatMessageItem(
                        message = message,
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) },
                        onInsert = onInsertText,
                        onApplyDiff = onApplyDiff
                    )
                }

                if (isStreaming) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = streamingText.ifEmpty { "…" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringRes(R.string.ai_chat_placeholder)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isStreaming) {
                    IconButton(onClick = onStopStreaming) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringRes(R.string.ai_stop),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onSendMessage(input.trim())
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringRes(R.string.ai_send),
                            tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextSourceChip(
    source: AiContextSource,
    label: String,
    selected: Boolean,
    onToggle: (AiContextSource) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(source) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

@Composable
private fun ChatMessageItem(
    message: AiChatMessage,
    onCopy: (String) -> Unit,
    onInsert: (String) -> Unit,
    onApplyDiff: (AiProposedChange) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == AiRole.USER
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1.0f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                if (message.proposedChanges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    for (change in message.proposedChanges) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = change.filePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    TextButton(onClick = { onCopy(change.proposedContent) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringRes(R.string.ai_copy_code))
                                    }
                                    TextButton(onClick = { onInsert(change.proposedContent) }) {
                                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringRes(R.string.ai_insert_into_file))
                                    }
                                    TextButton(onClick = { onApplyDiff(change) }) {
                                        Icon(Icons.Default.Difference, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringRes(R.string.ai_apply_as_diff))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
