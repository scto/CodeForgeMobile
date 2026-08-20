// Modul: :core:data
package com.codeforge.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.RecentProjectsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val RECENT_PROJECTS_KEY = stringSetPreferencesKey("recent_projects")
private const val FIELD_SEPARATOR = "\u0001"

/**
 * Persistiert Recent-Projects als String-Set in Preferences-DataStore (nicht im Proto-DataStore,
 * da unstrukturiert/häufig wechselnd — siehe Kommentar in RecentProjectsRepository.kt).
 * Jeder Eintrag: "id\u0001name\u0001path\u0001lastOpenedEpochMillis\u0001moduleCount".
 */
@Singleton
class RecentProjectsRepositoryImpl @Inject constructor(
    private val preferencesDataStore: DataStore<Preferences>
) : RecentProjectsRepository {

    override val recentProjects: Flow<List<RecentProject>> =
        preferencesDataStore.data.map { prefs ->
            prefs[RECENT_PROJECTS_KEY]
                ?.mapNotNull(::decode)
                ?.sortedByDescending { it.lastOpenedEpochMillis }
                ?: emptyList()
        }

    override suspend fun addOrUpdate(project: RecentProject) {
        preferencesDataStore.edit { prefs ->
            val current = prefs[RECENT_PROJECTS_KEY].orEmpty()
                .mapNotNull(::decode)
                .filterNot { it.id == project.id }
            prefs[RECENT_PROJECTS_KEY] = (current + project).map(::encode).toSet()
        }
    }

    override suspend fun remove(projectId: String) {
        preferencesDataStore.edit { prefs ->
            val current = prefs[RECENT_PROJECTS_KEY].orEmpty().mapNotNull(::decode)
            prefs[RECENT_PROJECTS_KEY] = current.filterNot { it.id == projectId }.map(::encode).toSet()
        }
    }

    private fun encode(project: RecentProject): String = listOf(
        project.id, project.name, project.path,
        project.lastOpenedEpochMillis.toString(), project.moduleCount.toString()
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String): RecentProject? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 5) return null
        return RecentProject(
            id = parts[0],
            name = parts[1],
            path = parts[2],
            lastOpenedEpochMillis = parts[3].toLongOrNull() ?: return null,
            moduleCount = parts[4].toIntOrNull() ?: 1
        )
    }
}
