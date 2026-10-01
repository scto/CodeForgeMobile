package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class MaterialLightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xfffafafa.toInt())
        setColor(TEXT_NORMAL, 0xff90a4ae.toInt())
        setColor(KEYWORD, 0xff39adb5.toInt())
        setColor(LITERAL, 0xff91b859.toInt())
        setColor(LITERAL, 0xfff76d47.toInt())
        setColor(COMMENT, 0xffccd7da.toInt())
        setColor(OPERATOR, 0xff39adb5.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xfffafafa.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xffccd7da.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff90a4ae.toInt())
        setColor(CURRENT_LINE, 0xfff3f3f3.toInt())
        setColor(SELECTION_INSERT, 0xff272727.toInt())
        setColor(SELECTION_HANDLE, 0xff272727.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffe7e7e8.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffe7e7e8.toInt())
        setColor(BLOCK_LINE, 0xffe7e7e8.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xff39adb5.toInt())
        setColor(LINE_DIVIDER, 0xffe7e7e8.toInt())
        setColor(SCROLL_BAR_TRACK, 0xfffafafa.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffe7e7e8.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xffccd7da.toInt())
    }
}
