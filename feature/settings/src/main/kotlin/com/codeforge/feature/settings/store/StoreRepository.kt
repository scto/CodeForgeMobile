// Modul: :feature:settings
package com.codeforge.feature.settings.store

import android.content.Context
import com.codeforge.feature.settings.lsp.ConditionalText
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
    /** Katalog online geladen (oder vom Server als unverändert bestätigt). */
    data class Online(val catalog: StoreCatalog) : StoreLoadResult
    /**
     * Katalog stammt aus dem Zwischenspeicher; [cachedAt] ist lesbar formatiert.
     * [bundled] = die mit der App ausgelieferte Fassung (noch nie online geladen).
     */
    data class Cached(val catalog: StoreCatalog, val cachedAt: String, val bundled: Boolean = false) : StoreLoadResult
    /** Laden fehlgeschlagen. */
    data class Failed(val message: String, val unsupportedSchema: Boolean = false) : StoreLoadResult
}

/**
 * Lädt den Katalog von GitHub Pages – nach dem Vorbild des CodeAssist-Stores:
 *  1. [cached] liefert sofort die letzte Fassung (Datei, sonst die eingebaute aus den Assets),
 *  2. [refresh] fragt im Hintergrund mit `If-None-Match` nach; unverändert kostet das nur ein 304,
 *  3. ein online geladener Katalog wird [MEMO_MS] im Speicher wiederverwendet (Tab-Wechsel ohne Anfrage).
 */
@Singleton
class StoreRepository internal constructor(
    private val fetcher: HttpTextFetcher,
    private val cacheFile: File,
    private val url: String = StoreConfig.CATALOG_URL,
    private val bundled: () -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val etagFile get() = File(cacheFile.parentFile, cacheFile.name + ".etag")
    private val statsFile get() = File(cacheFile.parentFile, "store-stats.json")

    private data class Memo(val language: String, val catalog: StoreCatalog, val at: Long)

    @Volatile
    private var memo: Memo? = null

    @Inject constructor(
        fetcher: DefaultHttpTextFetcher,
        @ApplicationContext context: Context,
    ) : this(fetcher, File(context.filesDir, "store-catalog.json"), bundled = { readBundledCatalog(context) })

    /** Letzte bekannte Fassung ohne Netz: Datei, sonst eingebauter Katalog; `null`, wenn keine lesbar ist. */
    suspend fun cached(language: String): StoreLoadResult.Cached? = withContext(Dispatchers.IO) {
        val file = cacheFile.takeIf { it.isFile }
        file?.let { runCatching { StoreCatalogParser.parse(it.readText(), language).getOrNull() }.getOrNull() }?.let {
            return@withContext StoreLoadResult.Cached(it, DateFormat.getDateTimeInstance().format(Date(file.lastModified())))
        }
        bundled()?.let { body -> StoreCatalogParser.parse(body, language).getOrNull() }?.let { catalog ->
            StoreLoadResult.Cached(catalog, catalog.updated, bundled = true)
        }
    }

    /**
     * Fragt den Server (bedingt) nach dem aktuellen Katalog. Ohne [force] wird ein höchstens [MEMO_MS]
     * alter Katalog aus dem Speicher geliefert. Bei Netzfehlern [StoreLoadResult.Failed] – die Oberfläche
     * zeigt dann weiter, was [cached] geliefert hat.
     */
    suspend fun refresh(language: String, force: Boolean = false): StoreLoadResult = withContext(Dispatchers.IO) {
        memo?.takeIf { !force && it.language == language && clock() - it.at < MEMO_MS }?.let {
            return@withContext StoreLoadResult.Online(it.catalog)
        }
        val etag = etagFile.takeIf { it.isFile && cacheFile.isFile }?.let { runCatching { it.readText().trim() }.getOrNull() }
        val response = fetcher.fetchIfChanged(url, etag?.ifBlank { null })
        val body = when (val r = response.getOrNull()) {
            is ConditionalText.Fresh -> r.body.also { saveBody(it, r.etag) }
            ConditionalText.NotModified -> runCatching { cacheFile.readText() }.getOrNull()
            null -> return@withContext StoreLoadResult.Failed(response.exceptionOrNull()?.message ?: "Netzwerkfehler")
        } ?: return@withContext StoreLoadResult.Failed("Zwischenspeicher nicht lesbar")
        val parsed = StoreCatalogParser.parse(body, language)
        parsed.getOrNull()?.let { catalog ->
            memo = Memo(language, catalog, clock())
            return@withContext StoreLoadResult.Online(catalog)
        }
        val err = parsed.exceptionOrNull()
        StoreLoadResult.Failed(err?.message ?: "ungültiger Katalog", err is UnsupportedStoreSchemaException)
    }

    /** Lädt online, sonst Zwischenspeicher bzw. eingebauten Katalog. */
    suspend fun load(language: String, force: Boolean = false): StoreLoadResult {
        val online = refresh(language, force)
        if (online !is StoreLoadResult.Failed || online.unsupportedSchema) return online
        return cached(language) ?: online
    }

    /** Vergisst den Katalog im Speicher (z. B. nach „Neu laden“). */
    fun invalidate() {
        memo = null
    }

    private fun saveBody(body: String, etag: String?) {
        // Nur gültige Kataloge ersetzen die letzte gute Fassung.
        if (StoreCatalogParser.parse(body, "en").isFailure) return
        runCatching { cacheFile.writeText(body) }
        runCatching { if (etag != null) etagFile.writeText(etag) else etagFile.delete() }
    }

    /** Lädt die Statistik (Downloads, Bewertungen); offline die zuletzt geladene, sonst leer. */
    suspend fun loadStats(url: String): StoreStats = withContext(Dispatchers.IO) {
        fetcher.fetch(url).getOrNull()?.let { body ->
            val stats = StoreCatalogParser.parseStats(body)
            if (stats.items.isNotEmpty()) runCatching { statsFile.writeText(body) }
            return@withContext stats
        }
        cachedStats()
    }

    /** Zuletzt geladene Statistik ohne Netz. */
    suspend fun cachedStats(): StoreStats = withContext(Dispatchers.IO) {
        runCatching { statsFile.takeIf { it.isFile }?.readText() }.getOrNull()?.let(StoreCatalogParser::parseStats) ?: StoreStats()
    }

    companion object {
        /** Wiederverwendung eines online geladenen Katalogs im Speicher. */
        const val MEMO_MS = 5L * 60 * 1000
        /** Eingebauter Katalog (vom Gradle-Task `fetchBundledStoreCatalog`). */
        const val BUNDLED_ASSET = "store/store.json"
    }
}

private fun readBundledCatalog(context: Context): String? =
    runCatching { context.assets.open(StoreRepository.BUNDLED_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
