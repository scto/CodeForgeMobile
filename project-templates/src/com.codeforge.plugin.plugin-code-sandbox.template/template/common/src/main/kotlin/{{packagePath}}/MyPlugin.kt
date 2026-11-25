package {{packageName}}

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit

/**
 * Sandbox-Plugin: registriert Editor-Aktionen (Editor → Erweiterungs-Schaltfläche). Läuft im isolierten Prozess,
 * bekommt den Text vom Editor und liefert Änderungen zurück, die die App anwendet.
 */
class MyPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginExtensionPoints.EDITOR_ACTION, Reverse)
        context.register(PluginExtensionPoints.EDITOR_ACTION, CountWords)
    }

    override fun onUnload() = Unit

    /** Kehrt die Auswahl um (ohne Auswahl: Hinweis). */
    private object Reverse : EditorAction {
        override val id = "reverse"
        override val title = "Auswahl umkehren"

        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            if (!context.hasSelection) return listOf(EditorEffect.ShowMessage("Bitte Text markieren"))
            val start = context.selectionStart
            val end = context.selectionEnd
            val reversed = Text.reverse(context.text.substring(start, end))
            return listOf(EditorEffect.ApplyEdits(listOf(TextEdit(start, end, reversed))), EditorEffect.Select(start, end))
        }
    }

    /** Zählt Wörter in der Auswahl bzw. der ganzen Datei. */
    private object CountWords : EditorAction {
        override val id = "count-words"
        override val title = "Wörter zählen"
        override val kind = EditorAction.KIND_SOURCE

        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            val text = if (context.hasSelection) context.selectedText else context.text
            return listOf(EditorEffect.ShowMessage("${Text.words(text)} Wörter"))
        }
    }
}

/** Reine Textfunktionen – ohne SDK testbar (siehe src/test). */
object Text {
    fun reverse(s: String): String = s.reversed()

    fun words(s: String): Int = s.split(Regex("\\s+")).count { it.isNotBlank() }
}
