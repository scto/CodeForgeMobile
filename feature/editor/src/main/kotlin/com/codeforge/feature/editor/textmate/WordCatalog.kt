/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Lädt `assets/textmate/keywords.json` (Scope -> Schlüsselwörter) und `assets/textmate/words.json`
 * (englisches Wörterbuch) lazy und liefert Präfix-Treffer für die Auto-Vervollständigung.
 */
package com.codeforge.feature.editor.textmate

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

object WordCatalog {

    private const val MAX_RESULTS = 30
    private const val MIN_WORD_PREFIX = 3

    @Volatile private var keywords: Map<String, Array<String>>? = null
    @Volatile private var words: Array<String>? = null

    /** Scopes, für die das Wörterbuch (Fließtext) statt Schlüsselwörtern verwendet wird. */
    private val proseScopes = setOf("text.plain", "text.html.markdown", "text.tex.latex")

    fun isProse(scope: String): Boolean = scope in proseScopes

    fun keywordsFor(context: Context, scope: String, prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val list = loadKeywords(context)[scope] ?: return emptyList()
        return list.asSequence()
            .filter { it.length > prefix.length && it.startsWith(prefix, ignoreCase = true) }
            .take(MAX_RESULTS).toList()
    }

    fun wordsFor(context: Context, prefix: String): List<String> {
        if (prefix.length < MIN_WORD_PREFIX) return emptyList()
        val all = loadWords(context)
        val p = prefix.lowercase()
        var lo = 0
        var hi = all.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (all[mid] < p) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<String>()
        var i = lo
        while (i < all.size && out.size < MAX_RESULTS && all[i].startsWith(p)) {
            if (all[i].length > p.length) out += matchCase(all[i], prefix)
            i++
        }
        return out
    }

    private fun matchCase(word: String, prefix: String): String =
        if (prefix.first().isUpperCase()) word.replaceFirstChar { it.uppercase() } else word

    @Synchronized
    private fun loadKeywords(context: Context): Map<String, Array<String>> {
        keywords?.let { return it }
        val result = HashMap<String, Array<String>>()
        runCatching {
            context.applicationContext.assets.open("textmate/keywords.json").use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        val scope = r.nextName()
                        val items = LinkedHashSet<String>()
                        r.beginArray()
                        while (r.hasNext()) r.nextString().trim().takeIf { it.isNotEmpty() }?.let(items::add)
                        r.endArray()
                        result[scope] = items.toTypedArray()
                    }
                    r.endObject()
                }
            }
        }.onFailure { android.util.Log.w("WordCatalog", "keywords.json nicht ladbar", it) }
        keywords = result
        return result
    }

    @Synchronized
    private fun loadWords(context: Context): Array<String> {
        words?.let { return it }
        val list = ArrayList<String>(280_000)
        runCatching {
            context.applicationContext.assets.open("textmate/words.json").use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8).buffered()).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        list += r.nextName()
                        r.skipValue()
                    }
                    r.endObject()
                }
            }
        }.onFailure { android.util.Log.w("WordCatalog", "words.json nicht ladbar", it) }
        val sorted = list.toTypedArray().also { it.sort() }
        words = sorted
        return sorted
    }
}
