@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import com.codeforge.core.resources.ResGetter

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.codeforge.feature.filetree.FileTreeDrawer
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun EditorScreen(
    modifier: Modifier = Modifier,
    uiState: EditorUiState,
    snackbarHostState: SnackbarHostState,
    drawerState: DrawerState,
    effectFlow: SharedFlow<EditorUiEffect>,
    onEvent: (EditorUiEvent) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val languageProvider = remember(context) { SoraLanguageProvider(context) }
    var activeEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showGotoLineDialog by remember { mutableStateOf(false) }
    var gotoLineInput by remember { mutableStateOf("") }

    LaunchedEffect(effectFlow) {
        effectFlow.collectLatest { effect ->
            when (effect) {
                EditorUiEffect.TriggerEditorUndo -> activeEditor?.undo()
                EditorUiEffect.TriggerEditorRedo -> activeEditor?.redo()
                is EditorUiEffect.TriggerGotoLine -> {
                    activeEditor?.let { ed ->
                        val targetLine = (effect.line - 1).coerceIn(0, ed.text.lineCount - 1)
                        val colCount = ed.text.getColumnCount(targetLine)
                        ed.setSelection(targetLine, colCount)
                    }
                }
                else -> {}
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            FileTreeDrawer(
                rootPath = uiState.rootPath,
                onFileClick = { path ->
                    onEvent(EditorUiEvent.OpenFile(path))
                    scope.launch { drawerState.close() }
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                },
                modifier = Modifier.width(320.dp),
                fileTreeConfig = uiState.fileTreeConfig
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        val activeFile = uiState.openFiles.getOrNull(uiState.activeFileIndex)
        val titleText = activeFile?.path?.substringAfterLast('/')?.let { ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_title_file, it) } ?: ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_title_default)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(titleText) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_open_filetree)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEvent(EditorUiEvent.BackClicked) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                            )
                        }
                        IconButton(
                            onClick = { activeEditor?.undo() },
                            enabled = activeEditor?.canUndo() ?: false
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_undo)
                            )
                        }
                        IconButton(
                            onClick = { activeEditor?.redo() },
                            enabled = activeEditor?.canRedo() ?: false
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_redo)
                            )
                        }
                        IconButton(onClick = { onEvent(EditorUiEvent.ToggleSearchPanel) }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_search_replace)
                            )
                        }
                        if (uiState.isLspConnected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_lsp_connected),
                                tint = Color.Green,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(onClick = { onEvent(EditorUiEvent.RunLspFormat) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                                    contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_format)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (uiState.isBuilding || uiState.buildLogs.isNotEmpty()) {
                                    onEvent(EditorUiEvent.ToggleBuildLogs)
                                } else {
                                    onEvent(EditorUiEvent.RunBuild)
                                }
                            }
                        ) {
                            if (uiState.isBuilding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Build,
                                    contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_cd_build)
                                )
                            }
                        }
                        IconButton(onClick = { onEvent(EditorUiEvent.SaveFile) }) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_save)
                            )
                        }
                        IconButton(onClick = { onEvent(EditorUiEvent.ToggleComposePreview) }) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Compose Preview",
                                tint = Color(0xFF6366F1)
                            )
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.cd_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_compose_preview)) },
                                onClick = {
                                    showMenu = false
                                    onEvent(EditorUiEvent.ToggleComposePreview)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_format_code)) },
                                onClick = {
                                    showMenu = false
                                    onEvent(EditorUiEvent.RunLspFormat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_change_language)) },
                                onClick = {
                                    showMenu = false
                                    showLanguageDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_change_theme)) },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_change_font)) },
                                onClick = {
                                    showMenu = false
                                    showFontDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_goto_line)) },
                                onClick = {
                                    showMenu = false
                                    showGotoLineDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_settings)) },
                                onClick = {
                                    showMenu = false
                                    onEvent(EditorUiEvent.NavigateToSettings)
                                }
                            )
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
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                }

                if (uiState.openFiles.isNotEmpty()) {
                    val safeActiveIndex = uiState.activeFileIndex.coerceIn(0, uiState.openFiles.size - 1)
                    androidx.compose.runtime.key(uiState.openFiles.size) {
                        ScrollableTabRow(
                            selectedTabIndex = safeActiveIndex
                        ) {
                            uiState.openFiles.forEachIndexed { index, file ->
                                Tab(
                                    selected = index == safeActiveIndex,
                                    onClick = { onEvent(EditorUiEvent.SelectTab(index)) },
                                    text = {
                                        var tabMenuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(file.path.substringAfterLast('/') + if (file.isDirty) "*" else "")
                                                IconButton(
                                                    onClick = { tabMenuExpanded = true },
                                                    modifier = Modifier.padding(start = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_tab_options),
                                                        tint = Color.Gray
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = tabMenuExpanded,
                                                onDismissRequest = { tabMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_close_tab)) },
                                                    onClick = {
                                                        tabMenuExpanded = false
                                                        onEvent(EditorUiEvent.CloseTab(index))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_close_others)) },
                                                    onClick = {
                                                        tabMenuExpanded = false
                                                        onEvent(EditorUiEvent.CloseOthersTab(index))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_close_all)) },
                                                    onClick = {
                                                        tabMenuExpanded = false
                                                        onEvent(EditorUiEvent.CloseAllTabs)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Suchen & Ersetzen Leiste
                if (uiState.isSearchPanelVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { query ->
                                    onEvent(EditorUiEvent.SearchQueryChanged(query))
                                    activeEditor?.let { ed ->
                                        if (query.isNotEmpty()) {
                                            val searchType = when {
                                                uiState.isRegex -> SearchOptions.TYPE_REGULAR_EXPRESSION
                                                uiState.isWholeWord -> SearchOptions.TYPE_WHOLE_WORD
                                                else -> SearchOptions.TYPE_NORMAL
                                            }
                                            val options = SearchOptions(searchType, !uiState.isMatchCase, RegexBackrefGrammar.DEFAULT)
                                            runCatching { ed.searcher.search(query, options) }
                                        } else {
                                            ed.searcher.stopSearch()
                                        }
                                    }
                                },
                                placeholder = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_search_hint)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = { runCatching { activeEditor?.searcher?.gotoPrevious() } }) {
                                Text("▲")
                            }
                            IconButton(onClick = { runCatching { activeEditor?.searcher?.gotoNext() } }) {
                                Text("▼")
                            }
                            IconButton(onClick = {
                                onEvent(EditorUiEvent.ToggleSearchPanel)
                                activeEditor?.searcher?.stopSearch()
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_close))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.replaceQuery,
                                onValueChange = { onEvent(EditorUiEvent.ReplaceQueryChanged(it)) },
                                placeholder = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_replace_hint)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(onClick = {
                                runCatching { activeEditor?.searcher?.replaceCurrentMatch(uiState.replaceQuery) }
                            }) {
                                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_replace))
                            }
                            Button(onClick = {
                                runCatching { activeEditor?.searcher?.replaceAll(uiState.replaceQuery) }
                            }) {
                                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_replace_all))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.isMatchCase,
                                    onCheckedChange = { onEvent(EditorUiEvent.ToggleMatchCase) }
                                )
                                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_match_case), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.isRegex,
                                    onCheckedChange = { onEvent(EditorUiEvent.ToggleRegex) }
                                )
                                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_regex), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.isWholeWord,
                                    onCheckedChange = { onEvent(EditorUiEvent.ToggleWholeWord) }
                                )
                                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_whole_word), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                uiState.openFiles.getOrNull(uiState.activeFileIndex)?.let { active ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (com.codeforge.feature.editor.ui.isImageFilePath(active.path)) {
                            com.codeforge.feature.editor.ui.ImageFilePreview(
                                filePath = active.path,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        } else {
                            SoraCodeEditor(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                content = active.content,
                                filePath = active.path,
                                languageName = active.languageName,
                                languageProvider = languageProvider,
                                editorConfig = uiState.editorConfig,
                                diagnostics = active.diagnostics,
                                completions = active.completions,
                                onContentChanged = { text -> onEvent(EditorUiEvent.TextChanged(text)) },
                                onCursorPositionChanged = { line, column ->
                                    onEvent(EditorUiEvent.CursorPositionChanged(line, column))
                                },
                                onPositionTextChanged = { text -> onEvent(EditorUiEvent.PositionTextChanged(text)) },
                                onSaveRequested = { onEvent(EditorUiEvent.SaveFile) },
                                onSearchToggleRequested = { onEvent(EditorUiEvent.ToggleSearchPanel) },
                                onCompletionRequested = { onEvent(EditorUiEvent.CompletionRequested) },
                                onCompletionItemSelected = { item -> onEvent(EditorUiEvent.CompletionItemSelected(item)) },
                                onEditorCreated = { editor -> activeEditor = editor }
                            )
                        }

                        if (active.diagnostics.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .background(Color(0xFF222222))
                                    .padding(8.dp)
                            ) {
                                items(active.diagnostics.size) { index ->
                                    val diag = active.diagnostics[index]
                                    val color = when (diag.severity) {
                                        1 -> Color.Red
                                        2 -> Color.Yellow
                                        else -> Color.White
                                    }
                                    Text(
                                        text = ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_diag_line_msg, diag.range.start.line, diag.message),
                                        color = color,
                                        fontSize = TextUnit(12f, TextUnitType.Sp)
                                    )
                                }
                            }
                        }

                        // Bottom position status bar
                        if (uiState.positionText.isNotEmpty()) {
                            Text(
                                text = uiState.positionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Symbol input view bar for fast code typing
                        if (uiState.editorConfig.symbolBarVisible) {
                            EditorSymbolBar(
                                editor = activeEditor,
                                preset = when {
                                    active.path.endsWith(".kt") || active.path.endsWith(".kts") || active.path.endsWith(".java") -> "JAVA_KOTLIN"
                                    active.path.endsWith(".py") -> "PYTHON"
                                    active.path.endsWith(".html") || active.path.endsWith(".xml") -> "HTML_XML"
                                    active.path.endsWith(".js") || active.path.endsWith(".ts") -> "JS_TS"
                                    else -> "DEFAULT"
                                }
                            )
                        }
                    }
                }
            }

            // Build Logs Modal Sheet
            if (uiState.showBuildLogs) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                ModalBottomSheet(
                    onDismissRequest = { onEvent(EditorUiEvent.ToggleBuildLogs) },
                    sheetState = sheetState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 600.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_build_logs),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(8.dp)
                        ) {
                            items(uiState.buildLogs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = Color.LightGray,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = TextUnit(12f, TextUnitType.Sp)
                                )
                            }
                        }
                    }
                }
            }

            // Language Switcher Dialog
            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_select_language)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            listOf(
                                "Builtin Java" to { activeEditor?.setEditorLanguage(languageProvider.getBuiltinJavaLanguage()) },
                                "TextMate Kotlin" to { activeEditor?.setEditorLanguage(languageProvider.getLanguageByScope("source.kotlin")) },
                                "TextMate Java" to { activeEditor?.setEditorLanguage(languageProvider.getLanguageByScope("source.java")) },
                                "TextMate Python" to { activeEditor?.setEditorLanguage(languageProvider.getLanguageByScope("source.python")) },
                                "TextMate HTML" to { activeEditor?.setEditorLanguage(languageProvider.getLanguageByScope("text.html.basic")) },
                                "TextMate JavaScript" to { activeEditor?.setEditorLanguage(languageProvider.getLanguageByScope("source.js")) },
                                "Monarch Java" to { activeEditor?.setEditorLanguage(languageProvider.getMonarchLanguage("source.java")) },
                                "Monarch Kotlin" to { activeEditor?.setEditorLanguage(languageProvider.getMonarchLanguage("source.kotlin")) },
                                "Monarch Python" to { activeEditor?.setEditorLanguage(languageProvider.getMonarchLanguage("source.python")) },
                                "Monarch TypeScript" to { activeEditor?.setEditorLanguage(languageProvider.getMonarchLanguage("source.typescript")) },
                                "Tree-Sitter Java" to { activeEditor?.setEditorLanguage(languageProvider.getTreeSitterJavaLanguage()) }
                            ).forEach { (name, action) ->
                                TextButton(
                                    onClick = {
                                        action()
                                        showLanguageDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguageDialog = false }) { Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel)) }
                    }
                )
            }

            // Theme Switcher Dialog
            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_dialog_select_theme)) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Triple("Darcula", "darcula", Color(0xFF2B2B2B)),
                                Triple("Quiet Light", "quietlight", Color(0xFFF5F5F5)),
                                Triple("Ayu Dark", "ayu-dark", Color(0xFF0F1419)),
                                Triple("Solarized Dark", "solarized_dark", Color(0xFF002B36)),
                                Triple("GitHub Light", "GitHub", Color(0xFFFFFFFF)),
                                Triple("VS Code Dark+", "VS2019", Color(0xFF1E1E1E)),
                                Triple("Notepad++", "NotepadXX", Color(0xFFFFFFFF)),
                                Triple("Eclipse", "Eclipse", Color(0xFFFFFFFF))
                            ).forEach { (displayName, themeKey, bgColor) ->
                                TextButton(
                                    onClick = {
                                        activeEditor?.let { languageProvider.applySchemeByName(it, themeKey) }
                                        onEvent(EditorUiEvent.SelectTheme(themeKey))
                                        showThemeDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(displayName)
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(18.dp)
                                                .background(bgColor, RoundedCornerShape(4.dp))
                                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) { Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel)) }
                    }
                )
            }

            // Font Switcher Dialog
            if (showFontDialog) {
                AlertDialog(
                    onDismissRequest = { showFontDialog = false },
                    title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_dialog_select_font)) },
                    text = {
                        Column {
                            listOf("JetBrains Mono", "Ubuntu Mono", "Roboto Mono").forEach { fontName ->
                                TextButton(
                                    onClick = {
                                        onEvent(EditorUiEvent.SelectTypeface(fontName))
                                        val fontAsset = when (fontName) {
                                            "Ubuntu Mono" -> "UbuntuMono-Regular.ttf"
                                            "Roboto Mono" -> "RobotoMono-Regular.ttf"
                                            else -> "JetBrainsMono-Regular.ttf"
                                        }
                                        runCatching {
                                            val tf = Typeface.createFromAsset(context.assets, fontAsset)
                                            activeEditor?.typefaceText = tf
                                            activeEditor?.typefaceLineNumber = tf
                                        }
                                        showFontDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(fontName)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showFontDialog = false }) { Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel)) }
                    }
                )
            }

            // Goto Line Dialog
            if (showGotoLineDialog) {
                AlertDialog(
                    onDismissRequest = { showGotoLineDialog = false },
                    title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_dialog_goto_line)) },
                    text = {
                        OutlinedTextField(
                            value = gotoLineInput,
                            onValueChange = { gotoLineInput = it },
                            placeholder = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_editor_dialog_goto_line_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val lineNum = gotoLineInput.toIntOrNull()
                            if (lineNum != null && lineNum > 0) {
                                onEvent(EditorUiEvent.GotoLine(lineNum))
                            }
                            showGotoLineDialog = false
                            gotoLineInput = ""
                        }) {
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.action_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showGotoLineDialog = false
                            gotoLineInput = ""
                        }) {
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}
