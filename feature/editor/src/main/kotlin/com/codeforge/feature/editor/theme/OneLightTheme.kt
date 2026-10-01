package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class OneLightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xfffafafa.toInt())
        setColor(TEXT_NORMAL, 0xff383a42.toInt())
        setColor(KEYWORD, 0xffa626a4.toInt())
        setColor(LITERAL, 0xff50a14f.toInt())
        setColor(LITERAL, 0xff986801.toInt())
        setColor(COMMENT, 0xffa0a1a7.toInt())
        setColor(OPERATOR, 0xff4078f2.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xfffafafa.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff9d9d9f.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff383a42.toInt())
        setColor(CURRENT_LINE, 0xfff2f2f2.toInt())
        setColor(SELECTION_INSERT, 0xff526fff.toInt())
        setColor(SELECTION_HANDLE, 0xff526fff.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffe5e5e6.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffe5e5e6.toInt())
        setColor(BLOCK_LINE, 0xffe5e5e6.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffa626a4.toInt())
        setColor(LINE_DIVIDER, 0xffe5e5e6.toInt())
        setColor(SCROLL_BAR_TRACK, 0xfffafafa.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffe5e5e6.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xffa0a1a7.toInt())
    }
}
