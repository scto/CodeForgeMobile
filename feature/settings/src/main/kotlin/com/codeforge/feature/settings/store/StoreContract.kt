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
) {
    val selectedCategory: StoreCategory?
        get() = catalog?.categories?.let { cats -> cats.firstOrNull { it.id == selectedCategoryId } ?: cats.firstOrNull() }
}

sealed interface StoreUiEvent {
    data object Reload : StoreUiEvent
    data class SelectCategory(val id: String) : StoreUiEvent
}
