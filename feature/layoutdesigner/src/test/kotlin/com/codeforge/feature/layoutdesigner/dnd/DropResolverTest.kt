package com.codeforge.feature.layoutdesigner.dnd

import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.model.PaletteEntry
import com.codeforge.feature.layoutdesigner.model.WidgetCategory
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropResolverTest {

    private val buttonEntry = PaletteEntry("button", "Button", WidgetCategory.BUTTON, emptyMap())

    @Test
    fun resolveDrop_linearLayout_vertical_calculatesIndexAndLine() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <TextView android:id="@+id/v1" android:layout_width="match_parent" android:layout_height="50dp" />
    <TextView android:id="@+id/v2" android:layout_width="match_parent" android:layout_height="50dp" />
</LinearLayout>"""
        val doc = LayoutXml.parse(xml)
        val bounds = mapOf(
            0 to DesignerRect(0f, 0f, 300f, 600f),
            1 to DesignerRect(0f, 0f, 300f, 100f), // centerY = 50
            2 to DesignerRect(0f, 100f, 300f, 200f), // centerY = 150
        )

        // Drop above first child centerY (e.g. y = 30) -> index 0
        val targetTop = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(150f, 30f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetTop)
        assertEquals(0, targetTop!!.parentUid)
        assertEquals(0, targetTop.index)
        assertTrue(targetTop.indicator is DropIndicator.Line)

        // Drop between first and second child (e.g. y = 120, below v1 and above v2 centerY) -> index 1
        val targetMiddle = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(150f, 120f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetMiddle)
        assertEquals(1, targetMiddle!!.index)

        // Drop below second child centerY (e.g. y = 250) -> index 2
        val targetBottom = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(150f, 250f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetBottom)
        assertEquals(2, targetBottom!!.index)
    }

    @Test
    fun resolveDrop_linearLayout_horizontal_calculatesIndexByX() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="horizontal">
    <Button android:id="@+id/b1" android:layout_width="100dp" android:layout_height="50dp" />
</LinearLayout>"""
        val doc = LayoutXml.parse(xml)
        val bounds = mapOf(
            0 to DesignerRect(0f, 0f, 400f, 200f),
            1 to DesignerRect(0f, 0f, 100f, 100f), // centerX = 50
        )

        // Pointer left of child centerX -> index 0
        val targetLeft = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(20f, 50f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetLeft)
        assertEquals(0, targetLeft!!.index)

        // Pointer right of child centerX -> index 1
        val targetRight = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(80f, 50f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetRight)
        assertEquals(1, targetRight!!.index)
    }

    @Test
    fun resolveDrop_frameLayout_computesGravityZones() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />"""
        val doc = LayoutXml.parse(xml)
        val bounds = mapOf(
            0 to DesignerRect(0f, 0f, 300f, 300f),
        )

        // Bottom right zone: x = 250, y = 250
        val targetBR = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(250f, 250f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetBR)
        assertEquals("bottom|end", targetBR!!.suggestedAttributes["android:layout_gravity"])

        // Center zone: x = 150, y = 150
        val targetCenter = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(150f, 150f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(targetCenter)
        assertEquals("center", targetCenter!!.suggestedAttributes["android:layout_gravity"])
    }

    @Test
    fun resolveDrop_constraintLayout_setsParentConstraintsAndSnappedMargins() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />"""
        val doc = LayoutXml.parse(xml)
        val bounds = mapOf(
            0 to DesignerRect(0f, 0f, 400f, 800f),
        )

        // x = 85 (snaps to 88 or 80 depending on 8dp grid; 85/8 = 10.625 -> 11 * 8 = 88)
        val target = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(85f, 163f),
            payload = DragPayload.NewWidget(buttonEntry),
            densityDpFactor = 1f,
        )
        assertNotNull(target)
        assertEquals("parent", target!!.suggestedAttributes["app:layout_constraintStart_toStartOf"])
        assertEquals("parent", target.suggestedAttributes["app:layout_constraintTop_toTopOf"])
        assertEquals("88dp", target.suggestedAttributes["android:layout_marginStart"])
        assertEquals("160dp", target.suggestedAttributes["android:layout_marginTop"])
    }

    @Test
    fun resolveDrop_deepestContainerWins() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <FrameLayout android:id="@+id/innerFrame"
        android:layout_width="200dp"
        android:layout_height="200dp" />
</LinearLayout>"""
        val doc = LayoutXml.parse(xml)
        val bounds = mapOf(
            0 to DesignerRect(0f, 0f, 400f, 800f),
            1 to DesignerRect(50f, 50f, 250f, 250f),
        )

        // Pointer inside inner FrameLayout (uid 1) -> inner container wins over root LinearLayout (uid 0)
        val target = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(100f, 100f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNotNull(target)
        assertEquals(1, target!!.parentUid)
    }

    @Test
    fun resolveDrop_outsideBounds_returnsNull() {
        val doc = LayoutXml.parse("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" />")
        val bounds = mapOf(0 to DesignerRect(0f, 0f, 100f, 100f))

        val target = DropResolver.resolveDrop(
            document = doc,
            boundsMap = bounds,
            pointer = DesignerOffset(500f, 500f),
            payload = DragPayload.NewWidget(buttonEntry),
        )
        assertNull(target)
    }
}
