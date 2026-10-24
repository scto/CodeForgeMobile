package com.codeforge.feature.layoutdesigner.model

import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeSolverTest {

    @Test
    fun solve_topologicalOrder_placesDependenciesFirst() {
        val xml = """
            <RelativeLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <Button
                    android:id="@+id/btnBottom"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/title" />
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentTop="true" />
            </RelativeLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val result = RelativeSolver.solve(doc.root)

        assertFalse(result.hasCycle)
        assertEquals(2, result.orderedNodes.size)
        // title must come before btnBottom
        assertEquals("title", result.orderedNodes[0].id)
        assertEquals("btnBottom", result.orderedNodes[1].id)
    }

    @Test
    fun solve_detectsCycle() {
        val xml = """
            <RelativeLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <Button
                    android:id="@+id/btnA"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/btnB" />
                <Button
                    android:id="@+id/btnB"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/btnA" />
            </RelativeLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val result = RelativeSolver.solve(doc.root)

        assertTrue(result.hasCycle)
        assertTrue(result.cycleNodes.contains("btnA"))
        assertTrue(result.cycleNodes.contains("btnB"))
    }

    @Test
    fun solve_missingTarget_reportedInMissingList() {
        val xml = """
            <RelativeLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <Button
                    android:id="@+id/btnA"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/missingView" />
            </RelativeLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val result = RelativeSolver.solve(doc.root)

        assertTrue(result.missingTargets.contains("missingView"))
    }
}
