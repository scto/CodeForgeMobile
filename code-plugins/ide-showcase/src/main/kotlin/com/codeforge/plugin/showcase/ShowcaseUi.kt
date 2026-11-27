/**
 * Plugin: IDE-Werkzeugkasten – Oberflächen (Compose, Material 3, App-Theme).
 *
 * Alle Aufrufe der IDE laufen in Coroutinen mit `runCatching`: Ausnahmen in der Komposition würden die App beenden.
 */
package com.codeforge.plugin.showcase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.libs.plugin_api.IdeGitStatus
import com.codeforge.libs.plugin_ui_api.PluginScreen
import com.codeforge.libs.plugin_ui_api.PluginSettingsPage
import com.codeforge.libs.plugin_ui_api.PluginToolWindow
import com.codeforge.libs.plugin_ui_api.PluginUiScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Werkzeugfenster: Projektbaum

/** Baumansicht des Projekts mit Git-Zustand; Tippen auf eine Datei öffnet sie im Editor. */
class TreeToolWindow(private val state: ShowcaseState) : PluginToolWindow {
    override val id = ID
    override val title = "Projektbaum"

    @Composable
    override fun Content(scope: PluginUiScope, modifier: Modifier) {
        val ide = state.ide
        val coroutines = rememberCoroutineScope()
        var expanded by remember { mutableStateOf(emptySet<String>()) }
        var rows by remember { mutableStateOf(emptyList<TreeRow>()) }
        var error by remember { mutableStateOf<String?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        val events by state.logVersion.collectAsState()

        LaunchedEffect(expanded, reload, events / REFRESH_EVERY) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val root = ide.files.projectRoot() ?: error("Kein Projekt geöffnet")
                    val git = ProjectTree.gitMap(runCatching { ide.git.status() }.getOrNull())
                    val limit = state.context.settings().getInt("treeLimit").coerceIn(MIN_ROWS, MAX_ROWS)
                    ProjectTree.rows(root, expanded, git, limit) { ide.files.list(it) }
                }
            }
            result.onSuccess { rows = it; error = null }.onFailure { error = it.message }
        }

        Column(modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${rows.size} Einträge", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = emptySet() }) { Text("Alle zuklappen") }
                TextButton(onClick = { reload++ }) { Text("Neu laden") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows, key = { it.path }) { row ->
                    TreeRowItem(row) {
                        if (row.isDirectory) {
                            expanded = if (row.expanded) expanded.filterNot { it == row.path || it.startsWith(row.path + "/") }.toSet()
                            else expanded + row.path
                        } else {
                            coroutines.launch {
                                if (runCatching { ide.editor.open(row.path) }.getOrDefault(false)) scope.close()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "tree"
        private const val MIN_ROWS = 100
        private const val MAX_ROWS = 5000
        private const val REFRESH_EVERY = 5
    }
}

@Composable
private fun TreeRowItem(row: TreeRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (!row.isDirectory) "  " else if (row.expanded) "▾" else "▸", modifier = Modifier.width(16.dp))
        Text(if (row.isDirectory) "📁" else "📄")
        Spacer(Modifier.width(6.dp))
        Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        row.git?.let { g ->
            Text(
                gitBadge(g),
                style = MaterialTheme.typography.labelSmall,
                color = if (g == "STAGED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun gitBadge(state: String): String = when (state) {
    "MODIFIED", "CHANGED" -> "M"
    "UNTRACKED" -> "U"
    "ADDED" -> "A"
    "DELETED", "MISSING", "REMOVED" -> "D"
    "STAGED" -> "S"
    "CONFLICTING" -> "!"
    else -> state.take(1)
}

// ------------------------------------------------------------------ Bildschirm: Übersicht

/** Übersicht mit Registerkarten für jeden Bereich der API 5. */
class DashboardScreen(private val state: ShowcaseState) : PluginScreen {
    override val id = ID
    override val title = "IDE-Werkzeugkasten"

    @Composable
    override fun Content(scope: PluginUiScope, modifier: Modifier) {
        var tab by remember { mutableIntStateOf(0) }
        var message by remember { mutableStateOf<String?>(null) }
        Column(modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            val say: (String?) -> Unit = { message = it }
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                when (tab) {
                    0 -> EditorTab(state, say)
                    1 -> GitTab(state, say)
                    2 -> CommandsTab(state, say)
                    3 -> EventsTab(state)
                    4 -> AppSettingsTab(state, say)
                    else -> NavigationTab(state)
                }
            }
        }
    }

    companion object {
        const val ID = "dashboard"
        private val TABS = listOf("Editor", "Git", "Befehle", "Ereignisse", "Einstellungen", "Navigation")
    }
}

/** Führt [block] im Hintergrund aus und meldet Ergebnis oder Fehler über [say]. */
@Composable
private fun runner(say: (String?) -> Unit): (suspend () -> String?) -> Unit {
    val scope = rememberCoroutineScope()
    return { block -> scope.launch { say(runCatching { block() }.getOrElse { "Fehler: ${it.message}" }) } }
}

@Composable
private fun EditorTab(state: ShowcaseState, say: (String?) -> Unit) {
    val ide = state.ide
    val run = runner(say)
    var snap by remember { mutableStateOf(IdeEditorSnapshot()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { snap = runCatching { ide.editor.snapshot() }.getOrDefault(IdeEditorSnapshot()) }
    Text(snap.path ?: "Keine Datei geöffnet", style = MaterialTheme.typography.titleSmall)
    Text("Cursor ${snap.caret} · Auswahl ${snap.selectionStart}–${snap.selectionEnd} · ${snap.text.length} Zeichen" + if (snap.dirty) " · geändert" else "")
    Text("Offen: ${snap.openFiles.size} Dateien", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.padding(4.dp))
    ButtonRow {
        OutlinedButton(onClick = { reload++ }) { Text("Aktualisieren") }
        Button(onClick = { run { ide.commands.execute("${state.pluginId}.upper") ?: "Großgeschrieben" } }) { Text("Auswahl GROSS") }
        Button(onClick = { run { ide.commands.execute("${state.pluginId}.todo"); "TODO eingefügt" } }) { Text("TODO") }
        OutlinedButton(onClick = { run { if (ide.editor.save()) "Gespeichert" else "Nicht gespeichert" } }) { Text("Speichern") }
        OutlinedButton(onClick = { run { state.appendNote(null) } }) { Text("Notiz") }
    }
    snap.openFiles.forEach { f ->
        Text(f.substringAfterLast('/'), modifier = Modifier.fillMaxWidth().clickable { run { if (ide.editor.open(f)) "Geöffnet" else "Fehlgeschlagen" } }.padding(vertical = 6.dp))
    }
}

@Composable
private fun GitTab(state: ShowcaseState, say: (String?) -> Unit) {
    val ide = state.ide
    val run = runner(say)
    var status by remember { mutableStateOf<IdeGitStatus?>(null) }
    var branches by remember { mutableStateOf(emptyList<String>()) }
    var diff by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { status = ide.git.status(); branches = ide.git.branches() }.onFailure { say("Git: ${it.message}") }
    }
    val s = status
    if (s == null) {
        Text("Kein Git-Repository oder noch nicht geladen")
        return
    }
    Text(TextOps.gitSummary(s), style = MaterialTheme.typography.titleSmall)
    Text("Branches: " + branches.joinToString(), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(message, { message = it }, label = { Text("Commit-Nachricht") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ButtonRow {
        Button(enabled = message.isNotBlank(), onClick = { run { state.commitAll(message).also { reload++; message = "" } } }) { Text("Alles committen") }
        OutlinedButton(onClick = { reload++ }) { Text("Aktualisieren") }
        OutlinedButton(onClick = { ide.navigation.navigate(IdeNavTarget.Git()) }) { Text("Git-Ansicht") }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        val entries = s.staged.map { it to "S" } + s.changes.map { it.key to gitBadge(it.value) }
        items(entries, key = { it.second + it.first }) { (path, badge) ->
            Text("$badge  $path", modifier = Modifier.fillMaxWidth().clickable {
                run { diff = ide.git.diff(path, staged = badge == "S").ifBlank { "(kein Diff)" }; null }
            }.padding(vertical = 6.dp))
        }
    }
    if (diff.isNotEmpty()) {
        HorizontalDivider()
        Text(diff.take(MAX_DIFF), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
}

@Composable
private fun CommandsTab(state: ShowcaseState, say: (String?) -> Unit) {
    val ide = state.ide
    val run = runner(say)
    var filter by remember { mutableStateOf("") }
    val all = remember { runCatching { ide.commands.list() }.getOrDefault(emptyList()) }
    val shown = remember(filter, all) { all.filter { filter.isBlank() || it.title.contains(filter, true) || it.id.contains(filter, true) } }
    OutlinedTextField(filter, { filter = it }, label = { Text("${all.size} Befehle durchsuchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ButtonRow {
        AssistChip(onClick = { run { ide.commands.execute("cfm.build.run") ?: "Build gestartet" } }, label = { Text("cfm.build.run") })
        AssistChip(onClick = { run { ide.commands.execute("cfm.editor.saveAll") ?: "Alles gespeichert" } }, label = { Text("cfm.editor.saveAll") })
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(shown, key = IdeCommandInfo::id) { cmd ->
            Column(Modifier.fillMaxWidth().clickable { run { ide.commands.execute(cmd.id) ?: "${cmd.title}: ausgeführt" } }.padding(vertical = 6.dp)) {
                Text(cmd.title)
                Text("${cmd.category} · ${cmd.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EventsTab(state: ShowcaseState) {
    val version by state.logVersion.collectAsState()
    val lines = remember(version) { state.log.snapshot() }
    ButtonRow {
        Button(onClick = { state.ide.events.publish("hello", mapOf("from" to "Werkzeugkasten")) }) { Text("Ereignis senden") }
        OutlinedButton(onClick = { state.log.clear(); state.record("Protokoll geleert") }) { Text("Leeren") }
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(lines) { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
private fun AppSettingsTab(state: ShowcaseState, say: (String?) -> Unit) {
    val ide = state.ide
    val run = runner(say)
    val keys = remember { runCatching { ide.settings.keys() }.getOrDefault(emptyList()) }
    var values by remember { mutableStateOf(emptyMap<String, String?>()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { values = keys.associate { it.key to runCatching { ide.settings.get(it.key) }.getOrNull() } }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(keys, key = { it.key }) { info ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(info.key)
                    Text(info.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val v = values[info.key]
                if (info.type == "boolean") {
                    Switch(checked = v == "true", onCheckedChange = { on ->
                        run { (if (ide.settings.set(info.key, on.toString())) "${info.key} = $on" else "Nicht geändert").also { reload++ } }
                    })
                } else {
                    Text(v ?: "–", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NavigationTab(state: ShowcaseState) {
    val nav = state.ide.navigation
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Einstellungen: Editor" to IdeNavTarget.Settings("editor"),
            "Einstellungen: Plugins" to IdeNavTarget.Settings("plugins"),
            "Terminal mit „git status“" to IdeNavTarget.Terminal("git status"),
            "Git-Ansicht" to IdeNavTarget.Git(),
            "Build-Panel" to IdeNavTarget.BuildPanel,
            "Projektbaum (Werkzeugfenster)" to IdeNavTarget.PluginToolWindow(state.pluginId, TreeToolWindow.ID),
        ).forEach { (label, target) ->
            OutlinedButton(onClick = { runCatching { nav.navigate(target) } }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

private const val MAX_DIFF = 20_000

// ------------------------------------------------------------------ Einstellungsseite

/** Eigene Einstellungsseite: schreibt die Werte aus `contributes.settings` über `settings().set`. */
class ShowcaseSettingsPage(private val state: ShowcaseState) : PluginSettingsPage {
    override val title = "IDE-Werkzeugkasten"
    override val summary = "TODO-Name, Statusleiste, Ereignisfilter, Projektbaum"

    @Composable
    override fun Content(scope: PluginUiScope, modifier: Modifier) {
        val settings = state.context.settings()
        val coroutines = rememberCoroutineScope()
        var author by remember { mutableStateOf(settings.getString("author")) }
        var status by remember { mutableStateOf(settings.getBoolean("statusBar")) }
        var filter by remember { mutableStateOf(settings.getString("eventFilter")) }
        var limit by remember { mutableStateOf(settings.getString("treeLimit")) }
        fun save(key: String, value: String) = coroutines.launch { runCatching { settings.set(key, value) } }

        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(author, { author = it; save("author", it) }, label = { Text("Name für TODOs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Git-Zustand in der Statusleiste", modifier = Modifier.weight(1f))
                Switch(checked = status, onCheckedChange = { status = it; save("statusBar", it.toString()) })
            }
            Text("Ereignisprotokoll", style = MaterialTheme.typography.titleSmall)
            ButtonRow {
                listOf("all" to "Alle", "file" to "Dateien", "git" to "Git").forEach { (v, label) ->
                    if (filter == v) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { filter = v; save("eventFilter", v) }) { Text(label) }
                }
            }
            OutlinedTextField(limit, { limit = it.filter(Char::isDigit); save("treeLimit", limit) }, label = { Text("Max. Zeilen im Projektbaum (100–5000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { state.ide.navigation.navigate(IdeNavTarget.PluginScreen(state.pluginId, DashboardScreen.ID)) }) { Text("Werkzeugkasten öffnen") }
        }
    }
}
