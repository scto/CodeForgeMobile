/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Lesen und Schreiben von Android-Layout-XML. Das Lesen nutzt den JDK-/Android-DOM-Parser ohne
 * Namespace-Auflösung (Attributnamen bleiben als `android:text` erhalten); DOCTYPE wird
 * abgelehnt (keine externen Entities).
 */
package com.codeforge.feature.layoutdesigner.xml

import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

class LayoutParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
private const val NS_APP = "http://schemas.android.com/apk/res-auto"
private const val NS_TOOLS = "http://schemas.android.com/tools"
private val FIRST_ATTRS = listOf("android:id", "android:layout_width", "android:layout_height")

object LayoutXml {

    /** @throws LayoutParseException bei ungültigem XML oder fehlendem Wurzelelement. */
    fun parse(xml: String): LayoutDocument {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.isExpandEntityReferences = false }
            factory.isNamespaceAware = false
            factory.newDocumentBuilder().apply {
                setErrorHandler(null)
            }.parse(InputSource(StringReader(xml.trimStart('﻿'))))
        } catch (e: Exception) {
            throw LayoutParseException(e.message?.lineSequence()?.firstOrNull() ?: "XML ungültig", e)
        }
        val rootElement = document.documentElement ?: throw LayoutParseException("Kein Wurzelelement")
        var counter = 0
        fun convert(e: Element): LayoutNode {
            val uid = counter++
            val attrs = LinkedHashMap<String, String>()
            val named = e.attributes
            for (i in 0 until named.length) {
                val a = named.item(i)
                if (a.nodeName == "xmlns" || a.nodeName.startsWith("xmlns:")) continue
                attrs[a.nodeName] = a.nodeValue
            }
            val children = ArrayList<LayoutNode>()
            var c: Node? = e.firstChild
            while (c != null) {
                if (c is Element) children += convert(c)
                c = c.nextSibling
            }
            return LayoutNode(uid, e.tagName, attrs, children)
        }
        val root = convert(rootElement)
        return LayoutDocument(root, counter)
    }

    fun write(document: LayoutDocument): String {
        val prefixes = LinkedHashSet<String>()
        fun collect(n: LayoutNode) {
            n.attributes.keys.forEach { k -> k.substringBefore(':', "").takeIf { it.isNotEmpty() }?.let(prefixes::add) }
            n.children.forEach(::collect)
        }
        collect(document.root)
        prefixes += "android"
        val namespaces = linkedMapOf<String, String>()
        namespaces["android"] = NS_ANDROID
        if ("app" in prefixes) namespaces["app"] = NS_APP
        if ("tools" in prefixes) namespaces["tools"] = NS_TOOLS

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            writeNode(document.root, 0, namespaces)
        }
    }

    private fun StringBuilder.writeNode(node: LayoutNode, depth: Int, namespaces: Map<String, String>?) {
        val indent = "    ".repeat(depth)
        val attrIndent = "    ".repeat(depth + 1)
        append(indent).append('<').append(node.tag)
        val ordered = buildList {
            val keys = node.attributes.keys
            FIRST_ATTRS.filter { it in keys }.forEach { add(it) }
            keys.filter { it !in FIRST_ATTRS }.forEach { add(it) }
        }
        val lines = ArrayList<String>()
        namespaces?.forEach { (p, uri) -> lines += "xmlns:$p=\"$uri\"" }
        ordered.forEach { lines += "$it=\"${escape(node.attributes.getValue(it))}\"" }
        if (lines.size == 1) append(' ').append(lines[0])
        else lines.forEach { append('\n').append(attrIndent).append(it) }
        if (node.children.isEmpty()) {
            append(" />\n")
        } else {
            append(">\n")
            node.children.forEach { writeNode(it, depth + 1, null) }
            append(indent).append("</").append(node.tag).append(">\n")
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (ch in value) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\n' -> append("&#10;")
            '\t' -> append("&#9;")
            else -> append(ch)
        }
    }
}
