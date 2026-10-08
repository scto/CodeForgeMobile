/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Teilansichten des Designers: Palette, Struktur, Eigenschaften, XML und die Auswahl-Leiste.
 */
package com.codeforge.feature.layoutdesigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.layoutdesigner.LayoutDesignerUiEvent
import com.codeforge.feature.layoutdesigner.LayoutDesignerUiState
import com.codeforge.feature.layoutdesigner.model.AttrKind
import com.codeforge.feature.layoutdesigner.model.AttrSpec
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.PaletteEntry
import com.codeforge.feature.layoutdesigner.model.WidgetCatalog
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.parentOf
import com.codeforge.feature.layoutdesigner.model.parseColorArgb

/** Palette als Raster, nach Kategorien gruppiert. */
@Composable
internal fun PalettePanel(
    onPick: (PaletteEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember { WidgetCatalog.palette.groupBy { it.category } }
    LazyVerticalGrid(
        modifier = modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (category, entries) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_${category.name}") {
                Text(stringRes(categoryLabel(category)), style = MaterialTheme.typography.titleSmall)
            }
            items(entries, key = { it.key }) { entry ->
                ElevatedCard(onClick = { onPick(entry) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(paletteIcon(entry.key), contentDescription = null)
                        Text(
                            stringRes(paletteLabel(entry.key)),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Kompakte Leiste für die Aktionen auf dem ausgewählten Element. */
@Composable
internal fun SelectionBar(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uid = state.selectedUid
    val isRoot = uid == null || uid == state.document.root.uid
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.MoveSelected(-1)) }, enabled = !isRoot) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = stringRes(R.string.layout_move_up))
        }
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.MoveSelected(1)) }, enabled = !isRoot) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = stringRes(R.string.layout_move_down))
        }
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.IndentSelected) }, enabled = !isRoot) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = stringRes(R.string.layout_indent))
        }
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.OutdentSelected) }, enabled = !isRoot) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = stringRes(R.string.layout_outdent))
        }
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.DuplicateSelected) }, enabled = !isRoot) {
            Icon(Icons.Filled.ContentCopy, contentDescription = stringRes(R.string.layout_duplicate))
        }
        IconButton(onClick = { onEvent(LayoutDesignerUiEvent.DeleteSelected) }, enabled = !isRoot) {
            Icon(Icons.Filled.Delete, contentDescription = stringRes(R.string.common_loeschen))
        }
    }
}

/** Baumansicht: eingerückte Liste aller Elemente. */
@Composable
internal fun TreePanel(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.document) { state.document.flatten() }
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(rows, key = { _, row -> row.first.uid }) { _, (node, depth) ->
                val selected = node.uid == state.selectedUid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onEvent(LayoutDesignerUiEvent.Select(node.uid)) }
                        .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(node.tag.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium)
                        val detail = node.attr("android:id") ?: node.attr("android:text") ?: node.attr("android:hint")
                        if (detail != null) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        SelectionBar(state, onEvent)
    }
}

/** Eigenschaftenliste des ausgewählten Elements. */
@Composable
internal fun PropertiesPanel(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = state.selectedUid?.let { state.document.find(it) }
    if (node == null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringRes(R.string.layout_no_selection), style = MaterialTheme.typography.titleSmall)
                Text(stringRes(R.string.layout_select_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val parentTag = state.document.parentOf(node.uid)?.tag?.substringAfterLast('.')
    val specs = remember(node.tag, parentTag) { WidgetCatalog.attrsFor(node.tag, parentTag) }
    val specNames = remember(specs) { specs.map { it.name }.toSet() }
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(node.tag, style = MaterialTheme.typography.titleMedium)
        specs.forEach { spec ->
            AttributeEditor(
                node = node,
                spec = spec,
                value = node.attr(spec.name).orEmpty(),
                onChange = { onEvent(LayoutDesignerUiEvent.SetAttribute(node.uid, spec.name, it)) },
            )
        }
        val others = node.attributes.keys.filter { it !in specNames }
        if (others.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringRes(R.string.layout_other_attributes), style = MaterialTheme.typography.titleSmall)
            others.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AttributeEditor(
                            node = node,
                            spec = AttrSpec(name, AttrKind.TEXT),
                            value = node.attr(name).orEmpty(),
                            onChange = { onEvent(LayoutDesignerUiEvent.SetAttribute(node.uid, name, it)) },
                            labelOverride = name,
                        )
                    }
                    IconButton(onClick = { onEvent(LayoutDesignerUiEvent.SetAttribute(node.uid, name, null)) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.layout_remove_attribute))
                    }
                }
            }
        }
        OutlinedButton(onClick = { showAdd = true }) { Text(stringRes(R.string.layout_add_attribute)) }
    }

    if (showAdd) {
        AddAttributeDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, value ->
                showAdd = false
                onEvent(LayoutDesignerUiEvent.SetAttribute(node.uid, name, value))
            },
        )
    }
}

/** Ein Eingabefeld passend zur [AttrSpec.kind]. Leerer Wert entfernt das Attribut. */
@Composable
private fun AttributeEditor(
    node: LayoutNode,
    spec: AttrSpec,
    value: String,
    onChange: (String?) -> Unit,
    labelOverride: String? = null,
) {
    val label = labelOverride ?: spec.name.removePrefix("android:")
    when (spec.kind) {
        AttrKind.ENUM -> EnumField(label, value, spec.options, onChange)
        else -> {
            // Lokaler Text, damit Cursor/Eingabe beim Zurückschreiben nicht springen; Undo/Redo
            // von außen wird über den LaunchedEffect eingespielt.
            var text by remember(node.uid, spec.name) { mutableStateOf(value) }
            LaunchedEffect(value) { if (value != text) text = value }
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onChange(it.ifEmpty { null })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (spec.kind == AttrKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
                    ),
                    leadingIcon = if (spec.kind == AttrKind.COLOR) {
                        {
                            val argb = parseColorArgb(text)
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(
                                        if (argb != null) Color(argb.toInt()) else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp),
                                    ),
                            )
                        }
                    } else null,
                )
                if (spec.kind == AttrKind.SIZE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spec.options.forEach { option ->
                            AssistChip(onClick = { text = option; onChange(option) }, label = { Text(option) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnumField(label: String, value: String, options: List<String>, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value.ifEmpty { "–" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("–") }, onClick = { expanded = false; onChange(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onChange(option) })
            }
        }
    }
}

@Composable
private fun AddAttributeDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("android:") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.layout_add_attribute)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.layout_attr_name)) }, singleLine = true)
                OutlinedTextField(value, { value = it }, label = { Text(stringRes(R.string.layout_attr_value)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), value) }, enabled = name.isNotBlank() && value.isNotEmpty()) {
                Text(stringRes(R.string.layout_set_attribute))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}

/** XML-Tab: bearbeitbarer Text; Änderungen gelten erst nach „Übernehmen“. */
@Composable
internal fun XmlPanel(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.xmlDraft ?: state.xmlText,
            onValueChange = { onEvent(LayoutDesignerUiEvent.XmlChanged(it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            isError = state.xmlError != null,
        )
        state.xmlError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.xmlDraft != null && state.xmlError == null) {
            Text(stringRes(R.string.layout_xml_pending), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(LayoutDesignerUiEvent.ApplyXml) }, enabled = state.xmlDraft != null) {
                Text(stringRes(R.string.layout_apply_xml))
            }
            OutlinedButton(onClick = { onEvent(LayoutDesignerUiEvent.RevertXml) }, enabled = state.xmlDraft != null) {
                Text(stringRes(R.string.layout_revert_xml))
            }
        }
    }
}
