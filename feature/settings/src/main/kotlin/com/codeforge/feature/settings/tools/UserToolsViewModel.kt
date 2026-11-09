// Modul: :feature:settings
package com.codeforge.feature.settings.tools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.FormatterContribution
import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.domain.extension.UserTools
import com.codeforge.core.domain.extension.UserToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Formatierer in der Verwaltung (Plugin oder selbst angelegt). */
@Immutable
data class FormatterRow(
    val pluginId: String,
    val formatter: FormatterContribution,
    val enabled: Boolean,
) {
    /** `true` bei selbst angelegten Formatierern (löschbar). */
    val isUser: Boolean get() = pluginId == UserTools.USER_PLUGIN_ID
}

/** Zustand der Nutzer-Werkzeuge. */
@Immutable
data class UserToolsUiState(
    val lspServers: List<LspServerContribution> = emptyList(),
    val formatters: List<FormatterRow> = emptyList(),
)

/** Aktionen der Nutzer-Werkzeuge. */
sealed interface UserToolsEvent {
    /** Externen Sprachserver speichern. */
    data class SaveLsp(val server: LspServerContribution) : UserToolsEvent
    /** Externen Sprachserver entfernen. */
    data class RemoveLsp(val id: String) : UserToolsEvent
    /** Formatierer speichern. */
    data class SaveFormatter(val formatter: FormatterContribution) : UserToolsEvent
    /** Selbst angelegten Formatierer entfernen. */
    data class RemoveFormatter(val id: String) : UserToolsEvent
    /** Formatierer ein-/ausschalten. */
    data class SetFormatterEnabled(val pluginId: String, val id: String, val enabled: Boolean) : UserToolsEvent
}

/** ViewModel für externe Sprachserver und Formatierer. */
@HiltViewModel
class UserToolsViewModel @Inject constructor(
    private val repository: UserToolsRepository,
    contributions: ContributionRepository,
) : ViewModel() {

    /** UI-Zustand: Plugin-Formatierer erscheinen auch dann, wenn sie ausgeschaltet sind. */
    val uiState: StateFlow<UserToolsUiState> = combine(repository.tools, contributions.contributions) { user, active ->
        // `contributions` enthält ausgeschaltete Formatierer nicht mehr; Plugin-Formatierer sind daher immer „an“.
        val plugin = active.formatters.filter { it.pluginId != UserTools.USER_PLUGIN_ID }
            .map { FormatterRow(it.pluginId, it.item, enabled = true) }
        val own = user.formatters.map {
            FormatterRow(UserTools.USER_PLUGIN_ID, it, UserTools.formatterKey(UserTools.USER_PLUGIN_ID, it.id) !in user.disabledFormatters)
        }
        val disabledPlugin = user.disabledFormatters.filterNot { it.startsWith(UserTools.USER_PLUGIN_ID + ":") }
        // Ausgeschaltete Plugin-Formatierer stehen nicht mehr in `contributions`: Schlüssel anzeigen, damit sie wieder aktiviert werden können.
        val off = disabledPlugin.map { key ->
            val pid = key.substringBefore(':')
            val fid = key.substringAfter(':')
            FormatterRow(pid, FormatterContribution(fid, fid, emptyList(), emptyList(), listOf(fid)), enabled = false)
        }
        UserToolsUiState(lspServers = user.lspServers, formatters = plugin + own + off)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserToolsUiState())

    /** Verarbeitet eine Aktion. */
    fun onEvent(event: UserToolsEvent) {
        viewModelScope.launch {
            when (event) {
                is UserToolsEvent.SaveLsp -> repository.saveLspServer(event.server)
                is UserToolsEvent.RemoveLsp -> repository.removeLspServer(event.id)
                is UserToolsEvent.SaveFormatter -> repository.saveFormatter(event.formatter)
                is UserToolsEvent.RemoveFormatter -> repository.removeFormatter(event.id)
                is UserToolsEvent.SetFormatterEnabled -> repository.setFormatterEnabled(event.pluginId, event.id, event.enabled)
            }
        }
    }
}
