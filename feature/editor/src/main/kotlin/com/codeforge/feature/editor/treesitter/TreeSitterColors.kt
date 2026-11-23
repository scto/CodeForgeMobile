/**
 * Modul: :feature:editor
 *
 * Farben für Tree-sitter-Highlighting. Tree-sitter liefert Capture-Namen (`@keyword`, `@function.method`, …), der
 * Editor braucht Farbkennungen. Jede [TsColorGroup] hat eine feste eigene Kennung; ihre Farbe wird bei jedem
 * Theme-Wechsel aus den TextMate-Regeln des aktiven Editor-Themes gesetzt ([TreeSitterColors.apply]). So sehen
 * Tree-sitter- und TextMate-Highlighting im selben Theme gleich aus, auch mit Theme-Plugins.
 */
package com.codeforge.feature.editor.treesitter

import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.internal.grammar.ScopeStack
import org.eclipse.tm4e.core.internal.theme.Theme

private const val SCOPE_KEYWORD = "keyword"
private const val SCOPE_VARIABLE = "variable"
private const val SCOPE_STORAGE_TYPE = "storage.type"
private const val SCOPE_TYPE = "entity.name.type"
private const val SCOPE_ATTRIBUTE_NAME = "entity.other.attribute-name"

/**
 * Farbgruppen mit den TextMate-Scopes, deren Farbe sie übernehmen (erster Treffer gewinnt), und einer
 * Standard-Farbkennung von sora-editor als Rückfall ohne TextMate-Theme.
 */
internal enum class TsColorGroup(val scopes: List<String>, val fallbackId: Int, val italic: Boolean = false) {
    KEYWORD(listOf("keyword.control", SCOPE_KEYWORD, "storage.modifier", SCOPE_STORAGE_TYPE), EditorColorScheme.KEYWORD),
    OPERATOR(listOf("keyword.operator"), EditorColorScheme.OPERATOR),
    STRING(listOf("string.quoted", "string"), EditorColorScheme.LITERAL),
    ESCAPE(listOf("constant.character.escape", "string.regexp", "constant.character"), EditorColorScheme.LITERAL),
    COMMENT(listOf("comment.line", "comment"), EditorColorScheme.COMMENT, italic = true),
    NUMBER(listOf("constant.numeric"), EditorColorScheme.LITERAL),
    CONSTANT(listOf("constant.language", "constant.other", "variable.other.constant", "constant"), EditorColorScheme.LITERAL),
    TYPE(listOf(SCOPE_TYPE, "support.type", "entity.name.class", SCOPE_STORAGE_TYPE), EditorColorScheme.IDENTIFIER_NAME),
    NAMESPACE(listOf("entity.name.namespace", "entity.name.package", SCOPE_TYPE), EditorColorScheme.IDENTIFIER_NAME),
    FUNCTION(listOf("entity.name.function", "support.function", "meta.function-call"), EditorColorScheme.FUNCTION_NAME),
    VARIABLE(listOf("variable.other", SCOPE_VARIABLE), EditorColorScheme.IDENTIFIER_VAR),
    PARAMETER(listOf("variable.parameter", SCOPE_VARIABLE), EditorColorScheme.IDENTIFIER_VAR),
    VARIABLE_BUILTIN(listOf("variable.language", SCOPE_KEYWORD), EditorColorScheme.KEYWORD),
    PROPERTY(listOf("variable.other.property", "variable.other.member", "support.variable.property", SCOPE_VARIABLE), EditorColorScheme.IDENTIFIER_VAR),
    ATTRIBUTE(listOf("storage.type.annotation", "entity.name.function.decorator", "meta.annotation", SCOPE_ATTRIBUTE_NAME), EditorColorScheme.ANNOTATION),
    TAG(listOf("entity.name.tag"), EditorColorScheme.HTML_TAG),
    TAG_ATTRIBUTE(listOf(SCOPE_ATTRIBUTE_NAME), EditorColorScheme.ATTRIBUTE_NAME),
    LABEL(listOf("entity.name.label", "variable.other.label", SCOPE_KEYWORD), EditorColorScheme.KEYWORD),
    PUNCTUATION(listOf("punctuation"), EditorColorScheme.OPERATOR),
    HEADING(listOf("markup.heading", "entity.name.section"), EditorColorScheme.KEYWORD),
    ERROR(listOf("invalid", "invalid.illegal"), EditorColorScheme.TEXT_NORMAL),
    ;

    /** Eigene Farbkennung (außerhalb der Kennungen von sora-editor und TextMate). */
    val colorId: Int get() = BASE_COLOR_ID + ordinal

    /** Stil für [io.github.rosemoe.sora.editor.ts.TsThemeBuilder]. */
    val style: Long get() = TextStyle.makeStyle(colorId, 0, false, italic, false)

    companion object {
        /** TextMate belegt 255 + Index der Theme-Farbtabelle; 5000+ bleibt frei. */
        const val BASE_COLOR_ID = 5000
    }
}

/** Zuordnung Capture-Name → Farbgruppe. Unbekannte Unterformen fallen auf den Namen vor dem letzten Punkt zurück. */
internal object TsCaptureMapping {
    val rules: Map<String, TsColorGroup> = linkedMapOf(
        "keyword" to TsColorGroup.KEYWORD,
        "conditional" to TsColorGroup.KEYWORD,
        "repeat" to TsColorGroup.KEYWORD,
        "include" to TsColorGroup.KEYWORD,
        "exception" to TsColorGroup.KEYWORD,
        "keyword.operator" to TsColorGroup.OPERATOR,
        "operator" to TsColorGroup.OPERATOR,
        "string" to TsColorGroup.STRING,
        "character" to TsColorGroup.STRING,
        "markup" to TsColorGroup.STRING,
        "markup.raw" to TsColorGroup.STRING,
        "markup.link" to TsColorGroup.CONSTANT,
        "string.escape" to TsColorGroup.ESCAPE,
        "string.regex" to TsColorGroup.ESCAPE,
        "escape" to TsColorGroup.ESCAPE,
        "string.special" to TsColorGroup.CONSTANT,
        "comment" to TsColorGroup.COMMENT,
        "number" to TsColorGroup.NUMBER,
        "float" to TsColorGroup.NUMBER,
        "constant" to TsColorGroup.CONSTANT,
        "boolean" to TsColorGroup.CONSTANT,
        "type" to TsColorGroup.TYPE,
        "constructor" to TsColorGroup.TYPE,
        "namespace" to TsColorGroup.NAMESPACE,
        "module" to TsColorGroup.NAMESPACE,
        "function" to TsColorGroup.FUNCTION,
        "variable" to TsColorGroup.VARIABLE,
        "variable.builtin" to TsColorGroup.VARIABLE_BUILTIN,
        "parameter" to TsColorGroup.PARAMETER,
        "variable.parameter" to TsColorGroup.PARAMETER,
        "property" to TsColorGroup.PROPERTY,
        "attribute" to TsColorGroup.ATTRIBUTE,
        "tag" to TsColorGroup.TAG,
        "label" to TsColorGroup.LABEL,
        "punctuation" to TsColorGroup.PUNCTUATION,
        "delimiter" to TsColorGroup.PUNCTUATION,
        "markup.heading" to TsColorGroup.HEADING,
        "error" to TsColorGroup.ERROR,
    )

    /** Gruppe für einen Capture-Namen wie `keyword.function` (mit Rückfall auf `keyword`). */
    fun groupFor(capture: String): TsColorGroup? {
        var name = capture.removePrefix("@")
        while (true) {
            rules[name]?.let { return it }
            if (!name.contains('.')) return null
            name = name.substringBeforeLast('.')
        }
    }
}

/** Setzt die Farben der [TsColorGroup]s im Farbschema des Editors. */
object TreeSitterColors {
    private const val HEX = 16
    private const val ALPHA_BITS = 8
    private const val ALPHA_SHIFT = 24
    private const val ALPHA_MASK = 0xFFL

    /**
     * Überträgt die Farben des aktiven TextMate-Themes auf die Tree-sitter-Kennungen von [scheme]. Ohne geladenes
     * Theme gelten die Standardfarben von sora-editor.
     */
    fun apply(scheme: EditorColorScheme) {
        val theme = runCatching { ThemeRegistry.getInstance().currentThemeModel?.theme }.getOrNull()
        for (group in TsColorGroup.entries) {
            val color = theme?.let { resolve(it, group.scopes) } ?: scheme.getColor(group.fallbackId)
            scheme.setColor(group.colorId, color)
        }
    }

    /** Vordergrundfarbe (ARGB) des ersten Scopes, für den das Theme eine eigene Farbe hat; sonst `null`. */
    internal fun resolve(theme: Theme, scopes: List<String>): Int? {
        val defaultFg = theme.defaults.foregroundId
        for (scope in scopes) {
            val attrs = theme.match(ScopeStack.from(*scope.split(' ').toTypedArray())) ?: continue
            val fg = attrs.foregroundId
            if (fg != 0 && fg != defaultFg) {
                theme.getColor(fg)?.let(::parseColor)?.let { return it }
            }
        }
        return null
    }

    /** `#RGB`, `#RRGGBB` oder `#RRGGBBAA` (TextMate-Reihenfolge) → ARGB; ungültig → `null`. */
    internal fun parseColor(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        val rgba = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("") + "FF"
            6 -> hex + "FF"
            8 -> hex
            else -> ""
        }
        return rgba.toLongOrNull(HEX)?.let { v -> ((v and ALPHA_MASK).toInt() shl ALPHA_SHIFT) or (v ushr ALPHA_BITS).toInt() }
    }

}
