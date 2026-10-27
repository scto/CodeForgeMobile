/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Inhalt einer Sprachpaket-Datei (`contributes.languagePacks[].path`):
 * `{ "strings": { "<Name der String-Ressource>": "Übersetzter Text mit %1$s wie im Original" } }`.
 * Der Name ist der Ressourcenname aus `core/resources/src/main/res/values/strings.xml`.
 */
object LanguagePackFileParser {
    const val MAX_ENTRIES = 20_000
    private val NAME = Regex("[A-Za-z][A-Za-z0-9_]*")

    fun parse(text: String): Result<Map<String, String>> = runCatching {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("Sprachpaket muss ein JSON-Objekt sein")
        val strings = root["strings"] as? JsonObject
            ?: throw IllegalArgumentException("Sprachpaket: 'strings' fehlt")
        require(strings.size <= MAX_ENTRIES) { "Sprachpaket enthält zu viele Einträge (max. $MAX_ENTRIES)" }
        val out = LinkedHashMap<String, String>(strings.size)
        for ((name, v) in strings) {
            require(NAME.matches(name)) { "Ungültiger Ressourcenname: $name" }
            val value = (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: throw IllegalArgumentException("Text für '$name' muss ein String sein")
            out[name] = value
        }
        require(out.isNotEmpty()) { "Sprachpaket ist leer" }
        out
    }
}

/** Ein verfügbares Sprachpaket eines Plugins; [locale] als BCP-47-Tag (z. B. "fr"). */
data class ExtensionLanguagePack(
    val pluginId: String,
    val locale: String,
    val label: String,
    val strings: Map<String, String>
) {
    val key: String get() = ExtensionColorTheme.keyOf(pluginId, locale)
}
