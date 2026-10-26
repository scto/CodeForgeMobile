// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class ExtensionInstallProgress(
    val pkg: LspExtensionPackage,
    val status: ExtensionStatus,
    val progress: Float
)

@Immutable
data class ExtensionInstallOutcome(
    val pkg: LspExtensionPackage,
    val result: Result<File>
)

/** Startet den Vordergrunddienst, solange Installationen laufen (austauschbar für Tests). */
fun interface ExtensionInstallServiceStarter {
    fun start()
}

/**
 * Führt Installationen von Erweiterungen (LSP-Server) prozessweit aus, unabhängig vom Screen/ViewModel.
 *
 * Läuft in einem eigenen Scope; der [ExtensionInstallService] hält den Prozess als Vordergrunddienst am Leben,
 * damit Download, Prüfung und Entpacken auch weiterlaufen, wenn die App im Hintergrund ist oder der Screen
 * verlassen wird. Zustand und Ergebnisse stehen als Flows bereit.
 */
@Singleton
class ExtensionInstallCoordinator internal constructor(
    private val manager: ExtensionsManager,
    private val scope: CoroutineScope,
    private val serviceStarter: ExtensionInstallServiceStarter
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        manager: ExtensionsManager
    ) : this(
        manager = manager,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        serviceStarter = ExtensionInstallServiceStarter { ExtensionInstallService.start(context) }
    )

    private val jobs = ConcurrentHashMap<String, Job>()

    private val _active = MutableStateFlow<Map<String, ExtensionInstallProgress>>(emptyMap())

    /** Laufende Installationen je Paket-ID. Leer = nichts zu tun (Dienst beendet sich). */
    val active: StateFlow<Map<String, ExtensionInstallProgress>> = _active.asStateFlow()

    private val _outcomes = MutableSharedFlow<ExtensionInstallOutcome>(extraBufferCapacity = 16)

    /** Ergebnis je abgeschlossener Installation (nicht bei Abbruch). */
    val outcomes: SharedFlow<ExtensionInstallOutcome> = _outcomes.asSharedFlow()

    fun install(pkg: LspExtensionPackage) {
        val started = synchronized(this) {
            if (_active.value.containsKey(pkg.id)) {
                false
            } else {
                _active.update { it + (pkg.id to ExtensionInstallProgress(pkg, ExtensionStatus.DOWNLOADING, 0f)) }
                true
            }
        }
        if (!started) return
        serviceStarter.start()
        jobs[pkg.id] = scope.launch {
            val result = manager.install(pkg) { progress, status ->
                _active.update { map ->
                    if (map.containsKey(pkg.id)) map + (pkg.id to ExtensionInstallProgress(pkg, status, progress)) else map
                }
            }
            jobs.remove(pkg.id)
            _active.update { it - pkg.id }
            _outcomes.emit(ExtensionInstallOutcome(pkg, result))
        }
    }

    /** Bricht alle laufenden Installationen ab (z. B. über die Benachrichtigung). */
    fun cancelAll() {
        val running = jobs.keys.toList()
        running.forEach { id -> jobs.remove(id)?.cancel() }
        _active.update { it - running.toSet() }
    }
}
