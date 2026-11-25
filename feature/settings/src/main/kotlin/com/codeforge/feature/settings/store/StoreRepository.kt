// Modul: :feature:settings
package com.codeforge.feature.settings.store

import android.content.Context
import com.codeforge.feature.settings.lsp.DefaultHttpTextFetcher
import com.codeforge.feature.settings.lsp.HttpTextFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis des Ladens. */
sealed interface StoreLoadResult {
    /** Katalog online geladen. */
    data class Online(val catalog: StoreCatalog) : StoreLoadResult
    /** Katalog stammt aus dem Zwischenspeicher (offline); [cachedAt] ist lesbar formatiert. */
    data class Cached(val catalog: StoreCatalog, val cachedAt: String) : StoreLoadResult
    /** Laden fehlgeschlagen. */
    data class Failed(val message: String, val unsupportedSchema: Boolean = false) : StoreLoadResult
}

/** Lädt den Katalog von GitHub Pages und hält die letzte gültige Fassung offline bereit. */
@Singleton
class StoreRepository internal constructor(
    private val fetcher: HttpTextFetcher,
    private val cacheFile: File,
    private val url: String = StoreConfig.CATALOG_URL,
) {
    @Inject constructor(
        fetcher: DefaultHttpTextFetcher,
        @ApplicationContext context: Context,
    ) : this(fetcher, File(context.filesDir, "store-catalog.json"))

    /** Lädt die Statistik (Downloads, Bewertungen); offline die zuletzt geladene, sonst leer. */
    suspend fun loadStats(url: String): StoreStats = withContext(Dispatchers.IO) {
        val cache = File(cacheFile.parentFile, "store-stats.json")
        fetcher.fetch(url).getOrNull()?.let { body ->
            val stats = StoreCatalogParser.parseStats(body)
            if (stats.items.isNotEmpty()) runCatching { cache.writeText(body) }
            return@withContext stats
        }
        runCatching { cache.takeIf { it.isFile }?.readText() }.getOrNull()?.let(StoreCatalogParser::parseStats) ?: StoreStats()
    }

    /** Lädt den Katalog (online, sonst Zwischenspeicher). */
    suspend fun load(language: String): StoreLoadResult = withContext(Dispatchers.IO) {
        val fetched = fetcher.fetch(url)
        fetched.onSuccess { body ->
            val parsed = StoreCatalogParser.parse(body, language)
            parsed.onSuccess { catalog ->
                runCatching { cacheFile.writeText(body) }
                return@withContext StoreLoadResult.Online(catalog)
            }
            val err = parsed.exceptionOrNull()
            if (err is UnsupportedStoreSchemaException) return@withContext StoreLoadResult.Failed(err.message.orEmpty(), true)
        }
        val reason = fetched.exceptionOrNull()?.message ?: "ungültiger Katalog"
        val cached = runCatching { cacheFile.takeIf { it.isFile } }.getOrNull()
        val catalog = cached?.let { StoreCatalogParser.parse(it.readText(), language).getOrNull() }
        if (cached != null && catalog != null) {
            StoreLoadResult.Cached(catalog, DateFormat.getDateTimeInstance().format(Date(cached.lastModified())))
        } else {
            StoreLoadResult.Failed(reason)
        }
    }
}
