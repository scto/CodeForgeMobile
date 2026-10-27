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
 * Inhalt einer Icon-Theme-Datei (`contributes.iconThemes[].path`). Alle Werte sind Bilddateien
 * (PNG/WebP/JPEG) relativ zur Theme-Datei:
 * ```
 * { "file": "file.png", "folder": "folder.png", "folderOpen": "folder-open.png",
 *   "fileExtensions": { "kt": "kotlin.png" }, "fileNames": { "build.gradle.kts": "gradle.png" },
 *   "folderNames": { "src": "folder-src.png" }, "folderNamesOpen": { "src": "folder-src-open.png" } }
 * ```
 * Auflösungsreihenfolge Datei: exakter Dateiname → längste passende Endung (`tar.gz` vor `gz`) → `file`.
 * Ordner: Name (offen/zu) → `folderOpen`/`folder`. Die Suche ist case-insensitiv.
 */
data class IconThemeFile(
    val file: String? = null,
    val folder: String? = null,
    val folderOpen: String? = null,
    val fileExtensions: Map<String, String> = emptyMap(),
    val fileNames: Map<String, String> = emptyMap(),
    val folderNames: Map<String, String> = emptyMap(),
    val folderNamesOpen: Map<String, String> = emptyMap()
) {
    /** Relativer Pfad des Bildes für eine Datei oder null (→ App-Standardicon). */
    fun forFile(name: String): String? {
        val lower = name.lowercase()
        fileNames[lower]?.let { return it }
        var idx = lower.indexOf('.')
        while (idx >= 0 && idx < lower.length - 1) {
            fileExtensions[lower.substring(idx + 1)]?.let { return it }
            idx = lower.indexOf('.', idx + 1)
        }
        return file
    }

    fun forFolder(name: String, open: Boolean): String? {
        val lower = name.lowercase()
        if (open) folderNamesOpen[lower]?.let { return it }
        folderNames[lower]?.let { return it }
        return if (open) folderOpen ?: folder else folder
    }

    /** Alle referenzierten Bilddateien (zur Validierung). */
    fun allPaths(): List<String> =
        listOfNotNull(file, folder, folderOpen) + fileExtensions.values + fileNames.values +
            folderNames.values + folderNamesOpen.values
}

object IconThemeFileParser {
    private val ALLOWED = setOf("png", "webp", "jpg", "jpeg")

    private fun safeRel(p: String): Boolean =
        p.isNotBlank() && !p.startsWith("/") && !p.startsWith("\\") && p.split('/', '\\').none { it == ".." } &&
            p.substringAfterLast('.', "").lowercase() in ALLOWED

    fun parse(text: String): Result<IconThemeFile> = runCatching {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("Icon-Theme muss ein JSON-Objekt sein")

        fun str(key: String): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun map(key: String): Map<String, String> =
            (root[key] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { k.lowercase().removePrefix(".") to it }
            }?.toMap().orEmpty()

        val parsed = IconThemeFile(
            file = str("file"),
            folder = str("folder"),
            folderOpen = str("folderOpen"),
            fileExtensions = map("fileExtensions"),
            fileNames = map("fileNames"),
            folderNames = map("folderNames"),
            folderNamesOpen = map("folderNamesOpen")
        )
        val paths = parsed.allPaths()
        require(paths.isNotEmpty()) { "Icon-Theme enthält keine Icons" }
        paths.firstOrNull { !safeRel(it) }?.let {
            throw IllegalArgumentException("Ungültiger Icon-Pfad '$it' (relativ, ohne '..', nur png/webp/jpg)")
        }
        parsed
    }
}

/** Ein verfügbares Plugin-Icon-Theme. [baseDir] ist das Verzeichnis der Theme-Datei, auf das die Pfade zeigen. */
data class ExtensionIconTheme(
    val pluginId: String,
    val themeId: String,
    val label: String,
    val baseDir: String,
    val icons: IconThemeFile
) {
    val key: String get() = ExtensionColorTheme.keyOf(pluginId, themeId)
}
