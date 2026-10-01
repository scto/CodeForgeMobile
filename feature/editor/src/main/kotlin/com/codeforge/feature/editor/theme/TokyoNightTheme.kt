package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class TokyoNightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff1a1b26.toInt())
        setColor(TEXT_NORMAL, 0xffa9b1d6.toInt())
        setColor(KEYWORD, 0xffbb9af7.toInt())
        setColor(LITERAL, 0xff9ece6a.toInt())
        setColor(LITERAL, 0xffff9e64.toInt())
        setColor(COMMENT, 0xff565f89.toInt())
        setColor(OPERATOR, 0xff89ddff.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff1a1b26.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff3b4261.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xffa9b1d6.toInt())
        setColor(CURRENT_LINE, 0xff292e42.toInt())
        setColor(SELECTION_INSERT, 0xffc0caf5.toInt())
        setColor(SELECTION_HANDLE, 0xffc0caf5.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff364a82.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff364a82.toInt())
        setColor(BLOCK_LINE, 0xff3b4261.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffbb9af7.toInt())
        setColor(LINE_DIVIDER, 0xff1a1b26.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff1a1b26.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff292e42.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff3b4261.toInt())
    }
}
