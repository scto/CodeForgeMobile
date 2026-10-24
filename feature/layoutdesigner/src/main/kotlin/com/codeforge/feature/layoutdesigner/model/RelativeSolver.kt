/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reiner Kotlin-Solver für RelativeLayout (ohne Android-/Compose-Abhängigkeit).
 * Ermittelt Abhängigkeiten zwischen Geschwistern, sortiert sie topologisch und erkennt Zyklen.
 */
package com.codeforge.feature.layoutdesigner.model

data class RelativeNodeSpec(
    val uid: Int,
    val id: String?,
    val node: LayoutNode,
    val dependencies: Set<String>,
    val alignParentStart: Boolean = false,
    val alignParentEnd: Boolean = false,
    val alignParentTop: Boolean = false,
    val alignParentBottom: Boolean = false,
    val centerInParent: Boolean = false,
    val centerHorizontal: Boolean = false,
    val centerVertical: Boolean = false,
    val belowId: String? = null,
    val aboveId: String? = null,
    val toStartOfId: String? = null,
    val toEndOfId: String? = null,
    val alignStartId: String? = null,
    val alignEndId: String? = null,
    val alignTopId: String? = null,
    val alignBottomId: String? = null,
)

data class RelativeSolveResult(
    val orderedNodes: List<RelativeNodeSpec>,
    val hasCycle: Boolean,
    val cycleNodes: List<String> = emptyList(),
    val missingTargets: List<String> = emptyList(),
)

object RelativeSolver {

    private val relativeTargetAttrs = listOf(
        "android:layout_below",
        "android:layout_above",
        "android:layout_toStartOf",
        "android:layout_toEndOf",
        "android:layout_toLeftOf",
        "android:layout_toRightOf",
        "android:layout_alignStart",
        "android:layout_alignEnd",
        "android:layout_alignTop",
        "android:layout_alignBottom",
        "android:layout_alignBaseline",
    )

    fun solve(container: LayoutNode): RelativeSolveResult {
        val siblingIds = container.children.mapNotNull { it.attr("android:id")?.let(::stripId) }.toSet()
        val specs = container.children.map { child -> mapChild(child) }
        val idToSpec = specs.filter { it.id != null }.associateBy { it.id!! }

        val missing = mutableListOf<String>()
        for (spec in specs) {
            for (dep in spec.dependencies) {
                if (dep !in siblingIds) {
                    missing.add(dep)
                }
            }
        }

        // Topologische Sortierung via Kahn's Algorithmus
        val inDegree = mutableMapOf<String, Int>()
        val adj = mutableMapOf<String, MutableList<String>>()

        for (id in idToSpec.keys) {
            inDegree[id] = 0
            adj[id] = mutableListOf()
        }

        for (spec in specs) {
            val fromId = spec.id ?: continue
            for (dep in spec.dependencies) {
                if (dep in idToSpec) {
                    // dep must come before fromId (dep -> fromId)
                    adj.getOrPut(dep) { mutableListOf() }.add(fromId)
                    inDegree[fromId] = (inDegree[fromId] ?: 0) + 1
                }
            }
        }

        val queue = ArrayDeque<String>()
        for ((id, deg) in inDegree) {
            if (deg == 0) queue.add(id)
        }

        val sortedIds = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            sortedIds.add(curr)
            for (neighbor in adj[curr] ?: emptyList()) {
                val newDeg = (inDegree[neighbor] ?: 1) - 1
                inDegree[neighbor] = newDeg
                if (newDeg == 0) {
                    queue.add(neighbor)
                }
            }
        }

        val hasCycle = sortedIds.size < idToSpec.size
        val cycleIds = if (hasCycle) {
            idToSpec.keys.filter { it !in sortedIds }
        } else {
            emptyList()
        }

        // Gesamtreihenfolge: Sortierte Knoten mit ID, gefolgt von unaufgelösten Zyklus-Knoten und Knoten ohne ID
        val resultList = mutableListOf<RelativeNodeSpec>()
        val addedUids = mutableSetOf<Int>()

        for (id in sortedIds) {
            val s = idToSpec[id] ?: continue
            resultList.add(s)
            addedUids.add(s.uid)
        }
        for (spec in specs) {
            if (spec.uid !in addedUids) {
                resultList.add(spec)
                addedUids.add(spec.uid)
            }
        }

        return RelativeSolveResult(
            orderedNodes = resultList,
            hasCycle = hasCycle,
            cycleNodes = cycleIds,
            missingTargets = missing.distinct(),
        )
    }

    private fun mapChild(child: LayoutNode): RelativeNodeSpec {
        val rawId = child.attr("android:id")
        val cleanId = rawId?.let(::stripId)

        val deps = mutableSetOf<String>()
        for (attr in relativeTargetAttrs) {
            val v = child.attr(attr) ?: continue
            deps.add(stripId(v))
        }

        val below = child.attr("android:layout_below")?.let(::stripId)
        val above = child.attr("android:layout_above")?.let(::stripId)
        val toStart = (child.attr("android:layout_toStartOf") ?: child.attr("android:layout_toLeftOf"))?.let(::stripId)
        val toEnd = (child.attr("android:layout_toEndOf") ?: child.attr("android:layout_toRightOf"))?.let(::stripId)
        val aStart = (child.attr("android:layout_alignStart") ?: child.attr("android:layout_alignLeft"))?.let(::stripId)
        val aEnd = (child.attr("android:layout_alignEnd") ?: child.attr("android:layout_alignRight"))?.let(::stripId)
        val aTop = child.attr("android:layout_alignTop")?.let(::stripId)
        val aBottom = child.attr("android:layout_alignBottom")?.let(::stripId)

        return RelativeNodeSpec(
            uid = child.uid,
            id = cleanId,
            node = child,
            dependencies = deps,
            alignParentStart = isTrue(child, "android:layout_alignParentStart", "android:layout_alignParentLeft"),
            alignParentEnd = isTrue(child, "android:layout_alignParentEnd", "android:layout_alignParentRight"),
            alignParentTop = isTrue(child, "android:layout_alignParentTop"),
            alignParentBottom = isTrue(child, "android:layout_alignParentBottom"),
            centerInParent = isTrue(child, "android:layout_centerInParent"),
            centerHorizontal = isTrue(child, "android:layout_centerHorizontal"),
            centerVertical = isTrue(child, "android:layout_centerVertical"),
            belowId = below,
            aboveId = above,
            toStartOfId = toStart,
            toEndOfId = toEnd,
            alignStartId = aStart,
            alignEndId = aEnd,
            alignTopId = aTop,
            alignBottomId = aBottom,
        )
    }

    private fun isTrue(node: LayoutNode, vararg attrs: String): Boolean {
        for (a in attrs) {
            if (node.attr(a) == "true") return true
        }
        return false
    }

    private fun stripId(id: String): String =
        id.trim().removePrefix("@+id/").removePrefix("@id/")
}
