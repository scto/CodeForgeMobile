// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import org.json.JSONObject

/**
 * Ein Eintrag im manifest.json eines Templates (assets/templates/<id>/manifest.json).
 *
 * - [source]: relativer Pfad der .ftl-Datei innerhalb des Template-Ordners.
 * - [target]: relativer Zielpfad im generierten Projekt. Darf ${key}-Platzhalter
 *   enthalten (z.B. "${packagePath}"), die aus dem Rendering-Model aufgelöst werden.
 * - [conditionParam]/[conditionEquals]: wenn gesetzt, wird die Datei nur erzeugt,
 *   falls params[conditionParam] == conditionEquals (Default-Vergleichswert: "true").
 *   Ermöglicht z.B. ein optionales Test-Modul (includeTests).
 */
data class TemplateFileEntry(
    val source: String,
    val target: String,
    val conditionParam: String? = null,
    val conditionEquals: String = "true"
) {
    fun isIncluded(params: Map<String, String>): Boolean {
        if (conditionParam == null) return true
        return params[conditionParam] == conditionEquals
    }
}

data class TemplateManifest(val files: List<TemplateFileEntry>) {
    companion object {
        fun parse(json: String): TemplateManifest {
            val root = JSONObject(json)
            val filesArray = root.getJSONArray("files")
            val entries = (0 until filesArray.length()).map { index ->
                val entry = filesArray.getJSONObject(index)
                TemplateFileEntry(
                    source = entry.getString("source"),
                    target = entry.getString("target"),
                    conditionParam = if (entry.has("conditionParam")) entry.getString("conditionParam") else null,
                    conditionEquals = if (entry.has("conditionEquals")) entry.getString("conditionEquals") else "true"
                )
            }
            return TemplateManifest(entries)
        }
    }
}
