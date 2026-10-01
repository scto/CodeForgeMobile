package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class GitHubLightTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xffffffff.toInt())
        setColor(TEXT_NORMAL, 0xff24292e.toInt())
        setColor(KEYWORD, 0xffd73a49.toInt())
        setColor(LITERAL, 0xff032f62.toInt())
        setColor(LITERAL, 0xff005cc5.toInt())
        setColor(COMMENT, 0xff6a737d.toInt())
        setColor(OPERATOR, 0xffd73a49.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xfff6f8fa.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xffb3b3b3.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff24292e.toInt())
        setColor(CURRENT_LINE, 0xfff0f4f8.toInt())
        setColor(SELECTION_INSERT, 0xff24292e.toInt())
        setColor(SELECTION_HANDLE, 0xff24292e.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffc8e1ff.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffc8e1ff.toInt())
        setColor(BLOCK_LINE, 0xffe1e4e8.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffd73a49.toInt())
        setColor(LINE_DIVIDER, 0xffe1e4e8.toInt())
        setColor(SCROLL_BAR_TRACK, 0xfff6f8fa.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffd1d5da.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff959da5.toInt())
    }
}
