/**
 * Plugin: Text-Werkzeuge (Sandbox)
 *
 * Editor-Aktionen auf der Auswahl (ohne Auswahl: auf der Zeile bzw. der ganzen Datei für Zeilenaktionen).
 * Läuft mit `"sandbox": true` im isolierten Prozess: Es sieht nur den Text, den die App schickt, und liefert
 * Ersetzungen zurück, die die App selbst anwendet.
 */
package com.codeforge.plugin.text

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit

class TextToolsPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        ACTIONS.forEach { context.register(PluginExtensionPoints.EDITOR_ACTION, it) }
    }

    override fun onUnload() = Unit

    private companion object {
        val ACTIONS: List<EditorAction> = listOf(
            word("case.camel", "Schreibweise: camelCase", TextTransforms::camel),
            word("case.pascal", "Schreibweise: PascalCase", TextTransforms::pascal),
            word("case.snake", "Schreibweise: snake_case", TextTransforms::snake),
            word("case.kebab", "Schreibweise: kebab-case", TextTransforms::kebab),
            word("case.constant", "Schreibweise: KONSTANTE", TextTransforms::constant),
            word("case.upper", "Großbuchstaben") { it.uppercase() },
            word("case.lower", "Kleinbuchstaben") { it.lowercase() },
            lines("lines.sort", "Zeilen sortieren (A–Z)") { TextTransforms.sortLines(it) },
            lines("lines.sortDesc", "Zeilen sortieren (Z–A)") { TextTransforms.sortLines(it, descending = true) },
            lines("lines.reverse", "Zeilen umkehren", TextTransforms::reverseLines),
            lines("lines.unique", "Doppelte Zeilen entfernen", TextTransforms::uniqueLines),
            lines("lines.trim", "Leerzeichen am Zeilenende entfernen", TextTransforms::trimLines),
            word("enc.base64", "Base64 kodieren", TextTransforms::base64Encode),
            word("dec.base64", "Base64 dekodieren", TextTransforms::base64Decode),
            word("enc.url", "URL kodieren", TextTransforms::urlEncode),
            word("dec.url", "URL dekodieren", TextTransforms::urlDecode),
            object : EditorAction {
                override val id = "stats"
                override val title = "Zeilen, Wörter, Zeichen zählen"
                override val kind = EditorAction.KIND_SOURCE
                override suspend fun perform(context: EditorContext) =
                    listOf(EditorEffect.ShowMessage(TextTransforms.stats(if (context.hasSelection) context.selectedText else context.text)))
            },
        )

        /** Auswahl, sonst das Wort unter dem Cursor. */
        fun word(id: String, title: String, f: (String) -> String) = object : EditorAction {
            override val id = id
            override val title = title
            override suspend fun perform(context: EditorContext): List<EditorEffect> {
                val (start, end) = if (context.hasSelection) context.selectionStart to context.selectionEnd else wordAt(context.text, context.cursorOffset)
                if (start == end) return listOf(EditorEffect.ShowMessage("Nichts ausgewählt"))
                val replaced = runCatching { f(context.text.substring(start, end)) }.getOrElse {
                    return listOf(EditorEffect.ShowMessage("Nicht umwandelbar: ${it.message}"))
                }
                return listOf(EditorEffect.ApplyEdits(listOf(TextEdit(start, end, replaced))), EditorEffect.Select(start, start + replaced.length))
            }
        }

        /** Auswahl (auf ganze Zeilen erweitert), sonst die ganze Datei. */
        fun lines(id: String, title: String, f: (String) -> String) = object : EditorAction {
            override val id = id
            override val title = title
            override val kind = EditorAction.KIND_SOURCE
            override suspend fun perform(context: EditorContext): List<EditorEffect> {
                val t = context.text
                val start = if (context.hasSelection) t.lastIndexOf('\n', context.selectionStart - 1) + 1 else 0
                val end = if (context.hasSelection) t.indexOf('\n', context.selectionEnd - 1).let { if (it < 0) t.length else it } else t.length
                val replaced = f(t.substring(start, end))
                return listOf(EditorEffect.ApplyEdits(listOf(TextEdit(start, end, replaced))))
            }
        }

        fun wordAt(text: String, offset: Int): Pair<Int, Int> {
            fun part(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'
            var s = offset.coerceIn(0, text.length)
            var e = s
            while (s > 0 && part(text[s - 1])) s--
            while (e < text.length && part(text[e])) e++
            return s to e
        }
    }
}
