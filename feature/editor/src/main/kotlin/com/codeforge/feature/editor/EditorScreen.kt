// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditorRoute(
    modifier: Modifier = Modifier,
    projectPath: String? = null,
    onNavigate: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Öffne Projektpfad einmalig beim Start
    LaunchedEffect(projectPath) {
        if (!projectPath.isNullOrBlank()) {
            viewModel.onEvent(EditorUiEvent.OpenFile(projectPath))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is EditorUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is EditorUiEffect.NavigateTo -> onNavigate(effect.route)
            }
        }
    }

    EditorScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    uiState: EditorUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (EditorUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                uiState = uiState,
                onEvent = onEvent
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tab-Leiste ──────────────────────────────────────────────
            if (uiState.openFiles.isNotEmpty()) {
                EditorTabRow(
                    openFiles = uiState.openFiles,
                    activeIndex = uiState.activeFileIndex,
                    onSelectTab = { onEvent(EditorUiEvent.SelectTab(it)) },
                    onCloseTab = { onEvent(EditorUiEvent.CloseTab(it)) }
                )
            }

            // ── Inhalt ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.openFiles.isEmpty() -> {
                        EmptyEditorPlaceholder(modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        val active = uiState.openFiles[uiState.activeFileIndex]
                        SoraCodeEditor(
                            modifier = Modifier.fillMaxSize(),
                            content = active.content,
                            language = EditorLanguageType.fromPath(active.path),
                            onContentChanged = { text -> onEvent(EditorUiEvent.TextChanged(text)) }
                        )
                    }
                }

                // LSP-Spinner (oben rechts über dem Editor)
                if (uiState.isLspLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // ── Diagnostik-Leiste ──────────────────────────────────────
            if (uiState.diagnostics.isNotEmpty()) {
                DiagnosticsBar(diagnostics = uiState.diagnostics)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    uiState: EditorUiState,
    onEvent: (EditorUiEvent) -> Unit
) {
    val activeFile = uiState.openFiles.getOrNull(uiState.activeFileIndex)
    val title = activeFile?.path?.substringAfterLast('/') ?: "Editor"
    val subtitle = activeFile?.path?.substringBeforeLast('/') ?: ""

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Undo
            IconButton(
                onClick = { onEvent(EditorUiEvent.UndoClicked) },
                enabled = uiState.canUndo
            ) {
                Icon(Icons.Default.Undo, contentDescription = "Rückgängig")
            }
            // Redo
            IconButton(
                onClick = { onEvent(EditorUiEvent.RedoClicked) },
                enabled = uiState.canRedo
            ) {
                Icon(Icons.Default.Redo, contentDescription = "Wiederholen")
            }
            // LSP Format
            IconButton(
                onClick = { onEvent(EditorUiEvent.RunLspFormat) },
                enabled = uiState.isLspConnected && activeFile != null
            ) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = "LSP Formatieren")
            }
            // Speichern
            IconButton(
                onClick = { onEvent(EditorUiEvent.SaveFile) },
                enabled = uiState.openFiles.getOrNull(uiState.activeFileIndex)?.isDirty == true
            ) {
                Icon(Icons.Default.Save, contentDescription = "Speichern")
            }
            // Alle speichern
            IconButton(
                onClick = { onEvent(EditorUiEvent.SaveAllFiles) },
                enabled = uiState.openFiles.any { it.isDirty }
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = "Alle speichern")
            }
        }
    )
}

@Composable
private fun EditorTabRow(
    openFiles: List<OpenFile>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp
    ) {
        openFiles.forEachIndexed { index, file ->
            Tab(
                selected = index == activeIndex,
                onClick = { onSelectTab(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Dirty-Indikator
                        if (file.isDirty) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Nicht gespeichert",
                                modifier = Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = file.path.substringAfterLast('/'),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Close-Button
                        IconButton(
                            onClick = { onCloseTab(index) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tab schließen",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyEditorPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Keine Datei geöffnet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Öffne eine Datei über den Datei-Explorer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DiagnosticsBar(diagnostics: List<LspDiagnostic>) {
    val errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (errors > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text("$errors", color = MaterialTheme.colorScheme.onError)
            }
            Text(
                text = if (errors == 1) "Fehler" else "Fehler",
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (warnings > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                Text("$warnings")
            }
            Text(
                text = if (warnings == 1) "Warnung" else "Warnungen",
                style = MaterialTheme.typography.labelSmall
            )
        }
        val first = diagnostics.first()
        Text(
            text = "Zeile ${first.line}: ${first.message}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
