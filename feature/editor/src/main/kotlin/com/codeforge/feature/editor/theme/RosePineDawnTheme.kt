package com.codeforge.feature.editor.theme

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class RosePineDawnTheme : EditorColorScheme() {
    init {
        setColor(WHOLE_BACKGROUND, 0xfffaf4ed.toInt())
        setColor(TEXT_NORMAL, 0xff575279.toInt())
        setColor(KEYWORD, 0xff31748f.toInt())
        setColor(LITERAL, 0xffea9d34.toInt())
        setColor(LITERAL, 0xffd7827e.toInt())
        setColor(COMMENT, 0xff9893a5.toInt())
        setColor(OPERATOR, 0xff56949f.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xfffaf4ed.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xff9893a5.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff575279.toInt())
        setColor(CURRENT_LINE, 0xfff2e9e1.toInt())
        setColor(SELECTION_INSERT, 0xff575279.toInt())
        setColor(SELECTION_HANDLE, 0xff575279.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xffdfdad9.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xffdfdad9.toInt())
        setColor(BLOCK_LINE, 0xffdfdad9.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xff31748f.toInt())
        setColor(LINE_DIVIDER, 0xffdfdad9.toInt())
        setColor(SCROLL_BAR_TRACK, 0xfffaf4ed.toInt())
        setColor(SCROLL_BAR_THUMB, 0xffdfdad9.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff9893a5.toInt())
    }
}
