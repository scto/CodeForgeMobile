package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class CodeForge2DarkTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff121318.toInt())
        setColor(TEXT_NORMAL, 0xffdfdfdf.toInt())
        setColor(KEYWORD, 0xffff4d4d.toInt())
        setColor(LITERAL, 0xff6A8759.toInt())
        setColor(LITERAL, 0xff7A9EC2.toInt())
        setColor(COMMENT, 0xff707070.toInt())
        setColor(OPERATOR, 0xffCCCCCC.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff121318.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff707070.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xffdfdfdf.toInt())
        setColor(CURRENT_LINE, 0xff242730.toInt())
        setColor(SELECTION_INSERT, 0xffdfdfdf.toInt())
        setColor(SELECTION_HANDLE, 0xffdfdfdf.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff3d3b37.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff3d3b37.toInt())
        setColor(BLOCK_LINE, 0xff707070.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffff4d4d.toInt())
        setColor(LINE_DIVIDER, 0xff242730.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff121318.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff242730.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff707070.toInt())
    }
}
