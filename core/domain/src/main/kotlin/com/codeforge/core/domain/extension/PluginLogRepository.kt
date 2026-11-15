/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.StateFlow

/** Stufe einer Protokollzeile eines Plugins. */
enum class PluginLogLevel { DEBUG, INFO, WARN, ERROR }

/** Eine Protokollzeile eines Plugins. */
data class PluginLogLine(
    val timeMillis: Long,
    val level: PluginLogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Protokolle der Plugins: je Plugin die letzten Zeilen im Speicher (unabhängig davon, ob das Datei-Log der App
 * eingeschaltet ist), zusätzlich ins App-Log mit dem Tag `plugin/<id>`.
 */
interface PluginLogRepository {
    /** Zeilen von [pluginId], älteste zuerst. */
    fun observe(pluginId: String): StateFlow<List<PluginLogLine>>

    /** Schreibt eine Zeile für [pluginId]. */
    fun append(pluginId: String, line: PluginLogLine)

    /** Löscht die Zeilen von [pluginId]. */
    fun clear(pluginId: String)
}
