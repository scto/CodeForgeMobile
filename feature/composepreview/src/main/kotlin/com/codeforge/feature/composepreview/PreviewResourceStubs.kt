/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Ersatz für die `R`-Klassen eines Projekts in der Vorschau.
 *
 * `R` entsteht erst beim Gradle-Build; die Vorschau kompiliert aber Quelltext. Deshalb werden für jede benutzte
 * `R`-Klasse (`import x.y.R` oder `R.` im eigenen Paket) kleine Stub-Klassen erzeugt – nur mit den tatsächlich
 * referenzierten Einträgen. Texte, Pluralformen, Text-Arrays, Farben und Maße kommen aus den `res/values*`-Ordnern
 * aller Module; `stringResource`/`pluralStringResource`/… werden auf Funktionen umgeleitet, die diese Werte liefern
 * (passend zur Sprache der Vorschau). Unbekannte IDs (z. B. `android.R`) gehen an die echten Funktionen.
 */
internal object PreviewResourceStubs {

    /** Paket der Hilfsfunktionen in der Vorschau. */
    const val STUB_PACKAGE = "codeforge.preview.res"

    /** Erste ID der Stubs; liegt bewusst unterhalb des App-Bereichs `0x7f…`. */
    private const val FIRST_ID = 0x7e000000

    private val R_REFERENCE = Regex("""\bR\.(string|plurals|array|color|dimen|drawable|mipmap|font|raw|xml|bool|integer|anim|layout|menu|style|id)\.(\w+)""")
    private val R_IMPORT = Regex("""^[ \t]*import[ \t]+([\w.]+)\.R[ \t]*;?[ \t]*$""", RegexOption.MULTILINE)
    private val PACKAGE = Regex("""^[ \t]*package[ \t]+([\w.]+)""", RegexOption.MULTILINE)
    private val VALUES_DIR = Regex("""values(?:-([a-z]{2,3})(?:-r([A-Z]{2}))?)?""")
    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
    )

    /** Umgeleitete Funktionen: echter Name → Name im [STUB_PACKAGE]. */
    private val REDIRECTED = listOf(
        "androidx.compose.ui.res.stringResource",
        "androidx.compose.ui.res.pluralStringResource",
        "androidx.compose.ui.res.stringArrayResource",
        "androidx.compose.ui.res.colorResource",
        "androidx.compose.ui.res.dimensionResource",
        "androidx.compose.ui.res.painterResource"
    )

    /** Werte einer Sprache (Schlüssel `""` = Standard). */
    internal data class Values(
        val strings: Map<String, String> = emptyMap(),
        val plurals: Map<String, Map<String, String>> = emptyMap(),
        val arrays: Map<String, List<String>> = emptyMap(),
        val colors: Map<String, String> = emptyMap(),
        val dimens: Map<String, String> = emptyMap()
    )

    /** Erzeugte Dateien (Dateiname → Quelltext); leer, wenn keine Datei `R` benutzt. */
    fun generate(projectRoot: File?, sources: List<String>): Map<String, String> {
        val refs = linkedMapOf<String, MutableSet<String>>()
        val rClasses = linkedSetOf<String>()
        for (text in sources) {
            val found = R_REFERENCE.findAll(text).toList()
            if (found.isEmpty()) continue
            found.forEach { refs.getOrPut(it.groupValues[1]) { sortedSetOf() } += it.groupValues[2] }
            val imported = R_IMPORT.findAll(text).map { it.groupValues[1] }.filterNot { it == "android" }.toList()
            rClasses += imported
            if (imported.isEmpty()) PACKAGE.find(text)?.groupValues?.get(1)?.let { rClasses += it }
        }
        if (refs.isEmpty() || rClasses.isEmpty()) return emptyMap()

        val ids = linkedMapOf<String, Int>()
        refs.forEach { (type, names) -> names.forEach { ids["$type.$it"] = FIRST_ID + ids.size } }
        val values = loadValues(projectRoot)

        val files = linkedMapOf<String, String>()
        rClasses.forEachIndexed { i, pkg -> files["PreviewR_$i.kt"] = rClassSource(pkg, refs, ids) }
        files["PreviewResources.kt"] = helperSource(refs, ids, values)
        return files
    }

    /** Leitet die Compose-Ressourcenfunktionen in [text] auf die Stub-Funktionen um. */
    fun redirectImports(text: String): String {
        var result = text
        for (fqn in REDIRECTED) {
            val simple = fqn.substringAfterLast('.')
            val usesFunction = Regex("""\b$simple\s*\(""").containsMatchIn(result)
            val explicit = Regex("""^([ \t]*import[ \t]+)${Regex.escape(fqn)}([ \t]*;?[ \t]*)$""", RegexOption.MULTILINE)
            result = when {
                explicit.containsMatchIn(result) -> explicit.replace(result) { "${it.groupValues[1]}$STUB_PACKAGE.$simple${it.groupValues[2]}" }
                usesFunction && Regex("""^[ \t]*import[ \t]+androidx\.compose\.ui\.res\.\*""", RegexOption.MULTILINE).containsMatchIn(result) ->
                    addImport(result, "$STUB_PACKAGE.$simple")
                else -> result
            }
        }
        return result
    }

    private fun addImport(text: String, fqn: String): String {
        val pkg = PACKAGE.find(text)
        val line = "import $fqn\n"
        return if (pkg == null) line + text else {
            val end = text.indexOf('\n', pkg.range.last).let { if (it < 0) text.length else it + 1 }
            text.substring(0, end) + "\n" + line + text.substring(end)
        }
    }

    // --- Ressourcen lesen ----------------------------------------------------------------------------------------

    /** Liest `res/values*` aller Module (ohne `build`-Ordner). Schlüssel: `""`, `de`, `de-CH`, … */
    internal fun loadValues(projectRoot: File?): Map<String, Values> {
        if (projectRoot == null) return emptyMap()
        val out = linkedMapOf<String, Values>()
        for (res in findResDirs(projectRoot)) {
            val dirs = res.listFiles { f -> f.isDirectory && VALUES_DIR.matches(f.name) }.orEmpty().sortedBy { it.name }
            for (dir in dirs) {
                val m = VALUES_DIR.matchEntire(dir.name) ?: continue
                val locale = listOfNotNull(m.groupValues[1].ifEmpty { null }, m.groupValues[2].ifEmpty { null }).joinToString("-")
                dir.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty().sortedBy { it.name }.forEach { xml ->
                    out[locale] = merge(out[locale] ?: Values(), parseValuesXml(xml))
                }
            }
        }
        return out
    }

    private fun findResDirs(root: File): List<File> {
        val result = mutableListOf<File>()
        fun visit(dir: File, depth: Int) {
            if (depth > 6) return
            File(dir, "src/main/res").takeIf { it.isDirectory }?.let { result += it }
            dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name !in setOf("build", "src", "node_modules", "build-logic", "buildSrc") }
                ?.sortedBy { it.name }
                ?.forEach { visit(it, depth + 1) }
        }
        visit(root, 0)
        return result
    }

    private val xmlCache = HashMap<String, Pair<Long, Values>>()

    /** Liest eine `values`-XML-Datei (mit Zwischenspeicher je Datei). */
    internal fun parseValuesXml(file: File): Values {
        val stamp = file.lastModified() * 31 + file.length()
        synchronized(xmlCache) { xmlCache[file.absolutePath]?.takeIf { it.first == stamp }?.let { return it.second } }
        val parsed = runCatching { parseValuesXml(file.readText()) }.getOrDefault(Values())
        synchronized(xmlCache) { xmlCache[file.absolutePath] = stamp to parsed }
        return parsed
    }

    /** Liest `values`-XML aus Text. */
    internal fun parseValuesXml(xml: String): Values {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val arrays = linkedMapOf<String, List<String>>()
        val colors = linkedMapOf<String, String>()
        val dimens = linkedMapOf<String, String>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val e = children.item(i) as? Element ?: continue
            val name = e.getAttribute("name").replace('.', '_')
            if (name.isEmpty()) continue
            when (e.tagName) {
                "string" -> strings[name] = unescape(e.textContent)
                "plurals" -> plurals[name] = e.elements("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
                "string-array", "array" -> arrays[name] = e.elements("item").map { unescape(it.textContent) }
                "color" -> colors[name] = e.textContent.trim()
                "dimen" -> dimens[name] = e.textContent.trim()
            }
        }
        return Values(strings, plurals, arrays, colors, dimens)
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** Android-Escapes: `\'`, `\"`, `\n`, `\t`, `\@`, `\?` und umschließende Anführungszeichen. */
    internal fun unescape(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> if (i + 5 < s.length) {
                        s.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun merge(a: Values, b: Values) = Values(
        a.strings + b.strings, a.plurals + b.plurals, a.arrays + b.arrays, a.colors + b.colors, a.dimens + b.dimens
    )

    // --- Quelltext erzeugen --------------------------------------------------------------------------------------

    private fun ident(name: String) = if (name in KOTLIN_KEYWORDS) "`$name`" else name

    private fun rClassSource(pkg: String, refs: Map<String, Set<String>>, ids: Map<String, Int>): String = buildString {
        appendLine("@file:Suppress(\"ClassName\", \"unused\")")
        appendLine("package $pkg")
        appendLine()
        appendLine("/** Vorschau-Ersatz für das generierte R (nur benutzte Einträge). */")
        appendLine("object R {")
        refs.forEach { (type, names) ->
            appendLine("    object $type {")
            names.forEach { appendLine("        const val ${ident(it)}: Int = 0x${ids.getValue("$type.$it").toString(16)}") }
            appendLine("    }")
        }
        appendLine("}")
    }

    private fun lit(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun helperSource(refs: Map<String, Set<String>>, ids: Map<String, Int>, values: Map<String, Values>): String = buildString {
        fun idOf(type: String, name: String) = "0x" + ids.getValue("$type.$name").toString(16)
        val locales = values.keys.sortedBy { it.length }
        appendLine("@file:Suppress(\"unused\")")
        appendLine("package $STUB_PACKAGE")
        appendLine()
        appendLine("import androidx.compose.runtime.Composable")
        appendLine("import androidx.compose.ui.graphics.Color")
        appendLine("import androidx.compose.ui.graphics.painter.ColorPainter")
        appendLine("import androidx.compose.ui.graphics.painter.Painter")
        appendLine("import androidx.compose.ui.platform.LocalConfiguration")
        appendLine("import androidx.compose.ui.unit.Dp")
        appendLine("import androidx.compose.ui.unit.dp")
        appendLine()
        appendLine("private const val FIRST = 0x${FIRST_ID.toString(16)}")
        appendLine("private const val LAST = 0x${(FIRST_ID + ids.size).toString(16)}")
        appendLine("private fun isStub(id: Int) = id in FIRST until LAST")
        appendLine()
        // Texte je Sprache
        appendLine("private val STRINGS: Map<String, Map<Int, String>> = mapOf(")
        locales.forEach { loc ->
            val v = values.getValue(loc)
            val entries = refs["string"].orEmpty().mapNotNull { n -> v.strings[n]?.let { "${idOf("string", n)} to ${lit(it)}" } }
            if (entries.isNotEmpty()) appendLine("    ${lit(loc)} to mapOf(${entries.joinToString(", ")}),")
        }
        appendLine(")")
        appendLine("private val PLURALS: Map<String, Map<Int, Map<String, String>>> = mapOf(")
        locales.forEach { loc ->
            val v = values.getValue(loc)
            val entries = refs["plurals"].orEmpty().mapNotNull { n ->
                v.plurals[n]?.let { q -> "${idOf("plurals", n)} to mapOf(${q.entries.joinToString(", ") { "${lit(it.key)} to ${lit(it.value)}" }})" }
            }
            if (entries.isNotEmpty()) appendLine("    ${lit(loc)} to mapOf(${entries.joinToString(", ")}),")
        }
        appendLine(")")
        appendLine("private val ARRAYS: Map<String, Map<Int, Array<String>>> = mapOf(")
        locales.forEach { loc ->
            val v = values.getValue(loc)
            val entries = refs["array"].orEmpty().mapNotNull { n ->
                v.arrays[n]?.let { a -> "${idOf("array", n)} to arrayOf(${a.joinToString(", ") { lit(it) }})" }
            }
            if (entries.isNotEmpty()) appendLine("    ${lit(loc)} to mapOf(${entries.joinToString(", ")}),")
        }
        appendLine(")")
        val defaults = values[""] ?: Values()
        val colorEntries = refs["color"].orEmpty().mapNotNull { n -> (defaults.colors[n] ?: values.values.firstNotNullOfOrNull { it.colors[n] })?.let { n to it } }
        appendLine("private val COLORS: Map<Int, String> = mapOf(${colorEntries.joinToString(", ") { "${idOf("color", it.first)} to ${lit(it.second)}" }})")
        val dimenEntries = refs["dimen"].orEmpty().mapNotNull { n -> (defaults.dimens[n] ?: values.values.firstNotNullOfOrNull { it.dimens[n] })?.let { n to it } }
        appendLine("private val DIMENS: Map<Int, String> = mapOf(${dimenEntries.joinToString(", ") { "${idOf("dimen", it.first)} to ${lit(it.second)}" }})")
        appendLine()
        append(
            """
            @Composable
            private fun localeKeys(): List<String> {
                val locale = LocalConfiguration.current.locales[0]
                return listOf(locale.language + "-" + locale.country, locale.language, "").distinct()
            }

            private fun <V> lookup(table: Map<String, Map<Int, V>>, keys: List<String>, id: Int): V? =
                keys.firstNotNullOfOrNull { table[it]?.get(id) }

            @Composable
            fun stringResource(id: Int): String =
                if (isStub(id)) lookup(STRINGS, localeKeys(), id) ?: "?" else androidx.compose.ui.res.stringResource(id)

            @Composable
            fun stringResource(id: Int, vararg formatArgs: Any): String =
                if (isStub(id)) (lookup(STRINGS, localeKeys(), id) ?: "?").format(*formatArgs)
                else androidx.compose.ui.res.stringResource(id, *formatArgs)

            @Composable
            private fun pluralForm(id: Int, count: Int): String {
                val forms = lookup(PLURALS, localeKeys(), id) ?: return "?"
                val locale = LocalConfiguration.current.locales[0]
                val quantity = runCatching { android.icu.text.PluralRules.forLocale(locale).select(count.toDouble()) }
                    .getOrDefault(if (count == 1) "one" else "other")
                return forms[quantity] ?: forms["other"] ?: forms.values.firstOrNull() ?: "?"
            }

            @Composable
            fun pluralStringResource(id: Int, count: Int): String =
                if (isStub(id)) pluralForm(id, count) else androidx.compose.ui.res.pluralStringResource(id, count)

            @Composable
            fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String =
                if (isStub(id)) pluralForm(id, count).format(*formatArgs)
                else androidx.compose.ui.res.pluralStringResource(id, count, *formatArgs)

            @Composable
            fun stringArrayResource(id: Int): Array<String> =
                if (isStub(id)) lookup(ARRAYS, localeKeys(), id) ?: emptyArray() else androidx.compose.ui.res.stringArrayResource(id)

            @Composable
            fun colorResource(id: Int): Color {
                if (!isStub(id)) return androidx.compose.ui.res.colorResource(id)
                val raw = COLORS[id]?.removePrefix("#") ?: return Color.Magenta
                val argb = when (raw.length) {
                    3 -> "FF" + raw.map { "${'$'}it${'$'}it" }.joinToString("")
                    4 -> raw.map { "${'$'}it${'$'}it" }.joinToString("")
                    6 -> "FF" + raw
                    else -> raw
                }
                return argb.toLongOrNull(16)?.let { Color(it.toInt()) } ?: Color.Magenta
            }

            @Composable
            fun dimensionResource(id: Int): Dp {
                if (!isStub(id)) return androidx.compose.ui.res.dimensionResource(id)
                val raw = DIMENS[id] ?: return 0.dp
                return raw.removeSuffix("dip").removeSuffix("dp").removeSuffix("sp").removeSuffix("px").toFloatOrNull()?.dp ?: 0.dp
            }

            /** Bilder aus `res/drawable` kennt die Vorschau nicht: Platzhalter statt Absturz. */
            @Composable
            fun painterResource(id: Int): Painter =
                if (isStub(id)) ColorPainter(Color(0x33888888)) else androidx.compose.ui.res.painterResource(id)
            """.trimIndent()
        )
        appendLine()
    }
}
