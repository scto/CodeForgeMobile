package com.codeforge.feature.layoutdesigner.res

import com.codeforge.feature.layoutdesigner.model.LayoutNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IncludeResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolve_normalInclude_mergesAttributesCorrectly() {
        val resDir = tempFolder.newFolder("res")
        val layoutDir = File(resDir, "layout").apply { mkdirs() }

        val includedFile = File(layoutDir, "header.xml")
        includedFile.writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/original_header"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#FF0000">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Header Title" />
            </LinearLayout>
            """.trimIndent()
        )

        val resolver = IncludeResolver(listOf(resDir))

        val includeNode = LayoutNode(
            uid = 10,
            tag = "include",
            attributes = mapOf(
                "layout" to "@layout/header",
                "android:id" to "@+id/overridden_header",
                "android:layout_width" to "200dp",
                "android:layout_height" to "80dp",
                "android:layout_marginTop" to "16dp",
            ),
        )

        val result = resolver.resolve("@layout/header", includeNode = includeNode)
        assertTrue(result is IncludedLayoutResult.Success)

        val success = result as IncludedLayoutResult.Success
        assertEquals("header", success.layoutName)
        assertEquals("@+id/overridden_header", success.mergedRoot.attr("android:id"))
        assertEquals("200dp", success.mergedRoot.attr("android:layout_width"))
        assertEquals("80dp", success.mergedRoot.attr("android:layout_height"))
        assertEquals("16dp", success.mergedRoot.attr("android:layout_marginTop"))
        assertEquals("#FF0000", success.mergedRoot.attr("android:background"))
        assertEquals(1, success.mergedRoot.children.size)
    }

    @Test
    fun resolve_cycleDetected_returnsCycleResult() {
        val resDir = tempFolder.newFolder("res")
        val layoutDir = File(resDir, "layout").apply { mkdirs() }

        val fileA = File(layoutDir, "layout_a.xml")
        val fileB = File(layoutDir, "layout_b.xml")

        fileA.writeText(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/layout_b" />
            </FrameLayout>
            """.trimIndent()
        )

        fileB.writeText(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/layout_a" />
            </FrameLayout>
            """.trimIndent()
        )

        val resolver = IncludeResolver(listOf(resDir))

        // Simuliere Rekursion mit visitedPaths
        val result = resolver.resolve(
            layoutRef = "@layout/layout_a",
            visitedPaths = setOf(fileA.canonicalPath),
        )
        assertTrue(result is IncludedLayoutResult.CycleDetected)
        val cycle = result as IncludedLayoutResult.CycleDetected
        assertEquals("layout_a", cycle.layoutName)
    }

    @Test
    fun resolve_depthLimitExceeded_returnsDepthLimitResult() {
        val resDir = tempFolder.newFolder("res")
        val resolver = IncludeResolver(listOf(resDir))

        val result = resolver.resolve(
            layoutRef = "@layout/some_layout",
            depth = 9,
        )
        assertTrue(result is IncludedLayoutResult.DepthLimitExceeded)
    }

    @Test
    fun resolve_notFound_returnsNotFoundResult() {
        val resDir = tempFolder.newFolder("res")
        val resolver = IncludeResolver(listOf(resDir))

        val result = resolver.resolve("@layout/non_existent")
        assertTrue(result is IncludedLayoutResult.NotFound)
    }
}
