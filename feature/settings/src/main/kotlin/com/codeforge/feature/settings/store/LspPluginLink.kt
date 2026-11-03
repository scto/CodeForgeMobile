// Modul: :feature:settings
package com.codeforge.feature.settings.store

/**
 * Verknüpft ein Store-Plugin der Kategorie „lsp“ (`com.codeforge.plugin.<name>.lsp`) mit dem passenden
 * Server-Programm im Reiter „Sprachserver“ (Paket-ID im [com.codeforge.feature.settings.extensions.LspExtensionCatalog]).
 * Das Plugin bindet den Server nur ein; das Programm selbst installiert der Extensions-Manager.
 */
object LspPluginLink {
    private const val PREFIX = "com.codeforge.plugin."
    private const val SUFFIX = ".lsp"

    /** Plugin-Namen, die im Extensions-Katalog unter anderer ID stehen. */
    private val ALIASES = mapOf("web-langservers" to "vscode-langservers-extracted")

    /** Paket-ID im Extensions-Katalog oder `null`, wenn die ID kein LSP-Plugin dieses Schemas ist. */
    fun extensionIdFor(pluginId: String): String? {
        if (!pluginId.startsWith(PREFIX) || !pluginId.endsWith(SUFFIX)) return null
        val name = pluginId.removePrefix(PREFIX).removeSuffix(SUFFIX)
        if (name.isBlank()) return null
        return ALIASES[name] ?: name
    }
}

/** Zustand des zugehörigen Server-Programms eines LSP-Store-Eintrags. */
enum class LspServerState { PRESENT, MISSING }
