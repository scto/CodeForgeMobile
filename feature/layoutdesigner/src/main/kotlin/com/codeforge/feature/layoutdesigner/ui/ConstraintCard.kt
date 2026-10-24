/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Visuelle Constraint-Karte und Steuerelemente für das Eigenschaften-Panel:
 * - Mini-Diagramm mit Status je Kante (Start, End, Top, Bottom)
 * - Bias-Schieberegler (horizontal und vertikal)
 * - Schnellaktionen: Horizontal/Vertikal zentrieren, Alle entfernen, Guidelines hinzufügen
 * - Geschwister-Auswahllisten für RelativeLayout
 */
package com.codeforge.feature.layoutdesigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.layoutdesigner.LayoutDesignerUiEvent
import com.codeforge.feature.layoutdesigner.model.ConstraintAnchor
import com.codeforge.feature.layoutdesigner.model.ConstraintTarget
import com.codeforge.feature.layoutdesigner.model.LayoutNode

@Composable
fun ConstraintCard(
    node: LayoutNode,
    parentTag: String,
    parentUid: Int,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConstraintParent = parentTag == "ConstraintLayout" || parentTag.endsWith("ConstraintLayout")
    if (!isConstraintParent) return

    val startLinked = node.attr("app:layout_constraintStart_toStartOf") != null || node.attr("app:layout_constraintStart_toEndOf") != null
    val endLinked = node.attr("app:layout_constraintEnd_toEndOf") != null || node.attr("app:layout_constraintEnd_toStartOf") != null
    val topLinked = node.attr("app:layout_constraintTop_toTopOf") != null || node.attr("app:layout_constraintTop_toBottomOf") != null
    val bottomLinked = node.attr("app:layout_constraintBottom_toBottomOf") != null || node.attr("app:layout_constraintBottom_toTopOf") != null

    val hBias = node.attr("app:layout_constraintHorizontal_bias")?.toFloatOrNull() ?: 0.5f
    val vBias = node.attr("app:layout_constraintVertical_bias")?.toFloatOrNull() ?: 0.5f

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringRes(R.string.layout_constraint_card_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // Mini-Diagramm
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Zentraler View-Kasten
                Box(
                    modifier = Modifier
                        .size(100.dp, 60.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = node.tag.substringAfterLast('.'),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                // Top Anchor
                AnchorWidget(
                    isLinked = topLinked,
                    label = "Top",
                    onToggle = {
                        if (topLinked) {
                            onEvent(LayoutDesignerUiEvent.RemoveConstraint(node.uid, ConstraintAnchor.TOP))
                        } else {
                            onEvent(LayoutDesignerUiEvent.SetConstraint(node.uid, ConstraintAnchor.TOP, ConstraintTarget.Parent, ConstraintAnchor.TOP))
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // Bottom Anchor
                AnchorWidget(
                    isLinked = bottomLinked,
                    label = "Bottom",
                    onToggle = {
                        if (bottomLinked) {
                            onEvent(LayoutDesignerUiEvent.RemoveConstraint(node.uid, ConstraintAnchor.BOTTOM))
                        } else {
                            onEvent(LayoutDesignerUiEvent.SetConstraint(node.uid, ConstraintAnchor.BOTTOM, ConstraintTarget.Parent, ConstraintAnchor.BOTTOM))
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // Start Anchor
                AnchorWidget(
                    isLinked = startLinked,
                    label = "Start",
                    onToggle = {
                        if (startLinked) {
                            onEvent(LayoutDesignerUiEvent.RemoveConstraint(node.uid, ConstraintAnchor.START))
                        } else {
                            onEvent(LayoutDesignerUiEvent.SetConstraint(node.uid, ConstraintAnchor.START, ConstraintTarget.Parent, ConstraintAnchor.START))
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                // End Anchor
                AnchorWidget(
                    isLinked = endLinked,
                    label = "End",
                    onToggle = {
                        if (endLinked) {
                            onEvent(LayoutDesignerUiEvent.RemoveConstraint(node.uid, ConstraintAnchor.END))
                        } else {
                            onEvent(LayoutDesignerUiEvent.SetConstraint(node.uid, ConstraintAnchor.END, ConstraintTarget.Parent, ConstraintAnchor.END))
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // Bias Schieberegler
            if (startLinked && endLinked) {
                Text(
                    text = "Horizontaler Bias: ${(hBias * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = hBias,
                    onValueChange = { onEvent(LayoutDesignerUiEvent.SetHorizontalBias(node.uid, it)) },
                    valueRange = 0f..1f,
                )
            }

            if (topLinked && bottomLinked) {
                Text(
                    text = "Vertikaler Bias: ${(vBias * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = vBias,
                    onValueChange = { onEvent(LayoutDesignerUiEvent.SetVerticalBias(node.uid, it)) },
                    valueRange = 0f..1f,
                )
            }

            // Schaltflächen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(LayoutDesignerUiEvent.CenterHorizontally(node.uid)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringRes(R.string.layout_center_horizontal), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onEvent(LayoutDesignerUiEvent.CenterVertically(node.uid)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringRes(R.string.layout_center_vertical), style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(LayoutDesignerUiEvent.ClearConstraints(node.uid)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringRes(R.string.layout_clear_constraints), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onEvent(LayoutDesignerUiEvent.AddGuideline(parentUid, isVertical = true)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringRes(R.string.layout_add_guideline_v), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AnchorWidget(
    isLinked: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline

    IconButton(
        onClick = onToggle,
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = if (isLinked) Icons.Filled.Link else Icons.Filled.Add,
            contentDescription = "$label Anchor",
            tint = if (isLinked) activeColor else inactiveColor,
            modifier = Modifier.size(16.dp),
        )
    }
}
