// Modul: :app
package com.codeforge.app

import android.os.Build
import android.os.Process

/**
 * Erkennt den isolierten Prozess der Plugin-Sandbox (`android:isolatedProcess="true"`).
 *
 * Ab Android 9 über [Process.isIsolated]; davor über den UID-Bereich isolierter Prozesse
 * (`AID_ISOLATED_START` 99000 bis `AID_ISOLATED_END` 99999 je Nutzer).
 */
internal object SandboxProcess {
    private const val PER_USER_RANGE = 100_000
    private const val ISOLATED_START = 99_000
    private const val ISOLATED_END = 99_999

    fun isCurrent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Process.isIsolated()
        } else {
            isIsolatedUid(Process.myUid())
        }

    fun isIsolatedUid(uid: Int): Boolean = uid % PER_USER_RANGE in ISOLATED_START..ISOLATED_END
}
