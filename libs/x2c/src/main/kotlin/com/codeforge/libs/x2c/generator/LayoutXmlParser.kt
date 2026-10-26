package com.codeforge.libs.x2c.generator

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/**
 * Ein Element eines Layout-XML mit Quellzeile. Attribute tragen den normalisierten Präfix
 * (`android:`, `app:`, `tools:`), unabhängig davon, wie das Projekt den Namensraum benannt hat.
 */
internal class XmlElement(
    val tag: String,
    val attrs: LinkedHashMap<String, String>,
    val line: Int,
    var parent: XmlElement? = null
) {
    val children: MutableList<XmlElement> = mutableListOf()
    private val consumed = HashSet<String>()

    /** Liest ein Attribut und merkt es als behandelt (alles Unbehandelte wird zu einem `TODO(x2c)`). */
    fun attr(name: String): String? {
        consumed += name
        return attrs[name]
    }

    fun peek(name: String): String? = attrs[name]

    fun markConsumed(name: String) {
        consumed += name
    }

    fun unconsumed(): List<String> = attrs.keys.filter { it !in consumed }

    val shortTag: String get() = tag.substringAfterLast('.')
}

/** SAX-Parser ohne DTD/externe Entities (XXE-sicher), Zeilennummern über Textsuche (Kommentare ausgeblendet). */
internal object LayoutXmlParser {
    private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
    private const val NS_APP = "http://schemas.android.com/apk/res-auto"
    private const val NS_TOOLS = "http://schemas.android.com/tools"
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    fun parse(xml: String): XmlElement {
        require(xml.isNotBlank()) { "XML ist leer" }
        val scan = COMMENT.replace(xml) { m -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }

        val spf = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }

        var root: XmlElement? = null
        var current: XmlElement? = null
        var cursor = 0
        var lineAtCursor = 1
        var countedUpTo = 0

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                val needle = "<$qName"
                var idx = scan.indexOf(needle, cursor)
                while (idx >= 0 && idx + needle.length < scan.length && isNameChar(scan[idx + needle.length])) {
                    idx = scan.indexOf(needle, idx + 1)
                }
                val line = if (idx < 0) lineAtCursor else {
                    for (i in countedUpTo until idx) if (scan[i] == '\n') lineAtCursor++
                    countedUpTo = idx
                    cursor = idx + 1
                    lineAtCursor
                }
                val attrs = LinkedHashMap<String, String>()
                for (i in 0 until attributes.length) {
                    val local = attributes.getLocalName(i).ifEmpty { attributes.getQName(i) }
                    val key = when (attributes.getURI(i)) {
                        NS_ANDROID -> "android:$local"
                        NS_APP -> "app:$local"
                        NS_TOOLS -> "tools:$local"
                        "" -> local
                        else -> attributes.getQName(i)
                    }
                    attrs[key] = attributes.getValue(i)
                }
                val el = XmlElement(qName, attrs, line, current)
                current?.children?.add(el)
                if (root == null) root = el
                current = el
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                current = current?.parent
            }
        }
        spf.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        return requireNotNull(root) { "Kein Wurzelelement" }
    }

    private fun isNameChar(c: Char) = c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' || c == ':'
}
