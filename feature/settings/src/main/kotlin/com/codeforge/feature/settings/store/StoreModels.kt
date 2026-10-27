// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.runtime.Immutable

/** Adresse des Store-Katalogs (GitHub Pages). Vorlage der Seite: Ordner `store-site/` im App-Repository. */
object StoreConfig {
    const val CATALOG_URL = "https://scto.github.io/codeforge-store/store.json"
    const val SUPPORTED_SCHEMA = 1
}

@Immutable
data class StoreCategory(val id: String, val title: String)

@Immutable
data class StoreItem(
    val id: String,
    val categoryId: String,
    val name: String,
    val summary: String,
    val version: String,
    val author: String,
    val homepageUrl: String?,
    val downloadUrl: String?,
    val sha256: String?,
)

@Immutable
data class StoreCatalog(
    val schema: Int,
    val updated: String,
    val categories: List<StoreCategory>,
    val items: List<StoreItem>,
) {
    fun itemsOf(categoryId: String): List<StoreItem> = items.filter { it.categoryId == categoryId }
}

class UnsupportedStoreSchemaException(val schema: Int) : Exception("Schema $schema wird nicht unterstützt")
