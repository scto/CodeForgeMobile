package com.codeforge.libs.x2c.theme

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

internal data class StyleDef(val name: String, val parent: String?, val items: Map<String, String>)

internal data class ValuesDoc(val colors: Map<String, String>, val styles: List<StyleDef>)

/** Liest `<color>` und `<style>/<item>` aus einer values-Datei (SAX, ohne DTD/Entities). */
internal object ValuesXmlParser {
    fun parse(xml: String): ValuesDoc {
        val colors = LinkedHashMap<String, String>()
        val styles = mutableListOf<StyleDef>()
        val spf = SAXParserFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        var styleName: String? = null
        var styleParent: String? = null
        var items = LinkedHashMap<String, String>()
        var currentName: String? = null
        var currentKind: String? = null
        val text = StringBuilder()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName) {
                    "color" -> { currentKind = "color"; currentName = a.getValue("name"); text.setLength(0) }
                    "style" -> { styleName = a.getValue("name"); styleParent = a.getValue("parent"); items = LinkedHashMap() }
                    "item" -> if (styleName != null) { currentKind = "item"; currentName = a.getValue("name"); text.setLength(0) }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (currentKind != null) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "color" -> { currentName?.let { colors[it] = text.toString().trim() }; currentKind = null }
                    "item" -> if (currentKind == "item") { currentName?.let { items[it] = text.toString().trim() }; currentKind = null }
                    "style" -> { styleName?.let { styles += StyleDef(it, styleParent, items) }; styleName = null }
                }
            }
        }
        spf.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        return ValuesDoc(colors, styles)
    }
}
