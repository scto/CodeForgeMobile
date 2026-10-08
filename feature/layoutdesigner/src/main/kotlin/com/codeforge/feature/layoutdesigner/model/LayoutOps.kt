/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reine, unveränderliche Baum-Operationen auf [LayoutDocument]. Jede Funktion liefert ein neues
 * Dokument (oder das unveränderte, wenn die Operation nicht anwendbar ist) – ideal für Undo/Redo.
 */
package com.codeforge.feature.layoutdesigner.model

fun LayoutNode.find(uid: Int): LayoutNode? {
    if (this.uid == uid) return this
    for (c in children) c.find(uid)?.let { return it }
    return null
}

fun LayoutDocument.find(uid: Int): LayoutNode? = root.find(uid)

fun LayoutDocument.parentOf(uid: Int): LayoutNode? {
    fun walk(n: LayoutNode): LayoutNode? {
        if (n.children.any { it.uid == uid }) return n
        for (c in n.children) walk(c)?.let { return it }
        return null
    }
    return walk(root)
}

/** Alle Knoten in Dokumentreihenfolge mit Tiefe (für die Baumansicht). */
fun LayoutDocument.flatten(): List<Pair<LayoutNode, Int>> = buildList {
    fun walk(n: LayoutNode, depth: Int) {
        add(n to depth)
        n.children.forEach { walk(it, depth + 1) }
    }
    walk(root, 0)
}

private fun LayoutNode.mapNode(uid: Int, transform: (LayoutNode) -> LayoutNode): LayoutNode =
    if (this.uid == uid) transform(this) else copy(children = children.map { it.mapNode(uid, transform) })

/** Container, in den ein neues Widget bei Auswahl von [selectedUid] eingefügt wird. */
fun LayoutDocument.insertTarget(selectedUid: Int?): LayoutNode {
    val selected = selectedUid?.let { find(it) } ?: return root
    if (selected.isContainer) return selected
    return parentOf(selected.uid) ?: root
}

private val idCounterRegex = Regex("[^A-Za-z0-9_]")

/** Erzeugt eine im Dokument noch nicht verwendete `@+id/…`-Kennung. */
fun LayoutDocument.freshId(tag: String): String {
    val base = tag.substringAfterLast('.').replaceFirstChar { it.lowercase() }.replace(idCounterRegex, "")
    val used = flatten().mapNotNull { it.first.attr("android:id") }.toSet()
    var n = 1
    while ("@+id/$base$n" in used || "@id/$base$n" in used) n++
    return "@+id/$base$n"
}

data class AddResult(val document: LayoutDocument, val newUid: Int?)

/** Fügt ein Widget aus der Palette am Ende des passenden Containers ein. */
fun LayoutDocument.addWidget(entry: PaletteEntry, selectedUid: Int?): AddResult {
    val target = insertTarget(selectedUid)
    val attrs = LinkedHashMap<String, String>()
    attrs["android:id"] = freshId(entry.tag)
    attrs.putAll(entry.defaults)
    val node = LayoutNode(uid = nextUid, tag = entry.tag, attributes = attrs)
    val updated = root.mapNode(target.uid) { it.copy(children = it.children + node) }
    return AddResult(LayoutDocument(updated, nextUid + 1), node.uid)
}

fun LayoutDocument.remove(uid: Int): LayoutDocument {
    if (uid == root.uid) return this
    fun strip(n: LayoutNode): LayoutNode =
        n.copy(children = n.children.filter { it.uid != uid }.map(::strip))
    return copy(root = strip(root))
}

/** Tiefe Kopie mit frischen uids/ids direkt hinter dem Original. */
fun LayoutDocument.duplicate(uid: Int): AddResult {
    if (uid == root.uid) return AddResult(this, null)
    val parent = parentOf(uid) ?: return AddResult(this, null)
    val original = find(uid) ?: return AddResult(this, null)
    var counter = nextUid
    // Kennungen innerhalb des Klons eindeutig halten: Ids sammeln, die bereits vergeben wurden.
    val taken = HashSet<String>(flatten().mapNotNull { it.first.attr("android:id") })
    fun cloneUnique(n: LayoutNode): LayoutNode {
        val newAttrs = LinkedHashMap(n.attributes)
        n.attr("android:id")?.let {
            val base = n.tag.substringAfterLast('.').replaceFirstChar { c -> c.lowercase() }.replace(idCounterRegex, "")
            var i = 1
            while ("@+id/$base$i" in taken) i++
            val id = "@+id/$base$i"
            taken += id
            newAttrs["android:id"] = id
        }
        val self = counter++
        return LayoutNode(self, n.tag, newAttrs, n.children.map(::cloneUnique))
    }
    val clone = cloneUnique(original)
    val updated = root.mapNode(parent.uid) { p ->
        val index = p.children.indexOfFirst { it.uid == uid }
        p.copy(children = p.children.toMutableList().apply { add(index + 1, clone) })
    }
    return AddResult(LayoutDocument(updated, counter), clone.uid)
}

/** Verschiebt innerhalb des Elternteils um [delta] Positionen (−1 = nach oben). */
fun LayoutDocument.moveWithinParent(uid: Int, delta: Int): LayoutDocument {
    val parent = parentOf(uid) ?: return this
    val from = parent.children.indexOfFirst { it.uid == uid }
    val to = (from + delta).coerceIn(0, parent.children.lastIndex)
    if (from == to) return this
    val updated = root.mapNode(parent.uid) { p ->
        p.copy(children = p.children.toMutableList().apply { add(to, removeAt(from)) })
    }
    return copy(root = updated)
}

/** Einrücken: das Widget wandert in den vorherigen Geschwister-Container (ans Ende). */
fun LayoutDocument.indent(uid: Int): LayoutDocument {
    val parent = parentOf(uid) ?: return this
    val index = parent.children.indexOfFirst { it.uid == uid }
    val previous = parent.children.getOrNull(index - 1)?.takeIf { it.isContainer } ?: return this
    val node = parent.children[index]
    val updated = root.mapNode(parent.uid) { p ->
        p.copy(
            children = p.children.filter { it.uid != uid }.map {
                if (it.uid == previous.uid) it.copy(children = it.children + node) else it
            },
        )
    }
    return copy(root = updated)
}

/** Ausrücken: das Widget wandert hinter seinen Elternteil in dessen Elternteil. */
fun LayoutDocument.outdent(uid: Int): LayoutDocument {
    val parent = parentOf(uid) ?: return this
    val grand = parentOf(parent.uid) ?: return this
    val node = parent.children.first { it.uid == uid }
    val updated = root.mapNode(grand.uid) { g ->
        val parentIndex = g.children.indexOfFirst { it.uid == parent.uid }
        val newChildren = g.children.toMutableList()
        newChildren[parentIndex] = parent.copy(children = parent.children.filter { it.uid != uid })
        newChildren.add(parentIndex + 1, node)
        g.copy(children = newChildren)
    }
    return copy(root = updated)
}

/** Setzt ein Attribut; `null`/leer entfernt es. Ein neues Attribut wird ans Ende gehängt. */
fun LayoutDocument.setAttribute(uid: Int, name: String, value: String?): LayoutDocument {
    val node = find(uid) ?: return this
    if (node.attributes[name] == value || (value.isNullOrEmpty() && name !in node.attributes)) return this
    val updated = root.mapNode(uid) { n ->
        val map = LinkedHashMap(n.attributes)
        if (value.isNullOrEmpty()) map.remove(name) else map[name] = value
        n.copy(attributes = map)
    }
    return copy(root = updated)
}

/** Wandelt ein Widget in einen anderen Tag um (Kinder und gemeinsame Attribute bleiben). */
fun LayoutDocument.changeTag(uid: Int, newTag: String): LayoutDocument {
    if (find(uid) == null || newTag.isBlank()) return this
    return copy(root = root.mapNode(uid) { it.copy(tag = newTag) })
}
