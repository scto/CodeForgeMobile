/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.registry.ExtensionPoint

/**
 * Seit API 3: Zustand des Editors beim Aufruf einer [EditorAction]. Offsets zählen Zeichen ab Dateianfang,
 * Zeilen und Spalten ab 0.
 */
data class EditorContext(
    /** Absoluter Pfad der aktiven Datei. */
    val filePath: String,
    /** Sprach-ID wie im LSP (`kotlin`, `java`, `xml`, `python` …). */
    val languageId: String,
    /** Aktueller Inhalt des Editors (auch ungespeichert). */
    val text: String,
    /** Cursorposition (= Ende der Auswahl). */
    val cursorOffset: Int,
    /** Beginn der Auswahl; gleich [selectionEnd], wenn nichts ausgewählt ist. */
    val selectionStart: Int,
    /** Ende der Auswahl. */
    val selectionEnd: Int
) {
    /** Ob Text ausgewählt ist. */
    val hasSelection: Boolean get() = selectionStart != selectionEnd

    /** Der ausgewählte Text (leer ohne Auswahl). */
    val selectedText: String get() = text.substring(selectionStart.coerceIn(0, text.length), selectionEnd.coerceIn(0, text.length))

    /** Zeile des Cursors (ab 0). */
    val cursorLine: Int get() = text.take(cursorOffset.coerceIn(0, text.length)).count { it == '\n' }
}

/** Offset des ersten Zeichens der Zeile, in der [offset] liegt. */
fun EditorContext.lineStart(offset: Int): Int = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(-1)).let { it + 1 }

/** Offset hinter dem letzten Zeichen der Zeile, in der [offset] liegt (vor dem Zeilenumbruch). */
fun EditorContext.lineEnd(offset: Int): Int = text.indexOf('\n', offset.coerceIn(0, text.length)).let { if (it < 0) text.length else it }

/** Seit API 3: Ersetzung im Bereich [start] bis [end] (exklusiv) durch [newText]; Offsets beziehen sich auf den Text vor allen Ersetzungen. */
data class TextEdit(val start: Int, val end: Int, val newText: String)

/** Seit API 3: Was eine [EditorAction] bewirkt. Die App wendet die Effekte in der angegebenen Reihenfolge an. */
sealed interface EditorEffect {
    /** Ersetzungen in der aktiven Datei (ein Rückgängig-Schritt). Überlappende Ersetzungen werden abgelehnt. */
    data class ApplyEdits(val edits: List<TextEdit>) : EditorEffect

    /** Cursor an [offset] setzen (nach den Ersetzungen, bezogen auf den neuen Text). */
    data class MoveCaret(val offset: Int) : EditorEffect

    /** Bereich [start] bis [end] auswählen (bezogen auf den neuen Text). */
    data class Select(val start: Int, val end: Int) : EditorEffect

    /** Neue Datei anlegen (darf nicht existieren) und öffnen. [path] absolut oder relativ zum Projekt. */
    data class CreateFile(val path: String, val content: String) : EditorEffect

    /** Datei im Editor öffnen. */
    data class OpenFile(val path: String) : EditorEffect

    /** Kurze Meldung anzeigen. */
    data class ShowMessage(val text: String) : EditorEffect
}

/**
 * Seit API 3: Aktion im Editor. Je nach [kind] erscheint sie in der Befehlspalette (immer), im Refactoring-Menü
 * (`refactor…`, `source…`) oder bei den Schnellkorrekturen (`quickfix`). [perform] läuft außerhalb des UI-Threads
 * mit Zeitlimit (30 s); eine Ausnahme wird im Plugin-Protokoll vermerkt und als Meldung angezeigt.
 */
interface EditorAction {
    /** Eindeutig je Plugin. */
    val id: String

    /** Angezeigter Titel. */
    val title: String

    /** Art wie bei LSP-Code-Actions, z. B. `quickfix`, `refactor`, `refactor.extract`, `refactor.rewrite` oder `source`. */
    val kind: String get() = KIND_REFACTOR

    /** Ob die Aktion für [context] angeboten wird (schnell halten; wird beim Öffnen der Menüs aufgerufen). */
    fun isAvailable(context: EditorContext): Boolean = true

    /** Führt die Aktion aus. */
    suspend fun perform(context: EditorContext): List<EditorEffect>

    companion object {
        /** Schnellkorrektur. */
        const val KIND_QUICKFIX = "quickfix"

        /** Refactoring. */
        const val KIND_REFACTOR = "refactor"

        /** Aktion auf der ganzen Datei (Imports, Sortieren …). */
        const val KIND_SOURCE = "source"
    }
}

/** Seit API 3: Diagnose (Fehler/Warnung) in der aktiven Datei, wie sie der Editor anzeigt. */
data class PluginDiagnostic(
    val message: String,
    /** 1 = Fehler, 2 = Warnung, 3 = Info, 4 = Hinweis (wie LSP). */
    val severity: Int,
    val startOffset: Int,
    val endOffset: Int,
    /** Code des Servers, falls vorhanden (z. B. `UNRESOLVED_REFERENCE`). */
    val code: String? = null,
    /** Quelle, z. B. der Name des Sprachservers. */
    val source: String? = null
)

/** Seit API 3: Liefert Schnellkorrekturen für Diagnosen am Cursor (Alt+Enter bzw. Glühbirne). */
fun interface QuickFixProvider {
    /** Korrekturen für [diagnostics] (alle Diagnosen, die den Cursor berühren); leer = keine. */
    suspend fun fixes(context: EditorContext, diagnostics: List<PluginDiagnostic>): List<EditorAction>
}

/** Seit API 3: Andockstellen für Code-Plugins (siehe [PluginContext.register]). */
object PluginExtensionPoints {
    /** Aktionen im Editor (Palette, Refactoring-Menü, Schnellkorrekturen). Berechtigung `editor`. */
    val EDITOR_ACTION: ExtensionPoint<EditorAction> = ExtensionPoint("editor.action", PluginPermissions.EDITOR)

    /** Schnellkorrekturen zu Diagnosen. Berechtigung `editor`. */
    val QUICK_FIX_PROVIDER: ExtensionPoint<QuickFixProvider> = ExtensionPoint("editor.quickFixProvider", PluginPermissions.EDITOR)
}

/** Seit API 3: Vorschlag der Vervollständigung aus einem Plugin. */
data class PluginCompletionItem(val label: String, val insertText: String = label, val detail: String? = null)

/** Seit API 3: ergänzt die Vervollständigung (zusätzlich zum Sprachserver) – schnell halten, läuft beim Tippen. */
fun interface CompletionProvider {
    /** Vorschläge für die Cursorposition von [context] (höchstens 200 werden übernommen). */
    suspend fun complete(context: EditorContext): List<PluginCompletionItem>
}

/** Seit API 3: Hover-Text (Markdown oder Klartext), wenn der Sprachserver nichts liefert. */
fun interface HoverProvider {
    /** Text für die Cursorposition von [context] oder `null`. */
    suspend fun hover(context: EditorContext): String?
}

/** Seit API 3: Ausgabe einer laufenden [RunTask]. */
interface RunConsole {
    /** Projektordner. */
    val projectRoot: String

    /** Aktive Datei beim Start oder `null`. */
    val activeFilePath: String?

    /** Schreibt eine Zeile in die Ausgabe. */
    fun println(line: String)

    /** Meldet ein Testergebnis (für Aufgaben der Art [RunTask.KIND_TEST]; erscheint in der Zusammenfassung). */
    fun reportTest(name: String, passed: Boolean, message: String? = null)
}

/**
 * Seit API 3: Ausführen- oder Test-Eintrag (Befehlspalette → „Ausführen: …“). [run] läuft im Hintergrund, bis es
 * endet oder der Nutzer abbricht (Coroutine wird abgebrochen); Programme startet es selbst, z. B. über
 * `hostServices.buildShellCommand` (Berechtigung `shell`). Rückgabe = Exit-Code (0 = Erfolg).
 */
interface RunTask {
    /** Eindeutig je Plugin. */
    val id: String

    /** Angezeigter Titel. */
    val title: String

    /** [KIND_RUN] oder [KIND_TEST]. */
    val kind: String get() = KIND_RUN

    /** Ob der Eintrag für das Projekt [projectRoot] angeboten wird. */
    fun appliesTo(projectRoot: String): Boolean = true

    /** Führt die Aufgabe aus. */
    suspend fun run(console: RunConsole): Int

    companion object {
        /** Programm ausführen. */
        const val KIND_RUN = "run"

        /** Tests ausführen (Ergebnisse über [RunConsole.reportTest]). */
        const val KIND_TEST = "test"
    }
}

/**
 * Seit API 3: Stelle in einer Datei. Zeilen und Spalten zählen ab 0 (wie im LSP); das Ende ist exklusiv und
 * darf fehlen (dann = Anfang).
 */
data class PluginLocation(
    /** Absoluter Pfad der Datei. */
    val filePath: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column
)

/** Seit API 3: eine Textänderung in einer (auch nicht geöffneten) Datei; ersetzt den Bereich [range] durch [newText]. */
data class PluginFileEdit(val range: PluginLocation, val newText: String)

/**
 * Seit API 3: „Gehe zur Definition“ für Sprachen ohne Sprachserver. Wird gefragt, wenn kein Server eine Definition
 * liefert; [EditorContext.cursorOffset] ist die Stelle des Symbols. Leere Liste = nichts gefunden.
 */
fun interface DefinitionProvider {
    /** Definitionen des Symbols am Cursor. */
    suspend fun definition(context: EditorContext): List<PluginLocation>
}

/** Seit API 3: „Verwendungen suchen“ für Sprachen ohne Sprachserver (siehe [DefinitionProvider]). */
fun interface ReferenceProvider {
    /** Verwendungen des Symbols am Cursor. */
    suspend fun references(context: EditorContext): List<PluginLocation>
}

/**
 * Seit API 3: „Umbenennen“ für Sprachen ohne Sprachserver. Die App zeigt die Änderungen als Vorschau und wendet
 * sie erst nach Bestätigung an (mit Rückgängig).
 */
fun interface RenameProvider {
    /** Änderungen, die das Symbol am Cursor in [newName] umbenennen; `null`, wenn nicht zuständig. */
    suspend fun rename(context: EditorContext, newName: String): List<PluginFileEdit>?
}

/** Seit API 3: ein einzelner Test in einer Datei; [line] zählt ab 0 und bestimmt, wo der Start-Knopf erscheint. */
data class PluginTestItem(
    /** Eindeutig je Datei; wird [TestProvider.run] übergeben (z. B. `test_add` oder `Klasse::methode`). */
    val id: String,
    /** Angezeigter Name. Ergebnisse aus [RunConsole.reportTest] werden über [id] oder [name] zugeordnet. */
    val name: String,
    val line: Int
)

/**
 * Seit API 3: Tests einer Sprache. Die App zeigt an jeder Testzeile einen Start-Knopf (grün/rot nach dem letzten
 * Lauf) und in der Befehlspalette „Tests dieser Datei ausführen“. Ergebnisse meldet [run] über
 * [RunConsole.reportTest].
 */
interface TestProvider {
    /** Eindeutig je Plugin. */
    val id: String

    /** Ob die Datei Tests dieser Quelle enthalten kann (schnell, ohne Dateizugriff). */
    fun appliesTo(filePath: String, languageId: String): Boolean

    /** Tests im aktuellen Text der Datei; wird nach Änderungen erneut gerufen (2 s Zeitlimit). */
    suspend fun discover(context: EditorContext): List<PluginTestItem>

    /** Führt [tests] aus [filePath] aus (leer = alle Tests der Datei). Rückgabe = Exit-Code (0 = Erfolg). */
    suspend fun run(filePath: String, tests: List<PluginTestItem>, console: RunConsole): Int
}

/** Seit API 3: weitere Andockstellen für Sprachen und Ausführen. */
object PluginLanguagePoints {
    /** Zusätzliche Vervollständigung. Berechtigung `editor`. */
    val COMPLETION_PROVIDER: ExtensionPoint<CompletionProvider> = ExtensionPoint("editor.completionProvider", PluginPermissions.EDITOR)

    /** Hover-Texte. Berechtigung `editor`. */
    val HOVER_PROVIDER: ExtensionPoint<HoverProvider> = ExtensionPoint("editor.hoverProvider", PluginPermissions.EDITOR)

    /** Definitionen, wenn kein Sprachserver antwortet. Berechtigung `editor`. */
    val DEFINITION_PROVIDER: ExtensionPoint<DefinitionProvider> = ExtensionPoint("editor.definitionProvider", PluginPermissions.EDITOR)

    /** Verwendungen, wenn kein Sprachserver antwortet. Berechtigung `editor`. */
    val REFERENCE_PROVIDER: ExtensionPoint<ReferenceProvider> = ExtensionPoint("editor.referenceProvider", PluginPermissions.EDITOR)

    /** Umbenennen, wenn kein Sprachserver antwortet. Berechtigung `editor`. */
    val RENAME_PROVIDER: ExtensionPoint<RenameProvider> = ExtensionPoint("editor.renameProvider", PluginPermissions.EDITOR)

    /** Tests je Datei mit Start-Knöpfen im Editor. Berechtigung `run`. */
    val TEST_PROVIDER: ExtensionPoint<TestProvider> = ExtensionPoint("run.testProvider", PluginPermissions.RUN)

    /** Ausführen- und Test-Einträge. Berechtigung `run`. */
    val RUN_TASK: ExtensionPoint<RunTask> = ExtensionPoint("run.task", PluginPermissions.RUN)
}
