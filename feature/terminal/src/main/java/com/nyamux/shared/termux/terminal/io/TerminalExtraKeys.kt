package com.nyamux.shared.termux.terminal.io

import android.view.View
import com.google.android.material.button.MaterialButton
import com.nyamux.shared.termux.extrakeys.ExtraKeyButton
import com.nyamux.shared.termux.extrakeys.ExtraKeysView
import com.nyamux.view.TerminalView

class TerminalExtraKeys(
    private val terminalView: TerminalView
) : ExtraKeysView.IExtraKeysView {
    override fun onExtraKeyButtonClick(view: View?, buttonInfo: ExtraKeyButton, button: MaterialButton?) {
        terminalView.mTermSession?.let { session ->
            when (buttonInfo.key) {
                "ESC" -> session.write("\u001b")
                "TAB" -> session.write("\t")
                "UP" -> session.write("\u001b[A")
                "DOWN" -> session.write("\u001b[B")
                "RIGHT" -> session.write("\u001b[C")
                "LEFT" -> session.write("\u001b[D")
                "HOME" -> session.write("\u001b[1~")
                "END" -> session.write("\u001b[4~")
                "PGUP" -> session.write("\u001b[5~")
                "PGDN" -> session.write("\u001b[6~")
                else -> session.write(buttonInfo.key)
            }
        }
    }

    override fun performExtraKeyButtonHapticFeedback(view: View?, buttonInfo: ExtraKeyButton, button: MaterialButton?): Boolean {
        return false
    }
}
