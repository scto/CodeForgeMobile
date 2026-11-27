// Modul: :libs:plugin-impl
package com.codeforge.libs.plugin_impl.ide

import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ide.IdeSettingInfo
import com.codeforge.core.domain.extension.ide.IdeSettingsAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Freigegebene App-Einstellungen für Plugins (API 5). Bewusst eine feste Tabelle statt Zugriff auf das ganze
 * Protobuf: jeder Schlüssel hat einen geprüften Typ, „…_disabled“-Felder erscheinen positiv (`editor.minimap`).
 */
@Singleton
class IdeSettingsAccessImpl @Inject constructor(private val repository: SettingsRepository) : IdeSettingsAccess {

    /** Eine Einstellung: Lesen aus [AppSettings], Schreiben über das passende `update…` des Repositorys. */
    internal class Setting(
        val info: IdeSettingInfo,
        val read: (AppSettings) -> String,
        val write: suspend (SettingsRepository, String) -> Boolean
    )

    internal val table: Map<String, Setting> = buildTable().associateBy { it.info.key }

    override val changes: Flow<String> = flow {
        var previous: Map<String, String>? = null
        repository.appSettings.collect { settings ->
            val current = table.mapValues { (_, v) -> v.read(settings) }
            previous?.let { p -> current.forEach { (k, v) -> if (p[k] != v) emit(k) } }
            previous = current
        }
    }

    override fun keys(): List<IdeSettingInfo> = table.values.map { it.info }

    override suspend fun get(key: String): String? = table[key]?.read?.invoke(repository.appSettings.first())

    override suspend fun set(key: String, value: String): Boolean = table[key]?.write?.invoke(repository, value.trim()) ?: false

    internal companion object {
        private const val BOOL = "boolean"
        private const val INT = "int"
        private const val TEXT = "string"

        private fun bool(v: String): Boolean? = v.lowercase().toBooleanStrictOrNull()
        private fun int(v: String, range: IntRange): Int? = v.toIntOrNull()?.takeIf { it in range }

        @Suppress("LongMethod")
        fun buildTable(): List<Setting> = listOf(
            Setting(IdeSettingInfo("theme.mode", "system|light|dark", "App-Theme"), { it.theme.mode.name.lowercase() }) { r, v ->
                val mode = ThemeMode.values().firstOrNull { it.name.equals(v, true) && it != ThemeMode.UNRECOGNIZED }
                mode?.let { m -> r.updateTheme { it.toBuilder().setMode(m).build() } } != null
            },
            Setting(IdeSettingInfo("theme.colorScheme", TEXT, "Farbschema-ID"), { it.theme.colorSchemeId }) { r, v ->
                r.updateTheme { it.toBuilder().setColorSchemeId(v).build() }; true
            },
            Setting(IdeSettingInfo("theme.dynamicColor", BOOL, "Dynamische Farben (Material You)"), { it.theme.useDynamicColor.toString() }) { r, v ->
                bool(v)?.let { b -> r.updateTheme { it.toBuilder().setUseDynamicColor(b).build() } } != null
            },
            Setting(IdeSettingInfo("theme.iconTheme", TEXT, "Datei-Icon-Theme"), { it.theme.iconThemeId }) { r, v ->
                r.updateTheme { it.toBuilder().setIconThemeId(v).build() }; true
            },
            editorInt("editor.tabSize", "Tabulatorbreite", 1..16, { tabSize }) { setTabSize(it) },
            editorInt("editor.fontSize", "Schriftgröße (sp)", 6..48, { fontSize }) { setFontSize(it) },
            editorInt("editor.lineSpacingPercent", "Zeilenabstand in Prozent", 50..300, { lineSpacingPercent }) { setLineSpacingPercent(it) },
            editorInt("editor.rulerColumn", "Lineal-Spalte (0 = aus)", 0..400, { rulerColumn }) { setRulerColumn(it) },
            Setting(IdeSettingInfo("editor.fontFamily", TEXT, "Schriftart"), { it.editor.fontFamily }) { r, v ->
                r.updateEditor { it.toBuilder().setFontFamily(v).build() }; true
            },
            editorBool("editor.wordWrap", "Zeilenumbruch", { wordWrap }) { setWordWrap(it) },
            editorBool("editor.showNonPrintable", "Unsichtbare Zeichen zeigen", { showNonPrintableChars }) { setShowNonPrintableChars(it) },
            editorBool("editor.stickyScroll", "Mitlaufende Kopfzeilen", { stickyScrollEnabled }) { setStickyScrollEnabled(it) },
            editorBool("editor.minimap", "Minimap", { minimapEnabled }) { setMinimapEnabled(it) },
            editorBool("editor.rainbowBrackets", "Farbige Klammerpaare", { rainbowBracketsEnabled }) { setRainbowBracketsEnabled(it) },
            editorBool("editor.hardTabs", "Echte Tabulatoren", { hardTabs }) { setHardTabs(it) },
            editorBool("editor.formatOnPaste", "Beim Einfügen formatieren", { formatOnPaste }) { setFormatOnPaste(it) },
            editorBool("editor.formatOnSave.kotlin", "Kotlin beim Speichern formatieren", { formatOnSaveKotlin }) { setFormatOnSaveKotlin(it) },
            editorBool("editor.formatOnSave.java", "Java beim Speichern formatieren", { formatOnSaveJava }) { setFormatOnSaveJava(it) },
            editorBool("editor.formatOnSave.xml", "XML beim Speichern formatieren", { formatOnSaveXml }) { setFormatOnSaveXml(it) },
            editorBool("editor.formatOnSave.json", "JSON beim Speichern formatieren", { formatOnSaveJson }) { setFormatOnSaveJson(it) },
            editorBool("editor.lineNumbers", "Zeilennummern", { !lineNumbersHidden }) { setLineNumbersHidden(!it) },
            editorBool("editor.codeFolding", "Code-Faltung", { !codeFoldingDisabled }) { setCodeFoldingDisabled(!it) },
            editorBool("editor.inlayHints", "Inlay-Hinweise", { !inlayHintsDisabled }) { setInlayHintsDisabled(!it) },
            editorBool("editor.indentGuides", "Einrückungslinien", { !indentGuidesDisabled }) { setIndentGuidesDisabled(!it) },
            editorBool("editor.currentLineHighlight", "Aktuelle Zeile hervorheben", { !currentLineHighlightDisabled }) {
                setCurrentLineHighlightDisabled(!it)
            },
            editorBool("editor.cursorBlink", "Blinkender Cursor", { !cursorBlinkDisabled }) { setCursorBlinkDisabled(!it) },
            editorBool("editor.readOnlyDefault", "Dateien schreibgeschützt öffnen", { readOnlyDefault }) { setReadOnlyDefault(it) },
            Setting(IdeSettingInfo("terminal.fontSize", INT, "Terminal-Schriftgröße"), { it.terminal.fontSize.toString() }) { r, v ->
                int(v, 6..48)?.let { n -> r.updateTerminal { it.toBuilder().setFontSize(n).build() } } != null
            },
            Setting(IdeSettingInfo("git.gutterMarkers", BOOL, "Git-Markierungen im Rand"), { (!it.git.gutterMarkersDisabled).toString() }) { r, v ->
                bool(v)?.let { b -> r.updateGit { it.toBuilder().setGutterMarkersDisabled(!b).build() } } != null
            },
            Setting(IdeSettingInfo("fileTree.showHidden", BOOL, "Versteckte Dateien zeigen"), { (!it.fileTree.hideHidden).toString() }) { r, v ->
                bool(v)?.let { b -> r.updateFileTree { it.toBuilder().setHideHidden(!b).build() } } != null
            },
        )

        private fun editorBool(
            key: String,
            description: String,
            read: com.codeforge.core.datastore.proto.EditorConfig.() -> Boolean,
            write: com.codeforge.core.datastore.proto.EditorConfig.Builder.(Boolean) -> Unit
        ) = Setting(IdeSettingInfo(key, BOOL, description), { it.editor.read().toString() }) { r, v ->
            bool(v)?.let { b -> r.updateEditor { it.toBuilder().apply { write(b) }.build() } } != null
        }

        private fun editorInt(
            key: String,
            description: String,
            range: IntRange,
            read: com.codeforge.core.datastore.proto.EditorConfig.() -> Int,
            write: com.codeforge.core.datastore.proto.EditorConfig.Builder.(Int) -> Unit
        ) = Setting(IdeSettingInfo(key, "$INT ${range.first}..${range.last}", description), { it.editor.read().toString() }) { r, v ->
            int(v, range)?.let { n -> r.updateEditor { it.toBuilder().apply { write(n) }.build() } } != null
        }
    }
}
