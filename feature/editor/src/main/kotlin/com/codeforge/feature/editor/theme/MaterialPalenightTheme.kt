package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class MaterialPalenightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff292d3e.toInt())
        setColor(TEXT_NORMAL, 0xffa6accd.toInt())
        setColor(KEYWORD, 0xffc792ea.toInt())
        setColor(LITERAL, 0xffc3e88d.toInt())
        setColor(LITERAL, 0xfff78c6c.toInt())
        setColor(COMMENT, 0xff676e95.toInt())
        setColor(OPERATOR, 0xff89ddff.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff292d3e.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff676e95.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xffa6accd.toInt())
        setColor(CURRENT_LINE, 0xff222533.toInt())
        setColor(SELECTION_INSERT, 0xffffcc00.toInt())
        setColor(SELECTION_HANDLE, 0xffffcc00.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff3c435e.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff3c435e.toInt())
        setColor(BLOCK_LINE, 0xff3c435e.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffc792ea.toInt())
        setColor(LINE_DIVIDER, 0xff222533.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff292d3e.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff3c435e.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff676e95.toInt())
    }
}
