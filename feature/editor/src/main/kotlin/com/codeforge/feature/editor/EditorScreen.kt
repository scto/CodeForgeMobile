/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import com.codeforge.core.designsystem.OptionalThemedFileIcon
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.codeforge.core.domain.model.LspSessionInfo
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.TooltipIconButton
import com.codeforge.feature.editor.fileprovider.EditorFileProvider
import com.codeforge.feature.editor.overlay.ConflictChoice
import com.codeforge.feature.editor.overlay.MergeConflicts
import com.codeforge.feature.editor.overlay.UpdateChipDialog
import com.codeforge.feature.editor.ui.AiAssistantSheet
import com.codeforge.feature.editor.ui.AiDiffDialog
import com.codeforge.feature.editor.ui.AiQuickActionsSheet
import com.codeforge.feature.editor.ui.AiSettingsDialog
import com.codeforge.feature.editor.ui.BreakpointsOverviewSheet
import com.codeforge.feature.editor.ui.ConflictBar
import com.codeforge.feature.editor.ui.DocumentOutlineSheet
import com.codeforge.feature.editor.ui.EditBreakpointDialog
import com.codeforge.feature.editor.ui.EditorSymbolBar
import com.codeforge.feature.editor.ui.HoverSheet
import com.codeforge.feature.editor.ui.ImageFilePreview
import com.codeforge.feature.editor.ui.QuickFixSheet
import com.codeforge.feature.editor.ui.ReferencesSheet
import com.codeforge.feature.editor.ui.RenameDialog
import com.codeforge.feature.editor.ui.SignatureHelpBar
import com.codeforge.feature.editor.ui.isImageFilePath
import com.codeforge.libs.code_tools.edit.LineEdits
import com.codeforge.libs.code_tools.format.CodeFormatter
import com.codeforge.libs.code_tools.format.FormatOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorRoute(
    modifier: Modifier = Modifier,
    /** Projektwurzel des Workspaces — aktiviert Dependency-Update-Chips im Versionskatalog. */
    projectRootPath: String? = null,
    onNavigate: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pluginCommands by viewModel.pluginCommands.collectAsState()
    val pluginMenuEntries by viewModel.pluginMenuEntries.collectAsState()
    val pluginToolbarItems = remember(pluginCommands, pluginMenuEntries) {
        buildPluginToolbarItems(pluginCommands, pluginMenuEntries)
    }
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

    fun cursorPos() = controller?.getCursorLineAndColumn() ?: (0 to 0)

    // Signaturhilfe automatisch bei „(“ und „,“ anzeigen, bei „)“ ausblenden.
    LaunchedEffect(activeContent, controller) {
        val c = controller ?: return@LaunchedEffect
        val text = activeContent ?: return@LaunchedEffect
        delay(200)
        val (line, col) = c.getCursorLineAndColumn() ?: return@LaunchedEffect
        val typed = text.lineSequence().elementAtOrNull(line)?.getOrNull(col - 1)
        when (typed) {
            '(', ',' -> viewModel.onEvent(EditorUiEvent.RequestSignatureHelp(line, col))
            ')' -> viewModel.onEvent(EditorUiEvent.DismissSignatureHelp)
            else -> Unit
        }
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

            var tabMenuIndex by remember { mutableStateOf<Int?>(null) }
            if (uiState.openFiles.isNotEmpty()) {
                val safeTabIndex = uiState.activeFileIndex.coerceIn(0, uiState.openFiles.lastIndex)
                // Beim Öffnen einer neuen Datei ist tabPositions im selben Frame noch veraltet
                // (Länge n, Index n) -> Standard-Indikator crasht. Eigener Indikator mit Bounds-Check.
                ScrollableTabRow(
                    selectedTabIndex = safeTabIndex,
                    indicator = { tabPositions ->
                        tabPositions.getOrNull(safeTabIndex)?.let { position ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(position)
                            )
                        }
                    }
                ) {
                    uiState.openFiles.forEachIndexed { index, file ->
                        // Eigener Tab statt Material-Tab: Long-Click (Kontextmenü) geht mit
                        // Tab/selectable nicht zuverlässig neben onClick.
                        Box {
                            val selected = index == safeTabIndex
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .combinedClickable(
                                        onClick = { viewModel.onEvent(EditorUiEvent.SelectTab(index)) },
                                        onLongClick = { tabMenuIndex = index }
                                    )
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OptionalThemedFileIcon(
                                        file.path.substringAfterLast('/'),
                                        Modifier.padding(end = 6.dp).size(16.dp)
                                    )
                                    Text(
                                        (if (file.isDirty) "• " else "") + file.path.substringAfterLast('/'),
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = tabMenuIndex == index,
                                onDismissRequest = { tabMenuIndex = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringRes(R.string.editor_tab_close)) },
                                    onClick = { tabMenuIndex = null; viewModel.onEvent(EditorUiEvent.CloseTab(index)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringRes(R.string.editor_tab_close_others)) },
                                    enabled = uiState.openFiles.size > 1,
                                    onClick = { tabMenuIndex = null; viewModel.onEvent(EditorUiEvent.CloseOtherTabs(index)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringRes(R.string.editor_tab_close_all)) },
                                    onClick = { tabMenuIndex = null; viewModel.onEvent(EditorUiEvent.CloseAllTabs) }
                                )
                            }
                        }
                    }
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
                onReferences = ::triggerReferences,
                onHover = { cursorPos().let { (l, c) -> viewModel.onEvent(EditorUiEvent.ShowHover(l, c)) } },
                onTypeDefinition = { cursorPos().let { (l, c) -> viewModel.onEvent(EditorUiEvent.GoToTypeDefinition(l, c)) } },
                onImplementation = {
                    cursorPos().let { (l, c) ->
                        viewModel.onEvent(
                            EditorUiEvent.GoToImplementation(l, c, controller?.getWordAtCursor().orEmpty())
                        )
                    }
                },
                onSignatureHelp = { cursorPos().let { (l, c) -> viewModel.onEvent(EditorUiEvent.RequestSignatureHelp(l, c)) } },
                onToggleLspSessions = { viewModel.onEvent(EditorUiEvent.ToggleLspSessionsDialog) },
                hasBreakpoints = uiState.activeBreakpoints.isNotEmpty(),
                onBreakpoints = { viewModel.onEvent(EditorUiEvent.ShowBreakpointsOverview(true)) },
                onOpenAiAssistant = { viewModel.onEvent(EditorUiEvent.ToggleAiAssistant(true)) },
                onOpenAiQuickActions = { viewModel.onEvent(EditorUiEvent.ToggleAiQuickActions(true)) },
                pluginItems = pluginToolbarItems,
                onPluginCommand = { viewModel.onEvent(EditorUiEvent.RunPluginCommand(it.pluginId, it.commandId)) }
            )

            if (uiState.isSearchBarVisible) {
                EditorSearchBar(uiState = uiState, onEvent = viewModel::onEvent, controller = controller)
            }

            if (uiState.openFiles.isEmpty()) {
                EditorEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                uiState.activeFile?.let { active ->
                    if (isImageFilePath(active.path)) {
                        ImageFilePreview(
                            filePath = active.path,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // key(path): jede Datei bekommt eigenen CodeEditor + EditorController + Undo-Stack.
                        // Ohne key wurde die AndroidView beim Tab-Wechsel wiederverwendet, der neue Controller
                        // nie attached und der alte detached -> Undo/Redo/Format tot.
                        key(active.path) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val conflicts = remember(active.content, uiState.conflictDetectionEnabled) {
                                if (uiState.conflictDetectionEnabled) MergeConflicts.scan(active.content) else emptyList()
                            }
                            var conflictIndex by remember(active.path) { mutableStateOf(0) }
                            val safeIndex = conflictIndex.coerceIn(0, (conflicts.size - 1).coerceAtLeast(0))
                            fun resolveCurrent(choice: ConflictChoice) {
                                val block = conflicts.getOrNull(safeIndex) ?: return
                                controller?.applyTextChange(MergeConflicts.resolve(active.content, block, choice))
                            }
                            ConflictBar(
                                conflicts = conflicts,
                                index = safeIndex,
                                onKeepOurs = { resolveCurrent(ConflictChoice.OURS) },
                                onKeepTheirs = { resolveCurrent(ConflictChoice.THEIRS) },
                                onKeepBoth = { resolveCurrent(ConflictChoice.BOTH) },
                                onNext = {
                                    val next = (safeIndex + 1) % conflicts.size
                                    conflictIndex = next
                                    controller?.jumpToLine(conflicts[next].startLine)
                                }
                            )
                            uiState.signatureHelp?.let { SignatureHelpBar(help = it) }
                            SoraCodeEditor(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                path = active.path,
                                content = active.content,
                                language = EditorLanguageType.fromPath(active.path),
                                settings = uiState.displaySettings,
                                diagnostics = uiState.activeDiagnostics,
                                completionProvider = viewModel.completionProvider,
                                updateAnnotations = uiState.activeUpdateAnnotations,
                                breakpoints = uiState.activeBreakpoints,
                                gitChanges = uiState.activeGitChanges,
                                conflicts = conflicts,
                                pausedLine = uiState.activePausedLine,
                                onToggleBreakpoint = { viewModel.onEvent(EditorUiEvent.ToggleBreakpoint(it)) },
                                onEditBreakpoint = { viewModel.onEvent(EditorUiEvent.EditBreakpoint(it)) },
                                onUpdateChipClick = { viewModel.onEvent(EditorUiEvent.UpdateChipClicked(it)) },
                                onTestTargetClick = { viewModel.onEvent(EditorUiEvent.RunTestTarget(it)) },
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

    uiState.hoverText?.let { text ->
        HoverSheet(text = text, onDismiss = { viewModel.onEvent(EditorUiEvent.DismissHover) })
    }

    if (uiState.isReferencesSheetVisible) {
        ReferencesSheet(
            symbolName = uiState.referencesSymbol,
            locations = uiState.referencesList,
            onSelectLocation = { viewModel.onEvent(EditorUiEvent.SelectLocation(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.DismissReferences) }
        )
    }

    if (uiState.isLspSessionsDialogVisible) {
        LspSessionsDialog(
            sessions = uiState.runningLspSessions,
            onRestart = { viewModel.onEvent(EditorUiEvent.RestartLspSession(it)) },
            onStop = { viewModel.onEvent(EditorUiEvent.StopLspSession(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.ToggleLspSessionsDialog) }
        )
    }

    if (uiState.editingBreakpoint != null) {
        EditBreakpointDialog(
            breakpoint = uiState.editingBreakpoint!!,
            onSave = { viewModel.onEvent(EditorUiEvent.SaveBreakpoint(it)) },
            onDelete = { viewModel.onEvent(EditorUiEvent.DeleteBreakpoint(it)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.DismissBreakpointEdit) }
        )
    }

    if (uiState.isBreakpointsOverviewVisible) {
        BreakpointsOverviewSheet(
            breakpointsByPath = uiState.breakpointsByPath,
            onBreakpointClick = { path, line ->
                viewModel.onEvent(EditorUiEvent.OpenFile(path))
                controller?.jumpToLine(line - 1)
                viewModel.onEvent(EditorUiEvent.ShowBreakpointsOverview(false))
            },
            onDeleteBreakpoint = { viewModel.onEvent(EditorUiEvent.DeleteBreakpoint(it)) },
            onDeleteAll = { viewModel.onEvent(EditorUiEvent.DeleteAllBreakpoints) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.ShowBreakpointsOverview(false)) }
        )
    }

    if (uiState.isAiAssistantOpen) {
        AiAssistantSheet(
            history = uiState.aiHistory,
            isStreaming = uiState.isAiStreaming,
            streamingText = uiState.aiStreamingText,
            tokenUsage = uiState.aiTokensUsage,
            preparedContext = uiState.aiPreparedContext,
            selectedSources = uiState.aiSelectedContextSources,
            onToggleSource = { viewModel.onEvent(EditorUiEvent.ToggleAiContextSource(it)) },
            onSendMessage = { prompt ->
                val selected = controller?.hasSelection() == true
                val selText = if (selected) controller?.let { c ->
                    val (s, e) = c.selection() ?: (0 to 0)
                    c.currentText()?.substring(s, e)
                } else null
                viewModel.onEvent(EditorUiEvent.SendAiPrompt(prompt, selText))
            },
            onStopStreaming = { viewModel.onEvent(EditorUiEvent.StopAiStream) },
            onClearHistory = { viewModel.onEvent(EditorUiEvent.ClearAiHistory) },
            onOpenSettings = { viewModel.onEvent(EditorUiEvent.ToggleAiSettings(true)) },
            onInsertText = { text -> controller?.insertText(text) },
            onApplyDiff = { change -> viewModel.onEvent(EditorUiEvent.ShowAiDiff(change)) },
            onDismiss = { viewModel.onEvent(EditorUiEvent.ToggleAiAssistant(false)) }
        )
    }

    if (uiState.isAiQuickActionsOpen) {
        val selected = controller?.hasSelection() == true
        val selText = if (selected) controller?.let { c ->
            val (s, e) = c.selection() ?: (0 to 0)
            c.currentText()?.substring(s, e)
        } else null
        val cursorLine = controller?.getCursorLineAndColumn()?.first

        AiQuickActionsSheet(
            onDismiss = { viewModel.onEvent(EditorUiEvent.ToggleAiQuickActions(false)) },
            onActionSelected = { action ->
                viewModel.onEvent(EditorUiEvent.TriggerAiQuickAction(action, selText, cursorLine))
            },
            hasError = uiState.activeDiagnostics.isNotEmpty()
        )
    }

    if (uiState.isAiSettingsOpen) {
        AiSettingsDialog(
            initialOptions = uiState.aiChatOptions,
            initialPolicy = uiState.aiPrivacyPolicy,
            initialDailyLimit = uiState.aiDailyLimit,
            onDismiss = { viewModel.onEvent(EditorUiEvent.ToggleAiSettings(false)) },
            onSave = { opts, policy, limit ->
                viewModel.onEvent(EditorUiEvent.SaveAiOptions(opts))
                viewModel.onEvent(EditorUiEvent.SaveAiPrivacyPolicy(policy))
                viewModel.onEvent(EditorUiEvent.SaveAiDailyLimit(limit))
            },
            onTestConnection = { opts -> viewModel.testAiConnection(opts) }
        )
    }

    uiState.aiProposedDiff?.let { change ->
        AiDiffDialog(
            proposedChange = change,
            onAccept = { viewModel.onEvent(EditorUiEvent.AcceptAiProposedChange(change)) },
            onDiscard = { viewModel.onEvent(EditorUiEvent.DismissAiDiff) }
        )
    }

    uiState.aiFileConflictChange?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(EditorUiEvent.DismissAiFileConflict) },
            title = { Text(stringRes(R.string.ai_file_changed_warning)) },
            text = { Text(stringRes(R.string.ai_file_changed_message, conflict.filePath.substringAfterLast('/'))) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.AcceptAiProposedChange(conflict, forceOverwrite = true)) }) {
                    Text(stringRes(R.string.editor_los))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.DismissAiFileConflict) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }

    if (uiState.isAiPrivacyWarningVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(EditorUiEvent.DismissAiPrivacyWarning) },
            title = { Text(stringRes(R.string.ai_privacy_warning_title)) },
            text = { Text(stringRes(R.string.ai_privacy_warning_message, uiState.aiChatOptions.provider.displayName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.ConfirmAiPrivacyWarning) }) {
                    Text(stringRes(R.string.ai_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.DismissAiPrivacyWarning) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }

    if (uiState.isAiDailyLimitWarningVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(EditorUiEvent.DismissAiDailyLimit) },
            title = { Text(stringRes(R.string.ai_daily_limit)) },
            text = { Text(stringRes(R.string.ai_daily_limit_reached, uiState.aiDailyLimit)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.ConfirmAiDailyLimit) }) {
                    Text(stringRes(R.string.ai_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(EditorUiEvent.DismissAiDailyLimit) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }

    if (uiState.isSaveOnCloseDialogVisible) {
        com.codeforge.feature.editor.ui.SaveOnCloseDialog(
            dirtyFiles = uiState.dirtyFiles,
            saveErrors = uiState.saveOnCloseErrors,
            onSaveAll = { viewModel.onEvent(EditorUiEvent.SaveAllAndClose) },
            onDiscardAll = { viewModel.onEvent(EditorUiEvent.DiscardAllAndClose) },
            onCancel = { viewModel.onEvent(EditorUiEvent.DismissSaveOnCloseDialog) }
        )
    }
}

/** Ein Eintrag im Plugin-Menü der Editor-Leiste. */
internal data class PluginToolbarItem(val pluginId: String, val commandId: String, val title: String)

/** Menüeinträge für den Editor zuerst, danach alle übrigen registrierten Befehle (jeder Befehl bleibt erreichbar). */
internal fun buildPluginToolbarItems(
    commands: List<com.codeforge.core.domain.extension.PluginCommandInfo>,
    entries: List<com.codeforge.core.domain.extension.PluginMenuEntry>
): List<PluginToolbarItem> {
    val fromMenu = entries
        .filter { it.target == com.codeforge.core.domain.extension.PluginMenuTarget.EDITOR_MENU }
        .map { PluginToolbarItem(it.pluginId, it.commandId, it.title) }
    val shown = fromMenu.mapTo(HashSet()) { it.pluginId to it.commandId }
    val rest = commands.filter { (it.pluginId to it.commandId) !in shown }
        .map { PluginToolbarItem(it.pluginId, it.commandId, it.title) }
    return fromMenu + rest
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
    onReferences: () -> Unit,
    onHover: () -> Unit,
    onTypeDefinition: () -> Unit,
    onImplementation: () -> Unit,
    onSignatureHelp: () -> Unit,
    onToggleLspSessions: () -> Unit,
    hasBreakpoints: Boolean,
    onBreakpoints: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenAiQuickActions: () -> Unit,
    pluginItems: List<PluginToolbarItem> = emptyList(),
    onPluginCommand: (PluginToolbarItem) -> Unit = {}
) {
    val isImage = activePath?.let { isImageFilePath(it) } == true
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // KI-Assistent & Schnellaktionen
        TooltipIconButton(onClick = onOpenAiAssistant, label = stringRes(R.string.ai_assistant_title)) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = stringRes(R.string.ai_assistant_title),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        TooltipIconButton(onClick = onOpenAiQuickActions, label = stringRes(R.string.ai_quick_actions)) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = stringRes(R.string.ai_quick_actions)
            )
        }

        LspStatusBadge(
            state = lspServerState,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            onClick = onToggleLspSessions
        )

        // Breakpoints-Übersicht
        if (!isImage) {
            TooltipIconButton(onClick = onBreakpoints, label = stringRes(R.string.debug_tab_breakpoints)) {
                Icon(
                    imageVector = Icons.Default.Adjust,
                    contentDescription = stringRes(R.string.debug_tab_breakpoints),
                    tint = if (hasBreakpoints) androidx.compose.ui.graphics.Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Outline / Struktur: immer verfügbar (LSP oder Fallback)
        if (!isImage) {
            TooltipIconButton(onClick = onOutline, label = stringRes(R.string.lsp_outline)) {
                Icon(Icons.Default.AccountTree, contentDescription = stringRes(R.string.lsp_outline))
            }
        }

        // Quick-Fix (Glühbirne): wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowCodeAction = isLspConnected && (capabilities == null || capabilities.codeActionProvider)
        if (allowCodeAction && !isImage) {
            TooltipIconButton(onClick = onQuickFix, label = stringRes(R.string.lsp_quick_fix)) {
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
            TooltipIconButton(onClick = onRename, label = stringRes(R.string.lsp_rename)) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringRes(R.string.lsp_rename))
            }
        }

        // Definition: wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowDefinition = isLspConnected && (capabilities == null || capabilities.definitionProvider)
        if (allowDefinition && !isImage) {
            TooltipIconButton(onClick = onDefinition, label = stringRes(R.string.lsp_definition)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringRes(R.string.lsp_definition))
            }
        }

        // References (Verwendungen): wenn LSP aktiv und (Capabilities erlaubt oder null)
        val allowReferences = isLspConnected && (capabilities == null || capabilities.referencesProvider)
        if (allowReferences && !isImage) {
            TooltipIconButton(onClick = onReferences, label = stringRes(R.string.lsp_references)) {
                Icon(Icons.Default.FindInPage, contentDescription = stringRes(R.string.lsp_references))
            }
        }

        // Befehle aus Code-Plugins (nur sichtbar, wenn mindestens ein Plugin Befehle registriert hat)
        if (pluginItems.isNotEmpty() && !isImage) {
            var pluginMenuOpen by remember { mutableStateOf(false) }
            Box {
                TooltipIconButton(onClick = { pluginMenuOpen = true }, label = stringRes(R.string.editor_plugin_befehle)) {
                    Icon(Icons.Default.Extension, contentDescription = stringRes(R.string.editor_plugin_befehle))
                }
                DropdownMenu(expanded = pluginMenuOpen, onDismissRequest = { pluginMenuOpen = false }) {
                    pluginItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.title) },
                            onClick = { pluginMenuOpen = false; onPluginCommand(item) }
                        )
                    }
                }
            }
        }

        // Weitere Sprachserver-Aktionen: Dokumentation (Hover), Typ-Definition, Implementierungen, Parameter-Info
        if (isLspConnected && !isImage) {
            var lspMenuOpen by remember { mutableStateOf(false) }
            Box {
                TooltipIconButton(onClick = { lspMenuOpen = true }, label = stringRes(R.string.lsp_more_actions)) {
                    Icon(Icons.Default.Info, contentDescription = stringRes(R.string.lsp_more_actions))
                }
                DropdownMenu(expanded = lspMenuOpen, onDismissRequest = { lspMenuOpen = false }) {
                    if (capabilities == null || capabilities.hoverProvider) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.lsp_hover)) },
                            onClick = { lspMenuOpen = false; onHover() }
                        )
                    }
                    if (capabilities == null || capabilities.typeDefinitionProvider) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.lsp_type_definition)) },
                            onClick = { lspMenuOpen = false; onTypeDefinition() }
                        )
                    }
                    if (capabilities == null || capabilities.implementationProvider) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.lsp_implementation)) },
                            onClick = { lspMenuOpen = false; onImplementation() }
                        )
                    }
                    if (capabilities == null || capabilities.signatureHelpProvider) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.lsp_signature_help)) },
                            onClick = { lspMenuOpen = false; onSignatureHelp() }
                        )
                    }
                }
            }
        }

        TooltipIconButton(onClick = { controller?.undo() }, enabled = !isImage && (controller?.canUndoState ?: false), label = stringRes(R.string.editor_rueckgaengig)) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringRes(R.string.editor_rueckgaengig))
        }
        TooltipIconButton(onClick = { controller?.redo() }, enabled = !isImage && (controller?.canRedoState ?: false), label = stringRes(R.string.editor_wiederholen)) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringRes(R.string.editor_wiederholen))
        }
        TooltipIconButton(onClick = { controller?.selectAll() }, enabled = !isImage, label = stringRes(R.string.editor_alles_auswaehlen)) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringRes(R.string.editor_alles_auswaehlen))
        }
        TooltipIconButton(onClick = { controller?.cut() }, enabled = !isImage, label = stringRes(R.string.editor_ausschneiden)) {
            Icon(Icons.Filled.ContentCut, contentDescription = stringRes(R.string.editor_ausschneiden))
        }
        TooltipIconButton(onClick = { controller?.copy() }, enabled = !isImage, label = stringRes(R.string.editor_kopieren)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = stringRes(R.string.editor_kopieren))
        }
        TooltipIconButton(onClick = { controller?.paste() }, enabled = !isImage, label = stringRes(R.string.editor_einfuegen)) {
            Icon(Icons.Filled.ContentPaste, contentDescription = stringRes(R.string.editor_einfuegen))
        }
        TooltipIconButton(onClick = onSave, enabled = !isImage, label = stringRes(R.string.common_speichern)) {
            Icon(Icons.Filled.Save, contentDescription = stringRes(R.string.common_speichern))
        }
        val hasSelection = controller?.hasSelection() == true
        val formatDesc = if (hasSelection) stringRes(R.string.editor_format_selection) else stringRes(R.string.editor_format_document)
        TooltipIconButton(onClick = onFormat, enabled = !isImage, label = formatDesc) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = formatDesc)
        }
        val unit = " ".repeat(tabSize.coerceIn(1, 8))
        val commentStyle = if (isImage) null else activePath?.let(LineEdits::commentStyleFor)
        TooltipIconButton(
            onClick = { commentStyle?.let { style -> controller?.runLineCommand { t, a, b -> LineEdits.toggleComment(t, a, b, style) } } },
            enabled = commentStyle != null,
            label = stringRes(R.string.editor_kommentar_umschalten)
        ) {
            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = stringRes(R.string.editor_kommentar_umschalten))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.indent(t, a, b, unit) } }, label = stringRes(R.string.editor_einruecken)) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = stringRes(R.string.editor_einruecken))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.dedent(t, a, b, unit) } }, label = stringRes(R.string.editor_ausruecken)) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = stringRes(R.string.editor_ausruecken))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.duplicateLines(t, a, b) } }, label = stringRes(R.string.editor_zeile_duplizieren)) {
            Icon(Icons.Filled.ControlPointDuplicate, contentDescription = stringRes(R.string.editor_zeile_duplizieren))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.deleteLines(t, a, b) } }, label = stringRes(R.string.editor_zeile_loeschen)) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = stringRes(R.string.editor_zeile_loeschen))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.moveLines(t, a, b, up = true) } }, label = stringRes(R.string.editor_zeile_nach_oben)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringRes(R.string.editor_zeile_nach_oben))
        }
        TooltipIconButton(onClick = { controller?.runLineCommand { t, a, b -> LineEdits.moveLines(t, a, b, up = false) } }, label = stringRes(R.string.editor_zeile_nach_unten)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringRes(R.string.editor_zeile_nach_unten))
        }
        TooltipIconButton(onClick = onGoToLine, label = stringRes(R.string.editor_gehe_zu_zeile)) {
            Icon(Icons.Filled.Numbers, contentDescription = stringRes(R.string.editor_gehe_zu_zeile))
        }
        TooltipIconButton(onClick = onToggleSearch, label = stringRes(R.string.editor_suchen_ersetzen)) {
            Icon(Icons.Filled.Search, contentDescription = stringRes(R.string.editor_suchen_ersetzen))
        }
        TooltipIconButton(onClick = onShare, label = stringRes(R.string.editor_teilen)) {
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
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
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Text(
            text = stringRes(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LspSessionsDialog(
    sessions: List<LspSessionInfo>,
    onRestart: (String) -> Unit,
    onStop: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        // Volle Breite: Der Standard-Dialog ist zu schmal, Badges und Buttons brachen zeichenweise um.
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.editor_lsp_sessions_title)) },
        text = {
            if (sessions.isEmpty()) {
                Text(stringRes(R.string.editor_lsp_no_sessions))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(sessions, key = { it.sessionId }) { session ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = session.serverId,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LspStatusBadge(state = session.state)
                                if (session.isHeavyweight) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = stringRes(R.string.editor_lsp_heavyweight_tag),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringRes(R.string.editor_lsp_open_docs_count, session.openDocumentCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    TextButton(onClick = { onRestart(session.sessionId) }) {
                                        Text(stringRes(R.string.editor_lsp_restart), maxLines = 1, softWrap = false)
                                    }
                                    TextButton(onClick = { onStop(session.sessionId) }) {
                                        Text(stringRes(R.string.editor_lsp_stop), maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_schliessen))
            }
        }
    )
}

@Composable
private fun EditorEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(R.string.editor_keine_datei_geoeffnet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringRes(R.string.editor_hinweis_datei_oeffnen),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
