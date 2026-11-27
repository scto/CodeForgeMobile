/**
 * Modul: :core:domain
 *
 * Plugin-API 5, Etappe 5: deklarative Befehle, Menüs und Tastenkürzel aus `plugin.json` (`contributes.commands`,
 * `contributes.menus`, `contributes.keybindings`) samt `when`-Bedingungen.
 */
package com.codeforge.core.domain.extension.ide

import com.codeforge.core.domain.editor.KeyChord
import com.codeforge.core.domain.editor.serialize
import com.codeforge.core.domain.extension.Resolved

/** Befehl aus `contributes.commands` (Titel/Kategorie für Menüs und Palette; ausgeführt wird der angemeldete Befehl). */
data class CommandContribution(val id: String, val title: String, val category: String? = null)

/** Menüeintrag aus `contributes.menus.<place>`. */
data class MenuContribution(val place: String, val command: String, val title: String? = null, val whenClause: String? = null)

/** Tastenkürzel aus `contributes.keybindings` ([key] wie `ctrl+alt+h`). */
data class KeybindingContribution(val command: String, val key: String, val whenClause: String? = null)

/** Orte für [MenuContribution]. */
object MenuPlaces {
    /** Plugin-Menü der Editor-Leiste. */
    const val EDITOR_CONTEXT = "editor/context"
    /** Kontextmenü eines Eintrags im Dateibaum. */
    const val FILE_TREE_CONTEXT = "fileTree/context"
    /** Sichtbarkeit in der Befehlspalette (ohne Eintrag: immer sichtbar). */
    const val COMMAND_PALETTE = "commandPalette"

    val ALL: Set<String> = setOf(EDITOR_CONTEXT, FILE_TREE_CONTEXT, COMMAND_PALETTE)
}

/** Ein auflösbarer Menüeintrag: voller Befehl, Titel, Bedingung. */
data class IdeMenuItem(val pluginId: String, val commandId: String, val title: String, val whenClause: String?)

/** Bildet aus den Beiträgen aktiver Plugins Menüeinträge und Tastenkürzel. */
object IdeMenus {
    private val LANGUAGES = mapOf(
        ".kt" to "kotlin", ".kts" to "kotlin", ".java" to "java", ".xml" to "xml", ".json" to "json", ".md" to "markdown",
        ".py" to "python", ".js" to "javascript", ".ts" to "typescript", ".gradle" to "groovy", ".sh" to "shellscript",
        ".yml" to "yaml", ".yaml" to "yaml", ".html" to "html", ".css" to "css", ".toml" to "toml", ".c" to "c", ".cpp" to "cpp"
    )

    /** Voller Befehl: `cfm.*` und IDs mit Punkt bleiben, sonst `<pluginId>.<command>`. */
    fun commandId(pluginId: String, command: String): String = if ('.' in command) command else "$pluginId.$command"

    /** Einträge für [place]; Titel aus dem Menü, sonst aus `contributes.commands`, sonst die ID. */
    fun items(
        place: String,
        menus: List<Resolved<MenuContribution>>,
        commands: List<Resolved<CommandContribution>>
    ): List<IdeMenuItem> {
        val titles = commands.associate { commandId(it.pluginId, it.item.id) to it.item.title }
        return menus.filter { it.item.place == place }.map {
            val id = commandId(it.pluginId, it.item.command)
            IdeMenuItem(it.pluginId, id, it.item.title ?: titles[id] ?: id, it.item.whenClause)
        }
    }

    /** Sichtbare Einträge für den Kontext [ctx]. */
    fun visible(items: List<IdeMenuItem>, ctx: Map<String, String?>): List<IdeMenuItem> =
        items.filter { WhenClause.evaluate(it.whenClause, ctx) }

    /** Tastenkürzel (normalisierte Schreibweise → Befehl), deren Bedingung in [ctx] gilt; erstes gewinnt. */
    fun keybindings(bindings: List<Resolved<KeybindingContribution>>, ctx: Map<String, String?>): Map<String, String> =
        bindings.filter { WhenClause.evaluate(it.item.whenClause, ctx) }
            .map { normalizeKey(it.item.key) to commandId(it.pluginId, it.item.command) }
            .distinctBy { it.first }
            .toMap()

    /** Speicherform wie `KeyChord.serialize` (`ctrl+alt+h` → `Ctrl+Alt+H`); ungültig → unverändert. */
    fun normalizeKey(key: String): String = KeyChord.parse(key)?.serialize() ?: key

    /** Kontext einer Datei für `when`: Sprache, Endung, Name, Pfad, Projekt, Git. */
    fun fileContext(path: String?, projectOpen: Boolean, gitRepo: Boolean, hasSelection: Boolean = false): Map<String, String?> {
        val name = path?.substringAfterLast('/')
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }?.let { ".$it" }
        return mapOf(
            "resourcePath" to path,
            "resourceFilename" to name,
            "resourceExtname" to ext,
            "editorLangId" to ext?.let { LANGUAGES[it] ?: it.drop(1) },
            "projectOpen" to projectOpen.toString(),
            "gitRepo" to gitRepo.toString(),
            "editorHasSelection" to hasSelection.toString()
        )
    }
}

/**
 * Auswertung von `when`-Bedingungen wie in VS Code (Teilmenge): Schlüssel (wahr, wenn gesetzt, nicht leer und nicht
 * `false`), `!`, `==`, `!=`, `=~` (regulärer Ausdruck), `&&`, `||` und Klammern. Werte ohne oder in `'…'`/`"…"`.
 * Ungültige Ausdrücke gelten als falsch, leere als wahr.
 */
object WhenClause {
    private val OPERATORS = setOf("&&", "||", "==", "!=", "=~")

    /** Ergebnis von [expression] in [ctx]. */
    fun evaluate(expression: String?, ctx: Map<String, String?>): Boolean {
        if (expression.isNullOrBlank()) return true
        return runCatching { Parser(tokenize(expression), ctx).parse() }.getOrDefault(false)
    }

    /** Ob [expression] gültig ist (für die Manifest-Prüfung). */
    fun isValid(expression: String): Boolean = runCatching { Parser(tokenize(expression), emptyMap()).parse() }.isSuccess

    private fun tokenize(s: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val two = s.substring(i, minOf(i + 2, s.length))
            when {
                c.isWhitespace() -> { i++ }
                two in OPERATORS -> { tokens += two; i += 2 }
                c in "()!" -> { tokens += c.toString(); i++ }
                c == '\'' || c == '"' -> {
                    val end = s.indexOf(c, i + 1)
                    require(end > i) { "Offenes Anführungszeichen" }
                    tokens += "\u0000" + s.substring(i + 1, end)
                    i = end + 1
                }
                else -> {
                    var j = i
                    while (j < s.length && !s[j].isWhitespace() && s[j] !in "()!&|=" ) j++
                    require(j > i) { "Unerwartetes Zeichen" }
                    tokens += s.substring(i, j)
                    i = j
                }
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>, private val ctx: Map<String, String?>) {
        private var pos = 0

        fun parse(): Boolean = orExpr().also { require(pos == tokens.size) { "Überzählige Zeichen" } }

        private fun peek() = tokens.getOrNull(pos)
        private fun next() = tokens.getOrNull(pos++) ?: throw IllegalArgumentException("Ausdruck endet unerwartet")

        private fun orExpr(): Boolean {
            var value = andExpr()
            while (peek() == "||") { next(); value = andExpr() || value }
            return value
        }

        private fun andExpr(): Boolean {
            var value = unary()
            while (peek() == "&&") { next(); value = unary() && value }
            return value
        }

        private fun unary(): Boolean {
            if (peek() != "!") return primary()
            next()
            return !unary()
        }

        private fun primary(): Boolean {
            val token = next()
            if (token == "(") return orExpr().also { require(next() == ")") { "Klammer fehlt" } }
            require(token !in OPERATORS && token != ")") { "Schlüssel erwartet" }
            val actual = ctx[token]
            return when (peek()) {
                "==" -> { next(); actual == literal(next()) }
                "!=" -> { next(); actual != literal(next()) }
                "=~" -> { next(); actual != null && Regex(literal(next()).trim('/')).containsMatchIn(actual) }
                else -> !actual.isNullOrEmpty() && actual != "false"
            }
        }

        private fun literal(token: String) = token.removePrefix("\u0000")
    }
}
