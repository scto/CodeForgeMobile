/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reines Kotlin-Zwischenmodell für ConstraintLayout (ohne Android-/Compose-Abhängigkeit).
 * Ermöglicht vollständige JVM-Testbarkeit von Constraint-Validierung, Warnungen und Mapping.
 */
package com.codeforge.feature.layoutdesigner.model

/** Ankerseiten eines Constraints. */
enum class ConstraintAnchor {
    START, END, TOP, BOTTOM, BASELINE, LEFT, RIGHT
}

/** Ziel eines Constraints. */
sealed interface ConstraintTarget {
    /** Der Elterncontainer. */
    data object Parent : ConstraintTarget
    /** Geschwisterknoten mit ID. */
    data class Sibling(val id: String) : ConstraintTarget
}

/** Verbindung eines Ankers zu einem Ziel. */
data class ConstraintLink(
    val target: ConstraintTarget,
    val targetAnchor: ConstraintAnchor,
    val marginDp: Int = 0,
    val goneMarginDp: Int? = null,
)

/** Größenangabe. */
sealed interface DimensionSpec {
    /** Inhalt umschließen. */
    data object Wrap : DimensionSpec
    /** Eltern ausfüllen. */
    data object MatchParent : DimensionSpec
    /** Feste Größe in dp. */
    data class Fixed(val dp: Int) : DimensionSpec
    /** Durch Constraints bestimmt. */
    data class MatchConstraints(
        val minDp: Int? = null,
        val maxDp: Int? = null,
        val percent: Float? = null,
        val ratio: String? = null,
    ) : DimensionSpec
}

/** Kettenstil. */
enum class ConstraintChainStyle {
    SPREAD, SPREAD_INSIDE, PACKED
}

/** Hilfslinie. */
data class GuidelineSpec(
    val isVertical: Boolean,
    val beginDp: Int? = null,
    val endDp: Int? = null,
    val percent: Float? = null,
)

/** Barriere. */
data class BarrierSpec(
    val direction: String,
    val referencedIds: List<String>,
)

/** Gruppe. */
data class GroupSpec(
    val referencedIds: List<String>,
    val visibility: String = "visible",
)

/** Warnung zu Constraints. */
sealed interface ConstraintWarning {
    /** Horizontale Constraint fehlt. */
    data class MissingHorizontalConstraint(val nodeUid: Int, val id: String?) : ConstraintWarning
    /** Vertikale Constraint fehlt. */
    data class MissingVerticalConstraint(val nodeUid: Int, val id: String?) : ConstraintWarning
    /** Ziel-ID existiert nicht. */
    data class UnknownTargetId(val nodeUid: Int, val missingId: String) : ConstraintWarning
    /** Zirkuläre Abhängigkeit. */
    data class CircularConstraint(val cycleIds: List<String>) : ConstraintWarning
}

/** Constraints eines Knotens. */
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

/** Constraint-Modell eines Containers. */
data class ConstraintContainerModel(
    val containerUid: Int,
    val children: List<NodeConstraintSpec>,
    val warnings: List<ConstraintWarning> = emptyList(),
)
