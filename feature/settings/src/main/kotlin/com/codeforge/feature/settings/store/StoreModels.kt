// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.runtime.Immutable

/** Adresse des Store-Katalogs (GitHub Pages). Vorlage der Seite: Ordner `store-site/` im App-Repository. */
object StoreConfig {
    const val CATALOG_URL = "https://scto.github.io/codeforge-store/store.json"
    /** Downloads und Bewertungen (täglich vom Store-Repo berechnet); fehlt sie, zeigt der Store keine Zahlen. */
    const val STATS_URL = "https://scto.github.io/codeforge-store/stats.json"
    /** Bewertungsformular (GitHub-Issue-Formular des Store-Repos). */
    const val RATING_URL = "https://github.com/scto/codeforge-store/issues/new?template=rating.yml"
    const val SUPPORTED_SCHEMA = 1
}

/** Kategorie des Stores. */
@Immutable
data class StoreCategory(val id: String, val title: String)

/** Eintrag im Store (Plugin, Theme, Tool usw.). */
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
    /** Kategorienpfade im Baum, z. B. `languages/jvm/kotlin` (leer bei älteren Katalogen). */
    val categories: List<String> = emptyList(),
    /** Schlagwörter (klein geschrieben). */
    val tags: List<String> = emptyList(),
    /** Größe des Pakets in Byte (0 = unbekannt). */
    val size: Long = 0,
    /** Erstes Erscheinen im Store (ISO-Datum) und letzte neue Version. */
    val published: String = "",
    val updated: String = "",
    /** Berechtigungen des Plugins (Anzeige). */
    val permissions: List<String> = emptyList(),
    /** Zweite Download-Adresse (GitHub Pages), falls die erste nicht erreichbar ist. */
    val mirrorUrl: String? = null,
) {
    /** Code-Plugin (nur Sideload). */
    val isCodePlugin: Boolean get() = categoryId == PLUGIN_CATEGORY
    /**
     * Direkt installierbar: deklarative Kategorie (Theme, Icons, Sprache, Grammatik, Snippets, LSP, Projektvorlage, Bundle),
     * HTTPS-Download und SHA-256. Die Item-ID ist die Plugin-ID. Code-Plugins (Kategorie „plugin“) gibt es
     * nur per manuellem Sideload; zusätzlich lehnt die Installation Archive mit ausführbarem Code ab.
     */
    /** Code-Tools führen Skripte aus: die App fragt vor der Installation nach. */
    val needsConfirmation: Boolean get() = categoryId == TOOL_CATEGORY

    /** Ob der Eintrag direkt installierbar ist. */
    val isInstallablePlugin: Boolean
        get() = categoryId in DECLARATIVE_CATEGORIES && sha256 != null && downloadUrl?.startsWith("https://") == true

    companion object {
        /** Kategorie der Sprachserver (Store-Plugins und lokale Server werden hier zusammengeführt). */
        const val LSP_CATEGORY = "lsp"
        const val PLUGIN_CATEGORY = "plugin"
        const val TOOL_CATEGORY = "tool"
        /** Projektvorlagen für den Projekt-Assistenten. */
        const val TEMPLATE_CATEGORY = "template"
        val DECLARATIVE_CATEGORIES =
            setOf("theme", "icons", "language", "grammar", "snippets", "lsp", "formatter", "bundle", TEMPLATE_CATEGORY, TOOL_CATEGORY)
    }
}

/** Katalog mit Kategorien und Einträgen. */
@Immutable
data class StoreCatalog(
    val schema: Int,
    val updated: String,
    val categories: List<StoreCategory>,
    val items: List<StoreItem>,
    /** Kategorienbaum, flach als Pfade (`languages`, `languages/jvm`, …); leer bei älteren Katalogen. */
    val tree: List<StoreCategory> = emptyList(),
    val statsUrl: String = StoreConfig.STATS_URL,
    val ratingUrl: String = StoreConfig.RATING_URL,
) {
    /** Einträge einer Kategorie. */
    fun itemsOf(categoryId: String): List<StoreItem> = items.filter { it.categoryId == categoryId }
}

/** Der Katalog nutzt ein nicht unterstütztes Schema. */
class UnsupportedStoreSchemaException(val schema: Int) : Exception("Schema $schema wird nicht unterstützt")

/** Eine Rezension aus `stats.json`. */
@Immutable
data class StoreReview(val user: String, val stars: Int, val text: String, val date: String, val url: String?)

/** Downloads und Bewertungen eines Pakets. */
@Immutable
data class StoreItemStats(
    val downloads: Long = 0,
    /** Durchschnitt 1–5 (0 = keine Bewertung). */
    val rating: Double = 0.0,
    val ratings: Int = 0,
    /** Anzahl je Sternzahl, Index 0 = 1 Stern. */
    val stars: List<Int> = emptyList(),
    val reviews: List<StoreReview> = emptyList(),
)

/** Statistik des Stores. */
@Immutable
data class StoreStats(val updated: String = "", val items: Map<String, StoreItemStats> = emptyMap()) {
    operator fun get(id: String): StoreItemStats = items[id] ?: StoreItemStats()
}

/** Sortierung der Liste. */
enum class StoreSort { RELEVANCE, DOWNLOADS, RATING, NAME, UPDATED, NEWEST }
