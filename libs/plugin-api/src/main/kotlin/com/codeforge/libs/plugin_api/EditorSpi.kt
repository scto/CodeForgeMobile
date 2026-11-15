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
