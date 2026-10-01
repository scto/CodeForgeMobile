package com.nyamux.view.textselection

import android.view.ActionMode
import android.view.MotionEvent
import android.view.ViewTreeObserver
import com.nyamux.view.TerminalView

class TextSelectionCursorController(
    private val terminalView: TerminalView
) : ViewTreeObserver.OnTouchModeChangeListener {
    val selectedText: String? = null
    val storedSelectedText: String? = null
    val actionMode: ActionMode? = null

    fun show(event: MotionEvent?) {}
    fun hide(): Boolean = true
    fun render() {}
    fun isActive(): Boolean = false
    fun unsetStoredSelectedText() {}
    fun decrementYTextSelectionCursors(decrement: Int) {}
    fun onDetached() {}
    fun getSelectors(sel: IntArray) {}

    override fun onTouchModeChanged(isInTouchMode: Boolean) {}
}
