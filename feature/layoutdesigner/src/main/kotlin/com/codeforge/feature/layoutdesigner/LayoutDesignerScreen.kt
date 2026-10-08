/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Visueller Editor für Android-Layout-XML. Kompakt (< 600dp): Tabs Vorschau / Struktur /
 * Eigenschaften / XML. Medium/Expanded: Vorschau links, rechts ein Panel mit Struktur,
 * Eigenschaften und XML.
 */
package com.codeforge.feature.layoutdesigner

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.WidthClass
import com.codeforge.core.ui.rememberWidthClass
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.preview.LayoutPreview
import com.codeforge.feature.layoutdesigner.preview.isApproximatedTag
import com.codeforge.feature.layoutdesigner.ui.PalettePanel
import com.codeforge.feature.layoutdesigner.ui.PropertiesPanel
import com.codeforge.feature.layoutdesigner.ui.SelectionBar
import com.codeforge.feature.layoutdesigner.ui.TreePanel
import com.codeforge.feature.layoutdesigner.ui.XmlPanel
import com.codeforge.feature.layoutdesigner.ui.deviceLabel
import com.codeforge.feature.layoutdesigner.ui.tabLabel

@Composable
fun LayoutDesignerRoute(
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LayoutDesignerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(filePath) { viewModel.onEvent(LayoutDesignerUiEvent.Load(filePath)) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LayoutDesignerUiEffect.ShowSnackbar -> snackbar.showSnackbar(effect.message)
                LayoutDesignerUiEffect.NavigateBack -> onBack()
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

    Scaffold(
        modifier = modifier,
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
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.Save) }, enabled = ready && !state.isSaving) {
                        Icon(Icons.Filled.Save, contentDescription = stringRes(R.string.common_speichern))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                else -> DesignerContent(state, onEvent)
            }
        }
    }

    if (state.showPalette) {
        ModalBottomSheet(onDismissRequest = { onEvent(LayoutDesignerUiEvent.PaletteVisible(false)) }) {
            PalettePanel(onPick = { onEvent(LayoutDesignerUiEvent.AddWidget(it)) })
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
}

@Composable
private fun DesignerContent(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
) {
    val wide = rememberWidthClass() != WidthClass.Compact
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            PreviewPane(state, onEvent, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider()
            // Im breiten Layout gibt es den Vorschau-Tab nicht – er entspricht „Struktur“.
            val sideTab = if (state.tab == DesignerTab.PREVIEW) DesignerTab.TREE else state.tab
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                DesignerTabs(sideTab, listOf(DesignerTab.TREE, DesignerTab.PROPERTIES, DesignerTab.XML), onEvent)
                TabContent(sideTab, state, onEvent, Modifier.weight(1f))
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            DesignerTabs(state.tab, DesignerTab.entries, onEvent)
            TabContent(state.tab, state, onEvent, Modifier.weight(1f))
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
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        when (tab) {
            DesignerTab.PREVIEW -> PreviewPane(state, onEvent, Modifier.fillMaxSize())
            DesignerTab.TREE -> TreePanel(state, onEvent)
            DesignerTab.PROPERTIES -> PropertiesPanel(state, onEvent)
            DesignerTab.XML -> XmlPanel(state, onEvent)
        }
    }
}

@Composable
private fun PreviewPane(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val approximated = remember(state.document) {
        state.document.flatten().any { isApproximatedTag(it.first.tag) }
    }
    Column(modifier) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
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
            onSelect = { onEvent(LayoutDesignerUiEvent.Select(it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        HorizontalDivider()
        SelectionBar(state, onEvent)
    }
}
