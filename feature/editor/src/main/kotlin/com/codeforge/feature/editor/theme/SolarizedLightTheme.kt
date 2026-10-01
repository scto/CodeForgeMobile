package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class SolarizedLightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xfffdf6e3.toInt())
        setColor(TEXT_NORMAL, 0xff657b83.toInt())
        setColor(KEYWORD, 0xff859900.toInt())
        setColor(LITERAL, 0xff2aa198.toInt())
        setColor(LITERAL, 0xffd33682.toInt())
        setColor(COMMENT, 0xff93a1a1.toInt())
        setColor(OPERATOR, 0xff859900.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xffeee8d5.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff93a1a1.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff586e75.toInt())
        setColor(CURRENT_LINE, 0xffeee8d5.toInt())
        setColor(SELECTION_INSERT, 0xff657b83.toInt())
        setColor(SELECTION_HANDLE, 0xff657b83.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffe8dfd6.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffe8dfd6.toInt())
        setColor(BLOCK_LINE, 0xffeee8d5.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xff859900.toInt())
        setColor(LINE_DIVIDER, 0xffeee8d5.toInt())
        setColor(SCROLL_BAR_TRACK, 0xfffdf6e3.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffeee8d5.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff93a1a1.toInt())
    }
}
