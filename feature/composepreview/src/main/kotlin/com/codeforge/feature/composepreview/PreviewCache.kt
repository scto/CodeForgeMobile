/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * Zwischenspeicher der Vorschau-Pipeline:
 *  - [PreviewBuildCache]: kompiliertes `classes.dex` je Quellstand (Datei + Nachbardateien + Laufzeit) auf der Platte.
 *    Funktions- oder Hell/Dunkel-Wechsel braucht dadurch keinen neuen Kotlin-Compiler-/d8-Lauf.
 *  - [PreviewResultCache]: fertige Bilder im Speicher (LRU).
 */
package com.codeforge.feature.composepreview

import java.io.File
import java.security.MessageDigest
import java.util.Properties

/** Schlüssel für den Zwischenspeicher. */
internal object PreviewCacheKey {
    /** Bei Änderungen an Quell-Aufbereitung/Compiler-Optionen erhöhen, damit alte Builds verworfen werden. */
    const val PIPELINE_VERSION = "1"

    /** Bildet den Schlüssel aus den Teilen (Hash). */
    fun of(vararg parts: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(PIPELINE_VERSION.toByteArray())
        for (p in parts) {
            md.update(0)
            md.update(p.toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}

/** Zwischengespeicherter Build. */
internal data class CachedBuild(
    val dexFile: File,
    val targetClassName: String,
    val compiledFiles: List<String>,
    val isFallbackTheme: Boolean,
    val fallbackReason: String?
)

/** Plattencache für Builds (LRU, höchstens [maxEntries]). */
internal class PreviewBuildCache(private val root: File, private val maxEntries: Int = 20) {

    /** Sucht den Build zum Schlüssel. */
    fun lookup(key: String): CachedBuild? {
        val dir = File(root, key)
        val dex = File(dir, "classes.dex")
        val meta = File(dir, "meta.properties")
        if (!dex.isFile || !meta.isFile) return null
        return runCatching {
            val p = Properties().apply { meta.inputStream().use { load(it) } }
            dir.setLastModified(System.currentTimeMillis())
            CachedBuild(
                dexFile = dex,
                targetClassName = p.getProperty("class").orEmpty(),
                compiledFiles = p.getProperty("files").orEmpty().split('\n').filter { it.isNotEmpty() },
                isFallbackTheme = p.getProperty("fallback") == "true",
                fallbackReason = p.getProperty("reason")?.takeIf { it.isNotEmpty() }
            )
        }.getOrNull()
    }

    /** Kopiert [dexFile] in den Cache und liefert den Cache-Eintrag; Fehler beim Schreiben ergeben `null` (Build bleibt nutzbar). */
    fun store(
        key: String,
        dexFile: File,
        targetClassName: String,
        compiledFiles: List<String>,
        isFallbackTheme: Boolean,
        fallbackReason: String?
    ): CachedBuild? = runCatching {
        val dir = File(root, key).apply { mkdirs() }
        val dex = File(dir, "classes.dex")
        dexFile.copyTo(dex, overwrite = true)
        dex.setReadOnly() // Android 14+ verlangt für DexClassLoader schreibgeschützte Dateien
        val p = Properties().apply {
            setProperty("class", targetClassName)
            setProperty("files", compiledFiles.joinToString("\n"))
            setProperty("fallback", isFallbackTheme.toString())
            setProperty("reason", fallbackReason.orEmpty())
        }
        File(dir, "meta.properties").outputStream().use { p.store(it, null) }
        prune()
        CachedBuild(dex, targetClassName, compiledFiles, isFallbackTheme, fallbackReason)
    }.getOrNull()

    /** Leert den Cache. */
    fun clear() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun prune() {
        val dirs = root.listFiles { f -> f.isDirectory }?.sortedByDescending { it.lastModified() }.orEmpty()
        dirs.drop(maxEntries).forEach { it.deleteRecursively() }
    }
}

/** Speichercache für Ergebnisse (LRU). */
internal class PreviewResultCache<V>(private val maxEntries: Int = 24) {
    private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > maxEntries
    }

    @Synchronized fun get(key: String): V? = map[key]

    @Synchronized fun put(key: String, value: V) { map[key] = value }

    @Synchronized fun clear() = map.clear()
}
