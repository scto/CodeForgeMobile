package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class NordDarkTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff2e3440.toInt())
        setColor(TEXT_NORMAL, 0xffd8dee9.toInt())
        setColor(KEYWORD, 0xff81a1c1.toInt())
        setColor(LITERAL, 0xffa3be8c.toInt())
        setColor(LITERAL, 0xffb48ead.toInt())
        setColor(COMMENT, 0xff4c566a.toInt())
        setColor(OPERATOR, 0xff81a1c1.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff2e3440.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff4c566a.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xffd8dee9.toInt())
        setColor(CURRENT_LINE, 0xff3b4252.toInt())
        setColor(SELECTION_INSERT, 0xffd8dee9.toInt())
        setColor(SELECTION_HANDLE, 0xffd8dee9.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff434c5e.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff434c5e.toInt())
        setColor(BLOCK_LINE, 0xff4c566a.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xff81a1c1.toInt())
        setColor(LINE_DIVIDER, 0xff3b4252.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff2e3440.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff434c5e.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff4c566a.toInt())
    }
}
