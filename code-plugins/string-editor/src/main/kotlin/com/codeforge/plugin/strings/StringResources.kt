/**
 * Plugin: String-Editor – Modell, Parser und Schreiber für Android-String-Ressourcen (reines Kotlin, testbar).
 *
 * Portiert und erweitert aus StringEditor von raredeveloperofc/msmt2018 (GPL-3.0). Anders als das Original geht beim
 * Speichern nichts verloren: Kommentare, `string-array`, `plurals` und unbekannte Elemente bleiben unverändert stehen,
 * ebenso die Formatierung unveränderter Einträge (kleine Git-Diffs).
 */
package com.codeforge.plugin.strings

/** Ein Element unter `<resources>`. [leading] ist der Text davor (Einrückung, Leerzeilen) und bleibt erhalten. */
sealed interface ResEntry {
    val leading: String

    /** Name (`name`-Attribut) oder `null` bei Kommentaren und Unbekanntem. */
    val name: String?
}

/**
 * `<string>`. [raw] ist der Inhalt wie in der Datei (XML- und Android-maskiert), [attrs] alle Attribute außer `name`
 * in Dateireihenfolge. [source] ist der unveränderte Originaltext oder `null`, sobald der Eintrag geändert wurde.
 */
data class StringEntry(
    override val name: String,
    val raw: String,
    val attrs: List<Pair<String, String>> = emptyList(),
    override val leading: String = DEFAULT_LEADING,
    val source: String? = null,
) : ResEntry {
    /** `translatable="false"`. */
    val translatable: Boolean get() = attrs.none { it.first == "translatable" && it.second == "false" }

    /** Enthält Auszeichnungen (`<b>`, `<xliff:g>`, CDATA) – wird dann roh bearbeitet. */
    val hasMarkup: Boolean get() = raw.contains('<')

    /** Text für die Anzeige und Bearbeitung (ohne Maskierung); bei Auszeichnungen der Rohtext. */
    val text: String get() = if (hasMarkup) raw else AndroidEscaper.unescape(raw)

    /** Neuer Eintrag mit Text [newText] (maskiert, außer bei Auszeichnungen). */
    fun withText(newText: String): StringEntry =
        copy(raw = if (hasMarkup) newText else AndroidEscaper.escape(newText), source = null)

    /** `translatable` setzen. */
    fun withTranslatable(on: Boolean): StringEntry {
        val rest = attrs.filterNot { it.first == "translatable" }
        return copy(attrs = if (on) rest else rest + ("translatable" to "false"), source = null)
    }

    /** XML des Elements. */
    fun toXml(): String = source ?: buildString {
        append("<string name=\"").append(XmlText.attr(name)).append('"')
        attrs.forEach { (k, v) -> append(' ').append(k).append("=\"").append(XmlText.attr(v)).append('"') }
        append('>').append(raw).append("</string>")
    }
}

/** Anderes Element (`string-array`, `plurals`, `color` …) oder Kommentar; wird unverändert geschrieben. */
data class RawEntry(
    val tag: String?,
    override val name: String?,
    val xml: String,
    override val leading: String = DEFAULT_LEADING,
    /** Kurzinfo für die Anzeige, z. B. die Einträge eines Arrays. */
    val summary: String = "",
) : ResEntry

/** Inhalt einer Ressourcendatei. [prolog] reicht bis einschließlich `<resources …>`, [epilog] ab `</resources>`. */
data class ResDocument(
    val prolog: String,
    val entries: List<ResEntry>,
    val trailing: String,
    val epilog: String,
) {
    val strings: List<StringEntry> get() = entries.filterIsInstance<StringEntry>()

    /** Datei als Text. */
    fun toXml(): String = buildString {
        append(prolog)
        entries.forEach { e ->
            append(e.leading)
            append(if (e is StringEntry) e.toXml() else (e as RawEntry).xml)
        }
        append(trailing).append(epilog)
    }

    /** Ersetzt den String-Eintrag mit Namen [name] (oder hängt [entry] an, wenn es ihn nicht gibt). */
    fun put(name: String?, entry: StringEntry): ResDocument {
        val i = entries.indexOfFirst { it is StringEntry && it.name == name }
        if (i < 0) return copy(entries = entries + entry.copy(leading = lastLeading()))
        return copy(entries = entries.toMutableList().also { it[i] = entry.copy(leading = entries[i].leading) })
    }

    fun remove(name: String): ResDocument = copy(entries = entries.filterNot { it is StringEntry && it.name == name })

    /** Verschiebt den String [name] um [delta] Plätze unter den Strings. */
    fun move(name: String, delta: Int): ResDocument {
        val idx = entries.indexOfFirst { it is StringEntry && it.name == name }
        if (idx < 0) return this
        val stringIdx = entries.indices.filter { entries[it] is StringEntry }
        val pos = stringIdx.indexOf(idx)
        val target = stringIdx.getOrNull(pos + delta) ?: return this
        val list = entries.toMutableList()
        val a = list[idx]
        val b = list[target]
        // Einrückung bleibt am Platz, nur die Einträge tauschen.
        list[idx] = retarget(b, a.leading)
        list[target] = retarget(a, b.leading)
        return copy(entries = list)
    }

    /** Strings alphabetisch, alles andere bleibt an seinem Platz. */
    fun sortStrings(): ResDocument {
        val sorted = strings.sortedBy { it.name.lowercase() }.iterator()
        return copy(entries = entries.map { e -> if (e is StringEntry) sorted.next().copy(leading = e.leading) else e })
    }

    private fun lastLeading(): String = entries.lastOrNull()?.leading?.substringAfterLast('\n')?.let { "\n" + it } ?: DEFAULT_LEADING

    private fun retarget(e: ResEntry, leading: String): ResEntry = when (e) {
        is StringEntry -> e.copy(leading = leading)
        is RawEntry -> e.copy(leading = leading)
    }

    companion object {
        /** Leere Datei. */
        val EMPTY = ResDocument("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>", emptyList(), "\n", "</resources>\n")
    }
}

const val DEFAULT_LEADING = "\n    "

/** Ungültige Datei. */
class ResParseException(message: String) : Exception(message)

/**
 * Zerlegt eine Ressourcendatei, ohne sie umzuformatieren: findet die Elemente direkt unter `<resources>` und behält
 * den Originaltext jedes Elements. Kein vollständiger XML-Parser – genug für Ressourcendateien (Kommentare, CDATA,
 * Verarbeitungsanweisungen, verschachtelte Auszeichnungen).
 */
object ResParser {
    private val ATTR = Regex("""([\w:.-]+)\s*=\s*("([^"]*)"|'([^']*)')""")

    fun parse(xml: String): ResDocument {
        val rootStart = findRoot(xml) ?: throw ResParseException("Kein <resources>-Element gefunden")
        val rootTagEnd = tagEnd(xml, rootStart)
        if (xml[rootTagEnd - 2] == '/') return ResDocument(xml.substring(0, rootTagEnd - 2) + ">", emptyList(), "", "</resources>" + xml.substring(rootTagEnd))
        val prolog = xml.substring(0, rootTagEnd)
        val entries = ArrayList<ResEntry>()
        var i = rootTagEnd
        while (true) {
            val next = xml.indexOf('<', i)
            if (next < 0) throw ResParseException("</resources> fehlt")
            val leading = xml.substring(i, next)
            if (leading.isNotBlank()) throw ResParseException("Text außerhalb eines Elements: „${leading.trim().take(40)}“")
            when {
                xml.startsWith("</resources", next) -> {
                    return ResDocument(prolog, entries, leading, xml.substring(next))
                }
                xml.startsWith("<!--", next) -> {
                    val end = xml.indexOf("-->", next).takeIf { it >= 0 } ?: throw ResParseException("Kommentar nicht geschlossen")
                    entries += RawEntry(null, null, xml.substring(next, end + 3), leading)
                    i = end + 3
                }
                else -> {
                    val end = elementEnd(xml, next)
                    entries += entry(xml.substring(next, end), leading)
                    i = end
                }
            }
        }
    }

    private fun entry(source: String, leading: String): ResEntry {
        val openEnd = tagEnd(source, 0)
        val head = source.substring(1, openEnd - 1).removeSuffix("/")
        val tag = head.trim().split(Regex("\\s+"), limit = 2).first()
        val attrs = ATTR.findAll(head.removePrefix(tag)).map { m ->
            m.groupValues[1] to XmlText.unescape(m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
        }.toList()
        val name = attrs.firstOrNull { it.first == "name" }?.second
        val selfClosing = source.endsWith("/>") && openEnd == source.length
        val inner = if (selfClosing) "" else source.substring(openEnd, source.lastIndexOf("</"))
        return if (tag == "string" && name != null) {
            StringEntry(name, inner, attrs.filterNot { it.first == "name" }, leading, source)
        } else {
            RawEntry(tag, name, source, leading, summary(tag, inner))
        }
    }

    private fun summary(tag: String, inner: String): String {
        val items = Regex("<item\\b[^>]*>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(inner)
            .map { AndroidEscaper.unescape(it.groupValues[1].trim()) }.toList()
        return when {
            items.isNotEmpty() -> items.joinToString(" · ").take(SUMMARY_MAX)
            tag == "string" -> inner.take(SUMMARY_MAX)
            else -> AndroidEscaper.unescape(inner.trim()).take(SUMMARY_MAX)
        }
    }

    /** Position von `<resources`, Kommentare und `<?…?>` davor übersprungen. */
    private fun findRoot(xml: String): Int? {
        var i = 0
        while (true) {
            val lt = xml.indexOf('<', i).takeIf { it >= 0 } ?: return null
            i = when {
                xml.startsWith("<?", lt) -> xml.indexOf("?>", lt).takeIf { it >= 0 }?.plus(2) ?: return null
                xml.startsWith("<!--", lt) -> xml.indexOf("-->", lt).takeIf { it >= 0 }?.plus(3) ?: return null
                xml.startsWith("<!", lt) -> xml.indexOf('>', lt) + 1
                xml.startsWith("<resources", lt) -> return lt
                else -> throw ResParseException("Wurzelelement ist nicht <resources>")
            }
        }
    }

    /** Index hinter dem `>` des Tags ab [start] (Anführungszeichen in Attributen beachtet). */
    private fun tagEnd(s: String, start: Int): Int {
        var quote: Char? = null
        for (k in start + 1 until s.length) {
            val c = s[k]
            if (quote != null) { if (c == quote) quote = null; continue }
            if (c == '"' || c == '\'') quote = c else if (c == '>') return k + 1
        }
        throw ResParseException("Tag nicht geschlossen")
    }

    /** Index hinter dem Element ab [start] (verschachtelt, Kommentare und CDATA darin übersprungen). */
    private fun elementEnd(s: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < s.length) {
            val lt = s.indexOf('<', i).takeIf { it >= 0 } ?: break
            i = when {
                s.startsWith("<!--", lt) -> s.indexOf("-->", lt).takeIf { it >= 0 }?.plus(3) ?: break
                s.startsWith("<![CDATA[", lt) -> s.indexOf("]]>", lt).takeIf { it >= 0 }?.plus(3) ?: break
                s.startsWith("</", lt) -> {
                    depth--
                    val e = s.indexOf('>', lt) + 1
                    if (depth == 0) return e
                    e
                }
                else -> {
                    val e = tagEnd(s, lt)
                    if (s[e - 2] != '/') depth++ else if (depth == 0) return e
                    e
                }
            }
        }
        throw ResParseException("Element nicht geschlossen: ${s.substring(start, minOf(s.length, start + 40))}")
    }

    private const val SUMMARY_MAX = 120
}

/** XML-Maskierung. */
object XmlText {
    /** Für Attributwerte. */
    fun attr(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    /** Entitäten auflösen (`&amp;`, `&lt;`, `&#123;` …). */
    fun unescape(s: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> {
                val cp = if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()
                String(Character.toChars(cp))
            }
        }
    }
}

/**
 * Android-Maskierung von String-Ressourcen (wie aapt2 sie liest): `\'`, `\"`, `\n`, `\t`, `\\`, `\@`/`\?` am Anfang,
 * `\uXXXX`, dazu XML-Entitäten. Text in doppelten Anführungszeichen wird wörtlich genommen.
 */
object AndroidEscaper {
    /** Klartext → Dateiinhalt. */
    fun escape(text: String): String {
        val sb = StringBuilder()
        text.forEachIndexed { i, c ->
            when (c) {
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '\'' -> sb.append("\\'")
                '"' -> sb.append("\\\"")
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '@', '?' -> sb.append(if (i == 0) "\\$c" else "$c")
                else -> sb.append(c)
            }
        }
        // aapt2 kürzt Leerraum am Rand und fasst Folgen zusammen – außer in Anführungszeichen.
        val needsQuotes = text != text.trim() || text.contains("  ")
        return if (needsQuotes) "\"$sb\"" else sb.toString()
    }

    /** Dateiinhalt → Klartext. */
    fun unescape(raw: String): String {
        val s = XmlText.unescape(raw)
        val sb = StringBuilder()
        var quoted = false
        var pendingSpace = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (pendingSpace && !(!quoted && c.isWhitespace())) {
                if (sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
            }
            when {
                c == '\\' && i + 1 < s.length -> {
                    val n = s[i + 1]
                    i += when (n) {
                        'n' -> { sb.append('\n'); 2 }
                        't' -> { sb.append('\t'); 2 }
                        'u' -> if (i + 6 <= s.length && s.substring(i + 2, i + 6).all { it.isLetterOrDigit() }) {
                            sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); 6
                        } else { sb.append(n); 2 }
                        else -> { sb.append(n); 2 }
                    }
                    continue
                }
                c == '"' -> quoted = !quoted
                !quoted && c.isWhitespace() -> pendingSpace = true
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}

/** Prüft Namen und Werte. */
object StringValidator {
    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_.]*$")

    /** Fehlermeldung für [name] oder `null`, wenn gültig. [taken] = schon vergebene Namen. */
    fun nameError(name: String, taken: Collection<String>): String? = when {
        name.isBlank() -> "Name fehlt"
        !NAME.matches(name) -> "Nur Buchstaben, Ziffern, _ und . – nicht mit einer Ziffer beginnen"
        name in taken -> "Name gibt es schon"
        else -> null
    }
}
