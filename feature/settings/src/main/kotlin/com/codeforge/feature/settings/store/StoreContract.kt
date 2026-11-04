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
    /** Gewählte Kategorie, sonst die erste. */
    val selectedCategory: StoreCategory?
        get() = catalog?.categories?.let { cats -> cats.firstOrNull { it.id == selectedCategoryId } ?: cats.firstOrNull() }
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
    /** Meldung wurde angezeigt. */
    data object MessageShown : StoreUiEvent
}
