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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import com.codeforge.feature.layoutdesigner.lint.IssueSeverity
import com.codeforge.feature.layoutdesigner.lint.LayoutIssue
import com.codeforge.feature.layoutdesigner.lint.QuickFix
import com.codeforge.feature.layoutdesigner.res.ResType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.codeforge.feature.layoutdesigner.model.parseDimension
import kotlin.math.roundToInt
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
    customViews: List<com.codeforge.feature.layoutdesigner.model.CustomViewInfo> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<com.codeforge.feature.layoutdesigner.model.WidgetCategory?>(null) }

    val allEntries = remember(customViews) {
        val projectEntries = customViews.map { cv ->
            PaletteEntry(
                key = "custom_${cv.qualifiedName}",
                tag = cv.qualifiedName,
                category = com.codeforge.feature.layoutdesigner.model.WidgetCategory.PROJECT,
                defaults = mapOf(
                    "android:layout_width" to "wrap_content",
                    "android:layout_height" to "wrap_content",
                ),
            )
        }
        WidgetCatalog.palette + projectEntries
    }

    val filteredEntries = remember(allEntries, searchQuery, selectedCategory) {
        allEntries.filter { entry ->
            val matchesCat = selectedCategory == null || entry.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                entry.key.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val groups = remember(filteredEntries) {
        filteredEntries.groupBy { it.category }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringRes(R.string.layout_search_palette)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text(stringRes(R.string.layout_filter_all)) },
            )
            com.codeforge.feature.layoutdesigner.model.WidgetCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(stringRes(categoryLabel(cat))) },
                )
            }
        }

        LazyVerticalGrid(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                                text = if (entry.key.startsWith("custom_")) entry.tag.substringAfterLast('.') else stringRes(paletteLabel(entry.key)),
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
                    val nodeIssues = state.issues.filter { it.uid == node.uid }
                    if (nodeIssues.isNotEmpty()) {
                        val hasError = nodeIssues.any { it.severity == IssueSeverity.ERROR }
                        Icon(
                            imageVector = if (hasError) Icons.Filled.Error else Icons.Filled.Warning,
                            contentDescription = "Issues",
                            tint = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        SelectionBar(state, onEvent)
    }
}

private enum class AttrGroup {
    LAYOUT, MARGINS, TEXT, APPEARANCE, BEHAVIOR, CONSTRAINTS, OTHER;

    @StringRes
    fun labelRes(): Int = when (this) {
        LAYOUT -> R.string.layout_group_layout
        MARGINS -> R.string.layout_group_margins
        TEXT -> R.string.layout_group_text
        APPEARANCE -> R.string.layout_group_appearance
        BEHAVIOR -> R.string.layout_group_behavior
        CONSTRAINTS -> R.string.layout_group_constraints
        OTHER -> R.string.layout_group_other
    }
}

private fun classifyAttr(name: String): AttrGroup = when {
    name.contains("constraint") -> AttrGroup.CONSTRAINTS
    name.contains("margin") || name.contains("padding") -> AttrGroup.MARGINS
    name.contains("text") || name.contains("gravity") || name.contains("hint") || name.contains("font") -> AttrGroup.TEXT
    name.contains("background") || name.contains("color") || name.contains("tint") || name.contains("src") || name.contains("elevation") || name.contains("alpha") -> AttrGroup.APPEARANCE
    name.contains("layout_width") || name.contains("layout_height") || name.contains("orientation") || name.contains("layout_weight") || name.contains("row") || name.contains("column") -> AttrGroup.LAYOUT
    name.contains("visibility") || name.contains("checked") || name.contains("enabled") || name.contains("clickable") || name.contains("inputType") -> AttrGroup.BEHAVIOR
    else -> AttrGroup.OTHER
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
    val otherAttrs = remember(node.attributes, specNames) {
        node.attributes.keys.filter { it !in specNames }.map { AttrSpec(it, AttrKind.TEXT) }
    }
    val allSpecs = remember(specs, otherAttrs) { specs + otherAttrs }

    var searchQuery by remember { mutableStateOf("") }
    var onlySet by remember { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf(setOf<AttrGroup>()) }
    var showAdd by remember { mutableStateOf(false) }
    var extractRequest by remember { mutableStateOf<ExtractRequest?>(null) }

    val filteredSpecs = remember(allSpecs, searchQuery, onlySet, node.attributes) {
        allSpecs.filter { spec ->
            val matchesSearch = searchQuery.isBlank() || spec.name.contains(searchQuery, ignoreCase = true)
            val matchesOnlySet = !onlySet || !node.attr(spec.name).isNullOrEmpty()
            matchesSearch && matchesOnlySet
        }
    }

    val groupedSpecs = remember(filteredSpecs) {
        filteredSpecs.groupBy { classifyAttr(it.name) }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(node.tag, style = MaterialTheme.typography.titleMedium)

        val parent = state.document.parentOf(node.uid)
        if (parent != null && parentTag != null) {
            ConstraintCard(
                node = node,
                parentTag = parentTag,
                parentUid = parent.uid,
                onEvent = onEvent,
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringRes(R.string.layout_search_attrs)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = onlySet,
                onClick = { onlySet = !onlySet },
                label = { Text(stringRes(R.string.layout_filter_only_set)) },
            )
            OutlinedButton(onClick = { showAdd = true }) { Text(stringRes(R.string.layout_add_attribute)) }
        }

        AttrGroup.entries.forEach { group ->
            val attrsInGroup = groupedSpecs[group] ?: emptyList()
            if (attrsInGroup.isNotEmpty()) {
                val isCollapsed = group in collapsedGroups
                Surface(
                    onClick = {
                        collapsedGroups = if (isCollapsed) collapsedGroups - group else collapsedGroups + group
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stringRes(group.labelRes())} (${attrsInGroup.size})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (!isCollapsed) {
                    attrsInGroup.forEach { spec ->
                        AttributeEditor(
                            node = node,
                            spec = spec,
                            value = node.attr(spec.name).orEmpty(),
                            onChange = { onEvent(LayoutDesignerUiEvent.SetAttribute(node.uid, spec.name, it)) },
                            repo = state.resourceRepo,
                            onExtract = { resType, valToExtract ->
                                extractRequest = ExtractRequest(node.uid, spec.name, resType, valToExtract)
                            },
                        )
                    }
                }
            }
        }
    }

    extractRequest?.let { req ->
        ExtractResourceDialog(
            request = req,
            onDismiss = { extractRequest = null },
            onConfirm = { resName, resValue ->
                extractRequest = null
                onEvent(
                    LayoutDesignerUiEvent.ExtractToResource(
                        nodeUid = req.nodeUid,
                        attrName = req.attrName,
                        resType = req.resType,
                        name = resName,
                        value = resValue,
                    )
                )
            },
        )
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeEditor(
    node: LayoutNode,
    spec: AttrSpec,
    value: String,
    onChange: (String?) -> Unit,
    labelOverride: String? = null,
    repo: com.codeforge.feature.layoutdesigner.res.ResourceRepository? = null,
    onExtract: ((ResType, String) -> Unit)? = null,
) {
    val label = labelOverride ?: spec.name.removePrefix("android:").removePrefix("app:")
    when (spec.kind) {
        AttrKind.ENUM -> EnumField(label, value, spec.options, onChange)
        AttrKind.BOOLEAN -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("true", "false").forEach { opt ->
                        FilterChip(
                            selected = value == opt,
                            onClick = { onChange(if (value == opt) null else opt) },
                            label = { Text(opt) },
                        )
                    }
                }
            }
        }
        AttrKind.FLAG -> {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                val currentFlags = remember(value) {
                    value.split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    spec.options.forEach { flag ->
                        FilterChip(
                            selected = flag in currentFlags,
                            onClick = {
                                val updated = if (flag in currentFlags) currentFlags - flag else currentFlags + flag
                                onChange(if (updated.isEmpty()) null else updated.joinToString("|"))
                            },
                            label = { Text(flag) },
                        )
                    }
                }
            }
        }
        else -> {
            var text by remember(node.uid, spec.name) { mutableStateOf(value) }
            LaunchedEffect(value) { if (value != text) text = value }

            val resolved = remember(text, repo) {
                when {
                    text.startsWith("@string/") -> repo?.resolveString(text)
                    text.startsWith("@color/") || text.startsWith("?") -> repo?.resolveColor(text)?.let { String.format("#%08X", it) }
                    text.startsWith("@dimen/") -> repo?.resolveDimension(text)?.let { "${it}dp" }
                    else -> null
                }
            }

            val canExtract = onExtract != null && text.isNotBlank() && !text.startsWith("@") && !text.startsWith("?") &&
                (spec.kind == AttrKind.TEXT || spec.kind == AttrKind.COLOR || spec.kind == AttrKind.DIMENSION)

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onChange(it.ifEmpty { null })
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (spec.kind == AttrKind.NUMBER || spec.kind == AttrKind.INTEGER || spec.kind == AttrKind.FLOAT) KeyboardType.Decimal else KeyboardType.Text,
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
                        trailingIcon = if (text.isNotEmpty()) {
                            {
                                IconButton(onClick = { text = ""; onChange(null) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.layout_clear_attr), modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null,
                    )
                    if (spec.kind == AttrKind.DIMENSION) {
                        IconButton(
                            onClick = {
                                val cur = parseDimension(text)?.roundToInt() ?: 0
                                val next = maxOf(0, cur - 4)
                                text = "${next}dp"
                                onChange(text)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "-4dp", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                val cur = parseDimension(text)?.roundToInt() ?: 0
                                val next = cur + 4
                                text = "${next}dp"
                                onChange(text)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "+4dp", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (canExtract) {
                        val resType = when (spec.kind) {
                            AttrKind.COLOR -> ResType.COLOR
                            AttrKind.DIMENSION -> ResType.DIMEN
                            else -> ResType.STRING
                        }
                        IconButton(onClick = { onExtract?.invoke(resType, text) }) {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = stringRes(R.string.layout_extract_resource),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (resolved != null && resolved != text) {
                    Text(
                        text = "= $resolved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                    )
                }
                if (spec.kind == AttrKind.SIZE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
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

private data class ExtractRequest(
    val nodeUid: Int,
    val attrName: String,
    val resType: ResType,
    val currentValue: String,
)

@Composable
private fun ExtractResourceDialog(
    request: ExtractRequest,
    onDismiss: () -> Unit,
    onConfirm: (name: String, value: String) -> Unit,
) {
    var resName by remember {
        mutableStateOf(
            request.attrName.substringAfterLast(':') + "_" +
                request.currentValue.lowercase()
                    .filter { it.isLetterOrDigit() || it == '_' }
                    .take(20)
                    .ifEmpty { "val" }
        )
    }
    var resValue by remember { mutableStateOf(request.currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.layout_extract_resource)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${request.resType.name.lowercase()} / ${request.attrName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = resName,
                    onValueChange = { resName = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    label = { Text(stringRes(R.string.layout_extract_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resValue,
                    onValueChange = { resValue = it },
                    label = { Text("Wert") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (resName.isNotBlank() && resValue.isNotBlank()) onConfirm(resName, resValue) },
                enabled = resName.isNotBlank() && resValue.isNotBlank(),
            ) {
                Text(stringRes(R.string.layout_extract_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        },
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

/** Probleme/Lint-Tab: Liste erkannter Probleme mit Schnellkorrekturen. */
@Composable
internal fun ProblemsPanel(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var severityFilter by remember { mutableStateOf<IssueSeverity?>(null) }
    val filteredIssues = remember(state.issues, severityFilter) {
        if (severityFilter == null) state.issues else state.issues.filter { it.severity == severityFilter }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = severityFilter == null,
                onClick = { severityFilter = null },
                label = { Text("${stringRes(R.string.layout_filter_all_issues)} (${state.issues.size})") },
            )
            val errorCount = state.issues.count { it.severity == IssueSeverity.ERROR }
            FilterChip(
                selected = severityFilter == IssueSeverity.ERROR,
                onClick = { severityFilter = IssueSeverity.ERROR },
                label = { Text("${stringRes(R.string.layout_filter_errors)} ($errorCount)") },
            )
            val warnCount = state.issues.count { it.severity == IssueSeverity.WARNING }
            FilterChip(
                selected = severityFilter == IssueSeverity.WARNING,
                onClick = { severityFilter = IssueSeverity.WARNING },
                label = { Text("${stringRes(R.string.layout_filter_warnings)} ($warnCount)") },
            )
            val infoCount = state.issues.count { it.severity == IssueSeverity.INFO }
            FilterChip(
                selected = severityFilter == IssueSeverity.INFO,
                onClick = { severityFilter = IssueSeverity.INFO },
                label = { Text("${stringRes(R.string.layout_filter_info)} ($infoCount)") },
            )
        }

        Spacer(Modifier.height(12.dp))

        if (filteredIssues.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringRes(R.string.layout_no_issues),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredIssues) { issue ->
                    val node = state.document.find(issue.uid)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (icon, tint) = when (issue.severity) {
                                    IssueSeverity.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                                    IssueSeverity.WARNING -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
                                    IssueSeverity.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
                                }
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = issue.ruleId,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tint,
                                    modifier = Modifier.weight(1f),
                                )
                                if (node != null) {
                                    Text(
                                        text = "<${node.tag.substringAfterLast('.')}>",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = issue.message,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onEvent(LayoutDesignerUiEvent.JumpToIssue(issue)) }) {
                                    Text(stringRes(R.string.layout_jump_to_element))
                                }
                                if (issue.quickFix != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { onEvent(LayoutDesignerUiEvent.ApplyQuickFix(issue.quickFix)) }) {
                                        Text(issue.quickFix.title)
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
