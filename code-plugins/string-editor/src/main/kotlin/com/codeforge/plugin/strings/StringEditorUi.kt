/**
 * Plugin: String-Editor – Oberflächen (Compose, Material 3, App-Theme): Seitenleiste und Editor-Bildschirm.
 *
 * Arbeit (Dateizugriff) läuft im Zustand ([StringEditorState]) in Coroutinen; Composables zeigen nur an.
 */
package com.codeforge.plugin.strings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_ui_api.PluginScreen
import com.codeforge.libs.plugin_ui_api.PluginSidebarScope
import com.codeforge.libs.plugin_ui_api.PluginSidebarView
import com.codeforge.libs.plugin_ui_api.PluginUiScope
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Seitenleiste

/** Symbol „Strings“ in der Seitenleiste: alle String-Dateien des Projekts, nach Modul und Sprache. */
class StringsSidebar(private val state: StringEditorState) : PluginSidebarView {
    override val id = "strings"
    override val title = "Strings"
    override val icon: ImageVector = Icons.Filled.Translate

    @Composable
    override fun Content(scope: PluginSidebarScope, modifier: Modifier) {
        val files by state.files.collectAsState()
        val ui by state.ui.collectAsState()
        LaunchedEffect(scope.projectRoot) { if (files.root != scope.projectRoot) state.scan() }
        val groups = remember(files.files) { files.files.groupBy { listOf(it.module.ifEmpty { "(Projekt)" }, it.sourceSet).filter(String::isNotEmpty).joinToString(" · ") } }

        Column(modifier) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("String-Ressourcen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (files.scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = { state.scan() }) { Icon(Icons.Filled.Refresh, contentDescription = "Neu einlesen") }
            }
            if (!files.scanning && files.files.isEmpty()) {
                Text(
                    "Keine strings.xml gefunden (gesucht in */src/*/res/values*/).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                groups.forEach { (group, list) ->
                    item(key = "h:$group") {
                        Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.path }) { f ->
                        val open = f.path == ui.path
                        Row(
                            Modifier.fillMaxWidth().clickable { state.open(f.path); scope.openScreen(StringEditorScreen.ID) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.label() + if (open && ui.dirty) " •" else "", fontWeight = if (open) FontWeight.SemiBold else null)
                                if (f.fileName != "strings.xml") Text(f.fileName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Editor-Bildschirm

/** Editor einer String-Datei: Liste (visuell) und XML. */
class StringEditorScreen(private val state: StringEditorState) : PluginScreen {
    override val id = ID
    override val title = "String-Editor"

    @Composable
    override fun Content(scope: PluginUiScope, modifier: Modifier) {
        val ui by state.ui.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        var tab by remember { mutableIntStateOf(0) }
        Box(modifier) {
            Column(Modifier.fillMaxSize()) {
                EditorHeader(state, ui, snackbar)
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Liste") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("XML") })
                }
                when {
                    ui.path == null -> Hint("Datei in der Seitenleiste unter „Strings“ wählen.")
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ui.doc == null -> Hint("Datei lässt sich nicht lesen: ${ui.error}")
                    tab == 0 -> StringList(state, ui, snackbar)
                    else -> XmlTab(state, ui)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        }
    }

    companion object {
        const val ID = "editor"
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
}

/** Datei, Sprachwahl, Speichern, Sortieren, im Code-Editor öffnen. */
@Composable
private fun EditorHeader(state: StringEditorState, ui: StringEditorUiState, snackbar: SnackbarHostState) {
    val coroutines = rememberCoroutineScope()
    var languages by remember { mutableStateOf(false) }
    var pendingSwitch by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = ui.file != null) { languages = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((ui.file?.label() ?: "–") + if (ui.dirty) " •" else "", style = MaterialTheme.typography.titleMedium)
                if (ui.file != null) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Sprache wählen")
            }
            Text(
                ui.file?.let { listOf(it.module, it.sourceSet, it.fileName).filter(String::isNotEmpty).joinToString(" · ") } ?: ui.path.orEmpty(),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            DropdownMenu(expanded = languages, onDismissRequest = { languages = false }) {
                state.siblings().forEach { f ->
                    DropdownMenuItem(text = { Text(f.label()) }, onClick = {
                        languages = false
                        if (ui.dirty && f.path != ui.path) pendingSwitch = f.path else state.open(f.path)
                    })
                }
            }
        }
        IconButton(enabled = ui.doc != null, onClick = { state.edit { it.sortStrings() } }) {
            Icon(Icons.Filled.SortByAlpha, contentDescription = "Alphabetisch sortieren")
        }
        IconButton(enabled = ui.path != null, onClick = {
            coroutines.launch { ui.path?.let { runCatching { state.ide.editor.open(it) } } }
        }) { Icon(Icons.Filled.Code, contentDescription = "Im Code-Editor öffnen") }
        IconButton(enabled = ui.dirty, onClick = {
            coroutines.launch { snackbar.showSnackbar(state.save() ?: "Gespeichert") }
        }) { Icon(Icons.Filled.Save, contentDescription = "Speichern") }
    }
    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Die aktuelle Datei hat ungespeicherte Änderungen.") },
            confirmButton = { TextButton(onClick = { pendingSwitch = null; state.open(target) }) { Text("Verwerfen") } },
            dismissButton = {
                TextButton(onClick = {
                    pendingSwitch = null
                    coroutines.launch { if (state.save() == null) state.open(target) }
                }) { Text("Speichern") }
            },
        )
    }
}

/** Visuelle Liste mit Suche, fehlenden Übersetzungen und „Neuer String“. */
@Composable
private fun StringList(state: StringEditorState, ui: StringEditorUiState, snackbar: SnackbarHostState) {
    val doc = ui.doc ?: return
    val coroutines = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val showDefault = remember { state.context.settings().getBoolean(StringEditorState.SHOW_DEFAULT) }
    val visible by remember(doc, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            doc.entries.filter { e ->
                q.isEmpty() || (e.name?.lowercase()?.contains(q) == true) ||
                    (e is StringEntry && e.text.lowercase().contains(q)) || (e is RawEntry && e.summary.lowercase().contains(q))
            }
        }
    }
    val missing = ui.missing

    fun delete(entry: StringEntry) {
        state.edit { it.remove(entry.name) }
        coroutines.launch {
            if (snackbar.showSnackbar("„${entry.name}“ gelöscht", "Rückgängig", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                state.edit { it.put(null, entry) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "search") {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    label = { Text("${doc.strings.size} Strings durchsuchen") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            if (missing.isNotEmpty()) {
                item(key = "missing") {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text("${missing.size} fehlende Übersetzungen – antippen zum Ergänzen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            missing.take(MAX_MISSING_CHIPS).forEach { name ->
                                AssistChip(onClick = { editing = EditTarget(null, name, ui.defaults[name].orEmpty(), true) }, label = { Text(name) })
                            }
                        }
                    }
                }
            }
            items(visible, key = { e -> e.name?.let { "n:$it" } ?: "x:${System.identityHashCode(e)}" }) { e ->
                when (e) {
                    is StringEntry -> StringRow(
                        entry = e,
                        defaultText = ui.defaults[e.name]?.takeIf { showDefault },
                        onEdit = { editing = EditTarget(e.name, e.name, e.text, e.translatable, e.hasMarkup) },
                        onMove = { delta -> state.edit { it.move(e.name, delta) } },
                        onToggleTranslatable = { state.edit { d -> d.put(e.name, e.withTranslatable(!e.translatable)) } },
                        onDelete = { delete(e) },
                    )
                    is RawEntry -> RawRow(e)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = EditTarget(null, "", "", true) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Neuer String") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { target ->
        EditDialog(
            target = target,
            taken = doc.strings.map { it.name }.filter { it != target.originalName },
            defaultText = ui.defaults[target.originalName ?: target.name],
            onDismiss = { editing = null },
            onSave = { name, text, translatable ->
                editing = null
                state.edit { d ->
                    val old = target.originalName?.let { n -> d.strings.firstOrNull { it.name == n } }
                    val entry = (old ?: StringEntry(name, "")).copy(name = name).withText(text).withTranslatable(translatable)
                    d.put(target.originalName, entry)
                }
            },
        )
    }
}

/** Was der Bearbeiten-Dialog zeigt; [originalName] `null` = neuer Eintrag. */
private data class EditTarget(val originalName: String?, val name: String, val text: String, val translatable: Boolean, val markup: Boolean = false)

@Composable
private fun StringRow(
    entry: StringEntry,
    defaultText: String?,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleTranslatable: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.text.ifEmpty { "(leer)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (defaultText != null && defaultText != entry.text) {
                Text("Standard: $defaultText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val badges = listOfNotNull("nicht übersetzbar".takeIf { !entry.translatable }, "Formatierung".takeIf { entry.hasMarkup })
            if (badges.isNotEmpty()) Text(badges.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Aktionen") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val actions: List<Pair<String, () -> Unit>> = listOf(
                    "Bearbeiten" to onEdit,
                    "Wert kopieren" to { clipboard.setText(AnnotatedString(entry.text)) },
                    "@string/${entry.name} kopieren" to { clipboard.setText(AnnotatedString("@string/${entry.name}")) },
                    "R.string.${entry.name} kopieren" to { clipboard.setText(AnnotatedString("R.string.${entry.name}")) },
                    (if (entry.translatable) "Als nicht übersetzbar markieren" else "Als übersetzbar markieren") to onToggleTranslatable,
                    "Nach oben" to { onMove(-1) },
                    "Nach unten" to { onMove(1) },
                )
                actions.forEach { (label, block) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; block() }) }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Kommentar oder anderes Element (Array, Plurals …): nur Anzeige, Bearbeitung im XML-Reiter. */
@Composable
private fun RawRow(e: RawEntry) {
    if (e.tag == null) {
        Text(
            e.xml.removePrefix("<!--").removeSuffix("-->").trim(),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("${e.name ?: e.tag}  ·  <${e.tag}>", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (e.summary.isNotBlank()) {
            Text(e.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EditDialog(
    target: EditTarget,
    taken: List<String>,
    defaultText: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, text: String, translatable: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(target.name) }
    var text by remember { mutableStateOf(target.text) }
    var translatable by remember { mutableStateOf(target.translatable) }
    val nameError = remember(name, taken) { StringValidator.nameError(name, taken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.originalName == null) "Neuer String" else "String bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.trim() }, singleLine = true, label = { Text("Name") },
                    isError = nameError != null && name.isNotEmpty(),
                    supportingText = { if (nameError != null && name.isNotEmpty()) Text(nameError) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    text, { text = it }, label = { Text("Text") },
                    textStyle = if (target.markup) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                if (target.markup) {
                    Text("Enthält Formatierung (<b>, <xliff:g> …) – wird als XML bearbeitet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Apostrophe, Anführungszeichen, & und Zeilenumbrüche werden beim Speichern maskiert.", style = MaterialTheme.typography.bodySmall)
                }
                if (defaultText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Standard: $defaultText", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { text = defaultText }) { Text("Übernehmen") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Übersetzbar", modifier = Modifier.weight(1f))
                    Switch(checked = translatable, onCheckedChange = { translatable = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = nameError == null, onClick = { onSave(name, text, translatable) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Ganze Datei als XML bearbeiten; „Übernehmen“ prüft und überträgt in die Liste. */
@Composable
private fun XmlTab(state: StringEditorState, ui: StringEditorUiState) {
    val current = remember(ui.doc) { ui.doc?.toXml().orEmpty() }
    var draft by remember(current) { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = draft != current, onClick = { error = state.applyXml(draft) }) { Text("Übernehmen") }
            TextButton(enabled = draft != current, onClick = { draft = current; error = null }) { Text("Zurücksetzen") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(
            draft, { draft = it },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        )
    }
}

private const val MAX_MISSING_CHIPS = 50
