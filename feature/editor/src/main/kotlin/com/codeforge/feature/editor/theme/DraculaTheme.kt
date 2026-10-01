package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class DraculaTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff282a36.toInt())
        setColor(TEXT_NORMAL, 0xfff8f8f2.toInt())
        setColor(KEYWORD, 0xffff79c6.toInt())
        setColor(LITERAL, 0xfff1fa8c.toInt())
        setColor(LITERAL, 0xffbd93f9.toInt())
        setColor(COMMENT, 0xff6272a4.toInt())
        setColor(OPERATOR, 0xffff79c6.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff282a36.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff6272a4.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xfff8f8f2.toInt())
        setColor(CURRENT_LINE, 0xff44475a.toInt())
        setColor(SELECTION_INSERT, 0xfff8f8f0.toInt())
        setColor(SELECTION_HANDLE, 0xfff8f8f0.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff44475a.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff44475a.toInt())
        setColor(BLOCK_LINE, 0xff6272a4.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffff79c6.toInt())
        setColor(LINE_DIVIDER, 0xff44475a.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff282a36.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff44475a.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff6272a4.toInt())
    }
}
