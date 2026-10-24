/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reine Kotlin-Zwischenablage für Layout-XML-Schnipsel.
 * Ermöglicht Kopieren und Einfügen von Teilbäumen mit automatischer Id-Neuzuordnung bei Kollisionen.
 */
package com.codeforge.feature.layoutdesigner.model

import com.codeforge.feature.layoutdesigner.xml.LayoutParseException
import com.codeforge.feature.layoutdesigner.xml.LayoutXml

object LayoutClipboard {

    /**
     * Erzeugt einen XML-Schnipsel aus den angegebenen [uids].
     */
    fun createSnippet(document: LayoutDocument, uids: Set<Int>): String {
        if (uids.isEmpty()) return ""
        val nodes = uids.mapNotNull { document.find(it) }
        if (nodes.isEmpty()) return ""

        return buildString {
            nodes.forEach { node ->
                val virtualDoc = LayoutDocument(
                    root = node,
                    nextUid = document.nextUid,
                    rootNamespaces = document.rootNamespaces,
                    indentation = document.indentation,
                )
                append(LayoutXml.write(virtualDoc, reformat = false))
                append("\n")
            }
        }.trim()
    }

    /**
     * Fügt [xmlSnippet] in [targetParentUid] ein. Vergibt bei Id-Kollisionen neue Kennungen.
     */
    fun pasteSnippet(
        document: LayoutDocument,
        xmlSnippet: String,
        targetParentUid: Int,
        targetIndex: Int? = null,
    ): AddResult {
        if (xmlSnippet.isBlank()) return AddResult(document, null)
        val target = document.find(targetParentUid) ?: return AddResult(document, null)
        if (!target.isContainer) return AddResult(document, null)

        val cleanSnippet = xmlSnippet.replace(Regex("""<\?xml.*?\?>"""), "").trim()
        if (cleanSnippet.isBlank()) return AddResult(document, null)

        val snippetDoc = try {
            val wrapped = "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n$cleanSnippet\n</FrameLayout>"
            LayoutXml.parse(wrapped)
        } catch (e: LayoutParseException) {
            return AddResult(document, null)
        }

        val existingIds = document.flatten().mapNotNull { it.first.attr("android:id") }.toMutableSet()
        var currentUid = document.nextUid

        fun reassignIdsAndUids(node: LayoutNode): LayoutNode {
            val assignedUid = currentUid++
            val newAttrs = LinkedHashMap(node.attributes)
            val oldId = node.attr("android:id")
            if (oldId != null) {
                val raw = oldId.removePrefix("@+id/").removePrefix("@id/")
                if (oldId in existingIds || "@+id/$raw" in existingIds || "@id/$raw" in existingIds) {
                    val base = node.tag.substringAfterLast('.').replaceFirstChar { it.lowercase() }
                    var i = 1
                    while ("@+id/$base$i" in existingIds || "@id/$base$i" in existingIds) i++
                    val fresh = "@+id/$base$i"
                    newAttrs["android:id"] = fresh
                    existingIds.add(fresh)
                } else {
                    existingIds.add(oldId)
                }
            }
            return node.copy(
                uid = assignedUid,
                attributes = newAttrs,
                children = node.children.map(::reassignIdsAndUids),
                isNew = true,
            )
        }

        val nodesToInsert = snippetDoc.root.children.map(::reassignIdsAndUids)

        if (nodesToInsert.isEmpty()) return AddResult(document, null)

        var updatedDoc = document
        val insertAt = targetIndex ?: target.children.size
        for ((idx, node) in nodesToInsert.withIndex()) {
            updatedDoc = updatedDoc.insertNode(node, targetParentUid, insertAt + idx)
        }

        return AddResult(updatedDoc.copy(nextUid = currentUid), nodesToInsert.first().uid)
    }
}
