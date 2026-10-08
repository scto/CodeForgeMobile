/**
 * Modul: :core:navigation
 * @author Thomas Schmid
 *
 * Kommunikationskanal zwischen :feature:filetree (Publisher: per Bonsai-Baum im
 * NavigationDrawer angeklickte Datei) und :feature:editor (Consumer: öffnet die Datei als
 * neuen Tab). Liegt in :core:navigation, da die Dependency-Regel direkte
 * :feature:*→:feature:*-Abhängigkeiten verbietet — analog zu [ActiveComposablePreviewBridge].
 *
 * Als [SharedFlow] (nicht [StateFlow]) modelliert: jeder Klick ist ein einmaliges Ereignis,
 * kein dauerhafter Zustand — ein erneutes Öffnen derselben Datei (gleicher Pfad) muss erneut
 * ausgelöst werden, auch wenn sich der "Wert" nicht ändert.
 */
package com.codeforge.core.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Sprungziel in einer Datei (1-basierte [line]/[column], [length] Zeichen markieren). */
data class JumpTarget(val path: String, val line: Int, val column: Int, val length: Int)

interface OpenFileRequestBridge {
    val openRequests: SharedFlow<String>

    /** Wird NACH [openRequests] für dieselbe Datei emittiert; der Editor positioniert den Cursor. */
    val jumpRequests: SharedFlow<JumpTarget>
    suspend fun requestOpen(path: String)

    /** Öffnet [path] und springt zur Position (z. B. Treffer der Projektsuche). */
    suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int = 0)
}

@Singleton
class OpenFileRequestBridgeImpl @Inject constructor() : OpenFileRequestBridge {
    private val _openRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val openRequests: SharedFlow<String> = _openRequests.asSharedFlow()

    private val _jumpRequests = MutableSharedFlow<JumpTarget>(extraBufferCapacity = 4)
    override val jumpRequests: SharedFlow<JumpTarget> = _jumpRequests.asSharedFlow()

    override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
        _openRequests.emit(path)
        _jumpRequests.emit(JumpTarget(path, line, column, length))
    }

    override suspend fun requestOpen(path: String) {
        _openRequests.emit(path)
    }
}
