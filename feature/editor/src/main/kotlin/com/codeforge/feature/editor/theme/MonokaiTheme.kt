package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class MonokaiTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xff272822.toInt())
        setColor(TEXT_NORMAL, 0xfff8f8f2.toInt())
        setColor(KEYWORD, 0xfff92672.toInt())
        setColor(LITERAL, 0xffe6db74.toInt())
        setColor(LITERAL, 0xffae81ff.toInt())
        setColor(COMMENT, 0xff75715e.toInt())
        setColor(OPERATOR, 0xfff92672.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xff272822.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff75715e.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xfff8f8f2.toInt())
        setColor(CURRENT_LINE, 0xff3e3d32.toInt())
        setColor(SELECTION_INSERT, 0xfff8f8f0.toInt())
        setColor(SELECTION_HANDLE, 0xfff8f8f0.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xff49483e.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff49483e.toInt())
        setColor(BLOCK_LINE, 0xff75715e.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xffa6e22e.toInt())
        setColor(LINE_DIVIDER, 0xff3e3d32.toInt())
        setColor(SCROLL_BAR_TRACK, 0xff272822.toInt())
        setColor(SCROLL_BAR_THUMB, 0xff49483e.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff75715e.toInt())
    }
}
