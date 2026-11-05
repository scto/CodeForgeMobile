/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Schneidet fremde Werkzeug-Schemas auf die Teilmenge zurecht, die alle Anbieter annehmen (Anthropic, OpenAI, Gemini):
 * `$ref` wird eingesetzt, Typlisten und `anyOf`/`oneOf` auf eine Variante reduziert, unbekannte Schlüsselwörter entfallen.
 */
object McpSchemas {
    private val keep = setOf("type", "description", "properties", "required", "items", "enum")
    private const val MAX_DEPTH = 12

    /** Normalisiertes Schema; wird immer ein `type: object` mit `properties`. */
    fun normalize(schema: JsonObject): JsonObject {
        val defs = ((schema["\$defs"] ?: schema["definitions"]) as? JsonObject) ?: JsonObject(emptyMap())
        val out = node(schema, defs, 0) as? JsonObject ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("type", "object")
            out.forEach { (k, v) -> if (k != "type") put(k, v) }
            if (!out.containsKey("properties")) put("properties", JsonObject(emptyMap()))
        }
    }

    private fun node(el: JsonElement, defs: JsonObject, depth: Int): JsonElement {
        val obj = el as? JsonObject ?: return el
        if (depth > MAX_DEPTH) return buildJsonObject { put("type", "string") }
        resolveRef(obj, defs)?.let { return node(it, defs, depth + 1) }
        (obj["anyOf"] ?: obj["oneOf"])?.let { variants ->
            val pick = (variants as? JsonArray)?.firstOrNull { v -> (v as? JsonObject)?.get("type")?.let { it is JsonPrimitive && it.contentOrNull == "null" } != true }
            if (pick != null) {
                val merged = JsonObject(obj.filterKeys { it != "anyOf" && it != "oneOf" } + ((pick as? JsonObject).orEmpty()))
                return node(merged, defs, depth + 1)
            }
        }
        return buildJsonObject {
            for ((k, v) in obj) {
                if (k !in keep) continue
                when (k) {
                    "type" -> put(k, singleType(v))
                    "properties" -> put(k, buildJsonObject { (v as? JsonObject)?.forEach { (name, sub) -> put(name, node(sub, defs, depth + 1)) } })
                    "items" -> put(k, node(v, defs, depth + 1))
                    "required" -> if (v is JsonArray) put(k, JsonArray(v.filter { it is JsonPrimitive && it.isString }))
                    "enum" -> if (v is JsonArray) put(k, JsonArray(v.filter { it !is JsonNull }))
                    else -> put(k, v)
                }
            }
            // Ein Feld ohne Typ ist für strenge Anbieter ungültig; als Text behandeln, wenn nichts Strukturelles dasteht.
            if (!obj.containsKey("type") && !obj.containsKey("properties") && !obj.containsKey("items")) put("type", "string")
        }
    }

    private fun resolveRef(obj: JsonObject, defs: JsonObject): JsonObject? {
        val ref = (obj["\$ref"] as? JsonPrimitive)?.contentOrNull ?: return null
        return defs[ref.substringAfterLast('/')] as? JsonObject
    }

    private fun singleType(v: JsonElement): JsonElement = when (v) {
        is JsonArray -> v.firstOrNull { it is JsonPrimitive && it.contentOrNull != "null" } ?: JsonPrimitive("string")
        else -> v
    }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}
