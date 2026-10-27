package com.codeforge.shared.termux.terminal.io;

import android.view.KeyEvent;

/**
 * Entscheidet, ob eine Hardware-Tastenkombination Kopieren/Einfügen im Terminal auslöst.
 * Nur Strg+Umschalt+C / Strg+Umschalt+V; Strg+C bleibt SIGINT, Strg+V bleibt unverändert.
 * Reine Funktion ohne Android-Zustand, damit sie als Unit-Test prüfbar ist.
 */
public final class ClipboardShortcuts {

    public static final int NONE = 0;
    public static final int COPY = 1;
    public static final int PASTE = 2;

    private ClipboardShortcuts() {}

    public static int resolve(boolean enabled, int keyCode, boolean ctrl, boolean shift, boolean alt) {
        if (!enabled || !ctrl || !shift || alt) return NONE;
        if (keyCode == KeyEvent.KEYCODE_C) return COPY;
        if (keyCode == KeyEvent.KEYCODE_V) return PASTE;
        return NONE;
    }
}
