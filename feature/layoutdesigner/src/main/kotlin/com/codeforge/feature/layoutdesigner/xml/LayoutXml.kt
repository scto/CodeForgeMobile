/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Lesen und Schreiben von Android-Layout-XML unter Erhalt von Kommentaren, Einrückung,
 * benutzerdefinierten Namespaces und Attribut-Reihenfolge. DOCTYPE wird abgelehnt.
 */
package com.codeforge.feature.layoutdesigner.xml

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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
        val cleanXml = xml.trimStart('\uFEFF')
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.isExpandEntityReferences = false }
            factory.isNamespaceAware = false
            factory.newDocumentBuilder().apply {
                setErrorHandler(null)
            }.parse(InputSource(StringReader(cleanXml)))
        } catch (e: Exception) {
            throw LayoutParseException(e.message?.lineSequence()?.firstOrNull() ?: Res.string(R.string.layout_xml_ungueltig), e)
        }

        // Kommentare vor dem Wurzelelement sammeln
        val docLeadingComments = ArrayList<String>()
        var docChild: Node? = document.firstChild
        while (docChild != null) {
            if (docChild.nodeType == Node.COMMENT_NODE) {
                docLeadingComments.add(docChild.nodeValue)
            } else if (docChild.nodeType == Node.ELEMENT_NODE) {
                break
            }
            docChild = docChild.nextSibling
        }

        val rootElement = document.documentElement ?: throw LayoutParseException(Res.string(R.string.layout_kein_wurzelelement))

        val sourceTags = extractTagAttributesInOrder(cleanXml)
        val rootNamespaces = LinkedHashMap<String, String>()

        if (sourceTags.isNotEmpty()) {
            for ((name, value) in sourceTags[0]) {
                if (name == "xmlns") {
                    rootNamespaces[""] = value
                } else if (name.startsWith("xmlns:")) {
                    rootNamespaces[name.substringAfter("xmlns:")] = value
                }
            }
        }

        val rootAttrs = rootElement.attributes
        for (i in 0 until rootAttrs.length) {
            val a = rootAttrs.item(i)
            if (a.nodeName == "xmlns" && "" !in rootNamespaces) {
                rootNamespaces[""] = a.nodeValue
            } else if (a.nodeName.startsWith("xmlns:")) {
                val prefix = a.nodeName.substringAfter("xmlns:")
                if (prefix !in rootNamespaces) {
                    rootNamespaces[prefix] = a.nodeValue
                }
            }
        }
        if ("android" !in rootNamespaces) {
            rootNamespaces["android"] = NS_ANDROID
        }

        val detectedIndent = detectIndentation(cleanXml)
        var counter = 0
        var tagCursor = 0

        fun convert(e: Element): LayoutNode {
            val uid = counter++
            val attrs = LinkedHashMap<String, String>()
            val sourceAttrs = if (tagCursor < sourceTags.size) sourceTags[tagCursor++] else null

            if (sourceAttrs != null) {
                for ((name, value) in sourceAttrs) {
                    if (name == "xmlns" || name.startsWith("xmlns:")) continue
                    val domVal = e.getAttribute(name).takeIf { it.isNotEmpty() } ?: value
                    attrs[name] = domVal
                }
            } else {
                val named = e.attributes
                for (i in 0 until named.length) {
                    val a = named.item(i)
                    if (a.nodeName == "xmlns" || a.nodeName.startsWith("xmlns:")) continue
                    attrs[a.nodeName] = a.nodeValue
                }
            }

            val children = ArrayList<LayoutNode>()
            val pendingComments = ArrayList<String>()
            var textContent: String? = null

            var c: Node? = e.firstChild
            while (c != null) {
                when (c.nodeType) {
                    Node.COMMENT_NODE -> {
                        pendingComments.add(c.nodeValue)
                    }
                    Node.TEXT_NODE -> {
                        val text = c.nodeValue
                        if (text.isNotBlank()) {
                            textContent = text.trim()
                        }
                    }
                    Node.ELEMENT_NODE -> {
                        val childNode = convert(c as Element)
                        val childWithComments = if (pendingComments.isNotEmpty()) {
                            val existing = childNode.leadingComments
                            childNode.copy(leadingComments = pendingComments.toList() + existing)
                        } else {
                            childNode
                        }
                        pendingComments.clear()
                        children += childWithComments
                    }
                }
                c = c.nextSibling
            }

            val trailingComments = if (pendingComments.isNotEmpty()) pendingComments.toList() else emptyList()
            val isInline = isFirstAttributeInline(cleanXml, e.tagName)

            return LayoutNode(
                uid = uid,
                tag = e.tagName,
                attributes = attrs,
                children = children,
                leadingComments = emptyList(),
                trailingComments = trailingComments,
                textContent = textContent,
                isNew = false,
                firstAttrInline = isInline,
            )
        }

        val rootNode = convert(rootElement)

        return LayoutDocument(
            root = rootNode,
            nextUid = counter,
            leadingComments = docLeadingComments,
            rootNamespaces = rootNamespaces,
            indentation = detectedIndent,
            reformatOnSave = false,
        )
    }

    private fun extractTagAttributesInOrder(xml: String): List<List<Pair<String, String>>> {
        val result = ArrayList<List<Pair<String, String>>>()
        val tagRegex = Regex("""<([A-Za-z0-9_.]+)([^>]*)>""")
        for (m in tagRegex.findAll(xml)) {
            val tagName = m.groupValues[1]
            if (tagName.startsWith("?") || tagName.startsWith("!") || tagName.startsWith("/")) continue
            val attrString = m.groupValues[2]
            val attrs = ArrayList<Pair<String, String>>()
            val attrRegex = Regex("""([A-Za-z0-9_:]+)\s*=\s*"([^"]*)"""")
            for (attrMatch in attrRegex.findAll(attrString)) {
                attrs.add(attrMatch.groupValues[1] to attrMatch.groupValues[2])
            }
            result.add(attrs)
        }
        return result
    }

    private fun detectIndentation(xml: String): String {
        val lines = xml.lineSequence().toList()
        var candidateIndent: String? = null
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("<") && !trimmed.startsWith("<?") && !trimmed.startsWith("<!") && !trimmed.startsWith("</")) {
                val indent = line.substring(0, line.length - trimmed.length)
                if (indent.isNotEmpty()) {
                    if (indent.contains('\t')) return "\t"
                    if (candidateIndent == null || indent.length < candidateIndent.length) {
                        candidateIndent = indent
                    }
                }
            }
        }
        return candidateIndent ?: "    "
    }

    private fun isFirstAttributeInline(xml: String, tagName: String): Boolean {
        val pattern = Regex("<$tagName([ \t\r\n]+)[^>]*>")
        val match = pattern.find(xml) ?: return false
        val whitespace = match.groupValues[1]
        return !whitespace.contains('\n')
    }

    fun write(document: LayoutDocument, reformat: Boolean = document.reformatOnSave): String {
        val prefixes = LinkedHashSet<String>()
        fun collect(n: LayoutNode) {
            n.attributes.keys.forEach { k -> k.substringBefore(':', "").takeIf { it.isNotEmpty() }?.let(prefixes::add) }
            n.children.forEach(::collect)
        }
        collect(document.root)
        prefixes += "android"

        val namespaces = LinkedHashMap<String, String>()
        if (reformat || document.rootNamespaces.isEmpty()) {
            namespaces["android"] = NS_ANDROID
            if ("app" in prefixes) namespaces["app"] = NS_APP
            if ("tools" in prefixes) namespaces["tools"] = NS_TOOLS
        } else {
            namespaces.putAll(document.rootNamespaces)
            if ("app" in prefixes && "app" !in namespaces) namespaces["app"] = NS_APP
            if ("tools" in prefixes && "tools" !in namespaces) namespaces["tools"] = NS_TOOLS
        }

        val step = if (reformat) "    " else document.indentation

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            document.leadingComments.forEach { comment ->
                append("<!--").append(comment).append("-->\n")
            }
            writeNode(document.root, 0, namespaces, step, reformat)
        }
    }

    private fun StringBuilder.writeNode(
        node: LayoutNode,
        depth: Int,
        namespaces: Map<String, String>?,
        step: String,
        reformat: Boolean,
    ) {
        val indent = step.repeat(depth)
        val attrIndent = step.repeat(depth + 1)

        node.leadingComments.forEach { comment ->
            append(indent).append("<!--").append(comment).append("-->\n")
        }

        append(indent).append('<').append(node.tag)

        val ordered = if (reformat || node.isNew) {
            buildList {
                val keys = node.attributes.keys
                FIRST_ATTRS.filter { it in keys }.forEach { add(it) }
                keys.filter { it !in FIRST_ATTRS }.forEach { add(it) }
            }
        } else {
            node.attributes.keys.toList()
        }

        val lines = ArrayList<String>()
        namespaces?.forEach { (p, uri) ->
            if (p.isEmpty()) lines += "xmlns=\"$uri\"" else lines += "xmlns:$p=\"$uri\""
        }
        ordered.forEach { attrName ->
            val value = node.attributes[attrName] ?: ""
            lines += "$attrName=\"${escape(value)}\""
        }

        if (lines.size == 1) {
            append(' ').append(lines[0])
        } else if (lines.isNotEmpty()) {
            val inlineFirst = !reformat && node.firstAttrInline
            if (inlineFirst) {
                append(' ').append(lines[0])
                for (i in 1 until lines.size) {
                    append('\n').append(attrIndent).append(lines[i])
                }
            } else {
                lines.forEach { append('\n').append(attrIndent).append(it) }
            }
        }

        val hasChildren = node.children.isNotEmpty()
        val hasTrailingComments = node.trailingComments.isNotEmpty()
        val hasText = !node.textContent.isNullOrEmpty()

        if (!hasChildren && !hasTrailingComments && !hasText) {
            append(" />\n")
        } else {
            append(">\n")
            if (hasText) {
                append(attrIndent).append(escape(node.textContent!!)).append('\n')
            }
            node.children.forEach { child ->
                writeNode(child, depth + 1, null, step, reformat)
            }
            node.trailingComments.forEach { comment ->
                append(attrIndent).append("<!--").append(comment).append("-->\n")
            }
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
