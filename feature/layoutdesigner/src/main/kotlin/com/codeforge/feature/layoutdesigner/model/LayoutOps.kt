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

fun LayoutDocument.isDescendantOf(candidateUid: Int, ancestorUid: Int): Boolean {
    val ancestor = find(ancestorUid) ?: return false
    return ancestor.find(candidateUid) != null && candidateUid != ancestorUid
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
    val node = LayoutNode(uid = nextUid, tag = entry.tag, attributes = attrs, isNew = true)
    val updated = root.mapNode(target.uid) { it.copy(children = it.children + node) }
    return AddResult(LayoutDocument(updated, nextUid + 1, leadingComments, rootNamespaces, indentation, reformatOnSave), node.uid)
}

/** Fügt ein Widget an einer expliziten Position [index] im [parentUid]-Container ein. */
fun LayoutDocument.addWidgetAt(entry: PaletteEntry, parentUid: Int, index: Int): AddResult {
    val parent = find(parentUid) ?: return AddResult(this, null)
    if (!parent.isContainer) return AddResult(this, null)
    val attrs = LinkedHashMap<String, String>()
    attrs["android:id"] = freshId(entry.tag)
    attrs.putAll(entry.defaults)
    val node = LayoutNode(uid = nextUid, tag = entry.tag, attributes = attrs, isNew = true)
    val updated = root.mapNode(parentUid) { p ->
        val list = p.children.toMutableList()
        val targetIndex = index.coerceIn(0, list.size)
        list.add(targetIndex, node)
        p.copy(children = list)
    }
    return AddResult(LayoutDocument(updated, nextUid + 1, leadingComments, rootNamespaces, indentation, reformatOnSave), node.uid)
}

/** Fügt einen bestehenden [node] an Position [index] unter [parentUid] ein. */
fun LayoutDocument.insertNode(node: LayoutNode, parentUid: Int, index: Int): LayoutDocument {
    val parent = find(parentUid) ?: return this
    if (!parent.isContainer) return this
    var maxUid = nextUid - 1
    fun count(n: LayoutNode) {
        if (n.uid > maxUid) maxUid = n.uid
        n.children.forEach(::count)
    }
    count(node)
    val updated = root.mapNode(parentUid) { p ->
        val list = p.children.toMutableList()
        val targetIndex = index.coerceIn(0, list.size)
        list.add(targetIndex, node)
        p.copy(children = list)
    }
    return copy(root = updated, nextUid = maxOf(nextUid, maxUid + 1))
}

/**
 * Verschiebt [uid] an Position [index] unter [newParentUid].
 * Ungültig: Wurzel verschieben, in sich selbst, in eigenen Nachfahren oder in Nicht-Container.
 */
fun LayoutDocument.moveNode(uid: Int, newParentUid: Int, index: Int): LayoutDocument {
    if (uid == root.uid || uid == newParentUid) return this
    val nodeToMove = find(uid) ?: return this
    val oldParent = parentOf(uid) ?: return this
    val newParent = find(newParentUid) ?: return this
    if (!newParent.isContainer) return this
    if (isDescendantOf(newParentUid, uid)) return this

    if (oldParent.uid == newParentUid) {
        val oldIndex = oldParent.children.indexOfFirst { it.uid == uid }
        val targetIndex = index.coerceIn(0, oldParent.children.lastIndex)
        if (oldIndex == targetIndex) return this
        val updated = root.mapNode(newParentUid) { p ->
            val list = p.children.toMutableList()
            val item = list.removeAt(oldIndex)
            list.add(targetIndex, item)
            p.copy(children = list)
        }
        return copy(root = updated)
    }

    val rootWithoutNode = root.mapNode(oldParent.uid) { p ->
        p.copy(children = p.children.filter { it.uid != uid })
    }
    val updatedRoot = rootWithoutNode.mapNode(newParentUid) { p ->
        val list = p.children.toMutableList()
        val targetIndex = index.coerceIn(0, list.size)
        list.add(targetIndex, nodeToMove)
        p.copy(children = list)
    }
    return copy(root = updatedRoot)
}

sealed interface DragPayload {
    data class NewWidget(val entry: PaletteEntry) : DragPayload
    data class ExistingNodes(val uids: Set<Int>) : DragPayload
}

sealed interface DropVerdict {
    data object Allowed : DropVerdict
    data class Warning(val reason: String) : DropVerdict
    data class Rejected(val reason: String) : DropVerdict
}

/** Prüft Regeln für Drag & Drop. */
fun LayoutDocument.canDrop(dragged: DragPayload, parentUid: Int): DropVerdict {
    val parent = find(parentUid) ?: return DropVerdict.Rejected("Elternelement existiert nicht")
    if (!parent.isContainer) return DropVerdict.Rejected("Element ist kein Container")

    if (parent.tag in setOf("include", "ViewStub")) {
        return DropVerdict.Rejected("${parent.tag} erlaubt keine Kinder")
    }

    val singleChildContainers = setOf("ScrollView", "NestedScrollView", "HorizontalScrollView")
    if (parent.tag in singleChildContainers) {
        when (dragged) {
            is DragPayload.NewWidget -> {
                if (parent.children.isNotEmpty()) {
                    return DropVerdict.Rejected("${parent.tag} erlaubt nur genau ein Kind")
                }
            }
            is DragPayload.ExistingNodes -> {
                val nonDraggedChildren = parent.children.filter { it.uid !in dragged.uids }
                if (nonDraggedChildren.isNotEmpty() || dragged.uids.size > 1) {
                    return DropVerdict.Rejected("${parent.tag} erlaubt nur genau ein Kind")
                }
            }
        }
    }

    when (dragged) {
        is DragPayload.NewWidget -> {
            if (dragged.entry.tag == "merge" && parentUid != root.uid) {
                return DropVerdict.Rejected("<merge> ist nur als Wurzelelement zulässig")
            }
            if (dragged.entry.tag == "TableRow" && parent.tag != "TableLayout") {
                return DropVerdict.Warning("<TableRow> sollte nur in einem <TableLayout> platziert werden")
            }
        }
        is DragPayload.ExistingNodes -> {
            if (root.uid in dragged.uids) {
                return DropVerdict.Rejected("Wurzelelement kann nicht verschoben werden")
            }
            if (parentUid in dragged.uids) {
                return DropVerdict.Rejected("Element kann nicht in sich selbst abgelegt werden")
            }
            if (dragged.uids.any { isDescendantOf(parentUid, it) }) {
                return DropVerdict.Rejected("Element kann nicht in einen eigenen Nachfahren abgelegt werden")
            }
        }
    }

    return DropVerdict.Allowed
}

/** Packt [uids] in einen neuen Container [containerTag]. Alle Knoten müssen denselben Elternteil haben. */
fun LayoutDocument.wrapIn(uids: Set<Int>, containerTag: String): AddResult {
    if (uids.isEmpty() || root.uid in uids) return AddResult(this, null)
    val parents = uids.mapNotNull { parentOf(it) }.distinct()
    if (parents.size != 1) return AddResult(this, null)
    val parent = parents.first()

    val childrenToWrap = parent.children.filter { it.uid in uids }
    if (childrenToWrap.isEmpty()) return AddResult(this, null)

    val weightRegex = Regex(".*:layout_weight$")
    val cleanedChildren = childrenToWrap.map { child ->
        val cleanedAttrs = child.attributes.filterKeys { !weightRegex.matches(it) }
        child.copy(attributes = cleanedAttrs)
    }

    val containerAttrs = linkedMapOf(
        "android:id" to freshId(containerTag),
        "android:layout_width" to "match_parent",
        "android:layout_height" to "wrap_content",
    )
    val containerNode = LayoutNode(
        uid = nextUid,
        tag = containerTag,
        attributes = containerAttrs,
        children = cleanedChildren,
        isNew = true,
    )

    val newParentChildren = ArrayList<LayoutNode>()
    var containerInserted = false
    for (child in parent.children) {
        if (child.uid in uids) {
            if (!containerInserted) {
                newParentChildren.add(containerNode)
                containerInserted = true
            }
        } else {
            newParentChildren.add(child)
        }
    }

    val updatedRoot = root.mapNode(parent.uid) { it.copy(children = newParentChildren) }
    return AddResult(copy(root = updatedRoot, nextUid = nextUid + 1), containerNode.uid)
}

/** Löst Container [uid] auf und platziert seine Kinder an dessen Stelle im Elternteil. */
fun LayoutDocument.unwrap(uid: Int): LayoutDocument {
    if (uid == root.uid) return this
    val node = find(uid) ?: return this
    val parent = parentOf(uid) ?: return this

    val newChildren = ArrayList<LayoutNode>()
    for (child in parent.children) {
        if (child.uid == uid) {
            newChildren.addAll(node.children)
        } else {
            newChildren.add(child)
        }
    }
    val updatedRoot = root.mapNode(parent.uid) { it.copy(children = newChildren) }
    return copy(root = updatedRoot)
}

data class IncompatibleAttribute(val nodeUid: Int, val attributeName: String, val reason: String)

fun LayoutDocument.findIncompatibleChildAttributes(containerUid: Int, newTag: String): List<IncompatibleAttribute> {
    val container = find(containerUid) ?: return emptyList()
    val oldTag = container.tag
    if (oldTag == newTag) return emptyList()

    val result = ArrayList<IncompatibleAttribute>()
    val isOldLinear = oldTag == "LinearLayout"
    val isNewLinear = newTag == "LinearLayout"
    val isOldConstraint = oldTag == "ConstraintLayout" || oldTag.endsWith(".ConstraintLayout")
    val isNewConstraint = newTag == "ConstraintLayout" || newTag.endsWith(".ConstraintLayout")
    val isOldRelative = oldTag == "RelativeLayout"
    val isNewRelative = newTag == "RelativeLayout"

    for (child in container.children) {
        for (attr in child.attributes.keys) {
            if (isOldLinear && !isNewLinear && attr == "android:layout_weight") {
                result.add(IncompatibleAttribute(child.uid, attr, "layout_weight wird nur in LinearLayout unterstützt"))
            }
            if (isOldConstraint && !isNewConstraint && attr.startsWith("app:layout_constraint")) {
                result.add(IncompatibleAttribute(child.uid, attr, "Constraint-Attribute werden nur in ConstraintLayout unterstützt"))
            }
            if (isOldRelative && !isNewRelative && (attr.startsWith("android:layout_align") || attr.startsWith("android:layout_to") || attr in setOf("android:layout_below", "android:layout_above", "android:layout_centerInParent", "android:layout_centerHorizontal", "android:layout_centerVertical"))) {
                result.add(IncompatibleAttribute(child.uid, attr, "RelativeLayout-Attribute werden nur in RelativeLayout unterstützt"))
            }
        }
    }
    return result
}

/** Wandelt ein Widget in einen anderen Tag um. Optional werden inkompatible Kind-Attribute bereinigt. */
fun LayoutDocument.changeTag(uid: Int, newTag: String, removeIncompatibleAttrs: Boolean = false): LayoutDocument {
    if (find(uid) == null || newTag.isBlank()) return this
    val incompatible = if (removeIncompatibleAttrs) findIncompatibleChildAttributes(uid, newTag) else emptyList()
    val incompatibleMap = incompatible.groupBy({ it.nodeUid }, { it.attributeName })

    fun updateNode(n: LayoutNode): LayoutNode {
        val updatedTag = if (n.uid == uid) newTag else n.tag
        val updatedChildren = n.children.map { child ->
            val toRemove = incompatibleMap[child.uid]
            if (toRemove != null) {
                child.copy(attributes = child.attributes.filterKeys { it !in toRemove })
            } else {
                updateNode(child)
            }
        }
        return n.copy(tag = updatedTag, children = updatedChildren)
    }

    return copy(root = updateNode(root))
}

/** Benennt die `android:id` von [uid] um und passt alle Verweise im Dokument an. */
fun LayoutDocument.renameId(uid: Int, newId: String): LayoutDocument {
    val node = find(uid) ?: return this
    val oldFullId = node.attr("android:id") ?: return this
    val oldRaw = oldFullId.removePrefix("@+id/").removePrefix("@id/").trim()
    val newRaw = newId.removePrefix("@+id/").removePrefix("@id/").trim()
    if (newRaw.isBlank() || oldRaw == newRaw) return this

    val allNodes = flatten().map { it.first }
    val collision = allNodes.any { other ->
        other.uid != uid && other.attr("android:id")?.let {
            it.removePrefix("@+id/").removePrefix("@id/").trim() == newRaw
        } == true
    }
    if (collision) return this

    val newIdWithPrefix = "@+id/$newRaw"

    fun updateAttrs(attrs: Map<String, String>, isTargetNode: Boolean): Map<String, String> {
        val updated = LinkedHashMap<String, String>()
        for ((k, v) in attrs) {
            if (isTargetNode && k == "android:id") {
                updated[k] = newIdWithPrefix
                continue
            }
            if (k == "app:constraint_referenced_ids") {
                val parts = v.split(",").map { part ->
                    val p = part.trim()
                    val pRaw = p.removePrefix("@+id/").removePrefix("@id/")
                    if (pRaw == oldRaw) {
                        if (p.startsWith("@id/")) "@id/$newRaw"
                        else if (p.startsWith("@+id/")) "@+id/$newRaw"
                        else newRaw
                    } else {
                        part
                    }
                }
                updated[k] = parts.joinToString(",")
                continue
            }
            val vRaw = v.removePrefix("@+id/").removePrefix("@id/").trim()
            if (vRaw == oldRaw && (v.startsWith("@id/") || v.startsWith("@+id/"))) {
                val prefix = if (v.startsWith("@+id/")) "@+id/" else "@id/"
                updated[k] = "$prefix$newRaw"
            } else {
                updated[k] = v
            }
        }
        return updated
    }

    fun walk(n: LayoutNode): LayoutNode {
        val newAttrs = updateAttrs(n.attributes, n.uid == uid)
        return n.copy(attributes = newAttrs, children = n.children.map(::walk))
    }

    return copy(root = walk(root))
}

data class DanglingReference(val sourceUid: Int, val sourceTag: String, val attributeName: String, val referencedId: String)
data class RemoveWithReferencesResult(val document: LayoutDocument, val danglingReferences: List<DanglingReference>)

/** Löscht [uid] und meldet hängende Verweise; bei [clearReferences] werden diese bereinigt. */
fun LayoutDocument.removeWithReferences(uid: Int, clearReferences: Boolean = false): RemoveWithReferencesResult {
    if (uid == root.uid) return RemoveWithReferencesResult(this, emptyList())
    val targetNode = find(uid) ?: return RemoveWithReferencesResult(this, emptyList())

    val removedIds = HashSet<String>()
    fun collectIds(n: LayoutNode) {
        n.attr("android:id")?.let {
            removedIds.add(it.removePrefix("@+id/").removePrefix("@id/").trim())
        }
        n.children.forEach(::collectIds)
    }
    collectIds(targetNode)

    val dangling = ArrayList<DanglingReference>()
    val allRemaining = flatten().map { it.first }.filter { it.uid != uid && !isDescendantOf(it.uid, uid) }

    for (node in allRemaining) {
        for ((k, v) in node.attributes) {
            if (k == "app:constraint_referenced_ids") {
                val parts = v.split(",").map { it.trim().removePrefix("@+id/").removePrefix("@id/") }
                for (p in parts) {
                    if (p in removedIds) {
                        dangling.add(DanglingReference(node.uid, node.tag, k, p))
                    }
                }
            } else if (v.startsWith("@id/") || v.startsWith("@+id/")) {
                val raw = v.removePrefix("@+id/").removePrefix("@id/").trim()
                if (raw in removedIds) {
                    dangling.add(DanglingReference(node.uid, node.tag, k, raw))
                }
            }
        }
    }

    var newDoc = remove(uid)
    if (clearReferences && dangling.isNotEmpty()) {
        val danglingByNode = dangling.groupBy({ it.sourceUid }, { it.attributeName })
        fun cleanNode(n: LayoutNode): LayoutNode {
            val toRemove = danglingByNode[n.uid]?.toSet() ?: emptySet()
            val newAttrs = LinkedHashMap<String, String>()
            for ((k, v) in n.attributes) {
                if (k in toRemove) {
                    if (k == "app:constraint_referenced_ids") {
                        val remainingParts = v.split(",").map { it.trim() }.filter {
                            it.removePrefix("@+id/").removePrefix("@id/") !in removedIds
                        }
                        if (remainingParts.isNotEmpty()) {
                            newAttrs[k] = remainingParts.joinToString(",")
                        }
                    }
                } else {
                    newAttrs[k] = v
                }
            }
            return n.copy(attributes = newAttrs, children = n.children.map(::cleanNode))
        }
        newDoc = newDoc.copy(root = cleanNode(newDoc.root))
    }

    return RemoveWithReferencesResult(newDoc, dangling)
}

fun LayoutDocument.removeNodes(uids: Set<Int>): LayoutDocument {
    if (uids.isEmpty() || root.uid in uids) return this
    fun strip(n: LayoutNode): LayoutNode =
        n.copy(children = n.children.filter { it.uid !in uids }.map(::strip))
    return copy(root = strip(root))
}

fun LayoutDocument.duplicateNodes(uids: Set<Int>): AddResult {
    if (uids.isEmpty() || root.uid in uids) return AddResult(this, null)
    var current = this
    var lastNewUid: Int? = null
    for (uid in uids) {
        val res = current.duplicate(uid)
        current = res.document
        if (res.newUid != null) lastNewUid = res.newUid
    }
    return AddResult(current, lastNewUid)
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
        return LayoutNode(self, n.tag, newAttrs, n.children.map(::cloneUnique), isNew = true)
    }
    val clone = cloneUnique(original)
    val updated = root.mapNode(parent.uid) { p ->
        val index = p.children.indexOfFirst { it.uid == uid }
        p.copy(children = p.children.toMutableList().apply { add(index + 1, clone) })
    }
    return AddResult(copy(root = updated, nextUid = counter), clone.uid)
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
