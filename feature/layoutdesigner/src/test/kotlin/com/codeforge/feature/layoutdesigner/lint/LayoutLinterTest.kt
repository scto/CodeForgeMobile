package com.codeforge.feature.layoutdesigner.lint

import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutLinterTest {

    private fun doc(root: LayoutNode): LayoutDocument = LayoutDocument(root = root, nextUid = 100)

    @Test
    fun testDuplicateId_detected() {
        val root = LayoutNode(
            uid = 1,
            tag = "LinearLayout",
            attributes = linkedMapOf("android:id" to "@+id/dup"),
            children = listOf(
                LayoutNode(
                    uid = 2,
                    tag = "TextView",
                    attributes = linkedMapOf("android:id" to "@+id/dup")
                )
            )
        )
        val issues = LayoutLinter.lint(doc(root))
        val dupIssues = issues.filter { it.ruleId == "DuplicateId" }
        assertEquals(2, dupIssues.size)
        assertEquals(IssueSeverity.ERROR, dupIssues.first().severity)
    }

    @Test
    fun testMissingIdReference_detected() {
        val root = LayoutNode(
            uid = 1,
            tag = "RelativeLayout",
            attributes = linkedMapOf(),
            children = listOf(
                LayoutNode(
                    uid = 2,
                    tag = "TextView",
                    attributes = linkedMapOf(
                        "android:id" to "@+id/txt",
                        "android:layout_below" to "@id/non_existent"
                    )
                )
            )
        )
        val issues = LayoutLinter.lint(doc(root))
        val missingIssues = issues.filter { it.ruleId == "MissingIdReference" }
        assertEquals(1, missingIssues.size)
        assertTrue(missingIssues.first().message.contains("non_existent"))
    }

    @Test
    fun testHardcodedText_andQuickFix() {
        val root = LayoutNode(
            uid = 1,
            tag = "TextView",
            attributes = linkedMapOf("android:text" to "Hello World")
        )
        val document = doc(root)
        val issues = LayoutLinter.lint(document)
        val hardcoded = issues.firstOrNull { it.ruleId == "HardcodedText" }
        assertNotNull(hardcoded)
        assertEquals(IssueSeverity.WARNING, hardcoded!!.severity)
        assertTrue(hardcoded.quickFix is QuickFix.ExtractResource)
    }

    @Test
    fun testMissingContentDescription_andQuickFix() {
        val root = LayoutNode(
            uid = 1,
            tag = "ImageView",
            attributes = linkedMapOf("android:src" to "@drawable/logo")
        )
        val document = doc(root)
        val issues = LayoutLinter.lint(document)
        val missingDesc = issues.firstOrNull { it.ruleId == "MissingContentDescription" }
        assertNotNull(missingDesc)
        val fix = missingDesc!!.quickFix as QuickFix.SetAttribute
        val fixedDoc = LayoutLinter.applyQuickFix(document, fix)
        assertEquals("no", fixedDoc.find(1)?.attributes?.get("android:importantForAccessibility"))
    }

    @Test
    fun testSmallTouchTarget_detected() {
        val root = LayoutNode(
            uid = 1,
            tag = "Button",
            attributes = linkedMapOf(
                "android:layout_width" to "32dp",
                "android:layout_height" to "32dp"
            )
        )
        val issues = LayoutLinter.lint(doc(root))
        val small = issues.firstOrNull { it.ruleId == "SmallTouchTarget" }
        assertNotNull(small)
    }

    @Test
    fun testScrollViewChildren_errorWhenMoreThanOne() {
        val root = LayoutNode(
            uid = 1,
            tag = "ScrollView",
            attributes = linkedMapOf(),
            children = listOf(
                LayoutNode(uid = 2, tag = "TextView", attributes = linkedMapOf()),
                LayoutNode(uid = 3, tag = "Button", attributes = linkedMapOf())
            )
        )
        val issues = LayoutLinter.lint(doc(root))
        val scrollIssues = issues.filter { it.ruleId == "ScrollViewChildren" }
        assertEquals(1, scrollIssues.size)
        assertEquals(IssueSeverity.ERROR, scrollIssues.first().severity)
    }

    @Test
    fun testNestedWeights_warning() {
        val innerLinear = LayoutNode(
            uid = 2,
            tag = "LinearLayout",
            attributes = linkedMapOf("android:layout_weight" to "1"),
            children = emptyList()
        )
        val outerLinear = LayoutNode(
            uid = 1,
            tag = "LinearLayout",
            attributes = linkedMapOf("android:layout_weight" to "1"),
            children = listOf(innerLinear)
        )
        val issues = LayoutLinter.lint(doc(outerLinear))
        val nestedIssues = issues.filter { it.ruleId == "NestedWeights" }
        assertEquals(1, nestedIssues.size)
    }

    @Test
    fun testInefficientWeight_andQuickFix() {
        val child = LayoutNode(
            uid = 2,
            tag = "TextView",
            attributes = linkedMapOf(
                "android:layout_weight" to "1",
                "android:layout_height" to "wrap_content"
            )
        )
        val linear = LayoutNode(
            uid = 1,
            tag = "LinearLayout",
            attributes = linkedMapOf("android:orientation" to "vertical"),
            children = listOf(child)
        )
        val document = doc(linear)
        val issues = LayoutLinter.lint(document)
        val ineff = issues.firstOrNull { it.ruleId == "InefficientWeight" }
        assertNotNull(ineff)
        val fix = ineff!!.quickFix as QuickFix.SetAttribute
        val fixedDoc = LayoutLinter.applyQuickFix(document, fix)
        assertEquals("0dp", fixedDoc.find(2)?.attributes?.get("android:layout_height"))
    }

    @Test
    fun testDeprecatedFillParent_andQuickFix() {
        val root = LayoutNode(
            uid = 1,
            tag = "TextView",
            attributes = linkedMapOf("android:layout_width" to "fill_parent")
        )
        val document = doc(root)
        val issues = LayoutLinter.lint(document)
        val deprecated = issues.firstOrNull { it.ruleId == "DeprecatedFillParent" }
        assertNotNull(deprecated)
        val fix = deprecated!!.quickFix as QuickFix.SetAttribute
        val fixedDoc = LayoutLinter.applyQuickFix(document, fix)
        assertEquals("match_parent", fixedDoc.find(1)?.attributes?.get("android:layout_width"))
    }

    @Test
    fun testPxUsage_warning() {
        val root = LayoutNode(
            uid = 1,
            tag = "TextView",
            attributes = linkedMapOf("android:layout_width" to "100px")
        )
        val issues = LayoutLinter.lint(doc(root))
        assertTrue(issues.any { it.ruleId == "PxUsage" })
    }

    @Test
    fun testWrongDimensionUnits_spForLayoutAndDpForText() {
        val root = LayoutNode(
            uid = 1,
            tag = "TextView",
            attributes = linkedMapOf(
                "android:textSize" to "16dp",
                "android:layout_margin" to "12sp"
            )
        )
        val document = doc(root)
        val issues = LayoutLinter.lint(document).filter { it.ruleId == "WrongDimensionUnit" }
        assertEquals(2, issues.size)

        var fixedDoc = document
        for (issue in issues) {
            val fix = issue.quickFix as QuickFix.SetAttribute
            fixedDoc = LayoutLinter.applyQuickFix(fixedDoc, fix)
        }
        assertEquals("16sp", fixedDoc.find(1)?.attributes?.get("android:textSize"))
        assertEquals("12dp", fixedDoc.find(1)?.attributes?.get("android:layout_margin"))
    }

    @Test
    fun testRtlAttributes_andQuickFix() {
        val root = LayoutNode(
            uid = 1,
            tag = "TextView",
            attributes = linkedMapOf("android:layout_marginLeft" to "16dp")
        )
        val document = doc(root)
        val issues = LayoutLinter.lint(document)
        val rtl = issues.firstOrNull { it.ruleId == "RtlAttributes" }
        assertNotNull(rtl)
        val fix = rtl!!.quickFix as QuickFix.ReplaceAttribute
        val fixedDoc = LayoutLinter.applyQuickFix(document, fix)
        assertNull(fixedDoc.find(1)?.attributes?.get("android:layout_marginLeft"))
        assertEquals("16dp", fixedDoc.find(1)?.attributes?.get("android:layout_marginStart"))
    }

    @Test
    fun testUnconstrainedChild_andCircularConstraints() {
        val child1 = LayoutNode(
            uid = 2,
            tag = "Button",
            attributes = linkedMapOf(
                "android:id" to "@+id/b1",
                "app:layout_constraintStart_toEndOf" to "@id/b2"
            )
        )
        val child2 = LayoutNode(
            uid = 3,
            tag = "Button",
            attributes = linkedMapOf(
                "android:id" to "@+id/b2",
                "app:layout_constraintStart_toEndOf" to "@id/b1"
            )
        )
        val root = LayoutNode(
            uid = 1,
            tag = "androidx.constraintlayout.widget.ConstraintLayout",
            attributes = linkedMapOf(),
            children = listOf(child1, child2)
        )
        val issues = LayoutLinter.lint(doc(root))
        assertTrue(issues.any { it.ruleId == "UnconstrainedChild" })
        assertTrue(issues.any { it.ruleId == "CircularConstraints" })
    }

    @Test
    fun testUselessParent_andQuickFix() {
        val child = LayoutNode(uid = 3, tag = "Button", attributes = linkedMapOf())
        val useless = LayoutNode(uid = 2, tag = "FrameLayout", attributes = linkedMapOf(), children = listOf(child))
        val root = LayoutNode(uid = 1, tag = "LinearLayout", attributes = linkedMapOf(), children = listOf(useless))
        val document = doc(root)

        val issues = LayoutLinter.lint(document)
        val uselessIssue = issues.firstOrNull { it.ruleId == "UselessParent" }
        assertNotNull(uselessIssue)

        val fix = uselessIssue!!.quickFix as QuickFix.UnwrapContainer
        val fixedDoc = LayoutLinter.applyQuickFix(document, fix)
        assertNull(fixedDoc.find(2))
        assertNotNull(fixedDoc.find(3))
        assertEquals(listOf(3), fixedDoc.root.children.map { it.uid })
    }

    @Test
    fun testDeepNesting_reportedAtLevelEleven() {
        var current = LayoutNode(uid = 11, tag = "TextView", attributes = linkedMapOf())
        for (i in 10 downTo 1) {
            current = LayoutNode(uid = i, tag = "FrameLayout", attributes = linkedMapOf(), children = listOf(current))
        }
        val issues = LayoutLinter.lint(doc(current))
        assertTrue(issues.any { it.ruleId == "DeepNesting" })
    }
}
