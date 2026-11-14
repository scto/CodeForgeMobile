package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.runtime.MemoryPressure
import com.codeforge.core.domain.runtime.MemoryReleaser
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import javax.inject.Inject

/** Beendet ungenutzte Sprachserver; die Sitzung der aktiven Datei bleibt. */
class LspMemoryReleaser @Inject constructor(
    private val repository: LspClientRepositoryImpl
) : MemoryReleaser {
    override val label: String get() = Res.string(R.string.memory_lsp)

    override suspend fun release(pressure: MemoryPressure): Int =
        repository.releaseUnusedSessions(includeOpenDocuments = pressure != MemoryPressure.LOW)
}
