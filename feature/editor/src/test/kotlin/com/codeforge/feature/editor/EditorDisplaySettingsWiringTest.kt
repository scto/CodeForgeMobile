package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDisplaySettingsWiringTest {

    /**
     * Belegt strukturiert für jedes Feld in [EditorDisplaySettings],
     * auf welche Eigenschaft / Komponente in [io.github.rosemoe.sora.widget.CodeEditor]
     * es in [applyDisplaySettings] bzw. [EditorLanguageFactory] abgebildet wird.
     */
    private val expectedMappings = mapOf(
        "tabSize" to "editor.tabWidth",
        "useTreeSitter" to "EditorLanguageFactory.create(useTreeSitter)",
        "textmateTheme" to "TextMateAssetLoader.setActiveTheme",
        "fontSizeSp" to "editor.setTextSize",
        "fontFamilyAssetPath" to "editor.typefaceText",
        "wordWrap" to "editor.isWordwrap",
        "showNonPrintableChars" to "editor.nonPrintablePaintingFlags",
        "stickyScrollEnabled" to "EditorStickyScroll.isEnabled",
        "magnifierEnabled" to "Magnifier.isEnabled",
        "symbolPairAutocompleteEnabled" to "editor.props.symbolPairAutoCompletion",
        "minimapEnabled" to "EditorMinimap.isEnabled",
        "rainbowBracketsEnabled" to "editor.isHighlightBracketPair / BracketPairs.isEnabled",
        "pinchToZoomEnabled" to "editor.setScaleTextEnabled / setScaleTextSizes",
        "desktopShortcutsEnabled" to "editor.props.navigationMode"
    )

    private val allDisplaySettingsProperties = setOf(
        "tabSize",
        "useTreeSitter",
        "textmateTheme",
        "fontSizeSp",
        "fontFamilyAssetPath",
        "wordWrap",
        "showNonPrintableChars",
        "stickyScrollEnabled",
        "magnifierEnabled",
        "symbolPairAutocompleteEnabled",
        "minimapEnabled",
        "rainbowBracketsEnabled",
        "pinchToZoomEnabled",
        "desktopShortcutsEnabled"
    )

    @Test
    fun `all EditorDisplaySettings fields have an explicit editor mapping`() {
        for (prop in allDisplaySettingsProperties) {
            assertTrue(
                "Property '$prop' in EditorDisplaySettings must have a documented mapping target in SoraCodeEditor",
                expectedMappings.containsKey(prop)
            )
            assertTrue(
                "Mapping target for '$prop' must not be blank",
                expectedMappings[prop]?.isNotBlank() == true
            )
        }

        assertEquals(
            "Every mapped option must correspond to an actual EditorDisplaySettings property",
            allDisplaySettingsProperties,
            expectedMappings.keys
        )
    }

    @Test
    fun `default values of EditorDisplaySettings are sane`() {
        val defaultSettings = EditorDisplaySettings()
        assertEquals(4, defaultSettings.tabSize)
        assertTrue(defaultSettings.useTreeSitter)
        assertEquals(14f, defaultSettings.fontSizeSp)
        assertTrue(defaultSettings.stickyScrollEnabled)
        assertTrue(defaultSettings.magnifierEnabled)
        assertTrue(defaultSettings.symbolPairAutocompleteEnabled)
        assertTrue(defaultSettings.minimapEnabled)
        assertTrue(defaultSettings.rainbowBracketsEnabled)
        assertTrue(defaultSettings.pinchToZoomEnabled)
        assertTrue(defaultSettings.desktopShortcutsEnabled)
    }
}
