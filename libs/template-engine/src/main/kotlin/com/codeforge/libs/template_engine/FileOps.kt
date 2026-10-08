// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import java.io.File

internal fun writeText(file: File, content: String) {
    file.parentFile?.mkdirs()
    file.writeText(content)
}

internal fun copyAsset(assets: AssetSource, assetPath: String, dst: File) {
    dst.parentFile?.mkdirs()
    assets.open(assetPath).use { input -> dst.outputStream().use { input.copyTo(it) } }
}

/** Kopiert den Asset-Ordner [assetRoot] rekursiv nach [outRoot] (relative Struktur ab [assetRoot]). */
internal fun copyAssetsDir(assets: AssetSource, assetRoot: String, outRoot: File) {
    fun copyDir(path: String) {
        for (name in assets.list(path)) {
            val child = "$path/$name"
            if (assets.list(child).isNotEmpty()) {
                copyDir(child)
            } else {
                copyAsset(assets, child, File(outRoot, child.removePrefix("$assetRoot/")))
            }
        }
    }
    copyDir(assetRoot)
}

/**
 * Hängt Strings aus [additionalStringsXml], deren `name` in `values/strings.xml` noch fehlt, vor
 * `</resources>`. Legt die Datei bei Bedarf an.
 */
internal fun mergeStringsXml(valuesDir: File, additionalStringsXml: String) {
    val stringsFile = File(valuesDir, "strings.xml")
    if (!stringsFile.exists()) {
        writeText(stringsFile, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n")
    }
    val nameRegex = Regex("""<string\s+name="([^"]+)"""")
    val base = stringsFile.readText()
    val existing = nameRegex.findAll(base).map { it.groupValues[1] }.toSet()
    val additions = Regex("""<string\s+name="([^"]+)"[^>]*>.*?</string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(additionalStringsXml)
        .map { it.value.trim() }
        .filter { node -> nameRegex.find(node)?.groupValues?.get(1)?.let { it !in existing } == true }
        .toList()
    if (additions.isEmpty()) return
    val insertion = additions.joinToString("\n") { "    $it" } + "\n"
    stringsFile.writeText(
        if (base.contains("</resources>")) base.replace("</resources>", insertion + "</resources>")
        else base + "\n" + insertion
    )
}

internal fun buildStringsXml(appName: String): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <string name="app_name">${appName.escapeXml()}</string>
    </resources>
""".trimIndent() + "\n"

internal fun String.escapeXml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "\\'")
