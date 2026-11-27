package {{packageName}}

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit

/**
 * Sandbox-Plugin (API 5). Läuft im isolierten Prozess.
 * - Editor-Aktionen (Editor → Erweiterungs-Schaltfläche): bekommen den Text und liefern Änderungen zurück.
 * - Befehl `hello`: über `contributes.menus` im Editor-Menü, Dateibaum und in der Palette, Tastenkürzel Strg+Alt+H.
 * - Ereignisse und Statusleiste: zählt Speichervorgänge.
 */
class MyPlugin : CodeForgePlugin {
    private var saves = 0

    override fun onLoad(context: PluginContext) {
        context.register(PluginExtensionPoints.EDITOR_ACTION, Reverse)
        context.register(PluginExtensionPoints.EDITOR_ACTION, CountWords)

        val ide = context.ide()
        // Rückgabe erscheint als Hinweis; `path` kommt aus Editor bzw. Dateibaum.
        ide.commands.register("hello", "Hallo aus der Sandbox", IdeCommandHandler { args ->
            "Hallo! Datei: ${args["path"]?.substringAfterLast('/') ?: "–"}, $saves× gespeichert"
        })
        ide.statusBar.set("saves", "💾 0", "Speichervorgänge seit dem Start", "${context.pluginId}.hello")
        ide.events.subscribe("file.saved") {
            saves++
            ide.statusBar.set("saves", "💾 $saves", "Speichervorgänge seit dem Start", "${context.pluginId}.hello")
        }
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
