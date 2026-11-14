// Modul: :feature:plugins
package com.codeforge.feature.plugins

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.ToolRunEvent
import com.codeforge.core.domain.runtime.IdeActivityRegistry.trackedAs
import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Laufende Installation der Voraussetzungen eines Plugins; [lastLine] ist die jüngste Ausgabezeile. */
@Immutable
data class RequirementInstall(val pluginId: String, val lastLine: String = "", val percent: Int? = null)

/** Ergebnis einer Installation; [tail] sind die letzten Ausgabezeilen (für Fehlermeldungen). */
@Immutable
data class RequirementInstallOutcome(val pluginId: String, val exitCode: Int, val tail: List<String>) {
    /** Ob alles installiert wurde. */
    val success: Boolean get() = exitCode == 0
}

/**
 * Installiert die fehlenden Voraussetzungen (`contributes.requires`) eines Plugins im App-Bereich: Die Installation
 * läuft weiter, wenn der Bildschirm verlassen wird, hält den Vordergrunddienst ([trackedAs]) und erscheint in der
 * Fortschrittsleiste. Je Plugin läuft höchstens eine Installation.
 */
@Singleton
class RequirementInstallCoordinator internal constructor(
    private val runner: PluginToolRunner,
    private val scope: CoroutineScope
) {
    private val _active = MutableStateFlow<Map<String, RequirementInstall>>(emptyMap())
    /** Laufende Installationen je Plugin-ID. */
    val active: StateFlow<Map<String, RequirementInstall>> = _active.asStateFlow()

    private val _outcomes = MutableSharedFlow<RequirementInstallOutcome>(extraBufferCapacity = 8)
    /** Abgeschlossene Installationen. */
    val outcomes: SharedFlow<RequirementInstallOutcome> = _outcomes.asSharedFlow()

    @Inject
    constructor(runner: PluginToolRunner) : this(runner, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** Startet die Installation für [pluginId] mit dem Titel [title]; ohne fehlende Voraussetzungen sofort erfolgreich. */
    fun install(pluginId: String, title: String) {
        if (pluginId in _active.value) return
        _active.update { it + (pluginId to RequirementInstall(pluginId)) }
        scope.launch {
            val progressId = IdeProgressRegistry.begin(IdeProgressKind.INSTALL, title)
            val tail = ArrayDeque<String>()
            val exitCode = try {
                runCatching { run(pluginId, title, progressId, tail) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse {
                        tail.addLast(it.message ?: it.javaClass.simpleName)
                        -1
                    }
            } finally {
                IdeProgressRegistry.end(progressId)
                _active.update { it - pluginId }
            }
            _outcomes.emit(RequirementInstallOutcome(pluginId, exitCode, tail.toList()))
        }
    }

    private suspend fun run(pluginId: String, title: String, progressId: Long, tail: ArrayDeque<String>): Int {
        val missing = runner.missingRequirements(pluginId)
        if (missing.isEmpty()) return 0
        var exitCode = 0
        runner.install(missing).trackedAs(title).collect { event ->
            when (event) {
                is ToolRunEvent.Line -> onLine(pluginId, progressId, event.text, tail)
                is ToolRunEvent.Finished -> exitCode = event.exitCode
            }
        }
        return exitCode
    }

    private fun onLine(pluginId: String, progressId: Long, line: String, tail: ArrayDeque<String>) {
        if (line.isBlank()) return
        tail.addLast(line)
        if (tail.size > TAIL_LINES) tail.removeFirst()
        val percent = PERCENT.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()
        IdeProgressRegistry.update(progressId, line, percent)
        _active.update { map ->
            map[pluginId]?.let { map + (pluginId to it.copy(lastLine = line, percent = percent ?: it.percent)) } ?: map
        }
    }

    private companion object {
        const val TAIL_LINES = 8
        val PERCENT = Regex("(\\d{1,3}) %")
    }
}
