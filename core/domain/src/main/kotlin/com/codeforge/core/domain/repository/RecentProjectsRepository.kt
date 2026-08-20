// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.RecentProject
import kotlinx.coroutines.flow.Flow

/**
 * Liefert kürzlich geöffnete Projekte für den Welcome-Screen (:feature:welcome).
 * Implementiert in :core:data, persistiert vermutlich über :core:datastore Preferences
 * (Liste von Pfaden) statt Proto, da unstrukturiert/häufig wechselnd.
 */
interface RecentProjectsRepository {
    val recentProjects: Flow<List<RecentProject>>
    suspend fun addOrUpdate(project: RecentProject)
    suspend fun remove(projectId: String)
}
