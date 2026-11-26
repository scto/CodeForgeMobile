/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Konfiguration der Symbolleiste über der Bildschirmtastatur: Aktionen, Symbole und eigene Makros
 * in frei wählbarer Reihenfolge; nur angeheftete Einträge erscheinen in der Leiste.
 */
package com.codeforge.core.domain.editor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Art eines Eintrags. */
@Serializable
enum class SymbolEntryKind {
    @SerialName("action") ACTION,
    @SerialName("symbol") SYMBOL,
    @SerialName("macro") MACRO
}

/** Eingebaute Aktion der Symbolleiste. */
enum class SymbolBarAction(val id: String) {
    TAB("tab"),
    LINE_COMMENT("line_comment"),
    MOVE_LINE_UP("move_line_up"),
    MOVE_LINE_DOWN("move_line_down"),
    DUPLICATE_LINE("duplicate_line"),
    DELETE_LINE("delete_line"),
    INDENT("indent"),
    DEDENT("dedent"),
    UNDO("undo"),
    REDO("redo"),
    CURSOR_LEFT("cursor_left"),
    CURSOR_RIGHT("cursor_right");

    companion object {
        /** Aktion zur ID. */
        fun byId(id: String): SymbolBarAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Eintrag der Symbolleiste.
 *
 * @property id stabil: `action:<id>`, `symbol:<text>` oder `macro:<zufall>`
 * @property kind Art des Eintrags
 * @property label Beschriftung (Symbol: das Zeichen selbst; Aktion: ihre ID)
 * @property text eingefügter Text (Symbol/Makro); `$0` markiert im Makro die Cursorposition
 * @property pinned in der Leiste sichtbar
 */
@Serializable
data class SymbolBarEntry(
    val id: String,
    val kind: SymbolEntryKind,
    val label: String,
    val text: String = "",
    val pinned: Boolean = true
) {
    /** Aktion eines Aktions-Eintrags. */
    val action: SymbolBarAction?
        get() = if (kind == SymbolEntryKind.ACTION) SymbolBarAction.byId(id.removePrefix(ACTION_PREFIX)) else null

    companion object {
        const val ACTION_PREFIX = "action:"
        const val SYMBOL_PREFIX = "symbol:"
        const val MACRO_PREFIX = "macro:"
        const val CURSOR_MARKER = "$0"

        /** Aktions-Eintrag. */
        fun action(action: SymbolBarAction, pinned: Boolean = true) = SymbolBarEntry(
            id = ACTION_PREFIX + action.id,
            kind = SymbolEntryKind.ACTION,
            label = action.id,
            pinned = pinned
        )

        /** Symbol-Eintrag. */
        fun symbol(text: String, pinned: Boolean = true) =
            SymbolBarEntry(id = SYMBOL_PREFIX + text, kind = SymbolEntryKind.SYMBOL, label = text, text = text, pinned = pinned)

        /** Makro-Eintrag. */
        fun macro(key: String, label: String, text: String) =
            SymbolBarEntry(id = MACRO_PREFIX + key, kind = SymbolEntryKind.MACRO, label = label, text = text)
    }
}

/** Standardliste, Speicherung und Bearbeitung der Symbolleiste. */
object SymbolBarConfig {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SymbolBarEntry.serializer())

    private val DEFAULT_SYMBOLS = listOf(
        "{", "}", "(", ")", ";", "=", ".", ",", "\"", "'", ":", "<", ">", "[", "]",
        "+", "-", "*", "/", "!", "?", "&", "|", "_", "->"
    )

    /** Standardliste wie in CodeAssist: Aktionen vorne, dann gängige Symbole. */
    val DEFAULT: List<SymbolBarEntry> = buildList {
        add(SymbolBarEntry.action(SymbolBarAction.TAB))
        add(SymbolBarEntry.action(SymbolBarAction.LINE_COMMENT))
        add(SymbolBarEntry.action(SymbolBarAction.MOVE_LINE_UP))
        add(SymbolBarEntry.action(SymbolBarAction.MOVE_LINE_DOWN))
        add(SymbolBarEntry.action(SymbolBarAction.DUPLICATE_LINE))
        DEFAULT_SYMBOLS.forEach { add(SymbolBarEntry.symbol(it)) }
        add(SymbolBarEntry.action(SymbolBarAction.CURSOR_LEFT, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.CURSOR_RIGHT, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.INDENT, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.DEDENT, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.DELETE_LINE, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.UNDO, pinned = false))
        add(SymbolBarEntry.action(SymbolBarAction.REDO, pinned = false))
    }

    /** Liest die gespeicherte Liste; leer oder defekt = Standard. Neue eingebaute Aktionen werden ergänzt. */
    fun decode(text: String): List<SymbolBarEntry> {
        if (text.isBlank()) return DEFAULT
        val stored = runCatching { json.decodeFromString(serializer, text) }.getOrNull()
            ?.filter { it.kind != SymbolEntryKind.ACTION || it.action != null }
            ?.distinctBy { it.id }
            ?: return DEFAULT
        val missing = SymbolBarAction.entries
            .map { SymbolBarEntry.action(it, pinned = false) }
            .filter { candidate -> stored.none { it.id == candidate.id } }
        return stored + missing
    }

    /** Speicherform; die Standardliste wird als leerer Text gespeichert. */
    fun encode(entries: List<SymbolBarEntry>): String =
        if (entries == DEFAULT) "" else json.encodeToString(serializer, entries)

    /** Verschiebt einen Eintrag um [delta] Positionen. */
    fun move(entries: List<SymbolBarEntry>, index: Int, delta: Int): List<SymbolBarEntry> {
        val target = index + delta
        if (index !in entries.indices || target !in entries.indices) return entries
        return entries.toMutableList().apply { add(target, removeAt(index)) }
    }

    /** Schaltet das Anheften eines Eintrags um. */
    fun togglePinned(entries: List<SymbolBarEntry>, id: String): List<SymbolBarEntry> =
        entries.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }

    /** Fügt ein Symbol hinzu (doppelte werden ignoriert). */
    fun addSymbol(entries: List<SymbolBarEntry>, text: String): List<SymbolBarEntry> {
        val entry = SymbolBarEntry.symbol(text)
        return if (text.isEmpty() || entries.any { it.id == entry.id }) entries else entries + entry
    }

    /** Fügt ein Makro hinzu oder ersetzt eines mit gleicher ID. */
    fun upsertMacro(entries: List<SymbolBarEntry>, macro: SymbolBarEntry): List<SymbolBarEntry> {
        val index = entries.indexOfFirst { it.id == macro.id }
        return if (index < 0) entries + macro else entries.toMutableList().apply { set(index, macro) }
    }

    /** Entfernt ein Symbol oder Makro; eingebaute Aktionen bleiben erhalten. */
    fun remove(entries: List<SymbolBarEntry>, id: String): List<SymbolBarEntry> =
        entries.filterNot { it.id == id && it.kind != SymbolEntryKind.ACTION }

    /**
     * Sichtbare Leiste: angeheftete Einträge, optional ergänzt um sprachspezifische Symbole,
     * die noch nicht in der Liste stehen.
     */
    fun visible(entries: List<SymbolBarEntry>, languageSymbols: List<String>): List<SymbolBarEntry> {
        val pinned = entries.filter { it.pinned }
        val known = entries.mapTo(HashSet()) { it.id }
        val extras = languageSymbols.distinct()
            .map { SymbolBarEntry.symbol(it) }
            .filter { it.id !in known }
        return pinned + extras
    }

    /** Text ohne Cursor-Markierung und Cursorversatz relativ zum Einfügebeginn. */
    fun expandMacro(text: String): Pair<String, Int> {
        val marker = text.indexOf(SymbolBarEntry.CURSOR_MARKER)
        if (marker < 0) return text to text.length
        return text.removeRange(marker, marker + SymbolBarEntry.CURSOR_MARKER.length) to marker
    }
}
