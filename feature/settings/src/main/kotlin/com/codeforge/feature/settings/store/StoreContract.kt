// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.runtime.Immutable

/** Zustand des Stores (Katalog, Kategorie, Installation, Meldungen). */
@Immutable
data class StoreUiState(
    val isLoading: Boolean = true,
    val catalog: StoreCatalog? = null,
    val selectedCategoryId: String? = null,
    /** Zeitpunkt der zwischengespeicherten Fassung, falls offline. */
    val cachedAt: String? = null,
    val errorMessage: String? = null,
    val unsupportedSchema: Boolean = false,
    /** Bereits installierte Plugin-IDs (Item-ID = Plugin-ID). */
    val installedPluginIds: Set<String> = emptySet(),
    /** Laufende Installation: Item-ID und Fortschritt 0..1 (null = unbestimmt). */
    val installingId: String? = null,
    val installProgress: Float? = null,
    /** Item-ID (LSP-Plugin) -> Zustand des Server-Programms; nur für Einträge mit passendem Extensions-Paket. */
    val serverStates: Map<String, LspServerState> = emptyMap(),
    /** Item-IDs, deren Server-Programm gerade nachinstalliert wird. */
    val installingServerIds: Set<String> = emptySet(),
    /** Einmalige Rückmeldung (fertiger Text) für die Snackbar. */
    val message: String? = null,
) {
    /** Gewählter Filter: [FILTER_ALL] (Standard), [FILTER_INSTALLED] oder eine Kategorie-ID. */
    val filter: String
        get() = selectedCategoryId ?: FILTER_ALL

    companion object {
        /** Alle Einträge des Katalogs. */
        const val FILTER_ALL = "__all__"
        /** Installierte Plugins (Verwaltung: Aktivieren, Entfernen, Sideload). */
        const val FILTER_INSTALLED = "__installed__"
    }
}

/** Aktionen im Store. */
sealed interface StoreUiEvent {
    /** Katalog neu laden. */
    data object Reload : StoreUiEvent
    /** Kategorie wählen. */
    data class SelectCategory(val id: String) : StoreUiEvent
    /** Eintrag installieren. */
    data class Install(val item: StoreItem) : StoreUiEvent
    /** Server-Programm eines Eintrags installieren. */
    data class InstallServer(val item: StoreItem) : StoreUiEvent
    /** Eintrag entfernen (Plugin und – bei Sprachservern – das Server-Programm). */
    data class Uninstall(val item: StoreItem) : StoreUiEvent
    /** Meldung (z. B. aus der Plugin-Verwaltung) anzeigen. */
    data class ShowMessage(val text: String) : StoreUiEvent
    /** Meldung wurde angezeigt. */
    data object MessageShown : StoreUiEvent
}
