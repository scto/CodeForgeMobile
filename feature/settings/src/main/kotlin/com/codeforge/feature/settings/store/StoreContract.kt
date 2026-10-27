// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.runtime.Immutable

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
    /** Einmalige Rückmeldung (fertiger Text) für die Snackbar. */
    val message: String? = null,
) {
    val selectedCategory: StoreCategory?
        get() = catalog?.categories?.let { cats -> cats.firstOrNull { it.id == selectedCategoryId } ?: cats.firstOrNull() }
}

sealed interface StoreUiEvent {
    data object Reload : StoreUiEvent
    data class SelectCategory(val id: String) : StoreUiEvent
    data class Install(val item: StoreItem) : StoreUiEvent
    data object MessageShown : StoreUiEvent
}
