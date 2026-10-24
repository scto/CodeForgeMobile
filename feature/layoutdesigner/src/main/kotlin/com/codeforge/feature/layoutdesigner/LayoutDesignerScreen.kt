/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Visueller Editor für Android-Layout-XML. Kompakt (< 600dp): Tabs Vorschau / Struktur /
 * Eigenschaften / XML. Medium/Expanded: Vorschau links, rechts ein Panel mit Struktur,
 * Eigenschaften und XML.
 * Unterstützt Drag & Drop, Zoom/Pan-Transformationen, Größengriffe, Overlays,
 * Breadcrumbs, Zwischenablage, Tastaturkürzel und PNG-Export.
 */
package com.codeforge.feature.layoutdesigner

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.WidthClass
import com.codeforge.core.ui.rememberWidthClass
import com.codeforge.feature.layoutdesigner.dnd.DragController
import com.codeforge.feature.layoutdesigner.files.LayoutDesignerFileProvider
import com.codeforge.feature.layoutdesigner.lint.IssueSeverity
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.parentOf
import com.codeforge.feature.layoutdesigner.preview.LayoutPreview
import com.codeforge.feature.layoutdesigner.preview.isApproximatedTag
import com.codeforge.feature.layoutdesigner.ui.BreadcrumbBar
import com.codeforge.feature.layoutdesigner.ui.CanvasConfigBar
import com.codeforge.feature.layoutdesigner.ui.PalettePanel
import com.codeforge.feature.layoutdesigner.ui.ProblemsPanel
import com.codeforge.feature.layoutdesigner.ui.PropertiesPanel
import com.codeforge.feature.layoutdesigner.ui.SelectionBar
import com.codeforge.feature.layoutdesigner.ui.TreePanel
import com.codeforge.feature.layoutdesigner.ui.XmlPanel
import com.codeforge.feature.layoutdesigner.ui.deviceLabel
import com.codeforge.feature.layoutdesigner.ui.tabLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
fun LayoutDesignerRoute(
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LayoutDesignerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringRes(R.string.layout_copied_to_clipboard)

    LaunchedEffect(filePath) { viewModel.onEvent(LayoutDesignerUiEvent.Load(filePath)) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LayoutDesignerUiEffect.ShowSnackbar -> snackbar.showSnackbar(effect.message)
                LayoutDesignerUiEffect.NavigateBack -> onBack()
                is LayoutDesignerUiEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                    snackbar.showSnackbar(copiedMessage)
                }
                is LayoutDesignerUiEffect.OpenLayoutFile -> {
                    viewModel.onEvent(LayoutDesignerUiEvent.Load(effect.path))
                }
            }
        }
    }
    BackHandler { viewModel.onEvent(LayoutDesignerUiEvent.BackRequested) }

    LayoutDesignerScreen(
        modifier = modifier,
        state = state,
        snackbar = snackbar,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDesignerScreen(
    state: LayoutDesignerUiState,
    snackbar: SnackbarHostState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var contextMenuOpen by remember { mutableStateOf(false) }
    var wrapInDialogOpen by remember { mutableStateOf(false) }
    var renameIdDialogOpen by remember { mutableStateOf(false) }
    var renameIdValue by remember { mutableStateOf("") }
    var changeTagDialogOpen by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareTitle = stringRes(R.string.layout_share_preview)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isCtrl = keyEvent.isCtrlPressed
                val isShift = keyEvent.isShiftPressed
                val isAlt = keyEvent.isAltPressed

                when {
                    isCtrl && isShift && keyEvent.key == Key.Z -> {
                        if (state.canRedo) onEvent(LayoutDesignerUiEvent.Redo)
                        true
                    }
                    isCtrl && keyEvent.key == Key.Y -> {
                        if (state.canRedo) onEvent(LayoutDesignerUiEvent.Redo)
                        true
                    }
                    isCtrl && keyEvent.key == Key.Z -> {
                        if (state.canUndo) onEvent(LayoutDesignerUiEvent.Undo)
                        true
                    }
                    isCtrl && keyEvent.key == Key.S -> {
                        onEvent(LayoutDesignerUiEvent.Save)
                        true
                    }
                    isCtrl && keyEvent.key == Key.C -> {
                        onEvent(LayoutDesignerUiEvent.CopySelected)
                        true
                    }
                    isCtrl && keyEvent.key == Key.X -> {
                        onEvent(LayoutDesignerUiEvent.CutSelected)
                        true
                    }
                    isCtrl && keyEvent.key == Key.V -> {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onEvent(LayoutDesignerUiEvent.PasteSnippet(clip))
                        }
                        true
                    }
                    isCtrl && keyEvent.key == Key.D -> {
                        onEvent(LayoutDesignerUiEvent.DuplicateSelected)
                        true
                    }
                    keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> {
                        onEvent(LayoutDesignerUiEvent.DeleteSelected)
                        true
                    }
                    isAlt && (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionLeft) -> {
                        onEvent(LayoutDesignerUiEvent.MoveSelected(-1))
                        true
                    }
                    isAlt && (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.DirectionRight) -> {
                        onEvent(LayoutDesignerUiEvent.MoveSelected(1))
                        true
                    }
                    keyEvent.key == Key.DirectionUp -> {
                        val allUids = state.document.flatten().map { it.first.uid }
                        val currIdx = allUids.indexOf(state.selectedUid)
                        if (currIdx > 0) {
                            onEvent(LayoutDesignerUiEvent.Select(allUids[currIdx - 1]))
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown -> {
                        val allUids = state.document.flatten().map { it.first.uid }
                        val currIdx = allUids.indexOf(state.selectedUid)
                        if (currIdx in 0 until allUids.lastIndex) {
                            onEvent(LayoutDesignerUiEvent.Select(allUids[currIdx + 1]))
                        }
                        true
                    }
                    else -> false
                }
            },
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.fileName.ifEmpty { stringRes(R.string.layout_title) }, maxLines = 1)
                        if (state.dirty) {
                            Text(
                                stringRes(R.string.layout_unsaved_marker),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.BackRequested) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringRes(R.string.layout_back))
                    }
                },
                actions = {
                    val ready = !state.isLoading && state.loadError == null
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.Undo) }, enabled = ready && state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringRes(R.string.layout_undo))
                    }
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.Redo) }, enabled = ready && state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringRes(R.string.layout_redo))
                    }
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.PaletteVisible(true)) }, enabled = ready) {
                        Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.layout_add_widget))
                    }
                    if (state.issues.isNotEmpty()) {
                        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.TabChanged(DesignerTab.PROBLEMS)) }) {
                            BadgedBox(
                                badge = {
                                    val hasError = state.issues.any { it.severity == IssueSeverity.ERROR }
                                    Badge(
                                        containerColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Text(state.issues.size.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = stringRes(R.string.layout_tab_problems))
                            }
                        }
                    }
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.Save) }, enabled = ready && !state.isSaving) {
                        Icon(Icons.Filled.Save, contentDescription = stringRes(R.string.common_speichern))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.layout_export_png)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        try {
                                            val imageBitmap = graphicsLayer.toImageBitmap()
                                            val bmp = imageBitmap.asAndroidBitmap()
                                            val outFile = File(context.cacheDir, "${state.fileName.substringBeforeLast('.')}_preview.png")
                                            withContext(Dispatchers.IO) {
                                                FileOutputStream(outFile).use { out ->
                                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                }
                                            }
                                            val shareIntent = LayoutDesignerFileProvider.buildSharePngIntent(context, outFile)
                                            context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                                        } catch (t: Throwable) {
                                            snackbar.showSnackbar(t.message ?: "Export failed")
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.layout_open_in_editor)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEvent(LayoutDesignerUiEvent.OpenInEditor)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(stringRes(R.string.layout_loading), modifier = Modifier.padding(top = 12.dp))
                }
                state.loadError != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.loadError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onEvent(LayoutDesignerUiEvent.OpenInEditor) }) {
                        Text(stringRes(R.string.layout_open_in_editor))
                    }
                }
                else -> DesignerContent(
                    state = state,
                    onEvent = onEvent,
                    graphicsLayer = graphicsLayer,
                    onOpenContextMenu = { contextMenuOpen = true },
                )
            }
        }
    }

    if (state.showPalette) {
        ModalBottomSheet(onDismissRequest = { onEvent(LayoutDesignerUiEvent.PaletteVisible(false)) }) {
            PalettePanel(
                onPick = { onEvent(LayoutDesignerUiEvent.AddWidget(it)) },
                customViews = state.customViews,
            )
        }
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(LayoutDesignerUiEvent.DismissDiscard) },
            title = { Text(stringRes(R.string.layout_discard_title)) },
            text = { Text(stringRes(R.string.layout_discard_text)) },
            confirmButton = {
                TextButton(onClick = { onEvent(LayoutDesignerUiEvent.ConfirmDiscard) }) {
                    Text(stringRes(R.string.layout_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(LayoutDesignerUiEvent.DismissDiscard) }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            },
        )
    }

    // Kontextmenü-BottomSheet
    if (contextMenuOpen && state.selectedUid != null) {
        ModalBottomSheet(onDismissRequest = { contextMenuOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_cut)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.CutSelected)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_copy)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.CopySelected)
                    },
                )
                val clip = clipboardManager.getText()?.text
                if (!clip.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.layout_context_paste)) },
                        onClick = {
                            contextMenuOpen = false
                            onEvent(LayoutDesignerUiEvent.PasteSnippet(clip))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_duplicate)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.DuplicateSelected)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.common_loeschen)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.DeleteSelected)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_wrap)) },
                    onClick = {
                        contextMenuOpen = false
                        wrapInDialogOpen = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_unwrap)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.UnwrapSelected)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_change_tag)) },
                    onClick = {
                        contextMenuOpen = false
                        changeTagDialogOpen = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_rename_id)) },
                    onClick = {
                        contextMenuOpen = false
                        renameIdValue = ""
                        renameIdDialogOpen = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.layout_context_show_in_xml)) },
                    onClick = {
                        contextMenuOpen = false
                        onEvent(LayoutDesignerUiEvent.TabChanged(DesignerTab.XML))
                    },
                )
            }
        }
    }

    // In Container packen Dialog
    if (wrapInDialogOpen) {
        val containers = listOf("LinearLayout", "FrameLayout", "ScrollView", "androidx.constraintlayout.widget.ConstraintLayout")
        AlertDialog(
            onDismissRequest = { wrapInDialogOpen = false },
            title = { Text(stringRes(R.string.layout_context_wrap)) },
            text = {
                Column {
                    containers.forEach { tag ->
                        TextButton(
                            onClick = {
                                wrapInDialogOpen = false
                                onEvent(LayoutDesignerUiEvent.WrapIn(tag))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tag.substringAfterLast('.'))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wrapInDialogOpen = false }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            },
        )
    }

    // ID umbenennen Dialog
    if (renameIdDialogOpen && state.selectedUid != null) {
        val uid = state.selectedUid
        AlertDialog(
            onDismissRequest = { renameIdDialogOpen = false },
            title = { Text(stringRes(R.string.layout_context_rename_id)) },
            text = {
                OutlinedTextField(
                    value = renameIdValue,
                    onValueChange = { renameIdValue = it },
                    label = { Text("ID (z. B. button_submit)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameIdDialogOpen = false
                        if (renameIdValue.isNotBlank()) {
                            onEvent(LayoutDesignerUiEvent.RenameId(uid, renameIdValue.trim()))
                        }
                    },
                ) {
                    Text(stringRes(R.string.layout_set_attribute))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameIdDialogOpen = false }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            },
        )
    }

    // Tag ändern Dialog
    if (changeTagDialogOpen && state.selectedUid != null) {
        val uid = state.selectedUid
        val availableTags = listOf(
            "TextView", "Button", "EditText", "ImageView", "CheckBox", "Switch",
            "LinearLayout", "FrameLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        )
        AlertDialog(
            onDismissRequest = { changeTagDialogOpen = false },
            title = { Text(stringRes(R.string.layout_context_change_tag)) },
            text = {
                Column {
                    availableTags.forEach { tag ->
                        TextButton(
                            onClick = {
                                changeTagDialogOpen = false
                                onEvent(LayoutDesignerUiEvent.ChangeTag(uid, tag, removeIncompatible = true))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tag.substringAfterLast('.'))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { changeTagDialogOpen = false }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            },
        )
    }
}

@Composable
private fun DesignerContent(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    graphicsLayer: GraphicsLayer,
    onOpenContextMenu: () -> Unit,
) {
    val wide = rememberWidthClass() != WidthClass.Compact
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            PreviewPane(state, onEvent, graphicsLayer, onOpenContextMenu, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider()
            val sideTab = if (state.tab == DesignerTab.PREVIEW) DesignerTab.TREE else state.tab
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                DesignerTabs(sideTab, listOf(DesignerTab.TREE, DesignerTab.PROPERTIES, DesignerTab.PROBLEMS, DesignerTab.XML), onEvent)
                TabContent(sideTab, state, onEvent, graphicsLayer, onOpenContextMenu, Modifier.weight(1f))
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            DesignerTabs(state.tab, DesignerTab.entries, onEvent)
            TabContent(state.tab, state, onEvent, graphicsLayer, onOpenContextMenu, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DesignerTabs(
    selected: DesignerTab,
    tabs: List<DesignerTab>,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
) {
    TabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0)) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onEvent(LayoutDesignerUiEvent.TabChanged(tab)) },
                text = { Text(stringRes(tabLabel(tab))) },
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: DesignerTab,
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    graphicsLayer: GraphicsLayer,
    onOpenContextMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        when (tab) {
            DesignerTab.PREVIEW -> PreviewPane(state, onEvent, graphicsLayer, onOpenContextMenu, Modifier.fillMaxSize())
            DesignerTab.TREE -> TreePanel(state, onEvent)
            DesignerTab.PROPERTIES -> PropertiesPanel(state, onEvent)
            DesignerTab.PROBLEMS -> ProblemsPanel(state, onEvent)
            DesignerTab.XML -> XmlPanel(state, onEvent)
        }
    }
}

@Composable
private fun PreviewPane(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    graphicsLayer: GraphicsLayer,
    onOpenContextMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val approximated = remember(state.document) {
        state.document.flatten().any { isApproximatedTag(it.first.tag) }
    }
    val dragController = remember { DragController() }

    Column(modifier) {
        BreadcrumbBar(
            document = state.document,
            selectedUid = state.selectedUid,
            onSelect = { onEvent(LayoutDesignerUiEvent.Select(it)) },
        )
        CanvasConfigBar(state = state, onEvent = onEvent)

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DevicePreset.entries) { preset ->
                FilterChip(
                    selected = preset == state.device,
                    onClick = { onEvent(LayoutDesignerUiEvent.DeviceChanged(preset)) },
                    label = { Text(stringRes(deviceLabel(preset))) },
                )
            }
        }
        if (approximated) {
            Text(
                stringRes(R.string.layout_approx_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        LayoutPreview(
            document = state.document,
            selectedUid = state.selectedUid,
            device = state.device,
            onSelect = { uid ->
                // Bei erneutem Antippen des bereits ausgewählten Elements zum Elternteil wechseln
                if (uid == state.selectedUid) {
                    val parent = state.document.parentOf(uid)
                    if (parent != null) onEvent(LayoutDesignerUiEvent.Select(parent.uid))
                } else {
                    onEvent(LayoutDesignerUiEvent.Select(uid))
                }
            },
            dragController = dragController,
            onDrop = { payload, target -> onEvent(LayoutDesignerUiEvent.DropWidget(payload, target)) },
            zoom = state.zoom,
            panX = state.panX,
            panY = state.panY,
            onZoomChange = { onEvent(LayoutDesignerUiEvent.SetZoom(it)) },
            onPanChange = { px, py -> onEvent(LayoutDesignerUiEvent.SetPan(px, py)) },
            onResetZoomPan = { onEvent(LayoutDesignerUiEvent.ResetZoomPan) },
            onResizeDelta = { dx, dy ->
                val density = 2f
                val deltaWDp = (dx / density).roundToInt()
                val deltaHDp = (dy / density).roundToInt()
                val selNode = state.document.flatten().find { it.first.uid == state.selectedUid }?.first
                if (selNode != null) {
                    val curW = com.codeforge.feature.layoutdesigner.model.parseDimension(selNode.attr("android:layout_width"))?.roundToInt() ?: 100
                    val curH = com.codeforge.feature.layoutdesigner.model.parseDimension(selNode.attr("android:layout_height"))?.roundToInt() ?: 50
                    onEvent(LayoutDesignerUiEvent.ResizeSelected(curW + deltaWDp, curH + deltaHDp))
                }
            },
            onToggleSizeMode = { isWidth -> onEvent(LayoutDesignerUiEvent.ToggleSizeMode(isWidth)) },
            effectiveWidthDp = state.effectiveWidthDp,
            effectiveHeightDp = state.effectiveHeightDp,
            graphicsLayer = graphicsLayer,
            resourceRepo = state.resourceRepo,
            resourceConfig = com.codeforge.feature.layoutdesigner.res.ResourceConfig(
                isNight = state.isDarkTheme,
                isLandscape = state.isLandscape,
                swDp = state.effectiveWidthDp,
                wDp = state.effectiveWidthDp,
            ),
            showTools = state.showToolsAttributes,
            includeResolver = state.includeResolver,
            inflatedViewStubs = state.inflatedViewStubs,
            onToggleInflateViewStub = { onEvent(LayoutDesignerUiEvent.ToggleInflateViewStub(it)) },
            onOpenIncluded = { onEvent(LayoutDesignerUiEvent.OpenIncludedLayout(it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionBar(state, onEvent, modifier = Modifier.weight(1f))
            IconButton(
                onClick = onOpenContextMenu,
                enabled = state.selectedUid != null,
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Kontextmenü")
            }
        }
    }
}
