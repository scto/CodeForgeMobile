/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reiner Kotlin-Mapper zur Übersetzung von XML-Attributen eines ConstraintLayout-Containers
 * in das typisierte [ConstraintContainerModel] mit Validierung und Warnungserkennung.
 */
package com.codeforge.feature.layoutdesigner.model

import kotlin.math.roundToInt

object ConstraintMapper {

    private val supportedLinkAttrs = mapOf(
        "layout_constraintStart_toStartOf" to Pair(ConstraintAnchor.START, ConstraintAnchor.START),
        "layout_constraintStart_toEndOf" to Pair(ConstraintAnchor.START, ConstraintAnchor.END),
        "layout_constraintEnd_toStartOf" to Pair(ConstraintAnchor.END, ConstraintAnchor.START),
        "layout_constraintEnd_toEndOf" to Pair(ConstraintAnchor.END, ConstraintAnchor.END),
        "layout_constraintTop_toTopOf" to Pair(ConstraintAnchor.TOP, ConstraintAnchor.TOP),
        "layout_constraintTop_toBottomOf" to Pair(ConstraintAnchor.TOP, ConstraintAnchor.BOTTOM),
        "layout_constraintBottom_toTopOf" to Pair(ConstraintAnchor.BOTTOM, ConstraintAnchor.TOP),
        "layout_constraintBottom_toBottomOf" to Pair(ConstraintAnchor.BOTTOM, ConstraintAnchor.BOTTOM),
        "layout_constraintBaseline_toBaselineOf" to Pair(ConstraintAnchor.BASELINE, ConstraintAnchor.BASELINE),
        "layout_constraintLeft_toLeftOf" to Pair(ConstraintAnchor.START, ConstraintAnchor.START),
        "layout_constraintLeft_toRightOf" to Pair(ConstraintAnchor.START, ConstraintAnchor.END),
        "layout_constraintRight_toLeftOf" to Pair(ConstraintAnchor.END, ConstraintAnchor.START),
        "layout_constraintRight_toRightOf" to Pair(ConstraintAnchor.END, ConstraintAnchor.END),
    )

    fun mapContainer(container: LayoutNode): ConstraintContainerModel {
        val siblingIds = container.children.mapNotNull { it.attr("android:id")?.let(::stripId) }.toSet()
        val mappedChildren = mutableListOf<NodeConstraintSpec>()
        val allWarnings = mutableListOf<ConstraintWarning>()

        for (child in container.children) {
            val childSpec = mapChild(child, siblingIds)
            mappedChildren.add(childSpec)
            allWarnings.addAll(childSpec.warnings)
        }

        // Zyklen-Erkennung über die Kind-Abhängigkeiten
        val cycleWarnings = detectCycles(mappedChildren)
        allWarnings.addAll(cycleWarnings)

        return ConstraintContainerModel(
            containerUid = container.uid,
            children = mappedChildren,
            warnings = allWarnings,
        )
    }

    private fun mapChild(child: LayoutNode, validSiblingIds: Set<String>): NodeConstraintSpec {
        val simpleTag = child.tag.substringAfterLast('.')
        val rawId = child.attr("android:id")
        val cleanId = rawId?.let(::stripId)
        val warnings = mutableListOf<ConstraintWarning>()

        // Nicht unterstützte Spezialtypen als Näherung markieren
        val isSpecialApproximated = simpleTag in setOf("Flow", "Layer", "MotionLayout")
        val approxReason = if (isSpecialApproximated) "$simpleTag wird als Näherung dargestellt" else null

        // 1. Links ermitteln
        val links = mutableMapOf<ConstraintAnchor, ConstraintLink>()
        for ((attrSuffix, anchorPair) in supportedLinkAttrs) {
            val attrVal = child.attr("app:$attrSuffix") ?: child.attr("android:$attrSuffix") ?: continue
            val (srcAnchor, targetAnchor) = anchorPair
            val target = parseTarget(attrVal)
            val margin = getMarginForAnchor(child, srcAnchor)
            val goneMargin = getGoneMarginForAnchor(child, srcAnchor)

            links[srcAnchor] = ConstraintLink(
                target = target,
                targetAnchor = targetAnchor,
                marginDp = margin,
                goneMarginDp = goneMargin,
            )

            if (target is ConstraintTarget.Sibling && target.id !in validSiblingIds) {
                warnings.add(ConstraintWarning.UnknownTargetId(child.uid, target.id))
            }
        }

        // 2. Guidelines, Barriers, Groups
        val isGuideline = simpleTag == "Guideline"
        val isBarrier = simpleTag == "Barrier"
        val isGroup = simpleTag == "Group"

        val guidelineSpec = if (isGuideline) {
            val orient = child.attr("android:orientation") ?: "horizontal"
            val begin = parseDimDp(child, "app:layout_constraintGuide_begin", "android:layout_constraintGuide_begin")
            val end = parseDimDp(child, "app:layout_constraintGuide_end", "android:layout_constraintGuide_end")
            val percent = parseFloat(child, "app:layout_constraintGuide_percent", "android:layout_constraintGuide_percent")
            GuidelineSpec(orient == "vertical", begin, end, percent)
        } else null

        val barrierSpec = if (isBarrier) {
            val dir = child.attr("app:barrierDirection") ?: "left"
            val refs = child.attr("app:constraint_referenced_ids")?.split(',')?.map { it.trim().removePrefix("@+id/").removePrefix("@id/") } ?: emptyList()
            BarrierSpec(dir, refs)
        } else null

        val groupSpec = if (isGroup) {
            val refs = child.attr("app:constraint_referenced_ids")?.split(',')?.map { it.trim().removePrefix("@+id/").removePrefix("@id/") } ?: emptyList()
            val vis = child.attr("android:visibility") ?: "visible"
            GroupSpec(refs, vis)
        } else null

        // 3. Biases & Ketten
        val hBias = parseFloat(child, "app:layout_constraintHorizontal_bias", "android:layout_constraintHorizontal_bias") ?: 0.5f
        val vBias = parseFloat(child, "app:layout_constraintVertical_bias", "android:layout_constraintVertical_bias") ?: 0.5f

        val hChain = parseChainStyle(child.attr("app:layout_constraintHorizontal_chainStyle"))
        val vChain = parseChainStyle(child.attr("app:layout_constraintVertical_chainStyle"))
        val hWeight = parseFloat(child, "app:layout_constraintHorizontal_weight")
        val vWeight = parseFloat(child, "app:layout_constraintVertical_weight")

        // 4. Dimension Specs
        val wSpec = parseDimensionSpec(child, "android:layout_width", "app:layout_constraintWidth_percent")
        val hSpec = parseDimensionSpec(child, "android:layout_height", "app:layout_constraintHeight_percent")

        // 5. Fehlende Constraints prüfen (außer für Guidelines, Barriers, Groups)
        if (!isGuideline && !isBarrier && !isGroup) {
            val hasH = links.containsKey(ConstraintAnchor.START) || links.containsKey(ConstraintAnchor.END)
            val hasV = links.containsKey(ConstraintAnchor.TOP) || links.containsKey(ConstraintAnchor.BOTTOM) || links.containsKey(ConstraintAnchor.BASELINE)
            if (!hasH) warnings.add(ConstraintWarning.MissingHorizontalConstraint(child.uid, cleanId))
            if (!hasV) warnings.add(ConstraintWarning.MissingVerticalConstraint(child.uid, cleanId))
        }

        return NodeConstraintSpec(
            uid = child.uid,
            id = cleanId,
            tag = child.tag,
            links = links,
            horizontalBias = hBias,
            verticalBias = vBias,
            widthSpec = wSpec,
            heightSpec = hSpec,
            horizontalChainStyle = hChain,
            verticalChainStyle = vChain,
            horizontalWeight = hWeight,
            verticalWeight = vWeight,
            guideline = guidelineSpec,
            barrier = barrierSpec,
            group = groupSpec,
            isApproximated = isSpecialApproximated,
            approximationReason = approxReason,
            warnings = warnings,
        )
    }

    private fun detectCycles(children: List<NodeConstraintSpec>): List<ConstraintWarning> {
        val warnings = mutableListOf<ConstraintWarning>()
        val idToChild = children.filter { it.id != null }.associateBy { it.id!! }
        val adj = mutableMapOf<String, MutableSet<String>>()

        for (child in children) {
            val fromId = child.id ?: continue
            val targets = child.links.values.mapNotNull {
                (it.target as? ConstraintTarget.Sibling)?.id
            }
            adj.getOrPut(fromId) { mutableSetOf() }.addAll(targets)
        }

        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val cyclePath = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            visited.add(node)
            recStack.add(node)
            cyclePath.add(node)

            for (neighbor in adj[node] ?: emptySet()) {
                if (neighbor !in visited) {
                    if (dfs(neighbor)) return true
                } else if (neighbor in recStack) {
                    cyclePath.add(neighbor)
                    return true
                }
            }

            recStack.remove(node)
            cyclePath.removeAt(cyclePath.lastIndex)
            return false
        }

        for (id in idToChild.keys) {
            if (id !in visited) {
                cyclePath.clear()
                if (dfs(id)) {
                    val cycleStart = cyclePath.last()
                    val cycleIndex = cyclePath.indexOf(cycleStart)
                    val realCycle = cyclePath.subList(cycleIndex, cyclePath.size)
                    warnings.add(ConstraintWarning.CircularConstraint(realCycle.toList()))
                    break
                }
            }
        }

        return warnings
    }

    private fun parseTarget(value: String): ConstraintTarget {
        val clean = value.trim()
        return if (clean == "parent") {
            ConstraintTarget.Parent
        } else {
            ConstraintTarget.Sibling(stripId(clean))
        }
    }

    private fun stripId(id: String): String =
        id.trim().removePrefix("@+id/").removePrefix("@id/")

    private fun getMarginForAnchor(child: LayoutNode, anchor: ConstraintAnchor): Int {
        val attrName = when (anchor) {
            ConstraintAnchor.START -> "android:layout_marginStart"
            ConstraintAnchor.END -> "android:layout_marginEnd"
            ConstraintAnchor.TOP -> "android:layout_marginTop"
            ConstraintAnchor.BOTTOM -> "android:layout_marginBottom"
            else -> return 0
        }
        val fallback = when (anchor) {
            ConstraintAnchor.START -> "android:layout_marginLeft"
            ConstraintAnchor.END -> "android:layout_marginRight"
            else -> null
        }
        val raw = child.attr(attrName) ?: fallback?.let { child.attr(it) }
        return parseDimension(raw)?.roundToInt() ?: 0
    }

    private fun getGoneMarginForAnchor(child: LayoutNode, anchor: ConstraintAnchor): Int? {
        val attrName = when (anchor) {
            ConstraintAnchor.START -> "app:layout_goneMarginStart"
            ConstraintAnchor.END -> "app:layout_goneMarginEnd"
            ConstraintAnchor.TOP -> "app:layout_goneMarginTop"
            ConstraintAnchor.BOTTOM -> "app:layout_goneMarginBottom"
            else -> return null
        }
        val raw = child.attr(attrName) ?: return null
        return parseDimension(raw)?.roundToInt()
    }

    private fun parseDimensionSpec(child: LayoutNode, attrName: String, percentAttrName: String): DimensionSpec {
        val raw = child.attr(attrName)?.trim()
        val ratio = child.attr("app:layout_constraintDimensionRatio")
        val percent = parseFloat(child, percentAttrName)

        return when (raw) {
            "0dp", "0dip" -> DimensionSpec.MatchConstraints(percent = percent, ratio = ratio)
            "match_parent", "fill_parent" -> DimensionSpec.MatchParent
            "wrap_content", null, "" -> DimensionSpec.Wrap
            else -> {
                val dp = parseDimension(raw)?.roundToInt()
                if (dp != null) DimensionSpec.Fixed(dp) else DimensionSpec.Wrap
            }
        }
    }

    private fun parseDimDp(child: LayoutNode, vararg attrs: String): Int? {
        for (a in attrs) {
            val v = child.attr(a) ?: continue
            val d = parseDimension(v) ?: continue
            return d.roundToInt()
        }
        return null
    }

    private fun parseFloat(child: LayoutNode, vararg attrs: String): Float? {
        for (a in attrs) {
            val v = child.attr(a) ?: continue
            val f = v.toFloatOrNull() ?: continue
            return f
        }
        return null
    }

    private fun parseChainStyle(value: String?): ConstraintChainStyle? = when (value?.trim()) {
        "spread" -> ConstraintChainStyle.SPREAD
        "spread_inside" -> ConstraintChainStyle.SPREAD_INSIDE
        "packed" -> ConstraintChainStyle.PACKED
        else -> null
    }
}
