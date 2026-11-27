/**
 * Plugin: String-Editor – Zustand (eine Datei zur Zeit) und Laden/Speichern über die IDE-API.
 */
package com.codeforge.plugin.strings

import com.codeforge.libs.plugin_api.IdeApi
import com.codeforge.libs.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Zustand des Editors (unveränderlich). */
data class StringEditorUiState(
    val path: String? = null,
    val file: StringFile? = null,
    val doc: ResDocument? = null,
    /** Dateitext beim Laden bzw. letzten Speichern. */
    val savedXml: String = "",
    /** Texte der Standardsprache (bei Übersetzungen), Name → Text. */
    val defaults: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean get() = doc != null && doc.toXml() != savedXml

    /** Namen der Standardsprache, die in dieser Übersetzung fehlen. */
    val missing: List<String>
        get() {
            val have = doc?.strings?.mapTo(HashSet()) { it.name } ?: return emptyList()
            return defaults.keys.filter { it !in have }
        }
}

/** Liste der String-Dateien für die Seitenleiste. */
data class StringFilesUiState(val files: List<StringFile> = emptyList(), val scanning: Boolean = false, val root: String? = null)

/** Hält Dateiliste und offene Datei; Oberfläche und Befehle teilen sich eine Instanz. */
class StringEditorState(val context: PluginContext, val ide: IdeApi, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(StringEditorUiState())
    val ui: StateFlow<StringEditorUiState> = _ui

    private val _files = MutableStateFlow(StringFilesUiState())
    val files: StateFlow<StringFilesUiState> = _files

    /** Sucht die String-Dateien des Projekts (im Hintergrund). */
    fun scan() {
        if (_files.value.scanning) return
        _files.update { it.copy(scanning = true) }
        scope.launch {
            val root = runCatching { ide.files.projectRoot() }.getOrNull()
            val found = withContext(Dispatchers.IO) {
                root?.let { r -> StringFileScanner.scan(r) { ide.files.list(it) } }.orEmpty()
            }
            _files.value = StringFilesUiState(found, scanning = false, root = root)
        }
    }

    /** Öffnet [path]; ungespeicherte Änderungen an einer anderen Datei gehen verloren (Oberfläche fragt vorher). */
    fun open(path: String) {
        if (path == _ui.value.path && _ui.value.doc != null) return
        val root = _files.value.root ?: runCatching { ide.files.projectRoot() }.getOrNull().orEmpty()
        val file = StringFile.of(root, path)
        _ui.value = StringEditorUiState(path = path, file = file, loading = true)
        scope.launch { load(path, file) }
    }

    /** Lädt die offene Datei neu (verwirft Änderungen). */
    fun reload() {
        val s = _ui.value
        val path = s.path ?: return
        _ui.value = s.copy(loading = true, error = null)
        scope.launch { load(path, s.file) }
    }

    private suspend fun load(path: String, file: StringFile?) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = if (ide.files.exists(path)) ide.files.read(path) else ResDocument.EMPTY.toXml()
                val defaults = file?.takeIf { it.qualifier.isNotEmpty() && ide.files.exists(it.defaultPath) }
                    ?.let { f -> runCatching { ResParser.parse(ide.files.read(f.defaultPath)) }.getOrNull() }
                    ?.strings?.filter { it.translatable }?.associate { it.name to it.text }.orEmpty()
                Triple(text, ResParser.parse(text), defaults)
            }
        }
        if (_ui.value.path != path) return
        result
            .onSuccess { (text, doc, defaults) -> _ui.value = StringEditorUiState(path, file, doc, text, defaults) }
            .onFailure { e -> _ui.value = StringEditorUiState(path, file, error = e.message ?: e.javaClass.simpleName) }
    }

    /** Ändert das Dokument. */
    fun edit(change: (ResDocument) -> ResDocument) {
        _ui.update { s -> s.doc?.let { s.copy(doc = change(it), error = null) } ?: s }
    }

    /** Ersetzt das Dokument durch geparsten [xml]; `null` bei Erfolg, sonst die Fehlermeldung. */
    fun applyXml(xml: String): String? = runCatching { ResParser.parse(xml) }
        .onSuccess { doc -> _ui.update { it.copy(doc = doc, error = null) } }
        .exceptionOrNull()?.let { it.message ?: "Ungültiges XML" }

    /** Speichert die offene Datei; `null` bei Erfolg, sonst die Fehlermeldung. */
    suspend fun save(): String? {
        val s = _ui.value
        val path = s.path ?: return "Keine Datei"
        var doc = s.doc ?: return "Nichts zu speichern"
        if (context.settings().getBoolean(SORT_ON_SAVE)) doc = doc.sortStrings()
        val xml = doc.toXml()
        return runCatching {
            withContext(Dispatchers.IO) { ide.files.write(path, xml) }
            _ui.update { if (it.path == path) it.copy(doc = doc, savedXml = xml) else it }
            runCatching { ide.events.publish("saved", mapOf("path" to path, "strings" to doc.strings.size.toString())) }
            null
        }.getOrElse { it.message ?: "Speichern fehlgeschlagen" }
    }

    /** Andere Sprachen derselben Datei (gleicher `res`-Ordner und Dateiname). */
    fun siblings(): List<StringFile> {
        val f = _ui.value.file ?: return emptyList()
        return _files.value.files.filter { it.resDir == f.resDir && it.fileName == f.fileName }
    }

    companion object {
        const val SORT_ON_SAVE = "sortOnSave"
        const val SHOW_DEFAULT = "showDefault"
    }
}
