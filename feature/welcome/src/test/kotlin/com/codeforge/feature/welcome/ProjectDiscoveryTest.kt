package com.codeforge.feature.welcome

import com.codeforge.core.domain.model.RecentProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectDiscoveryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `scan lists visible directories only and counts modules`() {
        val root = tmp.root
        java.io.File(root, "AppA/app").mkdirs(); java.io.File(root, "AppA/app/build.gradle.kts").writeText("")
        java.io.File(root, "AppA/lib").mkdirs(); java.io.File(root, "AppA/lib/build.gradle").writeText("")
        java.io.File(root, "Plain").mkdirs()
        java.io.File(root, ".cfm").mkdirs()
        java.io.File(root, "note.txt").writeText("x")

        val result = ProjectDiscovery.scan(root)
        assertEquals(listOf("AppA", "Plain"), result.map { it.name })
        assertEquals(2, result[0].moduleCount)
        assertEquals(1, result[1].moduleCount)
    }

    @Test fun `scan of missing root is empty`() {
        assertTrue(ProjectDiscovery.scan(java.io.File(tmp.root, "nope")).isEmpty())
    }

    @Test fun `merge keeps recent entry and adds unknown discovered ones`() {
        val recent = listOf(RecentProject("/x/A", "A", "/x/A", 500L))
        val found = listOf(
            RecentProject("/x/A", "A", "/x/A", 10L),
            RecentProject("/x/B", "B", "/x/B", 900L)
        )
        val merged = ProjectDiscovery.merge(recent, found)
        assertEquals(listOf("B", "A"), merged.map { it.name })
        assertEquals(500L, merged.first { it.name == "A" }.lastOpenedEpochMillis)
    }

    @Test fun `deleteProject loescht Unterordner im Stamm`() {
        val proj = java.io.File(tmp.root, "App").apply { mkdirs() }
        java.io.File(proj, "a/b").mkdirs(); java.io.File(proj, "a/b/f.txt").writeText("x")
        assertTrue(ProjectDiscovery.deleteProject(tmp.root, proj))
        assertTrue(!proj.exists())
    }

    @Test fun `deleteProject verweigert Stamm, Aussenstehende und Ausbrueche`() {
        val root = java.io.File(tmp.root, "root").apply { mkdirs() }
        val outside = java.io.File(tmp.root, "outside").apply { mkdirs() }
        java.io.File(root, "x").mkdirs()
        assertTrue(!ProjectDiscovery.deleteProject(root, root))
        assertTrue(!ProjectDiscovery.deleteProject(root, outside))
        assertTrue(!ProjectDiscovery.deleteProject(root, java.io.File(root, "x/../../outside")))
        assertTrue(outside.exists() && root.exists())
    }
}
