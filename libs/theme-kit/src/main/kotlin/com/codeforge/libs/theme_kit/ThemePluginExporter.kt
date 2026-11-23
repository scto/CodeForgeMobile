// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Verpackt ein Theme als deklaratives Farbschema-Plugin (helle und dunkle Variante, alle 36 Rollen), damit es sich
 * als App-Farbschema installieren lässt.
 */
object ThemePluginExporter {
    private val json = Json { prettyPrint = true }
    private val SLUG = Regex("[^a-z0-9]+")

    /** Plugin-ID aus dem Namen, z. B. „Mein Blau“ → `user.theme.mein-blau`. */
    fun pluginIdFor(name: String): String {
        val slug = name.lowercase().replace(SLUG, "-").trim('-').ifEmpty { "eigenes" }
        return "user.theme.$slug"
    }

    /** Theme-ID der Variante: `user.theme.mein-blau` → `mein-blau-dark` / `mein-blau-light`. */
    fun themeIdFor(pluginId: String, isDark: Boolean): String =
        pluginId.substringAfterLast('.') + if (isDark) "-dark" else "-light"

    /**
     * Schreibt das Plugin-Archiv nach [out] und gibt die Plugin-ID zurück. [version] muss bei erneutem Installieren
     * desselben Namens steigen (Aktualisierung).
     */
    fun export(spec: ThemeSpec, name: String, out: File, version: String = "1.0.0"): String {
        val id = pluginIdFor(name)
        val manifest = buildJsonObject {
            put("id", id)
            put("name", name)
            put("version", version)
            put("apiVersion", 2)
            put("publisher", "theme-builder")
            put("description", "Mit dem Theme-Builder erstellt (Grundfarbe ${spec.seed}, Stil ${spec.style}).")
            put("permissions", JsonArray(emptyList()))
            put("categories", buildJsonArray { add(JsonPrimitive("theme")) })
            put(
                "contributes",
                buildJsonObject {
                    put(
                        "colorThemes",
                        buildJsonArray {
                            add(themeEntry(themeIdFor(id, isDark = true), "$name (dunkel)", "dark"))
                            add(themeEntry(themeIdFor(id, isDark = false), "$name (hell)", "light"))
                        },
                    )
                },
            )
        }
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.entry("plugin.json", json.encodeToString(JsonObject.serializer(), manifest))
            zip.entry("themes/dark.json", colorsJson(spec, isDark = true))
            zip.entry("themes/light.json", colorsJson(spec, isDark = false))
        }
        return id
    }

    private fun themeEntry(id: String, label: String, ui: String) = buildJsonObject {
        put("id", id)
        put("label", label)
        put("uiTheme", ui)
        put("path", "themes/$ui.json")
    }

    private fun colorsJson(spec: ThemeSpec, isDark: Boolean): String {
        val colors = ThemeEngine.roles(spec, isDark).toHexMap()
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject { put("colors", JsonObject(colors.mapValues { JsonPrimitive(it.value) })) },
        )
    }

    private fun ZipOutputStream.entry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }
}
