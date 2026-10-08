/**
 * Modul: :core:navigation
 * @author Thomas Schmid
 *
 * Kanal zwischen Modulen, die Dateien auf der Platte ändern (z. B. :feature:dependencyupdates
 * über :libs:dependency-updater-impl) und dem Editor (:feature:editor), der offene Puffer hält.
 * Liegt in :core:navigation, weil :feature:*→:feature:*-Abhängigkeiten verboten sind (analog zu
 * [OpenFileRequestBridge]).
 *
 * Ablauf bei einer Änderung durch ein Fremdmodul:
 *  1. [requestFlush]: Editor schreibt ungespeicherte Puffer der betroffenen Dateien auf die Platte,
 *     damit die Änderung nicht gegen einen veralteten Plattenstand läuft.
 *  2. Das Fremdmodul ändert die Dateien.
 *  3. [notifyExternalChange]: Editor lädt die betroffenen offenen Tabs neu.
 */
package com.codeforge.core.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aufforderung an den Editor, ungespeicherte Puffer zu speichern; der Editor ruft danach [done] ab.
 * [directory] = `true`: [paths] enthält Ordner, gespeichert wird alles darunter.
 */
class FlushRequest(val paths: Set<String>, val directory: Boolean = false) {
    val done: CompletableDeferred<Unit> = CompletableDeferred()
}

interface FileSyncBridge {
    val flushRequests: SharedFlow<FlushRequest>
    val externalChanges: SharedFlow<Set<String>>

    /** Ein ganzer Ordner hat sich geändert (Git-Checkout, Pull, Reset …) → offene Tabs darunter neu laden. */
    val directoryChanges: SharedFlow<String>

    /** Wartet (max. ~5 s), bis der Editor gespeichert hat; kehrt sofort zurück, wenn kein Editor lauscht. */
    suspend fun requestFlush(paths: Set<String>)

    suspend fun notifyExternalChange(paths: Collection<String>)

    /** Wie [requestFlush], aber für alle offenen Dateien unterhalb von [directory]. */
    suspend fun requestFlushDirectory(directory: String)

    suspend fun notifyDirectoryChange(directory: String)
}

@Singleton
class FileSyncBridgeImpl @Inject constructor() : FileSyncBridge {
    private val _flushRequests = MutableSharedFlow<FlushRequest>(extraBufferCapacity = 8)
    private val _externalChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    private val _directoryChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override val flushRequests: SharedFlow<FlushRequest> = _flushRequests.asSharedFlow()
    override val externalChanges: SharedFlow<Set<String>> = _externalChanges.asSharedFlow()
    override val directoryChanges: SharedFlow<String> = _directoryChanges.asSharedFlow()

    override suspend fun requestFlush(paths: Set<String>) {
        if (paths.isEmpty() || _flushRequests.subscriptionCount.value == 0) return
        val request = FlushRequest(paths)
        _flushRequests.emit(request)
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) { request.done.await() }
    }

    override suspend fun notifyExternalChange(paths: Collection<String>) {
        if (paths.isNotEmpty()) _externalChanges.emit(paths.toSet())
    }

    override suspend fun requestFlushDirectory(directory: String) {
        if (_flushRequests.subscriptionCount.value == 0) return
        val request = FlushRequest(setOf(directory), directory = true)
        _flushRequests.emit(request)
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) { request.done.await() }
    }

    override suspend fun notifyDirectoryChange(directory: String) {
        _directoryChanges.emit(directory)
    }

    private companion object {
        const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
