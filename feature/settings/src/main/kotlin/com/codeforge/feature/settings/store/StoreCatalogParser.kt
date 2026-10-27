// Modul: :feature:settings
package com.codeforge.feature.settings.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Liest `store.json`. Texte dürfen ein String oder ein Objekt `{"de": "...", "en": "..."}` sein.
 * Nur http(s)-Links werden übernommen; Einträge ohne `id`, `category` oder Namen fallen weg.
 */
object StoreCatalogParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
            )
        }
        // Unbekannte Kategorien aus Einträgen werden mit ihrer ID als Titel ergänzt.
        val known = declared.map { it.id }.toSet()
        val extra = items.map { it.categoryId }.distinct().filter { it !in known }.map { StoreCategory(it, it) }
        StoreCatalog(schema, root.str("updated").orEmpty(), declared + extra, items)
    }

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
