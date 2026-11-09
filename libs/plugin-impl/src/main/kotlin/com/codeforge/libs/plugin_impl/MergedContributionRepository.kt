/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.UserToolsRepository
import com.codeforge.core.domain.extension.withUserTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Gesamtsicht aus Plugin-Beiträgen und Nutzer-Werkzeugen (externe Sprachserver, Formatierer). */
@Singleton
class MergedContributionRepository @Inject constructor(
    plugins: PluginRepositoryImpl,
    user: UserToolsRepository,
) : ContributionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val contributions: StateFlow<ActiveContributions> =
        combine(plugins.contributions, user.tools) { c, u -> c.withUserTools(u) }
            .stateIn(scope, SharingStarted.Eagerly, plugins.contributions.value.withUserTools(user.tools.value))
}
