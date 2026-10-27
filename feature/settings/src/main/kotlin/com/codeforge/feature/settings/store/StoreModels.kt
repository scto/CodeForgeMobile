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
) {
    /**
     * Direkt installierbar: deklarative Kategorie (Theme, Icons, Sprache, Grammatik, Snippets, LSP, Bundle),
     * HTTPS-Download und SHA-256. Die Item-ID ist die Plugin-ID. Code-Plugins (Kategorie „plugin“) gibt es
     * nur per manuellem Sideload; zusätzlich lehnt die Installation Archive mit ausführbarem Code ab.
     */
    val isInstallablePlugin: Boolean
        get() = categoryId in DECLARATIVE_CATEGORIES && sha256 != null && downloadUrl?.startsWith("https://") == true

    companion object {
        const val PLUGIN_CATEGORY = "plugin"
        val DECLARATIVE_CATEGORIES = setOf("theme", "icons", "language", "grammar", "snippets", "lsp", "formatter", "bundle")
    }
}

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
