/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.editor.fileprovider.EditorFileProvider
import com.codeforge.feature.editor.overlay.UpdateChipDialog
import com.codeforge.libs.code_tools.edit.LineEdits
import com.codeforge.libs.code_tools.format.CodeFormatter
import com.codeforge.libs.code_tools.format.FormatOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun EditorRoute(
    modifier: Modifier = Modifier,
    /** Projektwurzel des Workspaces — aktiviert Dependency-Update-Chips im Versionskatalog. */
    projectRootPath: String? = null,
    onNavigate: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var controller by remember { mutableStateOf<EditorController?>(null) }

    val scope = rememberCoroutineScope()
    var showGoToLine by remember { mutableStateOf(false) }

    LaunchedEffect(projectRootPath) { viewModel.onEvent(EditorUiEvent.SetProjectRoot(projectRootPath)) }

    // Sprung aus der Projektsuche: warten, bis die Zieldatei aktiv UND ihr Text im Widget angekommen ist.
    val activePath = uiState.activeFile?.path
    val activeContent = uiState.activeFile?.content
    LaunchedEffect(uiState.pendingJump, activePath, activeContent, controller) {
        val jump = uiState.pendingJump ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        if (activePath != jump.path || activeContent == null) return@LaunchedEffect
        var tries = 0
        while (c.currentText() != activeContent && tries++ < 20) delay(25)
        c.jumpToMatch(jump.line - 1, jump.column - 1, jump.length)
        viewModel.onEvent(EditorUiEvent.JumpHandled)
    }

    fun formatDocument() {
        val c = controller ?: return
        val path = uiState.activeFile?.path ?: return
        val text = c.currentText() ?: return
        val tab = uiState.displaySettings.tabSize
        val result = CodeFormatter.format(text, CodeFormatter.languageFor(path), FormatOptions(indentSize = tab))
        c.applyTextChange(result.text)
        scope.launch {
            snackbarHostState.showSnackbar(
                result.warning ?: if (result.text == text) Res.string(R.string.editor_bereits_formatiert) else Res.string(R.string.editor_formatiert)
            )
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            ScrollableTabRow(selectedTabIndex = uiState.activeFileIndex) {
                uiState.openFiles.forEachIndexed { index, file ->
                    Tab(
                        selected = index == uiState.activeFileIndex,
                        onClick = { viewModel.onEvent(EditorUiEvent.SelectTab(index)) },
                        text = {
                            Text(
                                (if (file.isDirty) "• " else "") + file.path.substringAfterLast('/')
                            )
                        }
                    )
                }
            }

            EditorToolbar(
                controller = controller,
                activePath = uiState.activeFile?.path,
                isLspConnected = uiState.isLspConnected,
                tabSize = uiState.displaySettings.tabSize,
                onFormat = ::formatDocument,
                onFormatLsp = { viewModel.onEvent(EditorUiEvent.RunLspFormat) },
                onGoToLine = { showGoToLine = true },
                onSave = { viewModel.onEvent(EditorUiEvent.Save) },
                onToggleSearch = { viewModel.onEvent(EditorUiEvent.ToggleSearchBar) },
                onShare = {
                    uiState.activeFile?.let { active ->
                        context.startActivity(EditorFileProvider.buildShareIntent(context, active.path))
                    }
                }
            )

            if (uiState.isSearchBarVisible) {
                EditorSearchBar(uiState = uiState, onEvent = viewModel::onEvent, controller = controller)
            }

            uiState.activeFile?.let { active ->
                SoraCodeEditor(
                    modifier = Modifier.fillMaxSize(),
                    path = active.path,
                    content = active.content,
                    language = EditorLanguageType.fromPath(active.path),
                    settings = uiState.displaySettings,
                    diagnostics = uiState.activeDiagnostics,
                    completionProvider = viewModel.completionProvider,
                    updateAnnotations = uiState.activeUpdateAnnotations,
                    onUpdateChipClick = { viewModel.onEvent(EditorUiEvent.UpdateChipClicked(it)) },
                    onContentChanged = { text -> viewModel.onEvent(EditorUiEvent.TextChanged(text)) },
                    onControllerReady = { controller = it }
                )
            }
        }
    }

    if (showGoToLine) {
        GoToLineDialog(
            lineCount = uiState.activeFile?.content?.count { it == '\n' }?.plus(1) ?: 1,
            onDismiss = { showGoToLine = false },
            onGo = { line -> controller?.jumpToLine(line - 1); showGoToLine = false }
        )
    }

    uiState.updateDialog?.let { update ->
        UpdateChipDialog(
            update = update,
            totalPending = uiState.pendingUpdateCount,
            isApplying = uiState.isApplyingUpdate,
            onUpdate = { viewModel.onEvent(EditorUiEvent.ApplyUpdate(update)) },
            onUpdateAll = { viewModel.onEvent(EditorUiEvent.ApplyAllUpdates) },
            onCancel = { viewModel.onEvent(EditorUiEvent.UpdateDialogCancel) }
        )
    }
}

@Composable
private fun EditorToolbar(
    modifier: Modifier = Modifier,
    controller: EditorController?,
    activePath: String?,
    isLspConnected: Boolean,
    tabSize: Int,
    onFormat: () -> Unit,
    onFormatLsp: () -> Unit,
    onGoToLine: () -> Unit,
    onSave: () -> Unit,
    onToggleSearch: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        IconButton(onClick = { controller?.undo() }, enabled = controller?.canUndo() ?: false) {
            Icon(Icons.Filled.Undo, contentDescription = stringRes(R.string.editor_rueckgaengig))
        }
        IconButton(onClick = { controller?.redo() }, enabled = controller?.canRedo() ?: false) {
            Icon(Icons.Filled.Redo, contentDescription = stringRes(R.string.editor_wiederholen))
        }
        IconButton(onClick = { controller?.selectAll() }) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringRes(R.string.editor_alles_auswaehlen))
        }
        IconButton(onClick = { controller?.cut() }) {
            Icon(Icons.Filled.ContentCut, contentDescription = stringRes(R.string.editor_ausschneiden))
        }
        IconButton(onClick = { controller?.copy() }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = stringRes(R.string.editor_kopieren))
        }
        IconButton(onClick = { controller?.paste() }) {
            Icon(Icons.Filled.ContentPaste, contentDescription = stringRes(R.string.editor_einfuegen))
        }
        IconButton(onClick = onSave) {
            Icon(Icons.Filled.Save, contentDescription = stringRes(R.string.common_speichern))
        }
        IconButton(onClick = onFormat) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringRes(R.string.editor_formatieren))
        }
        if (isLspConnected) {
            IconButton(onClick = onFormatLsp) {
                Icon(Icons.Filled.FormatAlignLeft, contentDescription = stringRes(R.string.editor_formatieren_lsp))
            }
        }
        val unit = " ".repeat(tabSize.coerceIn(1, 8))
        val commentStyle = activePath?.let(LineEdits::commentStyleFor)
        IconButton(
            onClick = { commentStyle?.let { style -> controller?.runLineCommand { t, a, b -> LineEdits.toggleComment(t, a, b, style) } } },
            enabled = commentStyle != null
        ) {
            Icon(Icons.Filled.Comment, contentDescription = stringRes(R.string.editor_kommentar_umschalten))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.indent(t, a, b, unit) } }) {
            Icon(Icons.Filled.FormatIndentIncrease, contentDescription = stringRes(R.string.editor_einruecken))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.dedent(t, a, b, unit) } }) {
            Icon(Icons.Filled.FormatIndentDecrease, contentDescription = stringRes(R.string.editor_ausruecken))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.duplicateLines(t, a, b) } }) {
            Icon(Icons.Filled.ControlPointDuplicate, contentDescription = stringRes(R.string.editor_zeile_duplizieren))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.deleteLines(t, a, b) } }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = stringRes(R.string.editor_zeile_loeschen))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.moveLines(t, a, b, up = true) } }) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringRes(R.string.editor_zeile_nach_oben))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.moveLines(t, a, b, up = false) } }) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringRes(R.string.editor_zeile_nach_unten))
        }
        IconButton(onClick = onGoToLine) {
            Icon(Icons.Filled.Numbers, contentDescription = stringRes(R.string.editor_gehe_zu_zeile))
        }
        IconButton(onClick = onToggleSearch) {
            Icon(Icons.Filled.Search, contentDescription = "Suchen/Ersetzen")
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = stringRes(R.string.editor_teilen))
        }
    }
}

@Composable
private fun GoToLineDialog(lineCount: Int, onDismiss: () -> Unit, onGo: (line: Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    val line = input.toIntOrNull()?.takeIf { it in 1..lineCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.editor_gehe_zu_zeile)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(7) },
                singleLine = true,
                label = { Text("1 – $lineCount") },
                isError = input.isNotEmpty() && line == null
            )
        },
        confirmButton = { TextButton(onClick = { line?.let(onGo) }, enabled = line != null) { Text(stringRes(R.string.editor_los)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } }
    )
}
