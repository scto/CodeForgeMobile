/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.StateFlow

/**
 * Vom Nutzer selbst angelegte Werkzeuge (ohne Plugin): externe Sprachserver und Formatierer.
 * [disabledFormatters] enthält Schlüssel der Form `pluginId:formatterId` (Nutzer-Formatierer: `user:id`).
 */
data class UserTools(
    val lspServers: List<LspServerContribution> = emptyList(),
    val formatters: List<FormatterContribution> = emptyList(),
    val disabledFormatters: Set<String> = emptySet(),
    /** Anzeigenamen ausgeschalteter Formatierer (Schlüssel wie in [disabledFormatters]), damit sie lesbar bleiben. */
    val disabledLabels: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Pseudo-Plugin-ID der Nutzer-Werkzeuge. */
        const val USER_PLUGIN_ID = "user"

        /** Schlüssel eines Formatierers für [disabledFormatters]. */
        fun formatterKey(pluginId: String, formatterId: String): String = "$pluginId:$formatterId"
    }
}

/** Verwaltet [UserTools] (persistent). */
interface UserToolsRepository {
    /** Aktueller Bestand. */
    val tools: StateFlow<UserTools>

    /** Legt einen externen Sprachserver an oder ersetzt den mit gleicher ID. */
    suspend fun saveLspServer(server: LspServerContribution)

    /** Entfernt einen externen Sprachserver. */
    suspend fun removeLspServer(id: String)

    /** Legt einen Nutzer-Formatierer an oder ersetzt den mit gleicher ID. */
    suspend fun saveFormatter(formatter: FormatterContribution)

    /** Entfernt einen Nutzer-Formatierer. */
    suspend fun removeFormatter(id: String)

    /** Schaltet einen Formatierer (Plugin oder Nutzer) ein oder aus. */
    suspend fun setFormatterEnabled(pluginId: String, formatterId: String, enabled: Boolean, label: String = formatterId)
}

/** Fügt Nutzer-Werkzeuge hinzu und entfernt ausgeschaltete Formatierer; Plugin-Beiträge gewinnen bei gleicher ID. */
fun ActiveContributions.withUserTools(user: UserTools): ActiveContributions {
    val lsp = lspServers + user.lspServers
        .filter { u -> lspServers.none { it.item.id == u.id } }
        .map { Resolved(UserTools.USER_PLUGIN_ID, "", it) }
    val fmts = (formatters + user.formatters
        .filter { u -> formatters.none { it.item.id == u.id } }
        .map { Resolved(UserTools.USER_PLUGIN_ID, "", it) })
        .filter { UserTools.formatterKey(it.pluginId, it.item.id) !in user.disabledFormatters }
    return copy(lspServers = lsp, formatters = fmts)
}
