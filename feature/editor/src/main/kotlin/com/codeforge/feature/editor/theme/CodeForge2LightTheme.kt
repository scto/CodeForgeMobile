package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class CodeForge2LightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xffFAFAFA.toInt())
        setColor(TEXT_NORMAL, 0xff242730.toInt())
        setColor(KEYWORD, 0xffD32F2F.toInt())
        setColor(LITERAL, 0xff547C44.toInt())
        setColor(LITERAL, 0xff3B75AB.toInt())
        setColor(COMMENT, 0xff888888.toInt())
        setColor(OPERATOR, 0xff555555.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xffFAFAFA.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff888888.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff242730.toInt())
        setColor(CURRENT_LINE, 0xffEAEAEA.toInt())
        setColor(SELECTION_INSERT, 0xff242730.toInt())
        setColor(SELECTION_HANDLE, 0xff242730.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffD2E3F0.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffD2E3F0.toInt())
        setColor(BLOCK_LINE, 0xff888888.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffD32F2F.toInt())
        setColor(LINE_DIVIDER, 0xffEAEAEA.toInt())
        setColor(SCROLL_BAR_TRACK, 0xffFAFAFA.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffEAEAEA.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff888888.toInt())
    }
}
