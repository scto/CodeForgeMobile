// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspRuntimeStats
import com.codeforge.core.domain.model.LspRuntimeStatsProvider
import com.codeforge.core.domain.model.LspServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LspRuntimeStatsProviderImpl @Inject constructor(
    private val client: LspClientRepositoryImpl
) : LspRuntimeStatsProvider {

    override suspend fun statsFor(serverIds: Set<String>): LspRuntimeStats? = withContext(Dispatchers.IO) {
        val sessions = client.getAllSessions().filter { it.serverId in serverIds }
        if (sessions.isEmpty()) return@withContext null
        val pids = sessions.mapNotNull { it.processId() }
        val states = sessions.map { it.serverState.value }
        LspRuntimeStats(
            serverId = sessions.first().serverId,
            sessionCount = sessions.size,
            state = when {
                LspServerState.RUNNING in states -> LspServerState.RUNNING
                LspServerState.STARTING in states -> LspServerState.STARTING
                else -> states.first()
            },
            memoryBytes = if (pids.isEmpty()) null else ProcFs.treeRssBytes(pids),
            startedAtMillis = sessions.mapNotNull { it.startedAtMillis }.minOrNull(),
            lastUsedMillis = sessions.maxOf { it.lastUsedTimestamp },
            openDocuments = sessions.sumOf { it.openDocumentCount },
            processIds = pids
        )
    }
}
