package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.domain.model.LspServerSettings
import com.codeforge.core.domain.repository.LspSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Einstellungen im Speicher für Tests. */
class FakeLspSettings(initial: Map<String, LspServerSettings> = emptyMap()) : LspSettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun update(serverId: String, change: (LspServerSettings) -> LspServerSettings) {
        val key = LspServerDefaults.keyFor(serverId)
        settings.update { it + (key to change(it[key] ?: LspServerSettings())) }
    }
}
