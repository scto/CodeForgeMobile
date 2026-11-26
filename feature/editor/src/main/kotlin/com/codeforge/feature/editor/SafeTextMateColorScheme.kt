package com.codeforge.feature.editor

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * TextMate-Farbschema, das veraltete Farb-IDs toleriert.
 *
 * Spans tragen Farb-IDs aus der ColorMap des Themes, mit dem sie analysiert wurden. Wechselt das globale
 * [ThemeRegistry]-Theme (z. B. durch die Theme-Vorschau in den Editor-Einstellungen), kann ein Editor noch
 * Spans mit IDs zeichnen, die in der neuen, kleineren ColorMap nicht existieren -> IndexOutOfBoundsException
 * in `ColorMap.getColor`. Bis die Neuanalyse fertig ist, wird stattdessen die normale Textfarbe verwendet.
 */
internal class SafeTextMateColorScheme(
    registry: ThemeRegistry,
    model: ThemeModel
) : TextMateColorScheme(registry, model) {

    override fun getColor(type: Int): Int = try {
        super.getColor(type)
    } catch (_: IndexOutOfBoundsException) {
        super.getColor(EditorColorScheme.TEXT_NORMAL)
    }

    companion object {
        fun create(registry: ThemeRegistry = ThemeRegistry.getInstance()): EditorColorScheme =
            SafeTextMateColorScheme(registry, registry.currentThemeModel)
    }
}
