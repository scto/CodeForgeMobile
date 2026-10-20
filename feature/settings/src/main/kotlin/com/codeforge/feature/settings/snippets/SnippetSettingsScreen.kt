// Modul: :feature:settings
package com.codeforge.feature.settings.snippets

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetValidationResult
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
fun SnippetSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SnippetSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SnippetSettingsScreen(
        modifier = modifier,
        uiState = uiState,
        onBack = onBack,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetSettingsScreen(
    modifier: Modifier = Modifier,
    uiState: SnippetSettingsUiState,
    onBack: () -> Unit,
    onEvent: (SnippetSettingsUiEvent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onEvent(SnippetSettingsUiEvent.DismissUserMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.snippets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringRes(R.string.common_zurueck)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(SnippetSettingsUiEvent.ClickExport) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = stringRes(R.string.snippets_export))
                    }
                    IconButton(onClick = { onEvent(SnippetSettingsUiEvent.ClickImport) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = stringRes(R.string.snippets_import))
                    }
                    IconButton(onClick = { onEvent(SnippetSettingsUiEvent.ClickResetToDefaults) }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = stringRes(R.string.snippets_reset_defaults))
                    }
                    IconButton(onClick = { onEvent(SnippetSettingsUiEvent.ClickCreateSnippet) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.snippets_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Suchfeld
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onEvent(SnippetSettingsUiEvent.SearchChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringRes(R.string.snippets_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onEvent(SnippetSettingsUiEvent.SearchChanged("")) }) {
                            Icon(Icons.Filled.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true
            )

            // Scope-Filter-Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedScope == null,
                        onClick = { onEvent(SnippetSettingsUiEvent.ScopeFilterSelected(null)) },
                        label = { Text(stringRes(R.string.snippets_all_filter)) }
                    )
                }
                items(uiState.availableScopes) { scope ->
                    FilterChip(
                        selected = uiState.selectedScope.equals(scope, ignoreCase = true),
                        onClick = {
                            val newScope = if (uiState.selectedScope.equals(scope, ignoreCase = true)) null else scope
                            onEvent(SnippetSettingsUiEvent.ScopeFilterSelected(newScope))
                        },
                        label = { Text(scope) }
                    )
                }
            }

            val snippets = uiState.filteredSnippets
            if (snippets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringRes(R.string.snippets_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        SnippetCard(
                            snippet = snippet,
                            onToggleEnabled = { isEnabled ->
                                onEvent(SnippetSettingsUiEvent.ToggleEnabled(snippet.id, isEnabled))
                            },
                            onEdit = { onEvent(SnippetSettingsUiEvent.ClickEditSnippet(snippet)) },
                            onDelete = { onEvent(SnippetSettingsUiEvent.RequestDeleteSnippet(snippet)) }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Snippet erstellen / bearbeiten
    if (uiState.isCreateOrEditDialogOpen) {
        SnippetEditDialog(
            uiState = uiState,
            onEvent = onEvent
        )
    }

    // Dialog: Import / Export
    if (uiState.isImportExportDialogOpen) {
        SnippetImportExportDialog(
            uiState = uiState,
            onEvent = onEvent
        )
    }

    // Dialog: Reset Bestätigung
    if (uiState.isResetConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { onEvent(SnippetSettingsUiEvent.DismissResetDialog) },
            title = { Text(stringRes(R.string.snippets_reset_defaults)) },
            text = { Text(stringRes(R.string.snippets_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = { onEvent(SnippetSettingsUiEvent.ConfirmResetToDefaults) }
                ) {
                    Text(stringRes(R.string.pw_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SnippetSettingsUiEvent.DismissResetDialog) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }

    // Dialog: Delete Bestätigung
    uiState.deletingSnippet?.let { snippetToDelete ->
        AlertDialog(
            onDismissRequest = { onEvent(SnippetSettingsUiEvent.DismissDeleteDialog) },
            title = { Text(stringRes(R.string.snippets_delete)) },
            text = { Text(Res.string(R.string.snippets_delete_confirm, snippetToDelete.prefix)) },
            confirmButton = {
                Button(
                    onClick = { onEvent(SnippetSettingsUiEvent.ConfirmDeleteSnippet) }
                ) {
                    Text(stringRes(R.string.snippets_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SnippetSettingsUiEvent.DismissDeleteDialog) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }
}

@Composable
private fun SnippetCard(
    snippet: Snippet,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kürzel-Badge (Monospace)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = snippet.prefix,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Name, Scope & Beschreibung
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snippet.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = snippet.scope.joinToString(", ").ifEmpty { "all" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (snippet.isBuiltin) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringRes(R.string.snippets_builtin_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (snippet.description.isNotBlank()) {
                    Text(
                        text = snippet.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Actions: Switch, Edit, Delete
            Switch(
                checked = snippet.isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringRes(R.string.snippets_edit))
            }

            if (!snippet.isBuiltin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringRes(R.string.snippets_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnippetEditDialog(
    uiState: SnippetSettingsUiState,
    onEvent: (SnippetSettingsUiEvent) -> Unit
) {
    val isEditing = uiState.editingSnippet != null
    val validation = uiState.editValidationResult
    val isValid = validation is SnippetValidationResult.Valid

    Dialog(
        onDismissRequest = { onEvent(SnippetSettingsUiEvent.DismissEditDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringRes(if (isEditing) R.string.snippets_edit else R.string.snippets_add),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Prefix & Scope in einer Zeile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.editPrefix,
                        onValueChange = { onEvent(SnippetSettingsUiEvent.EditPrefixChanged(it)) },
                        label = { Text(stringRes(R.string.snippets_prefix)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.editScope,
                        onValueChange = { onEvent(SnippetSettingsUiEvent.EditScopeChanged(it)) },
                        label = { Text(stringRes(R.string.snippets_scope)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Name
                OutlinedTextField(
                    value = uiState.editName,
                    onValueChange = { onEvent(SnippetSettingsUiEvent.EditNameChanged(it)) },
                    label = { Text(stringRes(R.string.snippets_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = uiState.editDescription,
                    onValueChange = { onEvent(SnippetSettingsUiEvent.EditDescriptionChanged(it)) },
                    label = { Text(stringRes(R.string.snippets_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Variable helper chips
                Text(
                    text = stringRes(R.string.snippets_insert_variable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val variables = listOf(
                        "\$1", "\$2", "\${1:default}", "\$0",
                        "\$TM_FILENAME", "\$TM_FILENAME_BASE", "\$PACKAGE", "\$CLASSNAME",
                        "\$CURRENT_YEAR", "\$CLIPBOARD"
                    )
                    variables.forEach { variable ->
                        AssistChip(
                            onClick = { onEvent(SnippetSettingsUiEvent.InsertSnippetVariable(variable)) },
                            label = {
                                Text(
                                    text = variable,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Template Body (Monospace)
                OutlinedTextField(
                    value = uiState.editBody,
                    onValueChange = { onEvent(SnippetSettingsUiEvent.EditBodyChanged(it)) },
                    label = { Text(stringRes(R.string.snippets_body)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Syntax-Validierung
                when (validation) {
                    SnippetValidationResult.Valid -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringRes(R.string.snippets_syntax_valid),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is SnippetValidationResult.Invalid -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Res.string(
                                    R.string.snippets_syntax_error,
                                    validation.line,
                                    validation.column,
                                    validation.error
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onEvent(SnippetSettingsUiEvent.DismissEditDialog) }) {
                        Text(stringRes(R.string.common_abbrechen))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onEvent(SnippetSettingsUiEvent.SaveEditSnippet) },
                        enabled = isValid && uiState.editPrefix.isNotBlank() && uiState.editName.isNotBlank()
                    ) {
                        Text(stringRes(R.string.common_speichern))
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetImportExportDialog(
    uiState: SnippetSettingsUiState,
    onEvent: (SnippetSettingsUiEvent) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val isExport = uiState.importExportMode == ImportExportMode.EXPORT

    AlertDialog(
        onDismissRequest = { onEvent(SnippetSettingsUiEvent.DismissImportExportDialog) },
        title = {
            Text(stringRes(if (isExport) R.string.snippets_export_title else R.string.snippets_import_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isExport) {
                    Text(
                        text = stringRes(R.string.snippets_export_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringRes(R.string.snippets_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.importExportText,
                    onValueChange = { onEvent(SnippetSettingsUiEvent.ImportExportTextChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    readOnly = isExport,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                if (isExport) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.importExportText))
                            onEvent(SnippetSettingsUiEvent.DismissUserMessage)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringRes(R.string.editor_kopieren))
                    }
                }
            }
        },
        confirmButton = {
            if (isExport) {
                Button(onClick = { onEvent(SnippetSettingsUiEvent.DismissImportExportDialog) }) {
                    Text(stringRes(R.string.pw_ok))
                }
            } else {
                Button(
                    onClick = { onEvent(SnippetSettingsUiEvent.ConfirmImport) },
                    enabled = uiState.importExportText.isNotBlank()
                ) {
                    Text(stringRes(R.string.snippets_import))
                }
            }
        },
        dismissButton = {
            if (!isExport) {
                TextButton(onClick = { onEvent(SnippetSettingsUiEvent.DismissImportExportDialog) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        }
    )
}
