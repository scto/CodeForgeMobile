package com.nyamux.view

import android.view.KeyEvent
import android.view.MotionEvent
import com.nyamux.terminal.TerminalSession

interface TerminalViewClient {
    fun onSingleTapUp(e: MotionEvent) {}
    fun shouldEnforceCharBasedInput(): Boolean = false
    fun isTerminalViewSelected(): Boolean = true
    fun copyModeChanged(copyMode: Boolean) {}
    fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean = false
    fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    fun onLongPress(event: MotionEvent): Boolean = false
    fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    fun onScale(scale: Float): Float = scale
    fun readControlKey(): Boolean = false
    fun readAltKey(): Boolean = false
    fun readShiftKey(): Boolean = false
    fun readFnKey(): Boolean = false
    fun shouldBackButtonBeMappedToEscape(): Boolean = false
    fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    fun onEmulatorSet() {}
    fun logError(tag: String?, message: String?) {}
    fun logWarn(tag: String?, message: String?) {}
    fun logInfo(tag: String?, message: String?) {}
    fun logDebug(tag: String?, message: String?) {}
    fun logVerbose(tag: String?, message: String?) {}
    fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    fun logStackTrace(tag: String?, e: Exception?) {}
}
