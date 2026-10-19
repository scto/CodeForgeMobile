/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.editor.fileprovider.EditorFileProvider
import com.codeforge.feature.editor.overlay.UpdateChipDialog
import com.codeforge.feature.editor.ui.DocumentOutlineSheet
import com.codeforge.feature.editor.ui.EditorSymbolBar
import com.codeforge.feature.editor.ui.ImageFilePreview
import com.codeforge.feature.editor.ui.QuickFixSheet
import com.codeforge.feature.editor.ui.ReferencesSheet
import com.codeforge.feature.editor.ui.RenameDialog
import com.codeforge.feature.editor.ui.isImageFilePath
import com.codeforge.libs.code_tools.edit.LineEdits
import com.codeforge.libs.code_tools.format.CodeFormatter
import com.codeforge.libs.code_tools.format.FormatOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    fun formatCode() {
        val range = controller?.getSelectionLspRange()
        viewModel.onEvent(EditorUiEvent.FormatCode(range))
    }

    fun triggerQuickFix() {
        val pos = controller?.getCursorLineAndColumn() ?: (0 to 0)
        viewModel.onEvent(EditorUiEvent.TriggerQuickFix(pos.first, pos.second))
    }

    fun triggerRename() {
        val pos = controller?.getCursorLineAndColumn() ?: (0 to 0)
        val word = controller?.getWordAtCursor() ?: ""
        viewModel.onEvent(EditorUiEvent.TriggerRename(pos.first, pos.second, word))
    }

    fun triggerDefinition() {
        val pos = controller?.getCursorLineAndColumn() ?: (0 to 0)
        viewModel.onEvent(EditorUiEvent.GoToDefinition(pos.first, pos.second))
    }

    fun triggerReferences() {
        val pos = controller?.getCursorLineAndColumn() ?: (0 to 0)
        val word = controller?.getWordAtCursor() ?: ""
        viewModel.onEvent(EditorUiEvent.FindReferences(pos.first, pos.second, word))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is EditorUiEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        effect.onAction?.invoke()
                    }
                }
                is EditorUiEffect.ApplyFormattedText -> {
                    controller?.applyTextChange(effect.formattedText)
                }
                is EditorUiEffect.NavigateTo -> onNavigate(effect.route)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Alt+Enter Shortcut für Quick-Fix
                if (event.isAltPressed && event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                    triggerQuickFix()
                    true
                } else false
            },
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
                lspServerState = uiState.lspServerState,
                capabilities = uiState.serverCapabilities,
                hasDiagnostics = uiState.activeDiagnostics.isNotEmpty(),
                tabSize = uiState.displaySettings.tabSize,
                onFormat = ::formatCode,
                onGoToLine = { showGoToLine = true },
                onSave = { viewModel.onEvent(EditorUiEvent.Save) },
                onToggleSearch = { viewModel.onEvent(EditorUiEvent.ToggleSearchBar) },
                onShare = {
                    uiState.activeFile?.let { active ->
                        context.startActivity(EditorFileProvider.buildShareIntent(context, active.path))
                    }
                },
                onQuickFix = ::triggerQuickFix,
                onRename = ::triggerRename,
                onOutline = { viewModel.onEvent(EditorUiEvent.ToggleOutline) },
                onDefinition = ::triggerDefinition,
                onReferences = ::triggerReferences
            )

            if (uiState.isSearchBarVisible) {
                EditorSearchBar(uiState = uiState, onEvent = viewModel::onEvent, controller = controller)
            }

            uiState.activeFile?.let { active ->
                if (isImageFilePath(active.path)) {
                    ImageFilePreview(
                        filePath = active.path,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SoraCodeEditor(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
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

                        if (WindowInsets.isImeVisible) {
                            EditorSymbolBar(
                                filePath = active.path,
                                onInsertText = { text ->
                                    controller?.insertText(text)
                                    viewModel.onEvent(EditorUiEvent.InsertText(text))
                                }
                            )
                        }
                    }
                }
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

    // Task 31 Sheets & Dialogs
    if (uiState.isCodeActionsSheetVisible) {
        QuickFixSheet(
            actions = uiState.codeActions,
            onSelectAction = { viewModel.onEvent(EditorUiEvent.ApplyCodeAction(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.DismissQuickFix) }
        )
    }

    uiState.renameDialog?.let { renameState ->
        RenameDialog(
            state = renameState,
            onNameChanged = { viewModel.onEvent(EditorUiEvent.RenameNameChanged(it)) },
            onRequestPreview = { viewModel.onEvent(EditorUiEvent.RequestRenamePreview) },
            onConfirm = { viewModel.onEvent(EditorUiEvent.ConfirmRename) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.DismissRename) }
        )
    }

    if (uiState.isOutlineVisible) {
        DocumentOutlineSheet(
            symbols = uiState.filteredOutlineSymbols,
            filter = uiState.outlineFilter,
            isLoading = uiState.isLoadingOutline,
            onFilterChanged = { viewModel.onEvent(EditorUiEvent.OutlineFilterChanged(it)) },
            onSelectSymbol = { viewModel.onEvent(EditorUiEvent.SelectOutlineSymbol(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.ToggleOutline) }
        )
    }

    if (uiState.isReferencesSheetVisible) {
        ReferencesSheet(
            symbolName = uiState.referencesSymbol,
            locations = uiState.referencesList,
            onSelectLocation = { viewModel.onEvent(EditorUiEvent.SelectLocation(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.DismissReferences) }
        )
    }
}

@Composable
private fun EditorToolbar(
    modifier: Modifier = Modifier,
    controller: EditorController?,
    activePath: String?,
    isLspConnected: Boolean,
    lspServerState: LspServerState,
    capabilities: LspServerCapabilities?,
    hasDiagnostics: Boolean,
    tabSize: Int,
    onFormat: () -> Unit,
    onGoToLine: () -> Unit,
    onSave: () -> Unit,
    onToggleSearch: () -> Unit,
    onShare: () -> Unit,
    onQuickFix: () -> Unit,
    onRename: () -> Unit,
    onOutline: () -> Unit,
    onDefinition: () -> Unit,
    onReferences: () -> Unit
) {
    val isImage = activePath?.let { isImageFilePath(it) } == true
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LspStatusBadge(
            state = lspServerState,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp)
        )

        // Outline / Struktur: immer verfügbar (LSP oder Fallback)
        if (!isImage) {
            IconButton(onClick = onOutline) {
                Icon(Icons.Default.AccountTree, contentDescription = stringRes(R.string.lsp_outline))
            }
        }

        // Quick-Fix (Glühbirne): wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowCodeAction = isLspConnected && (capabilities == null || capabilities.codeActionProvider)
        if (allowCodeAction && !isImage) {
            IconButton(onClick = onQuickFix) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = stringRes(R.string.lsp_quick_fix),
                    tint = if (hasDiagnostics) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Rename (Umbenennen): wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowRename = isLspConnected && (capabilities == null || capabilities.renameProvider)
        if (allowRename && !isImage) {
            IconButton(onClick = onRename) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringRes(R.string.lsp_rename))
            }
        }

        // Definition: wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowDefinition = isLspConnected && (capabilities == null || capabilities.definitionProvider)
        if (allowDefinition && !isImage) {
            IconButton(onClick = onDefinition) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringRes(R.string.lsp_definition))
            }
        }

        // References (Verwendungen): wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowReferences = isLspConnected && (capabilities == null || capabilities.referencesProvider)
        if (allowReferences && !isImage) {
            IconButton(onClick = onReferences) {
                Icon(Icons.Default.FindInPage, contentDescription = stringRes(R.string.lsp_references))
            }
        }

        IconButton(onClick = { controller?.undo() }, enabled = !isImage && (controller?.canUndo() ?: false)) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringRes(R.string.editor_rueckgaengig))
        }
        IconButton(onClick = { controller?.redo() }, enabled = !isImage && (controller?.canRedo() ?: false)) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringRes(R.string.editor_wiederholen))
        }
        IconButton(onClick = { controller?.selectAll() }, enabled = !isImage) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringRes(R.string.editor_alles_auswaehlen))
        }
        IconButton(onClick = { controller?.cut() }, enabled = !isImage) {
            Icon(Icons.Filled.ContentCut, contentDescription = stringRes(R.string.editor_ausschneiden))
        }
        IconButton(onClick = { controller?.copy() }, enabled = !isImage) {
            Icon(Icons.Filled.ContentCopy, contentDescription = stringRes(R.string.editor_kopieren))
        }
        IconButton(onClick = { controller?.paste() }, enabled = !isImage) {
            Icon(Icons.Filled.ContentPaste, contentDescription = stringRes(R.string.editor_einfuegen))
        }
        IconButton(onClick = onSave, enabled = !isImage) {
            Icon(Icons.Filled.Save, contentDescription = stringRes(R.string.common_speichern))
        }
        val hasSelection = controller?.hasSelection() == true
        val formatDesc = if (hasSelection) stringRes(R.string.editor_format_selection) else stringRes(R.string.editor_format_document)
        IconButton(onClick = onFormat, enabled = !isImage) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = formatDesc)
        }
        val unit = " ".repeat(tabSize.coerceIn(1, 8))
        val commentStyle = if (isImage) null else activePath?.let(LineEdits::commentStyleFor)
        IconButton(
            onClick = { commentStyle?.let { style -> controller?.runLineCommand { t, a, b -> LineEdits.toggleComment(t, a, b, style) } } },
            enabled = commentStyle != null
        ) {
            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = stringRes(R.string.editor_kommentar_umschalten))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.indent(t, a, b, unit) } }) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = stringRes(R.string.editor_einruecken))
        }
        IconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.dedent(t, a, b, unit) } }) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = stringRes(R.string.editor_ausruecken))
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
            Icon(Icons.Filled.Search, contentDescription = stringRes(R.string.editor_suchen_ersetzen))
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

@Composable
private fun LspStatusBadge(
    state: LspServerState,
    modifier: Modifier = Modifier
) {
    if (state == LspServerState.STOPPED) return

    val (bgColor, textColor, textRes) = when (state) {
        LspServerState.RUNNING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            R.string.editor_lsp_status_running
        )
        LspServerState.STARTING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            R.string.editor_lsp_status_starting
        )
        LspServerState.FAILED, LspServerState.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            R.string.editor_lsp_status_failed
        )
        LspServerState.STOPPED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.editor_lsp_status_stopped
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = stringRes(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
