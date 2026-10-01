package com.codeforge.feature.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.nyamux.terminal.TerminalSession
import com.nyamux.terminal.TerminalSessionClient
import com.nyamux.view.TerminalView
import com.nyamux.view.TerminalViewClient

class TerminalViewClientImpl(
    private val context: Context,
    private val terminalView: TerminalView,
    private val viewModel: TerminalViewModel
) : TerminalViewClient, TerminalSessionClient {

    var extraKeysView: View? = null

    override fun onTextChanged(session: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}
