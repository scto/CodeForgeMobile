/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Tastenbelegung des Editors: Aktionen mit Standard-Akkorden und vom Nutzer überschriebene Belegungen.
 * Tastennamen folgen `android.view.KeyEvent.keyCodeToString` ohne Präfix `KEYCODE_` (z. B. `P`, `ENTER`, `DPAD_UP`).
 */
package com.codeforge.core.domain.editor

/** Tastenkombination aus Modifikatoren und genau einer Taste. */
data class KeyChord(
    val key: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
) {
    /** Ob der Akkord beim Tippen Text erzeugt (dann fängt der Editor ihn nicht ab). */
    val producesText: Boolean
        get() = !ctrl && !alt && !meta && (key.length == 1 || key in TEXT_KEYS)

    companion object {

        /** Tasten, die allein nie einen Akkord bilden. */
        val MODIFIER_KEYS: Set<String> = setOf(
            "SHIFT_LEFT", "SHIFT_RIGHT", "CTRL_LEFT", "CTRL_RIGHT", "ALT_LEFT", "ALT_RIGHT",
            "META_LEFT", "META_RIGHT", "FUNCTION", "CAPS_LOCK", "NUM_LOCK", "SYM"
        )

        private val TEXT_KEYS = setOf(
            "SPACE", "ENTER", "NUMPAD_ENTER", "TAB", "DEL", "FORWARD_DEL", "SLASH", "BACKSLASH", "PERIOD", "COMMA",
            "MINUS", "EQUALS", "PLUS", "SEMICOLON", "APOSTROPHE", "GRAVE", "LEFT_BRACKET", "RIGHT_BRACKET",
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "MOVE_HOME", "MOVE_END", "PAGE_UP", "PAGE_DOWN"
        )

        private val DISPLAY_NAMES = mapOf(
            "DPAD_UP" to "↑", "DPAD_DOWN" to "↓", "DPAD_LEFT" to "←", "DPAD_RIGHT" to "→",
            "ENTER" to "Enter", "NUMPAD_ENTER" to "Enter", "TAB" to "Tab", "ESCAPE" to "Esc",
            "DEL" to "Backspace", "FORWARD_DEL" to "Delete", "SPACE" to "Space",
            "MOVE_HOME" to "Home", "MOVE_END" to "End", "PAGE_UP" to "PgUp", "PAGE_DOWN" to "PgDn",
            "SLASH" to "/", "BACKSLASH" to "\\", "PERIOD" to ".", "COMMA" to ",", "MINUS" to "-",
            "EQUALS" to "=", "PLUS" to "+", "SEMICOLON" to ";", "APOSTROPHE" to "'", "GRAVE" to "`",
            "LEFT_BRACKET" to "[", "RIGHT_BRACKET" to "]", "INSERT" to "Ins"
        )

        /** Lesbarer Name einer Taste. */
        fun displayKey(key: String): String = DISPLAY_NAMES[key] ?: key.lowercase()
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .let { if (it.length == 1) it.uppercase() else it }

        /** Tastenname aus einer Eingabe wie `p`, `/`, `F6`, `enter` oder `↑`; `null` bei leerer Eingabe. */
        fun keyFromText(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            DISPLAY_NAMES.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }?.let { return it.key }
            return trimmed.uppercase().replace(' ', '_').takeUnless { it in MODIFIER_KEYS }
        }

        /** Liest die Speicherform; `null` bei leerem oder ungültigem Text. */
        fun parse(text: String): KeyChord? {
            val parts = text.split(CHORD_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val key = parts.last().uppercase()
            if (key in MODIFIER_KEYS) return null
            val mods = parts.dropLast(1).map { it.lowercase() }.toSet()
            val known = setOf("ctrl", "alt", "shift", "meta")
            if (!known.containsAll(mods)) return null
            return KeyChord(
                key = key,
                ctrl = "ctrl" in mods,
                shift = "shift" in mods,
                alt = "alt" in mods,
                meta = "meta" in mods
            )
        }
    }
}

private const val CHORD_SEPARATOR = "+"

private fun KeyChord.modifierNames(): List<String> = buildList {
    if (ctrl) add("Ctrl")
    if (alt) add("Alt")
    if (shift) add("Shift")
    if (meta) add("Meta")
}

/** Speicherform, z. B. `Ctrl+Shift+P`. */
fun KeyChord.serialize(): String = (modifierNames() + key).joinToString(CHORD_SEPARATOR)

/** Anzeigeform, z. B. `Ctrl+Shift+↑`. */
fun KeyChord.display(): String = (modifierNames() + KeyChord.displayKey(key)).joinToString(CHORD_SEPARATOR)

/** Gruppen der Tastenbelegung. */
enum class KeymapCategory { EDITING, CODE, NAVIGATION, TABS, FOLDING }

/** Belegbare Editor-Aktion mit stabiler ID und Standard-Akkord. */
@Suppress("MagicNumber")
enum class KeymapAction(val id: String, val category: KeymapCategory, val defaultChord: KeyChord?) {
    UNDO("undo", KeymapCategory.EDITING, KeyChord("Z", ctrl = true)),
    REDO("redo", KeymapCategory.EDITING, KeyChord("Z", ctrl = true, shift = true)),
    SAVE("save", KeymapCategory.EDITING, KeyChord("S", ctrl = true)),
    TOGGLE_COMMENT("toggle_comment", KeymapCategory.EDITING, KeyChord("SLASH", ctrl = true)),
    DUPLICATE_LINE("duplicate_line", KeymapCategory.EDITING, KeyChord("D", ctrl = true)),
    DELETE_LINE("delete_line", KeymapCategory.EDITING, KeyChord("Y", ctrl = true)),
    MOVE_LINE_UP("move_line_up", KeymapCategory.EDITING, KeyChord("DPAD_UP", alt = true, shift = true)),
    MOVE_LINE_DOWN("move_line_down", KeymapCategory.EDITING, KeyChord("DPAD_DOWN", alt = true, shift = true)),
    FORMAT("format", KeymapCategory.EDITING, KeyChord("L", ctrl = true, alt = true)),

    QUICK_FIX("quick_fix", KeymapCategory.CODE, KeyChord("ENTER", alt = true)),
    REFACTOR("refactor", KeymapCategory.CODE, KeyChord("R", ctrl = true, shift = true)),
    RENAME("rename", KeymapCategory.CODE, KeyChord("F6", shift = true)),
    DEFINITION("definition", KeymapCategory.CODE, KeyChord("B", ctrl = true)),
    REFERENCES("references", KeymapCategory.CODE, KeyChord("F7", alt = true)),
    HOVER("hover", KeymapCategory.CODE, KeyChord("Q", ctrl = true)),
    SIGNATURE_HELP("signature_help", KeymapCategory.CODE, KeyChord("P", ctrl = true)),
    ORGANIZE_IMPORTS("organize_imports", KeymapCategory.CODE, KeyChord("O", ctrl = true, alt = true)),

    COMMAND_PALETTE("command_palette", KeymapCategory.NAVIGATION, KeyChord("P", ctrl = true, shift = true)),
    FIND("find", KeymapCategory.NAVIGATION, KeyChord("F", ctrl = true)),
    GO_TO_LINE("go_to_line", KeymapCategory.NAVIGATION, KeyChord("G", ctrl = true)),
    OUTLINE("outline", KeymapCategory.NAVIGATION, KeyChord("F12", ctrl = true)),

    NEXT_TAB("next_tab", KeymapCategory.TABS, KeyChord("DPAD_RIGHT", alt = true)),
    PREVIOUS_TAB("previous_tab", KeymapCategory.TABS, KeyChord("DPAD_LEFT", alt = true)),
    CLOSE_TAB("close_tab", KeymapCategory.TABS, KeyChord("W", ctrl = true)),
    CLOSE_OTHER_TABS("close_other_tabs", KeymapCategory.TABS, null),
    GO_TO_TAB_1("go_to_tab_1", KeymapCategory.TABS, KeyChord("1", alt = true)),
    GO_TO_TAB_2("go_to_tab_2", KeymapCategory.TABS, KeyChord("2", alt = true)),
    GO_TO_TAB_3("go_to_tab_3", KeymapCategory.TABS, KeyChord("3", alt = true)),
    GO_TO_TAB_4("go_to_tab_4", KeymapCategory.TABS, KeyChord("4", alt = true)),
    GO_TO_TAB_5("go_to_tab_5", KeymapCategory.TABS, KeyChord("5", alt = true)),
    GO_TO_TAB_6("go_to_tab_6", KeymapCategory.TABS, KeyChord("6", alt = true)),
    GO_TO_TAB_7("go_to_tab_7", KeymapCategory.TABS, KeyChord("7", alt = true)),
    GO_TO_TAB_8("go_to_tab_8", KeymapCategory.TABS, KeyChord("8", alt = true)),
    GO_TO_TAB_9("go_to_tab_9", KeymapCategory.TABS, KeyChord("9", alt = true)),

    FOLD_TOGGLE("fold_toggle", KeymapCategory.FOLDING, KeyChord("PERIOD", ctrl = true)),
    FOLD_ALL("fold_all", KeymapCategory.FOLDING, KeyChord("MINUS", ctrl = true, shift = true)),
    UNFOLD_ALL("unfold_all", KeymapCategory.FOLDING, KeyChord("EQUALS", ctrl = true, shift = true));

    /** Tab-Index (0-basiert) für die „Gehe zu Tab N“-Aktionen, sonst `null`. */
    val tabIndex: Int?
        get() = if (id.startsWith(TAB_PREFIX)) id.removePrefix(TAB_PREFIX).toIntOrNull()?.minus(1) else null

    companion object {
        private const val TAB_PREFIX = "go_to_tab_"

        /** Aktion zur ID. */
        fun byId(id: String): KeymapAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Aufgelöste Tastenbelegung aus den gespeicherten Überschreibungen
 * (Aktions-ID -> Speicherform; leerer Wert = Belegung entfernt).
 */
class Keymap(val overrides: Map<String, String> = emptyMap()) {

    private val byChord: Map<KeyChord, KeymapAction> = buildMap {
        for (action in KeymapAction.entries) {
            val chord = chordFor(action) ?: continue
            putIfAbsent(chord, action)
        }
    }

    /** Anzahl geänderter Aktionen. */
    val modifiedCount: Int get() = KeymapAction.entries.count(::isModified)

    /** Wirksamer Akkord einer Aktion (`null` = nicht belegt). */
    fun chordFor(action: KeymapAction): KeyChord? =
        if (overrides.containsKey(action.id)) KeyChord.parse(overrides.getValue(action.id)) else action.defaultChord

    /** Aktion für einen gedrückten Akkord. */
    fun actionFor(chord: KeyChord): KeymapAction? = byChord[chord]

    /** Ob die Aktion vom Standard abweicht. */
    fun isModified(action: KeymapAction): Boolean = chordFor(action) != action.defaultChord

    /** Andere Aktion, die denselben Akkord schon nutzt. */
    fun conflictWith(action: KeymapAction, chord: KeyChord): KeymapAction? =
        KeymapAction.entries.firstOrNull { it != action && chordFor(it) == chord }

    /** Neue Überschreibungen mit geänderter Belegung (`null` entfernt die Belegung). */
    fun withBinding(action: KeymapAction, chord: KeyChord?): Map<String, String> {
        val next = overrides.toMutableMap()
        if (chord == action.defaultChord) next.remove(action.id) else next[action.id] = chord?.serialize().orEmpty()
        return next
    }

    /** Neue Überschreibungen mit Standardbelegung für die Aktion. */
    fun withDefault(action: KeymapAction): Map<String, String> = overrides - action.id
}
