// Modul: :feature:settings
package com.codeforge.feature.settings.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Liest `store.json`. Texte dürfen ein String oder ein Objekt `{"de": "...", "en": "..."}` sein.
 * Nur http(s)-Links werden übernommen; Einträge ohne `id`, `category` oder Namen fallen weg.
 */
object StoreCatalogParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Liest den Katalog aus JSON; wählt Texte in [language]. Fehler bei nicht unterstütztem Schema. */
    fun parse(body: String, language: String): Result<StoreCatalog> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val schema = (root["schema"] as? JsonPrimitive)?.intOrNull ?: 1
        if (schema > StoreConfig.SUPPORTED_SCHEMA) throw UnsupportedStoreSchemaException(schema)

        val declared = (root["categories"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            StoreCategory(id, o.text("title", language) ?: id)
        }
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val cat = o.str("category") ?: return@mapNotNull null
            val name = o.text("name", language) ?: return@mapNotNull null
            StoreItem(
                id = id, categoryId = cat, name = name,
                summary = o.text("summary", language).orEmpty(),
                version = o.str("version").orEmpty(),
                author = o.str("author").orEmpty(),
                homepageUrl = o.str("homepage")?.takeIf(::isWebUrl),
                downloadUrl = o.str("downloadUrl")?.takeIf(::isWebUrl),
                sha256 = o.str("sha256")?.takeIf { it.length == 64 && it.all { c -> c.isLetterOrDigit() } },
                categories = o.strings("categories").filter { CATEGORY.matches(it) },
                tags = o.strings("tags").map { it.lowercase() }.take(MAX_TAGS),
                size = (o["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                published = o.str("published").orEmpty(),
                updated = o.str("updated").orEmpty(),
                permissions = o.strings("permissions"),
                mirrorUrl = o.str("mirrorUrl")?.takeIf { it.startsWith("https://") },
            )
        }
        val tree = (root["tree"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.takeIf { CATEGORY.matches(it) } ?: return@mapNotNull null
            StoreCategory(id, o.text("title", language) ?: id.substringAfterLast('/'))
        }
        // Unbekannte Kategorien aus Einträgen werden mit ihrer ID als Titel ergänzt.
        val known = declared.map { it.id }.toSet()
        val extra = items.map { it.categoryId }.distinct().filter { it !in known }.map { StoreCategory(it, it) }
        StoreCatalog(
            schema, root.str("updated").orEmpty(), declared + extra, items, tree,
            statsUrl = root.str("statsUrl")?.takeIf { it.startsWith("https://") } ?: StoreConfig.STATS_URL,
            ratingUrl = root.str("ratingUrl")?.takeIf { it.startsWith("https://") } ?: StoreConfig.RATING_URL,
        )
    }

    /** Liest `stats.json`; Fehler ergeben eine leere Statistik (der Store funktioniert auch ohne). */
    fun parseStats(body: String): StoreStats = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val items = (root["items"] as? JsonObject).orEmptyMap().mapNotNull { (id, el) ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val reviews = (o["reviews"] as? JsonArray).orEmpty().mapNotNull { r ->
                val ro = r as? JsonObject ?: return@mapNotNull null
                val stars = (ro["stars"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..5 } ?: return@mapNotNull null
                StoreReview(ro.str("user").orEmpty().take(40), stars, ro.str("text").orEmpty().take(600), ro.str("date").orEmpty().take(10),
                    ro.str("url")?.takeIf { it.startsWith("https://") })
            }
            id to StoreItemStats(
                downloads = (o["downloads"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0,
                rating = (o["rating"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.0, 5.0) ?: 0.0,
                ratings = (o["ratings"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0) ?: 0,
                stars = (o["stars"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }.take(5),
                reviews = reviews.take(5),
            )
        }.toMap()
        StoreStats(root.str("updated").orEmpty(), items)
    }.getOrDefault(StoreStats())

    private val CATEGORY = Regex("[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*){0,5}")
    private const val MAX_TAGS = 20

    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() && s.length <= 60 } }

    private fun JsonObject?.orEmptyMap(): Map<String, JsonElement> = this ?: emptyMap()

    private fun isWebUrl(u: String) = u.startsWith("https://") || u.startsWith("http://")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.text(key: String, language: String): String? = when (val el: JsonElement? = this[key]) {
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> (el[language] ?: el["en"] ?: el.values.firstOrNull())
            .let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.jsonArray ?: emptyList()
}
