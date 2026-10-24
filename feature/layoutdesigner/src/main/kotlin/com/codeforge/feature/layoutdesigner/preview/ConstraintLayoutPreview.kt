/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Echte Darstellung von androidx.constraintlayout.widget.ConstraintLayout mittels
 * androidx.constraintlayout.compose.ConstraintLayout und ConstraintSet.
 */
package com.codeforge.feature.layoutdesigner.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import com.codeforge.feature.layoutdesigner.model.ConstraintAnchor
import com.codeforge.feature.layoutdesigner.model.ConstraintChainStyle
import com.codeforge.feature.layoutdesigner.model.ConstraintMapper
import com.codeforge.feature.layoutdesigner.model.ConstraintTarget
import com.codeforge.feature.layoutdesigner.model.DimensionSpec
import com.codeforge.feature.layoutdesigner.model.LayoutNode

@Composable
internal fun ConstraintView(
    container: LayoutNode,
    ctx: PreviewContext,
    modifier: Modifier = Modifier,
) {
    val model = remember(container) { ConstraintMapper.mapContainer(container) }

    val constraintSet = remember(model) {
        ConstraintSet {
            val refMap = model.children.associate { it.uid to createRefFor("n_${it.uid}") }

            // 1. Guidelines
            for (child in model.children) {
                val gl = child.guideline ?: continue
                val ref = refMap[child.uid] ?: continue
                if (gl.isVertical) {
                    when {
                        gl.beginDp != null -> createGuidelineFromStart(gl.beginDp.dp)
                        gl.endDp != null -> createGuidelineFromEnd(gl.endDp.dp)
                        gl.percent != null -> createGuidelineFromStart(gl.percent)
                        else -> createGuidelineFromStart(0.5f)
                    }
                } else {
                    when {
                        gl.beginDp != null -> createGuidelineFromTop(gl.beginDp.dp)
                        gl.endDp != null -> createGuidelineFromBottom(gl.endDp.dp)
                        gl.percent != null -> createGuidelineFromTop(gl.percent)
                        else -> createGuidelineFromTop(0.5f)
                    }
                }
            }

            // 2. Barriers
            for (child in model.children) {
                val b = child.barrier ?: continue
                val ref = refMap[child.uid] ?: continue
                val refs = b.referencedIds.mapNotNull { id ->
                    model.children.find { it.id == id }?.let { refMap[it.uid] }
                }
                if (refs.isNotEmpty()) {
                    when (b.direction.lowercase()) {
                        "start", "left" -> createStartBarrier(*refs.toTypedArray())
                        "end", "right" -> createEndBarrier(*refs.toTypedArray())
                        "top" -> createTopBarrier(*refs.toTypedArray())
                        "bottom" -> createBottomBarrier(*refs.toTypedArray())
                    }
                }
            }

            // 3. Child Constraints
            for (child in model.children) {
                if (child.guideline != null || child.barrier != null || child.group != null) continue
                val ref = refMap[child.uid] ?: continue

                constrain(ref) {
                    // Maße
                    when (val w = child.widthSpec) {
                        DimensionSpec.Wrap -> width = Dimension.wrapContent
                        DimensionSpec.MatchParent -> width = Dimension.matchParent
                        is DimensionSpec.Fixed -> width = Dimension.value(w.dp.dp)
                        is DimensionSpec.MatchConstraints -> {
                            width = when {
                                w.percent != null -> Dimension.percent(w.percent)
                                w.ratio != null -> Dimension.ratio(w.ratio)
                                else -> Dimension.fillToConstraints
                            }
                        }
                    }
                    when (val h = child.heightSpec) {
                        DimensionSpec.Wrap -> height = Dimension.wrapContent
                        DimensionSpec.MatchParent -> height = Dimension.matchParent
                        is DimensionSpec.Fixed -> height = Dimension.value(h.dp.dp)
                        is DimensionSpec.MatchConstraints -> {
                            height = when {
                                h.percent != null -> Dimension.percent(h.percent)
                                h.ratio != null -> Dimension.ratio(h.ratio)
                                else -> Dimension.fillToConstraints
                            }
                        }
                    }

                    // Anchors / Links
                    for ((srcAnchor, link) in child.links) {
                        val targetAnchor = link.targetAnchor
                        val margin = link.marginDp.dp
                        val goneMargin = link.goneMarginDp?.dp ?: 0.dp

                        val targetRef = when (val t = link.target) {
                            ConstraintTarget.Parent -> parent
                            is ConstraintTarget.Sibling -> model.children.find { it.id == t.id }?.let { refMap[it.uid] }
                        } ?: continue

                        when (srcAnchor) {
                            ConstraintAnchor.START -> {
                                val targetLine = if (targetAnchor == ConstraintAnchor.END) targetRef.end else targetRef.start
                                start.linkTo(targetLine, margin, goneMargin)
                            }
                            ConstraintAnchor.END -> {
                                val targetLine = if (targetAnchor == ConstraintAnchor.START) targetRef.start else targetRef.end
                                end.linkTo(targetLine, margin, goneMargin)
                            }
                            ConstraintAnchor.TOP -> {
                                val targetLine = if (targetAnchor == ConstraintAnchor.BOTTOM) targetRef.bottom else targetRef.top
                                top.linkTo(targetLine, margin, goneMargin)
                            }
                            ConstraintAnchor.BOTTOM -> {
                                val targetLine = if (targetAnchor == ConstraintAnchor.TOP) targetRef.top else targetRef.bottom
                                bottom.linkTo(targetLine, margin, goneMargin)
                            }
                            ConstraintAnchor.BASELINE -> {
                                baseline.linkTo(targetRef.baseline, margin, goneMargin)
                            }
                            else -> {}
                        }
                    }

                    horizontalBias = child.horizontalBias
                    verticalBias = child.verticalBias
                }
            }
        }
    }

    Box(modifier = modifier) {
        ConstraintLayout(
            constraintSet = constraintSet,
            modifier = Modifier.fillMaxSize(),
        ) {
            container.children.forEach { child ->
                val simpleTag = child.tag.substringAfterLast('.')
                if (simpleTag == "Guideline") {
                    // Visuelle Guideline-Linie zeichnen
                    GuidelineView(child, Modifier.layoutId("n_${child.uid}"))
                } else if (simpleTag != "Barrier" && simpleTag != "Group") {
                    NodeView(child, ctx, Modifier.layoutId("n_${child.uid}"))
                }
            }
        }

        // Warnungen für ConstraintLayout (z. B. fehlende Constraints oder Zyklen)
        if (model.warnings.isNotEmpty()) {
            Text(
                text = "${model.warnings.size} Constraint-Warnung(en)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun GuidelineView(child: LayoutNode, modifier: Modifier) {
    val isVert = child.attr("android:orientation") == "vertical"
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color(0xFF00ACC1)
        val effect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        if (isVert) {
            drawLine(
                color = lineColor,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = effect,
            )
        } else {
            drawLine(
                color = lineColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = effect,
            )
        }
    }
}
