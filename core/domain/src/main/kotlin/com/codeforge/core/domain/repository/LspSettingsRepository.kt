package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.domain.model.LspServerSettings
import kotlinx.coroutines.flow.StateFlow

/** Einstellungen der Language-Server (an/aus, einzelne Fähigkeiten, JVM-Optionen). */
interface LspSettingsRepository {
    /** Alle gespeicherten Einstellungen, Schlüssel siehe [LspServerDefaults.keyFor]. */
    val settings: StateFlow<Map<String, LspServerSettings>>

    /** Ändert die Einstellungen eines Servers und speichert sie. */
    suspend fun update(serverId: String, change: (LspServerSettings) -> LspServerSettings)

    /** Einstellungen eines Servers (Standard, wenn nichts gespeichert ist). */
    fun of(serverId: String): LspServerSettings =
        settings.value[LspServerDefaults.keyFor(serverId)] ?: LspServerSettings()

    /** Ob [feature] für den Server genutzt werden darf. */
    fun allows(serverId: String, feature: LspFeature): Boolean = of(serverId).allows(feature)

    /** Wirksame JVM-Optionen eines Servers (gespeichert, sonst Vorgabe) oder `null`, wenn kein JVM-Server. */
    fun jvmOptions(serverId: String): JvmOptions? {
        val key = LspServerDefaults.keyFor(serverId)
        if (key !in LspServerDefaults.JVM_SERVERS) return null
        return of(key).jvm ?: LspServerDefaults.jvmFor(key)
    }
}
