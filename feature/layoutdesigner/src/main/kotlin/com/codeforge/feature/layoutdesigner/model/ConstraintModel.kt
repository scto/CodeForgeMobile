/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reines Kotlin-Zwischenmodell für ConstraintLayout (ohne Android-/Compose-Abhängigkeit).
 * Ermöglicht vollständige JVM-Testbarkeit von Constraint-Validierung, Warnungen und Mapping.
 */
package com.codeforge.feature.layoutdesigner.model

enum class ConstraintAnchor {
    START, END, TOP, BOTTOM, BASELINE, LEFT, RIGHT
}

sealed interface ConstraintTarget {
    data object Parent : ConstraintTarget
    data class Sibling(val id: String) : ConstraintTarget
}

data class ConstraintLink(
    val target: ConstraintTarget,
    val targetAnchor: ConstraintAnchor,
    val marginDp: Int = 0,
    val goneMarginDp: Int? = null,
)

sealed interface DimensionSpec {
    data object Wrap : DimensionSpec
    data object MatchParent : DimensionSpec
    data class Fixed(val dp: Int) : DimensionSpec
    data class MatchConstraints(
        val minDp: Int? = null,
        val maxDp: Int? = null,
        val percent: Float? = null,
        val ratio: String? = null,
    ) : DimensionSpec
}

enum class ConstraintChainStyle {
    SPREAD, SPREAD_INSIDE, PACKED
}

data class GuidelineSpec(
    val isVertical: Boolean,
    val beginDp: Int? = null,
    val endDp: Int? = null,
    val percent: Float? = null,
)

data class BarrierSpec(
    val direction: String,
    val referencedIds: List<String>,
)

data class GroupSpec(
    val referencedIds: List<String>,
    val visibility: String = "visible",
)

sealed interface ConstraintWarning {
    data class MissingHorizontalConstraint(val nodeUid: Int, val id: String?) : ConstraintWarning
    data class MissingVerticalConstraint(val nodeUid: Int, val id: String?) : ConstraintWarning
    data class UnknownTargetId(val nodeUid: Int, val missingId: String) : ConstraintWarning
    data class CircularConstraint(val cycleIds: List<String>) : ConstraintWarning
}

data class NodeConstraintSpec(
    val uid: Int,
    val id: String?,
    val tag: String,
    val links: Map<ConstraintAnchor, ConstraintLink> = emptyMap(),
    val horizontalBias: Float = 0.5f,
    val verticalBias: Float = 0.5f,
    val widthSpec: DimensionSpec = DimensionSpec.Wrap,
    val heightSpec: DimensionSpec = DimensionSpec.Wrap,
    val horizontalChainStyle: ConstraintChainStyle? = null,
    val verticalChainStyle: ConstraintChainStyle? = null,
    val horizontalWeight: Float? = null,
    val verticalWeight: Float? = null,
    val guideline: GuidelineSpec? = null,
    val barrier: BarrierSpec? = null,
    val group: GroupSpec? = null,
    val isApproximated: Boolean = false,
    val approximationReason: String? = null,
    val warnings: List<ConstraintWarning> = emptyList(),
)

data class ConstraintContainerModel(
    val containerUid: Int,
    val children: List<NodeConstraintSpec>,
    val warnings: List<ConstraintWarning> = emptyList(),
)
