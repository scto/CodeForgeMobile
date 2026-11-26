/**
 * Modul: :core:ui
 * @author Thomas Schmid
 */
package com.codeforge.core.ui

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

/** Name einer Taste wie `KeyEvent.keyCodeToString`, ohne Präfix `KEYCODE_` (z. B. `P`, `ENTER`, `DPAD_UP`). */
fun Key.keyName(): String = KeyEvent.keyCodeToString(nativeKeyCode).removePrefix("KEYCODE_")
