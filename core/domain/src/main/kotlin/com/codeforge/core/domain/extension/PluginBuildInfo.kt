/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Herkunftsangabe eines Plugin-Pakets (`build-info.json`, geschrieben von `templates/build.py`,
 * `tool-plugins/generate.py` und den Gradle-Paketaufgaben der Code-Plugins):
 * `{"commit": "a1b2c3d", "dirty": false, "builtAt": "2026-11-20T10:00:00Z", "builder": "gradle", "apiVersion": 4}`.
 */
package com.codeforge.core.domain.extension

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

object PluginBuildInfo {
    const val FILE_NAME = "build-info.json"

    /** Kurzfassung für die Anzeige, z. B. `a1b2c3d · 2026-11-20 · gradle`; leer, wenn die Datei fehlt oder ungültig ist. */
    fun summary(file: File): String {
        if (!file.isFile || file.length() > MAX_SIZE) return ""
        val obj = runCatching { Json.parseToJsonElement(file.readText()) as JsonObject }.getOrNull() ?: return ""
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val commit = str("commit")?.take(12)?.filter { it.isLetterOrDigit() }
        val dirty = (obj["dirty"] as? JsonPrimitive)?.booleanOrNull == true
        val date = str("builtAt")?.take(10)?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
        val builder = str("builder")?.take(20)?.filter { it.isLetterOrDigit() || it == '-' || it == '.' }
        return listOfNotNull(commit?.let { if (dirty) "$it+dirty" else it }, date, builder).joinToString(" · ")
    }

    private const val MAX_SIZE = 4096L
}
