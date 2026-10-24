package com.codeforge.feature.layoutdesigner.lint

import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.parentOf
import com.codeforge.feature.layoutdesigner.model.parseDimension
import com.codeforge.feature.layoutdesigner.model.setAttribute
import com.codeforge.feature.layoutdesigner.model.unwrap
import com.codeforge.feature.layoutdesigner.res.ResourceRepository

enum class IssueSeverity {
    ERROR,
    WARNING,
    INFO
}

sealed interface QuickFix {
    val title: String

    data class SetAttribute(
        override val title: String,
        val targetUid: Int,
        val name: String,
        val value: String
    ) : QuickFix

    data class RemoveAttribute(
        override val title: String,
        val targetUid: Int,
        val name: String
    ) : QuickFix

    data class ReplaceAttribute(
        override val title: String,
        val targetUid: Int,
        val oldName: String,
        val newName: String,
        val newValue: String? = null
    ) : QuickFix

    data class ExtractResource(
        override val title: String,
        val targetUid: Int,
        val attrName: String,
        val rawValue: String
    ) : QuickFix

    data class UnwrapContainer(
        override val title: String,
        val targetUid: Int
    ) : QuickFix
}

data class LayoutIssue(
    val uid: Int,
    val severity: IssueSeverity,
    val ruleId: String,
    val message: String,
    val quickFix: QuickFix? = null
)

object LayoutLinter {

    fun lint(
        document: LayoutDocument,
        resourceRepository: ResourceRepository? = null
    ): List<LayoutIssue> {
        val issues = mutableListOf<LayoutIssue>()
        val allNodes = document.flatten().map { it.first }

        checkDuplicateIds(allNodes, issues)
        checkMissingIdReferences(document, allNodes, issues)
        if (resourceRepository != null) {
            checkMissingResourceReferences(allNodes, resourceRepository, issues)
        }
        checkHardcodedText(allNodes, issues)
        checkContentDescription(allNodes, issues)
        checkTouchTargetSize(allNodes, issues)
        checkScrollViewChildren(allNodes, issues)
        checkNestedWeights(document, allNodes, issues)
        checkInefficientWeight(document, allNodes, issues)
        checkDeprecatedFillParent(allNodes, issues)
        checkPxUsage(allNodes, issues)
        checkWrongDimensionUnits(allNodes, issues)
        checkRtlAttributes(allNodes, issues)
        checkConstraintLayoutRules(document, allNodes, issues)
        checkUselessParent(document, allNodes, issues)
        checkDeepNesting(document, issues)

        return issues
    }

    /** Führt eine Schnellkorrektur aus und liefert das aktualisierte Dokument zurück. */
    fun applyQuickFix(document: LayoutDocument, quickFix: QuickFix): LayoutDocument {
        return when (quickFix) {
            is QuickFix.SetAttribute -> {
                document.setAttribute(quickFix.targetUid, quickFix.name, quickFix.value)
            }
            is QuickFix.RemoveAttribute -> {
                document.setAttribute(quickFix.targetUid, quickFix.name, null)
            }
            is QuickFix.ReplaceAttribute -> {
                val node = document.find(quickFix.targetUid) ?: return document
                val existingVal = node.attributes[quickFix.oldName]
                val newVal = quickFix.newValue ?: existingVal ?: ""
                val withoutOld = document.setAttribute(quickFix.targetUid, quickFix.oldName, null)
                withoutOld.setAttribute(quickFix.targetUid, quickFix.newName, newVal)
            }
            is QuickFix.UnwrapContainer -> {
                document.unwrap(quickFix.targetUid)
            }
            is QuickFix.ExtractResource -> {
                // Auslagern wird über UI / ViewModel behandelt
                document
            }
        }
    }

    private fun rawId(idValue: String): String {
        return idValue.removePrefix("@+id/").removePrefix("@id/")
    }

    private fun checkDuplicateIds(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        val idCounts = mutableMapOf<String, MutableList<LayoutNode>>()
        for (node in nodes) {
            val idAttr = node.attributes["android:id"] ?: continue
            val raw = rawId(idAttr)
            if (raw.isNotBlank()) {
                idCounts.getOrPut(raw) { mutableListOf() }.add(node)
            }
        }
        for ((raw, nodeList) in idCounts) {
            if (nodeList.size > 1) {
                for (node in nodeList) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.ERROR,
                            ruleId = "DuplicateId",
                            message = "Doppelte ID '@+id/$raw'"
                        )
                    )
                }
            }
        }
    }

    private fun checkMissingIdReferences(
        document: LayoutDocument,
        nodes: List<LayoutNode>,
        issues: MutableList<LayoutIssue>
    ) {
        val definedIds = nodes.mapNotNull { it.attributes["android:id"]?.let { id -> rawId(id) } }.toSet()

        val idRefAttrs = setOf(
            "android:layout_above", "android:layout_below",
            "android:layout_toStartOf", "android:layout_toEndOf",
            "android:layout_toLeftOf", "android:layout_toRightOf",
            "android:layout_alignStart", "android:layout_alignEnd",
            "android:layout_alignTop", "android:layout_alignBottom",
            "android:layout_alignBaseline", "android:layout_alignLeft", "android:layout_alignRight",
            "android:labelFor", "android:accessibilityTraversalBefore", "android:accessibilityTraversalAfter",
            "android:nextFocusDown", "android:nextFocusUp",
            "android:nextFocusLeft", "android:nextFocusRight", "android:nextFocusForward"
        )

        for (node in nodes) {
            for ((key, value) in node.attributes) {
                if (key in idRefAttrs || key.contains(":layout_constraint") && key.endsWith("toTopOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toBottomOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toStartOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toEndOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toLeftOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toRightOf") ||
                    key.contains(":layout_constraint") && key.endsWith("toBaselineOf")
                ) {
                    val target = rawId(value)
                    if (target != "parent" && target.isNotEmpty() && target !in definedIds) {
                        issues.add(
                            LayoutIssue(
                                uid = node.uid,
                                severity = IssueSeverity.ERROR,
                                ruleId = "MissingIdReference",
                                message = "Verweis auf nicht existierende ID '$value' in $key"
                            )
                        )
                    }
                } else if (key.endsWith("constraint_referenced_ids")) {
                    val refs = value.split(",").map { rawId(it.trim()) }.filter { it.isNotEmpty() }
                    for (ref in refs) {
                        if (ref != "parent" && ref !in definedIds) {
                            issues.add(
                                LayoutIssue(
                                    uid = node.uid,
                                    severity = IssueSeverity.ERROR,
                                    ruleId = "MissingIdReference",
                                    message = "Referenzierte ID '$ref' in $key nicht definiert"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkMissingResourceReferences(
        nodes: List<LayoutNode>,
        repo: ResourceRepository,
        issues: MutableList<LayoutIssue>
    ) {
        val resRegex = Regex("^@(string|color|dimen|drawable|style)/([a-zA-Z0-9_]+)$")
        for (node in nodes) {
            for ((key, value) in node.attributes) {
                val match = resRegex.find(value) ?: continue
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val exists = repo.hasResource(type, name)
                if (!exists) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "MissingResourceReference",
                            message = "Ressource '@$type/$name' nicht gefunden ($key)"
                        )
                    )
                }
            }
        }
    }

    private fun checkHardcodedText(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        val textAttrs = setOf("android:text", "android:hint", "android:contentDescription")
        for (node in nodes) {
            for (attr in textAttrs) {
                val value = node.attributes[attr] ?: continue
                if (value.isNotBlank() && !value.startsWith("@string/") && !value.startsWith("@android:string/") && !value.startsWith("?")) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "HardcodedText",
                            message = "Fest codierter Text im Attribut '$attr'",
                            quickFix = QuickFix.ExtractResource("In Ressource auslagern", node.uid, attr, value)
                        )
                    )
                }
            }
        }
    }

    private fun checkContentDescription(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        for (node in nodes) {
            val simpleTag = node.tag.substringAfterLast('.')
            if (simpleTag in listOf("ImageView", "ImageButton", "ShapeableImageView")) {
                val desc = node.attributes["android:contentDescription"]
                val important = node.attributes["android:importantForAccessibility"]
                if (desc.isNullOrBlank() && important != "no") {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "MissingContentDescription",
                            message = "Fehlende Bildbeschreibung (contentDescription) an <${node.tag}>",
                            quickFix = QuickFix.SetAttribute(
                                title = "importantForAccessibility=\"no\" setzen",
                                targetUid = node.uid,
                                name = "android:importantForAccessibility",
                                value = "no"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun checkTouchTargetSize(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        val clickableTags = setOf(
            "Button", "ImageButton", "MaterialButton", "CheckBox", "RadioButton",
            "Switch", "MaterialSwitch", "ToggleButton", "FloatingActionButton", "ExtendedFloatingActionButton"
        )
        for (node in nodes) {
            val isClickable = node.attributes["android:clickable"] == "true" ||
                node.tag.substringAfterLast('.') in clickableTags
            if (!isClickable) continue

            val w = node.attributes["android:layout_width"]
            val h = node.attributes["android:layout_height"]
            val wDp = w?.let { parseDimension(it) }
            val hDp = h?.let { parseDimension(it) }

            if ((wDp != null && wDp < 48f) || (hDp != null && hDp < 48f)) {
                val isHeightSmall = hDp != null && hDp < 48f
                val fixAttr = if (isHeightSmall) "android:layout_height" else "android:layout_width"
                issues.add(
                    LayoutIssue(
                        uid = node.uid,
                        severity = IssueSeverity.WARNING,
                        ruleId = "SmallTouchTarget",
                        message = "Klickbereich kleiner als empfohlenes Minimum von 48×48 dp (${w ?: "?"} × ${h ?: "?"})",
                        quickFix = QuickFix.SetAttribute("Auf 48dp vergrößern", node.uid, fixAttr, "48dp")
                    )
                )
            }
        }
    }

    private fun checkScrollViewChildren(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        val scrollContainers = setOf("ScrollView", "NestedScrollView", "HorizontalScrollView")
        for (node in nodes) {
            if (node.tag.substringAfterLast('.') in scrollContainers) {
                if (node.children.size > 1) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.ERROR,
                            ruleId = "ScrollViewChildren",
                            message = "<${node.tag}> darf nur genau ein direktes Kind haben (${node.children.size} vorhanden)"
                        )
                    )
                }
            }
        }
    }

    private fun checkNestedWeights(
        document: LayoutDocument,
        nodes: List<LayoutNode>,
        issues: MutableList<LayoutIssue>
    ) {
        for (node in nodes) {
            if (node.tag.substringAfterLast('.') == "LinearLayout") {
                val hasWeight = node.attributes.containsKey("android:layout_weight")
                val parent = document.parentOf(node.uid)
                val parentIsLinear = parent?.tag?.substringAfterLast('.') == "LinearLayout"
                val parentHasWeight = parent?.attributes?.containsKey("android:layout_weight") == true

                if (hasWeight && parentIsLinear && parentHasWeight) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "NestedWeights",
                            message = "Verschachtelte layout_weight können die Layout-Leistung beeinträchtigen"
                        )
                    )
                }
            }
        }
    }

    private fun checkInefficientWeight(
        document: LayoutDocument,
        nodes: List<LayoutNode>,
        issues: MutableList<LayoutIssue>
    ) {
        for (node in nodes) {
            if (node.tag.substringAfterLast('.') == "LinearLayout") {
                val orientation = node.attributes["android:orientation"] ?: "vertical"
                for (child in node.children) {
                    if (child.attributes.containsKey("android:layout_weight")) {
                        if (orientation == "vertical") {
                            val h = child.attributes["android:layout_height"]
                            if (h != null && h != "0dp") {
                                issues.add(
                                    LayoutIssue(
                                        uid = child.uid,
                                        severity = IssueSeverity.WARNING,
                                        ruleId = "InefficientWeight",
                                        message = "Verwende '0dp' für layout_height bei vertikalem layout_weight",
                                        quickFix = QuickFix.SetAttribute("layout_height auf 0dp setzen", child.uid, "android:layout_height", "0dp")
                                    )
                                )
                            }
                        } else {
                            val w = child.attributes["android:layout_width"]
                            if (w != null && w != "0dp") {
                                issues.add(
                                    LayoutIssue(
                                        uid = child.uid,
                                        severity = IssueSeverity.WARNING,
                                        ruleId = "InefficientWeight",
                                        message = "Verwende '0dp' für layout_width bei horizontalem layout_weight",
                                        quickFix = QuickFix.SetAttribute("layout_width auf 0dp setzen", child.uid, "android:layout_width", "0dp")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkDeprecatedFillParent(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        for (node in nodes) {
            for ((key, value) in node.attributes) {
                if (value == "fill_parent") {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "DeprecatedFillParent",
                            message = "'fill_parent' ist veraltet in $key, verwende 'match_parent'",
                            quickFix = QuickFix.SetAttribute("Durch 'match_parent' ersetzen", node.uid, key, "match_parent")
                        )
                    )
                }
            }
        }
    }

    private fun checkPxUsage(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        for (node in nodes) {
            for ((key, value) in node.attributes) {
                if (value.endsWith("px") && (key.contains("layout_") || key.contains("padding") || key.contains("min") || key.contains("max"))) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "PxUsage",
                            message = "Pixel-Einheit 'px' in $key vermeiden, verwende 'dp'"
                        )
                    )
                }
            }
        }
    }

    private fun checkWrongDimensionUnits(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        for (node in nodes) {
            val textSize = node.attributes["android:textSize"]
            if (textSize != null && textSize.endsWith("dp")) {
                issues.add(
                    LayoutIssue(
                        uid = node.uid,
                        severity = IssueSeverity.WARNING,
                        ruleId = "WrongDimensionUnit",
                        message = "Verwende 'sp' statt 'dp' für textSize",
                        quickFix = QuickFix.SetAttribute("In sp umwandeln", node.uid, "android:textSize", textSize.replace("dp", "sp"))
                    )
                )
            }
            for ((key, value) in node.attributes) {
                if (key != "android:textSize" && value.endsWith("sp") && (key.contains("layout_") || key.contains("padding"))) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "WrongDimensionUnit",
                            message = "Verwende 'dp' statt 'sp' für $key",
                            quickFix = QuickFix.SetAttribute("In dp umwandeln", node.uid, key, value.replace("sp", "dp"))
                        )
                    )
                }
            }
        }
    }

    private fun checkRtlAttributes(nodes: List<LayoutNode>, issues: MutableList<LayoutIssue>) {
        val rtlReplacements = mapOf(
            "android:layout_marginLeft" to "android:layout_marginStart",
            "android:layout_marginRight" to "android:layout_marginEnd",
            "android:paddingLeft" to "android:paddingStart",
            "android:paddingRight" to "android:paddingEnd",
            "android:layout_alignParentLeft" to "android:layout_alignParentStart",
            "android:layout_alignParentRight" to "android:layout_alignParentEnd",
            "android:layout_toLeftOf" to "android:layout_toStartOf",
            "android:layout_toRightOf" to "android:layout_toEndOf",
            "android:layout_alignLeft" to "android:layout_alignStart",
            "android:layout_alignRight" to "android:layout_alignEnd"
        )
        for (node in nodes) {
            for ((oldAttr, newAttr) in rtlReplacements) {
                if (node.attributes.containsKey(oldAttr)) {
                    issues.add(
                        LayoutIssue(
                            uid = node.uid,
                            severity = IssueSeverity.WARNING,
                            ruleId = "RtlAttributes",
                            message = "Verwende '$newAttr' statt '$oldAttr' für RTL-Unterstützung",
                            quickFix = QuickFix.ReplaceAttribute("Zu Start/End migrieren", node.uid, oldAttr, newAttr)
                        )
                    )
                }
            }
        }
    }

    private fun checkConstraintLayoutRules(
        document: LayoutDocument,
        nodes: List<LayoutNode>,
        issues: MutableList<LayoutIssue>
    ) {
        val helperTags = setOf(
            "Guideline", "androidx.constraintlayout.widget.Guideline",
            "Barrier", "androidx.constraintlayout.widget.Barrier",
            "Group", "androidx.constraintlayout.widget.Group"
        )

        for (node in nodes) {
            if (node.tag.substringAfterLast('.') == "ConstraintLayout") {
                for (child in node.children) {
                    if (child.tag in helperTags || child.tag.substringAfterLast('.') in helperTags) continue

                    val hasHorizontal = child.attributes.keys.any {
                        it.contains(":layout_constraint") && (
                            it.endsWith("toStartOf") || it.endsWith("toEndOf") ||
                            it.endsWith("toLeftOf") || it.endsWith("toRightOf")
                        )
                    }
                    val hasVertical = child.attributes.keys.any {
                        it.contains(":layout_constraint") && (
                            it.endsWith("toTopOf") || it.endsWith("toBottomOf") ||
                            it.endsWith("toBaselineOf")
                        )
                    }

                    if (!hasHorizontal || !hasVertical) {
                        issues.add(
                            LayoutIssue(
                                uid = child.uid,
                                severity = IssueSeverity.ERROR,
                                ruleId = "UnconstrainedChild",
                                message = "<${child.tag}> benötigt mindestens eine horizontale und eine vertikale Einschränkung"
                            )
                        )
                    }
                }

                // Check circular constraints in horizontal or vertical axis
                val childIdMap = node.children.mapNotNull { c ->
                    c.attributes["android:id"]?.let { rawId(it) to c }
                }.toMap()

                for (child in node.children) {
                    val cId = child.attributes["android:id"]?.let { rawId(it) } ?: continue
                    // Check if child A constrains start/end to B, and B constrains start/end to A without being a chain
                    val hTarget = child.attributes.entries.firstOrNull {
                        it.key.contains(":layout_constraintStart_to") || it.key.contains(":layout_constraintEnd_to")
                    }?.value?.let { rawId(it) }

                    if (hTarget != null && hTarget in childIdMap && hTarget != cId) {
                        val other = childIdMap[hTarget]
                        val otherTarget = other?.attributes?.entries?.firstOrNull {
                            it.key.contains(":layout_constraintStart_to") || it.key.contains(":layout_constraintEnd_to")
                        }?.value?.let { rawId(it) }
                        val isChain = child.attributes.containsKey("app:layout_constraintHorizontal_chainStyle") ||
                            other?.attributes?.containsKey("app:layout_constraintHorizontal_chainStyle") == true
                        if (otherTarget == cId && !isChain) {
                            issues.add(
                                LayoutIssue(
                                    uid = child.uid,
                                    severity = IssueSeverity.ERROR,
                                    ruleId = "CircularConstraints",
                                    message = "Zirkuläre horizontale Einschränkung zwischen '@+id/$cId' und '@+id/$hTarget'"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkUselessParent(
        document: LayoutDocument,
        nodes: List<LayoutNode>,
        issues: MutableList<LayoutIssue>
    ) {
        val genericContainers = setOf("FrameLayout", "LinearLayout", "RelativeLayout")
        for (node in nodes) {
            if (node.uid != document.root.uid && node.tag.substringAfterLast('.') in genericContainers) {
                if (node.children.size == 1) {
                    val hasBackground = node.attributes.containsKey("android:background")
                    val hasPadding = node.attributes.keys.any { it.contains("padding") }
                    val hasElevation = node.attributes.containsKey("android:elevation")
                    val hasWeight = node.attributes.containsKey("android:layout_weight")

                    if (!hasBackground && !hasPadding && !hasElevation && !hasWeight) {
                        issues.add(
                            LayoutIssue(
                                uid = node.uid,
                                severity = IssueSeverity.INFO,
                                ruleId = "UselessParent",
                                message = "Überflüssiger Eltern-Container <${node.tag}> mit nur einem Kind",
                                quickFix = QuickFix.UnwrapContainer("Container auflösen", node.uid)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun checkDeepNesting(document: LayoutDocument, issues: MutableList<LayoutIssue>) {
        fun traverse(node: LayoutNode, depth: Int) {
            if (depth > 10) {
                issues.add(
                    LayoutIssue(
                        uid = node.uid,
                        severity = IssueSeverity.INFO,
                        ruleId = "DeepNesting",
                        message = "Sehr tiefe Verschachtelung (Ebene $depth > 10) kann die Render-Performance beeinträchtigen"
                    )
                )
            }
            for (child in node.children) {
                traverse(child, depth + 1)
            }
        }
        traverse(document.root, 1)
    }
}
