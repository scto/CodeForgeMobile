/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reines Kotlin-Repository für Android-Ressourcen:
 * - Ermittelt res/-Ordner aus dem Dateipfad
 * - Parst Werte aus values/ und qualifizierten Ordnern
 * - Qualifizierer-Matching nach Android-Regeln
 * - Verweisketten-Auflösung mit Zyklenerkennung
 * - Eingebaute Theme-Tabelle für ?attr/...
 * - Format-erhaltendes Auslagern („In Ressource auslagern“)
 */
package com.codeforge.feature.layoutdesigner.res

import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ResourceRepository(
    private val resDirs: List<File>,
) {
    private val entriesByType = mutableMapOf<ResType, MutableList<ResourceEntry>>()
    private val styles = mutableMapOf<String, StyleDef>()

    init {
        loadAll()
    }

    private fun loadAll() {
        entriesByType.clear()
        styles.clear()

        for (resDir in resDirs) {
            if (!resDir.exists() || !resDir.isDirectory) continue
            val subDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") } ?: continue
            for (dir in subDirs) {
                val qualifier = dir.name.removePrefix("values").removePrefix("-")
                val xmlFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") } ?: continue
                for (file in xmlFiles) {
                    parseValuesXml(file, qualifier)
                }
            }
        }
    }

    private fun parseValuesXml(file: File, qualifier: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Android-DOM-Parser (Harmony) kennt dieses Feature nicht und wirft ParserConfigurationException.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = try {
            factory.newDocumentBuilder().parse(file)
        } catch (_: Exception) {
            return
        }

        val root = doc.documentElement ?: return
        val children = root.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val elem = n as Element
            val tagName = elem.tagName
            val name = elem.getAttribute("name")
            if (name.isEmpty()) continue

            when (tagName) {
                "string" -> addEntry(ResType.STRING, name, elem.textContent.trim(), qualifier, file.absolutePath)
                "color" -> addEntry(ResType.COLOR, name, elem.textContent.trim(), qualifier, file.absolutePath)
                "dimen" -> addEntry(ResType.DIMEN, name, elem.textContent.trim(), qualifier, file.absolutePath)
                "integer" -> addEntry(ResType.INTEGER, name, elem.textContent.trim(), qualifier, file.absolutePath)
                "bool" -> addEntry(ResType.BOOL, name, elem.textContent.trim(), qualifier, file.absolutePath)
                "plurals" -> {
                    // Form "other" bevorzugt
                    val itemNodes = elem.getElementsByTagName("item")
                    var otherVal: String? = null
                    for (j in 0 until itemNodes.length) {
                        val itm = itemNodes.item(j) as? Element ?: continue
                        if (itm.getAttribute("quantity") == "other") {
                            otherVal = itm.textContent.trim()
                            break
                        }
                    }
                    if (otherVal != null) {
                        addEntry(ResType.STRING, name, otherVal, qualifier, file.absolutePath)
                    }
                }
                "string-array" -> {
                    val itemNodes = elem.getElementsByTagName("item")
                    if (itemNodes.length > 0) {
                        val firstVal = itemNodes.item(0).textContent.trim()
                        addEntry(ResType.ARRAY, name, firstVal, qualifier, file.absolutePath)
                    }
                }
                "style" -> {
                    val parent = elem.getAttribute("parent").ifEmpty {
                        if (name.contains('.')) name.substringBeforeLast('.') else null
                    }
                    val items = mutableMapOf<String, String>()
                    val itemNodes = elem.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val itm = itemNodes.item(j) as? Element ?: continue
                        val itemKey = itm.getAttribute("name")
                        if (itemKey.isNotEmpty()) {
                            items[itemKey] = itm.textContent.trim()
                        }
                    }
                    styles[name] = StyleDef(name, parent, items)
                }
            }
        }
    }

    private fun addEntry(type: ResType, name: String, value: String, qualifier: String, sourceFile: String) {
        entriesByType.getOrPut(type) { mutableListOf() }
            .add(ResourceEntry(type, name, value, qualifier, sourceFile))
    }

    /** Löst eine Zeichenkette (`@string/name` oder Direkttyp) auf */
    fun resolveString(raw: String?, config: ResourceConfig = ResourceConfig()): String? {
        if (raw == null) return null
        if (!raw.startsWith("@string/")) return raw
        val name = raw.removePrefix("@string/")
        return resolveChain(ResType.STRING, name, config)
    }

    /** Löst eine Farbe (`@color/name`, `#RGB` oder `?attr/...`) auf */
    fun resolveColor(raw: String?, config: ResourceConfig = ResourceConfig()): Long? {
        if (raw == null) return null
        if (raw.startsWith("#")) return parseColorArgb(raw)
        if (raw.startsWith("?")) return resolveThemeColor(raw, config.isNight)
        if (raw.startsWith("@color/")) {
            val name = raw.removePrefix("@color/")
            val resolvedVal = resolveChain(ResType.COLOR, name, config) ?: return null
            return parseColorArgb(resolvedVal)
        }
        return null
    }

    /** Löst eine Dimension (`@dimen/name` oder `16dp`) auf */
    fun resolveDimension(raw: String?, config: ResourceConfig = ResourceConfig()): Float? {
        if (raw == null) return null
        if (raw.startsWith("@dimen/")) {
            val name = raw.removePrefix("@dimen/")
            val resolvedVal = resolveChain(ResType.DIMEN, name, config) ?: return null
            return parseDimension(resolvedVal)
        }
        return parseDimension(raw)
    }

    /** Prüft, ob eine Ressource existiert */
    fun hasResource(type: String, name: String): Boolean {
        return when (type) {
            "string" -> entriesByType[ResType.STRING]?.any { it.name == name } == true
            "color" -> entriesByType[ResType.COLOR]?.any { it.name == name } == true
            "dimen" -> entriesByType[ResType.DIMEN]?.any { it.name == name } == true
            "integer" -> entriesByType[ResType.INTEGER]?.any { it.name == name } == true
            "bool" -> entriesByType[ResType.BOOL]?.any { it.name == name } == true
            "style" -> styles.containsKey(name)
            "drawable", "mipmap" -> {
                for (resDir in resDirs) {
                    val subDirs = resDir.listFiles { f -> f.isDirectory && (f.name.startsWith("drawable") || f.name.startsWith("mipmap")) } ?: continue
                    for (dir in subDirs) {
                        val matches = dir.listFiles { f -> f.isFile && f.name.substringBeforeLast('.') == name }
                        if (!matches.isNullOrEmpty()) return true
                    }
                }
                false
            }
            else -> true
        }
    }

    /** Löst ein Attribut aus einem Style auf (inkl. Vererbung) */
    fun resolveStyleAttr(styleName: String, attrName: String, depth: Int = 0): String? {
        if (depth > 10) return null
        val cleanName = styleName.removePrefix("@style/")
        val def = styles[cleanName] ?: return null
        def.items[attrName]?.let { return it }
        def.items["android:$attrName"]?.let { return it }
        val parent = def.parent ?: return null
        return resolveStyleAttr(parent, attrName, depth + 1)
    }

    /** Eingebaute Material-3 Theme-Tabelle */
    fun resolveThemeColor(attrRef: String, isDark: Boolean): Long? {
        val attr = attrRef.removePrefix("?android:attr/").removePrefix("?android:").removePrefix("?attr/").removePrefix("?")
        return if (isDark) {
            when (attr) {
                "colorPrimary" -> 0xFFD0BCFFL
                "colorOnPrimary" -> 0xFF381E72L
                "colorSecondary" -> 0xFFCCC2DCL
                "colorSurface" -> 0xFF141218L
                "colorOnSurface" -> 0xFFE6E0E9L
                "colorError" -> 0xFFF2B8B5L
                "textColorPrimary" -> 0xFFE6E0E9L
                "colorControlNormal" -> 0xFFCAC4D0L
                "selectableItemBackground" -> 0x00000000L
                else -> null
            }
        } else {
            when (attr) {
                "colorPrimary" -> 0xFF6750A4L
                "colorOnPrimary" -> 0xFFFFFFFFL
                "colorSecondary" -> 0xFF625B71L
                "colorSurface" -> 0xFFFEF7FFL
                "colorOnSurface" -> 0xFF1D1B20L
                "colorError" -> 0xFFB3261EL
                "textColorPrimary" -> 0xFF1D1B20L
                "colorControlNormal" -> 0xFF49454FL
                "selectableItemBackground" -> 0x00000000L
                else -> null
            }
        }
    }

    private fun resolveChain(type: ResType, initialName: String, config: ResourceConfig): String? {
        var currName = initialName
        var currType = type
        val visited = mutableSetOf<String>()

        for (step in 0 until 10) {
            val key = "${currType.name}/$currName"
            if (key in visited) return null // Zyklus
            visited.add(key)

            val entry = getBestMatchingEntry(currType, currName, config) ?: return null
            val value = entry.value

            when {
                value.startsWith("@string/") -> {
                    currName = value.removePrefix("@string/")
                    currType = ResType.STRING
                }
                value.startsWith("@color/") -> {
                    currName = value.removePrefix("@color/")
                    currType = ResType.COLOR
                }
                value.startsWith("@dimen/") -> {
                    currName = value.removePrefix("@dimen/")
                    currType = ResType.DIMEN
                }
                else -> return value
            }
        }
        return null
    }

    private fun getBestMatchingEntry(type: ResType, name: String, config: ResourceConfig): ResourceEntry? {
        val candidates = entriesByType[type]?.filter { it.name == name } ?: return null
        if (candidates.isEmpty()) return null

        var bestEntry: ResourceEntry? = null
        var bestScore = -1

        for (cand in candidates) {
            val score = scoreQualifier(cand.qualifier, config)
            if (score > bestScore) {
                bestScore = score
                bestEntry = cand
            }
        }

        return bestEntry
    }

    private fun scoreQualifier(qualifier: String, config: ResourceConfig): Int {
        if (qualifier.isEmpty()) return 0 // Default

        val tokens = qualifier.split('-').map { it.trim() }
        var score = 0

        for (t in tokens) {
            when {
                t == "night" -> if (config.isNight) score += 20 else return -1
                t == "notnight" -> if (!config.isNight) score += 20 else return -1
                t == "land" -> if (config.isLandscape) score += 10 else return -1
                t == "port" -> if (!config.isLandscape) score += 10 else return -1
                t == config.language || t.startsWith("${config.language}_") || t.startsWith("${config.language}-") -> score += 50
                t.startsWith("sw") && t.endsWith("dp") -> {
                    val req = t.removePrefix("sw").removeSuffix("dp").toIntOrNull() ?: continue
                    if (config.swDp >= req) score += 30 + req else return -1
                }
                t.startsWith("v") -> {
                    val req = t.removePrefix("v").toIntOrNull() ?: continue
                    if (config.apiLevel >= req) score += 5 + req else return -1
                }
            }
        }

        return score
    }

    /**
     * Schreibt eine neue Ressource in die passende Datei (z. B. values/strings.xml)
     */
    fun extractToResource(
        targetResDir: File,
        type: ResType,
        name: String,
        value: String,
        qualifier: String = "",
    ): Boolean {
        return runCatching {
            val dirName = if (qualifier.isEmpty()) "values" else "values-$qualifier"
            val valuesDir = File(targetResDir, dirName)
            if (!valuesDir.exists()) valuesDir.mkdirs()

            val fileName = when (type) {
                ResType.STRING -> "strings.xml"
                ResType.COLOR -> "colors.xml"
                ResType.DIMEN -> "dimens.xml"
                ResType.INTEGER, ResType.BOOL -> "integers.xml"
                else -> "values.xml"
            }
            val file = File(valuesDir, fileName)

            val xmlSnippet = when (type) {
                ResType.STRING -> "    <string name=\"$name\">$value</string>\n"
                ResType.COLOR -> "    <color name=\"$name\">$value</color>\n"
                ResType.DIMEN -> "    <dimen name=\"$name\">$value</dimen>\n"
                ResType.INTEGER -> "    <integer name=\"$name\">$value</integer>\n"
                ResType.BOOL -> "    <bool name=\"$name\">$value</bool>\n"
                else -> "    <item name=\"$name\">$value</item>\n"
            }

            if (!file.exists()) {
                val content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n$xmlSnippet</resources>\n"
                file.writeText(content)
            } else {
                val text = file.readText()
                if (text.contains("name=\"$name\"")) {
                    // Ersetzen
                    val regex = Regex("<${type.name.lowercase()}\\s+name=\"$name\"[^>]*>.*?</${type.name.lowercase()}>")
                    val newElem = xmlSnippet.trim()
                    val updated = regex.replace(text, newElem)
                    file.writeText(updated)
                } else {
                    // Vor </resources> einfügen
                    val updated = text.replace("</resources>", "$xmlSnippet</resources>")
                    file.writeText(updated)
                }
            }

            // Neu laden
            loadAll()
            true
        }.getOrDefault(false)
    }

    companion object {
        /** Findet alle res/-Ordner für die gegebene Layout-Datei */
        fun findResDirsForLayout(layoutFile: File): List<File> {
            val list = mutableListOf<File>()
            var curr: File? = layoutFile.parentFile
            while (curr != null && curr.name != "src") {
                if (curr.name == "res") {
                    list.add(curr)
                }
                curr = curr.parentFile
            }
            if (curr != null && curr.name == "src") {
                // Finde alle res/-Ordner unter src/*
                curr.listFiles { f -> f.isDirectory }?.forEach { sourceSet ->
                    val r = File(sourceSet, "res")
                    if (r.exists() && r !in list) list.add(r)
                }
            }
            return list
        }
    }
}
