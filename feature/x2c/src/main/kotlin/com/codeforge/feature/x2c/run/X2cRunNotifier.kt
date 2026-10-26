package com.codeforge.feature.x2c.run

import com.codeforge.feature.x2c.X2cPhase

/**
 * Hält einen langen Lauf am Leben und zeigt ihn in der Statusleiste. Als Schnittstelle, damit das ViewModel ohne
 * Android-Framework getestet werden kann.
 */
interface X2cRunNotifier {
    /** Wird beim Start eines Laufs (Migration, Build, Rückgängig) aufgerufen. */
    fun started(onCancelRequested: () -> Unit)

    /** [percent] 0..99 oder `null` = unbestimmt. */
    fun update(phase: X2cPhase?, percent: Int?)

    fun finished()
}
