/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reine Kotlin-Berechnung von Drag & Drop-Zielen auf der JVM ohne Compose/Android-Abhängigkeit.
 */
package com.codeforge.feature.layoutdesigner.dnd

import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.canDrop
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.parentOf
import kotlin.math.roundToInt

data class DesignerRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

data class DesignerOffset(val x: Float, val y: Float)

sealed interface DropIndicator {
    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
    ) : DropIndicator

    data class HighlightContainer(val rect: DesignerRect) : DropIndicator
}

data class DropTarget(
    val parentUid: Int,
    val index: Int,
    val indicator: DropIndicator,
    val verdict: DropVerdict,
    val suggestedAttributes: Map<String, String> = emptyMap(),
)

object DropResolver {

    fun resolveDrop(
        document: LayoutDocument,
        boundsMap: Map<Int, DesignerRect>,
        pointer: DesignerOffset,
        payload: DragPayload,
        setGravityOnDrop: Boolean = false,
        densityDpFactor: Float = 1f,
    ): DropTarget? {
        val matchingNodes = boundsMap.filter { (_, rect) -> rect.contains(pointer.x, pointer.y) }
        if (matchingNodes.isEmpty()) return null

        // Nach Baumtiefe absteigend sortieren -> tiefster Knoten gewinnt
        val depths = document.flatten().associate { it.first.uid to it.second }
        val sortedUids = matchingNodes.keys.sortedByDescending { depths[it] ?: 0 }

        for (deepestUid in sortedUids) {
            val node = document.find(deepestUid) ?: continue
            val rect = boundsMap[deepestUid] ?: continue

            if (node.isContainer) {
                return resolveContainerDrop(
                    document = document,
                    container = node,
                    containerRect = rect,
                    boundsMap = boundsMap,
                    pointer = pointer,
                    payload = payload,
                    setGravityOnDrop = setGravityOnDrop,
                    densityDpFactor = densityDpFactor,
                )
            } else {
                // Über einem Blatt: Entscheidung vor/nach Blatt im Elternteil
                val parent = document.parentOf(node.uid) ?: continue
                val parentRect = boundsMap[parent.uid] ?: continue
                return resolveLeafDrop(
                    document = document,
                    leaf = node,
                    leafRect = rect,
                    parent = parent,
                    parentRect = parentRect,
                    pointer = pointer,
                    payload = payload,
                )
            }
        }
        return null
    }

    private fun resolveContainerDrop(
        document: LayoutDocument,
        container: com.codeforge.feature.layoutdesigner.model.LayoutNode,
        containerRect: DesignerRect,
        boundsMap: Map<Int, DesignerRect>,
        pointer: DesignerOffset,
        payload: DragPayload,
        setGravityOnDrop: Boolean,
        densityDpFactor: Float,
    ): DropTarget {
        val verdict = document.canDrop(payload, container.uid)
        val tag = container.tag

        return when {
            tag == "LinearLayout" -> {
                val isVertical = container.attr("android:orientation") != "horizontal"
                val children = container.children
                if (children.isEmpty()) {
                    val indicator = if (isVertical) {
                        DropIndicator.Line(containerRect.left + 8f, containerRect.top + 8f, containerRect.right - 8f, containerRect.top + 8f)
                    } else {
                        DropIndicator.Line(containerRect.left + 8f, containerRect.top + 8f, containerRect.left + 8f, containerRect.bottom - 8f)
                    }
                    DropTarget(container.uid, 0, indicator, verdict)
                } else if (isVertical) {
                    var insertIndex = children.size
                    var lineY = containerRect.bottom
                    for (i in children.indices) {
                        val childRect = boundsMap[children[i].uid] ?: continue
                        if (pointer.y < childRect.centerY) {
                            insertIndex = i
                            lineY = childRect.top
                            break
                        }
                    }
                    val indicator = DropIndicator.Line(containerRect.left, lineY, containerRect.right, lineY)
                    DropTarget(container.uid, insertIndex, indicator, verdict)
                } else {
                    var insertIndex = children.size
                    var lineX = containerRect.right
                    for (i in children.indices) {
                        val childRect = boundsMap[children[i].uid] ?: continue
                        if (pointer.x < childRect.centerX) {
                            insertIndex = i
                            lineX = childRect.left
                            break
                        }
                    }
                    val indicator = DropIndicator.Line(lineX, containerRect.top, lineX, containerRect.bottom)
                    DropTarget(container.uid, insertIndex, indicator, verdict)
                }
            }

            tag in setOf("FrameLayout", "CoordinatorLayout") -> {
                val suggested = LinkedHashMap<String, String>()
                val applyGravity = payload is DragPayload.NewWidget || setGravityOnDrop
                if (applyGravity) {
                    val gravity = calculateGravity(containerRect, pointer)
                    suggested["android:layout_gravity"] = gravity
                }
                DropTarget(
                    parentUid = container.uid,
                    index = container.children.size,
                    indicator = DropIndicator.HighlightContainer(containerRect),
                    verdict = verdict,
                    suggestedAttributes = suggested,
                )
            }

            tag == "ConstraintLayout" || tag.endsWith(".ConstraintLayout") -> {
                val suggested = LinkedHashMap<String, String>()
                val dxPx = (pointer.x - containerRect.left).coerceAtLeast(0f)
                val dyPx = (pointer.y - containerRect.top).coerceAtLeast(0f)
                val dxDp = (dxPx / densityDpFactor).roundToInt()
                val dyDp = (dyPx / densityDpFactor).roundToInt()
                val gridX = (Math.round(dxDp / 8.0) * 8).toInt()
                val gridY = (Math.round(dyDp / 8.0) * 8).toInt()

                suggested["app:layout_constraintStart_toStartOf"] = "parent"
                suggested["app:layout_constraintTop_toTopOf"] = "parent"
                if (gridX > 0) suggested["android:layout_marginStart"] = "${gridX}dp"
                if (gridY > 0) suggested["android:layout_marginTop"] = "${gridY}dp"

                DropTarget(
                    parentUid = container.uid,
                    index = container.children.size,
                    indicator = DropIndicator.HighlightContainer(containerRect),
                    verdict = verdict,
                    suggestedAttributes = suggested,
                )
            }

            tag == "RelativeLayout" -> {
                val suggested = LinkedHashMap<String, String>()
                val dxPx = (pointer.x - containerRect.left).coerceAtLeast(0f)
                val dyPx = (pointer.y - containerRect.top).coerceAtLeast(0f)
                val dxDp = (dxPx / densityDpFactor).roundToInt()
                val dyDp = (dyPx / densityDpFactor).roundToInt()
                val gridX = (Math.round(dxDp / 8.0) * 8).toInt()
                val gridY = (Math.round(dyDp / 8.0) * 8).toInt()

                suggested["android:layout_alignParentStart"] = "true"
                suggested["android:layout_alignParentTop"] = "true"
                if (gridX > 0) suggested["android:layout_marginStart"] = "${gridX}dp"
                if (gridY > 0) suggested["android:layout_marginTop"] = "${gridY}dp"

                DropTarget(
                    parentUid = container.uid,
                    index = container.children.size,
                    indicator = DropIndicator.HighlightContainer(containerRect),
                    verdict = verdict,
                    suggestedAttributes = suggested,
                )
            }

            else -> {
                DropTarget(
                    parentUid = container.uid,
                    index = container.children.size,
                    indicator = DropIndicator.HighlightContainer(containerRect),
                    verdict = verdict,
                )
            }
        }
    }

    private fun resolveLeafDrop(
        document: LayoutDocument,
        leaf: com.codeforge.feature.layoutdesigner.model.LayoutNode,
        leafRect: DesignerRect,
        parent: com.codeforge.feature.layoutdesigner.model.LayoutNode,
        parentRect: DesignerRect,
        pointer: DesignerOffset,
        payload: DragPayload,
    ): DropTarget {
        val verdict = document.canDrop(payload, parent.uid)
        val childIndex = parent.children.indexOfFirst { it.uid == leaf.uid }.coerceAtLeast(0)

        if (parent.tag == "LinearLayout") {
            val isVertical = parent.attr("android:orientation") != "horizontal"
            if (isVertical) {
                val third = leafRect.height / 3f
                return if (pointer.y < leafRect.top + third) {
                    DropTarget(
                        parentUid = parent.uid,
                        index = childIndex,
                        indicator = DropIndicator.Line(parentRect.left, leafRect.top, parentRect.right, leafRect.top),
                        verdict = verdict,
                    )
                } else {
                    DropTarget(
                        parentUid = parent.uid,
                        index = childIndex + 1,
                        indicator = DropIndicator.Line(parentRect.left, leafRect.bottom, parentRect.right, leafRect.bottom),
                        verdict = verdict,
                    )
                }
            } else {
                val third = leafRect.width / 3f
                return if (pointer.x < leafRect.left + third) {
                    DropTarget(
                        parentUid = parent.uid,
                        index = childIndex,
                        indicator = DropIndicator.Line(leafRect.left, parentRect.top, leafRect.left, parentRect.bottom),
                        verdict = verdict,
                    )
                } else {
                    DropTarget(
                        parentUid = parent.uid,
                        index = childIndex + 1,
                        indicator = DropIndicator.Line(leafRect.right, parentRect.top, leafRect.right, parentRect.bottom),
                        verdict = verdict,
                    )
                }
            }
        }

        return DropTarget(
            parentUid = parent.uid,
            index = parent.children.size,
            indicator = DropIndicator.HighlightContainer(parentRect),
            verdict = verdict,
        )
    }

    private fun calculateGravity(containerRect: DesignerRect, pointer: DesignerOffset): String {
        val relX = (pointer.x - containerRect.left) / containerRect.width.coerceAtLeast(1f)
        val relY = (pointer.y - containerRect.top) / containerRect.height.coerceAtLeast(1f)

        val horiz = when {
            relX < 0.33f -> "start"
            relX > 0.66f -> "end"
            else -> "center_horizontal"
        }
        val vert = when {
            relY < 0.33f -> "top"
            relY > 0.66f -> "bottom"
            else -> "center_vertical"
        }

        return if (horiz == "center_horizontal" && vert == "center_vertical") "center" else "$vert|$horiz"
    }
}
